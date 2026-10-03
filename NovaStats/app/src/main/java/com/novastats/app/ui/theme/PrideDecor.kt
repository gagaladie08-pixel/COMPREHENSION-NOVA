package com.novastats.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

/*
 * 🏳️‍🌈 Kit décoratif du thème Survivor — appliqué UNIQUEMENT quand `Nova.isPride` est vrai.
 * Les composants partagés (cartes, classements, popups, bandeaux, titres de section, fond) l'utilisent
 * pour que chaque onglet, page, sous-page et popup respire le thème : drapeaux arc-en-ciel, trans, bi,
 * gay, lesbien, pan, non-binaire et chevron Progress.
 */

/** Vrai si le thème actif est Survivor. */
val Nova.isPride: Boolean
    @Composable get() = theme.id == "survivor"

/** Drapeau associé à une position de classement : #1 arc-en-ciel, #2 trans, #3 bi, #4 gay, #5 lesbien, #6 pan, #7 non-binaire, puis ça tourne. */
fun prideFlagFor(position: Int): List<Color> = NovaColors.PrideFlags[((position - 1).coerceAtLeast(0)) % NovaColors.PrideFlags.size]

/** Pinceau horizontal du drapeau d'une position (pour colorer un numéro, un compteur…). */
fun prideBrushFor(position: Int): Brush = Brush.horizontalGradient(prideFlagFor(position))

/** Pinceau conique animé qui enchaîne tous les drapeaux (bordures de cartes et de popups). */
@Composable
fun rememberPrideSweep(periodMs: Int = 6000): Brush {
    val t by rememberInfiniteTransition(label = "prideSweep").animateFloat(0f, 1f, infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "ps")
    val n = NovaColors.PrideCycle.size
    val shifted = List(n + 1) { i -> NovaColors.PrideCycle[(i + (t * n).toInt()) % n] }
    return Brush.sweepGradient(shifted)
}

/** Bordure aux couleurs de tous les drapeaux (tournante) sur la forme donnée. */
fun Modifier.prideBorder(shape: Shape, brush: Brush, width: Dp = 1.2.dp): Modifier = drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(outline, brush, style = Stroke(width.toPx()))
}

/** Liseré vertical d'un drapeau sur le bord gauche d'une ligne (bandes empilées). */
fun Modifier.prideStripe(flag: List<Color>, width: Dp = 4.dp, alpha: Float = 0.95f): Modifier = drawBehind {
    val w = width.toPx(); val h = size.height / flag.size
    flag.forEachIndexed { i, c -> drawRect(c, Offset(0f, i * h), Size(w, h + 0.5f), alpha = alpha) }
}

/** Fond en bandes horizontales d'un drapeau, très transparent (fond de ligne de classement). */
fun Modifier.prideWash(flag: List<Color>, alpha: Float = 0.10f): Modifier = drawBehind {
    val h = size.height / flag.size
    flag.forEachIndexed { i, c -> drawRect(c, Offset(0f, i * h), Size(size.width, h + 0.5f), alpha = alpha) }
}

/** Ruban des 7 drapeaux côte à côte (arc-en-ciel, trans, bi, gay, lesbien, pan, non-binaire). */
@Composable
fun PrideRibbon(modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Row(modifier.fillMaxWidth().height(height)) {
        NovaColors.PrideFlags.forEach { flag ->
            Row(Modifier.weight(1f).fillMaxHeight()) { flag.forEach { c -> Box(Modifier.weight(1f).fillMaxHeight().background(c)) } }
        }
    }
}

/** Petit drapeau rectangulaire (bandes horizontales) — par exemple sous un titre de section. */
@Composable
fun MiniFlag(flag: List<Color>, width: Dp = 28.dp, height: Dp = 4.dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).drawBehind {
        val h = size.height / flag.size
        flag.forEachIndexed { i, c -> drawRect(c, Offset(0f, i * h), Size(size.width, h + 0.5f)) }
    })
}

/** Chevron Progress (blanc, rose, bleu, marron, noir) dessiné à gauche d'un bloc. */
fun Modifier.progressChevron(height: Dp = 24.dp, alpha: Float = 0.9f): Modifier = drawBehind {
    val h = height.toPx(); val cy = size.height / 2; val cw = h * 0.22f
    NovaColors.Progress.reversed().forEachIndexed { i, col ->
        val x = (NovaColors.Progress.size - 1 - i) * cw * 0.6f
        val p = Path().apply { moveTo(x, cy - h / 2); lineTo(x + cw * 2.2f, cy); lineTo(x, cy + h / 2); close() }
        drawPath(p, col, alpha = alpha)
    }
}

/**
 * Voile Survivor posé par-dessus le contenu de l'app : larges bandes diagonales de tous les drapeaux
 * (très transparentes) + quelques cœurs/confettis qui flottent lentement. Visible sur chaque onglet.
 */
@Composable
fun PrideVeil(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "veil").animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "va")
    val pieces = remember { List(18) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 3f + Random.nextFloat() * 4f, Random.nextFloat() * 6.28f, Random.nextInt(NovaColors.PrideConfetti.size).toFloat()) } }
    val bands = NovaColors.PrideFlags.flatten()
    Canvas(modifier) {
        // Bandes diagonales (−18°) sur toute la surface
        val bandH = size.height / 10f
        rotate(-18f) {
            val total = bands.size
            for (i in -8 until total + 8) {
                val c = bands[((i % total) + total) % total]
                drawRect(c, Offset(-size.width, i * bandH * 0.45f + (t * bandH * 0.45f * 7)), Size(size.width * 3, bandH * 0.45f), alpha = 0.035f)
            }
        }
        // Cœurs qui flottent
        pieces.forEach { p ->
            val y = ((p[1] - t) % 1f + 1f) % 1f
            val x = p[0] + sin(t * 6.28f * 2 + p[3]) * 0.02f
            val col = NovaColors.PrideConfetti[p[4].toInt()].copy(alpha = 0.10f + 0.08f * ((sin(t * 6.28f * 3 + p[3]) + 1f) / 2f))
            drawHeart(Offset(x * size.width, y * size.height), p[2].dp.toPx(), col)
        }
    }
}

/** Petit cœur plein centré en [c], « rayon » [r]. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + r)
        cubicTo(c.x - r * 1.6f, c.y - r * 0.2f, c.x - r * 0.9f, c.y - r * 1.3f, c.x, c.y - r * 0.5f)
        cubicTo(c.x + r * 0.9f, c.y - r * 1.3f, c.x + r * 1.6f, c.y - r * 0.2f, c.x, c.y + r)
        close()
    }
    drawPath(p, color, style = Fill)
}
