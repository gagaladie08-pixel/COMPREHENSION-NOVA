package com.novastats.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Ouverture (≈ 8 s, passable d'un tap) — noir total, un faisceau de lumière révèle NOVASTATS lettre par
 * lettre (flou → net), la caméra recule, une seule phrase, un bouton contour. Parallaxe au mouvement du téléphone.
 */
@Composable
fun WelcomeScreen(audio: ObAudio, onEnter: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }   // 0 noir · 1 faisceau · 2 titre · 3 phrase · 4 bouton
    val tilt = rememberTilt()

    LaunchedEffect(Unit) {
        delay(600); phase = 1; audio.whoosh(0.16f)
        delay(900); phase = 2
    }
    LaunchedEffect(phase) { if (phase == 3) { delay(1400); phase = 4 } }

    // « Caméra » : léger recul pendant l'apparition du titre
    val camera by animateFloatAsState(if (phase >= 2) 1f else 1.18f, tween(3200, easing = ObEasing), label = "cam")
    val haze by animateFloatAsState(if (phase >= 2) 1f else 0f, tween(2600, easing = ObEasing), label = "haze")

    Box(
        Modifier.fillMaxSize().background(ObColors.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (phase >= 4) onEnter() else phase = 4 }
    ) {
        // Brume lumineuse très douce au centre (profondeur)
        Canvas(Modifier.fillMaxSize().alpha(haze * 0.9f).parallax(tilt, 10.dp)) {
            val c = Offset(size.width / 2, size.height * 0.46f)
            drawCircle(Brush.radialGradient(listOf(ObColors.Ivory.copy(alpha = 0.10f), ObColors.Ivory.copy(alpha = 0.03f), Color.Transparent), c, size.width * 0.9f), size.width * 0.9f, c)
        }
        LightSweep(ObColors.Ivory, trigger = phase >= 1)

        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp).graphicsLayer { scaleX = camera; scaleY = camera },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.parallax(tilt, 22.dp).breathingGlow(ObColors.Ivory, 0.06f, 0.16f, 4200, 90.dp)) {
                BlurInTitle("NOVASTATS", ObColors.Ivory, fontSize = 44, start = phase >= 2, perLetterMs = 110, letterSpacing = 9) { if (phase == 2) phase = 3 }
            }
            Spacer(Modifier.height(26.dp))
            RiseIn(visible = phase >= 3, modifier = Modifier.parallax(tilt, 12.dp)) {
                Hairline(ObColors.Ivory, Modifier.padding(horizontal = 120.dp))
            }
            Spacer(Modifier.height(22.dp))
            RiseIn(visible = phase >= 3, delayMs = 250, modifier = Modifier.parallax(tilt, 8.dp)) {
                Text("Ta musique. Ton histoire.", color = ObColors.IvoryDim, fontFamily = ObFonts.body, fontWeight = FontWeight.Light, fontSize = 17.sp, letterSpacing = 4.sp)
            }
        }

        RiseIn(visible = phase >= 4, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) {
            ObButton("Commencer", listOf(ObColors.Ivory), ghost = true, onClick = { audio.whoosh(); audio.tap(); onEnter() })
        }
        if (phase in 1..3) Text("toucher pour passer", color = ObColors.Ivory.copy(alpha = 0.25f), fontFamily = ObFonts.body, fontSize = 10.sp, letterSpacing = 3.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
    }
}
