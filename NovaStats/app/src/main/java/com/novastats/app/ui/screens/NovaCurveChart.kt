package com.novastats.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import com.novastats.app.ui.theme.drawNovaCurve
import com.novastats.app.ui.theme.rememberCurveAnim
import kotlin.math.abs

/**
 * POPUPS.md — graphique courbe unique, paramétrable, utilisé par Billboard, Certifications, Panthéon et Hall of Fame.
 * Le style du tracé (lissage, glow, dégradé, décor, arc-en-ciel, scan) vient du thème ([drawNovaCurve]) ;
 * ici on gère les axes, les ruptures (null), la grille, les seuils colorés, le ⭐ du peak et l'inversion de l'axe Y.
 *
 * @param values     valeurs dans l'ordre chronologique ; `null` = absence (rupture de segment)
 * @param color      couleur de trait contextuelle (ex. niveau de certification) — sinon celle du thème
 * @param invertY    positions de classement : la plus petite valeur (#1) en haut
 * @param gridValues lignes horizontales + libellé à gauche
 * @param thresholds lignes de seuil colorées (paliers de certification)
 * @param peakIndex  index du peak marqué d'une ⭐
 */
@Composable
fun NovaCurveChart(
    values: List<Float?>,
    modifier: Modifier,
    color: Color? = null,
    invertY: Boolean = false,
    minY: Float? = null,
    maxY: Float? = null,
    yLabel: (Float) -> String = { v -> if (invertY) "#${v.toInt()}" else v.toInt().toString() },
    gridValues: List<Float> = emptyList(),
    thresholds: List<Pair<Float, Color>> = emptyList(),
    peakIndex: Int? = null,
    showPoints: Boolean = false,
    endDot: Boolean = !invertY,
    /** Libellés de l'axe X alignés sur [values] (dates des périodes). null = pas de libellé. */
    xLabels: List<String?> = emptyList()
) {
    val theme = Nova.theme
    val textColor = theme.textSecondary
    val present = values.filterNotNull()
    val anim = rememberCurveAnim(theme, values.size)
    if (present.isEmpty()) return
    // Marge en tête : sans elle une courbe cumulative colle au bord haut et paraît plate.
    val dataLo = present.min()
    val dataHi = maxOf(present.max(), (thresholds.maxOfOrNull { it.first } ?: 0f), (gridValues.maxOrNull() ?: 0f))
    val lo = minY ?: (if (invertY) 1f else 0f).coerceAtMost(dataLo)
    val hi = maxY ?: maxOf(dataHi, lo + 1f) + (dataHi - lo).coerceAtLeast(1f) * 0.06f
    val stroke = color ?: theme.chart.stroke?.let { theme.color(it) } ?: theme.primary

    Canvas(modifier) {
        val w = size.width; val h = size.height
        val padL = 36.dp.toPx(); val padT = 12.dp.toPx(); val padB = if (xLabels.any { it != null }) 17.dp.toPx() else 8.dp.toPx()
        val plotW = w - padL; val plotH = h - padT - padB
        val n = values.size.coerceAtLeast(1)
        fun x(i: Int) = padL + if (n == 1) plotW / 2 else plotW * i / (n - 1)
        fun y(v: Float): Float {
            val f = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
            return if (invertY) padT + plotH * f else padT + plotH * (1 - f)
        }
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(200, (textColor.red * 255).toInt(), (textColor.green * 255).toInt(), (textColor.blue * 255).toInt())
            textSize = 9.sp.toPx(); isAntiAlias = true
        }

        /* ---- Lignes horizontales : grille (fine) + seuils (pointillés colorés) ----
         * Les libellés sont triés par ordonnée puis espacés d'au moins 11 dp : avant, tous les paliers
         * d'une certification s'empilaient en bas à gauche et se recouvraient illisiblement. */
        data class HLine(val value: Float, val y: Float, val color: Color, val dashed: Boolean)
        val candidates = (gridValues.distinct().map { HLine(it, y(it), textColor, false) } +
            thresholds.map { (v, c) -> HLine(v, y(v), c, true) })
            .distinctBy { (it.value * 1000).toInt() to (it.y * 10).toInt() }
            .sortedBy { it.y }
        val minGap = 11.dp.toPx()
        val kept = mutableListOf<HLine>()
        for (line in candidates) {
            if (kept.none { abs(it.y - line.y) < minGap }) kept += line
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()), 0f)
        kept.forEach { line ->
            drawLine(
                line.color.copy(alpha = if (line.dashed) 0.55f else 0.18f),
                Offset(padL, line.y), Offset(w, line.y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = if (line.dashed) dash else null
            )
            drawContext.canvas.nativeCanvas.drawText(yLabel(line.value), 1.dp.toPx(), line.y + 3.dp.toPx(), paint)
        }

        // Segments continus (ruptures sur null)
        val segments = mutableListOf<List<Offset>>()
        var seg = mutableListOf<Offset>()
        values.forEachIndexed { i, v ->
            if (v != null) seg += Offset(x(i), y(v))
            else if (seg.isNotEmpty()) { segments += seg; seg = mutableListOf() }
        }
        if (seg.isNotEmpty()) segments += seg
        val baseline = padT + plotH
        // Peu de points : sans marqueurs on ne voit qu'un trait isolé, on ne comprend pas le graphique.
        val sparse = present.size <= 6
        segments.forEach { pts ->
            if (pts.size >= 2) drawNovaCurve(theme, pts, baselineY = baseline, anim = anim, strokeOverride = stroke, showPoints = showPoints || sparse)
            else drawCircle(stroke, radius = 3.5.dp.toPx(), center = pts.first())
        }
        if (endDot) segments.lastOrNull()?.lastOrNull()?.let {
            drawCircle(theme.background, radius = 5.5.dp.toPx(), center = it)
            drawCircle(stroke, radius = 3.5.dp.toPx(), center = it)
        }

        // ⭐ au peak
        peakIndex?.let { i ->
            val v = values.getOrNull(i) ?: return@let
            val c = Offset(x(i), y(v))
            drawCircle(NovaColors.Gold, radius = 7.dp.toPx(), center = c)
            val p = android.graphics.Paint().apply { textSize = 14.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
            drawContext.canvas.nativeCanvas.drawText("⭐", c.x, c.y - 9.dp.toPx(), p)
        }

        /* ---- Axe X : première période, milieu, dernière ---- */
        if (xLabels.any { it != null }) {
            val axisY = padT + plotH + 11.dp.toPx()
            val positions = listOf(0, (values.size - 1) / 2, values.size - 1).distinct().filter { it in xLabels.indices }
            positions.forEach { i ->
                val label = xLabels[i] ?: return@forEach
                val px = x(i)
                paint.textAlign = when (i) {
                    0 -> android.graphics.Paint.Align.LEFT
                    values.size - 1 -> android.graphics.Paint.Align.RIGHT
                    else -> android.graphics.Paint.Align.CENTER
                }
                val clamped = px.coerceIn(padL, w - 2.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(label, clamped, axisY, paint)
            }
            paint.textAlign = android.graphics.Paint.Align.LEFT
        }
    }
}
