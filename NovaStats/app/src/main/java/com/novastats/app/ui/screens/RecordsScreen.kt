package com.novastats.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.RecordRow
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.domain.RecordCategory
import com.novastats.app.domain.RecordDef
import com.novastats.app.domain.RecordUnit
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors

/**
 * 🏅 Onglet Records — 24 records (cahier des charges). Liste principale de cartes → popup géant (90 %) scrollable
 * avec sélecteurs Périodes (Daily/Weekly/Monthly/Yearly), Sections (Song/Album/Artist) et sous-sections.
 * Les valeurs viennent de `records_cache`, recalculée automatiquement après chaque écoute et après un import JSON.
 */
@Composable
fun RecordsScreen() {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val dao = remember { app.database.recordDao() }
    val count by dao.countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val lastCalc by dao.lastCalculated().collectAsStateWithLifecycle(initialValue = null)
    var open by remember { mutableStateOf<List<RecordDef>?>(null) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }

    LazyColumn(Modifier.fillMaxSize().background(theme.background)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("🏅 Records", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
                Text(
                    if (count == 0) "Aucun record calculé pour l'instant — écoute de la musique ou importe un JSON."
                    else "${RecordCatalog.ALL.size} records · ${formatCount(count)} entrées · calculés ${formatDate(lastCalc)}",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                )
            }
        }
        items(RecordCatalog.screens) { defs -> RecordCard(defs) { open = defs } }
        item { Spacer(Modifier.height(24.dp)) }
    }

    open?.let { defs -> RecordPopup(defs, onDismiss = { open = null }, onEntity = { detail = it }) }
    DetailPopupHost(detail) { detail = null }
}

@Composable
private fun RecordCard(defs: List<RecordDef>, onClick: () -> Unit) {
    val theme = Nova.theme
    val main = defs.first()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp)).background(theme.surface)
            .border(1.dp, theme.primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(theme.primary.copy(alpha = 0.35f), theme.secondary.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center
        ) { Text(main.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("#${main.number} · " + defs.joinToString(" / ") { it.title }, color = theme.text, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(main.description, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp)) {
                if (main.periods.isNotEmpty()) Tag("📅 " + main.periods.joinToString("·") { it.label.take(1) })
                Tag(main.categories.joinToString(" ") { it.emoji })
                if (main.subs.isNotEmpty()) Tag("🧩 sous-sections")
            }
        }
        Text("›", color = theme.primary, fontSize = 26.sp)
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text, color = Nova.theme.textSecondary, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp)).background(Nova.theme.background.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/* ================================ POPUP 90 % ================================ */

@Composable
private fun RecordPopup(defs: List<RecordDef>, onDismiss: () -> Unit, onEntity: (DetailTarget) -> Unit) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val dao = remember { app.database.recordDao() }

    var def by remember { mutableStateOf(defs.first()) }
    var period by remember { mutableStateOf(def.periods.firstOrNull { it == Period.WEEKLY } ?: def.periods.firstOrNull()) }
    var category by remember { mutableStateOf(def.categories.first()) }
    var sub by remember { mutableStateOf(def.subs[category]?.firstOrNull()) }

    fun selectDef(d: RecordDef) {
        def = d
        period = d.periods.firstOrNull { it == period } ?: d.periods.firstOrNull()
        category = d.categories.firstOrNull { it == category } ?: d.categories.first()
        sub = d.subs[category]?.firstOrNull()
    }
    fun selectCategory(c: RecordCategory) { category = c; sub = def.subs[c]?.firstOrNull() }

    val rows by remember(def, period, category, sub) {
        dao.top10(def.id, period?.dbName, category.dbName, sub?.dbName, def.ascending)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    PopupScaffold(borderColor = theme.primary, onDismiss = onDismiss, heightFraction = 0.9f, fixedHeight = true, banner = {
        Column(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(theme.primary.copy(alpha = 0.55f), theme.secondary.copy(alpha = 0.35f)))).padding(16.dp)
        ) {
            Text("${def.emoji} ${def.title}", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text(def.description, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
    }) {
        if (defs.size > 1) {
            ChipRow(defs.map { "${it.emoji} ${it.title}" }, defs.indexOf(def)) { selectDef(defs[it]) }
            Spacer(Modifier.height(6.dp))
        }
        if (def.periods.isNotEmpty()) {
            Text("PÉRIODE", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            ChipRow(def.periods.map { it.label }, def.periods.indexOf(period)) { period = def.periods[it] }
            Spacer(Modifier.height(6.dp))
        }
        Text("SECTION", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        ChipRow(def.categories.map { "${it.emoji} ${sectionLabel(it)}" }, def.categories.indexOf(category)) { selectCategory(def.categories[it]) }
        val subs = def.subs[category].orEmpty()
        if (subs.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("SOUS-SECTION", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
            ChipRow(subs.map { it.label }, subs.indexOf(sub)) { sub = subs[it] }
        }
        Spacer(Modifier.height(10.dp))

        if (rows.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🕳️", fontSize = 40.sp)
                Text("Pas encore de record ici", color = theme.text, fontWeight = FontWeight.SemiBold)
                Text("Il faut plus d'historique pour cette combinaison (période · section).", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            rows.forEachIndexed { i, row -> RecordEntryRow(i + 1, row, def, period, category, onEntity) }
        }
    }
}

private fun sectionLabel(c: RecordCategory) = when (c) { RecordCategory.TRACK -> "Song"; RecordCategory.ALBUM -> "Album"; RecordCategory.ARTIST -> "Artist" }

@Composable
private fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Text(
                l, color = if (on) theme.background else theme.text, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (on) theme.primary else theme.background.copy(alpha = 0.7f))
                    .border(1.dp, if (on) theme.primary else theme.textSecondary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RecordEntryRow(rank: Int, row: RecordRow, def: RecordDef, period: Period?, category: RecordCategory, onEntity: (DetailTarget) -> Unit) {
    val theme = Nova.theme
    val posColor = when (rank) { 1 -> NovaColors.Gold; 2 -> NovaColors.Silver; 3 -> Color(0xFFCD7F32); else -> theme.textSecondary }
    val target = when (category) {
        RecordCategory.TRACK -> DetailTarget.Track(row.r.entityId)
        RecordCategory.ARTIST -> DetailTarget.Artist(row.r.entityId)
        RecordCategory.ALBUM -> DetailTarget.Album(row.r.entityId)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onEntity(target) }.padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (rank <= 3) listOf("🥇", "🥈", "🥉")[rank - 1] else "#$rank", color = posColor, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp))
        CoverArt(row.imageUrl, row.name ?: "?", size = 44, circle = category == RecordCategory.ARTIST)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.name ?: "Inconnu", color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(row.subtitle, row.r.extraData).joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatRecordValue(def, period, row.r.value), color = theme.primary, fontWeight = FontWeight.Bold)
            row.r.valueDate?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/** Valeur formatée selon l'unité du record (jours/semaines/mois adaptés à la période, écoutes, places, durée adaptative…). */
fun formatRecordValue(def: RecordDef, period: Period?, value: Double): String {
    val n = value.toLong()
    return when (def.unit) {
        RecordUnit.PERIODS -> "$n ${RecordCatalog.unitLabel(period, n > 1)}"
        RecordUnit.PLAYS -> "$n écoute${if (n > 1) "s" else ""}"
        RecordUnit.POSITIONS -> (if (def.id == "BIGGEST_FALL") "−" else "+") + "$n place${if (n > 1) "s" else ""}"
        RecordUnit.DURATION_MS -> RecordCatalog.formatDurationAdaptive(value.toLong())
        RecordUnit.TIMES -> "$n fois"
        RecordUnit.COUNT -> when (def.id) {
            "MOST_CERTIFICATIONS" -> "$n certif${if (n > 1) "s" else ""}"
            "MOST_HOF", "MOST_GLOBAL" -> "$n entrée${if (n > 1) "s" else ""}"
            "MOST_SUCCESSIVE_1" -> "$n #1 successifs"
            "MOST_SIMULTANEOUS" -> "$n simultané${if (n > 1) "s" else ""}"
            else -> "$n"
        }
    }
}
