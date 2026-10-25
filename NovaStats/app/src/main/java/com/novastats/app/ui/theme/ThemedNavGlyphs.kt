package com.novastats.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** Sémantique commune des pictogrammes de navigation; les silhouettes sont dessinées, pas des glyphes Material. */
enum class NovaTabGlyph {
    HOME, STATS, BILLBOARD, RECORDS, CERTIFICATIONS, HALL_OF_FAME, PANTHEON, AWARDS, SETTINGS, BRAND
}

/** Glyphes sémantiques réutilisables dont le dessin est réinterprété par la famille artistique de chaque thème. */
@Composable
internal fun ThemedNavGlyph(
    glyph: NovaTabGlyph,
    description: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val theme = Nova.theme
    val family = theme.icons
    Canvas(modifier.size(24.dp).semantics { contentDescription = description }) {
        val unit = size.minDimension / 24f
        val offsetX = (size.width - 24f * unit) / 2f
        val offsetY = (size.height - 24f * unit) / 2f
        val active = if (selected) 1f else 0.68f
        val primary = theme.primary
        val secondary = theme.secondary
        val accent = theme.accent
        val outlineColor = when (family) {
            IconStyle.ROYAL -> NovaColors.Gold
            IconStyle.STAGE -> theme.glowSecondary
            IconStyle.METAL -> Color(0xFFE6E6E6)
            IconStyle.POLAROID -> theme.background
            IconStyle.CLOUD -> if (selected) Color.White else theme.textSecondary
            else -> if (selected) theme.text else theme.textSecondary
        }
        val lineWidth = when (family) {
            IconStyle.BUBBLE, IconStyle.CLOUD -> 2.1f
            IconStyle.SPIKY, IconStyle.RAW -> 1.9f
            IconStyle.POLAROID, IconStyle.METAL -> 1.45f
            else -> 1.65f
        }
        val fillBrush = when (family) {
            IconStyle.CHROME_ROUND -> Brush.linearGradient(listOf(Color.White.copy(alpha = 0.92f), theme.accent.copy(alpha = 0.92f), Color(0xFF56515E), Color.White.copy(alpha = 0.76f)))
            IconStyle.METAL -> Brush.linearGradient(listOf(Color(0xFFC6C8D0), Color(0xFF3A3B43), Color(0xFF9A9BA4)))
            IconStyle.CANDY -> Brush.linearGradient(listOf(theme.primary, theme.glowSecondary, theme.secondary))
            IconStyle.POLAROID -> Brush.linearGradient(listOf(theme.primary, theme.secondary))
            IconStyle.DUAL -> Brush.linearGradient(listOf(theme.secondary, theme.primary))
            else -> Brush.linearGradient(listOf(theme.primary, theme.accent, theme.secondary))
        }

        fun xy(x: Float, y: Float) = Offset(offsetX + x * unit, offsetY + y * unit)
        fun stroke(width: Float = lineWidth) = Stroke(width * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = outlineColor, width: Float = lineWidth) {
            drawLine(color, xy(x1, y1), xy(x2, y2), width * unit, cap = StrokeCap.Round)
        }
        fun circle(x: Float, y: Float, radius: Float, color: Color, border: Color? = null, borderWidth: Float = 1.4f) {
            drawCircle(color, radius * unit, xy(x, y))
            if (border != null) drawCircle(border, radius * unit, xy(x, y), style = stroke(borderWidth))
        }
        fun rounded(x: Float, y: Float, w: Float, h: Float, color: Color, radius: Float = 1.6f) {
            drawRoundRect(color, xy(x, y), Size(w * unit, h * unit), CornerRadius(radius * unit))
        }
        fun polygon(points: List<Pair<Float, Float>>): Path = Path().apply {
            val first = xy(points.first().first, points.first().second)
            moveTo(first.x, first.y)
            points.drop(1).forEach { (x, y) -> xy(x, y).let { lineTo(it.x, it.y) } }
            close()
        }
        fun filled(points: List<Pair<Float, Float>>, alpha: Float = active * 0.72f) {
            val path = polygon(points)
            drawPath(path, fillBrush, alpha = alpha)
            drawPath(path, outlineColor.copy(alpha = 0.9f * active), style = stroke())
        }
        fun filledColor(points: List<Pair<Float, Float>>, color: Color) {
            val path = polygon(points)
            drawPath(path, color)
            drawPath(path, outlineColor.copy(alpha = 0.9f * active), style = stroke())
        }
        fun star(x: Float, y: Float, outer: Float, inner: Float, color: Color) {
            val points = List(10) { index ->
                val angle = -PI / 2.0 + index * PI / 5.0
                val radius = if (index % 2 == 0) outer else inner
                (x + cos(angle).toFloat() * radius) to (y + sin(angle).toFloat() * radius)
            }
            drawPath(polygon(points), color)
        }
        fun diamond(centerX: Float, centerY: Float, radius: Float, color: Color, lineOnly: Boolean = false) {
            val shape = polygon(listOf(centerX to centerY - radius, centerX + radius to centerY, centerX to centerY + radius, centerX - radius to centerY))
            if (lineOnly) drawPath(shape, color, style = stroke(1.15f)) else drawPath(shape, color)
        }

        // La matière du thème s'étend jusque dans le pictogramme (pas seulement autour de l'icône Material).
        when (family) {
            IconStyle.HUD -> {
                val color = theme.accent.copy(alpha = 0.34f + 0.55f * active)
                val span = 2.7f * unit
                listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).forEach { (sx, sy) ->
                    val p = xy(12f + sx * 10.6f, 12f + sy * 10.6f)
                    drawLine(color, p, Offset(p.x - sx * span, p.y), 1.1f * unit)
                    drawLine(color, p, Offset(p.x, p.y - sy * span), 1.1f * unit)
                }
                circle(12f, 12f, 9.4f, theme.surface.copy(alpha = 0.26f), theme.accent.copy(alpha = 0.25f * active), 0.8f)
            }
            IconStyle.CHROME_ROUND -> {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f), theme.accent.copy(alpha = 0.18f), Color.Transparent), xy(8f, 7f), 13f * unit),
                    10.4f * unit,
                    xy(12f, 12f)
                )
                drawCircle(theme.accent.copy(alpha = 0.22f * active), 10.4f * unit, xy(12f, 12f), style = stroke(1f))
                drawArc(Color.White.copy(alpha = 0.5f * active), 205f, 105f, false, xy(4.1f, 4.1f), Size(15.8f * unit, 15.8f * unit), style = stroke(0.8f))
            }
            IconStyle.RAW -> {
                val torn = polygon(listOf(3f to 6f, 7f to 4f, 13f to 5f, 20f to 3.5f, 21f to 16f, 17f to 20f, 11f to 19f, 4f to 21f))
                drawPath(torn, theme.primary.copy(alpha = 0.13f + 0.1f * active))
                line(4f, 5f, 7f, 4f, theme.text.copy(alpha = 0.5f), 0.8f)
                line(17f, 19f, 20f, 17f, theme.accent.copy(alpha = 0.75f), 0.9f)
            }
            IconStyle.ROYAL -> {
                circle(12f, 12f, 10f, NovaColors.Gold.copy(alpha = 0.06f + 0.08f * active), NovaColors.Gold.copy(alpha = 0.45f * active), 1f)
                val crown = polygon(listOf(7f to 6f, 9.4f to 8.2f, 12f to 5.7f, 14.7f to 8.2f, 17f to 6f, 16f to 10f, 8f to 10f))
                drawPath(crown, NovaColors.Gold.copy(alpha = 0.62f * active))
                for (x in listOf(7f, 12f, 17f)) circle(x, 6f, 0.55f, Color.White.copy(alpha = active))
            }
            IconStyle.BUBBLE -> {
                circle(6f, 7f, 3.2f, theme.secondary.copy(alpha = 0.2f), theme.primary.copy(alpha = 0.5f * active), 0.8f)
                circle(18f, 18f, 2.5f, theme.accent.copy(alpha = 0.18f), theme.glowSecondary.copy(alpha = 0.55f * active), 0.8f)
                circle(5f, 6f, 0.7f, Color.White.copy(alpha = 0.7f * active))
            }
            IconStyle.STAGE -> {
                drawCircle(Brush.radialGradient(listOf(theme.glowSecondary.copy(alpha = 0.21f * active), Color.Transparent), xy(12f, 5f), 11f * unit), 11f * unit, xy(12f, 5f))
                line(2.5f, 21f, 21.5f, 21f, NovaColors.Gold.copy(alpha = 0.65f * active), 1f)
                line(4f, 3f, 8f, 7f, theme.primary.copy(alpha = 0.5f), 0.9f)
                line(20f, 3f, 16f, 7f, theme.primary.copy(alpha = 0.5f), 0.9f)
            }
            IconStyle.SPIKY -> {
                val burst = Path()
                for (index in 0 until 16) {
                    val angle = -PI / 2.0 + index * PI / 8.0
                    val radius = if (index % 2 == 0) 10.5f else 8.7f
                    val point = xy(12f + cos(angle).toFloat() * radius, 12f + sin(angle).toFloat() * radius)
                    if (index == 0) burst.moveTo(point.x, point.y) else burst.lineTo(point.x, point.y)
                }
                burst.close()
                drawPath(burst, theme.primary.copy(alpha = 0.18f + 0.1f * active))
            }
            IconStyle.CLOUD -> {
                circle(8f, 7f, 4f, Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.48f * active), 0.8f)
                circle(14f, 6.5f, 4.8f, theme.primary.copy(alpha = 0.11f), Color.White.copy(alpha = 0.45f * active), 0.8f)
                circle(17.5f, 9f, 3.3f, theme.secondary.copy(alpha = 0.11f), Color.White.copy(alpha = 0.42f * active), 0.8f)
            }
            IconStyle.SUN -> {
                val halo = Brush.radialGradient(listOf(theme.glowSecondary.copy(alpha = 0.34f * active), theme.primary.copy(alpha = 0.13f), Color.Transparent), xy(12f, 12f), 12f * unit)
                drawCircle(halo, 12f * unit, xy(12f, 12f))
                repeat(8) { index ->
                    val angle = index * PI / 4.0
                    val start = xy(12f + cos(angle).toFloat() * 9.4f, 12f + sin(angle).toFloat() * 9.4f)
                    val end = xy(12f + cos(angle).toFloat() * 11.1f, 12f + sin(angle).toFloat() * 11.1f)
                    drawLine(theme.accent.copy(alpha = 0.45f + 0.45f * active), start, end, 1.1f * unit)
                }
            }
            IconStyle.METAL -> {
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF3A3A3A), Color(0xFF151515), Color(0xFF55545A))), xy(2.5f, 2.5f), Size(19f * unit, 19f * unit), CornerRadius(3f * unit))
                drawRoundRect(theme.primary.copy(alpha = 0.3f + 0.4f * active), xy(2.5f, 2.5f), Size(19f * unit, 19f * unit), CornerRadius(3f * unit), style = stroke(0.85f))
                listOf(4f to 4f, 20f to 4f, 4f to 20f, 20f to 20f).forEach { (x, y) -> circle(x, y, 0.85f, theme.glowSecondary) }
            }
            IconStyle.PRIDE -> Unit // Survivor possède son glyphe Progress dédié.
            IconStyle.CANDY -> {
                drawCircle(
                    Brush.linearGradient(listOf(theme.primary, theme.glowSecondary, theme.secondary), xy(3f, 3f), xy(21f, 21f)),
                    10.3f * unit,
                    xy(12f, 12f),
                    alpha = 0.22f * active
                )
                drawCircle(theme.accent.copy(alpha = 0.3f * active), 10.3f * unit, xy(12f, 12f), style = stroke(0.8f))
                drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f * active), Color.Transparent), 3f * unit, 11f * unit), 6f * unit, xy(9f, 7f))
            }
            IconStyle.POLAROID -> {
                drawRect(Color.White.copy(alpha = 0.92f), xy(3f, 2f), Size(18f * unit, 20f * unit))
                drawRect(theme.primary.copy(alpha = 0.16f + 0.1f * active), xy(5f, 4f), Size(14f * unit, 12.5f * unit))
                drawRect(theme.secondary.copy(alpha = 0.55f * active), xy(8f, 18f), Size(8f * unit, 1.2f * unit))
            }
            IconStyle.GEOMETRIC -> {
                diamond(12f, 12f, 10.4f, theme.glowSecondary.copy(alpha = 0.45f * active), lineOnly = true)
                diamond(12f, 12f, 8.2f, theme.primary.copy(alpha = 0.23f * active), lineOnly = true)
                diamond(12f, 12f, 5.8f, theme.accent.copy(alpha = 0.22f * active), lineOnly = true)
            }
            IconStyle.DUAL -> {
                drawArc(theme.secondary.copy(alpha = 0.46f * active), 95f, 170f, false, xy(2.5f, 2.5f), Size(19f * unit, 19f * unit), style = stroke(1.25f))
                drawArc(theme.primary.copy(alpha = 0.52f * active), -85f, 170f, false, xy(2.5f, 2.5f), Size(19f * unit, 19f * unit), style = stroke(2f))
                val horn = polygon(listOf(16.5f to 4.8f, 20.2f to 2.2f, 19.2f to 7.4f))
                drawPath(horn, theme.primary.copy(alpha = 0.8f * active))
            }
        }

        when (glyph) {
            NovaTabGlyph.HOME -> {
                filled(listOf(4f to 10f, 12f to 3.8f, 20f to 10f, 18.4f to 10f, 18.4f to 20f, 5.6f to 20f, 5.6f to 10f), active * 0.7f)
                rounded(10f, 13f, 4f, 7f, theme.background.copy(alpha = 0.86f), if (family in setOf(IconStyle.BUBBLE, IconStyle.CLOUD, IconStyle.CANDY)) 1.8f else 0.8f)
                line(12f, 14f, 12f, 19f, accent, 1f)
            }
            NovaTabGlyph.STATS -> {
                rounded(4f, 13f, 3.5f, 7f, primary.copy(alpha = active * 0.8f), 0.8f)
                rounded(10.2f, 9f, 3.5f, 11f, secondary.copy(alpha = active * 0.8f), 0.8f)
                rounded(16.4f, 5f, 3.5f, 15f, accent.copy(alpha = active * 0.8f), 0.8f)
                line(4.6f, 12f, 11.8f, 7.3f, outlineColor, 1.3f)
                line(11.8f, 7.3f, 18f, 4f, outlineColor, 1.3f)
                circle(4.6f, 12f, 0.9f, Color.White)
                circle(11.8f, 7.3f, 0.9f, Color.White)
                circle(18f, 4f, 0.9f, Color.White)
            }
            NovaTabGlyph.BILLBOARD -> {
                rounded(3.2f, 3.2f, 17.6f, 12.8f, theme.surface.copy(alpha = 0.72f), 1.2f)
                drawRoundRect(fillBrush, xy(3.2f, 3.2f), Size(17.6f * unit, 12.8f * unit), CornerRadius(1.2f * unit), alpha = active * 0.26f)
                drawRoundRect(outlineColor.copy(alpha = active), xy(3.2f, 3.2f), Size(17.6f * unit, 12.8f * unit), CornerRadius(1.2f * unit), style = stroke())
                star(12f, 9.5f, 3.8f, 1.7f, accent)
                line(8f, 16f, 16f, 16f, primary, 1.3f)
                line(10f, 17f, 9f, 20.5f, outlineColor, 1.2f)
                line(14f, 17f, 15f, 20.5f, outlineColor, 1.2f)
                line(7f, 20.5f, 17f, 20.5f, secondary, 1.5f)
            }
            NovaTabGlyph.RECORDS -> {
                filledColor(listOf(7.5f to 13.5f, 6f to 21f, 10.4f to 18.5f, 12f to 21f, 13.5f to 14f), secondary.copy(alpha = active * 0.8f))
                filledColor(listOf(12f to 14f, 14.5f to 18.5f, 18f to 21f, 16.5f to 13.5f), accent.copy(alpha = active * 0.72f))
                circle(12f, 10f, 7.2f, primary.copy(alpha = active * 0.55f), outlineColor, 1.5f)
                circle(12f, 10f, 5.2f, theme.background.copy(alpha = 0.25f), accent, 1.1f)
                star(12f, 10f, 3.5f, 1.7f, Color.White.copy(alpha = active))
            }
            NovaTabGlyph.CERTIFICATIONS -> {
                val gem = polygon(listOf(3.2f to 8f, 7.2f to 4f, 16.8f to 4f, 20.8f to 8f, 12f to 20.5f))
                drawPath(gem, fillBrush, alpha = active * 0.65f)
                drawPath(gem, outlineColor.copy(alpha = active), style = stroke())
                line(3.5f, 8f, 20.5f, 8f, accent, 1.1f)
                line(7.2f, 4f, 9.5f, 8f, outlineColor.copy(alpha = 0.85f), 0.9f)
                line(16.8f, 4f, 14.5f, 8f, outlineColor.copy(alpha = 0.85f), 0.9f)
                line(9.5f, 8f, 12f, 20f, secondary, 1f)
                line(14.5f, 8f, 12f, 20f, primary, 1f)
            }
            NovaTabGlyph.HALL_OF_FAME -> {
                val arch = Path().apply {
                    moveTo(xy(4f, 20f).x, xy(4f, 20f).y)
                    lineTo(xy(4f, 11f).x, xy(4f, 11f).y)
                    cubicTo(5f * unit + offsetX, 6f * unit + offsetY, 8f * unit + offsetX, 4f * unit + offsetY, 12f * unit + offsetX, 4f * unit + offsetY)
                    cubicTo(16f * unit + offsetX, 4f * unit + offsetY, 19f * unit + offsetX, 6f * unit + offsetY, 20f * unit + offsetX, 11f * unit + offsetY)
                    lineTo(xy(20f, 20f).x, xy(20f, 20f).y)
                    close()
                }
                drawPath(arch, fillBrush, alpha = active * 0.34f)
                drawPath(arch, outlineColor.copy(alpha = active), style = stroke())
                line(8f, 11f, 8f, 19f, primary, 1.5f)
                line(12f, 9f, 12f, 19f, accent, 1.5f)
                line(16f, 11f, 16f, 19f, secondary, 1.5f)
                line(4f, 20f, 20f, 20f, outlineColor, 1.3f)
                star(12f, 5.5f, 1.6f, 0.7f, accent)
            }
            NovaTabGlyph.PANTHEON -> {
                val orbit = Path().apply {
                    moveTo(xy(4f, 9f).x, xy(4f, 9f).y)
                    cubicTo(7f * unit + offsetX, 3f * unit + offsetY, 17f * unit + offsetX, 3f * unit + offsetY, 20f * unit + offsetX, 9f * unit + offsetY)
                    cubicTo(17f * unit + offsetX, 21f * unit + offsetY, 7f * unit + offsetX, 21f * unit + offsetY, 4f * unit + offsetX, 9f * unit + offsetY)
                }
                drawPath(orbit, primary.copy(alpha = 0.1f), style = stroke())
                drawPath(orbit, accent.copy(alpha = active), style = stroke(1.2f))
                line(6f, 17f, 18f, 7f, secondary, 1.2f)
                line(6f, 7f, 18f, 17f, primary, 1.2f)
                circle(6f, 7f, 1.4f, secondary)
                circle(18f, 7f, 1.4f, accent)
                circle(6f, 17f, 1.4f, primary)
                circle(18f, 17f, 1.4f, theme.glowSecondary)
                star(12f, 12f, 4f, 1.8f, Color.White.copy(alpha = active))
            }
            NovaTabGlyph.AWARDS -> {
                val cup = polygon(listOf(7f to 4f, 17f to 4f, 16f to 10f, 14.5f to 13f, 12f to 14.5f, 9.5f to 13f, 8f to 10f))
                drawPath(cup, fillBrush, alpha = active * 0.8f)
                drawPath(cup, outlineColor.copy(alpha = active), style = stroke())
                line(7f, 6f, 5f, 6f, primary, 1.5f); line(5f, 6f, 5f, 10f, primary, 1.5f); line(5f, 10f, 8.5f, 12f, primary, 1.5f)
                line(17f, 6f, 19f, 6f, secondary, 1.5f); line(19f, 6f, 19f, 10f, secondary, 1.5f); line(19f, 10f, 15.5f, 12f, secondary, 1.5f)
                line(12f, 14.5f, 12f, 18f, outlineColor, 1.4f)
                rounded(8f, 19f, 8f, 1.7f, accent, 0.7f)
                star(12f, 8.5f, 2.2f, 1f, Color.White)
            }
            NovaTabGlyph.SETTINGS -> {
                val gearPoints = ArrayList<Pair<Float, Float>>(32)
                repeat(32) { index ->
                    val angle = -PI / 2.0 + index * PI / 16.0
                    val radius = if (index % 4 < 2) 9.2f else 7.4f
                    gearPoints += (12f + cos(angle).toFloat() * radius) to (12f + sin(angle).toFloat() * radius)
                }
                val gear = polygon(gearPoints)
                drawPath(gear, fillBrush, alpha = active * 0.66f)
                drawPath(gear, outlineColor.copy(alpha = active), style = stroke())
                circle(12f, 12f, 4f, theme.background.copy(alpha = 0.92f), accent, 1.2f)
                circle(12f, 12f, 1.5f, secondary)
            }
            NovaTabGlyph.BRAND -> {
                star(12f, 12f, 8.4f, 4.1f, primary.copy(alpha = active * 0.46f))
                star(12f, 12f, 5.4f, 2.2f, Color.White.copy(alpha = active))
                diamond(12f, 12f, 10f, accent.copy(alpha = 0.65f * active), lineOnly = true)
            }
        }

        // Matériau de finition spécifique : reflets, motifs, clous, rayons, couture ou géométrie.
        when (family) {
            IconStyle.HUD -> {
                line(9f, 3f, 15f, 3f, NovaColors.Gold.copy(alpha = 0.65f * active), 0.8f)
                circle(12f, 2.7f, 0.7f, accent)
            }
            IconStyle.CHROME_ROUND -> {
                drawArc(Color.White.copy(alpha = 0.48f * active), 210f, 58f, false, xy(7f, 4f), Size(10f * unit, 8f * unit), style = stroke(0.9f))
                circle(8f, 6f, 0.65f, Color.White.copy(alpha = 0.86f * active))
            }
            IconStyle.RAW -> {
                line(4f, 17.5f, 7f, 17f, Color.White.copy(alpha = 0.48f * active), 0.8f)
                line(17f, 5f, 20f, 4.5f, theme.accent.copy(alpha = 0.85f), 0.8f)
            }
            IconStyle.ROYAL -> {
                star(18.3f, 5f, 1.2f, 0.5f, NovaColors.Gold)
                circle(5.8f, 16.8f, 0.7f, NovaColors.Gold)
            }
            IconStyle.BUBBLE -> {
                circle(18.7f, 5f, 1f, Color.White.copy(alpha = 0.52f * active))
                circle(5f, 18.5f, 0.7f, Color.White.copy(alpha = 0.48f * active))
            }
            IconStyle.STAGE -> {
                drawCircle(Brush.radialGradient(listOf(theme.glowSecondary.copy(alpha = 0.34f * active), Color.Transparent), xy(12f, 3f), 7f * unit), 7f * unit, xy(12f, 3f))
                line(3f, 22f, 21f, 22f, NovaColors.Gold.copy(alpha = 0.6f * active), 0.9f)
            }
            IconStyle.SPIKY -> {
                listOf(3f to 8f, 20f to 14f, 6f to 20f).forEach { (x, y) -> star(x, y, 1.5f, 0.55f, accent.copy(alpha = 0.75f * active)) }
            }
            IconStyle.CLOUD -> {
                circle(19f, 6f, 1f, Color.White.copy(alpha = 0.55f * active))
                circle(5f, 18f, 0.7f, theme.primary.copy(alpha = 0.6f * active))
            }
            IconStyle.SUN -> {
                star(19f, 5f, 1.3f, 0.55f, Color.White.copy(alpha = 0.82f * active))
            }
            IconStyle.METAL -> {
                listOf(4f to 4f, 20f to 4f, 4f to 20f, 20f to 20f).forEach { (x, y) -> circle(x, y, 0.65f, Color(0xFFC0C0C0)) }
            }
            IconStyle.PRIDE -> Unit
            IconStyle.CANDY -> {
                drawArc(Color.White.copy(alpha = 0.58f * active), 205f, 48f, false, xy(5f, 3f), Size(12f * unit, 10f * unit), style = stroke(0.8f))
                star(18f, 6f, 1.4f, 0.6f, Color.White.copy(alpha = active))
            }
            IconStyle.POLAROID -> {
                drawRect(Color.White.copy(alpha = 0.82f), xy(16.7f, 2.7f), Size(3.5f * unit, 1.2f * unit))
                circle(18.4f, 17.7f, 0.55f, theme.accent)
            }
            IconStyle.GEOMETRIC -> {
                diamond(12f, 12f, 2.1f, theme.accent.copy(alpha = 0.72f * active))
            }
            IconStyle.DUAL -> {
                drawArc(Color.White.copy(alpha = 0.72f * active), 205f, 140f, false, xy(5f, 5f), Size(14f * unit, 14f * unit), style = stroke(0.75f))
                line(19f, 4f, 21f, 2.2f, primary, 1.2f)
            }
        }
    }
}
