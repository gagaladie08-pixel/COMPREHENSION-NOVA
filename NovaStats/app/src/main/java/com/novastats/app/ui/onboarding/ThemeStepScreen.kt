package com.novastats.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.NovaFonts
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.NovaThemes
import com.novastats.app.ui.theme.PrideRibbon
import com.novastats.app.ui.theme.ThemeAmbient
import com.novastats.app.ui.theme.PrideVeil
import kotlinx.coroutines.delay
import kotlin.math.abs

val themeDescriptions = mapOf(
    "cyber_nova" to "L'ère digitale. Le futur de la musique est ici.",
    "neon_disco" to "La nuit t'appartient. Fais briller chaque écoute.",
    "villain_era" to "Pas de règles. Que ta musique.",
    "slay_queen" to "Chaque écoute est un couronnement.",
    "pink_y2k" to "La nostalgie réinventée. Cute mais puissante.",
    "velvet_stage" to "Ta musique mérite les projecteurs.",
    "pink_venom" to "Douce en surface. Mortelle en profondeur.",
    "cloud_nine" to "Là où la musique devient sérénité.",
    "solara" to "Chaleur, lumière et rythme. L'été à vie.",
    "chaos_born" to "Imprévisible. Unique. Comme toi.",
    "survivor" to "Tu as traversé tout ça. Célèbre chaque note.",
    "rainbow_pop" to "La vie est trop courte pour les couleurs ternes.",
    "pop_revolution" to "La musique change le monde. Commence par toi.",
    "african_confessions" to "Les racines qui font vibrer l'âme.",
    "bad_angel" to "Entre l'ombre et la lumière. Tu choisis."
)

/**
 * Étape 1/4 — Thèmes en plein écran : on glisse horizontalement, chaque thème occupe tout l'écran avec son
 * animation d'ambiance réelle, sa barre NOVASTATS, un classement factice dans sa police. Le nom glisse en
 * parallaxe plus lentement que le fond.
 */
@Composable
fun ThemeStepScreen(audio: ObAudio, initial: NovaTheme, onThemeSelected: (NovaTheme) -> Unit, onNext: (NovaTheme) -> Unit) {
    val themes = NovaThemes.ALL
    val startPage = remember { themes.indexOf(initial).coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startPage) { themes.size }
    var leaving by remember { mutableStateOf(false) }
    val current = themes[pager.currentPage]
    val bg by animateColorAsState(current.background, tween(600, easing = ObEasing), label = "bg")
    val tilt = rememberTilt()

    // Le thème s'applique dès qu'une page se stabilise (persisté) + un souffle discret
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { i -> val t = themes[i]; if (i != startPage || t.id != initial.id) { onThemeSelected(t); audio.whoosh(0.08f); audio.tap() } }
    }
    val fill by animateFloatAsState(if (leaving) 1f else 0f, tween(700, easing = ObEasing), label = "fill")
    LaunchedEffect(leaving) { if (leaving) { delay(750); onNext(current) } }

    Box(Modifier.fillMaxSize().background(bg)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val t = themes[page]
            // Décalage de la page (−1..1) pour la parallaxe du nom et le fondu entre deux thèmes
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            val focus = 1f - abs(offset).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.35f + 0.65f * focus }) {
                // Fond : couleur du thème + son animation d'ambiance (celle de l'app)
                Box(Modifier.fillMaxSize().background(t.background))
                if (t.id == "survivor") PrideVeil(Modifier.fillMaxSize()) else ThemeAmbient(t, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, t.background.copy(alpha = 0.55f), t.background.copy(alpha = 0.92f)), startY = 0f)))

                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    StepIndicator(1, t.primary, t.text)
                    Spacer(Modifier.height(18.dp))
                    MockApp(t, Modifier.padding(horizontal = 22.dp).graphicsLayer { translationX = offset * size.width * 0.15f })
                    Spacer(Modifier.weight(1f))
                    // Nom + inspiration, en parallaxe (plus lent que le fond)
                    Column(
                        Modifier.padding(horizontal = 28.dp).graphicsLayer { translationX = offset * size.width * 0.45f }.parallax(tilt, 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(t.name.uppercase(), color = t.primary, fontFamily = NovaFonts.family(t.titleFont), fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = 4.sp, textAlign = TextAlign.Center, lineHeight = 40.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(themeDescriptions[t.id] ?: t.inspiration, color = t.text.copy(alpha = 0.85f), fontFamily = NovaFonts.family(t.bodyFont), fontWeight = FontWeight.Light, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(t.inspiration, color = t.textSecondary.copy(alpha = 0.7f), fontFamily = NovaFonts.family(t.bodyFont), fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(132.dp))
                }
            }
        }

        // Pagination + bouton (fixes, au-dessus du pager)
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                themes.indices.forEach { i ->
                    val on = i == pager.currentPage
                    val w by animateFloatAsState(if (on) 18f else 5f, tween(400, easing = ObEasing), label = "dot")
                    Box(Modifier.height(5.dp).width(w.dp).clip(CircleShape).background(if (on) current.primary else current.text.copy(alpha = 0.25f)))
                }
            }
            Spacer(Modifier.height(18.dp))
            ObButton("Choisir ${current.name}", listOf(current.primary), enabled = !leaving) { leaving = true; audio.tick(); audio.success() }
            Text("${pager.currentPage + 1} / ${themes.size}  ·  glisse pour découvrir", color = current.textSecondary.copy(alpha = 0.6f), fontFamily = ObFonts.body, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 12.dp))
        }
        if (fill > 0f) Box(Modifier.fillMaxSize().alpha(fill).background(Color.Black))
    }
}

/** Aperçu réel : barre NOVASTATS du thème + classement factice de 3 lignes dans ses polices. */
@Composable
private fun MockApp(t: NovaTheme, modifier: Modifier = Modifier) {
    val title = NovaFonts.family(t.titleFont); val body = NovaFonts.family(t.bodyFont)
    val shape = RoundedCornerShape(t.cornerDp.coerceAtLeast(6).dp)
    Column(modifier.fillMaxWidth().clip(shape).background(t.surface.copy(alpha = 0.92f)).border(1.dp, t.primary.copy(alpha = 0.35f), shape)) {
        if (t.id == "survivor") PrideRibbon(height = 4.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(t.primary, t.accent))))
            Spacer(Modifier.width(10.dp))
            Text("NOVASTATS", style = TextStyle(fontFamily = title, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 2.sp, brush = Brush.horizontalGradient(listOf(t.primary, t.accent))))
            Spacer(Modifier.weight(1f))
            Text("00:19", color = t.text, fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, t.primary, t.accent, Color.Transparent))))
        listOf(Triple("#1", "Ton titre du moment", "1 254 ▶"), Triple("#2", "Celui que tu rejoues", "987 ▶"), Triple("#3", "Ta découverte", "641 ▶")).forEachIndexed { i, (pos, name, plays) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pos, color = if (i == 0) t.accent else t.text, fontFamily = title, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.width(34.dp))
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(t.secondary.copy(alpha = 0.6f), t.glowSecondary.copy(alpha = 0.6f)))))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = t.text, fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    Text("Artiste · Album", color = t.textSecondary, fontFamily = body, fontSize = 11.sp, maxLines = 1)
                }
                Text(plays, color = t.primary, fontFamily = body, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
