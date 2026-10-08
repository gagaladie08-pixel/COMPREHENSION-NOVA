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
import androidx.compose.ui.draw.shadow
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
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.domain.Dates
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.domain.RecordCategory
import com.novastats.app.domain.RecordDef
import com.novastats.app.domain.RecordGroup
import com.novastats.app.domain.RecordUnit
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.prideOnFlag
import com.novastats.app.ui.theme.prideFlagFor
import com.novastats.app.ui.theme.prideChip
import com.novastats.app.ui.theme.isPride
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
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
    val db = app.database
    val dao = remember { app.database.recordDao() }
    val count by dao.countFlow().collectAsStateWithLifecycle(initialValue = 0)
    val lastCalc by dao.lastCalculated().collectAsStateWithLifecycle(initialValue = null)
    var group by remember { mutableStateOf<RecordGroup?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<RecordDef?>(null) }
    // Pile de fiches : depuis la fiche Most Records on ouvre les classements détenus, retour = fiche précédente
    var entryStack by remember { mutableStateOf<List<RecordEntryRef>>(emptyList()) }
    val entry = entryStack.lastOrNull()
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    // État de la page record (période / section / sous-filtre / scroll) conservé ici : la page quitte la composition
    // quand on ouvre une fiche d'entrée, sinon ses filtres repartaient à « Semaine / Titres » et en haut de liste.
    val pageState = remember(page) { page?.let { RecordPageState(it) } }
    // Position de la liste principale (familles / records) : conservée quand on ouvre un record puis qu'on revient
    val mainList = remember { LazyListState() }

    BackHandler(enabled = entry != null || page != null || searching) {
        when {
            entry != null -> entryStack = entryStack.dropLast(1)
            page != null -> page = null
            else -> { searching = false; query = "" }
        }
    }

    // 🎨 Art du titre le plus écouté (all time) en fond d'écran
    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching { db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }

    var familyPage by remember { mutableStateOf(false) }
    when {
        entry != null -> RecordEntryPage(entry, onBack = { entryStack = entryStack.dropLast(1) }, onEntity = { detail = it }, onEntry = { entryStack = entryStack + it })
        familyPage -> RecordsFamilyPage(db, onBack = { familyPage = false })
        page != null -> RecordPage(page!!, pageState!!, onBack = { page = null }, onEntry = { entryStack = listOf(it) })
        else -> ScreenBackdrop(artUrl) {
        LazyColumn(Modifier.fillMaxSize(), state = mainList) {
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
                item { RecordSearchResults(query) { entryStack = listOf(it) } }
            } else {
                item {
                    // Familles : puces horizontales façon niveaux de certification, avec le nombre de records
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NovaFilterChip(
                            flagKey = "all", selected = group == null, onClick = { group = null }, label = { Text("Tous (${RecordCatalog.ALL.size})") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = theme.primary.copy(alpha = 0.25f), selectedLabelColor = theme.text)
                        )
                        RecordGroup.entries.forEach { g ->
                            val c = groupColor(g)
                            NovaFilterChip(
                                flagKey = g, selected = group == g, onClick = { group = if (group == g) null else g },
                                label = { Text("${g.emoji} ${g.label} (${RecordCatalog.inGroup(g).size})") },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.copy(alpha = 0.3f), selectedLabelColor = theme.text),
                                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = group == g, borderColor = c.copy(alpha = 0.5f), selectedBorderColor = c)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                item(key = "live") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NovaColors.Gold.copy(alpha = 0.12f))
                                .clickable { familyPage = true }.padding(14.dp)
                        ) {
                            Text("🏆 Records", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "🎯 Approche · ⚠️ Menaces · 📅 Éphéméride · 🎉 Records battus — les 4 côte à côte",
                                color = Nova.theme.textSecondary, style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
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
        RecordGroup.LISTENING -> Color(0xFF1ABC9C)
        RecordGroup.PALMARES -> NovaColors.Gold
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
    val db = app.database
    var rows by remember { mutableStateOf<List<RecordRow>>(emptyList()) }
    LaunchedEffect(query) {
        rows = if (query.trim().length < 2) emptyList() else runCatching { app.database.recordDao().search(query.trim()) }.getOrDefault(emptyList())
    }
    // 🎨 Art du titre le plus écouté (all time) en fond d'écran
    val artUrl by produceState<String?>(initialValue = null) {
        value = runCatching { db.trackDao().topAllTime(1).first().firstOrNull()?.track?.coverUrl }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
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

}

/* ================================ 📄 Page d'un record (ex-popup 90 %) ================================ */

/** Référence d'une ligne de Top 10 : tout ce qu'il faut pour la page « pourquoi il est là ». */
data class RecordEntryRef(val def: RecordDef, val period: Period?, val category: RecordCategory, val sub: String?, val row: RecordRow, val rank: Int?, val event: String? = null)

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
    /** Dernières lignes chargées : réutilisées immédiatement au retour d'une fiche (sinon la liste repart vide → scroll perdu). */
    var rows by mutableStateOf<List<RecordRow>>(emptyList())
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

    LaunchedEffect(def, period, category, sub) {
        dao.top10(def.id, period?.dbName, category.dbName, sub?.dbName, def.ascending).collect { st.rows = it }
    }
    val rows = st.rows
    val maxValue = remember(rows) { rows.maxOfOrNull { it.r.value } ?: 0.0 }

    LazyColumn(Modifier.fillMaxSize().background(theme.background), state = st.list) {
        item { PageHeader("Records", "${def.emoji} ${def.title}", color, onBack, def.description) }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                // Sections Titres / Artistes / Albums
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.surface.copy(alpha = 0.8f)).padding(3.dp)) {
                    def.categories.forEachIndexed { ci, c ->
                        val on = c == category
                        val pride = Nova.isPride
                        Text(
                            "${c.emoji} ${sectionLabel(c)}", color = if (on) (if (pride) prideOnFlag(prideFlagFor(ci + 1)) else theme.background) else theme.text, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).then(if (pride) Modifier.prideChip(on, prideFlagFor(ci + 1), Color.Transparent) else Modifier.background(if (on) color else Color.Transparent)).clickable { selectCategory(c) }.padding(vertical = 8.dp)
                        )
                    }
                }
                if (def.periods.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    SegmentedRows(def.periods.map { it.label }, def.periods.indexOf(period), color) { period = def.periods[it] }
                }
                val subs = def.subs[category].orEmpty()
                if (subs.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    // Sous-sections : segments pleine largeur (comme Titres / Artistes / Albums), sur plusieurs lignes si besoin
                    SegmentedRows(subs.map { it.label }, subs.indexOf(sub), color) { sub = subs[it] }
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

/** Segments pleine largeur répartis équitablement sur ⌈n/3⌉ lignes (≤ 4 par ligne si une seule ligne). */
@Composable
private fun SegmentedRows(labels: List<String>, selected: Int, color: Color, onSelect: (Int) -> Unit) {
    val theme = Nova.theme
    val pride = Nova.isPride
    val n = labels.size
    val rowsCount = if (n <= 4) 1 else (n + 2) / 3
    val base = n / rowsCount; val extra = n % rowsCount
    var start = 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rowsCount) { r ->
            val size = base + if (r < extra) 1 else 0
            val slice = (start until start + size).toList(); start += size
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(theme.surface.copy(alpha = 0.8f)).padding(3.dp)) {
                slice.forEach { i ->
                    val on = i == selected
                    Text(
                        labels[i], color = if (on) (if (pride) prideOnFlag(prideFlagFor(i + 1)) else theme.background) else theme.text, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).then(if (pride) Modifier.prideChip(on, prideFlagFor(i + 1), Color.Transparent) else Modifier.background(if (on) color else Color.Transparent))
                            .clickable { onSelect(i) }.padding(horizontal = 4.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChipRow(labels: List<String>, selected: Int, color: Color, onSelect: (Int) -> Unit) {
    val theme = Nova.theme
    val pride = Nova.isPride
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Text(
                l, color = if (on) (if (pride) prideOnFlag(prideFlagFor(i + 1)) else theme.background) else theme.text, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    .then(if (pride) Modifier.prideChip(on, prideFlagFor(i + 1), theme.surface) else Modifier.background(if (on) color else theme.background.copy(alpha = 0.7f)))
                    .border(1.dp, if (on) (if (pride) Color.White.copy(alpha = 0.7f) else color) else theme.textSecondary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
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
private fun RecordEntryPage(ref: RecordEntryRef, onBack: () -> Unit, onEntity: (DetailTarget) -> Unit, onEntry: (RecordEntryRef) -> Unit = {}) {
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
        ref.event?.let { ev ->
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SectionTitle("📌 Ce qui s'est passé", color)
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.surface).padding(14.dp)) {
                        Text(ev, color = theme.text, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                    }
                }
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
                        // Contexte propre à cet élément : place dans le classement, profil, autres records
                        st.context.forEach { (title, text) ->
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.2f)))
                            Spacer(Modifier.height(8.dp))
                            Text(title, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(text, color = theme.text, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                        }
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
            if (st.held.isNotEmpty()) {
                // 🏆 Most Records : tous les classements détenus, groupés par famille, chaque ligne ouvre le classement
                val groups = st.held.groupBy { RecordCatalog.groupOf[it.def.id] ?: RecordGroup.TOTALS }.entries.sortedByDescending { it.value.size }
                item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("🏆 Tous ses records (${st.held.size})", color) } }
                groups.forEach { (g, list) ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${g.emoji} ${g.label}", color = groupColor(g), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("${list.size}", color = groupColor(g), fontWeight = FontWeight.Black)
                        }
                    }
                    items(list.size) { i ->
                        val h = list[i]
                        val subLabel = h.def.subs[h.category]?.firstOrNull { it.dbName == h.sub }?.label
                        val ctx = listOfNotNull(h.period?.frLabel, subLabel).joinToString(" · ")
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(theme.surface.copy(alpha = 0.6f))
                                .border(1.dp, groupColor(g).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                .clickable { onEntry(RecordEntryRef(h.def, h.period, h.category, h.sub, RecordRow(h.row, name, row.subtitle, row.imageUrl), rank = 1)) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(h.def.emoji, fontSize = 20.sp, modifier = Modifier.width(30.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.def.title, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(ctx.ifBlank { null }, h.row.extraData, if (h.tied > 1) "ex æquo (${h.tied})" else null).joinToString(" · "),
                                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(formatRecordValue(h.def, h.period, h.row.value), color = if (h.tied > 1) theme.textSecondary else NovaColors.Gold, fontWeight = FontWeight.Bold)
                                h.row.valueDate?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall) }
                            }
                            Text("›", color = theme.textSecondary, fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
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
        RecordUnit.DAYS -> "$n jour${if (n > 1) "s" else ""}"
        RecordUnit.COUNT -> when (def.id) {
            "MOST_REENTRIES" -> "$n retour${if (n > 1) "s" else ""}"
            "MOST_RECORDS" -> "$n record${if (n > 1) "s" else ""}"
            "MOST_CERTIFICATIONS" -> "$n certif${if (n > 1) "s" else ""}"
            "MOST_HOF", "MOST_GLOBAL" -> "$n entrée${if (n > 1) "s" else ""}"
            "MOST_SUCCESSIVE_1" -> "$n #1 successifs"
            "MOST_SIMULTANEOUS" -> "$n simultané${if (n > 1) "s" else ""}"
            else -> "$n"
        }
    }
}

/** Valeur formatée d'une ligne de cache. */
private fun valueOf(row: RecordRow): String {
    val def = RecordCatalog.ALL.firstOrNull { it.id == row.r.recordType } ?: return row.r.value.toInt().toString()
    return formatRecordValue(def, Period.entries.firstOrNull { it.dbName == row.r.periodType }, row.r.value)
}

/** Nom d'entité par catégorie + id. */
private suspend fun entityName(db: NovaDatabase, cat: String, id: Long): String = when (cat) {
    "TRACK" -> db.trackDao().getById(id)?.title
    "ALBUM" -> db.albumDao().getById(id)?.title
    else -> db.artistDao().byIds(listOf(id)).firstOrNull()?.name
} ?: "Inconnu"

/** Récit de l'événement : pourquoi cette ligne se retrouve dans la liste des records tombés. */
private fun fallenEvent(all: List<RecordRow>, row: RecordRow): String {
    val def = RecordCatalog.ALL.firstOrNull { it.id == row.r.recordType }
    val group = listOf(row.r.recordType, row.r.periodType ?: "-", row.r.category, row.r.subcategory ?: "-")
    val prev = all.filter {
        listOf(it.r.recordType, it.r.periodType ?: "-", it.r.category, it.r.subcategory ?: "-") == group &&
            (it.r.valueDate ?: "") < (row.r.valueDate ?: "")
    }.let { c -> if (def?.ascending == true) c.minByOrNull { it.r.value } else c.maxByOrNull { it.r.value } }
    val date = row.r.valueDate ?: "?"
    return if (prev != null && prev.r.id != row.r.id) {
        "Le $date, « ${row.name ?: "Inconnu"} » a fait tomber le record détenu par « ${prev.name ?: "Inconnu"} » (${valueOf(prev)}) — le nouveau palier est ${valueOf(row)}."
    } else {
        "Le $date, « ${row.name ?: "Inconnu"} » a établi ce record : ${valueOf(row)}."
    }
}

/** Les 12 records de la famille demandés par l'utilisateur (puce Approche étendue aux autres vues). */
private val FAMILY_RECORDS = setOf(
    "MOST_RECORDS", "MOST_CUMULATIVE", "MOST_TIME_AT_1", "MOST_CERTIFICATIONS",
    "MOST_BLOCKED_TOP5", "MOST_CONSISTENT", "MOST_DEBUT_1", "MOST_DEBUT_TOP10",
    "MOST_SONGS_IN_CHART", "MOST_SONGS_AT_1", "MOST_SONGS_TOP10", "MOST_SUCCESSIVE_1"
)

/** Enveloppe de la famille : majeurs historiques + les 12 records choisis. */
private val FAMILY_ALL: Set<String> get() = RecordCatalog.MAJOR + FAMILY_RECORDS

/** Types gérés par un compteur vivant (période en cours / cumul all time / streak) — exclus du calcul statique n°1/n°2. */
private val LIVE_HANDLED = setOf("BIGGEST_PERIOD", "LONGEST_LISTENING_STREAK")

/** Ligne de la famille des records : affichée sous les puces, cliquable vers son pop-up propre. */
private data class RecItem(
    val emoji: String,
    val title: String,
    val detail: String,
    /** Récit « ce qui s'est passé » — null = ligne non cliquable (certifications, Hall of Fame). */
    val story: String?,
    val row: RecordRow?
)

/**
 * 🏆 Écran « famille des records » : les 4 vues en petites puces côte à côte (style Certifications),
 * la puce active est surlignée et ses données s'écrivent directement en dessous.
 * Chaque ligne cliquable ouvre SON pop-up à elle — grand, premium, avec l'explication du record et le récit.
 */
@Composable
private fun RecordsFamilyPage(db: NovaDatabase, onBack: () -> Unit) {
    val theme = Nova.theme
    var tab by remember { mutableStateOf(0) }
    var approche by remember { mutableStateOf<List<RecItem>>(emptyList()) }
    var menaces by remember { mutableStateOf<List<RecItem>>(emptyList()) }
    var ephemeride by remember { mutableStateOf<List<RecItem>>(emptyList()) }
    var battus by remember { mutableStateOf<List<RecItem>>(emptyList()) }
    var popup by remember { mutableStateOf<RecItem?>(null) }
    LaunchedEffect(Unit) {
        runCatching {
            val ap = mutableListOf<RecItem>()
            val me = mutableListOf<RecItem>()
            // 🎯⚠️ BIGGEST_PERIOD vivant : leader de la période en cours face au record
            val bpRows = db.recordDao().rowsForTypes(listOf("BIGGEST_PERIOD"))
            for (p in listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY)) {
                val r = Dates.statsRangeFor(p)
                val leaders = mapOf(
                    "TRACK" to db.trackDao().topForPeriod(r.fromIso, r.toIso, 1).first().firstOrNull()?.let { Triple(it.track.trackId, it.periodPlays, it.track.title) },
                    "ARTIST" to db.artistDao().topForPeriod(r.fromIso, r.toIso, 1).first().firstOrNull()?.let { Triple(it.artist.artistId, it.periodPlays, it.artist.name) },
                    "ALBUM" to db.albumDao().topForPeriod(r.fromIso, r.toIso, 1).first().firstOrNull()?.let { Triple(it.album.albumId, it.periodPlays, it.album.title) }
                )
                for ((cat, leader) in leaders) {
                    if (leader == null || leader.second <= 0) continue
                    val row = bpRows.filter { it.r.periodType == p.dbName && it.r.category == cat }.maxByOrNull { it.r.value } ?: continue
                    val def = RecordCatalog.ALL.firstOrNull { it.id == row.r.recordType }
                    val title = "${def?.title ?: row.r.recordType} · ${p.frLabel}"
                    val detail = "Détenteur : « ${row.name ?: "Inconnu"} » — ${valueOf(row)}"
                    val gap = row.r.value - leader.second
                    if (gap > 0) {
                        val ev = if (leader.first == row.r.entityId) {
                            "Le record ${p.frLabel.lowercase()} appartient déjà à « ${leader.third} » : avec ${leader.second} écoutes sur la période en cours, il n'est plus qu'à ${gap.toInt()} de battre son propre record (${row.r.value.toInt()} ▶)."
                        } else {
                            "Le record ${p.frLabel.lowercase()} de « ${row.name ?: "Inconnu"} » (${row.r.value.toInt()} ▶) est en approche : « ${leader.third} » totalise déjà ${leader.second} écoutes sur la période en cours — il ne manque que ${gap.toInt()}."
                        }
                        ap += RecItem(def?.emoji ?: "🏆", title, detail, ev, row)
                    } else {
                        me += RecItem(def?.emoji ?: "🏆", title, detail, "🔥 « ${leader.third} » a déjà dépassé le record ${p.frLabel.lowercase()} (${row.r.value.toInt()} ▶) avec ${leader.second} écoutes sur la période en cours — le record est en train de tomber.", row)
                    }
                }
            }
            // 🎯⚠️ Cumuls all time vivants : le n°2 face au n°1
            val mcRows = db.recordDao().rowsForTypes(listOf("MOST_CUMULATIVE"))
            val t2 = db.trackDao().topAllTime(2).first()
            if (t2.size >= 2) {
                val gap = t2[0].periodPlays - t2[1].periodPlays
                val row = mcRows.filter { it.r.category == "TRACK" }.maxByOrNull { it.r.value }
                if (row != null) {
                    val detail = "Détenteur : « ${row.name ?: "Inconnu"} » — ${valueOf(row)}"
                    if (gap <= 0) me += RecItem("🏆", "Most Cumulative · Titre", detail, "🔥 Égalité en tête du cumul : « ${t2[1].track.title} » a rejoint « ${t2[0].track.title} » — le record all time peut basculer à chaque écoute.", row)
                    else ap += RecItem("🏆", "Most Cumulative · Titre", detail, "⚠️ « ${t2[1].track.title} » n'est qu'à $gap écoutes du cumul de « ${t2[0].track.title} » (${t2[0].periodPlays} ▶) — le record all time est en approche.", row)
                }
            }
            val a2 = db.artistDao().topAllTime(2).first()
            if (a2.size >= 2) {
                val gap = a2[0].periodPlays - a2[1].periodPlays
                val row = mcRows.filter { it.r.category == "ARTIST" }.maxByOrNull { it.r.value }
                if (row != null) {
                    val detail = "Détenteur : « ${row.name ?: "Inconnu"} » — ${valueOf(row)}"
                    if (gap <= 0) me += RecItem("🏆", "Most Cumulative · Artiste", detail, "🔥 Égalité en tête du cumul : « ${a2[1].artist.name} » a rejoint « ${a2[0].artist.name} » — le record all time peut basculer à chaque écoute.", row)
                    else ap += RecItem("🏆", "Most Cumulative · Artiste", detail, "⚠️ « ${a2[1].artist.name} » n'est qu'à $gap écoutes du cumul de « ${a2[0].artist.name} » (${a2[0].periodPlays} ▶) — le record all time est en approche.", row)
                }
            }
            val al2 = db.albumDao().topAllTime(2).first()
            if (al2.size >= 2) {
                val gap = al2[0].periodPlays - al2[1].periodPlays
                val row = mcRows.filter { it.r.category == "ALBUM" }.maxByOrNull { it.r.value }
                if (row != null) {
                    val detail = "Détenteur : « ${row.name ?: "Inconnu"} » — ${valueOf(row)}"
                    if (gap <= 0) me += RecItem("🏆", "Most Cumulative · Album", detail, "🔥 Égalité en tête du cumul : « ${al2[1].album.title} » a rejoint « ${al2[0].album.title} » — le record all time peut basculer à chaque écoute.", row)
                    else ap += RecItem("🏆", "Most Cumulative · Album", detail, "⚠️ « ${al2[1].album.title} » n'est qu'à $gap écoutes du cumul de « ${al2[0].album.title} » (${al2[0].periodPlays} ▶) — le record all time est en approche.", row)
                }
            }
            // 🎯⚠️ Streak d'écoute en cours face au record
            val lsRow = db.recordDao().rowsForTypes(listOf("LONGEST_LISTENING_STREAK")).maxByOrNull { it.r.value }
            if (lsRow != null) {
                val today = Dates.today()
                val set = db.dailyPlayDao().activeDaysSince(today.minusDays(400).toString()).map { java.time.LocalDate.parse(it) }.toSet()
                var cursor = today; var streak = 0
                if (!set.contains(cursor)) cursor = cursor.minusDays(1)
                while (set.contains(cursor)) { streak++; cursor = cursor.minusDays(1) }
                val rec = lsRow.r.value.toInt()
                val detail = "Détenteur : « ${lsRow.name ?: "Inconnu"} » — $rec jour(s)"
                when {
                    streak > rec -> me += RecItem("🔥", "Longest Listening Streak", detail, "🔥 Streak en cours : $streak jours — le record de $rec jour(s) est déjà dépassé !", lsRow)
                    streak > 0 -> ap += RecItem("🎧", "Longest Listening Streak", detail, "🎯 Streak en cours : $streak jour(s) sur les $rec du record de « ${lsRow.name ?: "Inconnu"} » — encore ${rec - streak} pour l'égaler.", lsRow)
                }
            }
            // 🎯⚠️ Statique : le n°2 du Top 10 de chaque record de la famille face au détenteur
            val statAp = mutableListOf<Pair<Double, RecItem>>()
            val famRows = db.recordDao().rowsForTypes(FAMILY_ALL.toList()).filter {
                it.r.recordType !in LIVE_HANDLED && !(it.r.recordType == "MOST_CUMULATIVE" && it.r.periodType == null)
            }
            val groups = famRows.groupBy { listOf(it.r.recordType, it.r.periodType ?: "-", it.r.category, it.r.subcategory ?: "-") }
            for ((_, rs) in groups) {
                val first0 = rs.first()
                val def = RecordCatalog.ALL.firstOrNull { d -> d.id == first0.r.recordType } ?: continue
                val sorted = rs.sortedBy { if (def.ascending) -it.r.value else it.r.value }
                if (sorted.size < 2) continue
                val one = sorted[0]; val two = sorted[1]
                val gapv = if (def.ascending) two.r.value - one.r.value else one.r.value - two.r.value
                if (gapv < 0) continue
                val cat = RecordCategory.valueOf(first0.r.category)
                val period = Period.entries.firstOrNull { it.dbName == first0.r.periodType }
                val subLabel = def.subs[cat]?.firstOrNull { it.dbName == first0.r.subcategory }?.label
                val title = listOfNotNull(def.title, period?.label, sectionLabel(cat), subLabel).joinToString(" · ")
                val detail = "Détenteur : « ${one.name ?: "Inconnu"} » — ${valueOf(one)}"
                if (gapv == 0.0) {
                    me += RecItem(def.emoji, title, detail, "🔥 Égalité en tête : « ${two.name ?: "Inconnu"} » (${valueOf(two)}) a rejoint « ${one.name ?: "Inconnu"} » — le record peut basculer à chaque instant.", one)
                } else {
                    statAp += gapv to RecItem(def.emoji, title, detail,
                        "« ${two.name ?: "Inconnu"} » (${valueOf(two)}) n'est qu'à ${formatRecordValue(def, period, gapv)} du record de « ${one.name ?: "Inconnu"} » (${valueOf(one)}).", one)
                }
            }
            statAp.sortBy { it.first }
            ap += statAp.map { it.second }
            approche = ap
            me.sortBy { RecordCatalog.groupOf[it.row?.r?.recordType ?: ""]?.ordinal ?: 99 }
            menaces = me
            // 📅 Éphéméride : un jour comme aujourd'hui, les années passées
            val today = Dates.today()
            val mmdd = java.time.format.DateTimeFormatter.ofPattern("MM-dd")
            val ep = mutableListOf<RecItem>()
            for (h in db.certificationDao().allHistory()) {
                val d = java.time.Instant.ofEpochMilli(h.certifiedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                if (d != today && d.format(mmdd) == today.format(mmdd)) {
                    val lvl = CertLevel.entries.firstOrNull { it.dbName == h.level }
                    ep += RecItem(lvl?.emoji ?: "🏅", "${d.year} · ${lvl?.label ?: h.level} obtenue", "« ${entityName(db, h.entityType, h.entityId)} »", null, null)
                }
            }
            for (h in db.hallOfFameDao().all()) {
                val d = runCatching { java.time.LocalDate.parse(h.entryDate) }.getOrNull() ?: continue
                if (d != today && d.format(mmdd) == today.format(mmdd)) {
                    ep += RecItem("🏛️", "${d.year} · Hall of Fame", h.entryType.replace('_', ' ').lowercase(), null, null)
                }
            }
            for (row in db.recordDao().recentRows(300).filter { it.r.recordType in FAMILY_ALL }) {
                val vd = row.r.valueDate ?: continue
                val d = runCatching { java.time.LocalDate.parse(vd) }.getOrNull() ?: continue
                if (d != today && d.format(mmdd) == today.format(mmdd)) {
                    val def = RecordCatalog.ALL.firstOrNull { it.id == row.r.recordType }
                    ep += RecItem(
                        def?.emoji ?: "🏆", "${d.year} · ${def?.title ?: row.r.recordType}",
                        "« ${row.name ?: "Inconnu"} » — ${valueOf(row)}",
                        "Le $vd, « ${row.name ?: "Inconnu"} » a établi le record « ${def?.title ?: row.r.recordType} » avec ${valueOf(row)}.", row
                    )
                }
            }
            ep.sortBy { it.title }
            ephemeride = ep
            // 🎉 Records battus : top 20 chrono de la famille
            val all = db.recordDao().rowsForTypes(FAMILY_ALL.toList())
            val fallen = all.filter { it.r.valueDate != null }.sortedByDescending { it.r.valueDate }.take(20)
            battus = fallen.mapIndexed { idx, f ->
                val def = RecordCatalog.ALL.firstOrNull { it.id == f.r.recordType }
                RecItem(def?.emoji ?: "🏆", "${idx + 1}. ${def?.title ?: f.r.recordType}", "« ${f.name ?: "Inconnu"} » · ${f.r.valueDate} — ${valueOf(f)}", fallenEvent(all, f), f)
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Records", "🏆 La famille des records", NovaColors.Gold, onBack, subtitle = "Les 12 records choisis + les majeurs · touche une ligne pour son pop-up")
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf("🎯 Approche" to approche.size, "⚠️ Menaces" to menaces.size, "📅 Éphéméride" to ephemeride.size, "🎉 Battus" to battus.size)
            tabs.forEachIndexed { i, (label, n) ->
                NovaFilterChip(
                    selected = tab == i, onClick = { tab = i },
                    label = { Text("$label ($n)") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NovaColors.Gold.copy(alpha = 0.3f), selectedLabelColor = theme.text),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = tab == i, borderColor = NovaColors.Gold.copy(alpha = 0.5f), selectedBorderColor = NovaColors.Gold)
                )
            }
        }
        val list = when (tab) { 0 -> approche; 1 -> menaces; 2 -> ephemeride; else -> battus }
        if (list.isEmpty()) {
            Text("Rien à afficher pour l'instant.", color = theme.textSecondary, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list.size) { i ->
                    val rc = list[i]
                    Column(
                        Modifier.fillMaxWidth()
                            .then(if (rc.story != null) Modifier.clickable { popup = rc } else Modifier)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("${rc.emoji} ${rc.title}", color = NovaColors.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(rc.detail, color = theme.text, style = MaterialTheme.typography.bodySmall)
                        rc.story?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
    popup?.let { rc -> RecordEventPopup(rc) { popup = null } }
}

/** Pop-up propre à la ligne cliquée : grand et premium — explication du record + réalités de l'événement. */
@Composable
private fun RecordEventPopup(rc: RecItem, onDismiss: () -> Unit) {
    val theme = Nova.theme
    val def = rc.row?.let { row -> RecordCatalog.ALL.firstOrNull { it.id == row.r.recordType } }
    NovaPopupCard(
        borderColor = NovaColors.Gold, onDismiss = onDismiss, glowDp = 26,
        widthFraction = 0.97f, heightFraction = 0.93f, backdropUrl = rc.row?.imageUrl,
        banner = {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                BlurredBackdrop(rc.row?.imageUrl, NovaColors.Gold, Modifier.fillMaxSize())
                if (rc.row != null) {
                    Box(
                        Modifier.size(140.dp).shadow(22.dp, RoundedCornerShape(14.dp), ambientColor = NovaColors.Gold, spotColor = NovaColors.Gold)
                            .border(3.dp, NovaColors.Gold, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                    ) {
                        CoverArt(rc.row.imageUrl, rc.row.name ?: "?", size = 140, circle = rc.row.r.category == "ARTIST", zoomable = true)
                    }
                } else {
                    Text(rc.emoji, fontSize = 64.sp)
                }
            }
        }
    ) {
        Text("${rc.emoji} ${def?.title ?: rc.title}", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
        // L'explication du record lui-même
        def?.description?.let {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(NovaColors.Gold.copy(alpha = 0.10f)).padding(12.dp)) {
                Text("📖 Le record", color = NovaColors.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(it, color = theme.text, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
            }
        }
        rc.row?.let { row ->
            Spacer(Modifier.height(14.dp))
            Text(row.name ?: "Inconnu", color = theme.text, fontWeight = FontWeight.Black, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            row.subtitle?.let { Text(it, color = theme.primary, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.height(8.dp))
            Text(valueOf(row), color = NovaColors.Gold, fontWeight = FontWeight.Black, fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            val period = Period.entries.firstOrNull { it.dbName == row.r.periodType }
            val subLabel = def?.subs?.get(RecordCategory.valueOf(row.r.category))?.firstOrNull { it.dbName == row.r.subcategory }?.label
            val ctx = listOfNotNull(period?.label, sectionLabel(RecordCategory.valueOf(row.r.category)), subLabel, row.r.valueDate?.let { "établi le $it" }).joinToString(" · ")
            Text(ctx, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(NovaColors.Gold.copy(alpha = 0.25f)))
        Spacer(Modifier.height(10.dp))
        SectionTitle("📌 Ce qui s'est passé", NovaColors.Gold)
        Text(rc.story ?: "", color = theme.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
        Spacer(Modifier.height(10.dp))
        Text(rc.detail, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(6.dp))
    }
}
