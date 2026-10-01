package com.novastats.app.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.domain.BillboardDates
import com.novastats.app.ui.theme.Nova
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Or fixe du popup Billboard — indépendant du thème (POPUPS.md). */
private val Gold = Color(0xFFFFD700)
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)

/**
 * 4. 🏆 Popup Billboard (fiche historique, appui long sur une ligne) :
 * bordure / dégradé or fixe, bannière Titre/Artiste + Peak en grand, courbe (#1 en haut) + zone colorée + ⭐ au peak,
 * badges Peak / Semaines / Série #1 / Série Top 10, détails.
 */
@Composable
fun ChartHistoryDialog(h: EntityHistory, onDismiss: () -> Unit) {
    val theme = Nova.theme
    val s = h.stats
    val unit = { n: Int -> BillboardDates.unitLabel(h.period, n) }
    val byDate = remember(h) { h.appearances.associateBy { it.date } }
    val anchors = remember(h) { h.anchors.filter { a -> !a.isBefore(s.firstEntry?.date ?: a) } }
    val positions = remember(anchors, byDate) { anchors.map { byDate[it]?.position?.toFloat() } }
    val maxPos = remember(h) { (h.appearances.maxOfOrNull { it.position } ?: h.chart.limit(h.period)).coerceAtLeast(5).toFloat() }
    val peakIdx = s.peak?.let { pk -> anchors.indexOf(pk.date).takeIf { it >= 0 } }

    NovaPopupCard(
        borderColor = Gold, onDismiss = onDismiss, glowDp = 18,
        banner = {
            // Bannière 180 dp : Titre / Artiste + Peak en grand
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                BlurredBackdrop(h.item.coverUrl, Gold, Modifier.fillMaxSize())
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(h.item.coverUrl, h.item.name, size = 96, circle = h.item.circle)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${h.chart.emoji} ${h.chart.label} · ${h.period.label}", color = Gold, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(h.item.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        h.item.secondary?.let { Text(it, color = Color.White.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Spacer(Modifier.height(6.dp))
                        s.peak?.let {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("PEAK ", color = Gold.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                                Text("#${it.position}", color = Gold, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 30.sp)
                                if (s.timesAtPeak > 1) Text("  ×${s.timesAtPeak}", color = Gold.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    ) {
        // Courbe (#1 en haut) + zone colorée + ⭐ au peak
        PopupSection("📈 Parcours dans le classement", Gold)
        if (positions.isEmpty()) Text("Pas encore de parcours enregistré.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        else NovaCurveChart(
            positions, Modifier.fillMaxWidth().height(170.dp), color = Gold, invertY = true, minY = 1f, maxY = maxPos,
            gridValues = listOf(1f, ((maxPos + 1) / 2).toInt().toFloat(), maxPos), peakIndex = peakIdx, showPoints = true
        )
        Spacer(Modifier.height(12.dp))
        // Badges
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PopupBadge("⭐", s.peak?.let { "#${it.position}" } ?: "—", "Peak", Gold)
            PopupBadge("📅", "${s.periodsInChart}", unit(s.periodsInChart).replaceFirstChar { it.uppercase() }, Gold)
            PopupBadge("👑", "${s.longestRunAt1}", "Série #1", Gold)
            PopupBadge("🔟", "${s.longestRunTop10}", "Série Top 10", Gold)
        }
        // Détails
        PopupSection("📋 Détails", Gold)
        PopupInfoRow("Première entrée", s.firstEntry?.let { "#${it.position} · ${it.date.format(dateFmt)}" } ?: "—")
        PopupInfoRow("Peak", s.peak?.let { "#${it.position} · ${it.date.format(dateFmt)}" } ?: "—", valueColor = Gold)
        PopupInfoRow("Total de périodes dans le chart", "${s.periodsInChart} ${unit(s.periodsInChart)}")
        PopupInfoRow("Plus longue série #1", "${s.longestRunAt1} ${unit(s.longestRunAt1)}")
        PopupInfoRow("Plus longue série Top 10", "${s.longestRunTop10} ${unit(s.longestRunTop10)}")
        PopupInfoRow("Record d'écoutes sur une période", "${formatCount(s.maxPlays)} ▶")
        if (s.currentRunAt1 > 0) PopupInfoRow("Série #1 en cours", "${s.currentRunAt1} ${unit(s.currentRunAt1)}", valueColor = Gold)
    }
}
