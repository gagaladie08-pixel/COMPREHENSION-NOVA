package com.novastats.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Glyphes dessinés spécialement pour les neuf onglets du thème Survivor. */
enum class SurvivorGlyph {
    HOME, STATS, BILLBOARD, RECORDS, CERTIFICATIONS, HALL_OF_FAME, PANTHEON, AWARDS, SETTINGS, BRAND
}

/** Icônes vectorielles multicolores : chaque onglet possède une silhouette propre, pas un filtre Material. */
@Composable
internal fun SurvivorNavGlyph(
    glyph: SurvivorGlyph,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = Nova.theme
    Canvas(modifier.size(24.dp).semantics { contentDescription = description }) {
        val unit = size.minDimension / 24f
        val offsetX = (size.width - 24f * unit) / 2f
        val offsetY = (size.height - 24f * unit) / 2f
        val pink = theme.primary
        val blue = theme.secondary
        val violet = theme.glowSecondary
        val mint = theme.accent
        val foreground = if (selected) theme.text else theme.textSecondary
        val fillAlpha = if (selected) 0.82f else 0.62f

        fun xy(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(offsetX + x * unit, offsetY + y * unit)
        fun outline(width: Float = 1.7f) = Stroke(width * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = foreground, width: Float = 1.7f) {
            drawLine(color, xy(x1, y1), xy(x2, y2), width * unit, cap = StrokeCap.Round)
        }
        fun circle(x: Float, y: Float, radius: Float, color: Color, width: Float? = null) {
            drawCircle(color, radius * unit, xy(x, y), style = width?.let { outline(it) } ?: androidx.compose.ui.graphics.drawscope.Fill)
        }
        fun rounded(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 1.5f) {
            drawRoundRect(
                color = color,
                topLeft = xy(x, y),
                size = androidx.compose.ui.geometry.Size(w * unit, h * unit),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * unit)
            )
        }
        fun polygon(points: List<Pair<Float, Float>>): Path = Path().apply {
            val first = points.first()
            moveTo(xy(first.first, first.second).x, xy(first.first, first.second).y)
            points.drop(1).forEach { (x, y) ->
                val point = xy(x, y)
                lineTo(point.x, point.y)
            }
            close()
        }
        fun star(x: Float, y: Float, outer: Float, inner: Float, color: Color) {
            val points = List(10) { i ->
                val angle = -PI / 2.0 + i * PI / 5.0
                val radius = if (i % 2 == 0) outer else inner
                (x + cos(angle).toFloat() * radius) to (y + sin(angle).toFloat() * radius)
            }
            drawPath(polygon(points), color)
        }

        when (glyph) {
            SurvivorGlyph.HOME -> {
                val house = polygon(listOf(4f to 10f, 12f to 4f, 20f to 10f, 18f to 10f, 18f to 20f, 6f to 20f, 6f to 10f))
                drawPath(house, pink.copy(alpha = if (selected) 0.34f else 0.22f))
                drawPath(house, foreground, style = outline())
                rounded(10f, 13f, 4f, 7f, blue.copy(alpha = fillAlpha), 1.2f)
                line(12f, 14f, 12f, 19f, Color.White.copy(alpha = 0.9f), 1f)
                star(19f, 5f, 2.1f, 0.9f, mint)
            }

            SurvivorGlyph.STATS -> {
                line(3.5f, 20f, 20.5f, 20f, foreground.copy(alpha = 0.8f), 1.2f)
                val bars = listOf(4.5f to 6f, 9.5f to 10f, 14.5f to 14f)
                val colors = listOf(blue, violet, pink)
                bars.forEachIndexed { index, (x, height) ->
                    rounded(x, 20f - height, 3.5f, height, colors[index].copy(alpha = fillAlpha), 1f)
                }
                line(5.7f, 12.2f, 11.2f, 8.3f, Color.White.copy(alpha = 0.85f), 1.1f)
                line(11.2f, 8.3f, 16.2f, 5f, mint, 1.1f)
                circle(5.7f, 12.2f, 1f, foreground)
                circle(11.2f, 8.3f, 1f, foreground)
                circle(16.2f, 5f, 1f, foreground)
            }

            SurvivorGlyph.BILLBOARD -> {
                rounded(3.5f, 3.5f, 17f, 13f, violet.copy(alpha = if (selected) 0.28f else 0.18f), 2.2f)
                drawRoundRect(
                    color = foreground,
                    topLeft = xy(3.5f, 3.5f),
                    size = androidx.compose.ui.geometry.Size(17f * unit, 13f * unit),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.2f * unit),
                    style = outline(1.35f)
                )
                line(7f, 17f, 17f, 17f, pink, 1.6f)
                line(10f, 17f, 9f, 20.5f, foreground, 1.4f)
                line(14f, 17f, 15f, 20.5f, foreground, 1.4f)
                line(7f, 20.5f, 17f, 20.5f, mint, 1.5f)
                star(12f, 9.5f, 3.7f, 1.7f, pink)
            }

            SurvivorGlyph.RECORDS -> {
                drawPath(polygon(listOf(7f to 13.5f, 6f to 21f, 10.3f to 18.8f, 12f to 22f, 13.4f to 14f)), blue.copy(alpha = fillAlpha))
                drawPath(polygon(listOf(12f to 14f, 14f to 18.8f, 18f to 21f, 17f to 13.5f)), violet.copy(alpha = fillAlpha))
                circle(12f, 10f, 7f, pink.copy(alpha = if (selected) 0.35f else 0.22f))
                circle(12f, 10f, 7f, foreground, 1.5f)
                circle(12f, 10f, 5.4f, mint.copy(alpha = 0.9f), 1f)
                star(12f, 10f, 3.7f, 1.7f, Color.White)
            }

            SurvivorGlyph.CERTIFICATIONS -> {
                val gem = polygon(listOf(3.5f to 8f, 7.5f to 4f, 16.5f to 4f, 20.5f to 8f, 12f to 20f))
                drawPath(gem, violet.copy(alpha = if (selected) 0.36f else 0.22f))
                drawPath(gem, foreground, style = outline())
                line(3.5f, 8f, 20.5f, 8f, mint, 1.3f)
                line(7.5f, 4f, 9.5f, 8f, foreground, 1.1f)
                line(16.5f, 4f, 14.5f, 8f, foreground, 1.1f)
                line(9.5f, 8f, 12f, 20f, blue, 1.1f)
                line(14.5f, 8f, 12f, 20f, pink, 1.1f)
            }

            SurvivorGlyph.HALL_OF_FAME -> {
                val hall = Path().apply {
                    moveTo(xy(4f, 20f).x, xy(4f, 20f).y)
                    lineTo(xy(4f, 11f).x, xy(4f, 11f).y)
                    cubicTo(5f * unit + offsetX, 7f * unit + offsetY, 8f * unit + offsetX, 4f * unit + offsetY, 12f * unit + offsetX, 4f * unit + offsetY)
                    cubicTo(16f * unit + offsetX, 4f * unit + offsetY, 19f * unit + offsetX, 7f * unit + offsetY, 20f * unit + offsetX, 11f * unit + offsetY)
                    lineTo(xy(20f, 20f).x, xy(20f, 20f).y)
                    close()
                }
                drawPath(hall, pink.copy(alpha = if (selected) 0.22f else 0.14f))
                drawPath(hall, foreground, style = outline())
                line(8f, 10.5f, 8f, 19f, blue, 1.7f)
                line(12f, 9.5f, 12f, 19f, mint, 1.7f)
                line(16f, 10.5f, 16f, 19f, violet, 1.7f)
                line(4f, 20f, 20f, 20f, foreground, 1.5f)
                star(12f, 6.5f, 1.7f, 0.7f, Color.White)
            }

            SurvivorGlyph.PANTHEON -> {
                val orbit = Path().apply {
                    moveTo(xy(4f, 9f).x, xy(4f, 9f).y)
                    cubicTo(7f * unit + offsetX, 4f * unit + offsetY, 17f * unit + offsetX, 4f * unit + offsetY, 20f * unit + offsetX, 9f * unit + offsetY)
                    cubicTo(17f * unit + offsetX, 20f * unit + offsetY, 7f * unit + offsetX, 20f * unit + offsetY, 4f * unit + offsetX, 9f * unit + offsetY)
                }
                drawPath(orbit, violet, style = outline(1.35f))
                line(6f, 17f, 18f, 7f, blue.copy(alpha = 0.9f), 1.2f)
                line(6f, 7f, 18f, 17f, pink.copy(alpha = 0.9f), 1.2f)
                circle(6f, 7f, 1.5f, blue)
                circle(18f, 7f, 1.5f, mint)
                circle(6f, 17f, 1.5f, pink)
                circle(18f, 17f, 1.5f, violet)
                star(12f, 12f, 4f, 1.8f, Color.White)
            }

            SurvivorGlyph.AWARDS -> {
                val cup = polygon(listOf(7f to 4f, 17f to 4f, 16f to 10f, 14.5f to 13f, 12f to 14.5f, 9.5f to 13f, 8f to 10f))
                drawPath(cup, mint.copy(alpha = if (selected) 0.8f else 0.58f))
                drawPath(cup, foreground, style = outline(1.5f))
                line(7f, 6f, 5f, 6f, pink, 1.6f)
                line(5f, 6f, 5f, 10f, pink, 1.6f)
                line(5f, 10f, 8.5f, 12f, pink, 1.6f)
                line(17f, 6f, 19f, 6f, blue, 1.6f)
                line(19f, 6f, 19f, 10f, blue, 1.6f)
                line(19f, 10f, 15.5f, 12f, blue, 1.6f)
                line(12f, 14.5f, 12f, 18f, foreground, 1.5f)
                line(8.5f, 20f, 15.5f, 20f, violet, 2f)
                rounded(9.5f, 18f, 5f, 1.7f, pink, 0.8f)
                star(12f, 8.5f, 2.3f, 1f, Color.White)
            }

            SurvivorGlyph.SETTINGS -> {
                val teeth = ArrayList<Pair<Float, Float>>(32)
                repeat(32) { index ->
                    val angle = -PI / 2.0 + index * PI / 16.0
                    val radius = if (index % 4 < 2) 9f else 7.2f
                    teeth += (12f + cos(angle).toFloat() * radius) to (12f + sin(angle).toFloat() * radius)
                }
                val gear = polygon(teeth)
                drawPath(gear, violet.copy(alpha = if (selected) 0.4f else 0.24f))
                drawPath(gear, foreground, style = outline(1.45f))
                circle(12f, 12f, 4.2f, theme.background)
                circle(12f, 12f, 4.2f, pink, 1.3f)
                circle(12f, 12f, 1.9f, blue)
                circle(12f, 12f, 0.8f, Color.White)
            }

            SurvivorGlyph.BRAND -> {
                val flame = Path().apply {
                    moveTo(xy(12f, 2.5f).x, xy(12f, 2.5f).y)
                    cubicTo(8f * unit + offsetX, 6f * unit + offsetY, 16f * unit + offsetX, 7f * unit + offsetY, 14f * unit + offsetX, 11f * unit + offsetY)
                    cubicTo(19f * unit + offsetX, 9f * unit + offsetY, 21f * unit + offsetX, 15f * unit + offsetY, 17f * unit + offsetX, 19f * unit + offsetY)
                    cubicTo(14f * unit + offsetX, 22f * unit + offsetY, 8f * unit + offsetX, 22f * unit + offsetY, 5.5f * unit + offsetX, 18f * unit + offsetY)
                    cubicTo(2.5f * unit + offsetX, 13f * unit + offsetY, 7f * unit + offsetX, 7f * unit + offsetY, 12f * unit + offsetX, 2.5f * unit + offsetY)
                    close()
                }
                drawPath(flame, pink.copy(alpha = if (selected) 0.44f else 0.28f))
                drawPath(flame, foreground, style = outline(1.35f))
                val inner = polygon(listOf(12f to 8f, 15.5f to 12f, 12f to 17f, 8.5f to 12f))
                drawPath(inner, blue.copy(alpha = 0.8f))
                star(12f, 12f, 2.5f, 1.1f, Color.White)
                circle(19f, 5.3f, 1f, mint)
            }
        }
    }
}
