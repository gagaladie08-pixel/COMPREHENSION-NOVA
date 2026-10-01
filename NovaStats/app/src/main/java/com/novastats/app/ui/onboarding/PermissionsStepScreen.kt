package com.novastats.app.ui.onboarding

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    @Suppress("BatteryLife")
    fun requestBattery(ctx: Context) = runCatching {
        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.recoverCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * 🔔 Étape 2/4 — Permissions : 3 orbes (🎵 🔋 🚀) + orbe central, cartes ESSENTIEL / RECOMMANDÉ,
 * gestion des refus de P1 (×1 dialog, ×2 guide manuel, ×3 continuer sans détection).
 */
@Composable
fun PermissionsStepScreen(audio: ObAudio, theme: NovaTheme, onBack: () -> Unit, onNext: (Permissions) -> Unit) {
    val ctx = LocalContext.current
    var perms by remember { mutableStateOf(PermissionChecks.all(ctx)) }
    var waitingFor by rememberSaveable { mutableStateOf<String?>(null) }   // "listener" / "battery"
    var p1Refusals by rememberSaveable { mutableIntStateOf(0) }
    var showRefusalDialog by remember { mutableStateOf(false) }
    var batterySkipped by rememberSaveable { mutableStateOf(false) }
    var bootSkipped by rememberSaveable { mutableStateOf(false) }
    var burstAt by remember { mutableStateOf<Int?>(null) }   // index de l'orbe qui explose
    var nova by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var p1Blink by remember { mutableStateOf(false) }

    fun celebrate(index: Int) { burstAt = index; audio.play("chime"); audio.soft() }

    LifecycleResumeEffect(Unit) {
        val now = PermissionChecks.all(ctx)
        when (waitingFor) {
            "listener" -> if (now.listener && !perms.listener) celebrate(0) else if (!now.listener) { p1Refusals++; audio.play("low"); audio.tap(); p1Blink = true; if (p1Refusals == 1) showRefusalDialog = true }
            "battery" -> if (now.battery && !perms.battery) celebrate(1) else if (!now.battery) { audio.play("low") }
        }
        waitingFor = null
        perms = now
        onPauseOrDispose { }
    }
    LaunchedEffect(perms.count) { if (perms.count == 3 && !nova) { delay(600); nova = true; audio.play("chord"); audio.doublePulse() } }
    LaunchedEffect(burstAt) { if (burstAt != null) { delay(900); burstAt = null } }
    LaunchedEffect(p1Blink) { if (p1Blink) { delay(1500); p1Blink = false } }

    val fill by animateFloatAsState(if (leaving) 1f else 0f, tween(if (perms.count == 3) 400 else 250), label = "fill")
    val canContinue = perms.listener || p1Refusals >= 3

    Box(Modifier.fillMaxSize().background(theme.background)) {
        ParticleField(Modifier.fillMaxSize(), listOf(theme.primary, theme.secondary, ObColors.Gold), mode = if (nova) ParticleMode.RAIN else ParticleMode.FLOAT, emitting = true, intensity = if (nova) 0.9f else 0.25f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            StepIndicator(2, theme.primary, theme.text)
            Spacer(Modifier.height(12.dp))
            CinzelTitle("Connecte NovaStats à ta musique", theme.primary, size = 22, letterSpacing = 2, modifier = Modifier.padding(horizontal = 24.dp))
            PoeticText("Pour vivre l'expérience complète, NovaStats a besoin de t'écouter.", theme.textSecondary, 15, Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
            Spacer(Modifier.height(10.dp))

            Orbs(perms, theme, burstAt, nova, p1Blink)

            Spacer(Modifier.height(12.dp))
            PermissionCard(
                theme = theme, emoji = "🎵", title = "Écouter ta musique", essential = true, granted = perms.listener, skipped = false,
                description = "Permet à NovaStats de détecter chaque titre que tu écoutes, quelle que soit l'application.",
                essentialNote = "Sans ça, NovaStats ne peut pas commencer ton histoire.",
                confirmed = "NovaStats écoute désormais chaque note avec toi. ✦",
                buttonLabel = if (p1Refusals >= 2) "📱 Ouvrir les paramètres" else "🔓 Autoriser l'écoute",
                onGrant = { waitingFor = "listener"; audio.tap(); PermissionChecks.openListenerSettings(ctx) },
                extra = if (p1Refusals >= 2 && !perms.listener) "Guide manuel : Paramètres → Notifications → Accès aux notifications (ou « Accès spécial ») → NovaStats → Autoriser." else if (p1Refusals >= 3 && !perms.listener) "NovaStats t'attendra." else null
            )
            PermissionCard(
                theme = theme, emoji = "🔋", title = "Rester en éveil", essential = false, granted = perms.battery, skipped = batterySkipped,
                description = "Empêche Android de couper NovaStats en arrière-plan. Ton histoire ne s'arrête jamais.",
                confirmed = "NovaStats ne dort jamais. ✦", buttonLabel = "🔋 Optimiser",
                onGrant = { waitingFor = "battery"; audio.tap(); PermissionChecks.requestBattery(ctx) }, onSkip = { batterySkipped = true; audio.play("low", 0.5f) }
            )
            PermissionCard(
                theme = theme, emoji = "🚀", title = "Renaître après chaque redémarrage", essential = false, granted = perms.boot, skipped = bootSkipped,
                description = "NovaStats se relance automatiquement quand ton téléphone redémarre.",
                confirmed = "NovaStats renaît toujours. ✦", buttonLabel = "🚀 Activer",
                onGrant = { PermissionChecks.setBoot(ctx, true); perms = perms.copy(boot = true); celebrate(2) }, onSkip = { bootSkipped = true; audio.play("low", 0.5f) }
            )

            Spacer(Modifier.height(18.dp))
            if (p1Refusals >= 3 && !perms.listener) PoeticText("NovaStats t'attendra. Tu pourras activer la détection à tout moment dans ⚙️.", theme.textSecondary, 14, Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
            ObButton(
                if (!perms.listener && p1Refusals >= 3) "Continuer sans détection" else "Continuer",
                listOf(theme.primary, theme.secondary, theme.glowSecondary), enabled = canContinue && !leaving, big = nova
            ) { leaving = true; audio.play(if (perms.count == 3) "boom" else "whoosh"); if (perms.count == 3) audio.impact() else audio.tap() }
            TextButton(onClick = onBack, modifier = Modifier.padding(top = 6.dp, bottom = 28.dp)) { Text("← Retour", color = theme.textSecondary) }
        }
        if (fill > 0f) Box(Modifier.fillMaxSize().alpha(fill).background(if (perms.count == 3) theme.primary else if (perms.count == 0) Color.Black else theme.surface))
    }
    LaunchedEffect(leaving) { if (leaving) { delay(if (perms.count == 3) 700 else 400); onNext(perms) } }

    if (showRefusalDialog) AlertDialog(
        onDismissRequest = { showRefusalDialog = false },
        title = { Text("🎵 NovaStats a besoin de t'écouter") },
        text = { Text("L'accès aux notifications permet de voir quel titre joue dans Spotify, YouTube Music, Deezer… Rien d'autre n'est lu. Sans cette autorisation, aucune écoute ne peut être comptée.") },
        confirmButton = { TextButton(onClick = { showRefusalDialog = false; waitingFor = "listener"; PermissionChecks.openListenerSettings(ctx) }) { Text("RÉESSAYER", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { showRefusalDialog = false }) { Text("Plus tard") } }
    )
}

/** 3 orbes + orbe central (indicateur global 0/3 → 3/3). */
@Composable
private fun Orbs(perms: Permissions, theme: NovaTheme, burstAt: Int?, nova: Boolean, p1Blink: Boolean) {
    val slow by rememberInfiniteTransition(label = "o").animateFloat(0.85f, 1.05f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "oa")
    val blink by rememberInfiniteTransition(label = "bl").animateFloat(0f, 1f, infiniteRepeatable(tween(250), RepeatMode.Reverse), label = "bla")
    val states = listOf(perms.listener, perms.battery, perms.boot)
    val emojis = listOf("🎵", "🔋", "🚀")
    val centralAlpha = when (perms.count) { 0 -> 0.12f; 1 -> 0.3f; 2 -> 0.6f; else -> 1f }
    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
        // Traits lumineux vers l'orbe central
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height * 0.72f; val cx = size.width / 2
            states.forEachIndexed { i, on ->
                if (on) { val x = size.width * (0.22f + 0.28f * i); drawLine(Brush.linearGradient(listOf(theme.primary, Color.Transparent), start = Offset(x, size.height * 0.3f), end = Offset(cx, cy)), Offset(x, size.height * 0.3f), Offset(cx, cy), 3f) }
            }
        }
        // Orbe central
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp).size(if (nova) (56 * slow).dp else 44.dp).scale(if (perms.count == 0) slow else 1f)
            .pulsingGlow(theme.primary, centralAlpha * 0.5f, centralAlpha, if (nova) 700 else 2000, 30.dp)
            .clip(CircleShape).background(Brush.radialGradient(listOf(theme.primary.copy(alpha = centralAlpha), ObColors.OrbOff)))
            .border(1.dp, theme.primary.copy(alpha = centralAlpha), CircleShape), contentAlignment = Alignment.Center) {
            Text(if (nova) "✨" else "${perms.count}/3", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Row(Modifier.align(Alignment.TopCenter).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            states.forEachIndexed { i, on ->
                val color = when { !on -> ObColors.OrbOff; i == 0 -> theme.primary; else -> ObColors.Green }
                val target by animateColorAsState(if (p1Blink && i == 0) ObColors.Red.copy(alpha = blink) else color, tween(300), label = "oc")
                val bursting = burstAt == i
                val sc by animateFloatAsState(if (bursting) 1.35f else if (!on) slow else 1f, tween(300), label = "os")
                Box(contentAlignment = Alignment.Center) {
                    if (bursting) ParticleField(Modifier.size(140.dp), listOf(ObColors.Gold, color), ParticleMode.BURST, emitting = true, intensity = 1f)
                    Box(Modifier.size(56.dp).scale(sc).pulsingGlow(target, if (on) 0.5f else 0.05f, if (on) 0.9f else 0.15f, 1400, 20.dp)
                        .clip(CircleShape).background(Brush.radialGradient(listOf(target.copy(alpha = 0.95f), target.copy(alpha = 0.4f))))
                        .border(2.dp, target, CircleShape), contentAlignment = Alignment.Center) {
                        Text(emojis[i], fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    theme: NovaTheme, emoji: String, title: String, essential: Boolean, granted: Boolean, skipped: Boolean,
    description: String, confirmed: String, buttonLabel: String, essentialNote: String? = null, extra: String? = null,
    onGrant: () -> Unit, onSkip: (() -> Unit)? = null
) {
    val badgeColor = if (essential) ObColors.Red else ObColors.Blue
    val borderColor = when { granted -> ObColors.Green; essential -> theme.primary; else -> theme.textSecondary.copy(alpha = 0.3f) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(theme.surface)
            .then(if (granted) Modifier.border(1.5.dp, ObColors.Green.copy(alpha = 0.8f), RoundedCornerShape(16.dp)) else Modifier.border(1.dp, borderColor, RoundedCornerShape(16.dp)))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Text(title.uppercase(), color = theme.text, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(if (essential) "⚠️ ESSENTIEL" else "💡 RECOMMANDÉ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(badgeColor).padding(horizontal = 6.dp, vertical = 3.dp))
        }
        Text(description, color = theme.textSecondary, fontFamily = ObFonts.raleway, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        if (essential && !granted) Text(essentialNote ?: "", color = ObColors.Red.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        extra?.let { Text(it, color = theme.text, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        when {
            granted -> {
                Text(if (essential) "🟢 CONNECTÉ ✓" else if (emoji == "🔋") "🟢 OPTIMISÉ ✓" else "🟢 ACTIVÉ ✓", color = ObColors.Green, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                PoeticText(confirmed, ObColors.Gold, 14, align = TextAlign.Start)
            }
            skipped -> Text("⚠️ Passé — tu pourras activer ça dans ⚙️", color = theme.textSecondary, fontSize = 12.sp)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (essential) "🔴 NON CONNECTÉ" else "⚪ EN ATTENTE", color = if (essential) ObColors.Red else theme.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                onSkip?.let { TextButton(onClick = it) { Text("Passer ↓", color = theme.textSecondary, fontSize = 12.sp) } }
                Box(Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(theme.primary, theme.secondary))).clickable { onGrant() }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    Text(buttonLabel.uppercase(), color = Color.White, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}
