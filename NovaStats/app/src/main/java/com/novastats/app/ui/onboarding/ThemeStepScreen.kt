package com.novastats.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.NovaThemes
import kotlin.math.abs

/**
 * Étape 1/4 — Carrousel 3D (coverflow) d'appareils, un par thème, chacun affichant l'app dans son univers avec
 * son fond animé réel. La scène entière (fond, halo, ambiance) bascule vers le thème centré.
 */
@Composable
fun ThemeStepScreen(audio: ObAudio, initial: NovaTheme, onThemeSelected: (NovaTheme) -> Unit, onNext: (NovaTheme) -> Unit) {
    val themes = remember { NovaThemes.ALL }
    val startPage = remember { themes.indexOf(initial).coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startPage) { themes.size }
    val settled = themes[pager.settledPage]
    val tilt = rememberTilt()
    val beats = rememberBeats(3, startMs = 150, stepMs = 150)

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { i -> val t = themes[i]; if (i != startPage || t.id != initial.id) { audio.tick(0.12f); audio.tap() }; onThemeSelected(t) }
    }
    val base by animateColorAsState(settled.background, tween(600, easing = ObEasing), label = "base")
    val accent by animateColorAsState(settled.primary, tween(600, easing = ObEasing), label = "accent")

    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val cardW = (screenW * 0.46f).coerceAtMost(210.dp)
    val sidePad = (screenW - cardW) / 2

    ObStage(base = base, accent = accent, ambient = settled, ambientAlpha = 0.45f) {
        ObLayout(step = 1, accent = accent, bottom = {
            Appear(beats >= 3) { ObPrimaryButton("Choisir ${settled.name}", fill = settled.primary) { audio.tap(); audio.whoosh(); onNext(settled) } }
            Text("${pager.settledPage + 1} / ${themes.size}  ·  glisse pour explorer", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                Appear(beats >= 1) { ObEyebrow("Choisis ton univers", accent) }
                Spacer(Modifier.height(6.dp))
                Appear(beats >= 1) {
                    AnimatedContent(settled, transitionSpec = { (fadeIn(tween(300, 80, ObEasing)) + slideInVertically(tween(350, 80, ObEasing)) { 14 }) togetherWith (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -10 }) }, label = "name") { t ->
                        Column {
                            ObHeadline(t.name, size = 38)
                            Spacer(Modifier.height(8.dp))
                            ObSub(t.inspiration.ifBlank { t.effects }, size = 15)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Swatches(t)
                                Spacer(Modifier.width(12.dp))
                                Text("${t.titleFont} · ${t.bodyFont}", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Appear(beats >= 2, modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pager, pageSize = PageSize.Fixed(cardW), contentPadding = PaddingValues(horizontal = sidePad), pageSpacing = 0.dp,
                        beyondViewportPageCount = 2, modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val off = (pager.currentPage - page) + pager.currentPageOffsetFraction  // >0 : page à gauche du centre
                        val d = abs(off).coerceAtMost(2f)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            DeviceMockup(
                                themes[page], tilt = if (d < 0.5f) tilt else null, ambient = d < 1.5f, glow = d < 0.5f,
                                rotY = (off * 42f).coerceIn(-60f, 60f),
                                modifier = Modifier.fillMaxWidth().graphicsLayer {
                                    val s = 1f - 0.16f * d
                                    scaleX = s; scaleY = s
                                    alpha = 1f - 0.45f * d.coerceAtMost(1f)
                                    translationX = off * cardW.toPx() * 0.22f
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Tu pourras en changer à tout moment dans Réglages → Apparence.", color = ObColors.Gray2, fontFamily = ObFonts.inter, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp))
        }
    }
}
