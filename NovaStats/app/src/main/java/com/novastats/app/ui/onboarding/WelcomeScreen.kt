package com.novastats.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 🎬 Écran de bienvenue — 7 phases / ~10 s. Tap n'importe où pour passer.
 * Inspiration Renaissance / Alter Ego : noir profond, or, chrome, violet.
 */
@Composable
fun WelcomeScreen(audio: ObAudio, onEnter: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Phase 1 : noir + battement de cœur
        phase = 1
        repeat(2) { audio.play("heartbeat"); audio.heartbeat(); delay(500) }
        // Phase 2 : particules + dégradé
        phase = 2; audio.startAmbient(); delay(1500)
        // Phase 3 : logo gravé dans l'or
        phase = 3; audio.play("whoosh", 0.6f); delay(1500)
        // Phase 4 : typewriter
        phase = 4; delay(1500)
        // Phase 5 : 3 phrases
        phase = 5; delay(2000)
        // Phase 6 : renaissance
        phase = 6; audio.play("chord"); audio.doublePulse(); delay(1500)
        // Phase 7 : bouton
        phase = 7
    }

    val bg = Brush.verticalGradient(listOf(Color.Black, ObColors.DeepViolet))
    val pulse by rememberInfiniteTransition(label = "wp").animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "wpa")
    val rotation by animateFloatAsState(if (phase >= 3) 360f else 0f, tween(1400), label = "rot")

    Box(
        Modifier.fillMaxSize().background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (phase >= 7) onEnter() else phase = 7 }
    ) {
        // Phase 2+ : dégradé chrome/or/violet qui pulse + particules du centre
        if (phase >= 2) {
            Box(Modifier.fillMaxSize().alpha(pulse).background(Brush.radialGradient(listOf(ObColors.Gold.copy(alpha = 0.35f), ObColors.Violet.copy(alpha = 0.25f), ObColors.Silver.copy(alpha = 0.08f), Color.Transparent))))
            ParticleField(Modifier.fillMaxSize(), ObColors.welcomeParticles, mode = if (phase >= 6) ParticleMode.RAIN else ParticleMode.BURST, emitting = phase in 2..3 || phase >= 6, intensity = if (phase >= 6) 1f else 0.6f)
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // Phase 3 : logo NOVASTATS gravé dans l'or — rotation 3D
            AnimatedVisibility(visible = phase >= 3, enter = fadeIn(tween(600)) + scaleIn(tween(800), initialScale = 0.6f)) {
                Box(Modifier.graphicsLayer { rotationY = rotation; cameraDistance = 16f * density }.pulsingGlow(ObColors.Gold, 0.2f, 0.55f, 1400, 40.dp)) {
                    Text(
                        "NOVASTATS", fontFamily = ObFonts.cinzel, fontWeight = FontWeight.Black, fontSize = 38.sp, letterSpacing = 8.sp,
                        style = androidx.compose.ui.text.TextStyle(brush = Brush.linearGradient(listOf(ObColors.Gold, ObColors.Silver, ObColors.Gold)))
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            // Phase 4 : typewriter
            if (phase >= 4) TypewriterText("You're about to enter a new era", charDelayMs = if (phase == 4) 42 else 0) { t ->
                Text(t, color = Color.White.copy(alpha = 0.9f), fontFamily = ObFonts.cormorant, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = 3.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(30.dp))
            // Phase 5 : 3 phrases, 0.5 s d'écart, particule à gauche
            if (phase >= 5) listOf("Un espace où chaque note compte", "Où chaque écoute est une empreinte", "Où ta musique raconte ton histoire").forEachIndexed { i, s ->
                var shown by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) { delay(if (phase == 5) i * 500L else 0L); shown = 1 }
                AnimatedVisibility(visible = shown == 1, enter = fadeIn(tween(600))) {
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(ObColors.welcomeParticles[i]).pulsingGlow(ObColors.welcomeParticles[i], radiusDp = 6.dp))
                        Spacer(Modifier.size(10.dp))
                        Text(s, color = ObColors.Silver, fontFamily = ObFonts.raleway, fontWeight = FontWeight.Light, fontSize = 15.sp, letterSpacing = 2.sp)
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            // Phase 6 : explosion « Bienvenue dans ta renaissance »
            AnimatedVisibility(visible = phase >= 6, enter = scaleIn(tween(700), initialScale = 0.3f) + fadeIn(tween(400))) {
                Box(Modifier.pulsingGlow(ObColors.GoldGlow, 0.3f, 0.8f, 1000, 36.dp)) {
                    CinzelTitle("Bienvenue dans ta renaissance", ObColors.Gold, size = 28, letterSpacing = 2)
                }
            }
        }

        // Phase 7 : bouton slide up
        AnimatedVisibility(
            visible = phase >= 7, enter = slideInVertically(tween(700)) { it } + fadeIn(tween(500)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp)
        ) {
            ObButton("Entre dans ton ère", listOf(ObColors.Gold, ObColors.Violet, ObColors.Cyan), onClick = { audio.play("whoosh"); audio.tap(); onEnter() })
        }
        if (phase < 7) Text("Toucher pour passer", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp))
    }
}
