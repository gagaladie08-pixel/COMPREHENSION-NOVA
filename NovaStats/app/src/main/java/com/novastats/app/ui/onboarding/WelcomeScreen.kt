package com.novastats.app.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novastats.app.ui.theme.NovaThemes

/**
 * Accueil « keynote » : l'appareil entre en 3D (rotation + montée), puis le titre monumental, la phrase, le bouton.
 * Rythme : tout est en place en 1,2 s.
 */
@Composable
fun WelcomeScreen(audio: ObAudio, onEnter: () -> Unit) {
    val theme = NovaThemes.DEFAULT
    val tilt = rememberTilt()
    val beats = rememberBeats(4, startMs = 200, stepMs = 180)
    LaunchedEffect(Unit) { audio.whoosh(0.12f) }
    val enter by animateFloatAsState(if (beats >= 1) 1f else 0f, tween(900, easing = ObEasing), label = "enter")

    ObStage(base = ObColors.Ink, accent = theme.primary) {
        ObLayout(step = null, accent = theme.primary, bottom = {
            Appear(beats >= 4) { ObPrimaryButton("Continuer") { audio.tap(); audio.whoosh(); onEnter() } }
        }) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                DeviceMockup(
                    theme, tilt = tilt,
                    rotY = -22f + 12f * enter, rotX = 6f - 4f * enter,
                    modifier = Modifier.fillMaxWidth(0.52f).graphicsLayer { alpha = enter; translationY = (1f - enter) * 120f; scaleX = 0.9f + 0.1f * enter; scaleY = 0.9f + 0.1f * enter }
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Appear(beats >= 2) { ObHeadline("NovaStats.", size = 46, align = TextAlign.Center) }
                Appear(beats >= 3) { ObSub("Ta musique. Ton histoire.\nChaque écoute devient un classement.", align = TextAlign.Center) }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
