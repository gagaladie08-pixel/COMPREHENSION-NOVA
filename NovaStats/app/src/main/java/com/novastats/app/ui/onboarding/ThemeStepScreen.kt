package com.novastats.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.NovaTheme
import com.novastats.app.ui.theme.NovaThemes
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
 * 🎨 Étape 1/4 — Choix du thème : preview live (vraie mini-UI), cartes à effet unique, fond qui suit le thème.
 * Le thème est appliqué immédiatement via [onThemeSelected] (persisté) ; [onNext] déclenche la transition.
 */
@Composable
fun ThemeStepScreen(audio: ObAudio, initial: NovaTheme, onThemeSelected: (NovaTheme) -> Unit, onNext: (NovaTheme) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    var burst by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val bg by animateColorAsState(selected.background, tween(500), label = "bg")
    val fill by animateFloatAsState(if (leaving) 1f else 0f, tween(300), label = "fill")
    val listState = rememberLazyListState()

    LaunchedEffect(selected) { burst = true; delay(500); burst = false }
    LaunchedEffect(Unit) { listState.animateScrollToItem(NovaThemes.ALL.indexOf(initial).coerceAtLeast(0)) }

    Box(Modifier.fillMaxSize().background(bg)) {
        ParticleField(Modifier.fillMaxSize(), listOf(selected.primary, selected.secondary, selected.glowSecondary), mode = ParticleMode.BURST, emitting = burst || leaving, intensity = if (leaving) 1f else 0.5f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            StepIndicator(1, selected.primary, selected.text)
            Spacer(Modifier.height(14.dp))
            CinzelTitle("Choisis ton univers", ObColors.Gold, size = 24, letterSpacing = 3)
            PoeticText("Chaque thème est une identité unique. Laquelle est la tienne ?", selected.textSecondary, size = 15, modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
            Spacer(Modifier.height(10.dp))

            LivePreview(selected, Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(200.dp))
            Spacer(Modifier.height(14.dp))

            LazyRow(state = listState, contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(NovaThemes.ALL, key = { it.id }) { t ->
                    val isSel = t.id == selected.id
                    val scale by animateFloatAsState(if (isSel) 1.15f else 0.92f, tween(200), label = "cs")
                    ThemeCard(t, isSel, Modifier.scale(scale).padding(vertical = 12.dp)) {
                        if (!isSel) { selected = t; onThemeSelected(t); audio.play("whoosh", 0.5f); audio.tap() }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(targetState = selected, transitionSpec = { (fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 }) togetherWith fadeOut(tween(120)) }, label = "name") { t ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                    Text("${t.emoji} ${t.name}", color = t.primary, fontFamily = ObFonts.cinzel, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 2.sp)
                    Text(themeDescriptions[t.id] ?: t.effects, color = t.textSecondary, fontFamily = ObFonts.raleway, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Light, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    Text(t.effects, color = t.textSecondary.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            ObButton("Choisir cet univers", listOf(selected.primary, selected.secondary, selected.glowSecondary), enabled = !leaving) {
                leaving = true; audio.play("chime"); audio.soft()
            }
            Text("Modifiable à tout moment dans ⚙️ → Apparence", color = selected.textSecondary.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp, bottom = 28.dp))
        }
        // Transition : l'écran se remplit de la couleur primary puis fade out brutal
        if (fill > 0f) Box(Modifier.fillMaxSize().alpha(fill).background(selected.primary))
    }
    LaunchedEffect(leaving) { if (leaving) { delay(650); onNext(selected) } }
}

/** Vraie mini-UI NovaStats rendue avec le thème (morphing 0.3 s entre thèmes). */
@Composable
fun LivePreview(t: NovaTheme, modifier: Modifier = Modifier) {
    val surface by animateColorAsState(t.surface, tween(300), label = "s")
    val primary by animateColorAsState(t.primary, tween(300), label = "p")
    val secondary by animateColorAsState(t.secondary, tween(300), label = "sec")
    val text by animateColorAsState(t.text, tween(300), label = "t")
    val textSec by animateColorAsState(t.textSecondary, tween(300), label = "ts")
    val background by animateColorAsState(t.background, tween(300), label = "b")
    val progress by rememberInfiniteTransition(label = "lp").animateFloat(0.55f, 0.95f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "lpa")
    val secs = (154 + (progress - 0.55f) / 0.4f * 48).toInt()
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(background).border(1.dp, primary.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).pulsingGlow(primary, 0.1f, 0.35f, 1800, 12.dp).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NovaStats", color = primary, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("⚙️", fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("🏠", "📊", "🏆", "🏅", "💎", "🏛️", "👑", "🎵").forEachIndexed { i, e -> Text(e, fontSize = 12.sp, modifier = Modifier.alpha(if (i == 0) 1f else 0.55f)) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(textSec.copy(alpha = 0.3f)))
        Spacer(Modifier.height(6.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(surface).padding(8.dp)) {
            Text("🎵 En cours — Blinding Lights", color = text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text("The Weeknd · Spotify", color = textSec, fontSize = 10.sp)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(textSec.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(progress).height(5.dp).background(Brush.horizontalGradient(listOf(primary, secondary))))
            }
            Text("${secs / 60}:${String.format("%02d", secs % 60)} / 3:22", color = textSec, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Aujourd'hui", color = textSec, fontSize = 10.sp, letterSpacing = 1.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("🎵 47", "⏱️ 2h34", "🎤 12", "💿 8").forEach { Text(it, color = primary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ThemeCard(t: NovaTheme, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.width(120.dp).height(150.dp).clip(RoundedCornerShape(14.dp)).background(t.surface)
            .then(if (selected) Modifier.rotatingBorder(listOf(t.primary, t.secondary, t.glowSecondary), 2.dp, 14.dp) else Modifier.border(1.dp, t.textSecondary.copy(alpha = 0.3f), RoundedCornerShape(14.dp)))
            .clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxWidth().height(76.dp).background(t.background)) {
            ThemeEffect(t, selected, Modifier.fillMaxSize())
            // mini aperçu UI
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Box(Modifier.width(60.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(t.primary))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.secondary))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.width(50.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.textSecondary.copy(alpha = 0.6f)))
            }
            Text(t.emoji, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Column(Modifier.padding(8.dp)) {
            Text(t.name, color = t.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(t.primary, t.secondary, t.glowSecondary, t.accent).forEach { c -> Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(c)) }
            }
        }
    }
}

/** Effet unique par thème (15 effets), piloté par un temps 0..1 sur 4 s. */
@Composable
fun ThemeEffect(t: NovaTheme, selected: Boolean, modifier: Modifier) {
    val time by rememberInfiniteTransition(label = "fx").animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "fxa")
    val open by animateFloatAsState(if (selected) 0.35f else 0.12f, tween(500), label = "curtain")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun pr(i: Int, salt: Int = 0): Float { val x = sin((i * 12.9898f + salt * 78.233f).toDouble()) * 43758.5453; return (x - kotlin.math.floor(x)).toFloat() }
        when (t.id) {
            "cyber_nova" -> { // lignes holographiques qui scannent de haut en bas
                val y = (time * 1.3f % 1f) * h
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, t.accent.copy(alpha = 0.5f), Color.Transparent), startY = y - 18f, endY = y + 18f), topLeft = Offset(0f, y - 18f), size = Size(w, 36f))
                for (i in 0..6) drawLine(t.secondary.copy(alpha = 0.12f), Offset(0f, h * i / 6), Offset(w, h * i / 6), 1f)
            }
            "neon_disco" -> { // reflets de boule à facettes
                for (i in 0 until 8) { val a = time * 2 * PI.toFloat() + i * 0.8f; drawCircle(listOf(t.primary, t.secondary, t.glowSecondary)[i % 3].copy(alpha = 0.35f), 6f + 4f * pr(i), Offset(w / 2 + cos(a) * w * 0.4f, h / 2 + sin(a * 1.3f) * h * 0.4f)) }
            }
            "villain_era" -> { // glitch toutes les 2 s
                val g = (time * 2f) % 1f
                if (g < 0.12f) for (i in 0 until 5) { val y = pr(i, (time * 50).toInt()) * h; drawRect(t.primary.copy(alpha = 0.5f), Offset((pr(i, 3) - 0.5f) * 30f, y), Size(w, 4f + pr(i) * 6f)) ; drawRect(t.secondary.copy(alpha = 0.4f), Offset((pr(i, 7) - 0.5f) * 40f, y + 8f), Size(w, 2f)) }
            }
            "slay_queen" -> { // paillettes dorées qui tombent lentement
                for (i in 0 until 18) { val y = ((pr(i) + time * 0.5f) % 1f) * h; val x = pr(i, 1) * w; val tw = 0.5f + 0.5f * sin((time * 20 + i).toDouble()).toFloat(); drawCircle(ObColors.Gold.copy(alpha = 0.3f + 0.6f * tw), 1.5f + 2f * pr(i, 2), Offset(x, y)) }
            }
            "pink_y2k" -> { // bulles roses qui montent et éclatent
                for (i in 0 until 10) { val p = (pr(i) + time * 0.6f) % 1f; val y = h - p * h; val x = pr(i, 1) * w + sin((p * 6).toDouble()).toFloat() * 6f; val r = 4f + 6f * pr(i, 2); val a = if (p > 0.85f) (1f - p) / 0.15f else 1f; drawCircle(t.primary.copy(alpha = 0.5f * a), r * (if (p > 0.85f) 1f + (p - 0.85f) * 6f else 1f), Offset(x, y), style = Stroke(1.5f)) }
            }
            "velvet_stage" -> { // rideau qui s'ouvre légèrement
                val cw = w * (0.5f - open)
                drawRect(Brush.horizontalGradient(listOf(t.primary, t.secondary)), Offset(0f, 0f), Size(cw, h))
                drawRect(Brush.horizontalGradient(listOf(t.secondary, t.primary), startX = w - cw, endX = w), Offset(w - cw, 0f), Size(cw, h))
                drawCircle(t.glowSecondary.copy(alpha = 0.35f), h * 0.45f, Offset(w / 2, h * 0.3f))
            }
            "pink_venom" -> { // flash rouge/rose toutes les 3 s
                val f = (time * 4f / 3f) % 1f
                if (f < 0.08f) drawRect((if (f < 0.04f) t.secondary else t.primary).copy(alpha = 0.55f * (1f - f / 0.08f)))
                drawLine(t.primary.copy(alpha = 0.5f), Offset(0f, h * 0.7f), Offset(w, h * 0.7f), 1.5f)
            }
            "cloud_nine" -> { // nuages qui dérivent
                for (i in 0 until 3) { val x = ((pr(i) + time * 0.25f) % 1.3f - 0.15f) * w; val y = h * (0.2f + 0.25f * i); val c = Color.White.copy(alpha = 0.65f); drawCircle(c, 9f, Offset(x, y)); drawCircle(c, 12f, Offset(x + 10f, y - 3f)); drawCircle(c, 8f, Offset(x + 22f, y)) }
            }
            "solara" -> { // rayons de soleil qui tournent
                rotate(time * 360f, Offset(w * 0.8f, h * 0.25f)) { for (i in 0 until 12) rotate(i * 30f, Offset(w * 0.8f, h * 0.25f)) { drawLine(t.glowSecondary.copy(alpha = 0.25f), Offset(w * 0.8f, h * 0.25f), Offset(w * 0.8f, h * 0.25f - h * 1.2f), 5f) } }
                drawCircle(t.primary.copy(alpha = 0.9f), 10f, Offset(w * 0.8f, h * 0.25f))
            }
            "chaos_born" -> { // éléments qui bougent de manière imprévisible
                val step = (time * 8).toInt()
                for (i in 0 until 6) { val x = pr(i, step) * w; val y = pr(i + 9, step) * h; val s = 4f + 10f * pr(i, step + 1); if (i % 2 == 0) drawRect(t.primary.copy(alpha = 0.6f), Offset(x, y), Size(s, s)) else drawCircle(t.glowSecondary.copy(alpha = 0.5f), s / 2, Offset(x, y)) }
            }
            "survivor" -> { // confettis multicolores en continu
                val cols = listOf(t.primary, t.secondary, t.glowSecondary, t.accent, ObColors.Gold)
                for (i in 0 until 20) { val p = (pr(i) + time) % 1f; val x = pr(i, 1) * w + sin((p * 10 + i).toDouble()).toFloat() * 8f; rotate(p * 720f, Offset(x, p * h)) { drawRect(cols[i % cols.size].copy(alpha = 0.85f), Offset(x - 3f, p * h - 2f), Size(6f, 4f)) } }
            }
            "rainbow_pop" -> { // dégradé arc-en-ciel qui pulse
                val a = 0.25f + 0.25f * sin((time * 2 * PI).toFloat())
                drawRect(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta).map { it.copy(alpha = a) }))
            }
            "pop_revolution" -> { // ondes sonores qui se propagent
                for (i in 0 until 4) { val p = (time + i * 0.25f) % 1f; drawCircle(t.primary.copy(alpha = (1f - p) * 0.6f), p * w * 0.6f, Offset(w / 2, h / 2), style = Stroke(2f)) }
                drawCircle(t.secondary, 5f, Offset(w / 2, h / 2))
            }
            "african_confessions" -> { // motifs textiles qui se dessinent progressivement
                val n = 10; val prog = time
                for (row in 0 until 3) { val y0 = h * (0.2f + 0.3f * row); var prev = Offset(0f, y0); for (i in 1..n) { val x = w * i / n; val y = y0 + (if (i % 2 == 0) -8f else 8f); val lim = (prog * n * 1.2f - row * 2); if (i.toFloat() <= lim) { drawLine((if (row == 1) t.glowSecondary else t.primary).copy(alpha = 0.8f), prev, Offset(x, y), 2f) }; prev = Offset(x, y) } }
                for (i in 0 until 4) if (i.toFloat() < prog * 5) drawRect(t.secondary.copy(alpha = 0.6f), Offset(w * (0.1f + 0.25f * i), h * 0.45f), Size(6f, 6f))
            }
            "bad_angel" -> { // alternance lumière / ombre dramatique
                val s = 0.5f + 0.5f * sin((time * 2 * PI).toFloat())
                drawRect(Color.White.copy(alpha = 0.35f * s), Offset(0f, 0f), Size(w / 2, h))
                drawRect(t.primary.copy(alpha = 0.45f * (1f - s)), Offset(w / 2, 0f), Size(w / 2, h))
                drawLine(ObColors.Gold.copy(alpha = 0.8f), Offset(w / 2, 0f), Offset(w / 2, h), 1.5f)
            }
            else -> drawRoundRect(t.primary.copy(alpha = 0.2f), cornerRadius = CornerRadius(8f))
        }
    }
}
