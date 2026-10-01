package com.novastats.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.animation.core.animate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* =====================================================================================
 * Effets signature des 15 thèmes.
 *   ThemeFxHost { contenu }  → applique les effets « sur le contenu » (saturation, jitter)
 *                              + l'overlay dessiné par-dessus, déclenché par ThemeEvents.
 * Les effets continus (bulles, paillettes, pulsation, scanlines) sont volontairement légers.
 * ===================================================================================== */

/** Hôte des effets : à poser autour du contenu principal (NavHost). Capture aussi les taps (pass Initial, sans consommer). */
@Composable
fun ThemeFxHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val theme = Nova.theme
    val fx = rememberContentFx(theme)
    Box(
        modifier
            .pointerInput(theme.signature) {
                if (theme.signature == Signature.BW_FLASH) awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.any { it.changedToDown() }) ThemeEvents.tapped()
                    }
                }
            }
            .graphicsLayer { translationX = fx.dx; translationY = fx.dy; alpha = fx.alpha }
            .drawWithContent {
                if (fx.saturation < 0.99f) {
                    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(fx.saturation) }) }
                    drawIntoCanvas { c ->
                        c.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                        drawContent()
                        c.restore()
                    }
                } else drawContent()
            }
    ) {
        content()
        ThemeSignatureOverlay(theme, Modifier.fillMaxSize())
    }
}

/** Effets appliqués au contenu lui-même. */
class ContentFx {
    var dx by mutableFloatStateOf(0f)
    var dy by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(1f)
    var saturation by mutableFloatStateOf(1f)
}

@Composable
private fun rememberContentFx(theme: NovaTheme): ContentFx {
    val fx = remember { ContentFx() }
    val tab = ThemeEvents.tabTick
    val tap = ThemeEvents.tapTick
    // Reset quand le thème change
    LaunchedEffect(theme.id) { fx.dx = 0f; fx.dy = 0f; fx.alpha = 1f; fx.saturation = 1f }
    when (theme.signature) {
        Signature.BW_FLASH -> {
            // N&B instantané → couleur en 150 ms (ease-out-expo)
            LaunchedEffect(tap, tab) {
                if (tap + tab == 0) return@LaunchedEffect
                fx.saturation = 0f
                animate(0f, 1f, animationSpec = tween(theme.transitionMs * 2, easing = EaseOutExpo)) { v, _ -> fx.saturation = v }
            }
        }
        Signature.JUMP_CUT -> LaunchedEffect(tab) {
            if (tab == 0) return@LaunchedEffect
            fx.dx = 10f; fx.alpha = 0.6f; delay(45)
            fx.dx = -6f; fx.alpha = 1f; delay(45)
            fx.dx = 0f
        }
        Signature.RANDOM_GLITCH -> LaunchedEffect(Unit) {
            while (true) {
                delay(Random.nextLong(1200, 4200))
                repeat(Random.nextInt(1, 4)) {
                    fx.dx = Random.nextInt(-9, 10).toFloat(); fx.dy = Random.nextInt(-4, 5).toFloat(); fx.alpha = Random.nextDouble(0.55, 1.0).toFloat()
                    delay(Random.nextLong(30, 90))
                }
                fx.dx = 0f; fx.dy = 0f; fx.alpha = 1f
            }
        }
        Signature.SCANLINES_GLITCH -> LaunchedEffect(tab) {
            if (tab == 0) return@LaunchedEffect
            repeat(3) { fx.dx = Random.nextInt(-4, 5).toFloat(); delay(35) }
            fx.dx = 0f
        }
        else -> Unit
    }
    return fx
}

/* ===================================== Overlay ===================================== */

/** Horloge en secondes (frame-driven) pour les effets continus. */
@Composable
private fun rememberClock(): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t.floatValue = (it - start) / 1_000_000_000f }
    }
    return t
}

/** Progression 0→1 (re)lancée à chaque changement de [tick] ; reste à 1 au repos. */
@Composable
private fun rememberBurst(tick: Int, durationMs: Int, easing: androidx.compose.animation.core.Easing = FastOutSlowInEasing): Float {
    val a = remember { Animatable(1f) }
    LaunchedEffect(tick) { if (tick > 0) { a.snapTo(0f); a.animateTo(1f, tween(durationMs, easing = easing)) } }
    return a.value
}

/** Overlay signature du thème (dessiné par-dessus le contenu, jamais interactif). */
@Composable
fun ThemeSignatureOverlay(theme: NovaTheme, modifier: Modifier = Modifier) {
    val tab = ThemeEvents.tabTick
    val unlock = ThemeEvents.unlockTick
    val tap = ThemeEvents.tapTick
    when (theme.signature) {
        Signature.SCANLINES_GLITCH -> {
            val p = rememberBurst(tab, theme.transitionMs, LinearEasing)
            val bars = remember(tab) { List(7) { Triple(Random.nextFloat(), Random.nextFloat() * 0.05f + 0.005f, Random.nextFloat() * 0.6f - 0.3f) } }
            Canvas(modifier) {
                var y = 0f
                val step = 4.dp.toPx()
                while (y < size.height) { drawLine(Color.Black.copy(alpha = 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f); y += step }
                if (p < 1f) bars.forEach { (fy, fh, dx) ->
                    val a = (1f - p) * 0.6f
                    drawRect(theme.accent.copy(alpha = a * 0.5f), Offset(dx * size.width, fy * size.height), Size(size.width, fh * size.height))
                    drawRect(theme.primary.copy(alpha = a * 0.35f), Offset(-dx * size.width, fy * size.height + fh * size.height), Size(size.width, 2f))
                }
            }
        }
        Signature.CHROME_SWEEP -> {
            var periodic by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) { while (true) { delay(7000); periodic++ } }
            val p = rememberBurst(tab + periodic, 700, LinearOutSlowInEasing)
            val specks = remember { List(28) { Offset(Random.nextFloat(), Random.nextFloat()) } }
            Canvas(modifier) {
                if (p < 1f) {
                    val x = -0.5f * size.width + p * 2f * size.width
                    rotate(-20f) {
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), theme.accent.copy(alpha = 0.22f), Color.White.copy(alpha = 0.16f), Color.Transparent), startX = x - 120.dp.toPx(), endX = x + 120.dp.toPx()),
                            Offset(x - 120.dp.toPx(), -size.height), Size(240.dp.toPx(), size.height * 3))
                    }
                    specks.forEach { s ->
                        val d = abs(s.x * size.width - x) / (160.dp.toPx())
                        if (d < 1f) drawCircle(Color.White.copy(alpha = (1f - d) * 0.8f), 1.5.dp.toPx() + (1f - d) * 2.dp.toPx(), Offset(s.x * size.width, s.y * size.height))
                    }
                }
            }
        }
        Signature.JUMP_CUT -> {
            var frame by remember { mutableStateOf(0) }
            LaunchedEffect(tab) { if (tab > 0) { frame = 1; delay(45); frame = 2; delay(45); frame = 0 } }
            val cuts = remember(tab) { List(4) { Random.nextFloat() to Random.nextFloat() * 0.03f + 0.004f } }
            if (frame > 0) Canvas(modifier) {
                if (frame == 1) {
                    drawRect(Color.Black.copy(alpha = 0.85f))
                    cuts.forEach { (fy, fh) -> drawRect(Color.White.copy(alpha = 0.7f), Offset(0f, fy * size.height), Size(size.width, fh * size.height)) }
                } else {
                    cuts.forEach { (fy, fh) -> drawRect(theme.primary.copy(alpha = 0.5f), Offset(0f, fy * size.height), Size(size.width, fh * size.height * 0.5f)) }
                }
            }
        }
        Signature.GOLD_SHIMMER -> {
            var periodic by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) { while (true) { delay(5000); periodic++ } }
            val p = rememberBurst(tab + periodic, 900, FastOutSlowInEasing)
            Canvas(modifier) {
                if (p < 1f) {
                    val y = -0.3f * size.height + p * 1.6f * size.height
                    rotate(-12f) {
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent, theme.primary.copy(alpha = 0.10f), Color.White.copy(alpha = 0.14f), theme.primary.copy(alpha = 0.10f), Color.Transparent), startY = y - 70.dp.toPx(), endY = y + 70.dp.toPx()),
                            Offset(-size.width, y - 70.dp.toPx()), Size(size.width * 3, 140.dp.toPx()))
                    }
                }
            }
        }
        Signature.RISING_BUBBLES -> {
            val clock by rememberClock()
            val items = remember { List(16) { i -> floatArrayOf(Random.nextFloat(), Random.nextFloat(), 6f + Random.nextFloat() * 10f, 0.05f + Random.nextFloat() * 0.07f, if (i % 4 == 0) 1f else 0f, Random.nextFloat() * 6.28f) } }
            Canvas(modifier) {
                items.forEach { b ->
                    val yy = ((b[1] - clock * b[3]) % 1f + 1f) % 1f
                    val xx = b[0] + sin(clock * 1.3f + b[5]) * 0.025f
                    val c = Offset(xx * size.width, yy * size.height)
                    val r = b[2].dp.toPx()
                    if (b[4] > 0.5f) drawStar(c, r * 0.9f, theme.accent.copy(alpha = 0.55f), rotation = clock * 40f + b[5] * 57f)
                    else {
                        drawCircle(theme.primary.copy(alpha = 0.18f), r, c)
                        drawCircle(theme.primary.copy(alpha = 0.45f), r, c, style = Stroke(1.5.dp.toPx()))
                        drawCircle(Color.White.copy(alpha = 0.7f), r * 0.22f, c + Offset(-r * 0.35f, -r * 0.35f))
                    }
                }
            }
        }
        Signature.CURTAIN -> {
            val p = rememberBurst(tab, theme.transitionMs, FastOutSlowInEasing)
            if (p < 1f) Canvas(modifier) {
                val half = size.width / 2
                val w = half * (1f - p)
                val folds = Brush.horizontalGradient(0f to theme.primary, 0.25f to theme.surface, 0.5f to theme.primary, 0.75f to theme.surface, 1f to theme.primary)
                drawRect(folds, Offset(0f, 0f), Size(w, size.height))
                drawRect(folds, Offset(size.width - w, 0f), Size(w, size.height))
                drawRect(theme.accent.copy(alpha = 0.9f), Offset(w - 2.dp.toPx(), 0f), Size(2.dp.toPx(), size.height))
                drawRect(theme.accent.copy(alpha = 0.9f), Offset(size.width - w, 0f), Size(2.dp.toPx(), size.height))
                // Projecteur
                drawCircle(Brush.radialGradient(listOf(theme.glowSecondary.copy(alpha = 0.35f * (1f - p)), Color.Transparent), center = Offset(half, size.height * 0.3f), radius = size.width * 0.6f), size.width * 0.6f, Offset(half, size.height * 0.3f))
            }
        }
        Signature.BW_FLASH -> {
            val p = rememberBurst(tap + tab, theme.transitionMs * 2, EaseOutExpo)
            if (p < 1f) Canvas(modifier) { drawRect(Color.White.copy(alpha = (1f - p) * 0.35f)) }
        }
        Signature.CLOUD_FADE -> {
            val p = rememberBurst(tab, theme.transitionMs, LinearOutSlowInEasing)
            if (p < 1f) Canvas(modifier) {
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = (1f - p) * 0.85f), theme.primary.copy(alpha = (1f - p) * 0.25f), Color.Transparent), center = Offset(size.width / 2, size.height / 2), radius = size.width * (0.5f + p)))
            }
        }
        Signature.WARM_PULSE -> {
            val a by rememberInfiniteTransition(label = "warm").animateFloat(0.06f, 0.18f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "wa")
            Canvas(modifier) {
                val c = Offset(size.width / 2, size.height * 1.05f)
                drawCircle(Brush.radialGradient(listOf(theme.glowSecondary.copy(alpha = a), theme.primary.copy(alpha = a * 0.5f), Color.Transparent), center = c, radius = size.width * 0.9f), size.width * 0.9f, c)
            }
        }
        Signature.RANDOM_GLITCH -> {
            var slices by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
            LaunchedEffect(Unit) { while (true) { delay(Random.nextLong(2500, 7000)); slices = List(3) { Random.nextFloat() to Random.nextFloat() * 0.02f + 0.003f }; delay(70); slices = emptyList() } }
            if (slices.isNotEmpty()) Canvas(modifier) { slices.forEach { (fy, fh) -> drawRect(theme.primary.copy(alpha = 0.35f), Offset(0f, fy * size.height), Size(size.width, fh * size.height)) } }
        }
        Signature.CONFETTI -> {
            val clock by rememberClock()
            var startAt by remember { mutableStateOf(-10f) }
            LaunchedEffect(unlock) { if (unlock > 0) startAt = clock }
            val pieces = remember(unlock) { List(90) { floatArrayOf(Random.nextFloat(), -Random.nextFloat() * 0.3f, 0.25f + Random.nextFloat() * 0.35f, Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 0.1f, Random.nextInt(6).toFloat()) } }
            val t = clock - startAt
            if (t in 0f..2.2f) Canvas(modifier) {
                pieces.forEach { c ->
                    val x = (c[0] + sin(t * 3f + c[3]) * 0.02f + c[4] * t) * size.width
                    val y = (c[1] + c[2] * t * (1f + t * 0.4f)) * size.height
                    val col = NovaColors.Rainbow[c[5].toInt()].copy(alpha = (1f - (t / 2.2f)).coerceIn(0f, 1f))
                    rotate(c[3] + t * 240f, pivot = Offset(x, y)) { drawRect(col, Offset(x - 4.dp.toPx(), y - 2.5.dp.toPx()), Size(8.dp.toPx(), 5.dp.toPx())) }
                }
            }
        }
        Signature.SPARKLES -> {
            val clock by rememberClock()
            val sparks = remember { List(26) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 0.6f + Random.nextFloat() * 1.6f, Random.nextFloat() * 6.28f, Random.nextInt(3).toFloat()) } }
            Canvas(modifier) {
                sparks.forEach { s ->
                    val a = (sin(clock * s[2] * 2f + s[3]) * 0.5f + 0.5f)
                    if (a > 0.35f) {
                        val col = listOf(Color.White, theme.accent, theme.primary)[s[4].toInt()].copy(alpha = (a - 0.35f) / 0.65f * 0.9f)
                        drawStar(Offset(s[0] * size.width, s[1] * size.height), 2.dp.toPx() + a * 4.dp.toPx(), col, rotation = clock * 30f)
                    }
                }
            }
        }
        Signature.BLUE_FLASH -> {
            val p = rememberBurst(tab, theme.transitionMs, EaseOutExpo)
            if (p < 1f) Canvas(modifier) { drawRect(lerp(theme.glowSecondary, Color.White, p).copy(alpha = (1f - p) * 0.5f)) }
        }
        Signature.PATTERN_REVEAL -> {
            val p = rememberBurst(tab, theme.transitionMs * 3, LinearEasing)
            Canvas(modifier) {
                val cell = 36.dp.toPx()
                val cols = (size.width / cell).toInt() + 2
                val rows = (size.height / cell).toInt() + 2
                val total = (cols + rows).toFloat()
                val stroke = Stroke(1.2.dp.toPx())
                for (r in 0 until rows) for (c in 0 until cols) {
                    val order = (r + c) / total
                    // Révélation progressive : apparaît (0→0.5) puis s'efface (0.5→1) en diagonale
                    val local = if (p >= 1f) 0f else {
                        val rise = ((p * 2f) - order).coerceIn(0f, 1f)
                        val fall = ((p - 0.5f) * 2f - order).coerceIn(0f, 1f)
                        (rise - fall).coerceIn(0f, 1f)
                    }
                    val alpha = 0.035f + local * 0.30f
                    val cx = c * cell; val cy = r * cell
                    val d = Path().apply { moveTo(cx + cell / 2, cy); lineTo(cx + cell, cy + cell / 2); lineTo(cx + cell / 2, cy + cell); lineTo(cx, cy + cell / 2); close() }
                    drawPath(d, (if ((r + c) % 2 == 0) theme.glowSecondary else theme.primary).copy(alpha = alpha), style = stroke)
                    if (local > 0.6f) drawCircle(theme.accent.copy(alpha = (local - 0.6f) * 0.8f), 2.dp.toPx(), Offset(cx + cell / 2, cy + cell / 2))
                }
            }
        }
        Signature.DUAL_CONTRAST -> {
            val phase = ThemeEvents.dualPhase
            val p = rememberBurst(tab, theme.transitionMs, FastOutSlowInEasing)
            Canvas(modifier) {
                val light = if (phase) theme.secondary else theme.primary
                val dark = if (phase) theme.primary else theme.secondary
                val a = 0.07f + (1f - p) * 0.18f
                drawRect(Brush.verticalGradient(listOf(light.copy(alpha = a), Color.Transparent), startY = 0f, endY = size.height * 0.45f))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, dark.copy(alpha = a * 0.6f)), startY = size.height * 0.6f, endY = size.height))
                // Halo fin (ange) en haut, trait épais (cornes) en bas
                drawCircle(light.copy(alpha = 0.25f + (1f - p) * 0.4f), 46.dp.toPx(), Offset(size.width / 2, -14.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                drawRect(dark.copy(alpha = 0.35f + (1f - p) * 0.4f), Offset(0f, size.height - 3.dp.toPx()), Size(size.width, 3.dp.toPx()))
            }
        }
    }
}

/* ===================================== Modificateurs réutilisables ===================================== */

/** Shimmer doré traversant un badge / une pastille (Slay Queen) — no-op pour les autres thèmes. */
@Composable
fun Modifier.goldShimmer(enabled: Boolean = Nova.theme.signature == Signature.GOLD_SHIMMER): Modifier {
    if (!enabled) return this
    val x by rememberInfiniteTransition(label = "shimmer").animateFloat(-1f, 2f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "sx")
    val gold = NovaColors.Gold
    return drawWithContent {
        drawContent()
        val w = size.width
        drawRect(Brush.linearGradient(listOf(Color.Transparent, gold.copy(alpha = 0.35f), Color.White.copy(alpha = 0.45f), gold.copy(alpha = 0.35f), Color.Transparent),
            start = Offset(x * w - w * 0.3f, 0f), end = Offset(x * w + w * 0.3f, size.height)))
    }
}

