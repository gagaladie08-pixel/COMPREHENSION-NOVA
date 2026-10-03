package com.novastats.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/*
 * 🎞️ Animations de fond « univers du thème », posées en voile très transparent sur les onglets principaux
 * (jamais dans les popups). Une animation par thème ; Survivor a son propre voile (PrideVeil).
 */

private const val TWO_PI = 6.2831855f

@Composable
fun ThemeAmbient(theme: NovaTheme, modifier: Modifier = Modifier) {
    if (theme.id == "survivor") return
    // Horloge 0→1 sur 12 s (boucle) ; les dessins utilisent sin/cos pour rester continus au raccord
    val t by rememberInfiniteTransition(label = "ambient").animateFloat(0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Restart), label = "amb")
    val seeds = remember { List(40) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * TWO_PI, Random.nextFloat()) } }
    Canvas(modifier) {
        when (theme.id) {
            "cyber_nova" -> cyberGrid(theme, t)
            "neon_disco" -> discoLights(theme, t, seeds)
            "villain_era" -> embers(theme, t, seeds)
            "slay_queen" -> goldDust(theme, t, seeds)
            "pink_y2k" -> y2kStars(theme, t, seeds)
            "velvet_stage" -> spotlights(theme, t)
            "pink_venom" -> venomDrips(theme, t, seeds)
            "cloud_nine" -> clouds(theme, t, seeds)
            "solara" -> sunRays(theme, t)
            "chaos_born" -> chaosShards(theme, t, seeds)
            "rainbow_pop" -> bokeh(t, seeds)
            "pop_revolution" -> equalizer(theme, t, seeds)
            "african_confessions" -> geometricDrift(theme, t)
            "bad_angel" -> feathersAndHalo(theme, t, seeds)
        }
    }
}

/* ----- Cyber Nova : grille perspective qui défile + faisceau de scan ----- */
private fun DrawScope.cyberGrid(theme: NovaTheme, t: Float) {
    val horizon = size.height * 0.55f
    val col = theme.accent
    for (i in 0 until 14) {
        val f = ((i + t * 2f) % 14f) / 14f
        val y = horizon + (size.height - horizon) * f * f
        drawLine(col.copy(alpha = 0.03f + 0.09f * f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
    for (i in -6..6) {
        val x0 = size.width / 2 + i * size.width * 0.09f
        val x1 = size.width / 2 + i * size.width * 0.5f
        drawLine(col.copy(alpha = 0.05f), Offset(x0, horizon), Offset(x1, size.height), 1.dp.toPx())
    }
    val scanY = ((t * 3f) % 1f) * size.height
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, theme.primary.copy(alpha = 0.10f), Color.Transparent), scanY - 40.dp.toPx(), scanY + 40.dp.toPx()), Offset(0f, scanY - 40.dp.toPx()), Size(size.width, 80.dp.toPx()))
}

/* ----- Neon Disco : taches de lumière colorées qui tournent comme une boule à facettes ----- */
private fun DrawScope.discoLights(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    val cols = listOf(theme.primary, theme.secondary, theme.accent, theme.glowSecondary, Color.White)
    seeds.take(9).forEachIndexed { i, s ->
        val a = t * TWO_PI * (if (i % 2 == 0) 1f else -1f) + s[3]
        val c = Offset(size.width * (0.5f + 0.45f * cos(a) * s[0]), size.height * (0.5f + 0.45f * sin(a * 0.7f) * s[1]))
        val r = size.width * (0.10f + 0.12f * s[2])
        drawCircle(Brush.radialGradient(listOf(cols[i % cols.size].copy(alpha = 0.12f), Color.Transparent), c, r), r, c)
    }
}

/* ----- Villain Era : braises qui montent et vacillent ----- */
private fun DrawScope.embers(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    seeds.forEach { s ->
        val y = ((s[1] - t * (0.6f + s[2])) % 1f + 1f) % 1f
        val x = s[0] + sin(t * TWO_PI * 2 + s[3]) * 0.03f
        val flick = 0.5f + 0.5f * sin(t * TWO_PI * 6 + s[3])
        val col = (if (s[4] > 0.5f) theme.primary else theme.accent).copy(alpha = 0.08f + 0.14f * flick * y)
        drawCircle(col, (1f + s[2] * 2.2f).dp.toPx(), Offset(x * size.width, y * size.height))
    }
}

/* ----- Slay Queen : poussière d'or qui tombe + bande de reflet diagonale ----- */
private fun DrawScope.goldDust(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    seeds.forEach { s ->
        val y = ((s[1] + t * (0.3f + s[2] * 0.5f)) % 1f)
        val x = s[0] + sin(t * TWO_PI + s[3]) * 0.015f
        val a = 0.06f + 0.16f * (0.5f + 0.5f * sin(t * TWO_PI * 4 + s[3]))
        drawStar4(Offset(x * size.width, y * size.height), (1.5f + s[2] * 2.5f).dp.toPx(), NovaColors.Gold.copy(alpha = a))
    }
    val shift = (t * 2f % 1f) * (size.width + size.height) - size.height
    rotate(-30f) { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, NovaColors.Gold.copy(alpha = 0.07f), Color.Transparent), shift, shift + 120.dp.toPx()), Offset(-size.height, -size.height), Size(size.width + size.height * 2, size.height * 3)) }
}

/* ----- Pink Y2K : étoiles et cœurs pastel qui flottent ----- */
private fun DrawScope.y2kStars(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    seeds.take(26).forEach { s ->
        val y = ((s[1] - t * (0.25f + s[2] * 0.3f)) % 1f + 1f) % 1f
        val x = s[0] + sin(t * TWO_PI * 1.5f + s[3]) * 0.02f
        val col = (if (s[4] > 0.5f) theme.primary else theme.secondary).copy(alpha = 0.12f + 0.10f * sin(t * TWO_PI * 3 + s[3]))
        val c = Offset(x * size.width, y * size.height)
        if (s[4] > 0.66f) drawHeart(c, (2f + s[2] * 3f).dp.toPx(), col) else drawStar4(c, (2f + s[2] * 4f).dp.toPx(), col)
    }
}

/* ----- Velvet Stage : deux projecteurs qui balaient la scène ----- */
private fun DrawScope.spotlights(theme: NovaTheme, t: Float) {
    listOf(-1f, 1f).forEachIndexed { i, side ->
        val sway = sin(t * TWO_PI + i * 1.7f) * 0.25f
        val top = Offset(if (side < 0) 0f else size.width, 0f)
        val baseX = size.width * (0.5f + sway + side * 0.1f)
        val p = Path().apply { moveTo(top.x, top.y); lineTo(baseX - size.width * 0.22f, size.height); lineTo(baseX + size.width * 0.22f, size.height); close() }
        drawPath(p, Brush.verticalGradient(listOf((if (i == 0) theme.accent else theme.primary).copy(alpha = 0.14f), Color.Transparent)))
    }
}

/* ----- Pink Venom : coulures qui descendent lentement ----- */
private fun DrawScope.venomDrips(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    seeds.take(14).forEach { s ->
        val len = size.height * (0.15f + s[2] * 0.35f)
        val y = ((s[1] + t * (0.2f + s[4] * 0.3f)) % 1.3f) * size.height - len
        val x = s[0] * size.width
        drawLine(Brush.verticalGradient(listOf(Color.Transparent, theme.primary.copy(alpha = 0.16f)), y, y + len), Offset(x, y), Offset(x, y + len), (2f + s[2] * 2f).dp.toPx())
        drawCircle(theme.primary.copy(alpha = 0.18f), (2.5f + s[2] * 2.5f).dp.toPx(), Offset(x, y + len))
    }
}

/* ----- Cloud Nine : nuages doux qui dérivent ----- */
private fun DrawScope.clouds(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    seeds.take(7).forEachIndexed { i, s ->
        val x = ((s[0] + t * (0.05f + s[2] * 0.08f) * (if (i % 2 == 0) 1f else -1f)) % 1.4f + 1.4f) % 1.4f - 0.2f
        val y = s[1] * 0.9f + 0.05f
        val r = size.width * (0.08f + s[4] * 0.08f)
        val c = Offset(x * size.width, y * size.height)
        val col = Color.White.copy(alpha = 0.16f)
        drawCircle(col, r, c); drawCircle(col, r * 0.8f, c + Offset(r * 0.9f, r * 0.2f)); drawCircle(col, r * 0.7f, c + Offset(-r * 0.8f, r * 0.25f)); drawCircle(col, r * 0.6f, c + Offset(r * 0.2f, -r * 0.5f))
    }
}

/* ----- Solara : rayons de soleil qui tournent depuis le coin haut droit ----- */
private fun DrawScope.sunRays(theme: NovaTheme, t: Float) {
    val c = Offset(size.width * 1.05f, -size.height * 0.05f)
    val len = size.maxDimension * 1.4f
    rotate(t * 360f / 6f, c) {
        for (i in 0 until 12) {
            val a = i * TWO_PI / 12f
            val p = Path().apply { moveTo(c.x, c.y); lineTo(c.x + cos(a) * len, c.y + sin(a) * len); lineTo(c.x + cos(a + 0.12f) * len, c.y + sin(a + 0.12f) * len); close() }
            drawPath(p, Brush.radialGradient(listOf(theme.accent.copy(alpha = 0.16f), Color.Transparent), c, len))
        }
    }
    val pulse = 0.5f + 0.5f * sin(t * TWO_PI * 2)
    drawCircle(Brush.radialGradient(listOf(theme.primary.copy(alpha = 0.10f + 0.08f * pulse), Color.Transparent), c, size.width * 0.6f), size.width * 0.6f, c)
}

/* ----- Chaos Born : éclats qui sautent de place + tranches colorées ----- */
private fun DrawScope.chaosShards(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    val step = floor(t * 16f)
    seeds.take(12).forEachIndexed { i, s ->
        val jx = abs(sin(step * 12.9898f + i * 78.233f)) ; val jy = abs(cos(step * 4.1414f + i * 31.7f))
        val c = Offset(jx * size.width, jy * size.height)
        val r = (6f + s[2] * 14f).dp.toPx()
        val p = Path().apply { moveTo(c.x, c.y - r); lineTo(c.x + r * 0.8f, c.y + r * 0.6f); lineTo(c.x - r * 0.9f, c.y + r * 0.4f); close() }
        drawPath(p, (if (i % 2 == 0) theme.primary else theme.accent).copy(alpha = 0.10f), style = Stroke(1.dp.toPx()))
    }
    if (step % 5f == 0f) drawRect(theme.primary.copy(alpha = 0.06f), Offset(0f, (t * 7f % 1f) * size.height), Size(size.width, 6.dp.toPx()))
}

/* ----- Rainbow Pop : bokeh arc-en-ciel qui dérive ----- */
private fun DrawScope.bokeh(t: Float, seeds: List<FloatArray>) {
    seeds.take(16).forEachIndexed { i, s ->
        val y = ((s[1] - t * (0.08f + s[2] * 0.1f)) % 1f + 1f) % 1f
        val x = s[0] + sin(t * TWO_PI + s[3]) * 0.03f
        val r = size.width * (0.03f + s[4] * 0.06f)
        drawCircle(NovaColors.Rainbow[i % NovaColors.Rainbow.size].copy(alpha = 0.10f), r, Offset(x * size.width, y * size.height))
    }
}

/* ----- Pop Revolution : égaliseur qui pulse en bas de l'écran ----- */
private fun DrawScope.equalizer(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    val n = 28; val w = size.width / n
    for (i in 0 until n) {
        val s = seeds[i % seeds.size]
        val h = size.height * (0.06f + 0.16f * abs(sin(t * TWO_PI * (2f + s[2] * 3f) + s[3])))
        drawRect(Brush.verticalGradient(listOf(theme.primary.copy(alpha = 0.16f), theme.accent.copy(alpha = 0.03f)), size.height - h, size.height), Offset(i * w + w * 0.2f, size.height - h), Size(w * 0.6f, h))
    }
}

/* ----- African Confessions : motif géométrique (losanges / triangles) qui glisse ----- */
private fun DrawScope.geometricDrift(theme: NovaTheme, t: Float) {
    val cell = 56.dp.toPx(); val off = t * cell * 2
    val cols = listOf(theme.primary, theme.secondary, theme.accent)
    var row = 0
    var y = -cell * 2 + off % (cell * 2)
    while (y < size.height + cell) {
        var x = -cell + (if (row % 2 == 0) 0f else cell / 2)
        var k = 0
        while (x < size.width + cell) {
            val p = Path().apply { moveTo(x, y - cell / 2); lineTo(x + cell / 2, y); lineTo(x, y + cell / 2); lineTo(x - cell / 2, y); close() }
            drawPath(p, cols[(row + k) % 3].copy(alpha = 0.07f), style = Stroke(1.2.dp.toPx()))
            x += cell; k++
        }
        y += cell; row++
    }
}

/* ----- Bad Angel : plumes qui tombent en tournoyant + auréole en haut ----- */
private fun DrawScope.feathersAndHalo(theme: NovaTheme, t: Float, seeds: List<FloatArray>) {
    val haloC = Offset(size.width / 2, -size.width * 0.15f)
    drawCircle(Brush.radialGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.14f), Color.Transparent), haloC, size.width * 0.5f), size.width * 0.5f, haloC, style = Stroke(10.dp.toPx()))
    seeds.take(16).forEach { s ->
        val y = ((s[1] + t * (0.15f + s[2] * 0.2f)) % 1.1f) - 0.05f
        val x = s[0] + sin(t * TWO_PI * 1.5f + s[3]) * 0.05f
        val c = Offset(x * size.width, y * size.height)
        val col = (if (s[4] > 0.5f) Color.White else theme.primary).copy(alpha = 0.14f)
        rotate(sin(t * TWO_PI + s[3]) * 40f + 20f, c) { drawOval(col, Offset(c.x - 3.dp.toPx(), c.y - 9.dp.toPx()), Size(6.dp.toPx(), 18.dp.toPx())) }
    }
}

/** Étoile à 4 branches (éclat). */
private fun DrawScope.drawStar4(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, color)
}
