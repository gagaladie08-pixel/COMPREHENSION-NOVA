package com.novastats.app.ui.onboarding

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.novastats.app.service.BootReceiver
import com.novastats.app.service.NovaListenerService
import com.novastats.app.service.NotificationAccess
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
 * Étape 2/4 — Liste « Réglages » bloquante : notifications + optimisation batterie obligatoires (pas de « Passer »,
 * bouton inactif tant que les deux ne sont pas réellement accordés, vérification à chaque retour dans l'app).
 * Redémarrage automatique recommandé. Chemins manuels HiOS / Phone Master si le réglage n'a pas pris.
 */
@Composable
fun PermissionsStepScreen(audio: ObAudio, theme: NovaTheme, onBack: () -> Unit, onNext: (Permissions) -> Unit) {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(PermissionChecks.all(ctx)) }
    var waitingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var listenerTries by rememberSaveable { mutableIntStateOf(0) }
    var batteryTries by rememberSaveable { mutableIntStateOf(0) }
    var batteryUnavailable by rememberSaveable { mutableStateOf(false) }
    var batteryManualOpened by rememberSaveable { mutableStateOf(false) }
    var appNotifications by remember { mutableStateOf(NotificationAccess.canPost(ctx)) }
    var appNotificationPrompted by rememberSaveable { mutableStateOf(false) }
    val appNotificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        appNotificationPrompted = true
        appNotifications = granted && NotificationAccess.canPost(ctx)
        if (appNotifications) { audio.tick(); audio.success() }
    }
    val beats = rememberBeats(4, startMs = 150, stepMs = 140)
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
        appNotifications = NotificationAccess.canPost(ctx)
        onPauseOrDispose { }
    }

    val batteryOk = perms.battery || (batteryUnavailable && batteryManualOpened)
    val required = perms.listener && batteryOk
    val missing = listOfNotNull(if (!perms.listener) "notifications" else null, if (!batteryOk) "batterie" else null)

    ObStage(base = theme.background, accent = accent) {
        ObLayout(step = 2, accent = accent, bottom = {
            Appear(beats >= 4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ObPrimaryButton(if (required) "Continuer" else "Il manque : ${missing.joinToString(" + ")}", enabled = required, fill = if (required) accent else ObColors.Text) { audio.tap(); audio.whoosh(); onNext(perms) }
                    Text(if (required) "Tout est en place." else "Les deux premiers réglages sont obligatoires.", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    ObLinkButton("Retour", ObColors.Gray) { onBack() }
                }
            }
        }) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Appear(beats >= 1) { ObEyebrow("Connexion", accent) }
                Spacer(Modifier.height(6.dp))
                Appear(beats >= 1) { ObHeadline("Deux réglages.\nRien de plus.") }
                Spacer(Modifier.height(12.dp))
                Appear(beats >= 2) { ObSub("NovaStats lit uniquement le titre en cours de lecture. Ces réglages lui permettent de ne jamais être coupé.") }
                Spacer(Modifier.height(26.dp))
                Appear(beats >= 3) {
                    ObCard {
                        PermRow(
                            icon = Icons.Rounded.Notifications, color = Color(0xFFFF3B30), title = "Accès aux notifications", required = true, done = perms.listener,
                            subtitle = "Voir le titre qui joue dans Spotify, YouTube Music, Deezer…",
                            help = if (listenerTries >= 1 && !perms.listener) PermissionChecks.manualListenerPath() else null,
                            buttonLabel = if (listenerTries >= 1) "Ouvrir" else "Autoriser"
                        ) { waitingFor = "listener"; audio.tap(); PermissionChecks.openListenerSettings(ctx) }
                        ObDivider()
                        PermRow(
                            icon = Icons.Rounded.BatteryChargingFull, color = Color(0xFF34C759), title = "Batterie sans restriction", required = true, done = batteryOk,
                            subtitle = "Empêche Android et Phone Master de couper NovaStats en arrière-plan.",
                            help = when {
                                batteryUnavailable && !batteryManualOpened -> "Pas d'écran direct sur cet appareil. " + PermissionChecks.manualBatteryPath()
                                batteryTries >= 1 && !perms.battery -> PermissionChecks.manualBatteryPath()
                                else -> null
                            },
                            buttonLabel = if (batteryUnavailable) "Ouvrir" else if (batteryTries >= 1) "Réessayer" else "Désactiver"
                        ) {
                            audio.tap()
                            if (batteryUnavailable) {
                                waitingFor = "battery_manual"
                                runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            } else {
                                waitingFor = "battery"
                                if (!PermissionChecks.requestBattery(ctx)) { batteryUnavailable = true; waitingFor = null }
                            }
                        }
                        ObDivider()
                        PermRow(
                            icon = Icons.Rounded.RestartAlt, color = Color(0xFF0A84FF), title = "Relance au redémarrage", required = false, done = perms.boot,
                            subtitle = "NovaStats se remet à l'écoute après un redémarrage.", help = null, buttonLabel = "Activer"
                        ) { PermissionChecks.setBoot(ctx, true); perms = perms.copy(boot = true); audio.tick(); audio.success() }
                        ObDivider()
                        PermRow(
                            icon = Icons.Rounded.Notifications, color = Color(0xFF7C6CFF), title = "Notifications NovaStats", required = false, done = appNotifications,
                            subtitle = "Reçois les certifications, premières écoutes et bilans même quand l'app est fermée.",
                            help = if (!appNotifications && (appNotificationPrompted || NotificationAccess.hasRuntimePermission(ctx))) "Paramètres → Applications → NovaStats → Notifications → Autoriser." else null,
                            buttonLabel = if (Build.VERSION.SDK_INT >= 33 && !NotificationAccess.hasRuntimePermission(ctx) && !appNotificationPrompted) "Autoriser" else "Ouvrir"
                        ) {
                            audio.tap()
                            if (Build.VERSION.SDK_INT >= 33 && !NotificationAccess.hasRuntimePermission(ctx) && !appNotificationPrompted) {
                                appNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                runCatching { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Appear(beats >= 4) {
                    Text("Aucune notification n'est lue ni stockée. Tout reste sur ton téléphone.", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Ligne de réglage : icône colorée, titre + état, action à droite ; aide manuelle dépliée si besoin. */
@Composable
fun PermRow(icon: ImageVector, color: Color, title: String, required: Boolean, done: Boolean, subtitle: String, help: String?, buttonLabel: String, onAction: () -> Unit) {
    Column {
        ObRow(icon, color, title, subtitle, trailing = { if (done) ObCheck(true) else ObMiniButton(buttonLabel, ObColors.Text, onAction) })
        Row(Modifier.padding(start = 68.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ObTag(if (done) "Activé" else if (required) "Requis" else "Conseillé", if (done) ObColors.Green else if (required) ObColors.Orange else ObColors.Gray)
        }
        AnimatedVisibility(visible = help != null && !done, enter = fadeIn(tween(300)) + expandVertically(tween(350, easing = ObEasing)), exit = fadeOut(tween(150)) + shrinkVertically(tween(250))) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ObColors.Surface2).padding(12.dp)) {
                Text("Chemin manuel : " + (help ?: ""), color = ObColors.Text, fontFamily = ObFonts.inter, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

// compat (ancienne checklist)
@Composable
fun ChecklistItem(theme: NovaTheme, index: Int, last: Boolean, active: Boolean, done: Boolean, required: Boolean, title: String, body: String, help: String?, buttonLabel: String, onAction: () -> Unit) =
    PermRow(Icons.Rounded.Check, theme.primary, title, required, done, body, help, buttonLabel, onAction)
