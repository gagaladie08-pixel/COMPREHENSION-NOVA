package com.novastats.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.NovaTheme
import kotlinx.coroutines.delay

/**
 * Étape 4/4 — Le rideau : noir, « TON ÈRE COMMENCE » en lettres qui se révèlent, l'état des connexions en une
 * ligne, puis le voile se dissout sur le vrai écran d'accueil (fondu géré par MainActivity).
 */
@Composable
fun FinalStepScreen(audio: ObAudio, theme: NovaTheme, perms: Permissions, guideCompleted: Boolean, onLaunch: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }   // 0 noir · 1 faisceau · 2 titre · 3 détails · 4 bouton
    var leaving by remember { mutableStateOf(false) }
    val tilt = rememberTilt()
    val accent = theme.primary

    LaunchedEffect(Unit) { delay(500); phase = 1; audio.whoosh(0.14f); delay(900); phase = 2 }
    LaunchedEffect(phase) { if (phase == 3) { delay(1600); phase = 4 } }
    val camera by animateFloatAsState(if (phase >= 2) 1f else 1.15f, tween(3000, easing = ObEasing), label = "cam")
    val tint by animateFloatAsState(if (phase >= 2) 1f else 0f, tween(2600, easing = ObEasing), label = "tint")
    val out by animateFloatAsState(if (leaving) 1f else 0f, tween(1300, easing = ObEasing), label = "out")
    LaunchedEffect(leaving) { if (leaving) { delay(1250); onLaunch() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Lueur de la couleur du thème qui monte du bas (le thème « prend » l'écran)
        Canvas(Modifier.fillMaxSize().alpha(tint).parallax(tilt, 8.dp)) {
            val c = Offset(size.width / 2, size.height * 0.95f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.06f), Color.Transparent), c, size.height * 0.8f), size.height * 0.8f, c)
        }
        LightSweep(accent, trigger = phase >= 1)

        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp).graphicsLayer { scaleX = camera; scaleY = camera; alpha = 1f - out },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            RiseIn(visible = phase >= 2, modifier = Modifier.parallax(tilt, 6.dp)) {
                Text("ÉTAPE 4 / 4", color = ObColors.Ivory.copy(alpha = 0.45f), fontFamily = ObFonts.body, fontSize = 10.sp, letterSpacing = 4.sp)
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.parallax(tilt, 20.dp).breathingGlow(accent, 0.08f, 0.22f, 4000, 90.dp)) {
                BlurInTitle("TON ÈRE COMMENCE", ObColors.Ivory, fontSize = 30, start = phase >= 2, perLetterMs = 70, letterSpacing = 5) { if (phase == 2) phase = 3 }
            }
            Spacer(Modifier.height(28.dp))
            RiseIn(visible = phase >= 3, modifier = Modifier.parallax(tilt, 10.dp)) { Hairline(ObColors.Ivory, Modifier.padding(horizontal = 120.dp)) }
            Spacer(Modifier.height(24.dp))
            RiseIn(visible = phase >= 3, delayMs = 200, modifier = Modifier.parallax(tilt, 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusWord("Détection", perms.listener, accent)
                    StatusWord("Batterie", perms.battery, accent)
                    StatusWord("Relance", perms.boot, accent)
                    if (guideCompleted) StatusWord("Constructeur", true, accent)
                }
            }
            Spacer(Modifier.height(18.dp))
            RiseIn(visible = phase >= 3, delayMs = 500) {
                PoeticText("Lance ta musique. NovaStats écrit la suite.", ObColors.IvoryDim, 15)
            }
        }

        RiseIn(visible = phase >= 4 && !leaving, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)) {
            ObButton("Entrer", listOf(accent), big = true) { leaving = true; audio.boom(); audio.impact() }
        }
        // Flash très doux de la couleur du thème au moment d'entrer, puis noir → MainActivity fond l'app par-dessus
        if (out > 0f) Box(Modifier.fillMaxSize().alpha(out * (1f - out) * 1.6f).background(accent))
    }
}

@Composable
private fun StatusWord(label: String, on: Boolean, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(5.dp).height(5.dp).background(if (on) accent else ObColors.Ivory.copy(alpha = 0.25f), androidx.compose.foundation.shape.CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(label.uppercase(), color = if (on) ObColors.Ivory else ObColors.Ivory.copy(alpha = 0.4f), fontFamily = ObFonts.body, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 2.sp)
    }
}
