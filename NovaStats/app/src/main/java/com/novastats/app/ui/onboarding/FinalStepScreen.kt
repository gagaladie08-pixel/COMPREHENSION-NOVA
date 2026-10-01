package com.novastats.app.ui.onboarding

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.NovaTheme
import kotlinx.coroutines.delay

/**
 * 🎊 Étape 4/4 — Le Grand Final : genèse (noir → point → explosion → construction UI), carte d'identité,
 * première mission, bouton « LANCER MON ÈRE ». Variantes selon le nombre de permissions.
 */
@Composable
fun FinalStepScreen(audio: ObAudio, theme: NovaTheme, perms: Permissions, guideCompleted: Boolean, onLaunch: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }       // 0 noir, 1 point, 2 explosion, 3 UI, 4 complète
    var built by remember { mutableIntStateOf(0) }       // éléments d'UI construits (0..12)
    var cardSections by remember { mutableIntStateOf(0) } // 0..4
    val power = perms.count
    val particles = power > 0

    LaunchedEffect(Unit) {
        phase = 0; repeat(3) { audio.play("heartbeat", 0.9f); audio.heartbeat(); delay(340) }
        phase = 1; delay(1000)
        phase = 2; if (power > 0) { audio.play("boom"); audio.impact() }; delay(900)
        phase = 3; if (power > 0) audio.play("crystal", 0.8f)
        for (i in 1..12) { delay(110); built = i; if (i % 3 == 0) audio.tap() }
        phase = 4; if (power > 0) { audio.play("chord", if (power == 3) 1f else 0.6f); audio.doublePulse() }
        for (i in 1..4) { delay(500); cardSections = i }
    }

    val beat by rememberInfiniteTransition(label = "hb").animateFloat(0.6f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "hba")
    val wave by animateFloatAsState(if (phase >= 2) 1f else 0f, tween(900), label = "wave")
    val flash by animateFloatAsState(if (phase == 2) 1f else 0f, tween(if (phase == 2) 150 else 700), label = "flash")
    val bgAlpha by animateFloatAsState(if (phase >= 3) 1f else 0f, tween(1200), label = "bg")

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Encre des couleurs du thème qui se répand
        Box(Modifier.fillMaxSize().alpha(bgAlpha).background(theme.background))
        // Phase 1-2 : point de lumière + shockwave
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            if (phase == 1) { val r = 6f + 22f * beat; drawCircle(Brush.radialGradient(listOf(Color.White, theme.primary, Color.Transparent), c, r * 3), r * 3, c) }
            if (phase >= 2 && wave < 1f) {
                val r = wave * size.height
                drawCircle(Brush.sweepGradient(listOf(theme.primary, theme.secondary, Color.White, theme.primary), c), r, c, alpha = (1f - wave) * 0.8f, style = Stroke(40f * (1f - wave) + 2f))
                drawCircle(theme.secondary.copy(alpha = (1f - wave) * 0.4f), r * 0.7f, c, style = Stroke(6f))
            }
        }
        if (flash > 0f) Box(Modifier.fillMaxSize().alpha(flash).background(Brush.radialGradient(listOf(Color.White, theme.primary))))
        if (particles && phase >= 2) ParticleField(Modifier.fillMaxSize(), listOf(theme.primary, theme.secondary, ObColors.Gold), mode = if (phase == 2) ParticleMode.BURST else if (phase >= 4) ParticleMode.CONVERGE else ParticleMode.FLOAT, emitting = phase == 2 || phase >= 4, intensity = when (power) { 3 -> 1f; 2 -> 0.8f; else -> 0.5f })

        if (phase >= 3) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(14.dp))
            StepIndicator(4, theme.primary, theme.text)
            // Construction de l'UI brique par brique
            AnimatedVisibility(visible = built >= 1, enter = slideInVertically(tween(400)) { -it } + fadeIn()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("NovaStats", color = theme.primary, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f).pulsingGlow(theme.primary, 0.1f, 0.3f, radiusDp = 10.dp))
                    Text("⚙️")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("🏠", "📊", "🏆", "🏅", "💎", "🏛️", "👑", "🎵").forEachIndexed { i, e ->
                    AnimatedVisibility(visible = built >= 2 + i, enter = scaleIn(tween(250), initialScale = 0.3f) + fadeIn()) { Text(e, fontSize = 18.sp) }
                }
            }
            AnimatedVisibility(visible = built >= 11, enter = fadeIn(tween(500))) {
                Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(14.dp)).background(theme.surface).pulsingGlow(theme.primary, 0.1f, 0.3f, 1600, 10.dp).padding(14.dp)) {
                    Text("🎵 EN COURS DE LECTURE", color = theme.textSecondary, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("🎧 En attente de ta première écoute…", color = theme.text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    Text("Lance ta musique et regarde la magie opérer", color = theme.textSecondary, fontSize = 12.sp)
                }
            }
            AnimatedVisibility(visible = phase >= 4, enter = fadeIn(tween(800)) + scaleIn(tween(800), initialScale = 0.7f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CinzelTitle("TON ÈRE COMMENCE", theme.primary, size = 30, letterSpacing = 5, modifier = Modifier.padding(horizontal = 16.dp).pulsingGlow(theme.primary, 0.2f, 0.5f, radiusDp = 30.dp))
                    PoeticText("🎧 La scène est prête. À toi de jouer. ✨", ObColors.Gold, 17, Modifier.padding(top = 8.dp).pulsingGlow(ObColors.GoldGlow, 0.1f, 0.4f, radiusDp = 14.dp))
                    PoeticText(
                        when (power) { 3 -> "NovaStats est à pleine puissance."; 2 -> "NovaStats est presque au maximum."; 1 -> "NovaStats est fonctionnel."; else -> "⚠️ Sans détection, NovaStats attend ton autorisation dans ⚙️." },
                        if (power == 0) ObColors.Red else theme.textSecondary, 14, Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                }
            }

            // Carte d'identité NovaStats
            if (phase >= 4) IdentityCard(theme, perms, guideCompleted, cardSections)

            // Première mission
            AnimatedVisibility(visible = cardSections >= 4, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(16.dp)).background(theme.surface).border(1.dp, ObColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp)).padding(16.dp)) {
                    Text("🎯 TA PREMIÈRE MISSION", color = ObColors.Gold, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 15.sp)
                    PoeticText("« Écoute ta première chanson et regarde NovaStats prendre vie »", theme.text, 15, Modifier.padding(vertical = 8.dp), TextAlign.Start)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎵 0/1", color = theme.text, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(10.dp))
                        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(ObColors.OrbOff).pulsingGlow(ObColors.Red, 0.1f, 0.5f, 800, 4.dp))
                    }
                    Text("Lance n'importe quelle app musicale et observe la magie", color = theme.textSecondary, fontFamily = ObFonts.raleway, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("📎 Spotify · YouTube Music · Deezer · Apple Music · ou autre 🎶", color = theme.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("Récompense : 🏆 Premier Scrobble", color = ObColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedVisibility(visible = cardSections >= 4, enter = fadeIn(tween(700)) + scaleIn(tween(700), initialScale = 0.6f)) {
                Box(Modifier.padding(6.dp).clip(RoundedCornerShape(50)).rotatingBorder(listOf(theme.secondary, theme.glowSecondary, theme.secondary), 2.dp, 50.dp, 3200).padding(5.dp)) {
                    ObButton("Lancer mon ère", listOf(theme.primary, theme.secondary, theme.glowSecondary), big = true) { audio.play("chord"); audio.doublePulse(); onLaunch() }
                }
            }
            Text("Le service de détection ${if (perms.listener) "🟢 est actif — en écoute" else "🔴 attend ton autorisation"}", color = if (perms.listener) ObColors.Green else ObColors.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 40.dp))
        }
    }
}

@Composable
private fun IdentityCard(theme: NovaTheme, perms: Permissions, guideCompleted: Boolean, sections: Int) {
    val device = remember { "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}" }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(18.dp)).rotatingBorder(listOf(theme.primary, theme.secondary, theme.primary), 2.dp, 18.dp, 3000)
            .background(theme.surface.copy(alpha = 0.92f)).pulsingGlow(theme.primary, 0.08f, 0.25f, 2000, 14.dp).padding(16.dp)
    ) {
        Text("CARTE D'IDENTITÉ NOVASTATS", color = theme.textSecondary, fontFamily = ObFonts.rajdhani, letterSpacing = 3.sp, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(visible = sections >= 1, enter = slideInHorizontally(tween(400)) { -it } + fadeIn()) {
            CardLine("🎨 UNIVERS", theme.text) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${theme.emoji} ${theme.name}", color = theme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                    listOf(theme.primary, theme.secondary, theme.glowSecondary, theme.accent).forEach { c -> Box(Modifier.padding(end = 3.dp).size(10.dp).clip(RoundedCornerShape(50)).background(c).pulsingGlow(c, 0.2f, 0.6f, 900, 4.dp)) }
                }
            }
        }
        AnimatedVisibility(visible = sections >= 2, enter = slideInVertically(tween(400)) { -it / 2 } + fadeIn()) {
            CardLine("🔗 CONNEXIONS", theme.text) {
                Text("🎵 Détection ${dot(perms.listener)}   🔋 Batterie ${dot(perms.battery)}   🚀 Démarrage ${dot(perms.boot)}", color = theme.text, fontSize = 13.sp)
            }
        }
        AnimatedVisibility(visible = sections >= 3, enter = scaleIn(tween(400), initialScale = 0.9f) + fadeIn()) {
            CardLine("📱 APPAREIL", theme.text) { Text("$device · ${if (guideCompleted) "🟢 Guide complété" else "⚪ Guide à finir dans ⚙️"}", color = theme.text, fontSize = 13.sp) }
        }
        AnimatedVisibility(visible = sections >= 4, enter = fadeIn(tween(900))) {
            CardLine("📊 COMPTEUR", theme.text) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf("🎵 0", "⏱️ 0:00", "🎤 0", "💿 0").forEach { Text(it, color = ObColors.OrbOff.copy(alpha = 0.9f).let { if (theme.isLight) Color(0xFF999999) else it }, fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    }
                    PoeticText("Chaque compteur attend ta première note.", theme.textSecondary, 13, Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

private fun dot(on: Boolean) = if (on) "🟢" else "⚪"

@Composable
private fun CardLine(label: String, color: Color, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = color.copy(alpha = 0.7f), fontFamily = ObFonts.rajdhani, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
        Spacer(Modifier.height(3.dp))
        content()
    }
}
