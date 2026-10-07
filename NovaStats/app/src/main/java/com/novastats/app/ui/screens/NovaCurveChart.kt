package com.novastats.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import com.novastats.app.ui.theme.drawNovaCurve
import com.novastats.app.ui.theme.rememberCurveAnim

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
    endDot: Boolean = !invertY
) {
    val theme = Nova.theme
    val textColor = theme.textSecondary
    val present = values.filterNotNull()
    val anim = rememberCurveAnim(theme, values.size)
    if (present.isEmpty()) return
    val lo = minY ?: (if (invertY) 1f else 0f).coerceAtMost(present.min())
    val hi = maxY ?: maxOf(present.max(), (thresholds.maxOfOrNull { it.first } ?: 0f), (gridValues.maxOrNull() ?: 0f)).coerceAtLeast(lo + 1f)
    val stroke = color ?: theme.chart.stroke?.let { theme.color(it) } ?: theme.primary

    Canvas(modifier) {
        val w = size.width; val h = size.height
        val padL = 34.dp.toPx(); val padT = 10.dp.toPx(); val padB = 8.dp.toPx()
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
        gridValues.distinct().forEach { g ->
            drawLine(textColor.copy(alpha = 0.2f), Offset(padL, y(g)), Offset(w, y(g)), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText(yLabel(g), 0f, y(g) + 3.dp.toPx(), paint)
        }
        thresholds.forEach { (v, c) ->
            drawLine(c.copy(alpha = 0.5f), Offset(padL, y(v)), Offset(w, y(v)), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(yLabel(v), 0f, y(v) + 3.dp.toPx(), paint)
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
        segments.forEach { pts ->
            if (pts.size >= 2) drawNovaCurve(theme, pts, baselineY = baseline, anim = anim, strokeOverride = stroke, showPoints = showPoints)
            else drawCircle(stroke, radius = 3.5.dp.toPx(), center = pts.first())
        }
        if (endDot) segments.lastOrNull()?.lastOrNull()?.let { drawCircle(stroke, radius = 4.dp.toPx(), center = it) }

        // ⭐ au peak
        peakIndex?.let { i ->
            val v = values.getOrNull(i) ?: return@let
            val c = Offset(x(i), y(v))
            drawCircle(NovaColors.Gold, radius = 7.dp.toPx(), center = c)
            val p = android.graphics.Paint().apply { textSize = 14.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
            drawContext.canvas.nativeCanvas.drawText("⭐", c.x, c.y - 9.dp.toPx(), p)
        }
    }
}
