package com.novastats.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.RecordRow
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.repository.RecordExplainer
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.domain.RecordCategory
import com.novastats.app.domain.RecordDef
import com.novastats.app.domain.RecordGroup
import com.novastats.app.domain.RecordUnit
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors

/**
 * 🏅 Onglet Records — familles de records en puces horizontales (façon niveaux de certification), une carte par record,
 * 🔍 au fond à droite (recherche d'un titre / artiste / album dans tous les records).
 * Appui sur une carte → PAGE du record (sections, périodes, sous-sections, Top 10).
 * Appui sur une ligne du Top 10 → PAGE « pourquoi il est là » spécifique au record (récit, faits, frise, éléments).
 */
@Composable
fun RecordsScreen() {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val dao = remember { app.database.recordDao() }
    val count by dao.countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val lastCalc by dao.lastCalculated().collectAsStateWithLifecycle(initialValue = null)
    var group by remember { mutableStateOf<RecordGroup?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<RecordDef?>(null) }
    var entry by remember { mutableStateOf<RecordEntryRef?>(null) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    // État de la page record (période / section / sous-filtre / scroll) conservé ici : la page quitte la composition
    // quand on ouvre une fiche d'entrée, sinon ses filtres repartaient à « Semaine / Titres » et en haut de liste.
    val pageState = remember(page) { page?.let { RecordPageState(it) } }
    // Position de la liste principale (familles / records) : conservée quand on ouvre un record puis qu'on revient
    val mainList = remember { LazyListState() }

    BackHandler(enabled = entry != null || page != null || searching) {
        when {
            entry != null -> entry = null
            page != null -> page = null
            else -> { searching = false; query = "" }
        }
    }

    when {
        entry != null -> RecordEntryPage(entry!!, onBack = { entry = null }, onEntity = { detail = it })
        page != null -> RecordPage(page!!, pageState!!, onBack = { page = null }, onEntry = { entry = it })
        else -> LazyColumn(Modifier.fillMaxSize().background(theme.background), state = mainList) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("🏅 Records", style = MaterialTheme.typography.headlineSmall, color = theme.text, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        // 🔍 au fond à droite, bien écarté du titre
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(if (searching) theme.primary else theme.surface)
                                .border(1.dp, theme.primary.copy(alpha = 0.5f), CircleShape)
                                .clickable { searching = !searching; if (!searching) query = "" },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Search, contentDescription = "Rechercher", tint = if (searching) theme.background else theme.primary) }
                    }
                    Text(
                        if (count == 0) "Aucun record calculé pour l'instant — écoute de la musique ou importe un JSON."
                        else "${RecordCatalog.ALL.size} records · ${formatCount(count)} entrées · calculés ${formatDate(lastCalc)}",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (searching) {
                item {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        placeholder = { Text("🔍 Un titre, un artiste, un album… dans tous les records") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item { RecordSearchResults(query) { entry = it } }
            } else {
                item {
                    // Familles : puces horizontales façon niveaux de certification, avec le nombre de records
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = group == null, onClick = { group = null }, label = { Text("Tous (${RecordCatalog.ALL.size})") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.primary.copy(alpha = 0.25f), selectedLabelColor = theme.text)
                        )
                        RecordGroup.entries.forEach { g ->
                            val c = groupColor(g)
                            FilterChip(
                                selected = group == g, onClick = { group = if (group == g) null else g },
                                label = { Text("${g.emoji} ${g.label} (${RecordCatalog.inGroup(g).size})") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.3f), selectedLabelColor = theme.text),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = group == g, borderColor = c.copy(alpha = 0.5f), selectedBorderColor = c)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                val groups = if (group == null) RecordGroup.entries.toList() else listOf(group!!)
                groups.forEach { g ->
                    item(key = "h_${g.name}") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("${g.emoji} ${g.label}", color = groupColor(g), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(g.description, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    items(RecordCatalog.inGroup(g), key = { it.id }) { def -> RecordCard(def, groupColor(g)) { page = def } }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    DetailPopupHost(detail) { detail = null }
}

/** Couleur d'une famille (dérivée du thème pour rester cohérent avec les 15 thèmes). */
@Composable
private fun groupColor(g: RecordGroup): Color {
    val t = Nova.theme
    return when (g) {
        RecordGroup.DURATION -> t.primary
        RecordGroup.MOVEMENT -> t.secondary
        RecordGroup.DEBUT -> t.accent
        RecordGroup.SPEED -> NovaColors.Gold
        RecordGroup.TOTALS -> t.glowSecondary
        RecordGroup.DOMINATION -> Color(0xFF9B59B6)
    }
}

@Composable
private fun RecordCard(def: RecordDef, color: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp)).background(theme.surface)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(color.copy(alpha = 0.4f), theme.secondary.copy(alpha = 0.25f)))),
            contentAlignment = Alignment.Center
        ) { Text(def.emoji, fontSize = 22.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(def.title, color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(def.description, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp)) {
                if (def.periods.isNotEmpty()) Tag("📅 " + def.periods.joinToString("·") { it.label.take(1) })
                Tag(def.categories.joinToString(" ") { it.emoji })
                if (def.subs.isNotEmpty()) Tag("🧩 sous-sections")
            }
        }
        Text("›", color = color, fontSize = 26.sp)
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text, color = Nova.theme.textSecondary, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(6.dp)).background(Nova.theme.background.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/* ================================ 🔍 Recherche dans tous les records ================================ */

@Composable
private fun RecordSearchResults(query: String, onOpen: (RecordEntryRef) -> Unit) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    var rows by remember { mutableStateOf<List<RecordRow>>(emptyList()) }
    LaunchedEffect(query) {
        rows = if (query.trim().length < 2) emptyList() else runCatching { app.database.recordDao().search(query.trim()) }.getOrDefault(emptyList())
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        when {
            query.trim().length < 2 -> Text("Tape au moins 2 lettres.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            rows.isEmpty() -> Text("Aucun record ne contient « ${query.trim()} ».", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            else -> {
                Text("${rows.size} ${if (rows.size > 1) "résultats" else "résultat"}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                rows.forEach { row ->
                    val def = RecordCatalog.byId(row.r.recordType) ?: return@forEach
                    val period = Period.entries.firstOrNull { it.dbName == row.r.periodType }
                    val cat = RecordCategory.entries.firstOrNull { it.dbName == row.r.category } ?: RecordCategory.TRACK
                    val subLabel = def.subs[cat]?.firstOrNull { it.dbName == row.r.subcategory }?.label
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(theme.surface)
                            .clickable { onOpen(RecordEntryRef(def, period, cat, row.r.subcategory, row, rank = null)) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoverArt(row.imageUrl, row.name ?: "?", size = 40, circle = cat == RecordCategory.ARTIST)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.name ?: "Inconnu", color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull("${def.emoji} ${def.title}", period?.label, sectionLabel(cat), subLabel).joinToString(" · "),
                                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(formatRecordValue(def, period, row.r.value), color = theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/* ================================ 📄 Page d'un record (ex-popup 90 %) ================================ */

/** Référence d'une ligne de Top 10 : tout ce qu'il faut pour la page « pourquoi il est là ». */
data class RecordEntryRef(val def: RecordDef, val period: Period?, val category: RecordCategory, val sub: String?, val row: RecordRow, val rank: Int?)

@Composable
private fun PageHeader(back: String, title: String, color: Color, onBack: () -> Unit, subtitle: String? = null) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.45f), theme.secondary.copy(alpha = 0.25f)))).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹ $back", color = theme.text, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 4.dp))
        }
        Text(title, color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

/** Filtres + position de scroll d'une page record, hoistés dans RecordsScreen pour survivre à l'ouverture d'une fiche. */
private class RecordPageState(def: RecordDef) {
    var period by mutableStateOf(def.periods.firstOrNull { it == Period.WEEKLY } ?: def.periods.firstOrNull())
    var category by mutableStateOf(def.categories.first())
    var sub by mutableStateOf(def.subs[category]?.firstOrNull())
    val list = LazyListState()
}

@Composable
private fun RecordPage(def: RecordDef, st: RecordPageState, onBack: () -> Unit, onEntry: (RecordEntryRef) -> Unit) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val dao = remember { app.database.recordDao() }
    val color = groupColor(RecordCatalog.groupOf[def.id] ?: RecordGroup.DURATION)

    var period by st::period
    var category by st::category
    var sub by st::sub
    fun selectCategory(c: RecordCategory) { category = c; sub = def.subs[c]?.firstOrNull() }

    val rows by remember(def, period, category, sub) {
        dao.top10(def.id, period?.dbName, category.dbName, sub?.dbName, def.ascending)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val maxValue = remember(rows) { rows.maxOfOrNull { it.r.value } ?: 0.0 }

    LazyColumn(Modifier.fillMaxSize().background(theme.background), state = st.list) {
        item { PageHeader("Records", "${def.emoji} ${def.title}", color, onBack, def.description) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                // Sections Titres / Artistes / Albums
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.surface.copy(alpha = 0.8f)).padding(3.dp)) {
                    def.categories.forEach { c ->
                        val on = c == category
                        Text(
                            "${c.emoji} ${sectionLabel(c)}", color = if (on) theme.background else theme.text, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) color else Color.Transparent).clickable { selectCategory(c) }.padding(vertical = 8.dp)
                        )
                    }
                }
                if (def.periods.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    ChipRow(def.periods.map { it.label }, def.periods.indexOf(period), color) { period = def.periods[it] }
                }
                val subs = def.subs[category].orEmpty()
                if (subs.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    ChipRow(subs.map { it.label }, subs.indexOf(sub), color) { sub = subs[it] }
                }
                Spacer(Modifier.height(4.dp))
                Text("Appuie sur une ligne pour voir pourquoi l'élément est là.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (rows.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🕳️", fontSize = 40.sp)
                Text("Pas encore de record ici", color = theme.text, fontWeight = FontWeight.SemiBold)
                Text("Il faut plus d'historique pour cette combinaison (période · section).", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        } else {
            items(rows.size) { i ->
                val row = rows[i]
                Box(Modifier.padding(horizontal = 14.dp)) {
                    RecordEntryRow(i + 1, row, def, period, category, maxValue, color) { onEntry(RecordEntryRef(def, period, category, sub?.dbName, row, i + 1)) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun sectionLabel(c: RecordCategory) = when (c) { RecordCategory.TRACK -> "Titres"; RecordCategory.ALBUM -> "Albums"; RecordCategory.ARTIST -> "Artistes" }

@Composable
private fun ChipRow(labels: List<String>, selected: Int, color: Color, onSelect: (Int) -> Unit) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Text(
                l, color = if (on) theme.background else theme.text, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (on) color else theme.background.copy(alpha = 0.7f))
                    .border(1.dp, if (on) color else theme.textSecondary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun RecordEntryRow(rank: Int, row: RecordRow, def: RecordDef, period: Period?, category: RecordCategory, maxValue: Double, color: Color, onClick: () -> Unit) {
    val theme = Nova.theme
    val first = rank == 1
    val posColor = when (rank) { 1 -> NovaColors.Gold; 2 -> NovaColors.Silver; 3 -> Color(0xFFCD7F32); else -> theme.textSecondary }
    // Barre proportionnelle au max (ex. 47 semaines = 100 %) ; records « ascendants » (plus petit = mieux) → inversé
    val fraction = if (maxValue <= 0.0) 0f else if (def.ascending) (1.0 - (row.r.value / maxValue) * 0.5).toFloat().coerceIn(0.1f, 1f) else (row.r.value / maxValue).toFloat().coerceIn(0.02f, 1f)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
            .then(if (first) Modifier.background(Brush.horizontalGradient(listOf(NovaColors.Gold.copy(alpha = 0.18f), Color.Transparent))).border(1.dp, NovaColors.Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp)) else Modifier.background(theme.surface.copy(alpha = 0.5f)))
            .clickable(onClick = onClick).padding(vertical = if (first) 10.dp else 6.dp, horizontal = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (rank <= 3) listOf("🥇", "🥈", "🥉")[rank - 1] else "#$rank", color = posColor, fontWeight = FontWeight.Bold, fontSize = if (first) 22.sp else 14.sp, modifier = Modifier.width(36.dp))
            CoverArt(row.imageUrl, row.name ?: "?", size = if (first) 52 else 44, circle = category == RecordCategory.ARTIST)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name ?: "Inconnu", color = if (first) NovaColors.Gold else theme.text, fontWeight = if (first) FontWeight.Black else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subText = listOfNotNull(row.subtitle, row.r.extraData).joinToString(" · ")
                if (subText.isNotBlank()) Text(subText, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRecordValue(def, period, row.r.value), color = if (first) NovaColors.Gold else color, fontWeight = FontWeight.Bold, fontSize = if (first) 17.sp else 14.sp)
                row.r.valueDate?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall) }
            }
            Text("›", color = theme.textSecondary, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, start = 36.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(theme.textSecondary.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(if (first) Brush.horizontalGradient(listOf(color, NovaColors.Gold)) else Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f)))))
        }
    }
}

/* ================================ 🧐 Page « pourquoi il est là » — spécifique à chaque record ================================ */

@Composable
private fun RecordEntryPage(ref: RecordEntryRef, onBack: () -> Unit, onEntity: (DetailTarget) -> Unit) {
    val theme = Nova.theme
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val def = ref.def; val row = ref.row
    val color = groupColor(RecordCatalog.groupOf[def.id] ?: RecordGroup.DURATION)
    val name = row.name ?: "Inconnu"
    var story by remember(ref) { mutableStateOf<RecordExplainer.Story?>(null) }
    LaunchedEffect(ref) {
        story = app.recordExplainer.explain(def, ref.period, ref.category, ref.sub, row.r.entityId, name, row.r.value)
    }
    val target = when (ref.category) {
        RecordCategory.TRACK -> DetailTarget.Track(row.r.entityId)
        RecordCategory.ARTIST -> DetailTarget.Artist(row.r.entityId)
        RecordCategory.ALBUM -> DetailTarget.Album(row.r.entityId)
    }
    val subLabel = def.subs[ref.category]?.firstOrNull { it.dbName == ref.sub }?.label
    val context = listOfNotNull(ref.period?.label, sectionLabel(ref.category), subLabel).joinToString(" · ")

    LazyColumn(Modifier.fillMaxSize().background(theme.background)) {
        item { PageHeader(def.title, "${def.emoji} ${def.title}", color, onBack, context) }
        item {
            // En-tête : image (appui → plein écran), nom, rang, valeur
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CoverArt(row.imageUrl, name, size = 110, circle = ref.category == RecordCategory.ARTIST, zoomable = true)
                Spacer(Modifier.height(10.dp))
                Text(name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
                row.subtitle?.let { Text(it, color = theme.textSecondary, textAlign = TextAlign.Center) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ref.rank?.let { r ->
                        val medal = if (r <= 3) listOf("🥇", "🥈", "🥉")[r - 1] else "#$r"
                        Text(medal + if (r > 3) " du classement" else "", color = if (r == 1) NovaColors.Gold else theme.textSecondary, fontWeight = FontWeight.Bold)
                    }
                    Text(formatRecordValue(def, ref.period, row.r.value), color = color, fontWeight = FontWeight.Black, fontSize = 24.sp)
                }
                row.r.extraData?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
            }
        }
        val st = story
        if (st == null) {
            item { Text("Reconstruction de l'histoire…", color = theme.textSecondary, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
        } else {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SectionTitle("🧐 Pourquoi il est là", color)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.surface).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text(st.headline, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(st.narrative, color = theme.text, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                    }
                }
            }
            if (st.facts.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SectionTitle("📋 Les faits", color)
                    st.facts.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                            Text(k, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.45f))
                            Text(v, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.55f), textAlign = TextAlign.End)
                        }
                    }
                }
            }
            if (st.timeline.isNotEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SectionTitle("📈 " + (st.timelineTitle ?: "Frise"), color)
                    Timeline(st.timeline, color)
                }
            }
            if (st.items.isNotEmpty()) {
                item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("🎵 " + (st.itemsTitle ?: "Éléments"), color) } }
                items(st.items.size) { i ->
                    val ent = st.items[i]
                    val t = when (ent.type) { EntityType.TRACK -> DetailTarget.Track(ent.id); EntityType.ALBUM -> DetailTarget.Album(ent.id); else -> DetailTarget.Artist(ent.id) }
                    val showImage = ent.imageUrl != null || ent.id != row.r.entityId
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (ent.highlight) color.copy(alpha = 0.10f) else theme.surface.copy(alpha = 0.6f))
                            .clickable { onEntity(t) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showImage) { CoverArt(ent.imageUrl, ent.name, size = 40, circle = ent.type == EntityType.ARTIST); Spacer(Modifier.width(10.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(ent.name, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ent.detail, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Voir la fiche complète ›", color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, color, RoundedCornerShape(12.dp)).clickable { onEntity(target) }.padding(vertical = 12.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** Frise horizontale : une colonne par période (date, position, écoutes), les périodes clés en surbrillance. */
@Composable
private fun Timeline(points: List<RecordExplainer.Point>, color: Color) {
    val theme = Nova.theme
    val maxPlays = points.maxOfOrNull { it.plays ?: 0 } ?: 0
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        points.forEach { pt ->
            Column(
                Modifier.width(if (pt.note != null) 96.dp else 64.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (pt.highlight) color.copy(alpha = 0.22f) else theme.surface)
                    .border(1.dp, if (pt.highlight) color else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(pt.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                pt.position?.let { Text("#$it", color = if (it == 1) NovaColors.Gold else if (pt.highlight) color else theme.text, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                pt.plays?.let { pl ->
                    Text("$pl ▶", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    // Mini-barre d'écoutes
                    Box(Modifier.padding(top = 3.dp).width(48.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(theme.textSecondary.copy(alpha = 0.15f))) {
                        Box(Modifier.fillMaxWidth(if (maxPlays > 0) (pl.toFloat() / maxPlays).coerceIn(0.03f, 1f) else 0f).height(3.dp).background(color))
                    }
                }
                pt.note?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center, fontSize = 9.sp) }
            }
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
