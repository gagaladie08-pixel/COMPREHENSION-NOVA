package com.novastats.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Icône d'onglet rendue selon le style d'icônes du thème (HUD, chrome, brut, royal, bulle, scène,
 * pointu, nuage, soleil, métal, pride, bonbon, polaroid, géométrique, dual).
 */
@Composable
fun ThemedTabIcon(icon: ImageVector, contentDescription: String, selected: Boolean, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    val tint by animateColorAsState(if (selected) theme.primary else theme.textSecondary, NovaMotion.spec(theme), label = "tint")
    val emphasis by animateFloatAsState(if (selected) 1f else 0f, NovaMotion.spec(theme), label = "emph")
    val style = theme.icons
    val rotation = if (style == IconStyle.RAW && selected) -3f else 0f
    val scale = 1f + emphasis * when (style) { IconStyle.BUBBLE, IconStyle.CLOUD, IconStyle.CANDY -> 0.18f; IconStyle.SPIKY, IconStyle.RAW -> 0.1f; else -> 0.06f }

    Box(
        modifier.size(34.dp).rotate(rotation).scale(scale).drawBehind {
            val c = center
            val r = size.minDimension / 2
            val e = emphasis
            when (style) {
                IconStyle.HUD -> {
                    // Coins de viseur HUD + reflet doré glossy
                    val l = r * 0.55f; val s = Stroke(1.5.dp.toPx())
                    val col = theme.accent.copy(alpha = 0.25f + 0.75f * e)
                    listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f)).forEach { d ->
                        val corner = Offset(c.x + d.x * r, c.y + d.y * r)
                        drawLine(col, corner, Offset(corner.x - d.x * l, corner.y), s.width)
                        drawLine(col, corner, Offset(corner.x, corner.y - d.y * l), s.width)
                    }
                    if (e > 0f) drawRect(Brush.verticalGradient(listOf(NovaColors.Gold.copy(alpha = 0.18f * e), Color.Transparent)), Offset(c.x - r, c.y - r), Size(r * 2, r))
                }
                IconStyle.CHROME_ROUND -> {
                    drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.35f + 0.3f * e), theme.accent.copy(alpha = 0.15f + 0.25f * e), Color.Black.copy(alpha = 0.3f)), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c)
                    drawCircle(theme.primary.copy(alpha = 0.5f + 0.5f * e), r, c, style = Stroke(1.5.dp.toPx()))
                }
                IconStyle.RAW -> {
                    // Papier déchiré : rectangle brut aux bords irréguliers
                    val p = Path().apply {
                        moveTo(c.x - r, c.y - r * 0.8f); lineTo(c.x - r * 0.6f, c.y - r); lineTo(c.x + r * 0.9f, c.y - r * 0.9f); lineTo(c.x + r, c.y + r * 0.7f)
                        lineTo(c.x + r * 0.5f, c.y + r); lineTo(c.x - r * 0.9f, c.y + r * 0.85f); close()
                    }
                    drawPath(p, if (e > 0.5f) theme.primary.copy(alpha = 0.25f) else theme.secondary.copy(alpha = 0.25f))
                    drawPath(p, theme.text.copy(alpha = 0.2f + 0.5f * e), style = Stroke(1.dp.toPx()))
                }
                IconStyle.ROYAL -> {
                    drawCircle(theme.primary.copy(alpha = 0.35f + 0.65f * e), r, c, style = Stroke((1.5f + 1.5f * e).dp.toPx()))
                    if (e > 0f) {
                        // petite couronne : 3 pointes au-dessus
                        val crown = Path().apply {
                            moveTo(c.x - r * 0.6f, c.y - r * 0.95f); lineTo(c.x - r * 0.35f, c.y - r * 1.35f); lineTo(c.x, c.y - r * 1.05f)
                            lineTo(c.x + r * 0.35f, c.y - r * 1.35f); lineTo(c.x + r * 0.6f, c.y - r * 0.95f); close()
                        }
                        drawPath(crown, NovaColors.Gold.copy(alpha = e))
                    }
                }
                IconStyle.BUBBLE -> {
                    drawCircle(theme.primary.copy(alpha = 0.18f + 0.5f * e), r * 1.05f, c)
                    drawCircle(Color.White.copy(alpha = 0.6f), r * 0.22f, c + Offset(-r * 0.4f, -r * 0.45f))
                }
                IconStyle.STAGE -> {
                    drawLine(theme.glowSecondary.copy(alpha = 0.3f + 0.7f * e), Offset(c.x - r, c.y + r), Offset(c.x + r, c.y + r), 1.dp.toPx())
                    if (e > 0f) drawCircle(Brush.radialGradient(listOf(theme.glowSecondary.copy(alpha = 0.35f * e), Color.Transparent), c, r * 1.4f), r * 1.4f, c)
                }
                IconStyle.SPIKY -> {
                    val p = Path().apply {
                        moveTo(c.x, c.y - r * 1.15f); lineTo(c.x + r * 1.15f, c.y); lineTo(c.x, c.y + r * 1.15f); lineTo(c.x - r * 1.15f, c.y); close()
                    }
                    drawPath(p, theme.primary.copy(alpha = 0.15f + 0.45f * e))
                    if (e > 0f) listOf(Offset(0.9f, -0.9f), Offset(-1f, 0.6f), Offset(0.7f, 1f)).forEach { d -> drawCircle(theme.accent.copy(alpha = 0.9f * e), r * 0.14f, Offset(c.x + d.x * r, c.y + d.y * r)) }
                }
                IconStyle.CLOUD -> {
                    val col = Color.White.copy(alpha = 0.7f + 0.3f * e)
                    drawCircle(theme.primary.copy(alpha = 0.12f + 0.2f * e), r * 1.1f, c + Offset(0f, 2.dp.toPx()))
                    drawCircle(col, r * 0.95f, c)
                    drawCircle(col, r * 0.6f, c + Offset(-r * 0.6f, r * 0.15f))
                    drawCircle(col, r * 0.65f, c + Offset(r * 0.6f, r * 0.1f))
                }
                IconStyle.SUN -> {
                    drawCircle(Brush.radialGradient(listOf(theme.glowSecondary.copy(alpha = 0.25f + 0.45f * e), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
                    if (e > 0f) for (i in 0 until 8) {
                        val a = i * Math.PI.toFloat() / 4
                        val d = Offset(kotlin.math.cos(a), kotlin.math.sin(a))
                        drawLine(theme.accent.copy(alpha = 0.8f * e), Offset(c.x + d.x * r * 0.95f, c.y + d.y * r * 0.95f), Offset(c.x + d.x * r * 1.25f, c.y + d.y * r * 1.25f), 1.5.dp.toPx())
                    }
                }
                IconStyle.METAL -> {
                    drawRect(Brush.linearGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1A1A1A), Color(0xFF2E2E2E))), Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
                    drawRect(theme.primary.copy(alpha = 0.3f + 0.6f * e), Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.dp.toPx()))
                    // rivets
                    listOf(Offset(-1f, -1f), Offset(1f, -1f), Offset(1f, 1f), Offset(-1f, 1f)).forEach { d -> drawCircle(theme.glowSecondary.copy(alpha = 0.5f + 0.5f * e), 1.5.dp.toPx(), Offset(c.x + d.x * (r - 3.dp.toPx()), c.y + d.y * (r - 3.dp.toPx()))) }
                }
                IconStyle.PRIDE -> {
                    // Bandeau de trois mini-drapeaux sous l'icône : arc-en-ciel | trans | bi
                    val h = 3.5f.dp.toPx(); val top = c.y + r - h
                    val flags = listOf(NovaColors.Rainbow, NovaColors.Trans, NovaColors.Bi)
                    val segW = r * 2 / flags.size
                    flags.forEachIndexed { fi, f ->
                        val x0 = c.x - r + fi * segW; val w = segW / f.size
                        f.forEachIndexed { ci, col -> drawRect(col, Offset(x0 + ci * w, top), Size(w + 0.5f, h), alpha = 0.55f + 0.45f * e) }
                    }
                    // Chevron Progress sur le flanc gauche (blanc, rose, bleu, marron, noir)
                    val cw = r * 0.22f
                    NovaColors.Progress.forEachIndexed { i, col ->
                        val x = c.x - r - 1.dp.toPx() + i * cw * 0.55f
                        val chev = Path().apply { moveTo(x, c.y - r * 0.55f); lineTo(x + cw, c.y); lineTo(x, c.y + r * 0.55f); lineTo(x + cw * 0.5f, c.y + r * 0.55f); lineTo(x + cw * 1.5f, c.y); lineTo(x + cw * 0.5f, c.y - r * 0.55f); close() }
                        drawPath(chev, col, alpha = 0.35f + 0.65f * e)
                    }
                    // Sélection : anneau en dégradé conique arc-en-ciel → trans → bi → gay
                    if (e > 0f) {
                        val ring = NovaColors.PrideCycle + NovaColors.PrideCycle.first()
                        drawCircle(Brush.sweepGradient(ring, c), r, c, alpha = 0.22f * e)
                        drawCircle(Brush.sweepGradient(ring, c), r - 1.dp.toPx(), c, alpha = 0.9f * e, style = Stroke(1.5.dp.toPx()))
                    }
                }
                IconStyle.CANDY -> {
                    drawCircle(Brush.linearGradient(listOf(theme.primary, theme.glowSecondary, theme.secondary), Offset(c.x - r, c.y - r), Offset(c.x + r, c.y + r)), r, c, alpha = 0.25f + 0.6f * e)
                    drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), c.y - r, c.y), r * 0.8f, c + Offset(0f, -r * 0.2f))
                }
                IconStyle.POLAROID -> {
                    val w = r * 1.9f; val top = c.y - r
                    drawRect(Color.White.copy(alpha = 0.85f + 0.15f * e), Offset(c.x - w / 2, top), Size(w, r * 2.1f))
                    drawRect(theme.primary.copy(alpha = 0.12f + 0.25f * e), Offset(c.x - w / 2 + 2.dp.toPx(), top + 2.dp.toPx()), Size(w - 4.dp.toPx(), r * 1.55f))
                    if (e > 0f) drawRect(theme.secondary.copy(alpha = e), Offset(c.x + w / 2 - 6.dp.toPx(), top + r * 1.65f), Size(4.dp.toPx(), 4.dp.toPx()))
                }
                IconStyle.GEOMETRIC -> {
                    val s = Stroke(1.2.dp.toPx())
                    val d = Path().apply { moveTo(c.x, c.y - r * 1.15f); lineTo(c.x + r * 1.15f, c.y); lineTo(c.x, c.y + r * 1.15f); lineTo(c.x - r * 1.15f, c.y); close() }
                    drawPath(d, theme.glowSecondary.copy(alpha = 0.35f + 0.65f * e), style = s)
                    val d2 = Path().apply { moveTo(c.x, c.y - r * 0.8f); lineTo(c.x + r * 0.8f, c.y); lineTo(c.x, c.y + r * 0.8f); lineTo(c.x - r * 0.8f, c.y); close() }
                    drawPath(d2, theme.primary.copy(alpha = 0.2f + 0.3f * e), style = s)
                }
                IconStyle.DUAL -> {
                    val light = theme.secondary; val dark = theme.primary
                    // Halo fin (ange) à gauche, cornes épaisses (démon) à droite
                    drawArc(light.copy(alpha = 0.35f + 0.65f * e), 90f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(1.dp.toPx()))
                    drawArc(dark.copy(alpha = 0.35f + 0.65f * e), -90f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3.dp.toPx()))
                    if (e > 0f) {
                        val horn = Path().apply { moveTo(c.x + r * 0.55f, c.y - r * 0.85f); lineTo(c.x + r * 0.95f, c.y - r * 1.35f); lineTo(c.x + r * 0.95f, c.y - r * 0.6f); close() }
                        drawPath(horn, dark.copy(alpha = e))
                    }
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}
