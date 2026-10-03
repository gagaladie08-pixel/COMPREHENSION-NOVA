package com.novastats.app.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.novastats.app.service.BootReceiver
import com.novastats.app.service.NovaListenerService
import com.novastats.app.ui.theme.NovaTheme
import kotlinx.coroutines.delay

/** État des 3 connexions. */
data class Permissions(val listener: Boolean, val battery: Boolean, val boot: Boolean) {
    val count get() = listOf(listener, battery, boot).count { it }
}

object PermissionChecks {
    fun listener(ctx: Context) = NovaListenerService.isEnabled(ctx)
    fun battery(ctx: Context) = runCatching { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }.getOrDefault(false)
    fun boot(ctx: Context) = ctx.packageManager.getComponentEnabledSetting(ComponentName(ctx, BootReceiver::class.java)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    fun setBoot(ctx: Context, enabled: Boolean) = runCatching {
        ctx.packageManager.setComponentEnabledSetting(
            ComponentName(ctx, BootReceiver::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP
        )
    }
    fun all(ctx: Context) = Permissions(listener(ctx), battery(ctx), boot(ctx))

    fun openListenerSettings(ctx: Context) = runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    /** Demande directe d'exclusion de l'optimisation batterie ; repli sur la liste système. Renvoie faux si aucun écran n'existe. */
    @Suppress("BatteryLife")
    fun requestBattery(ctx: Context): Boolean = runCatching {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.recoverCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)

    /** Chemin manuel selon le constructeur (HiOS/Tecno en premier : c'est le téléphone de référence). */
    fun manualBatteryPath(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            "tecno" in m || "infinix" in m || "itel" in m -> "Phone Master → Gestion batterie → Applications protégées (ou Économie d'énergie) → NovaStats → Autoriser. Puis Paramètres → Applications → NovaStats → Batterie → Aucune restriction."
            "xiaomi" in m || "redmi" in m || "poco" in m -> "Paramètres → Applications → Gérer les applications → NovaStats → Économiseur de batterie → Aucune restriction, et Démarrage automatique activé."
            "samsung" in m -> "Paramètres → Applications → NovaStats → Batterie → Non restreinte."
            "huawei" in m || "honor" in m -> "Paramètres → Batterie → Lancement d'applications → NovaStats → Gérer manuellement (tout activer)."
            "oppo" in m || "realme" in m || "oneplus" in m -> "Paramètres → Batterie → Plus de paramètres → Optimiser l'utilisation de la batterie → NovaStats → Ne pas optimiser."
            else -> "Paramètres → Applications → NovaStats → Batterie → Non restreinte / Ne pas optimiser."
        }
    }
    fun manualListenerPath(): String = "Paramètres → Notifications → Accès aux notifications (ou « Accès spécial des applications ») → NovaStats → Autoriser."
}

/**
 * Étape 2/4 — Checklist verticale BLOQUANTE : l'accès aux notifications et l'exclusion de l'optimisation
 * batterie sont obligatoires (aucun « Passer », bouton actif seulement quand les deux sont accordés, vérification
 * réelle à chaque retour dans l'app). Le redémarrage automatique est recommandé mais optionnel.
 */
@Composable
fun PermissionsStepScreen(audio: ObAudio, theme: NovaTheme, onBack: () -> Unit, onNext: (Permissions) -> Unit) {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(PermissionChecks.all(ctx)) }
    var waitingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var listenerTries by rememberSaveable { mutableIntStateOf(0) }
    var batteryTries by rememberSaveable { mutableIntStateOf(0) }
    // Garde-fou : aucun écran système d'exclusion batterie sur cet appareil → on l'indique et on accepte après ouverture manuelle
    var batteryUnavailable by rememberSaveable { mutableStateOf(false) }
    var batteryManualOpened by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val accent = theme.primary

    LifecycleResumeEffect(Unit) {
        val now = PermissionChecks.all(ctx)
        when (waitingFor) {
            "listener" -> if (now.listener && !perms.listener) { audio.tick(); audio.success() } else if (!now.listener) listenerTries++
            "battery" -> if (now.battery && !perms.battery) { audio.tick(); audio.success() } else if (!now.battery) batteryTries++
            "battery_manual" -> { batteryManualOpened = true; if (now.battery) { audio.tick(); audio.success() } }
        }
        waitingFor = null
        perms = now
        onPauseOrDispose { }
    }

    val batteryOk = perms.battery || (batteryUnavailable && batteryManualOpened)
    val required = perms.listener && batteryOk
    val missing = listOfNotNull(if (!perms.listener) "l'accès aux notifications" else null, if (!batteryOk) "l'optimisation batterie" else null)
    val fill by animateFloatAsState(if (leaving) 1f else 0f, tween(700, easing = ObEasing), label = "fill")
    LaunchedEffect(leaving) { if (leaving) { delay(750); onNext(perms) } }
    // Étape « active » = la première non faite
    val activeIndex = when { !perms.listener -> 0; !batteryOk -> 1; !perms.boot -> 2; else -> 3 }

    Box(Modifier.fillMaxSize().background(theme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            StepIndicator(2, accent, theme.text)
            Spacer(Modifier.height(34.dp))
            Text("CONNEXION", color = theme.textSecondary, fontFamily = ObFonts.body, fontSize = 11.sp, letterSpacing = 5.sp)
            Spacer(Modifier.height(8.dp))
            CinzelTitle("Laisse NovaStats t'écouter", theme.text, size = 26, letterSpacing = 1, modifier = Modifier.padding(horizontal = 28.dp))
            Spacer(Modifier.height(10.dp))
            PoeticText("Deux réglages sont indispensables pour que ton histoire s'écrive sans interruption. Ils ne lisent rien d'autre que le titre en cours.", theme.textSecondary, 14, Modifier.padding(horizontal = 36.dp))
            Spacer(Modifier.height(30.dp))

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                ChecklistItem(
                    theme = theme, index = 0, last = false, active = activeIndex == 0, done = perms.listener, required = true,
                    title = "Accès aux notifications",
                    body = "C'est ainsi que NovaStats voit le titre qui joue dans Spotify, YouTube Music, Deezer… Aucune notification n'est lue ni stockée.",
                    help = if (listenerTries >= 1 && !perms.listener) "Le réglage n'a pas été activé. Chemin manuel : ${PermissionChecks.manualListenerPath()}" else null,
                    buttonLabel = if (listenerTries >= 1) "Ouvrir les paramètres" else "Autoriser",
                    onAction = { waitingFor = "listener"; audio.tap(); PermissionChecks.openListenerSettings(ctx) }
                )
                ChecklistItem(
                    theme = theme, index = 1, last = false, active = activeIndex == 1, done = batteryOk, required = true,
                    title = "Optimisation batterie désactivée",
                    body = "Empêche Android (et Phone Master sur Tecno) de couper NovaStats en arrière-plan. Sans ça, des écoutes seraient perdues.",
                    help = when {
                        batteryUnavailable && !batteryManualOpened -> "Ton téléphone ne propose pas l'écran direct. Ouvre la liste des applications et retire la restriction pour NovaStats : ${PermissionChecks.manualBatteryPath()}"
                        batteryTries >= 1 && !perms.battery -> "Toujours optimisé. Chemin manuel : ${PermissionChecks.manualBatteryPath()}"
                        else -> null
                    },
                    buttonLabel = if (batteryUnavailable) "Ouvrir les applications" else if (batteryTries >= 1) "Réessayer" else "Désactiver",
                    onAction = {
                        audio.tap()
                        if (batteryUnavailable) {
                            waitingFor = "battery_manual"
                            runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        } else {
                            waitingFor = "battery"
                            if (!PermissionChecks.requestBattery(ctx)) { batteryUnavailable = true; waitingFor = null }
                        }
                    }
                )
                ChecklistItem(
                    theme = theme, index = 2, last = true, active = activeIndex == 2, done = perms.boot, required = false,
                    title = "Relance au redémarrage",
                    body = "NovaStats se remet à l'écoute tout seul quand le téléphone redémarre.",
                    help = null, buttonLabel = "Activer",
                    onAction = { PermissionChecks.setBoot(ctx, true); perms = perms.copy(boot = true); audio.tick(); audio.success() }
                )
            }

            Spacer(Modifier.height(30.dp))
            // Bouton principal : affiche en permanence ce qui manque
            val label = if (required) "Continuer" else "Il reste : ${missing.joinToString(" et ")}"
            ObButton(label, listOf(accent), enabled = required && !leaving) { leaving = true; audio.whoosh(); audio.tap() }
            if (!required) Text("Ces deux réglages sont obligatoires pour continuer.", color = theme.textSecondary.copy(alpha = 0.7f), fontFamily = ObFonts.body, fontSize = 11.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 12.dp, start = 32.dp, end = 32.dp))
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)) { Text("Retour", color = theme.textSecondary, fontFamily = ObFonts.body, letterSpacing = 2.sp, fontSize = 12.sp) }
        }
        if (fill > 0f) Box(Modifier.fillMaxSize().alpha(fill).background(Color.Black))
    }
}

/** Élément de checklist : anneau d'état + ligne verticale qui s'illumine ; seule l'étape active est dépliée. */
@Composable
fun ChecklistItem(
    theme: NovaTheme, index: Int, last: Boolean, active: Boolean, done: Boolean, required: Boolean,
    title: String, body: String, help: String?, buttonLabel: String, onAction: () -> Unit
) {
    val accent = theme.primary
    var expanded by remember(active, done) { mutableStateOf(active && !done) }
    val lineAlpha by animateFloatAsState(if (done) 1f else 0.18f, tween(900, easing = ObEasing), label = "line")
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable { if (!done) expanded = !expanded }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp).fillMaxHeight()) {
            StatusRing(done = done, active = active, accent = accent, dim = theme.textSecondary)
            if (!last) Box(Modifier.width(1.dp).weight(1f).padding(vertical = 4.dp).background(accent.copy(alpha = lineAlpha)))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = if (done) theme.text.copy(alpha = 0.7f) else theme.text, fontFamily = ObFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Text(if (done) "ACTIVÉ" else if (required) "REQUIS" else "CONSEILLÉ", color = if (done) accent else if (required) theme.text.copy(alpha = 0.7f) else theme.textSecondary, fontFamily = ObFonts.body, fontSize = 9.sp, letterSpacing = 2.sp)
            }
            AnimatedVisibility(visible = expanded && !done, enter = fadeIn(tween(500)) + expandVertically(tween(500, easing = ObEasing)), exit = fadeOut(tween(250)) + shrinkVertically(tween(350, easing = ObEasing))) {
                Column {
                    PoeticText(body, theme.textSecondary, 13, Modifier.padding(top = 6.dp), align = androidx.compose.ui.text.style.TextAlign.Start)
                    if (help != null) Text(help, color = theme.text.copy(alpha = 0.85f), fontFamily = ObFonts.body, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 10.dp))
                    Spacer(Modifier.height(14.dp))
                    ObButton(buttonLabel, listOf(accent), onClick = onAction)
                }
            }
        }
    }
}
