package com.novastats.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.NovaThemes
import kotlinx.coroutines.delay

/**
 * Accueil « keynote » : une écoute fictive se transforme en rang dans un classement. La démo est isolée de la
 * bibliothèque; elle ne crée aucune écoute et peut être relancée depuis le bouton prévu à cet effet.
 */
@Composable
fun WelcomeScreen(
    audio: ObAudio,
    effects: OnboardingEffects,
    onEffectsChange: (OnboardingEffects) -> Unit,
    onEnter: () -> Unit
) {
    val theme = NovaThemes.DEFAULT
    val tilt = rememberTilt()
    val beats = rememberBeats(4, startMs = 180, stepMs = 170)
    val reducedMotion = LocalOnboardingReducedMotion.current
    val reducedNow by rememberUpdatedState(reducedMotion)
    var replay by remember { mutableIntStateOf(0) }
    var scene by remember { mutableIntStateOf(0) }
    val enter by animateFloatAsState(if (beats >= 1) 1f else 0f, tween(obMotionDuration(800), easing = ObEasing), label = "enter")

    LaunchedEffect(reducedMotion) { if (reducedMotion) scene = 2 }
    LaunchedEffect(replay) {
        if (reducedNow) {
            scene = 2
            return@LaunchedEffect
        }
        scene = 0
        delay(950)
        if (reducedNow) { scene = 2; return@LaunchedEffect }
        scene = 1
        audio.tick(0.12f)
        delay(900)
        if (reducedNow) { scene = 2; return@LaunchedEffect }
        scene = 2
        audio.success()
    }

    ObStage(base = ObColors.Ink, accent = theme.primary) {
        ObLayout(step = null, accent = theme.primary, bottom = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Appear(beats >= 4 && scene >= 2) {
                    ObPrimaryButton("Continuer") { audio.tap(); audio.whoosh(); onEnter() }
                }
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ObLinkButton("Rejouer la démo", theme.primary) { replay++ }
                    OnboardingAccessibilityOptions(effects, theme.primary, onEffectsChange)
                }
            }
        }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(30.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ObTag("Démo interactive", theme.primary)
                Text("Aucune écoute enregistrée", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 10.sp)
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                CinematicPhone(
                    theme = theme,
                    scene = scene,
                    tilt = tilt,
                    modifier = Modifier.fillMaxHeight(0.96f).graphicsLayer {
                        alpha = enter
                        translationY = (1f - enter) * 72f
                        scaleX = 0.92f + 0.08f * enter
                        scaleY = 0.92f + 0.08f * enter
                    }
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Appear(beats >= 2) { ObHeadline("NovaStats.", size = 42, align = TextAlign.Center) }
                Appear(beats >= 3) {
                    ObSub("Ta musique. Ton histoire.\nChaque écoute devient un classement.", size = 15, align = TextAlign.Center)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CinematicPhone(theme: NovaTheme, scene: Int, tilt: androidx.compose.runtime.State<androidx.compose.ui.geometry.Offset>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val reducedMotion = LocalOnboardingReducedMotion.current
    val sceneInDuration = if (reducedMotion) 0 else 260
    val sceneOutDuration = if (reducedMotion) 0 else 160
    val sceneScaleInDuration = if (reducedMotion) 0 else 300
    val sceneScaleOutDuration = if (reducedMotion) 0 else 180
    Box(
        modifier.aspectRatio(0.51f).graphicsLayer {
            cameraDistance = 16f * density
            rotationY = if (reducedMotion) 0f else -10f + tilt.value.x * 5f
            rotationX = if (reducedMotion) 0f else 4f - tilt.value.y * 4f
        }
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(38.dp)).background(Color(0xFF151518)).border(1.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(38.dp)).padding(7.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(31.dp)).background(theme.background)) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("9:41", color = theme.text, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
                        Box(Modifier.width(42.dp).height(9.dp).clip(CircleShape).background(Color.Black))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(theme.primary))
                    }
                    Spacer(Modifier.height(17.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NOVA", color = theme.text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.4.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("STATS", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 1.4.sp)
                        Spacer(Modifier.weight(1f))
                        ObTag("EXEMPLE", theme.primary)
                    }
                    Spacer(Modifier.height(16.dp))
                    AnimatedContent(
                        targetState = scene,
                        transitionSpec = {
                            (fadeIn(tween(sceneInDuration)) + scaleIn(tween(sceneScaleInDuration), initialScale = 0.96f)) togetherWith
                                (fadeOut(tween(sceneOutDuration)) + scaleOut(tween(sceneScaleOutDuration), targetScale = 1.02f))
                        },
                        label = "demo-scene"
                    ) { currentScene ->
                        DemoScene(theme, currentScene)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Démonstration seulement · rien n'est ajouté à ta bibliothèque", color = theme.textSecondary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Medium, fontSize = 9.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun DemoScene(theme: NovaTheme, scene: Int) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(theme.surface.copy(alpha = 0.92f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Text(
            when (scene) {
                0 -> "01 · LECTURE EN COURS"
                1 -> "02 · TITRE RECONNU"
                else -> "03 · CLASSEMENT MIS À JOUR"
            },
            color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp
        )
        Spacer(Modifier.height(12.dp))
        if (scene < 2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(theme.primary, theme.secondary))), contentAlignment = Alignment.Center) {
                    Text("♫", color = theme.text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Morceau fictif", color = theme.text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Artiste de démonstration", color = theme.textSecondary, fontFamily = ObFonts.inter, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(15.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.22f))) {
                Box(Modifier.fillMaxWidth(if (scene == 0) 0.38f else 0.72f).height(4.dp).clip(CircleShape).background(theme.primary))
            }
            Spacer(Modifier.height(8.dp))
            Text(if (scene == 0) "Écoute simulée…" else "Une écoute fictive détectée", color = theme.textSecondary, fontFamily = ObFonts.inter, fontSize = 10.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#1", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 30.sp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(theme.secondary, theme.primary))), contentAlignment = Alignment.Center) {
                    Text("♫", color = theme.text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 23.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Morceau fictif", color = theme.text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Artiste de démonstration", color = theme.textSecondary, fontFamily = ObFonts.inter, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TON CLASSEMENT", color = theme.textSecondary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp)
                    Text("+1 écoute simulée", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.size(34.dp).clip(CircleShape).background(theme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text("↑", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            listOf(0.92f, 0.64f, 0.4f).forEachIndexed { index, fraction ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = theme.textSecondary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.width(12.dp))
                    Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(if (index == 0) theme.primary else theme.textSecondary.copy(alpha = 0.45f)))
                }
            }
        }
    }
}
