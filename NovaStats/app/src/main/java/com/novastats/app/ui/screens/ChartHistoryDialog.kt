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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Or fixe du popup Billboard — indépendant du thème (POPUPS.md). */
private val Gold = Color(0xFFFFD700)
private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)

/** Libellé court pour l'axe X de la courbe (« 3 oct. ») — la forme longue débordait du graphique. */
private val axisFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE)

/** Les snapshots Global sont hebdomadaires même si le chart affiche un cumul all-time. */
private fun chartPeriodUnit(period: Period, count: Int): String =
    if (period == Period.GLOBAL) {
        if (count == 1) "semaine" else "semaines"
    } else BillboardDates.unitLabel(period, count)

private fun previousPositionLabel(period: Period): String = when (period) {
    Period.DAILY -> "Position la veille"
    Period.WEEKLY, Period.GLOBAL -> "Position de la semaine précédente"
    Period.MONTHLY -> "Position du mois précédent"
    Period.YEARLY -> "Position de l'année précédente"
}

/** Une seule phrase qui raconte tout le parcours — l'âme de la fiche. */
fun storySentence(h: EntityHistory): String {
    val s = h.stats
    val unit = { n: Int -> chartPeriodUnit(h.period, n) }
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
private fun SelPill(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Text(
        label,
        color = if (on) theme.background else theme.textSecondary,
        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(8.dp))
            .background(if (on) Gold else Gold.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

/**
 * 4. 🏆 Popup Billboard (fiche historique, toucher une ligne) :
 * bannière + peak en grand, courbe à deux lectures (# / ▶) avec jalons 🚀↩🔥 et fenêtre 12 dernières
 * périodes, phrase-histoire, badges, détails, palmarès (certifs / Panthéon / Hall of Fame),
 * face-à-face ⚔️ (seconde courbe argent) et carte partageable 📤.
 */
@Composable
fun ChartHistoryDialog(
    h: EntityHistory, onDismiss: () -> Unit,
    compare: EntityHistory? = null,
    candidates: List<ChartItem> = emptyList(),
    navigationCandidates: List<ChartItem> = emptyList(),
    onCompare: (ChartItem) -> Unit = {},
    onClearCompare: () -> Unit = {},
    onShare: () -> Unit = {},
    onNavigate: (ChartItem) -> Unit = {},
    onViewPeriod: (LocalDate) -> Unit = {}
) {
    val theme = Nova.theme
    val s = h.stats
    val unit = { n: Int -> chartPeriodUnit(h.period, n) }
    var playsMode by remember { mutableStateOf(false) }
    var windowed by remember { mutableStateOf(false) }
    var picking by remember(h) { mutableStateOf(false) }
    var compareQuery by remember(h) { mutableStateOf("") }
    var detailsExpanded by remember(h) { mutableStateOf(false) }
    var honorsExpanded by remember(h) { mutableStateOf(false) }
    var compareExpanded by remember(h, compare) { mutableStateOf(compare != null) }
    val currentCandidateIndex = navigationCandidates.indexOfFirst { it.entityId == h.item.entityId }

    val byDate = remember(h) { h.appearances.associateBy { it.date } }
    val compareByDate = remember(compare) { compare?.appearances?.associateBy { it.date }.orEmpty() }
    val anchorsAll = remember(h) { h.anchors.filter { a -> !a.isBefore(s.firstEntry?.date ?: a) } }
    val anchors = if (windowed) anchorsAll.takeLast(12) else anchorsAll
    var selectedPoint by remember(h, anchors, playsMode) { mutableStateOf(anchors.lastIndex.takeIf { it >= 0 }) }
    val values = remember(anchors, byDate, playsMode) {
        anchors.map { byDate[it]?.let { a -> if (playsMode) a.playCount.toFloat() else a.position.toFloat() } }
    }
    val maxPos = remember(h, compare) {
        maxOf(
            h.appearances.maxOfOrNull { it.position } ?: h.chart.limit(h.period),
            compare?.appearances?.maxOfOrNull { it.position } ?: 1
        ).coerceAtLeast(5).toFloat()
    }
    val maxPlays = remember(h, compare) {
        maxOf(h.appearances.maxOfOrNull { it.playCount } ?: 0, compare?.appearances?.maxOfOrNull { it.playCount } ?: 0)
            .coerceAtLeast(1).toFloat()
    }
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
    // Courbe de comparaison alignée sur les mêmes ancres, en position comme en écoutes.
    val overlay = remember(compare, anchors, playsMode, compareByDate) {
        if (compare == null) null else anchors.map { date ->
            compareByDate[date]?.let { if (playsMode) it.playCount.toFloat() else it.position.toFloat() }
        }
    }
    val visibleCandidates = remember(candidates, compareQuery, h.item.entityId) {
        candidates.filter { it.entityId != h.item.entityId }.filter { candidate ->
            compareQuery.isBlank() || candidate.name.contains(compareQuery, ignoreCase = true) ||
                candidate.secondary?.contains(compareQuery, ignoreCase = true) == true
        }
    }
    val selectedDate = selectedPoint?.let { anchors.getOrNull(it) }
    val selectedAppearance = selectedDate?.let { byDate[it] }
    val selectedCompareAppearance = selectedDate?.let { compareByDate[it] }

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
        if (currentCandidateIndex >= 0 && navigationCandidates.size > 1) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onNavigate(navigationCandidates[currentCandidateIndex - 1]) },
                    enabled = currentCandidateIndex > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("‹ Précédent", color = if (currentCandidateIndex > 0) Gold else Gold.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(
                    "${currentCandidateIndex + 1} / ${navigationCandidates.size}",
                    color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
                )
                TextButton(
                    onClick = { onNavigate(navigationCandidates[currentCandidateIndex + 1]) },
                    enabled = currentCandidateIndex < navigationCandidates.lastIndex,
                    modifier = Modifier.weight(1f)
                ) { Text("Suivant ›", color = if (currentCandidateIndex < navigationCandidates.lastIndex) Gold else Gold.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }

        // Sélecteurs en deux lignes : mode de lecture puis fenêtre de l'historique.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelPill("Position", !playsMode, { playsMode = false }, Modifier.weight(1f))
            SelPill("Écoutes", playsMode, { playsMode = true }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelPill("Tout l'historique", !windowed, { windowed = false }, Modifier.weight(1f))
            SelPill("12 dernières périodes", windowed, { windowed = true }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        PopupSection(if (playsMode) "▶ Écoutes par période" else "📈 Parcours dans le classement", Gold)
        if (values.all { it == null }) Text("Pas encore de parcours enregistré.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        else NovaCurveChart(
            values, Modifier.fillMaxWidth().height(170.dp), color = Gold, invertY = !playsMode,
            minY = if (playsMode) 0f else 1f, maxY = if (playsMode) maxPlays * 1.06f else maxPos,
            gridValues = if (playsMode) emptyList() else listOf(1f, ((maxPos + 1) / 2).toInt().toFloat(), maxPos),
            peakIndex = peakIdx, showPoints = true, markers = markers,
            overlay = overlay, overlayColor = NovaColors.Silver,
            xLabels = anchors.map { it.format(axisFmt) },
            selectedIndex = selectedPoint,
            onPointSelected = { selectedPoint = it }
        )
        if (selectedDate != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp)).background(theme.surface.copy(alpha = 0.82f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(BillboardDates.label(h.period, selectedDate), color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    val pointSummary = selectedAppearance?.let { appearance ->
                        if (playsMode) "${formatCount(appearance.playCount)} écoutes · #${appearance.position}"
                        else "#${appearance.position} · ${formatCount(appearance.playCount)} écoutes"
                    } ?: "Hors classement sur cette période"
                    Text(pointSummary, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    if (compare != null) {
                        val compareSummary = selectedCompareAppearance?.let { appearance ->
                            if (playsMode) "${formatCount(appearance.playCount)} écoutes · #${appearance.position}"
                            else "#${appearance.position} · ${formatCount(appearance.playCount)} écoutes"
                        } ?: "hors classement"
                        Text("↔ ${compare.item.name} · $compareSummary", color = NovaColors.Silver, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (selectedAppearance != null) {
                    TextButton(onClick = { onViewPeriod(selectedDate) }) {
                        Text("Voir ce classement", color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            Text(
                "Touchez ou faites glisser sur la courbe pour explorer les périodes.",
                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
        if (compare != null && overlay != null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Gold))
                    Spacer(Modifier.width(4.dp))
                    Text(h.item.name, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
        // Le partage reste proche du récit, plutôt que caché au fond des statistiques.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onShare) { Text("📤 Partager ce parcours", color = Gold, fontWeight = FontWeight.Bold) }
        }

        PopupDisclosure("📋 Statistiques & détails", detailsExpanded, { detailsExpanded = !detailsExpanded }) {
            PopupInfoRow("Première entrée", s.firstEntry?.let { "#${it.position} · ${it.date.format(dateFmt)}" } ?: "—")
            PopupInfoRow("Peak", s.peak?.let { "#${it.position} · ${it.date.format(dateFmt)}" } ?: "—", valueColor = Gold)
            PopupInfoRow(previousPositionLabel(h.period), h.item.previousPosition?.let { "#$it" } ?: "—")
            PopupInfoRow("Total sur le chart", "${s.periodsInChart} ${unit(s.periodsInChart)}")
            PopupInfoRow(
                "Streak actuelle sur le chart",
                s.currentRunInChart.takeIf { it > 0 }?.let { "$it ${unit(it)}" } ?: "—"
            )
            PopupInfoRow("Plus longue série #1", "${s.longestRunAt1} ${unit(s.longestRunAt1)}")
            PopupInfoRow("Plus longue série Top 10", "${s.longestRunTop10} ${unit(s.longestRunTop10)}")
            PopupInfoRow("Record d'écoutes sur une période", "${formatCount(s.maxPlays)} ▶")
            if (s.currentRunAt1 > 0) PopupInfoRow("Série #1 en cours", "${s.currentRunAt1} ${unit(s.currentRunAt1)}", valueColor = Gold)
        }

        // 🏅 Certifications, Panthéon et Hall of Fame restent consultables sans allonger la vue par défaut.
        val ex = h.extras
        if (ex.certLines.isNotEmpty() || ex.pantheonLabel != null || ex.hofLines.isNotEmpty()) {
            val honorsCount = ex.certLines.size + ex.hofLines.size + if (ex.pantheonLabel != null) 1 else 0
            PopupDisclosure("🏅 Palmarès · $honorsCount", honorsExpanded, { honorsExpanded = !honorsExpanded }) {
                ex.pantheonLabel?.let { PopupInfoRow("Panthéon", it, valueColor = Gold) }
                ex.certLines.forEach { PopupInfoRow("Certification", it, valueColor = Gold) }
                ex.hofLines.forEach { PopupInfoRow("Hall of Fame", it, valueColor = Gold) }
            }
        }

        // ⚔️ Comparaison : recherche complète, accès direct aux candidats et courbes alignées dans les deux modes.
        PopupDisclosure(
            if (compare == null) "⚔️ Comparer un parcours" else "⚔️ Face-à-face · ${compare.item.name}",
            compareExpanded,
            {
                compareExpanded = !compareExpanded
                if (!compareExpanded) picking = false
                else if (compare == null) picking = true
            }
        ) {
            if (compare != null) {
                Text(
                    if (playsMode) "Les courbes dorée et argentée montrent les écoutes par période."
                    else "Courbe dorée : ${h.item.name} · courbe argentée : ${compare.item.name}",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { picking = !picking; if (!picking) compareQuery = "" }) {
                        Text(if (picking) "Masquer la recherche" else "Changer l'adversaire", color = Gold, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { picking = false; compareQuery = ""; onClearCompare() }) {
                        Text("Retirer", color = theme.textSecondary)
                    }
                }
            } else if (!picking) {
                Text("Choisis une entrée pour comparer son parcours au même chart.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { picking = true }) { Text("Choisir un adversaire", color = Gold, fontWeight = FontWeight.Bold) }
            }

            if (picking) {
                OutlinedTextField(
                    value = compareQuery, onValueChange = { compareQuery = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    placeholder = { Text("Rechercher un titre, un artiste…", color = theme.textSecondary) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Gold) },
                    trailingIcon = {
                        if (compareQuery.isNotEmpty()) TextButton(onClick = { compareQuery = "" }) { Text("Effacer", color = Gold) }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = theme.text),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold, unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f), cursorColor = Gold
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                if (visibleCandidates.isEmpty()) {
                    Text("Aucun candidat ne correspond à cette recherche.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                        items(visibleCandidates, key = { it.entityId }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable { picking = false; compareQuery = ""; onCompare(c) }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    c.secondary?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("#${c.officialPosition}", color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text("${formatCount(c.plays)} ▶", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Section repliable : conserve les détails du popup tout en laissant le récit et la courbe respirer. */
@Composable
private fun PopupDisclosure(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(theme.surface.copy(alpha = 0.62f))
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (expanded) "−" else "+", color = Gold, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        if (expanded) Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp, end = 4.dp)) { content() }
    }
}
