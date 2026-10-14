package com.novastats.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.novastats.app.data.repository.HonorGuest
import com.novastats.app.data.repository.HonorGuests
import com.novastats.app.data.db.dao.RecordRow
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.repository.RecordExplainer
import com.novastats.app.ui.share.ShareCards
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
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
                        placeholder = { Text("🔍 Un titre, un artiste, un album… dans tous les records", color = theme.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary,
                            unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
                            focusedTextColor = theme.text,
                            unfocusedTextColor = theme.text,
                            cursorColor = theme.primary
                        ),
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

    // 🔥 Cérémonie des paliers : une série d'écoute en cours qui franchit 7 / 30 / 100 / 365 jours se célèbre une seule fois
    val scope = rememberCoroutineScope()
    var tierQueue by remember { mutableStateOf<List<TierCelebration>>(emptyList()) }
    LaunchedEffect(count) {
        runCatching {
            val announced = app.settings.streakTiersAnnounced.first()
            tierQueue = db.recordDao().rowsForTypes(listOf("LONGEST_LISTENING_STREAK"))
                .filter { it.r.extraData?.contains("en cours") == true }
                .mapNotNull { row ->
                    val tier = RecordCatalog.tierFor(row.r.value.toInt()) ?: return@mapNotNull null
                    val key = "${row.r.category}:${row.r.entityId}:${tier.min}"
                    if (key in announced) null else TierCelebration(key, row.name ?: "Inconnu", row.imageUrl, row.r.value.toInt(), tier)
                }
                .sortedBy { it.tier.min }
        }
    }
    tierQueue.firstOrNull()?.let { c ->
        StreakTierPopup(c) {
            scope.launch { app.settings.markStreakTierAnnounced(c.key) }
            tierQueue = tierQueue.drop(1)
        }
    }

    // 🎯 « Le chart est à moi » : chaque nouveau record de simultanéité se célèbre une fois (amorçage silencieux au premier lancement)
    var simQueue by remember { mutableStateOf<List<SimCelebration>>(emptyList()) }
    LaunchedEffect(count) {
        runCatching {
            val rows = db.recordDao().rowsForTypes(listOf("MOST_SIMULTANEOUS"))
            val numberOnes = rows.groupBy { listOf(it.r.periodType ?: "-", it.r.category, it.r.subcategory ?: "-") }
                .mapNotNull { g -> g.value.maxByOrNull { it.r.value }?.takeIf { it.r.value >= 2 } }
            fun keyOf(row: com.novastats.app.data.db.dao.RecordRow) =
                "SIM:${row.r.category}:${row.r.subcategory}:${row.r.periodType}:${row.r.entityId}:${row.r.value.toLong()}"
            val announced = app.settings.simultaneousAnnounced.first()
            if (announced.isEmpty()) {
                val keys = numberOnes.map { keyOf(it) }.toSet()
                if (keys.isNotEmpty()) app.settings.markSimultaneousAnnounced(keys)
            } else {
                val fresh = numberOnes.filter { keyOf(it) !in announced }
                if (fresh.isNotEmpty()) {
                    app.settings.markSimultaneousAnnounced(fresh.map { keyOf(it) }.toSet())
                    simQueue = fresh.map { row ->
                        val zoneKey = row.r.subcategory?.substringAfterLast('_')
                        SimCelebration(
                            keyOf(row), row.name ?: "Inconnu", row.imageUrl, row.r.value.toInt(),
                            RecordCatalog.zoneLimit(zoneKey), RecordCatalog.zoneLabel(zoneKey), row.r.periodType, row.r.category
                        )
                    }.sortedByDescending { it.count }
                }
            }
        }
    }
    simQueue.firstOrNull()?.let { c ->
        SimultaneousPopup(c) { simQueue = simQueue.drop(1) }
    }

    // 🧹 Cérémonie du balayage : chaque nouveau record de Podium Sweep se célèbre une fois (amorçage silencieux au premier lancement)
    var sweepQueue by remember { mutableStateOf<List<SweepCelebration>>(emptyList()) }
    LaunchedEffect(count) {
        runCatching {
            val rows = db.recordDao().rowsForTypes(listOf("PODIUM_SWEEP"))
            val numberOnes = rows.groupBy { listOf(it.r.periodType ?: "-", it.r.category, it.r.subcategory ?: "-") }
                .mapNotNull { g -> g.value.maxByOrNull { it.r.value } }
            fun keyOf(row: com.novastats.app.data.db.dao.RecordRow) =
                "SWEEP:${row.r.category}:${row.r.subcategory}:${row.r.periodType}:${row.r.entityId}:${row.r.value.toLong()}"
            val announced = app.settings.sweepAnnounced.first()
            if (announced.isEmpty()) {
                val keys = numberOnes.map { keyOf(it) }.toSet()
                if (keys.isNotEmpty()) app.settings.markSweepAnnounced(keys)
            } else {
                val fresh = numberOnes.filter { keyOf(it) !in announced }
                if (fresh.isNotEmpty()) {
                    app.settings.markSweepAnnounced(fresh.map { keyOf(it) }.toSet())
                    sweepQueue = fresh.map { row ->
                        SweepCelebration(
                            keyOf(row), row.name ?: "Inconnu", row.imageUrl, row.r.value.toInt(),
                            RecordCatalog.sweepZoneOf(row.r.subcategory), RecordCatalog.sweepSolo(row.r.subcategory), row.r.periodType, row.r.category
                        )
                    }.sortedByDescending { it.count }
                }
            }
        }
    }
    sweepQueue.firstOrNull()?.let { c ->
        SweepCeremonyPopup(c) { sweepQueue = sweepQueue.drop(1) }
    }

    // 🌐 Cérémonies Multi-Chart : premières conquêtes D+W, D+W+M, D+W+M+Y et Grand Chelem Top 2.
    var multiChartQueue by remember { mutableStateOf<List<MultiChartCelebration>>(emptyList()) }
    LaunchedEffect(count, lastCalc) {
        runCatching {
            val milestones = setOf("DW", "DWM", "DWMY", "DWMY_TOP2")
            val rows = db.recordDao().rowsForTypes(listOf("MULTI_CHART"))
                .filter { row -> row.r.subcategory?.let { it in milestones } == true && row.r.value >= 1.0 }
            fun keyOf(row: com.novastats.app.data.db.dao.RecordRow) = "MC:${row.r.category}:${row.r.entityId}:${row.r.subcategory}"
            val announced = app.settings.multiChartAnnounced.first()
            val bootstrap = "__MULTI_CHART_BOOTSTRAP__"
            val baselineDate = app.settings.multiChartBootstrapDate.first()
            val keys = rows.map { keyOf(it) }.toSet()
            if (bootstrap !in announced || baselineDate == null) {
                app.settings.markMultiChartAnnounced(keys + bootstrap)
                app.settings.setMultiChartBootstrapDate(Dates.today().toString())
            } else {
                val fresh = rows.filter { keyOf(it) !in announced }
                if (fresh.isNotEmpty()) {
                    // La première reconstruction après installation peut révéler des exploits historiques : on les marque
                    // silencieusement. Seuls les paliers datés depuis l'amorçage déclenchent une vraie cérémonie.
                    app.settings.markMultiChartAnnounced(fresh.map { keyOf(it) }.toSet())
                    val current = fresh.filter { it.r.valueDate?.let { date -> date >= baselineDate } == true }
                    fun order(sub: String?) = when (sub) { "DW" -> 1; "DWM" -> 2; "DWMY" -> 3; "DWMY_TOP2" -> 4; else -> 0 }
                    multiChartQueue = current.map { row ->
                        MultiChartCelebration(keyOf(row), row.name ?: "Inconnu", row.imageUrl, row.r.value.toInt(), row.r.subcategory ?: "DW", row.r.category)
                    }.sortedWith(compareBy<MultiChartCelebration> { order(it.sub) }.thenByDescending { it.count })
                }
            }
        }
    }
    if (tierQueue.isEmpty() && simQueue.isEmpty() && sweepQueue.isEmpty()) {
        multiChartQueue.firstOrNull()?.let { c -> MultiChartCeremonyPopup(c) { multiChartQueue = multiChartQueue.drop(1) } }
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
        val limit = if (def.id == "MOST_PERFECT_ALL_KILL") 20 else 10
        dao.top10(def.id, period?.dbName, category.dbName, sub?.dbName, def.ascending, limit).collect { st.rows = it }
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
                Text(
                    if (def.id == "MOST_RECORDS") "Ouvre une ligne pour voir son palmarès détaillé, organisé par famille puis par record."
                    else "Appuie sur une ligne pour voir pourquoi l'élément est là.",
                    color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (rows.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🕳️", fontSize = 40.sp)
                Text("Pas encore de record ici", color = theme.text, fontWeight = FontWeight.SemiBold)
                Text("Il faut plus d'historique pour cette combinaison (période · section).", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        } else if (def.id == "MOST_PERFECT_ALL_KILL") {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    SectionTitle("🏛️ CÉRÉMONIE · TOP 20 ${period?.label?.uppercase(Locale.FRANCE).orEmpty()}", NovaColors.Gold)
                    Text("Les trois premiers sur le podium; les autres événements du classement restent accessibles juste dessous.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            val podiumCount = minOf(3, rows.size)
            if (podiumCount == 3) item {
                PerfectAllKillTop20Podium(rows.take(3), def, period, color) { row, rank ->
                    onEntry(RecordEntryRef(def, period, category, sub?.dbName, row, rank))
                }
            }
            items(rows.size - podiumCount) { offset ->
                val i = offset + podiumCount
                val row = rows[i]
                Box(Modifier.padding(horizontal = 14.dp)) {
                    RecordEntryRow(i + 1, row, def, period, category, maxValue, color, rows.getOrNull(i + 1)?.r?.value) { onEntry(RecordEntryRef(def, period, category, sub?.dbName, row, i + 1)) }
                }
            }
        } else {
            items(rows.size) { i ->
                val row = rows[i]
                Box(Modifier.padding(horizontal = 14.dp)) {
                    RecordEntryRow(i + 1, row, def, period, category, maxValue, color, rows.getOrNull(i + 1)?.r?.value) { onEntry(RecordEntryRef(def, period, category, sub?.dbName, row, i + 1)) }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PerfectAllKillTop20Podium(
    rows: List<RecordRow>,
    def: RecordDef,
    period: Period?,
    color: Color,
    onEntry: (RecordRow, Int) -> Unit
) {
    val theme = Nova.theme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp).height(166.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(1, 0, 2).forEach { index ->
            val row = rows[index]
            val first = index == 0
            val medalColor = when (index) { 0 -> NovaColors.Gold; 1 -> NovaColors.Silver; else -> Color(0xFFCD7F32) }
            Column(
                Modifier.weight(if (first) 1.15f else 1f)
                    .height(if (first) 166.dp else if (index == 1) 139.dp else 126.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Brush.verticalGradient(listOf(medalColor.copy(alpha = 0.28f), theme.surface.copy(alpha = 0.94f))))
                    .border(1.3.dp, medalColor.copy(alpha = 0.58f), RoundedCornerShape(15.dp))
                    .clickable { onEntry(row, index + 1) }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(listOf("🥇", "🥈", "🥉")[index], fontSize = if (first) 23.sp else 19.sp)
                CoverArt(row.imageUrl, row.name ?: "Artiste inconnu", size = if (first) 50 else 42, circle = true)
                Text(row.name ?: "Inconnu", color = if (first) NovaColors.Gold else theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatRecordValue(def, period, row.r.value), color = medalColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
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
private fun RecordEntryRow(rank: Int, row: RecordRow, def: RecordDef, period: Period?, category: RecordCategory, maxValue: Double, color: Color, runnerUp: Double?, onClick: () -> Unit) {
    // 🎤 Invités décisifs (détenteur du record uniquement) : sans leur version, le record tombait sous le dauphin
    val recDb = (LocalContext.current.applicationContext as NovaStatsApp).database
    var decisiveGuests by remember(row.r.id) { mutableStateOf<List<HonorGuest>>(emptyList()) }
    LaunchedEffect(row.r.id, runnerUp) {
        if (category != RecordCategory.TRACK || rank != 1) return@LaunchedEffect
        decisiveGuests = runCatching { HonorGuests.decisiveForRecord(recDb, row.r.entityId, row.r.value, runnerUp, row.r.valueDate) }.getOrDefault(emptyList())
    }
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
                // 🔥 Séries d'écoute : puce « EN COURS » pulsante + palier de flamme atteint
                if (def.id == "LONGEST_LISTENING_STREAK") {
                    val streakDays = row.r.value.toInt()
                    val tier = RecordCatalog.tierFor(streakDays)
                    val ongoing = row.r.extraData?.contains("en cours") == true
                    if (ongoing || tier != null) {
                        Spacer(Modifier.height(3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (ongoing) {
                                val pulse = rememberInfiniteTransition(label = "streak")
                                val a by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "pulse")
                                Text(
                                    "🔥 EN COURS · jour $streakDays", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFF6D00).copy(alpha = a)).padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                            tier?.let { t ->
                                Text(
                                    "${t.emoji} ${t.label}", color = Color(t.colorArgb), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(t.colorArgb).copy(alpha = 0.12f))
                                        .border(1.dp, Color(t.colorArgb).copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
                // 🎯 Most Simultaneous : palier de prise d'otage (Présence / Duo de choc / Mainmise / Razzia)
                if (def.id == "MOST_SIMULTANEOUS") {
                    val cnt = row.r.value.toInt()
                    val zl = RecordCatalog.zoneLimit(row.r.subcategory?.substringAfterLast('_'))
                    RecordCatalog.simultTier(cnt, zl)?.let { t ->
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${t.emoji} ${t.label}", color = Color(t.colorArgb), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(t.colorArgb).copy(alpha = 0.12f))
                                .border(1.dp, Color(t.colorArgb).copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                // 🧹 Podium Sweep : palier du balayeur (Coup de balai / Récidiviste / Balayeur en série / Monopole)
                if (def.id == "PODIUM_SWEEP") {
                    RecordCatalog.sweepTier(row.r.value.toInt())?.let { t ->
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${t.emoji} ${t.label}", color = Color(t.colorArgb), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color(t.colorArgb).copy(alpha = 0.12f))
                                .border(1.dp, Color(t.colorArgb).copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatRecordValue(def, period, row.r.value), color = if (first) NovaColors.Gold else color, fontWeight = FontWeight.Bold, fontSize = if (first) 17.sp else 14.sp)
                row.r.valueDate?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall) }
                decisiveGuests.forEach { g -> Text("🎤 ${g.artistName} (via « ${g.viaTitle} ») — ce record n'existerait pas sans ses écoutes", color = theme.accent, style = MaterialTheme.typography.labelSmall) }
            }
            Text("›", color = theme.textSecondary, fontSize = 22.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Box(Modifier.fillMaxWidth().padding(top = 6.dp, start = 36.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(theme.textSecondary.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).background(if (first) Brush.horizontalGradient(listOf(color, NovaColors.Gold)) else Brush.horizontalGradient(listOf(color, color.copy(alpha = 0.6f)))))
        }
    }
}

/* ================================ 🏆 Galerie Most Records ================================ */

private data class MostRecordsRecordBundle(
    val def: RecordDef,
    val group: RecordGroup,
    val held: List<RecordExplainer.Held>
)

private data class MostRecordsFamilyBundle(
    val group: RecordGroup,
    val records: List<MostRecordsRecordBundle>,
    val wins: Int
)

private fun mostRecordsSubLabel(entry: RecordExplainer.Held): String? =
    entry.def.subs[entry.category]?.firstOrNull { it.dbName == entry.sub }?.label
        ?: entry.sub?.takeIf { it.isNotBlank() && it != "ALL" }
            ?.lowercase(Locale.FRANCE)?.replace('_', ' ')
            ?.replaceFirstChar { it.uppercase() }

private fun mostRecordsEntrySearchText(entry: RecordExplainer.Held): String = listOfNotNull(
    entry.def.title,
    entry.def.description,
    entry.def.id,
    entry.period?.frLabel,
    entry.category.label,
    mostRecordsSubLabel(entry),
    entry.row.extraData
).joinToString(" ")

@Composable
private fun MostRecordsShowcase(
    score: Int,
    rank: Int?,
    recordCount: Int,
    familyCount: Int,
    sharedCount: Int,
    signature: MostRecordsRecordBundle?,
    color: Color
) {
    val theme = Nova.theme
    val glow by rememberInfiniteTransition(label = "most-records-glow").animateFloat(
        initialValue = 0.16f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "most-records-rim"
    )
    val counter = remember(score) { Animatable(0f) }
    LaunchedEffect(score) {
        counter.snapTo(0f)
        counter.animateTo(score.toFloat(), tween(1050))
    }
    val goldRim = Brush.horizontalGradient(
        listOf(NovaColors.Gold.copy(alpha = 0.40f + glow), color.copy(alpha = 0.30f + glow * 0.55f), NovaColors.Gold.copy(alpha = 0.38f + glow))
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        SectionTitle("🏆 La galerie des records", NovaColors.Gold)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(25.dp))
                .background(Brush.linearGradient(listOf(color.copy(alpha = 0.24f), theme.surface.copy(alpha = 0.97f), NovaColors.Gold.copy(alpha = 0.13f))))
                .border(1.5.dp, goldRim, RoundedCornerShape(25.dp)).padding(15.dp)
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 8.dp).size(104.dp).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(NovaColors.Gold.copy(alpha = glow * 0.50f), color.copy(alpha = 0.06f), Color.Transparent)))
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PALMARÈS D'ÉLITE", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Une collection de premières places", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text("Chaque victoire correspond à un classement détenu.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("🏆", fontSize = 39.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(counter.value.toInt().toString(), color = NovaColors.Gold, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 46.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("PREMIÈRES PLACES DÉTENUES", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Text("record × période × section × sous-catégorie", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    rank?.let {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(NovaColors.Gold.copy(alpha = 0.12f)).border(1.dp, NovaColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("RANG", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                            Text("#$it", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MostRecordsStatPill(recordCount.toString(), "records distincts", color, Modifier.weight(1f))
                    MostRecordsStatPill(familyCount.toString(), "familles", NovaColors.Gold, Modifier.weight(1f))
                    MostRecordsStatPill(sharedCount.toString(), "ex æquo", theme.textSecondary, Modifier.weight(1f))
                }
                signature?.let { best ->
                    Spacer(Modifier.height(11.dp))
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.background.copy(alpha = 0.32f))
                            .border(1.dp, NovaColors.Gold.copy(alpha = 0.20f), RoundedCornerShape(14.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✦", color = NovaColors.Gold, fontSize = 21.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("RECORD SIGNATURE", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
                            Text("${best.def.emoji} ${best.def.title}", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${best.held.size} #1", color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "Une égalité compte comme une première place partagée.",
            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun MostRecordsStatPill(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(13.dp)).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Text(label.uppercase(Locale.FRANCE), color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MostRecordsFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedFamily: RecordGroup?,
    onFamilySelected: (RecordGroup?) -> Unit,
    familyCounts: Map<RecordGroup, Int>,
    visibleWinCount: Int,
    visibleRecordCount: Int
) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = theme.textSecondary) },
            placeholder = { Text("Chercher un record, une période, une zone…", color = theme.textSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NovaColors.Gold,
                unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.35f),
                focusedTextColor = theme.text,
                unfocusedTextColor = theme.text,
                cursorColor = NovaColors.Gold
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NovaFilterChip(
                flagKey = "palmares_all", selected = selectedFamily == null, onClick = { onFamilySelected(null) },
                label = { Text("✨ Tout (${familyCounts.values.sum()})") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = NovaColors.Gold.copy(alpha = 0.20f), selectedLabelColor = theme.text),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedFamily == null, borderColor = NovaColors.Gold.copy(alpha = 0.4f), selectedBorderColor = NovaColors.Gold)
            )
            RecordGroup.entries.filter { (familyCounts[it] ?: 0) > 0 }.forEach { group ->
                val accent = groupColor(group)
                NovaFilterChip(
                    flagKey = group, selected = selectedFamily == group, onClick = { onFamilySelected(if (selectedFamily == group) null else group) },
                    label = { Text("${group.emoji} ${group.label} (${familyCounts[group] ?: 0})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent.copy(alpha = 0.22f), selectedLabelColor = theme.text),
                    border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedFamily == group, borderColor = accent.copy(alpha = 0.35f), selectedBorderColor = accent)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "$visibleWinCount victoire${if (visibleWinCount == 1) "" else "s"} affichée${if (visibleWinCount == 1) "" else "s"} · $visibleRecordCount record${if (visibleRecordCount == 1) "" else "s"} distinct${if (visibleRecordCount == 1) "" else "s"}",
            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun MostRecordsFamilyCard(
    family: MostRecordsFamilyBundle,
    totalWins: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val theme = Nova.theme
    val accent = groupColor(family.group)
    val progress = (family.wins.toFloat() / totalWins.coerceAtLeast(1)).coerceIn(0.04f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.17f), theme.surface.copy(alpha = 0.94f), accent.copy(alpha = 0.08f))))
                .border(1.dp, accent.copy(alpha = if (expanded) 0.48f else 0.22f), RoundedCornerShape(17.dp))
                .clickable(onClick = onClick).animateContentSize().padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(39.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)).border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text(family.group.emoji, fontSize = 21.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(family.group.label, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                    Text("${family.records.size} record${if (family.records.size == 1) "" else "s"} · ${family.wins} classement${if (family.wins == 1) "" else "s"} #1", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(family.group.description, color = theme.textSecondary.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${family.wins}", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text(if (expanded) "RÉDUIRE" else "EXPLORER", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(5.dp))
                Text(if (expanded) "⌃" else "⌄", color = accent, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(theme.background.copy(alpha = 0.42f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(accent))
            }
        }
    }
}

@Composable
private fun MostRecordsBundleCard(
    bundle: MostRecordsRecordBundle,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val theme = Nova.theme
    val accent = groupColor(bundle.group)
    Column(
        Modifier.fillMaxWidth().padding(start = 25.dp, end = 16.dp, top = 4.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(14.dp)).background(theme.surface.copy(alpha = 0.75f))
            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).animateContentSize().padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bundle.def.emoji, fontSize = 22.sp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(bundle.def.title, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${bundle.held.size} classement${if (bundle.held.size == 1) "" else "s"} détenu${if (bundle.held.size == 1) "" else "s"} · voir périodes et zones", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Text(if (expanded) "⌃" else "＋", color = accent, fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            Text(bundle.def.description, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun MostRecordsVictoryRow(
    entry: RecordExplainer.Held,
    onEntry: (RecordExplainer.Held) -> Unit
) {
    val theme = Nova.theme
    val accent = groupColor(entry.groupForMostRecords())
    val subLabel = mostRecordsSubLabel(entry)
    val context = listOfNotNull(entry.period?.frLabel, entry.category.label, subLabel)
        .joinToString(" · ").ifBlank { "Toutes périodes" }
    val dateLabel = entry.row.valueDate?.let { iso -> runCatching { Dates.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE)) }.getOrDefault(iso) }
    Row(
        Modifier.fillMaxWidth().padding(start = 38.dp, end = 16.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(13.dp)).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.10f), theme.surface.copy(alpha = 0.92f))))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(13.dp))
            .clickable { onEntry(entry) }.padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("🏅", fontSize = 15.sp)
                Text(context, color = theme.textSecondary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            dateLabel?.let { Text("Obtenu le $it", color = theme.textSecondary.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 21.dp, top = 2.dp)) }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatRecordValue(entry.def, entry.period, entry.row.value), color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (entry.tied > 1) {
                Text("🤝 PARTAGÉ · ${entry.tied}", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            } else Text("#1", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
        }
        Text("›", color = theme.textSecondary, fontSize = 21.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun MostConsistentSpotlight(
    def: RecordDef,
    name: String,
    imageUrl: String?,
    isArtist: Boolean,
    score: Int,
    rank: Int?,
    period: Period?,
    zoneLabel: String,
    points: List<RecordExplainer.Point>,
    color: Color
) {
    val theme = Nova.theme
    val accent = Color(0xFF80AEE8)
    val glow by rememberInfiniteTransition(label = "consistent-glow").animateFloat(
        initialValue = 0.10f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "consistent-rim"
    )
    val counter = remember(score) { Animatable(0f) }
    LaunchedEffect(score) {
        counter.snapTo(0f)
        counter.animateTo(score.coerceAtLeast(0).toFloat(), tween(1000))
    }
    val runPoints = remember(points) { points.filter { it.highlight } }
    val runStepByDate = remember(runPoints) { runPoints.mapIndexed { index, point -> point.date to (index + 1) }.toMap() }
    val bestPosition = runPoints.mapNotNull { it.position }.minOrNull()
    val rim = Brush.horizontalGradient(
        listOf(accent.copy(alpha = 0.34f + glow), color.copy(alpha = 0.22f + glow * 0.4f), accent.copy(alpha = 0.30f + glow))
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        SectionTitle("🧱 La série de fer", accent)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), theme.surface.copy(alpha = 0.97f), color.copy(alpha = 0.10f))))
                .border(1.5.dp, rim, RoundedCornerShape(22.dp)).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(imageUrl, name, size = 48, circle = isArtist)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("SÉRIE RECORD · ${zoneLabel.uppercase(Locale.FRANCE)}", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(name, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(period?.label ?: "Cadence non précisée", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                rank?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background((if (it == 1) NovaColors.Gold else accent).copy(alpha = 0.13f)).border(1.dp, (if (it == 1) NovaColors.Gold else accent).copy(alpha = 0.42f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
                        Text("RANG", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("#$it", color = if (it == 1) NovaColors.Gold else accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(counter.value.toInt().toString(), color = accent, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 46.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatRecordValue(def, period, score.toDouble()).uppercase(Locale.FRANCE), color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    Text("dans cette zone, sans interruption", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MostConsistentMetric(runPoints.firstOrNull()?.label ?: "—", "DÉBUT", accent, Modifier.weight(1f))
                MostConsistentMetric(runPoints.lastOrNull()?.label ?: "—", "FIN", accent, Modifier.weight(1f))
                MostConsistentMetric(bestPosition?.let { "#$it" } ?: "—", "MEILLEUR RANG", NovaColors.Gold, Modifier.weight(1f))
            }
            Spacer(Modifier.height(13.dp))
            Text("TRAJECTOIRE · SÉRIE RECORD EN SURBRILLANCE", color = theme.textSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(7.dp))
            if (points.isEmpty()) {
                Text("La trajectoire détaillée n'est pas disponible.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(points, key = { index, point -> "consistent-${point.date}-$index" }) { _, point ->
                        val inRun = point.highlight
                        val step = runStepByDate[point.date]
                        val pointAccent = if (inRun) accent else theme.textSecondary
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (inRun) accent.copy(alpha = 0.16f) else theme.surface.copy(alpha = 0.68f))
                                .border(1.dp, pointAccent.copy(alpha = if (inRun) 0.55f else 0.18f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(if (inRun) "SÉRIE · ${step ?: ""}" else "APPARITION", color = pointAccent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(point.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(point.position?.let { "#$it" } ?: "—", color = if (point.position == 1) NovaColors.Gold else if (inRun) accent else theme.text, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Les étapes lumineuses composent la plus longue série continue. Une période manquée ou une sortie de la zone la brise.",
                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun MostConsistentMetric(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(12.dp)).padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MostWeeksBlockedSpotlight(
    def: RecordDef,
    name: String,
    imageUrl: String?,
    isArtist: Boolean,
    score: Int,
    rank: Int?,
    period: Period?,
    contextLabel: String,
    zoneLabel: String,
    bestPosition: Int?,
    secondPlaceCount: Int,
    points: List<RecordExplainer.Point>,
    color: Color
) {
    val theme = Nova.theme
    val accent = Color(0xFFFF9257)
    val glow by rememberInfiniteTransition(label = "blocked-glow").animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(1050), RepeatMode.Reverse),
        label = "blocked-rim"
    )
    val counter = remember(score) { Animatable(0f) }
    LaunchedEffect(score) {
        counter.snapTo(0f)
        counter.animateTo(score.coerceAtLeast(0).toFloat(), tween(1000))
    }
    val rim = Brush.horizontalGradient(
        listOf(accent.copy(alpha = 0.35f + glow), color.copy(alpha = 0.19f + glow * 0.35f), accent.copy(alpha = 0.30f + glow))
    )
    val plainZoneLabel = zoneLabel.substringAfter(' ', zoneLabel).removePrefix("le ")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        SectionTitle("🚧 Le mur du #1", accent)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A2224).copy(alpha = 0.72f), theme.surface.copy(alpha = 0.97f), color.copy(alpha = 0.12f))))
                .border(1.5.dp, rim, RoundedCornerShape(22.dp)).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(imageUrl, name, size = 48, circle = isArtist)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("BLOQUÉ AU SOMMET", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp)
                    Text(name, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(contextLabel, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                rank?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background((if (it == 1) NovaColors.Gold else accent).copy(alpha = 0.13f)).border(1.dp, (if (it == 1) NovaColors.Gold else accent).copy(alpha = 0.42f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
                        Text("RANG", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("#$it", color = if (it == 1) NovaColors.Gold else accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(counter.value.toInt().toString(), color = accent, fontWeight = FontWeight.Black, fontSize = 43.sp, lineHeight = 46.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatRecordValue(def, period, score.toDouble()).uppercase(Locale.FRANCE), color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    Text("dans le $plainZoneLabel, sans jamais atteindre #1", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MostConsistentMetric(bestPosition?.let { "#$it" } ?: "—", "MEILLEUR RANG", NovaColors.Gold, Modifier.weight(1f))
                MostConsistentMetric(secondPlaceCount.toString(), "PASSAGES #2", accent, Modifier.weight(1f))
                MostConsistentMetric(period?.label ?: "—", "CADENCE", color, Modifier.weight(1f))
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(NovaColors.Gold.copy(alpha = 0.14f)).border(1.dp, NovaColors.Gold.copy(alpha = 0.52f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("1", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("LA BARRIÈRE : LA PREMIÈRE PLACE", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                    Text("Chaque période lumineuse montre une apparition dans la zone — toujours derrière le #1.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 15.sp)
                }
            }
            Spacer(Modifier.height(13.dp))
            Text("TENTATIVES DANS LA ZONE", color = theme.textSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(7.dp))
            if (points.isEmpty()) {
                Text("La trajectoire détaillée n'est pas disponible.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(points, key = { index, point -> "blocked-${point.date}-$index" }) { _, point ->
                        val inZone = point.highlight
                        val pointAccent = if (inZone) accent else theme.textSecondary
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (inZone) accent.copy(alpha = 0.16f) else theme.surface.copy(alpha = 0.68f))
                                .border(1.dp, pointAccent.copy(alpha = if (inZone) 0.55f else 0.18f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(if (inZone) "DANS LA ZONE" else "HORS ZONE", color = pointAccent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(point.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(point.position?.let { "#$it" } ?: "—", color = if (inZone) accent else theme.text, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "Le score compte les périodes dans la zone sélectionnée. Il ne s'agit pas d'une série consécutive.",
                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun MostWeeksBlockedRivals(
    items: List<RecordExplainer.Item>,
    onOpen: (RecordExplainer.Item) -> Unit
) {
    val theme = Nova.theme
    val accent = Color(0xFFFF9257)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        SectionTitle("🧱 Ceux qui occupaient le #1", accent)
        if (items.isEmpty()) {
            Text("Aucun bloqueur n'a pu être identifié pour ces périodes.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Les éléments ci-dessous étaient au sommet pendant ses apparitions dans la zone.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            items.forEachIndexed { index, item ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(accent.copy(alpha = if (index == 0) 0.16f else 0.08f), theme.surface.copy(alpha = 0.86f))))
                        .border(1.dp, accent.copy(alpha = if (index == 0) 0.42f else 0.20f), RoundedCornerShape(14.dp))
                        .clickable { onOpen(item) }.padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(25.dp).clip(CircleShape).background(if (index == 0) NovaColors.Gold.copy(alpha = 0.16f) else theme.background.copy(alpha = 0.62f)), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = if (index == 0) NovaColors.Gold else theme.textSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(8.dp))
                    CoverArt(item.imageUrl, item.name, size = 43, circle = item.type == EntityType.ARTIST)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.detail, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (index == 0) "🥇" else "#1", color = if (index == 0) NovaColors.Gold else accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

private fun lifespanDateLabel(period: Period?, iso: String?): String {
    if (iso == null) return "—"
    val date = runCatching { Dates.parse(iso) }.getOrNull() ?: return iso
    val fullDate = date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRANCE))
    return when (period) {
        Period.DAILY -> "le $fullDate"
        Period.WEEKLY -> "sem. du $fullDate"
        Period.MONTHLY -> date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE))
        Period.YEARLY -> date.year.toString()
        null -> fullDate
    }
}

@Composable
private fun LongestLifespanSpotlight(
    def: RecordDef,
    name: String,
    imageUrl: String?,
    isArtist: Boolean,
    score: Int,
    rank: Int?,
    period: Period?,
    context: String,
    zoneLabel: String,
    zoneLimit: Int,
    points: List<RecordExplainer.Point>,
    color: Color
) {
    val theme = Nova.theme
    val accent = Color(0xFF78C8D2)
    val gold = NovaColors.Gold
    val glow by rememberInfiniteTransition(label = "lifespan-glow").animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "lifespan-rim"
    )
    val counter = remember(score) { Animatable(0f) }
    LaunchedEffect(score) {
        counter.snapTo(0f)
        counter.animateTo(score.coerceAtLeast(0).toFloat(), tween(1050))
    }
    val zonePoints = remember(points, zoneLimit) { points.filter { point -> point.position?.let { it <= zoneLimit } == true } }
    val firstZonePoint = zonePoints.firstOrNull()
    val lastZonePoint = zonePoints.lastOrNull()
    val bestPosition = zonePoints.mapNotNull { it.position }.minOrNull()
    val appearanceCount = zonePoints.size
    val coverage = if (score > 0) (100 * appearanceCount / score).coerceIn(0, 100) else 0
    val returnCount = remember(points) { points.count { !it.note.isNullOrBlank() } }
    val plainZoneLabel = zoneLabel.substringAfter(' ', zoneLabel).removePrefix("le ")
    val rankAccent = if (rank == 1) gold else accent
    val rim = Brush.horizontalGradient(
        listOf(gold.copy(alpha = 0.26f + glow * 0.35f), accent.copy(alpha = 0.24f + glow), color.copy(alpha = 0.16f + glow * 0.35f), gold.copy(alpha = 0.20f + glow * 0.30f))
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
        SectionTitle("⏳ L'épopée dans le chart", accent)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF18353B).copy(alpha = 0.70f), theme.surface.copy(alpha = 0.97f), gold.copy(alpha = 0.11f))))
                .border(1.5.dp, rim, RoundedCornerShape(23.dp)).padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(imageUrl, name, size = 50, circle = isArtist)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("LONGÉVITÉ · ${plainZoneLabel.uppercase(Locale.FRANCE)}", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(name, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(context, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                rank?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(rankAccent.copy(alpha = 0.13f)).border(1.dp, rankAccent.copy(alpha = 0.42f), RoundedCornerShape(12.dp)).padding(horizontal = 9.dp, vertical = 6.dp)) {
                        Text("RANG", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("#$it", color = rankAccent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(counter.value.toInt().toString(), color = gold, fontWeight = FontWeight.Black, fontSize = 44.sp, lineHeight = 46.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatRecordValue(def, period, score.toDouble()).uppercase(Locale.FRANCE), color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    Text("entre le premier et le dernier passage dans la zone", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                "${lifespanDateLabel(period, firstZonePoint?.date)}  →  ${lifespanDateLabel(period, lastZonePoint?.date)}",
                color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.09f)).padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RecordSpotlightMetric(appearanceCount.toString(), "APPARITIONS EN ZONE", accent, Modifier.weight(1f))
                RecordSpotlightMetric("$coverage %", "TAUX DE PRÉSENCE", gold, Modifier.weight(1f))
                RecordSpotlightMetric(returnCount.toString(), "RETOURS", color, Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(theme.background.copy(alpha = 0.42f))) {
                Box(Modifier.fillMaxWidth(coverage / 100f).height(5.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(accent, gold))))
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                RecordSpotlightMetric(bestPosition?.let { "#$it" } ?: "—", "MEILLEUR RANG EN ZONE", gold, Modifier.weight(1f))
                RecordSpotlightMetric(period?.label ?: "—", "CADENCE", accent, Modifier.weight(1f))
            }
            Spacer(Modifier.height(13.dp))
            Text("CHRONOLOGIE DE CARRIÈRE", color = theme.textSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            Text("Les passages dans la zone brillent; les retours indiquent la durée de l'absence.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(7.dp))
            if (points.isEmpty()) {
                Text("La chronologie détaillée n'est pas disponible.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(points, key = { index, point -> "lifespan-${point.date}-$index" }) { _, point ->
                        val position = point.position
                        val inZone = position?.let { it <= zoneLimit } == true
                        val isStart = point.date == firstZonePoint?.date
                        val isEnd = point.date == lastZonePoint?.date
                        val endpoint = isStart || isEnd
                        val pointAccent = when {
                            endpoint -> gold
                            inZone -> accent
                            else -> theme.textSecondary
                        }
                        Column(
                            Modifier.width(if (point.note != null) 112.dp else 76.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (endpoint) gold.copy(alpha = 0.15f) else if (inZone) accent.copy(alpha = 0.13f) else theme.surface.copy(alpha = 0.66f))
                                .border(1.dp, pointAccent.copy(alpha = if (endpoint) 0.62f else if (inZone) 0.38f else 0.16f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 6.dp, vertical = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                when { isStart && isEnd -> "DÉBUT · FIN"; isStart -> "DÉBUT"; isEnd -> "DERNIER PASSAGE"; inZone -> "DANS LA ZONE"; else -> "HORS ZONE" },
                                color = pointAccent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                            )
                            Text(point.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(point.position?.let { "#$it" } ?: "—", color = if (point.position == 1) gold else if (inZone) accent else theme.text, fontWeight = FontWeight.Black, fontSize = 19.sp)
                            point.note?.let { Text(it, color = gold, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                "La durée inclut les absences : elle va du premier au dernier passage dans la zone, pas seulement les périodes de présence.",
                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun RecordSpotlightMetric(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(12.dp)).padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(label, color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun RecordExplainer.Held.groupForMostRecords(): RecordGroup = RecordCatalog.groupOf[def.id] ?: RecordGroup.TOTALS

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
    var mostRecordsSearch by remember(ref) { mutableStateOf("") }
    var selectedMostRecordsFamily by remember(ref) { mutableStateOf<RecordGroup?>(null) }
    var expandedMostRecordsFamilies by remember(ref) { mutableStateOf<Set<RecordGroup>>(emptySet()) }
    var expandedMostRecordsIds by remember(ref) { mutableStateOf<Set<String>>(emptySet()) }
    val mostRecordsHeld = story?.held.orEmpty()
    val mostRecordsBundles = remember(mostRecordsHeld) {
        mostRecordsHeld.groupBy { it.def.id }.values.mapNotNull { entries ->
            val first = entries.firstOrNull() ?: return@mapNotNull null
            val group = RecordCatalog.groupOf[first.def.id] ?: RecordGroup.TOTALS
            MostRecordsRecordBundle(
                def = first.def,
                group = group,
                held = entries.sortedWith(compareBy<RecordExplainer.Held> { it.period?.ordinal ?: Int.MAX_VALUE }.thenBy { it.sub.orEmpty() })
            )
        }.sortedWith(compareByDescending<MostRecordsRecordBundle> { it.held.size }.thenBy { it.def.title })
    }
    val mostRecordsFamilies = remember(mostRecordsBundles) {
        mostRecordsBundles.groupBy { it.group }.map { (group, records) ->
            val ordered = records.sortedWith(compareByDescending<MostRecordsRecordBundle> { it.held.size }.thenBy { it.def.title })
            MostRecordsFamilyBundle(group, ordered, ordered.sumOf { it.held.size })
        }.sortedWith(compareByDescending<MostRecordsFamilyBundle> { it.wins }.thenBy { it.group.ordinal })
    }
    LaunchedEffect(mostRecordsFamilies) {
        if (expandedMostRecordsFamilies.isEmpty() && mostRecordsFamilies.isNotEmpty()) {
            val firstFamily = mostRecordsFamilies.first()
            expandedMostRecordsFamilies = setOf(firstFamily.group)
            firstFamily.records.firstOrNull()?.def?.id?.let { expandedMostRecordsIds = setOf(it) }
        }
    }
    val visibleMostRecordsFamilies = remember(mostRecordsFamilies, selectedMostRecordsFamily, mostRecordsSearch) {
        val query = mostRecordsSearch.trim()
        mostRecordsFamilies.mapNotNull { family ->
            if (selectedMostRecordsFamily != null && family.group != selectedMostRecordsFamily) return@mapNotNull null
            val familyMatches = query.isNotEmpty() && family.group.label.contains(query, ignoreCase = true)
            val records = family.records.mapNotNull { bundle ->
                val recordMatches = query.isEmpty() || familyMatches ||
                    bundle.def.title.contains(query, ignoreCase = true) ||
                    bundle.def.description.contains(query, ignoreCase = true) ||
                    bundle.def.id.contains(query, ignoreCase = true)
                val matchingEntries = if (recordMatches) bundle.held else bundle.held.filter {
                    mostRecordsEntrySearchText(it).contains(query, ignoreCase = true)
                }
                matchingEntries.takeIf { it.isNotEmpty() }?.let { bundle.copy(held = matchingEntries) }
            }
            records.takeIf { it.isNotEmpty() }?.let { matched ->
                MostRecordsFamilyBundle(family.group, matched, matched.sumOf { it.held.size })
            }
        }
    }
    val familyWinCounts = mostRecordsFamilies.associate { it.group to it.wins }
    val visibleMostRecordsWinCount = visibleMostRecordsFamilies.sumOf { it.wins }
    val visibleMostRecordsRecordCount = visibleMostRecordsFamilies.sumOf { it.records.size }
    val shareScope = rememberCoroutineScope()
    var sharingSongReach by remember(ref) { mutableStateOf(false) }
    var songReachShareError by remember(ref) { mutableStateOf(false) }
    var sharingHonorCard by remember(ref) { mutableStateOf(false) }
    var honorShareError by remember(ref) { mutableStateOf(false) }
    var sharingArtistNoOne by remember(ref) { mutableStateOf(false) }
    var artistNoOneShareError by remember(ref) { mutableStateOf(false) }
    var sharingPakRecord by remember(ref) { mutableStateOf(false) }
    var pakRecordShareError by remember(ref) { mutableStateOf(false) }
    var selectedPakEventDate by remember(ref) { mutableStateOf<String?>(null) }
    var selectedPakEvent by remember(ref) { mutableStateOf<RecordExplainer.PerfectAllKillVisual?>(null) }
    var loadingPakEvent by remember(ref) { mutableStateOf(false) }
    var sharingPakEvent by remember(ref) { mutableStateOf(false) }
    var pakEventShareError by remember(ref) { mutableStateOf(false) }

    LaunchedEffect(story?.perfectAllKillRecordVisual?.dates) {
        if (selectedPakEventDate == null) selectedPakEventDate = story?.perfectAllKillRecordVisual?.dates?.lastOrNull()
    }
    LaunchedEffect(ref, selectedPakEventDate, story?.perfectAllKillRecordVisual?.featuredEvents) {
        val date = selectedPakEventDate ?: return@LaunchedEffect
        val recordVisual = story?.perfectAllKillRecordVisual ?: return@LaunchedEffect
        val preloaded = recordVisual.featuredEvents.firstOrNull { it.dateIso == date }
        if (preloaded != null) {
            selectedPakEvent = preloaded
            loadingPakEvent = false
        } else {
            loadingPakEvent = true
            try {
                selectedPakEvent = app.recordExplainer.perfectAllKillVisual(
                    ref.period?.dbName ?: Period.DAILY.dbName, date, row.r.entityId, includeMargins = true
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                selectedPakEvent = null
            } finally {
                loadingPakEvent = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(theme.background)) {
        item {
            PageHeader(
                def.title,
                if (def.id == "MOST_RECORDS") "🏆 Palmarès des records" else "${def.emoji} ${def.title}",
                color,
                onBack,
                if (def.id == "MOST_RECORDS") "Les classements où $name est à la première place" else context
            )
        }
        item {
            // En-tête : pochette, nom et rang. Les records premium de vitesse, succession et présence ont leur fond dédié.
            val streakRecord = def.id == "LONGEST_LISTENING_STREAK"
            val speedRecord = def.id == "FASTEST_CERT" || def.id == "FASTEST_PANTHEON"
            val successiveRecord = def.id == "MOST_SUCCESSIVE_1"
            val presenceRecord = def.id in setOf("MOST_CUMULATIVE", "MOST_CUMULATIVE_TOP10", "MOST_CUMULATIVE_TOP5", "MOST_TIME_AT_1")
            val songReachRecord = def.id in setOf("MOST_SONGS_IN_CHART", "MOST_SONGS_TOP10", "MOST_SONGS_TOP5", "MOST_SONGS_AT_1")
            val honorRecord = def.id == "MOST_HOF" || def.id == "MOST_GLOBAL"
            val artistNoOneRecord = def.id == "MOST_CUMULATIVE_ARTIST_NO1"
            val perfectAllKillRecord = def.id == "MOST_PERFECT_ALL_KILL"
            val mostRecordsRecord = def.id == "MOST_RECORDS"
            val mostConsistentRecord = def.id == "MOST_CONSISTENT"
            val mostBlockedRecord = def.id == "MOST_BLOCKED_TOP5"
            val lifespanRecord = def.id == "LONGEST_LIFESPAN"
            Box(Modifier.fillMaxWidth()) {
                if (streakRecord || speedRecord || successiveRecord || presenceRecord || songReachRecord || honorRecord || artistNoOneRecord || perfectAllKillRecord || mostRecordsRecord || mostConsistentRecord || mostBlockedRecord || lifespanRecord) BlurredBackdrop(row.imageUrl, color, Modifier.matchParentSize())
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoverArt(row.imageUrl, name, size = 110, circle = ref.category == RecordCategory.ARTIST, zoomable = true)
                    Spacer(Modifier.height(10.dp))
                    Text(name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 22.sp, textAlign = TextAlign.Center)
                    row.subtitle?.let { Text(it, color = theme.textSecondary, textAlign = TextAlign.Center) }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ref.rank?.let { r ->
                            val medal = if (r in 1..3) listOf("🥇", "🥈", "🥉")[r - 1] else "#$r"
                            Text(medal + if (r > 3) " du classement" else "", color = if (r == 1) NovaColors.Gold else theme.textSecondary, fontWeight = FontWeight.Bold)
                        }
                        if (streakRecord) {
                            // Compteur qui monte jour par jour jusqu'à la valeur du record
                            val counter = remember(row.r.value) { Animatable(0f) }
                            LaunchedEffect(row.r.value) { counter.animateTo(row.r.value.toFloat(), tween(1100)) }
                            Text("${counter.value.toInt()} jours", color = color, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        } else if (speedRecord) {
                            val counter = remember(row.r.value) { Animatable(0f) }
                            LaunchedEffect(row.r.value) { counter.animateTo(1f, tween(1250)) }
                            Text(
                                "⏱ ${RecordCatalog.formatFastDuration((row.r.value * counter.value).toLong())}",
                                color = color, fontWeight = FontWeight.Black, fontSize = 24.sp
                            )
                        } else {
                            Text(formatRecordValue(def, ref.period, row.r.value), color = color, fontWeight = FontWeight.Black, fontSize = 24.sp)
                        }
                    }
                    if (!mostRecordsRecord && !lifespanRecord) row.r.extraData?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                }
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
        story?.speedVisual?.let { visual ->
            item { SpeedSpotlight(name, row.imageUrl, ref.category == RecordCategory.ARTIST, visual, color) }
            item { SpeedStageLadder(def.id, visual, color) }
            item { SpeedActivityCalendar(visual, color) }
        }
        story?.successiveVisual?.let { visual ->
            item { SuccessiveSpotlight(name, row.imageUrl, ref.category == RecordCategory.ARTIST, visual, color) }
            item { SuccessiveRelayTimeline(visual, color) }
        }
        story?.presenceVisual?.let { visual ->
            item { PresenceSpotlight(name, row.imageUrl, ref.category == RecordCategory.ARTIST, def.id, visual, color) }
            item { PresencePositionRibbon(visual, color) }
        }
        story?.songReachVisual?.let { visual ->
            item {
                SongReachSpotlight(
                    name = name,
                    imageUrl = row.imageUrl,
                    isArtist = ref.category == RecordCategory.ARTIST,
                    visual = visual,
                    color = color,
                    sharing = sharingSongReach,
                    shareError = songReachShareError,
                    onShare = {
                        if (!sharingSongReach) {
                            sharingSongReach = true
                            songReachShareError = false
                            shareScope.launch {
                                try {
                                    val file = ShareCards.renderSongReachCard(
                                        app, row.r.entityId, name, row.subtitle, row.imageUrl,
                                        ref.category == RecordCategory.ARTIST, visual, theme
                                    )
                                    ShareCards.share(app, file, "Partager ce record")
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    songReachShareError = true
                                } finally {
                                    sharingSongReach = false
                                }
                            }
                        }
                    }
                )
            }
        }
        story?.artistCumulativeNoOneVisual?.let { visual ->
            item {
                ArtistCumulativeNoOneSpotlight(
                    name = name,
                    imageUrl = row.imageUrl,
                    visual = visual,
                    color = color,
                    sharing = sharingArtistNoOne,
                    shareError = artistNoOneShareError,
                    onShare = {
                        if (!sharingArtistNoOne) {
                            sharingArtistNoOne = true
                            artistNoOneShareError = false
                            shareScope.launch {
                                try {
                                    val file = ShareCards.renderArtistCumulativeNoOneCard(
                                        app, row.r.entityId, name, row.subtitle, row.imageUrl, visual, theme
                                    )
                                    ShareCards.share(app, file, "Partager ce record de #1")
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    artistNoOneShareError = true
                                } finally {
                                    sharingArtistNoOne = false
                                }
                            }
                        }
                    }
                )
            }
        }
        story?.perfectAllKillRecordVisual?.let { visual ->
            val selectedDate = selectedPakEventDate ?: visual.dates.lastOrNull()
            val event = selectedDate?.let { date ->
                selectedPakEvent?.takeIf { it.dateIso == date }
                    ?: visual.featuredEvents.firstOrNull { it.dateIso == date }
            }
            item {
                PerfectAllKillRecordSpotlight(
                    name = name,
                    imageUrl = row.imageUrl,
                    visual = visual,
                    selectedDate = selectedDate,
                    selectedEvent = event,
                    eventLoading = loadingPakEvent,
                    sharing = sharingPakRecord,
                    shareError = pakRecordShareError,
                    sharingEvent = sharingPakEvent,
                    eventShareError = pakEventShareError,
                    color = color,
                    onSelectDate = { selectedPakEventDate = it },
                    onShareRecord = {
                        if (!sharingPakRecord) {
                            sharingPakRecord = true
                            pakRecordShareError = false
                            shareScope.launch {
                                try {
                                    val file = ShareCards.renderPerfectAllKillRecordCard(
                                        app, row.r.entityId, name, row.imageUrl, visual, theme
                                    )
                                    ShareCards.share(app, file, "Partager ce record PAK")
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    pakRecordShareError = true
                                } finally {
                                    sharingPakRecord = false
                                }
                            }
                        }
                    },
                    onShareEvent = {
                        val eventToShare = event
                        if (eventToShare != null && !sharingPakEvent) {
                            sharingPakEvent = true
                            pakEventShareError = false
                            shareScope.launch {
                                try {
                                    val file = ShareCards.renderPerfectAllKillCard(app, eventToShare, theme)
                                    ShareCards.share(app, file, "Partager l'événement Perfect All Kill")
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    pakEventShareError = true
                                } finally {
                                    sharingPakEvent = false
                                }
                            }
                        }
                    }
                )
            }
        }
        story?.honorVisual?.let { visual ->
            item {
                HonorRecordSpotlight(
                    name = name,
                    subtitle = row.subtitle,
                    imageUrl = row.imageUrl,
                    isArtist = ref.category == RecordCategory.ARTIST,
                    recordTitle = def.title,
                    contextLabel = context,
                    visual = visual,
                    sharing = sharingHonorCard,
                    shareError = honorShareError,
                    onShare = {
                        if (!sharingHonorCard) {
                            sharingHonorCard = true
                            honorShareError = false
                            shareScope.launch {
                                try {
                                    val file = ShareCards.renderHonorCard(
                                        app, row.r.entityId, ref.category.dbName, name, row.subtitle, row.imageUrl,
                                        ref.category == RecordCategory.ARTIST, def.title, context, visual, theme
                                    )
                                    ShareCards.share(app, file, "Partager cette distinction")
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    honorShareError = true
                                } finally {
                                    sharingHonorCard = false
                                }
                            }
                        }
                    }
                )
            }
        }
        val st = story
        if (st == null) {
            item { Text("Reconstruction de l'histoire…", color = theme.textSecondary, modifier = Modifier.fillMaxWidth().padding(24.dp), textAlign = TextAlign.Center) }
        } else {
            if (def.id == "MOST_CONSISTENT") {
                item(key = "most-consistent-showcase") {
                    MostConsistentSpotlight(
                        def = def,
                        name = name,
                        imageUrl = row.imageUrl,
                        isArtist = ref.category == RecordCategory.ARTIST,
                        score = row.r.value.toInt().coerceAtLeast(0),
                        rank = ref.rank,
                        period = ref.period,
                        zoneLabel = subLabel ?: "Chart",
                        points = st.timeline,
                        color = color
                    )
                }
            }
            if (def.id == "MOST_BLOCKED_TOP5") {
                item(key = "most-blocked-showcase") {
                    MostWeeksBlockedSpotlight(
                        def = def,
                        name = name,
                        imageUrl = row.imageUrl,
                        isArtist = ref.category == RecordCategory.ARTIST,
                        score = row.r.value.toInt().coerceAtLeast(0),
                        rank = ref.rank,
                        period = ref.period,
                        contextLabel = context,
                        zoneLabel = subLabel ?: "zone",
                        bestPosition = st.timeline.mapNotNull { it.position }.minOrNull(),
                        secondPlaceCount = st.timeline.count { it.position == 2 },
                        points = st.timeline,
                        color = color
                    )
                }
                item(key = "most-blocked-rivals") {
                    MostWeeksBlockedRivals(st.items) { rival ->
                        val target = when (rival.type) {
                            EntityType.TRACK -> DetailTarget.Track(rival.id)
                            EntityType.ALBUM -> DetailTarget.Album(rival.id)
                            else -> DetailTarget.Artist(rival.id)
                        }
                        onEntity(target)
                    }
                }
            }
            if (def.id == "LONGEST_LIFESPAN") {
                item(key = "longest-lifespan-showcase") {
                    LongestLifespanSpotlight(
                        def = def,
                        name = name,
                        imageUrl = row.imageUrl,
                        isArtist = ref.category == RecordCategory.ARTIST,
                        score = row.r.value.toInt().coerceAtLeast(0),
                        rank = ref.rank,
                        period = ref.period,
                        context = context,
                        zoneLabel = subLabel ?: "Chart",
                        zoneLimit = RecordCatalog.zoneLimit(ref.sub),
                        points = st.timeline,
                        color = color
                    )
                }
            }
            if (def.id == "MOST_RECORDS") {
                item(key = "most-records-showcase") {
                    MostRecordsShowcase(
                        score = row.r.value.toInt().coerceAtLeast(0),
                        rank = ref.rank,
                        recordCount = mostRecordsBundles.size,
                        familyCount = mostRecordsFamilies.size,
                        sharedCount = mostRecordsHeld.count { it.tied > 1 },
                        signature = mostRecordsBundles.firstOrNull(),
                        color = color
                    )
                }
                item(key = "most-records-filters") {
                    MostRecordsFilters(
                        query = mostRecordsSearch,
                        onQueryChange = { mostRecordsSearch = it },
                        selectedFamily = selectedMostRecordsFamily,
                        onFamilySelected = { family ->
                            selectedMostRecordsFamily = family
                            family?.let { selected ->
                                expandedMostRecordsFamilies = expandedMostRecordsFamilies + selected
                                mostRecordsFamilies.firstOrNull { it.group == selected }?.records?.firstOrNull()?.def?.id?.let {
                                    expandedMostRecordsIds = expandedMostRecordsIds + it
                                }
                            }
                        },
                        familyCounts = familyWinCounts,
                        visibleWinCount = visibleMostRecordsWinCount,
                        visibleRecordCount = visibleMostRecordsRecordCount
                    )
                }
                if (visibleMostRecordsFamilies.isEmpty()) item(key = "most-records-empty") {
                    Text(
                        if (mostRecordsHeld.isEmpty()) "Aucun classement #1 détaillé n'est disponible."
                        else "Aucun trophée ne correspond à cette recherche ou à ce filtre.",
                        color = theme.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(26.dp)
                    )
                }
                visibleMostRecordsFamilies.forEach { family ->
                    val familyExpanded = mostRecordsSearch.isNotBlank() || family.group in expandedMostRecordsFamilies
                    item(key = "most-records-family-${family.group.name}") {
                        MostRecordsFamilyCard(family, mostRecordsHeld.size, familyExpanded) {
                            val opening = family.group !in expandedMostRecordsFamilies
                            expandedMostRecordsFamilies = if (opening) expandedMostRecordsFamilies + family.group else expandedMostRecordsFamilies - family.group
                            if (opening) family.records.firstOrNull()?.def?.id?.let { expandedMostRecordsIds = expandedMostRecordsIds + it }
                        }
                    }
                    if (familyExpanded) family.records.forEach { bundle ->
                        val bundleExpanded = mostRecordsSearch.isNotBlank() || bundle.def.id in expandedMostRecordsIds
                        item(key = "most-records-bundle-${family.group.name}-${bundle.def.id}") {
                            MostRecordsBundleCard(bundle, bundleExpanded) {
                                expandedMostRecordsIds = if (bundle.def.id in expandedMostRecordsIds) expandedMostRecordsIds - bundle.def.id else expandedMostRecordsIds + bundle.def.id
                            }
                        }
                        if (bundleExpanded) bundle.held.forEachIndexed { index, heldEntry ->
                            val periodKey = heldEntry.period?.dbName ?: "NONE"
                            val subKey = heldEntry.sub ?: "ALL"
                            item(key = "most-records-entry-${bundle.def.id}-${periodKey}-${subKey}-$index") {
                                MostRecordsVictoryRow(heldEntry) {
                                    onEntry(RecordEntryRef(heldEntry.def, heldEntry.period, heldEntry.category, heldEntry.sub,
                                        RecordRow(heldEntry.row, name, row.subtitle, row.imageUrl), rank = 1))
                                }
                            }
                        }
                    }
                }
            } else item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SectionTitle(if (def.id == "LONGEST_LIFESPAN") "📖 Le récit de sa carrière" else "🧐 Pourquoi il est là", color)
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
            st.multiChartVisual?.let { visual ->
                item { MultiChartConstellation(visual, color) }
            }
            if (def.id != "MOST_RECORDS" && st.facts.isNotEmpty()) item {
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
            st.seats?.let { seats ->
                item {
                    // 🧹 Podium Sweep : le balayage se rejoue — les sièges se retournent un par un, puis le podium apparaît
                    val sweep = def.id == "PODIUM_SWEEP"
                    var shown by remember(st) { mutableStateOf(if (sweep) 0 else seats.occupied.size) }
                    LaunchedEffect(st) {
                        if (sweep) for (i in 1..seats.occupied.size) { shown = i; delay(240) }
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SectionTitle(if (sweep) "🧹 Le balayage" else "🪑 ${seats.occupied.size} siège(s) sur ${seats.total} dans la zone", color)
                        SeatsRow(RecordExplainer.Seats(seats.total, if (sweep) (1..shown).toList() else seats.occupied), color)
                        if (sweep && shown >= seats.occupied.size && st.items.size >= 2) {
                            Spacer(Modifier.height(10.dp))
                            PodiumRow(st.items.take(3), color)
                        }
                    }
                }
            }
            if (st.timeline.isNotEmpty() && st.presenceVisual == null && def.id !in setOf("MOST_CONSISTENT", "MOST_BLOCKED_TOP5", "LONGEST_LIFESPAN")) item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SectionTitle((if (st.heat) "🔥 " else "📈 ") + (st.timelineTitle ?: "Frise"), color)
                    if (st.heat) HeatGrid(st.timeline, color) else Timeline(st.timeline, color, st.timelineValueSuffix ?: "▶")
                }
            }
            if (def.id != "MOST_RECORDS" && st.held.isNotEmpty()) {
                // 🏆 Affichage générique conservé pour les records qui exposent une liste détenue
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
            if (st.items.isNotEmpty() && def.id != "MOST_BLOCKED_TOP5") {
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

/** 🏛️ Fiche d'honneur exclusive aux deux classements HoF/Global. */
@Composable
private fun HonorRecordSpotlight(
    name: String,
    subtitle: String?,
    imageUrl: String?,
    isArtist: Boolean,
    recordTitle: String,
    contextLabel: String,
    visual: RecordExplainer.HonorVisual,
    sharing: Boolean,
    shareError: Boolean,
    onShare: () -> Unit
) {
    val theme = Nova.theme
    val global = visual.isGlobal
    val accent = if (global) Color(0xFF63DDF5) else NovaColors.Gold
    val deepAccent = if (global) Color(0xFF6558E8) else Color(0xFFB97525)
    val transition = rememberInfiniteTransition(label = if (global) "global-constellation" else "hof-hall")
    val glow by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.56f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "honor-glow"
    )
    val scoreCounter = remember(visual.score, visual.maxScore, visual.isGlobal) { Animatable(0f) }
    LaunchedEffect(visual.score, visual.maxScore, visual.isGlobal) {
        scoreCounter.snapTo(0f)
        scoreCounter.animateTo(visual.score.toFloat(), tween(1150))
    }
    val rim = Brush.horizontalGradient(
        listOf(accent.copy(alpha = 0.45f + glow * 0.45f), deepAccent.copy(alpha = 0.34f + glow * 0.30f), accent.copy(alpha = 0.34f + glow * 0.44f))
    )
    val panel = Brush.linearGradient(
        listOf(deepAccent.copy(alpha = if (global) 0.24f else 0.18f), theme.surface.copy(alpha = 0.98f), accent.copy(alpha = 0.12f))
    )
    val medal = if (global) "🌌" else "🏛️"
    val winnerLabel = when {
        visual.rank == 1 && visual.tiedAtTop > 1 -> "✦ SOMMET PARTAGÉ · ${visual.tiedAtTop} EX ÆQUO ✦"
        visual.rank == 1 && global -> "✦ AU SOMMET DE LA CONSTELLATION ✦"
        visual.rank == 1 -> "✦ AU PANTHÉON DES INTRONISÉS ✦"
        global -> "✧ CONSTELLATION · RANG #${visual.rank} ✧"
        else -> "✧ GALERIE D'HONNEUR · RANG #${visual.rank} ✧"
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(if (global) "🌌 Constellation Global" else "🏛️ Galerie d'honneur", accent)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(panel)
                .border(1.5.dp, rim, RoundedCornerShape(24.dp)).padding(15.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.18f), deepAccent.copy(alpha = 0.20f))))
                    .border(1.dp, accent.copy(alpha = 0.55f), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(winnerLabel, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    Modifier.size(64.dp).shadow(12.dp, if (isArtist) CircleShape else RoundedCornerShape(15.dp), ambientColor = accent, spotColor = accent)
                        .border(1.5.dp, accent.copy(alpha = 0.82f), if (isArtist) CircleShape else RoundedCornerShape(15.dp))
                ) { CoverArt(imageUrl, name, size = 64, circle = isArtist) }
                Column(Modifier.weight(1f)) {
                    Text(contextLabel.ifBlank { if (global) "RECORD GLOBAL" else "RECORD HALL OF FAME" }.uppercase(Locale.FRANCE), color = accent.copy(alpha = 0.90f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(name, color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    subtitle?.takeIf { it.isNotBlank() }?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("RANG", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("#${visual.rank}", color = accent, fontWeight = FontWeight.Black, fontSize = 26.sp)
                    Text("sur ${visual.boardSize.coerceAtLeast(1)}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(61.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.30f), deepAccent.copy(alpha = 0.16f), Color.Transparent)))
                        .border(1.2.dp, rim, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(if (visual.rank == 1) "👑" else medal, fontSize = 30.sp) }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(scoreCounter.value.toInt().toString(), color = accent, fontSize = 50.sp, lineHeight = 52.sp, fontWeight = FontWeight.Black)
                        Text("/ ${visual.maxScore}", color = theme.textSecondary, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    Text(if (global) "distinctions Global" else "intronisations HoF", color = theme.text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(if (global) "Triple Debut · All Kill · Legendary Run" else "Débuts directs · longs règnes · hebdo · mensuel", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HonorStatPill("${visual.entries.size}", if (global) "sceaux obtenus" else "intronisations datées", accent, Modifier.weight(1f))
                HonorStatPill("${visual.boardSize.coerceAtLeast(1)}", "entités distinguées", deepAccent, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sharing) Brush.horizontalGradient(listOf(accent.copy(alpha = 0.12f), theme.surface.copy(alpha = 0.76f))) else Brush.horizontalGradient(listOf(accent.copy(alpha = 0.90f), if (global) Color(0xFFB6F6FF) else Color(0xFFFFE7A0))))
                    .border(1.dp, rim, RoundedCornerShape(14.dp)).clickable(enabled = !sharing, onClick = onShare),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (sharing) "⏳ Préparation de la carte…" else if (global) "✦ PARTAGER MA CONSTELLATION ✦" else "✦ PARTAGER MON INTRONISATION ✦",
                    color = if (sharing) accent else Color(0xFF171321), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge
                )
            }
            if (shareError) Text("La carte n'a pas pu être préparée. Réessaie dans un instant.", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
        }

        visual.rival?.let { rival ->
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.13f), theme.surface.copy(alpha = 0.97f), deepAccent.copy(alpha = 0.14f))))
                    .border(1.1.dp, accent.copy(alpha = 0.36f), RoundedCornerShape(19.dp)).padding(14.dp)
            ) {
                Text("⚔ DUEL DES DISTINCTIONS", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                Text("Comparaison du total et de chaque distinction avec le détenteur voisin", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CoverArt(imageUrl, name, size = 46, circle = isArtist)
                    Column(Modifier.weight(1f)) {
                        Text("#${visual.rank} · $name", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${visual.score} au total", color = accent, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                    Text("VS", color = NovaColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                        Text("#${rival.rank} · ${rival.name}", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                        Text("${rival.score} au total", color = theme.textSecondary, fontSize = 18.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                    }
                    CoverArt(rival.imageUrl, rival.name, size = 46, circle = isArtist)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        rival.tied -> "🤝 ÉGALITÉ · le total de distinctions est partagé"
                        visual.score > rival.score -> "👑 ${visual.score - rival.score} distinction${if (visual.score - rival.score == 1) "" else "s"} d'avance sur ce rival"
                        else -> "🎯 ${rival.score - visual.score} distinction${if (rival.score - visual.score == 1) "" else "s"} à combler"
                    },
                    color = if (rival.tied) NovaColors.Gold else theme.text, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visual.seals.forEach { seal ->
                        val currentEarned = visual.entries.any { it.sealKey == seal.key }
                        val rivalEarned = seal.key in rival.earnedKeys
                        Column(
                            Modifier.width(98.dp).clip(RoundedCornerShape(12.dp)).background(theme.background.copy(alpha = 0.50f))
                                .border(1.dp, if (currentEarned || rivalEarned) accent.copy(alpha = 0.42f) else theme.textSecondary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(seal.title, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text("${if (currentEarned) "✦" else "—"}  /  ${if (rivalEarned) "✦" else "—"}", color = accent, fontSize = 17.sp, fontWeight = FontWeight.Black)
                            Text("toi / rival", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        if (global) {
            SectionTitle("✧ Les trois sceaux de la constellation", accent)
            Text("Lumière = distinction obtenue · silhouette = distinction encore à conquérir. Les nombres indiquent combien d'entités l'ont réellement obtenue; aucun pourcentage n'est extrapolé.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 16.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                visual.seals.forEach { seal -> GlobalSealCard(seal, accent, deepAccent) }
            }
        } else {
            SectionTitle("🕰️ Frise des intronisations", accent)
            Text("Chaque étape conserve son type, sa cadence, sa date et, lorsqu'il est enregistré, le détail du règne.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 16.sp)
            Spacer(Modifier.height(7.dp))
            visual.entries.forEachIndexed { index, entry ->
                val seal = visual.seals.firstOrNull { it.key == entry.sealKey }
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 12.dp).size(13.dp).shadow(6.dp, CircleShape, ambientColor = accent, spotColor = accent).clip(CircleShape).background(accent))
                        if (index < visual.entries.lastIndex) Box(Modifier.width(2.dp).height(99.dp).background(accent.copy(alpha = 0.32f)))
                    }
                    Column(
                        Modifier.weight(1f).padding(bottom = 8.dp).clip(RoundedCornerShape(15.dp))
                            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.13f), theme.surface.copy(alpha = 0.92f))))
                            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(15.dp)).padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${seal?.emoji ?: "🏅"} ${entry.title}", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(entry.periodLabel.uppercase(Locale.FRANCE), color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(entry.date, color = theme.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        val reignOrDetail = entry.reignDuration?.let { duration ->
                            "Règne continu : $duration${entry.reign?.let { " · $it" }.orEmpty()}"
                        } ?: when {
                            entry.weeksAtOne > 0 -> "${entry.weeksAtOne} semaines au #1 au total"
                            else -> seal?.detail
                        }
                        reignOrDetail?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        val subDetails = listOfNotNull(
                            entry.weeksAtOne.takeIf { it > 0 && entry.reignDuration != null }?.let { "$it sem. au #1 au total" },
                            entry.playsAtEntry.takeIf { it > 0 }?.let { "$it écoutes à l'intronisation" },
                            seal?.takeIf { it.distinctAwardees > 0 }?.let { "${it.distinctAwardees} entités ont obtenu ce sceau" }
                        ).joinToString(" · ")
                        if (subDetails.isNotBlank()) Text(subDetails, color = theme.textSecondary.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun HonorStatPill(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
        Text(label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GlobalSealCard(seal: RecordExplainer.HonorSealVisual, accent: Color, deepAccent: Color) {
    val theme = Nova.theme
    val earned = seal.earnedDate != null
    val border = if (earned) accent.copy(alpha = 0.58f) else theme.textSecondary.copy(alpha = 0.20f)
    Column(
        Modifier.width(228.dp).height(221.dp).clip(RoundedCornerShape(19.dp))
            .background(if (earned) Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), theme.surface.copy(alpha = 0.96f), deepAccent.copy(alpha = 0.15f))) else Brush.linearGradient(listOf(theme.surface.copy(alpha = 0.62f), theme.surface.copy(alpha = 0.62f))))
            .border(if (earned) 1.5.dp else 1.dp, border, RoundedCornerShape(19.dp)).padding(13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(53.dp).clip(CircleShape)
                .background(if (earned) Brush.radialGradient(listOf(accent.copy(alpha = 0.48f), deepAccent.copy(alpha = 0.20f), Color.Transparent)) else Brush.radialGradient(listOf(theme.background.copy(alpha = 0.68f), theme.background.copy(alpha = 0.68f))))
                .border(1.dp, border, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(if (earned) seal.emoji else "◇", color = if (earned) Color.Unspecified else theme.textSecondary.copy(alpha = 0.42f), fontSize = if (earned) 25.sp else 30.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(7.dp))
        Text(seal.title, color = if (earned) accent else theme.textSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (earned) "✦ OBTENU · ${seal.earnedDate}" else "◇ À CONQUÉRIR", color = if (earned) accent else theme.textSecondary.copy(alpha = 0.65f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(5.dp))
        Text(seal.description, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text("${seal.distinctAwardees} entités distinguées", color = if (earned) theme.text else theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(text, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** 🏁 Carte chrono + duel, réservés à Fastest Certification et Fastest Panthéon. */
@Composable
private fun SpeedSpotlight(name: String, imageUrl: String?, isArtist: Boolean, visual: RecordExplainer.SpeedVisual, color: Color) {
    val theme = Nova.theme
    val transition = rememberInfiniteTransition(label = "speed-ceremony")
    val glow by transition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(tween(950), RepeatMode.Reverse),
        label = "speed-glow"
    )
    val panel = if (visual.recordHolder) {
        Brush.horizontalGradient(listOf(NovaColors.Gold.copy(alpha = glow), color.copy(alpha = 0.12f), theme.surface))
    } else {
        Brush.horizontalGradient(listOf(color.copy(alpha = 0.16f), theme.surface, theme.surface))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(if (visual.recordHolder) "🏆 Cérémonie du record de vitesse" else "⚡ Course contre le chrono", color)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel)
                .border(1.dp, (if (visual.recordHolder) NovaColors.Gold else color).copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(imageUrl, name, size = 54, circle = isArtist)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("CHRONO OFFICIEL", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(name, color = theme.text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("CLASSEMENT", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (visual.recordHolder && visual.tiedAtTop > 1) "🥇 #1 · ex æquo" else if (visual.recordHolder) "🥇 #1" else "#${visual.rank} / ${visual.boardSize}",
                        color = if (visual.recordHolder) NovaColors.Gold else theme.text,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Text(
                RecordCatalog.formatFastDuration(visual.elapsedMs),
                color = if (visual.recordHolder) NovaColors.Gold else color,
                fontWeight = FontWeight.Black,
                fontSize = 25.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(7.dp))
            Text("Départ · ${visual.startDate}", color = theme.text, style = MaterialTheme.typography.bodySmall)
            Text("Palier atteint · ${visual.finishDate}", color = theme.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            visual.rival?.let { rival ->
                Spacer(Modifier.height(11.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.25f)))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(rival.imageUrl, rival.name, size = 42, circle = rival.type == EntityType.ARTIST)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                rival.tied -> "🤝 Co-détenteur du chrono"
                                visual.recordHolder -> "🥈 Dauphin le plus proche"
                                else -> "🎯 Chrono juste devant"
                            },
                            color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium
                        )
                        Text(rival.name, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (rival.tied) "ÉGALITÉ" else RecordCatalog.formatFastDuration(rival.gapMs), color = if (rival.tied) NovaColors.Gold else theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        if (!rival.tied) Text(
                            "${String.format(Locale.FRANCE, "%.1f", rival.percentGap)} % ${if (visual.recordHolder) "plus lent" else "de plus"}",
                            color = theme.textSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.End
                        )
                    }
                }
                Text(
                    when {
                        rival.tied -> "Même durée, même sommet : le record est partagé."
                        visual.recordHolder -> "${rival.name} suit derrière, à ${RecordCatalog.formatFastDuration(rival.gapMs)}."
                        else -> "${rival.name} est juste devant avec un chrono plus rapide de ${RecordCatalog.formatFastDuration(rival.gapMs)}."
                    },
                    color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp)
                )
            } ?: Text("Seul chrono actuellement enregistré dans cette catégorie.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            if (visual.recordHolder) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (visual.tiedAtTop > 1) "✨ ${visual.tiedAtTop} détenteurs partagent ce record." else "✨ Le meilleur temps de toute la bibliothèque — palier révélé !",
                    color = NovaColors.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NovaColors.Gold.copy(alpha = glow * 0.45f)).padding(horizontal = 9.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/** 👑 Carte premium de Most Successive #1 : score distinct, durée séparée, contexte, statut et dauphin. */
@Composable
private fun SuccessiveSpotlight(
    name: String,
    imageUrl: String?,
    isArtist: Boolean,
    visual: RecordExplainer.SuccessiveVisual,
    color: Color
) {
    val theme = Nova.theme
    val transition = rememberInfiniteTransition(label = "successive-ceremony")
    val glow by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "successive-glow"
    )
    val scoreCounter = remember(visual.distinctCount, visual.recordHolder) {
        Animatable(if (visual.recordHolder) 0f else visual.distinctCount.toFloat())
    }
    LaunchedEffect(visual.distinctCount, visual.recordHolder) {
        if (visual.recordHolder) scoreCounter.animateTo(visual.distinctCount.toFloat(), tween(1100))
    }
    val panel = if (visual.recordHolder) {
        Brush.horizontalGradient(listOf(NovaColors.Gold.copy(alpha = glow), color.copy(alpha = 0.12f), theme.surface))
    } else {
        Brush.horizontalGradient(listOf(color.copy(alpha = 0.16f), theme.surface, theme.surface))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(if (visual.recordHolder) "👑 Cérémonie du relais au #1" else "🔁 Carte du relais au #1", color)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel)
                .border(1.dp, (if (visual.recordHolder) NovaColors.Gold else color).copy(alpha = 0.52f), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Text(
                "CONTEXTE · ${visual.contextLabel}",
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 5.dp)
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(imageUrl, name, size = 54, circle = isArtist)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("DÉTENTEUR DU RELAIS", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(name, color = theme.text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("CLASSEMENT", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            visual.recordHolder && visual.tiedAtTop > 1 -> "🥇 #1 · ex æquo"
                            visual.recordHolder -> "🥇 #1"
                            else -> "#${visual.rank} / ${visual.boardSize}"
                        },
                        color = if (visual.recordHolder) NovaColors.Gold else theme.text,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(theme.surface.copy(alpha = 0.72f)).padding(10.dp)
                ) {
                    Text("SCORE · DISTINCTS", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${scoreCounter.value.toInt()} ${visual.itemLabel}",
                        color = if (visual.recordHolder) NovaColors.Gold else color,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text("éléments différents au #1", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(theme.surface.copy(alpha = 0.72f)).padding(10.dp)
                ) {
                    Text("DURÉE · SÉRIE", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(visual.durationLabel, color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    Text("sans interruption", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Début · ${visual.startDate}", color = theme.text, style = MaterialTheme.typography.bodySmall)
            Text("Fin · ${visual.endDate}", color = theme.text, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                (if (visual.continuing) "🟢 EN COURS · " else "🏁 TERMINÉE · ") + visual.statusLine,
                color = if (visual.continuing) Color(0xFF69C779) else theme.textSecondary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )
            visual.rival?.let { rival ->
                Spacer(Modifier.height(11.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.25f)))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(rival.imageUrl, rival.name, size = 42, circle = isArtist)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                rival.tied -> "🤝 Égalité au classement"
                                visual.recordHolder -> "🥈 Dauphin le plus proche"
                                rival.ahead -> "🎯 Rang juste devant"
                                else -> "🎯 Concurrent suivant"
                            },
                            color = color,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(rival.name, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (rival.tied) "ÉGALITÉ" else "${rival.gap} ${visual.itemLabel.removeSuffix("s")}${if (rival.gap == 1) "" else "s"}",
                            color = if (rival.tied) NovaColors.Gold else theme.text,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(if (rival.tied) "même score" else if (rival.ahead) "à rattraper" else "d'avance", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!rival.tied) {
                    Text(
                        if (rival.ahead) "${rival.name} a ${rival.score}; il manque exactement ${rival.gap} ${visual.itemLabel.removeSuffix("s")}${if (rival.gap == 1) "" else "s"} pour l'égaler."
                        else "${name} devance ${rival.name} de ${rival.gap} ${visual.itemLabel.removeSuffix("s")}${if (rival.gap == 1) "" else "s"} distincts.",
                        color = theme.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            } ?: Text("Aucun autre détenteur admissible dans cette combinaison.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            visual.stopper?.let { stopper ->
                Spacer(Modifier.height(11.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(theme.textSecondary.copy(alpha = 0.2f)))
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (stopper.name != null) CoverArt(stopper.imageUrl, stopper.name, size = 42)
                    else Text("🕳️", fontSize = 32.sp, modifier = Modifier.size(42.dp), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                stopper.direct -> "⛓ ÉLÉMENT QUI ROMPT LA SÉRIE"
                                stopper.name != null -> "📎 PROCHAIN #1 DOCUMENTÉ"
                                else -> "🕳 CAUSE NON IDENTIFIABLE"
                            },
                            color = color,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(stopper.name ?: "Aucun #1 exploitable", color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        stopper.ownerName?.let { Text("Détenteur : $it", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        stopper.date?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Text(stopper.explanation, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (visual.recordHolder) {
                Spacer(Modifier.height(10.dp))
                Text(
                    if (visual.tiedAtTop > 1) "✨ ${visual.tiedAtTop} détenteurs partagent la tête du classement."
                    else "✨ Meilleure succession admissible de la combinaison — le relais est célébré !",
                    color = NovaColors.Gold,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(NovaColors.Gold.copy(alpha = glow * 0.42f)).padding(horizontal = 9.dp, vertical = 7.dp)
                )
            }
        }
    }
}

/** 🎞️ Frise de pochettes : regroupe les #1 consécutifs du même titre et marque la sortie du relais. */
@Composable
private fun SuccessiveRelayTimeline(visual: RecordExplainer.SuccessiveVisual, color: Color) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle("🎞️ Frise illustrée · ${visual.segments.size} passage(s)", color)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            visual.segments.forEachIndexed { index, segment ->
                if (index > 0) Text("→", color = color, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Column(
                    Modifier.width(160.dp).clip(RoundedCornerShape(14.dp)).background(theme.surface)
                        .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(14.dp)).padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("RELAIS ${index + 1} · #1", color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(7.dp))
                    CoverArt(segment.imageUrl, segment.name, size = 68)
                    Spacer(Modifier.height(7.dp))
                    Text(segment.name, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (segment.periodCount > 1) "reste #1 · ${segment.durationLabel}" else "prend le #1 · ${segment.durationLabel}",
                        color = color,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (segment.firstDate == segment.lastDate) segment.firstDate else "${segment.firstDate} → ${segment.lastDate}",
                        color = theme.textSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
            val stopper = visual.stopper
            if (stopper != null) {
                Text(if (stopper.direct) "⇢" else "⋯", color = theme.textSecondary, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Column(
                    Modifier.width(188.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (stopper.direct) color.copy(alpha = 0.12f) else theme.surface)
                        .border(1.dp, if (stopper.direct) color.copy(alpha = 0.65f) else theme.textSecondary.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (stopper.direct) "RUPTURE" else if (stopper.name != null) "PROCHAIN #1" else "ARCHIVES", color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(7.dp))
                    if (stopper.name != null) CoverArt(stopper.imageUrl, stopper.name, size = 58)
                    else Text("🕳️", fontSize = 36.sp, modifier = Modifier.height(58.dp))
                    Spacer(Modifier.height(7.dp))
                    Text(stopper.name ?: "#1 inconnu", color = theme.text, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    stopper.date?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }
                }
            }
            if (stopper == null && visual.continuing) {
                Text("→", color = NovaColors.Gold, fontWeight = FontWeight.Black, fontSize = 20.sp)
                Column(
                    Modifier.width(148.dp).clip(RoundedCornerShape(14.dp)).background(NovaColors.Gold.copy(alpha = 0.12f))
                        .border(1.dp, NovaColors.Gold.copy(alpha = 0.65f), RoundedCornerShape(14.dp)).padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("👑 EN COURS", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(9.dp))
                    Text("#1 conservé", color = theme.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(visual.endShort, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("Les cartes compressent les périodes consécutives d'un même titre ou album; le score reste le nombre d'éléments distincts.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
}

/** 🎼 Échelle des éléments distincts, face-à-face du Top 2 et parcours des titres signatures. */
@Composable
private fun SongReachSpotlight(
    name: String,
    imageUrl: String?,
    isArtist: Boolean,
    visual: RecordExplainer.SongReachVisual,
    color: Color,
    sharing: Boolean,
    shareError: Boolean,
    onShare: () -> Unit
) {
    val theme = Nova.theme
    val accent = if (visual.rank == 1) NovaColors.Gold else color
    val ceremony = rememberInfiniteTransition(label = "song-reach-ceremony")
    val glow by ceremony.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(1450), RepeatMode.Reverse),
        label = "song-reach-glow"
    )
    val heroBorder = Brush.horizontalGradient(
        listOf(accent.copy(alpha = 0.42f + glow * 0.45f), color.copy(alpha = 0.32f + glow * 0.42f), NovaColors.Gold.copy(alpha = 0.30f + glow * 0.5f))
    )
    val heroPanel = Brush.linearGradient(
        listOf(accent.copy(alpha = if (visual.rank == 1) 0.23f else 0.15f), theme.surface.copy(alpha = 0.98f), color.copy(alpha = 0.13f))
    )
    val scoreCounter = remember(visual.score, visual.recordTitle) { Animatable(0f) }
    LaunchedEffect(visual.score, visual.recordTitle) {
        scoreCounter.snapTo(0f)
        scoreCounter.animateTo(visual.score.toFloat(), tween(1250))
    }
    val chartBase = visual.chartCount.coerceAtLeast(1)
    val levels = listOf(
        Triple("Chart", visual.chartCount, "Base"),
        Triple("Top 10", visual.top10Count, visual.top10ConversionPercent?.let { "$it% du chart" } ?: "—"),
        Triple("Top 5", visual.top5Count, visual.top5ConversionPercent?.let { "$it% du Top 10" } ?: "—"),
        Triple("#1", visual.numberOneCount, visual.numberOneConversionPercent?.let { "$it% du Top 5" } ?: "—")
    )
    val levelColors = listOf(color, Color(0xFF5DADE2), Color(0xFFBA68C8), NovaColors.Gold)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle("🏆 Cérémonie des titres d'exception", accent)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(heroPanel)
                .border(1.5.dp, heroBorder, RoundedCornerShape(22.dp))
                .padding(14.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(NovaColors.Gold.copy(alpha = 0.22f), accent.copy(alpha = 0.18f))))
                    .border(1.dp, NovaColors.Gold.copy(alpha = 0.42f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    if (visual.rank == 1 && visual.tiedAtTop > 1) "✦ COURONNE PARTAGÉE · ${visual.tiedAtTop} CO-DÉTENTEURS ✦"
                    else if (visual.rank == 1) "✦ CÉRÉMONIE DE COURONNEMENT ✦"
                    else "✧ CANDIDAT AU SOMMET · #${visual.rank} ✧",
                    color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(imageUrl, name, size = 52, circle = isArtist)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(visual.contextLabel.uppercase(Locale.FRANCE), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(name, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    if (visual.rank == 1 && visual.tiedAtTop > 1) "🥇 #1 · ${visual.tiedAtTop} ex æquo" else if (visual.rank == 1) "🥇 DÉTENTEUR #1" else "#${visual.rank}",
                    color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(54.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(NovaColors.Gold.copy(alpha = 0.30f), accent.copy(alpha = 0.10f))))
                        .border(1.2.dp, heroBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text(if (visual.rank == 1) "👑" else "🏅", fontSize = 27.sp) }
                Text(scoreCounter.value.toInt().toString(), color = accent, fontWeight = FontWeight.Black, fontSize = 48.sp, lineHeight = 50.sp)
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    Text(visual.recordTitle, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("${visual.score} ${if (visual.score == 1) visual.itemLabel else "${visual.itemLabel}s"} ${if (visual.score == 1) "unique" else "uniques"} · score sans doublons de périodes", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("CHART  →  TOP 10  →  TOP 5  →  #1", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                levels.forEachIndexed { index, (label, count, ratio) ->
                    SongReachMetricCard(
                        label = label,
                        count = count,
                        ratio = ratio,
                        fraction = (count.toFloat() / chartBase).coerceIn(0f, 1f),
                        accent = levelColors[index],
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Chaque ${visual.itemLabel} compte une fois au maximum; les taux décrivent la conversion entre les paliers, pas la durée passée dans le chart.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, lineHeight = 15.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(13.dp))
                    .background(
                        when {
                            sharing -> Brush.horizontalGradient(listOf(accent.copy(alpha = 0.14f), theme.surface.copy(alpha = 0.72f)))
                            visual.rank == 1 -> Brush.horizontalGradient(listOf(NovaColors.Gold, Color(0xFFFFEFA8)))
                            else -> Brush.horizontalGradient(listOf(color.copy(alpha = 0.30f), color.copy(alpha = 0.46f)))
                        }
                    )
                    .border(1.dp, heroBorder, RoundedCornerShape(13.dp))
                    .clickable(enabled = !sharing, onClick = onShare),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (sharing) "⏳ Préparation de la carte…" else "✦ CRÉER MA CARTE DE LÉGENDE ✦",
                    color = if (sharing) accent else if (visual.rank == 1) Color(0xFF241B00) else theme.text,
                    fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge
                )
            }
            if (shareError) Text("La carte n'a pas pu être préparée. Réessaie dans un instant.", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }

        visual.rival?.let { rival ->
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(NovaColors.Gold.copy(alpha = 0.19f), theme.surface.copy(alpha = 0.96f), color.copy(alpha = 0.12f))))
                    .border(1.2.dp, heroBorder, RoundedCornerShape(18.dp)).padding(14.dp)
            ) {
                Text("⚔ LE DUEL POUR LA COURONNE", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                Text("Le score, les paliers et l'écart face au rival le plus proche", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CoverArt(imageUrl, name, size = 48, circle = isArtist)
                    Column(Modifier.weight(1f)) {
                        Text("#${visual.rank} · $name", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${visual.score}", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    Text("VS", color = NovaColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                        Text("#${rival.rank} · ${rival.name}", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                        Text("${rival.score}", color = theme.textSecondary, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
                    }
                    CoverArt(rival.imageUrl, rival.name, size = 48, circle = isArtist)
                }
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Triple("Chart", visual.chartCount, rival.chartCount),
                        Triple("Top 10", visual.top10Count, rival.top10Count),
                        Triple("Top 5", visual.top5Count, rival.top5Count),
                        Triple("#1", visual.numberOneCount, rival.numberOneCount)
                    ).forEachIndexed { index, (label, current, other) ->
                        SongReachDuelMetric(label, current, other, levelColors[index], Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val gap = kotlin.math.abs(visual.score - rival.score)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                        .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.18f), theme.surface.copy(alpha = 0.72f), NovaColors.Gold.copy(alpha = 0.12f))))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            rival.tied -> "🤝 À ÉGALITÉ · le sommet est partagé"
                            visual.rank < rival.rank -> "👑 $gap ${visual.itemLabel}${if (gap == 1) "" else "s"} d'avance sur le suivant"
                            else -> "🎯 $gap ${visual.itemLabel}${if (gap == 1) "" else "s"} à rattraper pour rejoindre le détenteur"
                        },
                        color = if (rival.tied) NovaColors.Gold else theme.text,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (visual.featuredTracks.isNotEmpty()) {
            SectionTitle("✨ Les pièces maîtresses · trajectoire vers le sommet", color)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                visual.featuredTracks.forEachIndexed { index, track ->
                    Column(
                        Modifier.width(308.dp).clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.16f), theme.surface.copy(alpha = 0.96f), NovaColors.Gold.copy(alpha = 0.08f))))
                            .border(1.dp, Brush.horizontalGradient(listOf(color.copy(alpha = 0.50f), NovaColors.Gold.copy(alpha = 0.34f))), RoundedCornerShape(18.dp))
                            .padding(11.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (index == 0) "✦ PIÈCE MAÎTRESSE" else "✧ ${visual.itemLabel.uppercase(Locale.FRANCE)} SIGNATURE", color = if (index == 0) NovaColors.Gold else color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text("0${index + 1}", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverArt(track.imageUrl, track.name, size = 50)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.name, color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Meilleur rang #${track.peakPosition} · ${track.peakDate}", color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                                Text("${track.chartPeriods} appar. · ${track.top5Periods} Top 5 · ${track.numberOnePeriods} #1", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SongReachMilestone("Chart", track.firstChartDate, color, Modifier.weight(1f))
                            SongReachMilestone("Top 10", track.firstTop10Date, Color(0xFF5DADE2), Modifier.weight(1f))
                            SongReachMilestone("Top 5", track.firstTop5Date, Color(0xFFBA68C8), Modifier.weight(1f))
                            SongReachMilestone("#1", track.firstNumberOneDate, NovaColors.Gold, Modifier.weight(1f))
                        }
                    }
                }
            }
            Text("Première date de franchissement de chaque palier; les cases « — » indiquent un palier jamais atteint.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** 💎 Cérémonie Most Perfect All Kill : classement par cadence, rival, éphéméride et trinité partageable. */
@Composable
private fun PerfectAllKillRecordSpotlight(
    name: String,
    imageUrl: String?,
    visual: RecordExplainer.PerfectAllKillRecordVisual,
    selectedDate: String?,
    selectedEvent: RecordExplainer.PerfectAllKillVisual?,
    eventLoading: Boolean,
    sharing: Boolean,
    shareError: Boolean,
    sharingEvent: Boolean,
    eventShareError: Boolean,
    color: Color,
    onSelectDate: (String) -> Unit,
    onShareRecord: () -> Unit,
    onShareEvent: () -> Unit
) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.24f), theme.surface.copy(alpha = 0.98f), NovaColors.Gold.copy(alpha = 0.10f))))
            .border(1.5.dp, Brush.horizontalGradient(listOf(color.copy(alpha = 0.72f), NovaColors.Gold.copy(alpha = 0.48f), color.copy(alpha = 0.50f))), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoverArt(imageUrl, name, size = 56, circle = true)
            Column(Modifier.weight(1f)) {
                Text("MOST PERFECT ALL KILL", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, letterSpacing = 0.7.sp)
                Text("${visual.period.label} · trois charts à la fois", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("${visual.dates.size} événement${if (visual.dates.size == 1) "" else "s"} conservé${if (visual.dates.size == 1) "" else "s"}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("#${visual.rank}", color = if (visual.rank == 1) NovaColors.Gold else color, fontWeight = FontWeight.Black, fontSize = 26.sp)
                if (visual.tiedAtTop > 1) Text("${visual.tiedAtTop} ex æquo", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).padding(9.dp)) {
                Text("TOTAL · ${visual.period.label.uppercase(Locale.FRANCE)}", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("${visual.score} PAK", color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(NovaColors.Gold.copy(alpha = 0.10f)).padding(9.dp)) {
                Text("ÉCART AU RIVAL", color = NovaColors.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                val rival = visual.rival
                Text(
                    rival?.let { "${kotlin.math.abs(visual.score - it.score)} événement${if (kotlin.math.abs(visual.score - it.score) == 1) "" else "s"}" } ?: "—",
                    color = theme.text,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        visual.rival?.let { rival ->
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(theme.background.copy(alpha = 0.46f))
                    .border(1.dp, NovaColors.Gold.copy(alpha = 0.24f), RoundedCornerShape(13.dp)).padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoverArt(imageUrl, name, size = 40, circle = true)
                Column(Modifier.weight(1f)) {
                    Text("#${visual.rank} · $name", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${visual.score} PAK ${visual.period.label.lowercase()}", color = color, style = MaterialTheme.typography.labelSmall)
                }
                Text(if (rival.tied) "🤝" else "VS", color = NovaColors.Gold, fontWeight = FontWeight.Black)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("#${rival.rank} · ${rival.name}", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    Text("${rival.score} PAK ${visual.period.label.lowercase()}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                }
                CoverArt(rival.imageUrl, rival.name, size = 40, circle = true)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("ÉPHÉMÉRIDE · CHAQUE DATE PAK", color)
        Text("Choisis une date pour revoir les trois éléments n°1 et leur dauphin dans chaque chart.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        if (visual.dates.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(visual.dates) { date ->
                    val selected = date == selectedDate
                    val parts = date.split('-')
                    val dateLabel = if (parts.size == 3) when (visual.period) {
                        Period.DAILY, Period.WEEKLY -> "${parts[2]}/${parts[1]}"
                        Period.MONTHLY -> "${parts[1]}/${parts[0]}"
                        Period.YEARLY -> parts[0]
                        Period.GLOBAL -> date
                    } else date
                    Text(
                        dateLabel,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (selected) color.copy(alpha = 0.28f) else theme.background.copy(alpha = 0.55f))
                            .border(1.dp, if (selected) color else theme.textSecondary.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                            .clickable { onSelectDate(date) }.padding(horizontal = 9.dp, vertical = 7.dp),
                        color = if (selected) color else theme.textSecondary,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            visual.heatmap.takeLast(56).chunked(7).forEach { week ->
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { cell ->
                        val selected = cell.dateIso == selectedDate
                        val fill = if (cell.isPak) {
                            Brush.verticalGradient(listOf(color.copy(alpha = if (selected) 0.96f else 0.67f), NovaColors.Gold.copy(alpha = if (selected) 0.80f else 0.32f)))
                        } else {
                            Brush.verticalGradient(listOf(theme.background.copy(alpha = 0.65f), theme.background.copy(alpha = 0.65f)))
                        }
                        Box(
                            Modifier.weight(1f).height(27.dp).clip(RoundedCornerShape(6.dp))
                                .background(fill)
                                .border(1.dp, if (selected) Color.White.copy(alpha = 0.85f) else color.copy(alpha = if (cell.isPak) 0.32f else 0.12f), RoundedCornerShape(6.dp))
                                .clickable(enabled = cell.isPak) { onSelectDate(cell.dateIso) },
                            contentAlignment = Alignment.Center
                        ) { Text(cell.dateIso.takeLast(2), color = if (cell.isPak) Color.White else theme.textSecondary.copy(alpha = 0.55f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall) }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text("Case lumineuse = PAK · case sombre = snapshot sans PAK.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
        } else {
            Text("Aucune date PAK disponible pour l'instant.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(Modifier.height(11.dp))
        if (eventLoading) {
            Text("⏳ Chargement des trois charts pour cette date…", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else if (selectedEvent != null) {
            SectionTitle("LES TROIS PILIERS · ${selectedEvent.dateLabel.uppercase(Locale.FRANCE)}", color)
            PerfectAllKillTriadPanel(
                visual = selectedEvent,
                accent = color,
                sharing = sharingEvent,
                shareError = eventShareError,
                onShare = onShareEvent
            )
        }

        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.15f))
                .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                .clickable(enabled = !sharing, onClick = onShareRecord).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (sharing) "⏳ Préparation de la carte…" else "✦ PARTAGER LA FICHE · ${visual.period.label.uppercase(Locale.FRANCE)} ✦", color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
        if (shareError) Text("Impossible de préparer la carte. Réessaie.", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 💎 Deux cumuls indépendants, rival et historique des périodes #1 pour le record artiste. */
@Composable
private fun ArtistCumulativeNoOneSpotlight(
    name: String,
    imageUrl: String?,
    visual: RecordExplainer.ArtistCumulativeNoOneVisual,
    color: Color,
    sharing: Boolean,
    shareError: Boolean,
    onShare: () -> Unit
) {
    val theme = Nova.theme
    val chartColors = mapOf(
        "HOT_100" to Color(0xFF62C8FF),
        "ALBUMS_75" to Color(0xFFFFC76A)
    )
    val unit = when (visual.period) {
        Period.DAILY -> "jours"
        Period.WEEKLY -> "semaines"
        Period.MONTHLY -> "mois"
        Period.YEARLY -> "années"
        Period.GLOBAL -> "périodes"
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.24f), theme.surface.copy(alpha = 0.97f), NovaColors.Gold.copy(alpha = 0.10f))))
            .border(1.5.dp, Brush.horizontalGradient(listOf(color.copy(alpha = 0.74f), NovaColors.Gold.copy(alpha = 0.42f), color.copy(alpha = 0.50f))), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoverArt(imageUrl, name, size = 52, circle = true)
            Column(Modifier.weight(1f)) {
                Text("MOST CUMULATIVE #1", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium, letterSpacing = 0.8.sp)
                Text("${visual.selectedChartLabel} · ${visual.period.label}", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Rang #${visual.rank} sur ${visual.boardSize}${if (visual.tiedAtTop > 1) " · ${visual.tiedAtTop} ex æquo" else ""}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${visual.score}", color = color, fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 29.sp)
                Text(unit, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("DEUX CHARTS · COMPTEURS SÉPARÉS", color)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            visual.pillars.forEach { pillar ->
                val accent = chartColors[pillar.key] ?: color
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                        .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.19f), theme.background.copy(alpha = 0.54f))))
                        .border(1.dp, accent.copy(alpha = 0.36f), RoundedCornerShape(13.dp))
                        .padding(horizontal = 7.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("${pillar.emoji} ${pillar.label.uppercase(Locale.FRANCE)}", color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pillar.count.toString(), color = theme.text, fontWeight = FontWeight.Black, fontSize = 22.sp)
                    Text(unit, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(5.dp))
                    CoverArt(pillar.latestImageUrl, pillar.latestNumberOne ?: pillar.label, size = 38)
                    Text(pillar.latestNumberOne ?: "Aucun #1", color = theme.text, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(pillar.latestDate ?: "—", color = accent, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
        Text("Chaque chart garde son propre compteur : aucun total mixte ne compare des jours, semaines ou mois entre eux.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 7.dp))

        visual.rival?.let { rival ->
            Spacer(Modifier.height(11.dp))
            SectionTitle("FACE AU RIVAL LE PLUS PROCHE", NovaColors.Gold)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(theme.background.copy(alpha = 0.44f))
                    .border(1.dp, NovaColors.Gold.copy(alpha = 0.24f), RoundedCornerShape(15.dp))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        CoverArt(imageUrl, name, size = 39, circle = true)
                        Column(Modifier.weight(1f)) {
                            Text("#${visual.rank} · $name", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${visual.score} $unit · ${visual.selectedChartLabel}", color = color, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(if (rival.tied) "🤝" else "VS", color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("#${rival.rank} · ${rival.name}", color = theme.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                            Text("${rival.score} $unit · ${visual.selectedChartLabel}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                        }
                        CoverArt(rival.imageUrl, rival.name, size = 39, circle = true)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    visual.pillars.forEach { pillar ->
                        val accent = chartColors[pillar.key] ?: color
                        val rivalCount = rival.pillarCounts[pillar.key] ?: 0
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.10f))
                                .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(9.dp)).padding(vertical = 6.dp, horizontal = 3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(pillar.label, color = accent, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${pillar.count} : $rivalCount", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(11.dp))
        SectionTitle("HISTORIQUE RÉCENT · PÉRIODES AU N°1", color)
        val recent = visual.heatmap.takeLast(42)
        if (recent.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                recent.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { cell ->
                            val activeColors = cell.activeCharts.mapNotNull { chartColors[it] }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.fillMaxWidth().height(31.dp).clip(RoundedCornerShape(7.dp))
                                        .background(Brush.linearGradient(if (activeColors.isEmpty()) listOf(theme.background.copy(alpha = 0.55f), theme.background.copy(alpha = 0.55f)) else activeColors + activeColors.reversed()))
                                        .border(1.dp, if (cell.activeCharts.contains(visual.selectedChart)) color.copy(alpha = 0.84f) else theme.textSecondary.copy(alpha = 0.12f), RoundedCornerShape(7.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cell.date.takeLast(2), color = if (activeColors.isEmpty()) theme.textSecondary.copy(alpha = 0.55f) else Color.White, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                                    chartColors.forEach { (key, accent) ->
                                        Box(Modifier.size(4.dp).clip(CircleShape).background(if (key in cell.activeCharts) accent else Color.Transparent))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val from = recent.first().date
            val to = recent.last().date
            Text("${visual.period.label} · $from → $to · traits = chart où l'artiste était #1", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
        } else {
            Text("Aucun snapshot historique disponible pour cette cadence.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("ÉLÉMENTS CONTRIBUTEURS · PÉRIODE #1 (${visual.contributions.size})", color)
        Text("Titres/artistes crédités au Hot 100 et albums/propriétaires des Albums 75, datés selon la cadence sélectionnée.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        if (visual.contributions.isEmpty()) {
            Text("Aucun élément #1 conservé pour cette cadence.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 7.dp))
        } else {
            LazyRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(visual.contributions) { contribution ->
                    Column(
                        Modifier.width(218.dp).clip(RoundedCornerShape(13.dp)).background(theme.background.copy(alpha = 0.50f))
                            .border(1.dp, color.copy(alpha = 0.26f), RoundedCornerShape(13.dp)).padding(9.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(contribution.dateLabel, color = NovaColors.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        contribution.hot100Track?.let { track ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CoverArt(track.imageUrl, track.name, size = 40)
                                Column(Modifier.weight(1f)) {
                                    Text("🔥 HOT 100 · TITRE #1", color = chartColors.getValue("HOT_100"), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                    Text(track.name, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Crédit : ${track.creditedArtists ?: name}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        contribution.albums75Album?.let { album ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CoverArt(album.imageUrl, album.name, size = 40)
                                Column(Modifier.weight(1f)) {
                                    Text("💿 ALBUMS 75 · ALBUM #1", color = chartColors.getValue("ALBUMS_75"), fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                                    Text(album.name, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Propriétaire : ${album.creditedArtists ?: name}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 7.dp)) {
            chartColors.forEach { (key, accent) ->
                val label = visual.pillars.firstOrNull { it.key == key }?.label ?: key
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
                    Text(label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.18f))
                .border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(12.dp)).clickable(enabled = !sharing, onClick = onShare)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(if (sharing) "⏳ Préparation de la carte…" else "✦ PARTAGER CE RECORD ✦", color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
        }
        if (shareError) Text("Impossible de préparer la carte. Réessaie.", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SongReachMetricCard(label: String, count: Int, ratio: String, fraction: Float, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), theme.surface.copy(alpha = 0.88f))))
            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.58f), accent.copy(alpha = 0.20f))), shape)
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label.uppercase(Locale.FRANCE), color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(count.toString(), color = theme.text, fontWeight = FontWeight.Black, fontSize = 21.sp, lineHeight = 23.sp)
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(theme.textSecondary.copy(alpha = 0.16f))) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent))))
        }
        Text(ratio, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SongReachDuelMetric(label: String, current: Int, rival: Int, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), theme.surface.copy(alpha = 0.9f))))
            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = 0.44f), accent.copy(alpha = 0.15f))), shape)
            .padding(horizontal = 3.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label.uppercase(Locale.FRANCE), color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, maxLines = 1, fontSize = 8.sp)
        Text("$current : $rival", color = theme.text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, maxLines = 1, fontSize = 11.sp)
    }
}

@Composable
private fun SongReachMilestone(label: String, date: String?, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.clip(shape).background(Brush.verticalGradient(listOf(accent.copy(alpha = if (date == null) 0.07f else 0.22f), theme.surface.copy(alpha = 0.86f))))
            .border(1.dp, accent.copy(alpha = if (date == null) 0.14f else 0.46f), shape).padding(horizontal = 3.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label.uppercase(Locale.FRANCE), color = accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, maxLines = 1, fontSize = 8.sp)
        Text(date ?: "—", color = if (date == null) theme.textSecondary else theme.text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 8.sp, fontWeight = if (date == null) FontWeight.Normal else FontWeight.SemiBold)
    }
}

/** 📊 Échelle de présence, duel, statut et paliers — réservés aux quatre records de durée dans le chart. */
@Composable
private fun PresenceSpotlight(
    name: String,
    imageUrl: String?,
    isArtist: Boolean,
    recordId: String,
    visual: RecordExplainer.PresenceVisual,
    color: Color
) {
    val theme = Nova.theme
    val transition = rememberInfiniteTransition(label = "presence-ceremony")
    val glow by transition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(1050), RepeatMode.Reverse),
        label = "presence-glow"
    )
    val accent = if (visual.recordHolder) NovaColors.Gold else color
    val scoreCounter = remember(visual.score, recordId) { Animatable(0f) }
    LaunchedEffect(visual.score, recordId) {
        scoreCounter.snapTo(0f)
        scoreCounter.animateTo(visual.score.toFloat(), tween(if (visual.crownCeremony) 1350 else 750))
    }
    val panel = if (visual.recordHolder) {
        Brush.horizontalGradient(listOf(NovaColors.Gold.copy(alpha = if (visual.crownCeremony) glow else 0.18f), color.copy(alpha = 0.12f), theme.surface))
    } else {
        Brush.horizontalGradient(listOf(color.copy(alpha = 0.17f), theme.surface, theme.surface))
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(
            when {
                visual.crownCeremony -> "👑 Cérémonie du règne au #1"
                visual.isNumberOneRecord -> "👑 Cumul des #1 · règne continu"
                else -> "📊 Échelle de domination"
            },
            accent
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(panel)
                .border(1.dp, accent.copy(alpha = if (visual.crownCeremony) 0.7f else 0.45f), RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverArt(imageUrl, name, size = 52, circle = isArtist)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(visual.contextLabel.uppercase(Locale.FRANCE), color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(name, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("CLASSEMENT", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            visual.recordHolder && visual.tiedAtTop > 1 -> "🥇 #1 · ex æquo"
                            visual.recordHolder -> "🥇 Détenteur #1"
                            else -> "#${visual.rank ?: "?"}"
                        },
                        color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    scoreCounter.value.toInt().toString(), color = accent, fontWeight = FontWeight.Black, fontSize = 36.sp, lineHeight = 38.sp
                )
                Column(Modifier.weight(1f).padding(bottom = 3.dp)) {
                    Text(visual.recordLabel, color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(visual.periodUnit, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("ÉCHELLE DE DOMINATION", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                PresenceMetricCard("Chart", visual.chartCount, visual.chartCoveragePercent, "% de la période", color, Modifier.weight(1f))
                PresenceMetricCard("Top 10", visual.top10Count, visual.top10ConversionPercent, "% du chart", Color(0xFF5DADE2), Modifier.weight(1f))
                PresenceMetricCard("Top 5", visual.top5Count, visual.top5ConversionPercent, "% du Top 10", Color(0xFFBA68C8), Modifier.weight(1f))
                PresenceMetricCard("#1", visual.numberOneCount, visual.numberOneConversionPercent, "% du Top 5", NovaColors.Gold, Modifier.weight(1f))
            }
            Spacer(Modifier.height(11.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (visual.activeAtLatest) accent.copy(alpha = 0.13f) else theme.surface.copy(alpha = 0.82f))
                    .border(1.dp, (if (visual.activeAtLatest) accent else theme.textSecondary).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Text(
                    (if (visual.activeAtLatest) "🟢 " else "⚪ ") + visual.statusTitle.uppercase(Locale.FRANCE),
                    color = if (visual.activeAtLatest) accent else theme.textSecondary,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(3.dp))
                Text(visual.statusDetail, color = theme.text, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Série actuelle · ${visual.currentRun} ${visual.periodUnit}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    Text("Record continu · ${visual.longestRun} ${visual.periodUnit}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
                if (visual.longestRunStart != null && visual.longestRunEnd != null) {
                    Text("${visual.longestRunStart} → ${visual.longestRunEnd}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(9.dp))
            Text("DERNIÈRES DATES PERTINENTES", color = theme.textSecondary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOfNotNull(
                    visual.lastAppearance?.let { "Chart" to it },
                    visual.lastTop10?.let { "Top 10" to it },
                    visual.lastTop5?.let { "Top 5" to it },
                    visual.lastNumberOne?.let { "#1" to it }
                ).forEach { (label, date) ->
                    Column(
                        Modifier.padding(top = 5.dp).clip(RoundedCornerShape(9.dp)).background(theme.surface.copy(alpha = 0.75f))
                            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(9.dp)).padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(label, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text(date, color = theme.text, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            visual.rival?.let { rival ->
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.25f)))
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverArt(rival.imageUrl, rival.name, size = 42, circle = isArtist)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                rival.tied -> "🤝 À égalité"
                                rival.ahead -> "🎯 Juste devant"
                                else -> "🥈 Dauphin le plus proche"
                            },
                            color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium
                        )
                        Text(rival.name, color = theme.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Score · ${rival.score} ${visual.periodUnit}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (rival.tied) "ÉGALITÉ" else "ÉCART · ${rival.gap}",
                            color = if (rival.tied) NovaColors.Gold else theme.text,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (!rival.tied) Text(
                            "${visual.periodUnit} ${if (rival.ahead) "de retard" else "d'avance"}",
                            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End
                        )
                    }
                }
            } ?: Text("Aucun dauphin distinct dans les données de ce classement.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.2f)))
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${visual.milestone.reachedEmoji} ${visual.milestone.reachedLabel}", color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (visual.milestone.nextThreshold != null) "${visual.milestone.remaining} avant le prochain palier" else "Sommet atteint",
                    color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(theme.textSecondary.copy(alpha = 0.16f))) {
                Box(Modifier.fillMaxWidth(visual.milestone.progress).height(6.dp).background(Brush.horizontalGradient(listOf(color, accent))))
            }
            visual.milestone.nextLabel?.let {
                Text("Prochain palier · $it${visual.milestone.nextThreshold?.let { n -> " à $n ${visual.periodUnit}" } ?: ""}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun PresenceMetricCard(label: String, count: Int, percent: Int, ratioLabel: String, accent: Color, modifier: Modifier = Modifier) {
    val theme = Nova.theme
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(10.dp)).padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(count.toString(), color = theme.text, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("$percent%", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Text(ratioLabel, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 10.sp)
    }
}

/** 🟨 Frise positionnelle : un jalon par apparition, un interstice visible quand une période est manquée. */
@Composable
private fun PresencePositionRibbon(visual: RecordExplainer.PresenceVisual, color: Color) {
    val theme = Nova.theme
    val reveal = remember(visual) { Animatable(if (visual.crownCeremony) 0f else visual.longestRun.toFloat()) }
    LaunchedEffect(visual) {
        if (visual.crownCeremony) {
            reveal.snapTo(0f)
            reveal.animateTo(visual.longestRun.toFloat(), tween(1250))
        } else reveal.snapTo(visual.longestRun.toFloat())
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(if (visual.crownCeremony) "👑 Révélation du plus long règne" else "🎨 Frise des positions · ${visual.cells.size} période(s)", color)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PresenceLegend("#1", NovaColors.Gold)
            PresenceLegend("#2–5", color)
            PresenceLegend("#6–10", Color(0xFF5DADE2))
            PresenceLegend("Reste", theme.textSecondary)
        }
        Spacer(Modifier.height(7.dp))
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(visual.cells, key = { _, cell -> "${cell.periodIndex}:${cell.date}" }) { _, cell ->
                if (cell.gapBefore > 0) {
                    Column(Modifier.width(43.dp).height(70.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("⋯", color = theme.textSecondary, fontWeight = FontWeight.Black, fontSize = 19.sp)
                        Text("+${cell.gapBefore}", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                        Text("${visual.periodUnit} sans apparition", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, textAlign = TextAlign.Center, lineHeight = 9.sp)
                    }
                }
                val highlightRun = cell.longestRunStep > 0 && (!visual.crownCeremony || cell.longestRunStep <= reveal.value)
                val bandColor = when (cell.band) {
                    RecordExplainer.PresenceBand.NUMBER_ONE -> NovaColors.Gold
                    RecordExplainer.PresenceBand.TOP_FIVE -> color
                    RecordExplainer.PresenceBand.TOP_TEN -> Color(0xFF5DADE2)
                    RecordExplainer.PresenceBand.REST -> theme.textSecondary
                }
                Column(
                    Modifier.width(58.dp).height(70.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (cell.band == RecordExplainer.PresenceBand.REST) theme.surface else bandColor.copy(alpha = if (cell.band == RecordExplainer.PresenceBand.NUMBER_ONE) 0.2f else 0.14f))
                        .border(1.dp, if (highlightRun) NovaColors.Gold else bandColor.copy(alpha = 0.42f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(cell.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, fontSize = 9.sp)
                    Text(if (cell.position == 1) "👑" else "#${cell.position}", color = bandColor, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(
                        if (highlightRun) "SÉRIE ${cell.longestRunStep}" else "${cell.position}e place",
                        color = if (highlightRun) NovaColors.Gold else theme.textSecondary,
                        fontWeight = if (highlightRun) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            if (visual.crownCeremony) "Le doré révèle le plus long règne continu; le score principal reste le cumul total de #1, règnes isolés compris."
            else "Un jalon par période observée. Les pointillés marquent les périodes sans apparition; couleurs = niveau de position.",
            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun PresenceLegend(label: String, accent: Color) {
    val theme = Nova.theme
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.11f))
            .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(4.dp))
        Text(label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

/** 🪜 Étapes et temps intermédiaires de la certification ou de l'ascension Panthéon. */
@Composable
private fun SpeedStageLadder(recordId: String, visual: RecordExplainer.SpeedVisual, color: Color) {
    val theme = Nova.theme
    var revealed by remember(visual) { mutableStateOf(0) }
    LaunchedEffect(visual) {
        revealed = 0
        for (i in 1..visual.stages.size) { revealed = i; delay(170) }
    }
    val title = if (recordId == "FASTEST_CERT") "🪜 Parcours des certifications" else "👑 Ascension au Panthéon"
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(title, color)
        if (visual.stages.isEmpty()) {
            Text("Aucun jalon détaillé disponible.", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                visual.stages.forEachIndexed { index, stage ->
                    val current = index == visual.stages.lastIndex
                    val visible = index < revealed
                    Column(
                        Modifier.width(148.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (current) color.copy(alpha = if (visible) 0.19f else 0.07f) else theme.surface)
                            .border(1.dp, if (current) color.copy(alpha = 0.8f) else theme.textSecondary.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        Text("${if (current) "🏁" else "✓"} ${index + 1} · ${stage.label}", color = if (current) color else theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text(stage.date, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(7.dp))
                        Text("+${RecordCatalog.formatFastDuration(stage.splitMs)}", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        Text("${RecordCatalog.formatFastDuration(stage.elapsedMs)} depuis la découverte", color = theme.text, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        Text("${stage.playCount} écoutes · ${stage.activeDays} jours actifs", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        stage.route?.let { route ->
                            Spacer(Modifier.height(5.dp))
                            Text("↳ $route", color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Text("+ = temps depuis le jalon précédent · la durée sous chaque palier est cumulée depuis la première écoute.", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
}

/** 📆 Chaleur d'écoute sur toute la course, en jours / semaines / mois selon sa durée. */
@Composable
private fun SpeedActivityCalendar(visual: RecordExplainer.SpeedVisual, color: Color) {
    val theme = Nova.theme
    val maxPlays = visual.calendar.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
    val columns = when (visual.calendarGrain) {
        RecordExplainer.SpeedCalendarGrain.DAYS -> 7
        RecordExplainer.SpeedCalendarGrain.WEEKS -> 13
        RecordExplainer.SpeedCalendarGrain.MONTHS -> 6
    }
    val unit = when (visual.calendarGrain) {
        RecordExplainer.SpeedCalendarGrain.DAYS -> "jour par jour"
        RecordExplainer.SpeedCalendarGrain.WEEKS -> "semaine par semaine"
        RecordExplainer.SpeedCalendarGrain.MONTHS -> "mois par mois"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle("📆 Calendrier de la course · $unit", color)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.surface).padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${visual.activeDays} jours actifs", color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("sur ${visual.elapsedDays} jours écoulés", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            visual.calendar.chunked(columns).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    line.forEach { bucket ->
                        if (bucket.padding) {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val intensity = if (bucket.plays <= 0) 0.07f else 0.18f + 0.82f * (bucket.plays.toFloat() / maxPlays).coerceIn(0f, 1f)
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                                    .background(color.copy(alpha = intensity))
                                    .border(1.dp, if (bucket.activeDays > 0) color.copy(alpha = 0.38f) else Color.Transparent, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (visual.calendarGrain != RecordExplainer.SpeedCalendarGrain.DAYS) {
                                    Text(bucket.label, color = if (bucket.activeDays > 0) theme.text else theme.textSecondary, fontSize = 8.sp, maxLines = 1, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    repeat(columns - line.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
                }
                Spacer(Modifier.height(3.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Aucune écoute", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Box(Modifier.padding(horizontal = 5.dp).size(9.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.12f)))
                Text("→", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
                Box(Modifier.padding(horizontal = 5.dp).size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
                Text("journée la plus intense", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 🌐 Constellation Multi-Chart : positions simultanées sur la meilleure date de synchronisation. */
@Composable
private fun MultiChartConstellation(visual: RecordExplainer.MultiChartVisual, color: Color) {
    val theme = Nova.theme
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle(if (visual.top2) "🥈 Grand Chelem Top 2" else "🌐 Constellation des charts", color)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(theme.surface).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                visual.stages.forEach { stage ->
                    val tint = when (stage.position) {
                        1 -> NovaColors.Gold
                        2 -> NovaColors.Silver
                        null -> theme.textSecondary
                        else -> color
                    }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = if (stage.position == null) 0.08f else 0.16f))
                            .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(10.dp)).padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stage.code, color = tint, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                        Text(stage.position?.let { "#$it" } ?: "—", color = if (stage.position == 1) NovaColors.Gold else theme.text, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(stage.label, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        if (visual.top2) Text(if ((stage.position ?: Int.MAX_VALUE) <= 2) "✓ TOP 2" else "HORS TOP 2", color = tint, fontWeight = FontWeight.Bold, fontSize = 8.sp, maxLines = 1)
                    }
                }
            }
            visual.bestDate?.let {
                Spacer(Modifier.height(7.dp))
                Text("Meilleure synchronisation · $it", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Text(
                if (visual.top2) "Le même élément est #1 ou #2 dans chaque échelle affichée."
                else "Même élément, même date de référence — les rangs de chaque échelle sont réunis ici.",
                color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

/** Frise horizontale : une colonne par période (date, position, écoutes), les périodes clés en surbrillance. */
@Composable
private fun Timeline(points: List<RecordExplainer.Point>, color: Color, suffix: String = "▶") {
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
                    Text("$pl $suffix", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
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

/** 🔥 Calendrier de chaleur des séries d'écoute : un carré par jour, intensité = écoutes du jour, la journée la plus dangereuse cerclée d'orange. */
@Composable
private fun HeatGrid(points: List<RecordExplainer.Point>, color: Color) {
    val theme = Nova.theme
    val maxPlays = points.maxOfOrNull { it.plays ?: 0 } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        points.chunked(21).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                line.forEach { pt ->
                    val pl = pt.plays ?: 0
                    val intensity = if (maxPlays > 0) 0.15f + 0.85f * (pl.toFloat() / maxPlays) else 0.2f
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                            .background(color.copy(alpha = intensity))
                            .then(if (pt.highlight) Modifier.border(1.dp, Color(0xFFFF6D00), RoundedCornerShape(3.dp)) else Modifier)
                    )
                }
                repeat(21 - line.size) { Box(Modifier.weight(1f).aspectRatio(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("calme", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            Box(Modifier.padding(horizontal = 5.dp).width(14.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(color.copy(alpha = 0.2f)))
            Text("→", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            Box(Modifier.padding(horizontal = 5.dp).width(14.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Text("intense", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("🟧 la plus dangereuse", color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 🪑 Les sièges de la zone : dorés (avec leur numéro) quand le propriétaire les occupe, grisés sinon. */
@Composable
private fun SeatsRow(seats: RecordExplainer.Seats, color: Color) {
    val theme = Nova.theme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..seats.total).toList().chunked(if (seats.total <= 10) 10 else 25).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                line.forEach { seat ->
                    val mine = seat in seats.occupied
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                            .background(if (mine) Brush.horizontalGradient(listOf(NovaColors.Gold, color)) else Brush.horizontalGradient(listOf(theme.surface, theme.surface)))
                            .border(1.dp, if (mine) NovaColors.Gold else theme.textSecondary.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$seat", color = if (mine) theme.background else theme.textSecondary, fontWeight = if (mine) FontWeight.Black else FontWeight.Normal, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/** 🥇 Podium du dernier balayage : argent à gauche, or au centre (plus haut), bronze à droite. */
@Composable
private fun PodiumRow(items: List<RecordExplainer.Item>, color: Color) {
    val theme = Nova.theme
    val ranked = items.mapNotNull { ent ->
        val rank = ent.detail.substringBefore(" ").removePrefix("#").toIntOrNull() ?: return@mapNotNull null
        rank to ent
    }.sortedBy { it.first }
    if (ranked.isEmpty()) return
    val order = when (ranked.size) {
        1 -> ranked
        2 -> listOf(ranked[1], ranked[0])
        else -> listOf(ranked[1], ranked[0], ranked[2])
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEach { (rank, ent) ->
            val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
            val tint = when (rank) { 1 -> NovaColors.Gold; 2 -> NovaColors.Silver; else -> Color(0xFFCD7F32) }
            val h = when (rank) { 1 -> 84; 2 -> 62; else -> 46 }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                CoverArt(ent.imageUrl, ent.name, size = if (rank == 1) 62 else 46)
                Spacer(Modifier.height(4.dp))
                Text(medal, fontSize = 16.sp)
                Text(ent.name, color = theme.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.55f)))),
                    contentAlignment = Alignment.Center
                ) { Text("$rank", color = theme.background, fontWeight = FontWeight.Black, fontSize = 20.sp) }
            }
        }
    }
}

/** 🔥 Cérémonie de palier d'une série d'écoute : carte glow, pochette floutée, flamme géante, compteur qui monte jour par jour. */
@Composable
private fun StreakTierPopup(c: TierCelebration, onDismiss: () -> Unit) {
    val theme = Nova.theme
    val tierColor = Color(c.tier.colorArgb)
    val counter = remember(c.key) { Animatable(0f) }
    LaunchedEffect(c.key) {
        val steps = c.days.coerceIn(10, 40)
        var i = 0
        while (i < c.days) {
            i = minOf(c.days, i + maxOf(1, c.days / steps))
            counter.snapTo(i.toFloat())
            delay(900L / steps)
        }
    }
    NovaPopupCard(borderColor = tierColor, onDismiss = onDismiss, glowDp = 24, backdropUrl = c.imageUrl, banner = {}) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉 PALIER ATTEINT", color = tierColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Text(c.tier.emoji, fontSize = 56.sp)
            Text(c.name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("${counter.value.toInt()} jours d'écoute d'affilée", color = tierColor, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center)
            Text("${c.tier.emoji} ${c.tier.label} — la série continue", color = theme.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Un titre écouté chaque jour et la flamme tient. " +
                    (RecordCatalog.nextTier(c.days)?.let { "Prochain palier : ${it.emoji} ${it.label} à ${it.min} jours — encore ${it.min - c.days}." }
                        ?: "Tu es au sommet : plus aucun palier au-dessus de l'Inferno."),
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
            )
        }
    }
}

private data class TierCelebration(val key: String, val name: String, val imageUrl: String?, val days: Int, val tier: RecordCatalog.StreakTier)

private data class SimCelebration(
    val key: String, val name: String, val imageUrl: String?, val count: Int,
    val zoneLimit: Int, val zoneLabel: String, val periodType: String?, val category: String
)

private data class SweepCelebration(
    val key: String, val name: String, val imageUrl: String?, val count: Int,
    val zone: Int, val solo: Boolean, val periodType: String?, val category: String
)

private data class MultiChartCelebration(
    val key: String, val name: String, val imageUrl: String?, val count: Int, val sub: String, val category: String
)

/** 🌐 Cérémonie des paliers Multi-Chart — les échelles se révèlent l'une après l'autre. */
@Composable
private fun MultiChartCeremonyPopup(c: MultiChartCelebration, onDismiss: () -> Unit) {
    val theme = Nova.theme
    val top2 = RecordCatalog.multiChartIsTop2(c.sub)
    val base = RecordCatalog.multiChartBase(c.sub)
    val stages = RecordCatalog.multiChartChartKeys(c.sub)
    var revealed by remember(c.key) { mutableStateOf(0) }
    LaunchedEffect(c.key) {
        for (i in 1..stages.size) { revealed = i; delay(320) }
    }
    val title = when {
        top2 -> "🏆 GRAND CHELEM · TOP 2"
        base == "DW" -> "⚡ DOUBLE ÉCHELLE !"
        base == "DWM" -> "🔥 TRIPLE ÉCHELLE !"
        else -> "🌍 GRAND CHELEM !"
    }
    val highlight = if (top2) NovaColors.Gold else NovaColors.Silver
    NovaPopupCard(borderColor = highlight, onDismiss = onDismiss, glowDp = 24, backdropUrl = c.imageUrl, banner = {}) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = highlight, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(c.name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 21.sp, textAlign = TextAlign.Center)
            Text(sectionLabel(RecordCategory.valueOf(c.category)), color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                stages.forEachIndexed { index, code ->
                    val on = index < revealed
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (on) highlight.copy(alpha = 0.18f) else theme.surface)
                            .border(1.dp, if (on) highlight else theme.textSecondary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(vertical = 9.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(code, color = if (on) highlight else theme.textSecondary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                        Text(if (on) "VALIDÉ" else "…", color = if (on) highlight else theme.textSecondary, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("${c.count} ${if (c.count > 1) "jours" else "jour"} synchronisés", color = highlight, fontWeight = FontWeight.Black, fontSize = 23.sp, textAlign = TextAlign.Center)
            Text(
                if (top2) "#1 ou #2 dans chaque chart : le Grand Chelem d'élite est accompli."
                else "Les échelles de temps s'alignent — la domination dépasse un seul chart.",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
            )
        }
    }
}

/** 🧹 Cérémonie du balayage : les sièges de la zone se retournent un par un vers l'or — le geste signature, rejoué. */
@Composable
private fun SweepCeremonyPopup(c: SweepCelebration, onDismiss: () -> Unit) {
    val theme = Nova.theme
    val tier = RecordCatalog.sweepTier(c.count)
    val tierColor = tier?.let { Color(it.colorArgb) } ?: NovaColors.Gold
    var filled by remember(c.key) { mutableStateOf(0) }
    LaunchedEffect(c.key) {
        for (i in 1..c.zone) { filled = i; delay(300) }
    }
    val pLabel = Period.entries.firstOrNull { it.dbName == c.periodType }?.frLabel
    NovaPopupCard(borderColor = tierColor, onDismiss = onDismiss, glowDp = 24, backdropUrl = c.imageUrl, banner = {}) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🧹 BALAYAGE !", color = tierColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Text(c.name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(
                listOfNotNull(pLabel, runCatching { sectionLabel(RecordCategory.valueOf(c.category)) }.getOrNull(), "Top ${c.zone} · ${if (c.solo) "Solo" else "Standard"}").joinToString(" · "),
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            SeatsRow(RecordExplainer.Seats(c.zone, (1..filled).toList()), tierColor)
            Spacer(Modifier.height(10.dp))
            Text(
                "${c.count} ${if (c.count > 1) "balayages" else "balayage"} du Top ${c.zone}",
                color = tierColor, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center
            )
            tier?.let { Text("${it.emoji} ${it.label}", color = Color(it.colorArgb), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            Text(
                "Toutes les places de la zone, en même temps — et personne d'autre.",
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
            )
        }
    }
}

/** 🎯 « Le chart est à moi » : nouveau record de simultanéité — les sièges se remplissent un par un jusqu'au record. */
@Composable
private fun SimultaneousPopup(c: SimCelebration, onDismiss: () -> Unit) {
    val theme = Nova.theme
    val tier = RecordCatalog.simultTier(c.count, c.zoneLimit)
    val tierColor = tier?.let { Color(it.colorArgb) } ?: NovaColors.Gold
    var filled by remember(c.key) { mutableStateOf(0) }
    LaunchedEffect(c.key) {
        for (i in 1..c.count) { filled = i; delay(260) }
    }
    val seatsShown = if (c.zoneLimit <= 20) c.zoneLimit else 10
    val pLabel = Period.entries.firstOrNull { it.dbName == c.periodType }?.frLabel
    NovaPopupCard(borderColor = tierColor, onDismiss = onDismiss, glowDp = 24, backdropUrl = c.imageUrl, banner = {}) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎯 LE CHART EST À MOI", color = tierColor, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(10.dp))
            Text(c.name, color = theme.text, fontWeight = FontWeight.Black, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(
                listOfNotNull(pLabel, runCatching { sectionLabel(RecordCategory.valueOf(c.category)) }.getOrNull(), c.zoneLabel.removePrefix("le ")).joinToString(" · "),
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            SeatsRow(RecordExplainer.Seats(seatsShown, (1..filled).toList()), tierColor)
            Spacer(Modifier.height(10.dp))
            Text(
                "$filled ${if (filled > 1) "sièges" else "siège"} dans ${c.zoneLabel}",
                color = tierColor, fontWeight = FontWeight.Black, fontSize = 26.sp, textAlign = TextAlign.Center
            )
            tier?.let { Text("${it.emoji} ${it.label}", color = Color(it.colorArgb), fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            Text(
                "Nouveau record de simultanéité : jamais autant de places n'avaient été occupées à la fois dans ${c.zoneLabel} du chart ${pLabel ?: ""}.".replace("  ", " "),
                color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
            )
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
        RecordUnit.DURATION_MS -> if (def.id == "FASTEST_CERT" || def.id == "FASTEST_PANTHEON") RecordCatalog.formatFastDuration(value.toLong()) else RecordCatalog.formatDurationAdaptive(value.toLong())
        RecordUnit.TIMES -> "$n fois"
        RecordUnit.DAYS -> "$n jour${if (n > 1) "s" else ""}"
        RecordUnit.COUNT -> when (def.id) {
            "MOST_REENTRIES" -> "$n retour${if (n > 1) "s" else ""}"
            "MOST_RECORDS" -> "$n classement${if (n > 1) "s" else ""} remporté${if (n > 1) "s" else ""}"
            "MOST_CERTIFICATIONS" -> "$n certif${if (n > 1) "s" else ""}"
            "MOST_HOF", "MOST_GLOBAL" -> "$n entrée${if (n > 1) "s" else ""}"
            "MOST_PERFECT_ALL_KILL" -> "$n Perfect All Kill"
            "MOST_SUCCESSIVE_1" -> "$n éléments distincts au #1"
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

/**
 * Vraie chronologie des chutes : dans l'ordre des dates, chaque ligne qui fait STRICTEMENT mieux
 * que le meilleur précédent est une chute (pour Fastest Certification, plus PETIT = mieux).
 * Retourne les paires (nouveau détenteur, ancien détenteur battu).
 */
private fun fallenChronology(rs: List<RecordRow>, ascending: Boolean): List<Pair<RecordRow, RecordRow?>> {
    val out = mutableListOf<Pair<RecordRow, RecordRow?>>()
    var best: RecordRow? = null
    for (row in rs.sortedBy { it.r.valueDate ?: "" }) {
        row.r.valueDate ?: continue
        val better = best == null || (if (ascending) row.r.value < best.r.value else row.r.value > best.r.value)
        if (better) {
            out += row to best
            best = row
        }
    }
    return out
}

/** Les 12 records de la famille choisis par l'utilisateur — seuls records pris en compte. */
private val FAMILY_RECORDS = setOf(
    "MOST_RECORDS", "MOST_CUMULATIVE", "MOST_TIME_AT_1", "MOST_CERTIFICATIONS",
    "MOST_BLOCKED_TOP5", "MOST_CONSISTENT", "MOST_DEBUT_1", "MOST_DEBUT_TOP10",
    "MOST_SONGS_IN_CHART", "MOST_SONGS_AT_1", "MOST_SONGS_TOP10", "MOST_SUCCESSIVE_1"
)

/** Ligne de la famille des records : affichée sous les puces, cliquable vers son pop-up propre. */
private data class RecItem(
    val emoji: String,
    val title: String,
    val detail: String,
    /** Récit « ce qui s'est passé » — null = ligne non cliquable (certifications, Hall of Fame). */
    val story: String?,
    val row: RecordRow?,
    val top3: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val gapText: String? = null
)

/**
 * 🏆 Écran « famille des records » : les 4 vues en petites puces côte à côte (style Certifications),
 * la puce active est surlignée et ses données s'écrivent directement en dessous.
 * Seuls les 12 records choisis sont pris en compte.
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
            // Contexte par combinaison : top 3 actuel + chronologie des chutes
            val famRows = db.recordDao().rowsForTypes(FAMILY_RECORDS.toList())
            val groups = famRows.groupBy { listOf(it.r.recordType, it.r.periodType ?: "-", it.r.category, it.r.subcategory ?: "-") }
            val chrono = mutableMapOf<List<String>, List<Pair<RecordRow, RecordRow?>>>()
            val top3map = mutableMapOf<List<String>, List<String>>()
            for ((key, rs) in groups) {
                val def = RecordCatalog.ALL.firstOrNull { d -> d.id == rs.first().r.recordType } ?: continue
                chrono[key] = fallenChronology(rs, def.ascending)
                val medals = listOf("\ud83e\udd47", "\ud83e\udd48", "\ud83e\udd49")
                top3map[key] = rs.sortedBy { if (def.ascending) -it.r.value else it.r.value }.take(3)
                    .mapIndexed { i, r -> "${medals[i]} « ${r.name ?: "Inconnu"} » — ${valueOf(r)}" }
            }
            fun infoOf(key: List<String>) = top3map[key] ?: emptyList()
            fun histOf(key: List<String>) = (chrono[key] ?: emptyList()).reversed()
                .map { (r, _) -> "${r.r.valueDate} · « ${r.name ?: "Inconnu"} » — ${valueOf(r)}" }
            // 🎯⚠️ Most Cumulative vivant : le n°2 all time face au n°1 (titre / artiste / album)
            val mcRows = famRows.filter { it.r.recordType == "MOST_CUMULATIVE" && it.r.periodType == null }
            val mcKey = { cat: String -> listOf("MOST_CUMULATIVE", "-", cat, "-") }
            val t2 = db.trackDao().topAllTime(2).first()
            if (t2.size >= 2) {
                val gap = t2[0].periodPlays - t2[1].periodPlays
                val row = mcRows.filter { it.r.category == "TRACK" }.maxByOrNull { it.r.value }
                if (row != null) {
                    val detail = "Détenteur : « ${row.name ?: "Inconnu"} » — ${valueOf(row)}"
                    if (gap <= 0) me += RecItem("\ud83c\udfc6", "Most Cumulative · Titre", detail, "🔥 Égalité en tête du cumul : « ${t2[1].track.title} » a rejoint « ${t2[0].track.title} » — le record all time peut basculer à chaque écoute.", row, infoOf(mcKey("TRACK")), histOf(mcKey("TRACK")))
                    else ap += RecItem("\ud83c\udfc6", "Most Cumulative · Titre", detail, "⚠️ « ${t2[1].track.title} » n'est qu'à $gap écoutes du cumul de « ${t2[0].track.title} » (${t2[0].periodPlays} ▶) — le record all time est en approche.", row, infoOf(mcKey("TRACK")), histOf(mcKey("TRACK")), gapText = "$gap écoute(s)")
                }
            }
            val a2 = db.artistDao().topAllTime(2).first()
            if (a2.size >= 2) {
                val gap = a2[0].periodPlays - a2[1].periodPlays
                val row = mcRows.filter { it.r.category == "ARTIST" }.maxByOrNull { it.r.value }
                if (row != null) {
                    val detail = "Détenteur : « ${row.name ?: "Inconnu"} » — ${valueOf(row)}"
                    if (gap <= 0) me += RecItem("\ud83c\udfc6", "Most Cumulative · Artiste", detail, "🔥 Égalité en tête du cumul : « ${a2[1].artist.name} » a rejoint « ${a2[0].artist.name} » — le record all time peut basculer à chaque écoute.", row, infoOf(mcKey("ARTIST")), histOf(mcKey("ARTIST")))
                    else ap += RecItem("\ud83c\udfc6", "Most Cumulative · Artiste", detail, "⚠️ « ${a2[1].artist.name} » n'est qu'à $gap écoutes du cumul de « ${a2[0].artist.name} » (${a2[0].periodPlays} ▶) — le record all time est en approche.", row, infoOf(mcKey("ARTIST")), histOf(mcKey("ARTIST")), gapText = "$gap écoute(s)")
                }
            }
            val al2 = db.albumDao().topAllTime(2).first()
            if (al2.size >= 2) {
                val gap = al2[0].periodPlays - al2[1].periodPlays
                val row = mcRows.filter { it.r.category == "ALBUM" }.maxByOrNull { it.r.value }
                if (row != null) {
                    val detail = "Détenteur : « ${row.name ?: "Inconnu"} » — ${valueOf(row)}"
                    if (gap <= 0) me += RecItem("\ud83c\udfc6", "Most Cumulative · Album", detail, "🔥 Égalité en tête du cumul : « ${al2[1].album.title} » a rejoint « ${al2[0].album.title} » — le record all time peut basculer à chaque écoute.", row, infoOf(mcKey("ALBUM")), histOf(mcKey("ALBUM")))
                    else ap += RecItem("\ud83c\udfc6", "Most Cumulative · Album", detail, "⚠️ « ${al2[1].album.title} » n'est qu'à $gap écoutes du cumul de « ${al2[0].album.title} » (${al2[0].periodPlays} ▶) — le record all time est en approche.", row, infoOf(mcKey("ALBUM")), histOf(mcKey("ALBUM")), gapText = "$gap écoute(s)")
                }
            }
            // 🎯️ Statique : le n°2 du Top 10 de chaque record de la famille face au détenteur
            val statAp = mutableListOf<Pair<Double, RecItem>>()
            for ((key, rs) in groups) {
                if (key[0] == "MOST_CUMULATIVE" && key[1] == "-") continue // déjà géré en vivant
                val def = RecordCatalog.ALL.firstOrNull { d -> d.id == rs.first().r.recordType } ?: continue
                val sorted = rs.sortedBy { if (def.ascending) -it.r.value else it.r.value }
                if (sorted.size < 2) continue
                val one = sorted[0]; val two = sorted[1]
                val gapv = if (def.ascending) two.r.value - one.r.value else one.r.value - two.r.value
                if (gapv < 0) continue
                val cat = RecordCategory.valueOf(rs.first().r.category)
                val period = Period.entries.firstOrNull { it.dbName == rs.first().r.periodType }
                val subLabel = def.subs[cat]?.firstOrNull { it.dbName == rs.first().r.subcategory }?.label
                val title = listOfNotNull(def.title, period?.label, sectionLabel(cat), subLabel).joinToString(" · ")
                val detail = "Détenteur : « ${one.name ?: "Inconnu"} » — ${valueOf(one)}"
                if (gapv == 0.0) {
                    me += RecItem(def.emoji, title, detail, "🔥 Égalité en tête : « ${two.name ?: "Inconnu"} » (${valueOf(two)}) a rejoint « ${one.name ?: "Inconnu"} » — le record peut basculer à chaque instant.", one, infoOf(key), histOf(key))
                } else {
                    statAp += gapv to RecItem(def.emoji, title, detail,
                        "« ${two.name ?: "Inconnu"} » (${valueOf(two)}) n'est qu'à ${formatRecordValue(def, period, gapv)} du record de « ${one.name ?: "Inconnu"} » (${valueOf(one)}).",
                        one, infoOf(key), histOf(key), gapText = formatRecordValue(def, period, gapv))
                }
            }
            statAp.sortBy { it.first }
            ap += statAp.map { it.second }
            approche = ap
            menaces = me
            // 📅 Éphéméride : un jour comme aujourd'hui, les années passées
            val today = Dates.today()
            val mmdd = java.time.format.DateTimeFormatter.ofPattern("MM-dd")
            val ep = mutableListOf<RecItem>()
            for (h in db.certificationDao().allHistory()) {
                val d = java.time.Instant.ofEpochMilli(h.certifiedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                if (d != today && d.format(mmdd) == today.format(mmdd)) {
                    val lvl = CertLevel.entries.firstOrNull { it.dbName == h.level }
                    ep += RecItem(lvl?.emoji ?: "\ud83c\udfc5", "${d.year} · ${lvl?.label ?: h.level} obtenue", "« ${entityName(db, h.entityType, h.entityId)} »", null, null)
                }
            }
            for (h in db.hallOfFameDao().all()) {
                val d = runCatching { java.time.LocalDate.parse(h.entryDate) }.getOrNull() ?: continue
                if (d != today && d.format(mmdd) == today.format(mmdd)) {
                    ep += RecItem("\ud83c\udfdb️", "${d.year} · Hall of Fame", h.entryType.replace('_', ' ').lowercase(), null, null)
                }
            }
            for (row in db.recordDao().recentRows(300).filter { it.r.recordType in FAMILY_RECORDS }) {
                val vd = row.r.valueDate ?: continue
                val d = runCatching { java.time.LocalDate.parse(vd) }.getOrNull() ?: continue
                if (d != today && d.format(mmdd) == today.format(mmdd)) {
                    val def = RecordCatalog.ALL.firstOrNull { it.id == row.r.recordType }
                    val key = listOf(row.r.recordType, row.r.periodType ?: "-", row.r.category, row.r.subcategory ?: "-")
                    ep += RecItem(
                        def?.emoji ?: "\ud83c\udfc6", "${d.year} · ${def?.title ?: row.r.recordType}",
                        "« ${row.name ?: "Inconnu"} » — ${valueOf(row)}",
                        "Le $vd, « ${row.name ?: "Inconnu"} » a établi le record « ${def?.title ?: row.r.recordType} » avec ${valueOf(row)}.",
                        row, infoOf(key), histOf(key)
                    )
                }
            }
            ep.sortBy { it.title }
            ephemeride = ep
            // 🎉 Records battus : les 20 dernières vraies chutes de la famille
            val events = groups.flatMap { (key, _) -> (chrono[key] ?: emptyList()) }
                .filter { it.first.r.valueDate != null }
                .sortedByDescending { it.first.r.valueDate }
                .take(20)
            battus = events.map { (row, prev) ->
                val def = RecordCatalog.ALL.firstOrNull { it.id == row.r.recordType }
                val key = listOf(row.r.recordType, row.r.periodType ?: "-", row.r.category, row.r.subcategory ?: "-")
                val vd = row.r.valueDate
                val story = if (prev != null) {
                    "Le $vd, « ${row.name ?: "Inconnu"} » a fait tomber le record détenu par « ${prev.name ?: "Inconnu"} » (${valueOf(prev)}) — le nouveau palier est ${valueOf(row)}."
                } else {
                    "Le $vd, « ${row.name ?: "Inconnu"} » a établi ce record : ${valueOf(row)}."
                }
                RecItem(def?.emoji ?: "\ud83c\udfc6", "${def?.title ?: row.r.recordType}", "« ${row.name ?: "Inconnu"} » · $vd — ${valueOf(row)}", story, row, infoOf(key), histOf(key))
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Records", "🏆 La famille des 12 records", NovaColors.Gold, onBack, subtitle = "Tes records choisis · touche une ligne pour son pop-up complet")
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

/** Pop-up propre à la ligne cliquée : grand, premium, et complet — record expliqué, classement actuel, écart, récit, historique. */
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
            rc.gapText?.let { g ->
                Spacer(Modifier.height(10.dp))
                Text("ÉCART ACTUEL", color = theme.textSecondary, style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(g, color = NovaColors.Gold, fontWeight = FontWeight.Black, fontSize = 32.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        if (rc.top3.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionTitle("📊 Le classement aujourd'hui", NovaColors.Gold)
            rc.top3.forEach { Text(it, color = theme.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(NovaColors.Gold.copy(alpha = 0.25f)))
        Spacer(Modifier.height(10.dp))
        SectionTitle("📌 Ce qui s'est passé", NovaColors.Gold)
        Text(rc.story ?: "", color = theme.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
        if (rc.history.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionTitle("🕰️ Les anciens détenteurs", NovaColors.Gold)
            rc.history.forEach { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(rc.detail, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(6.dp))
    }
}
