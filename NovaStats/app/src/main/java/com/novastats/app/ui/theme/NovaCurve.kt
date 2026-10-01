package com.novastats.app.ui.theme

import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/* =====================================================================================
 * Graphique courbe thématisé — Canvas + Path, Brush.verticalGradient pour le remplissage,
 * glow via BlurMaskFilter. Chaque thème définit son ChartStyle (trait, lissage, décor, glow).
 * ===================================================================================== */

/** État d'animation d'une courbe : [reveal] 0→1 (scan gauche→droite), [phase] 0→1 en boucle (clignotements / arc-en-ciel). */
@Immutable
data class CurveAnim(val reveal: Float, val phase: Float)

/** À appeler dans le composable qui dessine : relance le scan quand [key] change. */
@Composable
fun rememberCurveAnim(theme: NovaTheme, key: Any?): CurveAnim {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(key, theme.id) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(if (theme.chart.scanReveal) 1100 else NovaMotion.duration(theme) * 2, easing = if (theme.chart.scanReveal) LinearEasing else NovaMotion.easing(theme)))
    }
    val animated = theme.chart.deco != CurveDeco.NONE || theme.chart.rainbow || theme.chart.alternatingGlow || theme.signature == Signature.WARM_PULSE
    val phase by if (animated) rememberInfiniteTransition(label = "curve").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart), label = "cp")
                 else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    return CurveAnim(reveal.value, phase)
}

/**
 * Dessine la courbe [pts] (coordonnées déjà projetées, x croissant) avec le style du [theme].
 * [baselineY] = bas de la zone de tracé (remplissage) ; [strokeOverride] = couleur contextuelle
 * (ex. couleur du palier de certification) utilisée quand le thème ne fixe pas la clé du trait.
 */
fun DrawScope.drawNovaCurve(
    theme: NovaTheme,
    pts: List<Offset>,
    baselineY: Float,
    anim: CurveAnim = CurveAnim(1f, 0f),
    strokeOverride: Color? = null,
    showPoints: Boolean = false
) {
    if (pts.isEmpty()) return
    val st = theme.chart
    val stroke = strokeOverride ?: st.stroke?.let { theme.color(it) } ?: theme.primary
    val fillTop = (st.fillTop?.let { theme.color(it) } ?: stroke).copy(alpha = st.fillAlpha)
    val fillBottom = st.fillBottom?.let { theme.color(it).copy(alpha = st.fillAlpha * 0.6f) } ?: fillTop.copy(alpha = 0.02f)
    val minX = pts.first().x; val maxX = pts.last().x
    val revealX = if (st.scanReveal) minX + (maxX - minX) * anim.reveal else maxX + 1f
    val topY = pts.minOf { it.y }

    val shaped = if (st.shape == CurveShape.IRREGULAR) pts.mapIndexed { i, p ->
        val j = sin(i * 2.3f + p.x * 0.05f) * 2.5.dp.toPx()
        Offset(p.x, p.y + j)
    } else pts
    val line = buildCurvePath(shaped, st.shape)
    val fill = Path().apply { addPath(line); lineTo(shaped.last().x, baselineY); lineTo(shaped.first().x, baselineY); close() }

    val strokeW = st.strokeWidthDp.dp.toPx()
    val rainbow = if (st.rainbow) Brush.horizontalGradient(rainbowColors(anim.phase), startX = minX, endX = maxX) else null

    clipRect(right = revealX) {
        // Remplissage
        if (st.rainbow) drawPath(fill, rainbow!!, alpha = 0.28f)
        else drawPath(fill, Brush.verticalGradient(listOf(fillTop, fillBottom), startY = topY, endY = baselineY))

        // Glow
        if (st.glowDp > 0f) {
            val glowAlpha = if (st.alternatingGlow) 0.35f + 0.35f * abs(sin(anim.phase * 2 * PI.toFloat())) else if (st.spotlight) 0.55f else 0.45f
            drawGlowPath(line, stroke.copy(alpha = glowAlpha), strokeW * (if (st.spotlight) 2.2f else 1.4f), st.glowDp.dp.toPx())
            if (st.alternatingGlow) {
                // Moitié du tracé plus lumineuse (alternance gauche / droite)
                val leftBright = anim.phase < 0.5f
                val midX = (minX + maxX) / 2
                clipRect(left = if (leftBright) minX else midX, right = if (leftBright) midX else maxX + 1f) {
                    drawGlowPath(line, theme.secondary.copy(alpha = 0.5f), strokeW * 2f, st.glowDp.dp.toPx() * 1.6f)
                }
            }
        }

        // Trait
        val join = if (st.shape == CurveShape.ANGULAR || st.shape == CurveShape.IRREGULAR) StrokeJoin.Miter else StrokeJoin.Round
        val cap = if (st.shape == CurveShape.ANGULAR) StrokeCap.Butt else StrokeCap.Round
        if (rainbow != null) drawPath(line, rainbow, style = Stroke(width = strokeW, cap = cap, join = join))
        else drawPath(line, stroke, style = Stroke(width = strokeW, cap = cap, join = join))

        // Points
        if (showPoints) shaped.forEach { drawCircle(stroke, radius = strokeW * 1.3f, center = it) }

        // Décorations
        when (st.deco) {
            CurveDeco.NONE -> Unit
            CurveDeco.BLINK_DOTS -> shaped.forEachIndexed { i, p ->
                if (i % 2 == 0) {
                    val a = 0.3f + 0.7f * abs(sin((anim.phase + i * 0.17f) * 2 * PI.toFloat()))
                    drawCircle(Color.White.copy(alpha = a), radius = 2.2.dp.toPx(), center = p)
                    drawCircle(stroke.copy(alpha = a * 0.5f), radius = 4.5.dp.toPx(), center = p)
                }
            }
            CurveDeco.STARS -> shaped.forEachIndexed { i, p ->
                if (i % 3 == 0 || i == shaped.lastIndex) drawStar(p, 4.dp.toPx(), theme.accent, rotation = anim.phase * 90f)
            }
            CurveDeco.DISCO_DOTS -> shaped.forEachIndexed { i, p ->
                val a = 0.4f + 0.6f * abs(sin((anim.phase * 2f + i * 0.31f) * 2 * PI.toFloat()))
                val c = listOf(theme.accent, Color.White, theme.secondary)[i % 3]
                drawCircle(c.copy(alpha = a), radius = 2.5.dp.toPx(), center = p)
            }
        }
    }

    // Tête de scan (Cyber Nova)
    if (st.scanReveal && anim.reveal < 1f) {
        drawLine(stroke.copy(alpha = 0.9f), Offset(revealX, topY - 6.dp.toPx()), Offset(revealX, baselineY), strokeWidth = 1.5.dp.toPx())
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, stroke.copy(alpha = 0.25f)), startX = revealX - 24.dp.toPx(), endX = revealX), topLeft = Offset(revealX - 24.dp.toPx(), topY - 6.dp.toPx()), size = androidx.compose.ui.geometry.Size(24.dp.toPx(), baselineY - topY + 6.dp.toPx()))
    }
}

/** Trace lissé (Bézier par points milieux), anguleux ou très arrondi. */
fun buildCurvePath(pts: List<Offset>, shape: CurveShape): Path = Path().apply {
    if (pts.isEmpty()) return@apply
    moveTo(pts.first().x, pts.first().y)
    if (pts.size == 1) return@apply
    when (shape) {
        CurveShape.ANGULAR, CurveShape.IRREGULAR -> pts.drop(1).forEach { lineTo(it.x, it.y) }
        CurveShape.SMOOTH, CurveShape.VERY_ROUND -> {
            val tension = if (shape == CurveShape.VERY_ROUND) 0.5f else 0.35f
            for (i in 0 until pts.lastIndex) {
                val p0 = pts[maxOf(i - 1, 0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[minOf(i + 2, pts.lastIndex)]
                val c1 = Offset(p1.x + (p2.x - p0.x) * tension, p1.y + (p2.y - p0.y) * tension)
                val c2 = Offset(p2.x - (p3.x - p1.x) * tension, p2.y - (p3.y - p1.y) * tension)
                cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
            }
        }
    }
}

/** Glow : trait flouté (BlurMaskFilter, accéléré matériellement à partir d'Android 9) ou trait large translucide en repli. */
fun DrawScope.drawGlowPath(path: Path, color: Color, width: Float, blur: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = width
            strokeCap = android.graphics.Paint.Cap.ROUND
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blur.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    } else {
        drawPath(path, color.copy(alpha = color.alpha * 0.35f), style = Stroke(width = width + blur, cap = StrokeCap.Round))
    }
}

/** Petite étoile à 4 branches. */
fun DrawScope.drawStar(center: Offset, r: Float, color: Color, rotation: Float = 0f) {
    val p = Path().apply {
        moveTo(0f, -r); lineTo(r * 0.3f, -r * 0.3f); lineTo(r, 0f); lineTo(r * 0.3f, r * 0.3f)
        lineTo(0f, r); lineTo(-r * 0.3f, r * 0.3f); lineTo(-r, 0f); lineTo(-r * 0.3f, -r * 0.3f); close()
    }
    translate(center.x, center.y) { rotate(rotation, pivot = Offset.Zero) { drawPath(p, color) } }
}
