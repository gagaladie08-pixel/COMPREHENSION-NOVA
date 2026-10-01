package com.novastats.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novastats.app.domain.BillboardDates
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.drawNovaCurve
import com.novastats.app.ui.theme.rememberCurveAnim
import com.novastats.app.ui.theme.NovaColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Gold = Color(0xFFFFD700)
private val goldBrush = Brush.linearGradient(listOf(Gold, Color(0xFFFFF3B0), Color(0xFFB8860B), Gold))
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)

/** Fiche historique d'une entité dans un chart (appui long sur une ligne). */
@Composable
fun ChartHistoryDialog(h: EntityHistory, onDismiss: () -> Unit) {
    val theme = Nova.theme
    val s = h.stats
    val unit = { n: Int -> BillboardDates.unitLabel(h.period, n) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, goldBrush, RoundedCornerShape(20.dp))
                    .background(theme.surface)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .verticalScroll(rememberScrollState())
            ) {
                // ---- Bannière 180dp
                Box(
                    Modifier.fillMaxWidth().height(180.dp)
                        .background(Brush.verticalGradient(listOf(Gold.copy(alpha = 0.55f), theme.surface)))
                ) {
                    Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CoverArt(h.item.coverUrl, h.item.name, size = 96, circle = h.item.circle)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${h.chart.emoji} ${h.chart.label} · ${h.period.label}", color = Gold, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(h.item.name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            h.item.secondary?.let { Text(it, color = theme.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            Spacer(Modifier.height(6.dp))
                            s.peak?.let {
                                Text("Peak #${it.position} ×${s.timesAtPeak}", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                // ---- Courbe
                Text("Parcours dans le classement", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                PositionChart(h, Modifier.fillMaxWidth().height(160.dp).padding(horizontal = 16.dp))

                // ---- Badges
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    HistoryBadge("⭐", s.peak?.let { "#${it.position}" } ?: "—", "Peak")
                    HistoryBadge("📅", "${s.periodsInChart}", unit(s.periodsInChart).replaceFirstChar { it.uppercase() })
                    HistoryBadge("👑", "${s.longestRunAt1}", "Série #1")
                    HistoryBadge("🔟", "${s.longestRunTop10}", "Série Top 10")
                }

                // ---- Détails
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine("Première entrée", s.firstEntry?.let { "#${it.position} · ${it.date.format(dateFmt)}" } ?: "—")
                    DetailLine("Peak", s.peak?.let { "#${it.position} · ${it.date.format(dateFmt)}" } ?: "—")
                    DetailLine("Total périodes classées", "${s.periodsInChart} ${unit(s.periodsInChart)}")
                    DetailLine("Plus longue série #1", "${s.longestRunAt1} ${unit(s.longestRunAt1)}")
                    DetailLine("Plus longue série Top 10", "${s.longestRunTop10} ${unit(s.longestRunTop10)}")
                    DetailLine("Record d'écoutes sur une période", "${formatCount(s.maxPlays)} ▶")
                    Spacer(Modifier.height(4.dp))
                    Text("Touche en dehors pour fermer", color = theme.textSecondary.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun HistoryBadge(emoji: String, value: String, label: String) {
    val theme = Nova.theme
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .background(Gold.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 14.sp)
        Text(value, color = Gold, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(label, color = theme.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        Text(value, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Courbe de position (1 en haut) sur la suite des ancres depuis la première entrée.
 * Les périodes hors chart créent une rupture ; ⭐ marque le peak.
 */
@Composable
private fun PositionChart(h: EntityHistory, modifier: Modifier) {
    val theme = Nova.theme
    val limit = h.chart.limit(h.period)
    val byDate = remember(h) { h.appearances.associateBy { it.date } }
    val anchors: List<LocalDate> = remember(h) { h.anchors.filter { a -> !a.isBefore(h.stats.firstEntry?.date ?: a) } }
    val maxPos = remember(h) { (h.appearances.maxOfOrNull { it.position } ?: limit).coerceAtLeast(5) }
    val lineColor = theme.primary
    val textColor = theme.textSecondary
    val anim = rememberCurveAnim(theme, h.appearances.size)

    Canvas(modifier) {
        val w = size.width; val hgt = size.height
        val padL = 28.dp.toPx(); val padB = 16.dp.toPx(); val padT = 12.dp.toPx()
        val plotW = w - padL; val plotH = hgt - padB - padT
        val n = anchors.size.coerceAtLeast(1)
        fun x(i: Int) = padL + if (n == 1) plotW / 2 else plotW * i / (n - 1)
        fun y(pos: Int) = padT + plotH * (pos - 1) / (maxPos - 1).coerceAtLeast(1)

        // Grille : #1, milieu, max
        val gridPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, (textColor.red * 255).toInt(), (textColor.green * 255).toInt(), (textColor.blue * 255).toInt())
            textSize = 10.sp.toPx(); isAntiAlias = true
        }
        listOf(1, (maxPos + 1) / 2, maxPos).distinct().forEach { p ->
            drawLine(textColor.copy(alpha = 0.2f), Offset(padL, y(p)), Offset(w, y(p)), strokeWidth = 1f)
            drawContext.canvas.nativeCanvas.drawText("#$p", 0f, y(p) + 4.dp.toPx(), gridPaint)
        }

        // Segments continus (ancres consécutives présentes)
        var segment = mutableListOf<Offset>()
        val segments = mutableListOf<List<Offset>>()
        anchors.forEachIndexed { i, a ->
            val ap = byDate[a]
            if (ap != null) segment += Offset(x(i), y(ap.position))
            else if (segment.isNotEmpty()) { segments += segment; segment = mutableListOf() }
        }
        if (segment.isNotEmpty()) segments += segment

        segments.forEach { pts ->
            if (pts.size >= 2) drawNovaCurve(theme, pts, baselineY = padT + plotH, anim = anim, showPoints = true)
            else pts.forEach { drawCircle(lineColor, radius = 3.5.dp.toPx(), center = it) }
        }

        // ⭐ au peak
        h.stats.peak?.let { peak ->
            val i = anchors.indexOf(peak.date)
            if (i >= 0) {
                val c = Offset(x(i), y(peak.position))
                drawCircle(Gold, radius = 7.dp.toPx(), center = c)
                val p = android.graphics.Paint().apply { textSize = 14.sp.toPx(); textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true }
                drawContext.canvas.nativeCanvas.drawText("⭐", c.x, c.y - 9.dp.toPx(), p)
            }
        }
    }
}
