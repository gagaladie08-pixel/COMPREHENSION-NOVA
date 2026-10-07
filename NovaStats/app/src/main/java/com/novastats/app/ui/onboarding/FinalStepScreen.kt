package com.novastats.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.NovaTheme
import kotlinx.coroutines.delay

/**
 * Étape 4/4 — « Tout est prêt. » : l'appareil dans le thème choisi, l'état des connexions, un bouton.
 * Au départ : boom + impact, l'appareil avance vers l'écran, puis MainActivity fond sur l'accueil réel.
 */
@Composable
fun FinalStepScreen(audio: ObAudio, theme: NovaTheme, perms: Permissions, guideCompleted: Boolean, onLaunch: () -> Unit) {
    val tilt = rememberTilt()
    val beats = rememberBeats(4, startMs = 200, stepMs = 170)
    var leaving by remember { mutableStateOf(false) }
    val enter by animateFloatAsState(if (beats >= 1) 1f else 0f, tween(900, easing = ObEasing), label = "enter")
    val zoom by animateFloatAsState(if (leaving) 1f else 0f, tween(900, easing = ObEasing), label = "zoom")
    LaunchedEffect(leaving) { if (leaving) { delay(850); onLaunch() } }
    LaunchedEffect(Unit) { audio.whoosh(0.12f) }
    val accent = theme.primary

    ObStage(base = theme.background, accent = accent, ambient = theme, ambientAlpha = 0.35f) {
        ObLayout(step = 4, accent = accent, bottom = {
            Appear(beats >= 4 && !leaving) { ObPrimaryButton("Commencer", fill = accent) { leaving = true; audio.boom(); audio.impact() } }
        }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Appear(beats >= 2) { ObHeadline("Tout est prêt.", size = 44, align = TextAlign.Center) }
                Spacer(Modifier.height(10.dp))
                Appear(beats >= 3) { ObSub("Lance ta musique. NovaStats écrit la suite.", align = TextAlign.Center) }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                DeviceMockup(
                    theme, tilt = tilt, rotY = 18f - 18f * enter, rotX = 8f - 8f * enter,
                    modifier = Modifier.fillMaxWidth(0.5f).graphicsLayer {
                        val s = (0.86f + 0.14f * enter) * (1f + 1.6f * zoom)
                        scaleX = s; scaleY = s; alpha = enter * (1f - zoom); translationY = (1f - enter) * 80f
                    }
                )
            }
            Appear(beats >= 3) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                    Status("Détection", perms.listener); Spacer(Modifier.width(18.dp))
                    Status("Batterie", perms.battery); Spacer(Modifier.width(18.dp))
                    Status("Relance", perms.boot)
                    if (guideCompleted) { Spacer(Modifier.width(18.dp)); Status("Constructeur", true) }
                }
            }
        }
    }
}

@Composable
private fun Status(label: String, on: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ObCheck(on, 16.dp); Spacer(Modifier.width(6.dp))
        Text(label, color = if (on) ObColors.Text else ObColors.Gray2, fontFamily = ObFonts.inter, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    }
}
