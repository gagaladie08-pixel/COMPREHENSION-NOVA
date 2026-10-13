package com.novastats.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.NovaThemes
import kotlinx.coroutines.delay

/**
 * Ouverture « keynote » : une écoute fictive devient une entrée de classement sous les yeux de la personne.
 * La scène est interactive et entièrement locale; elle n'écrit aucune donnée dans la bibliothèque.
 */
@Composable
fun WelcomeScreen(
    audio: ObAudio,
    effects: OnboardingEffects,
    onEffectsChange: (OnboardingEffects) -> Unit,
    onEnter: () -> Unit
) {
    val theme = NovaThemes.DEFAULT
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
                ObTag("EXPÉRIENCE INTERACTIVE", theme.primary)
                Text("DÉMO · 0 donnée enregistrée", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 9.sp)
            }
            Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                CinematicExperienceCard(
                    theme = theme,
                    scene = scene,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = enter
                        translationY = (1f - enter) * 52f
                        scaleX = 0.96f + 0.04f * enter
                        scaleY = 0.96f + 0.04f * enter
                    }
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Appear(beats >= 2) { ObHeadline("Une écoute.\nUne empreinte.", size = 36, align = TextAlign.Center) }
                Appear(beats >= 3) {
                    ObSub("NovaStats transforme les titres détectés en classements qui racontent ton histoire.", size = 14, align = TextAlign.Center)
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun CinematicExperienceCard(theme: NovaTheme, scene: Int, modifier: Modifier = Modifier) {
    val reducedMotion = LocalOnboardingReducedMotion.current
    val fadeInMs = if (reducedMotion) 0 else 260
    val fadeOutMs = if (reducedMotion) 0 else 160
    val scaleInMs = if (reducedMotion) 0 else 320
    val scaleOutMs = if (reducedMotion) 0 else 190

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.48f)
            val radius = size.minDimension * 0.48f
            drawCircle(
                brush = Brush.radialGradient(listOf(theme.primary.copy(alpha = 0.22f), theme.secondary.copy(alpha = 0.07f), Color.Transparent), center, radius),
                radius = radius,
                center = center
            )
            repeat(3) { ring ->
                drawCircle(
                    color = theme.primary.copy(alpha = 0.07f - ring * 0.012f),
                    radius = radius * (0.68f + ring * 0.12f),
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
        ObGlowCard(
            accent = theme.primary,
            modifier = Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.92f)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("NOVA / DÉMO", color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.weight(1f))
                    Text("0${scene + 1} — 03", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(10.dp))
                AnimatedContent(
                    targetState = scene,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    transitionSpec = {
                        (fadeIn(tween(fadeInMs)) + scaleIn(tween(scaleInMs), initialScale = 0.95f)) togetherWith
                            (fadeOut(tween(fadeOutMs)) + scaleOut(tween(scaleOutMs), targetScale = 1.03f))
                    },
                    label = "cinematic-experience"
                ) { currentScene ->
                    when (currentScene) {
                        0 -> ListeningScene(theme)
                        1 -> DetectedScene(theme)
                        else -> RankingScene(theme)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(theme.primary))
                    Spacer(Modifier.width(7.dp))
                    Text("SCÈNE FICTIVE · RIEN N'EST AJOUTÉ", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontWeight = FontWeight.SemiBold, fontSize = 8.sp, letterSpacing = 0.7.sp)
                    Spacer(Modifier.weight(1f))
                    Text("NOVA STATS", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
private fun ListeningScene(theme: NovaTheme) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ObTag("01 · ÉCOUTE EN COURS", theme.primary)
        Spacer(Modifier.height(14.dp))
        DemoVinyl(theme, 128.dp)
        Spacer(Modifier.height(14.dp))
        Text("Morceau fictif", color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("Artiste de démonstration", color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.height(12.dp))
        DemoWaveform(theme.primary)
        Text("Le signal est repéré…", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 10.sp, letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun DetectedScene(theme: NovaTheme) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(126.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(Brush.radialGradient(listOf(theme.primary.copy(alpha = 0.36f), theme.primary.copy(alpha = 0.06f), Color.Transparent), center), size.minDimension * 0.5f, center)
                drawCircle(theme.primary.copy(alpha = 0.45f), size.minDimension * 0.39f, center, style = Stroke(width = 1.5.dp.toPx()))
            }
            Box(Modifier.size(76.dp).clip(CircleShape).background(theme.primary), contentAlignment = Alignment.Center) {
                Text("✓", color = ObColors.Black, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 42.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        ObTag("02 · TITRE RECONNU", theme.primary)
        Text("Morceau fictif", color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.padding(top = 12.dp))
        Text("Artiste de démonstration", color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.height(11.dp))
        Text("Métadonnées prêtes pour le classement", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RankingScene(theme: NovaTheme) {
    Column(Modifier.fillMaxSize().padding(horizontal = 2.dp), verticalArrangement = Arrangement.Center) {
        ObTag("03 · CLASSEMENT MIS À JOUR", theme.primary)
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#1", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 48.sp, letterSpacing = (-3).sp)
            Spacer(Modifier.width(12.dp))
            DemoVinyl(theme, 74.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Morceau fictif", color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Artiste démo", color = ObColors.Gray, fontFamily = ObFonts.inter, fontSize = 10.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TON CLASSEMENT", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.1.sp)
                Text("+1 écoute simulée", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(theme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text("↗", color = theme.primary, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = 19.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        listOf(0.94f, 0.69f, 0.47f).forEachIndexed { index, fraction ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("0${index + 1}", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.width(22.dp))
                Box(Modifier.fillMaxWidth(fraction).height(if (index == 0) 5.dp else 3.dp).clip(CircleShape).background(if (index == 0) theme.primary else ObColors.Gray2.copy(alpha = 0.42f)))
            }
        }
    }
}

@Composable
private fun DemoVinyl(theme: NovaTheme, diameter: Dp) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.49f
            drawCircle(Brush.radialGradient(listOf(theme.secondary, theme.primary.copy(alpha = 0.88f), ObColors.Black), center, radius), radius, center)
            repeat(5) { ring ->
                drawCircle(Color.White.copy(alpha = 0.08f), radius * (0.48f + ring * 0.085f), center, style = Stroke(width = 0.7.dp.toPx()))
            }
            drawCircle(theme.primary.copy(alpha = 0.42f), radius * 0.29f, center)
        }
        Box(Modifier.size(diameter * 0.42f).clip(RoundedCornerShape(diameter * 0.12f)).background(Brush.linearGradient(listOf(theme.primary, theme.secondary))), contentAlignment = Alignment.Center) {
            Text("♫", color = ObColors.Text, fontFamily = ObFonts.inter, fontWeight = FontWeight.Black, fontSize = (diameter.value * 0.22f).sp)
        }
    }
}

@Composable
private fun DemoWaveform(accent: Color) {
    val heights = listOf(10, 18, 12, 27, 16, 34, 19, 26, 11, 21, 14, 30, 16, 24, 10)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEachIndexed { index, height ->
            Box(Modifier.width(4.dp).height(height.dp).clip(CircleShape).background(accent.copy(alpha = if (index in 3..11) 0.92f else 0.34f)))
        }
    }
}
