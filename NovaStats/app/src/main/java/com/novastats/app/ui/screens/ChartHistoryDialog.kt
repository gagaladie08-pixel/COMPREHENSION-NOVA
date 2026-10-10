package com.novastats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novastats.app.domain.BillboardDates
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Or fixe du popup Billboard — indépendant du thème (POPUPS.md). */
private val Gold = Color(0xFFFFD700)
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)

/** Libellé court pour l'axe X de la courbe (« 3 oct. ») — la forme longue débordait du graphique. */
private val axisFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

/** Une seule phrase qui raconte tout le parcours — l'âme de la fiche. */
fun storySentence(h: EntityHistory): String {
    val s = h.stats
    val unit = { n: Int -> BillboardDates.unitLabel(h.period, n) }
    val first = s.firstEntry ?: return "Pas encore de parcours dans ce classement."
    val sb = StringBuilder("Entré #${first.position} le ${first.date.format(dateFmt)}")
    if (first.position == 1) sb.append(" — entrée directe")
    s.peak?.takeIf { it.date != first.date }?.let { sb.append(", pic #${it.position} le ${it.date.format(dateFmt)}") }
    sb.append(", ${s.periodsInChart} ${unit(s.periodsInChart)} au total")
    if (s.longestRunAt1 > 1) sb.append(" dont ${s.longestRunAt1} d'affilée au sommet")
    if (s.currentRunAt1 > 0) sb.append(" — série #1 en cours : ${s.currentRunAt1}")
    return sb.append(".").toString()
}

/** Petite pilule de sélection (mode de lecture / fenêtre de la courbe). */
@Composable
private fun SelPill(label: String, on: Boolean, onClick: () -> Unit) {
    val theme = Nova.theme
    Text(
        label,
        color = if (on) theme.background else theme.textSecondary,
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (on) Gold else Gold.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * 4. 🏆 Popup Billboard (fiche historique, appui long sur une ligne) :
 * bannière + peak en grand, courbe à deux lectures (# / ▶) avec jalons 🚀↩🔥 et fenêtre 12 dernières
 * périodes, phrase-histoire, badges, détails, palmarès (certifs / Panthéon / Hall of Fame),
 * face-à-face ⚔️ (seconde courbe argent) et carte partageable 📤.
 */
@Composable
fun ChartHistoryDialog(
    h: EntityHistory, onDismiss: () -> Unit,
    compare: EntityHistory? = null,
    candidates: List<ChartItem> = emptyList(),
    onCompare: (ChartItem) -> Unit = {},
    onClearCompare: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    val theme = Nova.theme
    val s = h.stats
    val unit = { n: Int -> BillboardDates.unitLabel(h.period, n) }
    var playsMode by remember { mutableStateOf(false) }
    var windowed by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    val byDate = remember(h) { h.appearances.associateBy { it.date } }
    val anchorsAll = remember(h) { h.anchors.filter { a -> !a.isBefore(s.firstEntry?.date ?: a) } }
    val anchors = if (windowed) anchorsAll.takeLast(12) else anchorsAll
    val values = remember(anchors, byDate, playsMode) {
        anchors.map { byDate[it]?.let { a -> if (playsMode) a.playCount.toFloat() else a.position.toFloat() } }
    }
    val maxPos = remember(h) { (h.appearances.maxOfOrNull { it.position } ?: h.chart.limit(h.period)).coerceAtLeast(5).toFloat() }
    val peakIdx = remember(values, playsMode) {
        values.indices.filter { values[it] != null }
            .minByOrNull { if (playsMode) -values[it]!! else values[it]!! }
    }
    // Jalons : 🚀 première entrée (⚡ si directe), ↩ réentrée (trou dans les ancres), 🔥 record d'écoutes
    val markers = remember(h, anchors) {
        val idxAll = anchorsAll.withIndex().associate { it.value to it.index }
        val idx = anchors.withIndex().associate { it.value to it.index }
        buildMap {
            s.firstEntry?.let { fe -> idx[fe.date]?.let { put(it, if (fe.position == 1) "⚡" else "🚀") } }
            val sorted = h.appearances.sortedBy { it.date }
            sorted.zipWithNext().forEach { (a, b) ->
                if ((idxAll[b.date] ?: 0) - (idxAll[a.date] ?: 0) > 1) idx[b.date]?.let { put(it, "↩") }
            }
            h.appearances.maxByOrNull { it.playCount }?.takeIf { it.playCount > 1 }?.let { r -> idx[r.date]?.let { put(it, "🔥") } }
        }
    }
    // Courbe de comparaison alignée sur les mêmes ancres (positions uniquement)
    val overlay = remember(compare, anchors, playsMode) {
        if (compare == null || playsMode) null else {
            val m = compare.appearances.associateBy { it.date }
            anchors.map { m[it]?.position?.toFloat() }
        }
    }

    NovaPopupCard(
        borderColor = Gold, onDismiss = onDismiss, glowDp = 18, backdropUrl = h.item.coverUrl,
        banner = {
            // Bannière 180 dp : Titre / Artiste + Peak en grand
            Box(Modifier.fillMaxWidth().height(180.dp)) {
                BlurredBackdrop(h.item.coverUrl, Gold, Modifier.fillMaxSize())
                Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(h.item.coverUrl, h.item.name, size = 96, circle = h.item.circle, zoomable = true)
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
        // Sélecteurs : lecture (# / ▶) + fenêtre (tout / 12 dernières)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SelPill("#", !playsMode) { playsMode = false }
            SelPill("▶ écoutes", playsMode) { playsMode = true }
            Spacer(Modifier.width(10.dp))
            SelPill("Tout", !windowed) { windowed = false }
            SelPill("12 dern.", windowed) { windowed = true }
        }
        Spacer(Modifier.height(8.dp))
        PopupSection(if (playsMode) "▶ Écoutes par période" else "📈 Parcours dans le classement", Gold)
        if (values.all { it == null }) Text("Pas encore de parcours enregistré.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        else NovaCurveChart(
            values, Modifier.fillMaxWidth().height(170.dp), color = Gold, invertY = !playsMode,
            minY = if (playsMode) 0f else 1f, maxY = if (playsMode) null else maxPos,
            gridValues = if (playsMode) emptyList() else listOf(1f, ((maxPos + 1) / 2).toInt().toFloat(), maxPos),
            peakIndex = peakIdx, showPoints = true, markers = markers,
            overlay = overlay, overlayColor = NovaColors.Silver,
            xLabels = anchors.map { it.format(axisFmt) }
        )
        if (compare != null && overlay != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Gold))
                    Spacer(Modifier.width(4.dp))
                    Text(h.item.name, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(NovaColors.Silver))
                    Spacer(Modifier.width(4.dp))
                    Text(compare.item.name, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // 📖 La phrase-histoire
        PopupSection("📖 L'histoire", Gold)
        Text(storySentence(h), color = theme.text, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
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
        // 🏅 L'autre moitié de la carte : certifications, Panthéon, Hall of Fame
        val ex = h.extras
        if (ex.certLines.isNotEmpty() || ex.pantheonLabel != null || ex.hofLines.isNotEmpty()) {
            PopupSection("🏅 Palmarès", Gold)
            ex.pantheonLabel?.let { PopupInfoRow("Panthéon", it, valueColor = Gold) }
            ex.certLines.forEach { PopupInfoRow("Certification", it, valueColor = Gold) }
            ex.hofLines.forEach { PopupInfoRow("Hall of Fame", it, valueColor = Gold) }
        }
        // ⚔️ Face-à-face
        PopupSection("⚔️ Face-à-face", Gold)
        if (compare != null) {
            Text("Courbe argent : « ${compare.item.name} »", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClearCompare) { Text("Retirer la comparaison", color = Gold, fontWeight = FontWeight.Bold) }
        } else if (!picking) {
            TextButton(onClick = { picking = true }) { Text("Choisir un adversaire", color = Gold, fontWeight = FontWeight.Bold) }
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                items(candidates.filter { it.entityId != h.item.entityId }.take(50), key = { it.entityId }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { picking = false; onCompare(c) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#${c.position}", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                        Text(c.name, color = theme.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // 📤 Partager le parcours
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onShare) { Text("📤 Partager ce parcours", color = Gold, fontWeight = FontWeight.Bold) }
        }
    }
}
