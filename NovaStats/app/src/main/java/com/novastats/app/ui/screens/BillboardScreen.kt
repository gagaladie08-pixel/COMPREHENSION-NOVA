package com.novastats.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novastats.app.ui.navigation.BillboardFocus
import com.novastats.app.domain.BillboardDates
import com.novastats.app.domain.Chart
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Movement
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.isPride
import com.novastats.app.ui.theme.prideBrushFor
import com.novastats.app.ui.theme.prideFlagFor
import com.novastats.app.ui.theme.prideStripe
import com.novastats.app.ui.theme.prideWash
import com.novastats.app.ui.theme.NovaColors
import java.time.Instant
import androidx.compose.runtime.produceState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.ZoneOffset

private val Bronze = Color(0xFFCD7F32)

/**
 * 🏆 Billboard — Nova Hot 100 · Nova Artist 50 · Nova 75 Albums.
 * Classements figés par période (snapshots), navigation dans l'historique et fiche détaillée accessible au toucher.
 * Périodes + bandeau défilent avec la liste ; la rangée des charts reste collée en haut. Top 25 + « Voir plus » (+20).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BillboardScreen(vm: BillboardViewModel = viewModel()) {
    val theme = Nova.theme
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val compare by vm.compare.collectAsStateWithLifecycle()
    val focus by BillboardFocus.state.collectAsStateWithLifecycle()
    var pendingFocus by remember { mutableStateOf<BillboardFocus.Focus?>(null) }
    var highlightId by remember { mutableStateOf<Long?>(null) }
    var showPicker by remember { mutableStateOf(false) }

    var searching by remember { mutableStateOf(false) }

    val rows = state.filtered
    val limit = state.limit
    val unit = BillboardDates.unitLabel(state.period, 2)
    // Lignes à afficher : uniquement le classement réel (plus de places vides « — »)
    val totalRows = rows.size
    // Top 25 → « Voir plus » (+20) ; remis à 25 à chaque changement de chart / période / date / recherche
    var visible by remember(state.chart, state.period, state.anchor, state.query, state.sortDuration) { mutableIntStateOf(TOP_INITIAL) }
    val shown = minOf(visible, totalRows)
    val listState = rememberLazyListState()
    val screenScope = rememberCoroutineScope()
    LaunchedEffect(state.period, state.anchor) { listState.scrollToItem(0) }
    // 🚪 Téléporteur Stats → Billboard : applique chart/période, attend les lignes, surligne la cible
    LaunchedEffect(focus) {
        val f = focus ?: return@LaunchedEffect
        vm.search("")
        state.movementFilter?.let { vm.toggleMovementFilter(it) }
        vm.selectChart(f.chart)
        vm.selectPeriod(f.period)
        f.anchor?.let(vm::goToDate)
        pendingFocus = f
        BillboardFocus.consume()
    }
    LaunchedEffect(pendingFocus, state.items, state.anchor, state.chart, state.period, state.query, state.movementFilter, state.sortDuration) {
        val f = pendingFocus ?: return@LaunchedEffect
        if (state.chart != f.chart || state.period != f.period) return@LaunchedEffect
        if (f.anchor != null && state.anchor != BillboardDates.anchor(f.period, f.anchor)) return@LaunchedEffect
        if (state.query.isNotBlank() || state.movementFilter != null) return@LaunchedEffect
        if (state.items.isEmpty()) return@LaunchedEffect
        val snapshotItem = state.items.firstOrNull { it.entityId == f.entityId }
        if (f.expectedPosition != null && snapshotItem?.position != f.expectedPosition) return@LaunchedEffect
        if (f.expectedPlays != null && snapshotItem?.plays != f.expectedPlays) return@LaunchedEffect
        val idx = state.filtered.indexOfFirst { it.entityId == f.entityId }
        if (idx < 0) {
            if (f.expectedPosition == null) pendingFocus = null
            return@LaunchedEffect
        }
        if (visible < idx + 1) visible = idx + 1
        highlightId = f.entityId
        kotlinx.coroutines.delay(80)
        val dividersBefore = if (state.query.isBlank()) state.filtered.take(idx).count { it.position == 10 } else 0
        listState.scrollToItem(2 + idx + dividersBefore)
        pendingFocus = null
    }
    LaunchedEffect(highlightId) {
        if (highlightId != null) { kotlinx.coroutines.delay(2600); highlightId = null }
    }

    // 🎨 Pochette du n°1 du chart affiché, en fond d'écran
    val artUrl = state.items.firstOrNull()?.coverUrl

    ScreenBackdrop(artUrl) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        /* ---------- En-tête défilant : périodes → navigation → bandeau ---------- */
        item(key = "header") {
            Column(Modifier.fillMaxWidth()) {
                // 1. Périodes
                PeriodSegment(state.period) { vm.selectPeriod(it) }

                // 2. Navigation dans l'historique : la date et le calendrier sont directement découvrables.
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::previousPeriod) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Période précédente", tint = theme.text) }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = { showPicker = true }, onLongClick = { showPicker = true })
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                BillboardDates.label(state.period, state.anchor), color = theme.text, fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Filled.DateRange, "Ouvrir le calendrier", tint = theme.primary, modifier = Modifier.size(18.dp))
                            if (state.isCurrent) {
                                Spacer(Modifier.width(6.dp))
                                Badge("LIVE", NovaColors.Down)
                            }
                        }
                        Text(
                            if (state.isCurrent) "Dernière période publiée" else "Période archivée · toucher pour choisir une date",
                            color = if (state.isCurrent) NovaColors.Down else theme.textSecondary,
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = vm::nextPeriod, enabled = !state.isCurrent) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Période suivante", tint = if (state.isCurrent) theme.textSecondary.copy(alpha = 0.3f) else theme.text)
                    }
                }

                // 3. Cases de mouvement (↑ ↓ ✨ 🔁) : un appui filtre la liste, un second le retire
                if (state.items.isNotEmpty()) MovementFilterBoxes(state, vm::toggleMovementFilter)
                // 4. Bandeau résumé (style Stats)
                if (state.hasAnyData) SummaryBanner(state)
                // 4. Carte « #1 de la période » : la pochette du #1 en fond, titre, artiste, écoutes, règne
                state.summary.numberOne?.let { one -> if (state.items.isNotEmpty()) NumberOneCard(one, state) { vm.openHistory(one) } }
                // 5. Histoires hebdo, mensuelles et bilan des changements du Global cumulatif.
                if (state.period == Period.WEEKLY || state.period == Period.MONTHLY || state.period == Period.GLOBAL) {
                    val stories = remember(state.items, state.summary, state.chart, state.period) { buildStories(state) }
                    if (stories.isNotEmpty()) StoriesCarousel(stories, state.period)
                }
                TextButton(
                    onClick = { screenScope.launch { listState.animateScrollToItem(1) } },
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 12.dp)
                ) { Text("↓ Voir le classement", color = theme.primary, fontWeight = FontWeight.Bold) }
            }
        }

        /* ---------- Sous-onglets collants : Hot 100 / Artist 50 / Albums 75 + loupe (+ recherche) ---------- */
        stickyHeader(key = "tabs") {
            val chartIndex = Chart.entries.indexOf(state.chart)
            Column(Modifier.fillMaxWidth().background(theme.background)) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TabRow(
                        selectedTabIndex = chartIndex,
                        modifier = Modifier.weight(1f),
                        containerColor = theme.background,
                        contentColor = theme.primary,
                        indicator = { positions ->
                            Box(Modifier.tabIndicatorOffset(positions[chartIndex]).padding(horizontal = 24.dp).height(if (Nova.isPride) 5.dp else 3.dp).clip(RoundedCornerShape(2.dp)).background(if (Nova.isPride) Brush.horizontalGradient(prideFlagFor(chartIndex + 1)) else SolidColor(theme.primary)))
                        },
                        divider = {}
                    ) {
                        Chart.entries.forEach { c ->
                            val on = state.chart == c
                            Tab(
                                selected = on, onClick = { vm.selectChart(c) },
                                text = { Text(c.label.removePrefix("Nova "), style = MaterialTheme.typography.titleSmall, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1, softWrap = false) },
                                selectedContentColor = theme.primary, unselectedContentColor = theme.textSecondary
                            )
                        }
                    }
                    IconButton(onClick = { searching = !searching; if (!searching) vm.search("") }, modifier = Modifier.padding(end = 4.dp)) {
                        Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, "Rechercher", tint = if (searching || state.query.isNotBlank()) theme.primary else theme.textSecondary)
                    }
                }
                if (searching) {
                    OutlinedTextField(
                        value = state.query, onValueChange = vm::search, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        placeholder = { Text("Rechercher dans ${state.chart.label}…", color = theme.textSecondary) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = theme.textSecondary) },
                        trailingIcon = { if (state.query.isNotEmpty()) IconButton({ vm.search("") }) { Icon(Icons.Filled.Close, "Effacer", tint = theme.textSecondary) } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = theme.text),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = theme.primary, unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f), cursorColor = theme.primary),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                SortToggle(state.sortDuration) { vm.toggleSort() }
                if (state.period == Period.GLOBAL) {
                    Text(
                        if (state.sortDuration) "Global : écoutes, durée et rang cumulés · tri par durée · delta et mouvements vs semaine précédente"
                        else "Global : écoutes, durée et rang cumulés · delta d'écoutes et mouvements vs semaine précédente",
                        color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                } else if (state.sortDuration) {
                    Text(
                        "Rang affiché = durée d'écoute · mouvements et records = classement officiel",
                        color = theme.textSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        /* ---------- Classement ---------- */
        if (!state.hasAnyData) {
            item(key = "empty") {
                Box(Modifier.fillParentMaxHeight(0.6f)) {
                    EmptyState("🏆", "Ton Billboard t'attend", "Lance ta première écoute : chaque titre, artiste et album se battra pour la 1re place !")
                }
            }
        } else {
            if (state.items.isEmpty() && state.query.isBlank()) {
                item(key = "no-period") {
                    Text(
                        "Aucune écoute sur cette période — le classement se remplira à la prochaine lecture 🎧",
                        color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            }
            if (state.query.isNotBlank() && rows.isEmpty()) {
                item(key = "no-result") {
                    Text(
                        "Aucun résultat pour « ${state.query} » dans ${state.chart.label}",
                        color = theme.textSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
            }

            val realShown = minOf(shown, rows.size)
            items(rows.take(realShown), key = { it.entityId }) { item ->
                ChartRow(
                    item, state.period, showDuration = state.sortDuration, highlighted = item.entityId == highlightId,
                    onOpenHistory = { vm.openHistory(if (state.sortDuration) item.copy(position = item.officialPosition) else item) }
                )
                if (item.position == 10 && state.query.isBlank()) Top10Divider()
            }

            if (totalRows > shown) item(key = "more") { LoadMoreButton(totalRows - shown) { visible += TOP_STEP } }
            else if (state.query.isBlank() && state.items.size < limit) {
                item(key = "remaining") {
                    Text(
                        "Encore ${limit - state.items.size} place${if (limit - state.items.size > 1) "s" else ""} à conquérir sur ce $unit… continue d'écouter ✨",
                        color = theme.textSecondary.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
        }
    }
    }


    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(Dates.today())
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { vm.goToDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text("Aller à cette date") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } }
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }

    history?.let { h ->
        ChartHistoryDialog(
            h, onDismiss = { vm.clearCompare(); vm.closeHistory() },
            compare = compare, candidates = state.items, navigationCandidates = rows.take(shown),
            onCompare = vm::compareWith, onClearCompare = vm::clearCompare, onShare = { vm.shareHistory(theme) },
            onNavigate = { item ->
                vm.clearCompare()
                vm.openHistory(if (state.sortDuration) item.copy(position = item.officialPosition) else item)
            },
            onViewPeriod = { date ->
                h.appearances.firstOrNull { it.date == date }?.let { appearance ->
                    vm.clearCompare()
                    vm.closeHistory()
                    BillboardFocus.request(
                        BillboardFocus.Focus(
                            chart = h.chart, period = h.period, entityId = h.item.entityId,
                            anchor = date, expectedPosition = appearance.position, expectedPlays = appearance.playCount
                        )
                    )
                }
            }
        )
    }
}

/* ------------------------------------------------------------------------- */

@Composable
private fun SummaryBanner(state: BillboardUiState) {
    val theme = Nova.theme
    val s = state.summary
    var detailsExpanded by remember(state.chart, state.period, state.anchor) { mutableStateOf(false) }
    val cells = listOf(
        StripCell("${state.items.size}", "Classés"),
        StripCell("${s.newEntries}", "Nouveautés", NovaColors.DirectDebut),
        StripCell("${s.exits}", "Sorties", NovaColors.Down),
        StripCell("${s.reentries}", "Retours", theme.secondary)
    )
    val hasDetails = state.items.isNotEmpty() && (s.numberOne != null || s.biggestClimber != null || s.totalPlays > 0 || s.totalDurationMs > 0)
    val details: (@Composable () -> Unit)? = if (!hasDetails) null else {
        {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { detailsExpanded = !detailsExpanded }
                    .padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (detailsExpanded) "Masquer les détails" else "Voir le détail de la période",
                    color = theme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.weight(1f))
                Text(if (detailsExpanded) "−" else "+", color = theme.primary, fontWeight = FontWeight.Bold)
            }
            if (detailsExpanded) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // ⏱️ Temps d'écoute et ▶ écoutes de la période, variation vs période précédente.
                    if (s.totalPlays > 0 || s.prevPlays > 0) {
                        if (state.period == Period.GLOBAL) {
                            // Le cumul all-time se lit en delta absolu, pas en pourcentage.
                            val dDur = s.totalDurationMs - s.prevDurationMs
                            val dPlays = s.totalPlays - s.prevPlays
                            TrendLine("⏱️", formatDuration(s.totalDurationMs), s.totalDurationMs.toDouble(), s.prevDurationMs.toDouble(), formatDuration(s.prevDurationMs),
                                deltaText = "${if (dDur >= 0) "+" else "-"}${formatDuration(kotlin.math.abs(dDur))} cette semaine", prevPrefix = "cumul à la semaine précédente")
                            TrendLine("▶", "${formatCount(s.totalPlays)} écoutes", s.totalPlays.toDouble(), s.prevPlays.toDouble(), "${formatCount(s.prevPlays)} écoutes",
                                deltaText = "${if (dPlays >= 0) "+" else "-"}${formatCount(kotlin.math.abs(dPlays))} écoutes cette semaine", prevPrefix = "cumul à la semaine précédente")
                        } else {
                            TrendLine("⏱️", formatDuration(s.totalDurationMs), s.totalDurationMs.toDouble(), s.prevDurationMs.toDouble(), formatDuration(s.prevDurationMs))
                            TrendLine("▶", "${formatCount(s.totalPlays)} écoutes", s.totalPlays.toDouble(), s.prevPlays.toDouble(), "${formatCount(s.prevPlays)} écoutes")
                        }
                    }
                    s.numberOne?.let { one ->
                        val since = if (s.numberOneRun > 1) " · depuis ${s.numberOneRun} ${BillboardDates.unitLabel(state.period, s.numberOneRun)}" else " · nouveau #1"
                        Text(
                            buildString { append("👑 #1 : "); append(one.name); one.secondary?.let { if (state.chart == Chart.HOT_100 || state.chart == Chart.ALBUMS_75) append(" — $it") }; append(since) },
                            color = NovaColors.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    s.biggestClimber?.let { c ->
                        Text(
                            "🚀 Plus forte montée : ${c.name} (${c.movement.label()} → #${c.position})",
                            color = NovaColors.Up, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    s.biggestFaller?.let { c ->
                        Text(
                            "📉 Plus forte régression : ${c.name} (${c.movement.label()} → #${c.position})",
                            color = NovaColors.Down, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
    SummaryStrip(cells, content = details)
}

/**
 * Carte #1 (maquette utilisateur) : fond = pochette / photo du #1 assombrie, « 🎵 #1 SONG DE LA PÉRIODE »,
 * titre en accent, artiste, « N× écoutes », « 👑 Day/Week N » (règne en cours), grand « #1 » en filigrane à droite.
 * Appui → fiche historique de l'élément.
 */
@Composable
private fun NumberOneCard(one: ChartItem, state: BillboardUiState, onClick: () -> Unit) {
    val theme = Nova.theme
    val kind = when (state.chart) { Chart.HOT_100 -> "🎵 #1 SONG"; Chart.ARTIST_50 -> "🎤 #1 ARTIST"; Chart.ALBUMS_75 -> "💿 #1 ALBUM" }
    val runWord = when (state.period) { Period.DAILY -> "Day"; Period.WEEKLY -> "Week"; Period.MONTHLY -> "Month"; Period.YEARLY -> "Year"; Period.GLOBAL -> "Semaine" }
    val run = state.summary.numberOneRun.coerceAtLeast(1)
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(shape)
            .background(theme.surface).border(1.dp, NovaColors.Gold.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
    ) {
        // Fond : la photo du #1, recadrée et assombrie, dégradé vers la gauche pour la lisibilité
        if (one.coverUrl != null) {
            coil.compose.AsyncImage(model = one.coverUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize().alpha(0.55f))
        }
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f)))))
        // Filigrane « #1 »
        Text(
            "#1", color = NovaColors.Gold.copy(alpha = 0.28f), fontWeight = FontWeight.Black, fontSize = 96.sp, letterSpacing = (-4).sp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp)
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("$kind DE LA PÉRIODE", color = theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(10.dp))
            Text(one.name, color = theme.accent, fontWeight = FontWeight.Black, fontSize = 28.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 90.dp))
            if (one.secondary != null && state.chart != Chart.ARTIST_50) {
                Text(one.secondary, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 90.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (state.period == Period.GLOBAL) "${formatCount(one.plays)} écoutes cumulées" else "${one.plays}× écoutes",
                color = theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text("👑 $runWord $run", color = theme.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** « ⏱️ 17h 38min ↑ +13 % » + « vs période précédente : 15h 29min ». */
@Composable
private fun TrendLine(
    emoji: String, value: String, cur: Double, prev: Double, prevLabel: String,
    /** Global : au lieu du % (croissance du cumul), on affiche le delta absolu de la semaine (« +87 écoutes cette semaine »). */
    deltaText: String? = null, prevPrefix: String = "vs période précédente"
) {
    val theme = Nova.theme
    val pct = if (prev <= 0.0) null else ((cur - prev) / prev * 100).toInt()
    val up = cur >= prev
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$emoji ", style = MaterialTheme.typography.titleMedium)
        Text(value, color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                deltaText != null -> deltaText
                pct == null -> if (cur > 0) "nouveau" else ""; pct >= 0 -> "↑ +$pct %"; else -> "↓ $pct %"
            },
            color = if (up) NovaColors.Up else NovaColors.Down, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium
        )
    }
    Text("$prevPrefix : $prevLabel", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun Top10Divider() {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = theme.primary.copy(alpha = 0.4f))
        Text("  TOP 10  ", color = theme.primary, style = MaterialTheme.typography.labelSmall)
        HorizontalDivider(Modifier.weight(1f), color = theme.primary.copy(alpha = 0.4f))
    }
}

/** Quatre cases ↑ ↓ ✨ 🔁 avec compteur ; la case active est surlignée en couleur primaire du thème. */
@Composable
private fun MovementFilterBoxes(state: BillboardUiState, onToggle: (MovementFilter) -> Unit) {
    val theme = Nova.theme
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MovementFilter.entries.forEach { f ->
            val selected = state.movementFilter == f
            val tint = when (f) {
                MovementFilter.UP -> NovaColors.Up
                MovementFilter.DOWN -> NovaColors.Down
                MovementFilter.NEW -> NovaColors.Gold
                MovementFilter.REENTRY -> theme.secondary
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (selected) theme.primary.copy(alpha = 0.22f) else theme.surface)
                    .border(if (selected) 1.5.dp else 1.dp, if (selected) theme.primary else tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { onToggle(f) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${f.emoji} ${state.count(f)}", color = if (selected) theme.primary else tint, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
                Text(f.label, color = if (selected) theme.text else theme.textSecondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
fun movementColor(m: Movement): Color = when (m) {
    is Movement.Up -> NovaColors.Up
    is Movement.Down -> NovaColors.Down
    Movement.Same -> NovaColors.Neutral
    Movement.New -> NovaColors.DirectDebut
    Movement.Reentry -> Nova.theme.secondary
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartRow(item: ChartItem, period: Period, showDuration: Boolean = false, highlighted: Boolean = false, onOpenHistory: () -> Unit) {
    val theme = Nova.theme
    val isOne = item.position == 1
    val bg = when (item.position) {
        1 -> Brush.horizontalGradient(listOf(NovaColors.Gold.copy(alpha = 0.28f), NovaColors.Gold.copy(alpha = 0.08f)))
        2 -> Brush.horizontalGradient(listOf(NovaColors.Silver.copy(alpha = 0.14f), Color.Transparent))
        3 -> Brush.horizontalGradient(listOf(Bronze.copy(alpha = 0.14f), Color.Transparent))
        else -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val posColor = when (item.position) { 1 -> NovaColors.Gold; 2 -> NovaColors.Silver; 3 -> Bronze; else -> theme.text }
    val coverSize = if (isOne) 64 else 48
    val mColor = movementColor(item.movement)
    val pride = Nova.isPride
    val flag = prideFlagFor(item.position)

    Row(
        modifier = Modifier.fillMaxWidth()
            .then(
                when {
                    // Survivor : le #1 est cerclé du dégradé de tous les drapeaux, chaque ligne porte le drapeau de sa position
                    isOne && pride -> Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, Brush.horizontalGradient(NovaColors.PrideCycle), RoundedCornerShape(14.dp))
                    isOne -> Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, NovaColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    else -> Modifier
                }
            )
            .then(if (pride) Modifier.prideWash(flag, alpha = if (item.position <= 3) 0.14f else 0.07f).prideStripe(flag) else Modifier.background(bg))
            .then(if (highlighted) Modifier.border(2.dp, NovaColors.Gold, RoundedCornerShape(14.dp)) else Modifier)
            .combinedClickable(onClick = onOpenHistory, onLongClick = onOpenHistory)
            .padding(horizontal = 12.dp, vertical = if (isOne) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Position + mouvement
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (pride) Text("#${item.position}", fontWeight = FontWeight.Black, fontSize = if (isOne) 22.sp else 16.sp, style = LocalTextStyle.current.copy(brush = prideBrushFor(item.position)))
            else Text("#${item.position}", color = posColor, fontWeight = FontWeight.Black, fontSize = if (isOne) 22.sp else 16.sp)
            when (val m = item.movement) {
                Movement.New -> Badge("NEW", NovaColors.DirectDebut)
                Movement.Reentry -> Badge("↩ RE", theme.secondary)
                else -> Text(m.label(), color = mColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (showDuration) Text("off. #${item.officialPosition}", color = theme.textSecondary, fontSize = 9.sp, maxLines = 1)
        }
        CoverArt(item.coverUrl, item.name, size = coverSize, circle = item.circle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name, color = theme.text, fontWeight = if (isOne) FontWeight.Black else FontWeight.SemiBold,
                fontSize = if (isOne) 17.sp else 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            item.secondary?.let { Text(it, color = theme.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text(
                "${item.periodsInChart} ${BillboardDates.unitLabel(period, item.periodsInChart)} · Peak #${item.peakPosition} ×${item.timesAtPeak}",
                color = theme.textSecondary.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val m = item.movement
            // PEAK = première atteinte du meilleur rang ; REPEAK = retour à ce rang après l'avoir quitté.
            val positionPeak = m !is Movement.New && item.officialPosition == item.peakPosition && item.timesAtPeak == 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    item.isRepeak -> { Badge("REPEAK", NovaColors.Gold); Spacer(Modifier.width(4.dp)) }
                    positionPeak -> { Badge("PEAK", NovaColors.Up); Spacer(Modifier.width(4.dp)) }
                }
                Text(if (showDuration) formatDuration(item.durationMs) else "${formatCount(item.plays)} ▶", color = if (isOne) NovaColors.Gold else theme.primary, fontWeight = FontWeight.Bold, fontSize = if (isOne) 17.sp else 15.sp)
            }
            // En Global, les totaux cumulés montent mécaniquement; le delta hebdomadaire est plus informatif que NP.
            if (item.isPlaysPeak && period != Period.GLOBAL) Text("NP", color = NovaColors.Up, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            if (period == Period.GLOBAL || (m !is Movement.New && m !is Movement.Reentry)) {
                val v = item.variationPlays
                val (txt, col) = when {
                    v > 0 -> "+$v" to NovaColors.Up
                    v < 0 -> "$v" to NovaColors.Down
                    else -> (if (period == Period.GLOBAL) "0" else "=") to NovaColors.Neutral
                }
                Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Ouvrir la fiche historique de ${item.name}", tint = theme.textSecondary, modifier = Modifier.size(18.dp))
    }
}


/** Carte du carrousel « histoires » — un fait, une pochette. */
private data class Story(val coverUrl: String?, val circle: Boolean, val text: String)

/** Dix faits possibles calculés sur le snapshot affiché ; chaque carte absente = fait manquant. */
private fun buildStories(st: BillboardUiState): List<Story> {
    val items = st.items
    if (items.isEmpty()) return emptyList()
    if (st.period == Period.GLOBAL) return buildGlobalStories(st)
    val chartName = st.chart.label.removePrefix("Nova ")
    val de: (ChartItem) -> String = { it.secondary?.let { a -> " de $a" } ?: "" }
    val out = mutableListOf<Story>()
    // 1. Entrée directe (New n°1) sinon meilleure nouvelle entrée
    val newBest = items.filter { it.movement is Movement.New }.minByOrNull { it.position }
    if (newBest != null && newBest.position == 1) {
        out += Story(newBest.coverUrl, newBest.circle, "« ${newBest.name} »" + de(newBest) + " est entré directement n°1 du classement $chartName. Une entrée directe.")
    } else if (newBest != null) {
        out += Story(newBest.coverUrl, newBest.circle, "« ${newBest.name} »" + de(newBest) + " est la meilleure nouvelle entrée du classement $chartName, directement à la ${newBest.position}ᵉ place.")
    }
    // 2. Dominance : l'artiste qui occupe le plus de places
    if (st.chart != Chart.ARTIST_50) {
        items.groupBy { it.secondary.orEmpty() }.filter { it.key.isNotBlank() && it.value.size >= 2 }
            .maxByOrNull { it.value.size }?.let { (artist, rows) ->
                val one = items.first()
                out += if (one.secondary == artist)
                    Story(one.coverUrl, one.circle, "$artist occupe le plus grand nombre de places dans le classement $chartName (${rows.size}). Son titre « ${one.name} » est numéro 1.")
                else {
                    val best = rows.minByOrNull { it.position }!!
                    Story(best.coverUrl, best.circle, "$artist occupe le plus grand nombre de places dans le classement $chartName (${rows.size}), avec « ${best.name} » en meilleure position (#${best.position}).")
                }
            }
    }
    // 3. Duel au sommet : l'écart #1 / #2
    val one = items.first()
    val two = items.getOrNull(1)
    if (two != null) {
        val gap = one.plays - two.plays
        out += if (gap <= 10)
            Story(one.coverUrl, one.circle, "« ${one.name} » et « ${two.name} » ne sont séparés que de $gap écoute${if (gap > 1) "s" else ""} en tête du classement $chartName.")
        else Story(one.coverUrl, one.circle, "« ${one.name} » mène le classement $chartName avec $gap écoutes d'avance sur « ${two.name} ».")
    }
    // 4. Longévité : le plus grand nombre de périodes consécutives dans le chart
    items.maxByOrNull { it.periodsInChart }?.takeIf { it.periodsInChart > 1 }?.let { r ->
        out += Story(r.coverUrl, r.circle, "« ${r.name} »" + de(r) + " détient le record de longévité du classement $chartName, avec ${r.periodsInChart} ${BillboardDates.unitLabel(st.period, r.periodsInChart)} d'affilée.")
    }
    // 5. Plus forte progression de la période
    st.summary.biggestClimber?.let { c ->
        val up = c.movement as? Movement.Up ?: return@let
        out += Story(c.coverUrl, c.circle, "« ${c.name} »" + de(c) + " signe la plus forte progression du classement $chartName, gagnant ${up.places} place${if (up.places > 1) "s" else ""} pour atteindre la ${c.position}ᵉ position.")
    }
    // 6. Plus forte chute de la période
    st.summary.biggestFaller?.let { f ->
        val down = f.movement as? Movement.Down ?: return@let
        out += Story(f.coverUrl, f.circle, "« ${f.name} »" + de(f) + " signe la plus forte chute de la période, perdant ${down.places} place${if (down.places > 1) "s" else ""} — maintenant #${f.position}.")
    }
    // 7. Le retour : meilleure réentrée
    items.filter { it.movement is Movement.Reentry }.minByOrNull { it.position }?.let { r ->
        out += Story(r.coverUrl, r.circle, "« ${r.name} »" + de(r) + " fait son retour dans le classement $chartName, directement à la ${r.position}ᵉ place.")
    }
    // 8. Dynastie : le n°1 actuel déjà couronné plusieurs fois
    st.summary.numberOne?.takeIf { it.timesAtPeak > 1 }?.let { n ->
        out += Story(n.coverUrl, n.circle, "« ${n.name} »" + de(n) + " a maintenant été n°1 à ${n.timesAtPeak} reprises dans ce classement.")
    }
    // 9. Record personnel : meilleure période historique d'une ligne du chart
    items.filter { it.isPlaysPeak }.minByOrNull { it.position }?.let { r ->
        out += Story(r.coverUrl, r.circle, "« ${r.name} »" + de(r) + " vient de vivre sa meilleure période historique : ${r.plays} écoutes, un record personnel.")
    }
    return out
}

/** Histoires du Global : le rang et le total sont all-time, les mouvements et ajouts d'écoutes sont hebdomadaires. */
private fun buildGlobalStories(st: BillboardUiState): List<Story> {
    val items = st.items
    if (items.isEmpty()) return emptyList()
    val chartName = st.chart.label.removePrefix("Nova ")
    val de: (ChartItem) -> String = {
        if (st.chart == Chart.ARTIST_50) "" else it.secondary?.let { artist -> " de $artist" } ?: ""
    }
    val stories = mutableListOf<Story>()
    val leader = items.first()
    stories += Story(
        leader.coverUrl, leader.circle,
        "« ${leader.name} »" + de(leader) + " mène le Global $chartName avec ${formatCount(leader.plays)} écoutes cumulées."
    )

    items.getOrNull(1)?.let { runnerUp ->
        val gap = (leader.plays - runnerUp.plays).coerceAtLeast(0)
        stories += Story(
            leader.coverUrl, leader.circle,
            "Au cumul all-time, « ${leader.name} » garde ${formatCount(gap)} écoutes d'avance sur « ${runnerUp.name} »."
        )
    }

    items.maxByOrNull { it.variationPlays }?.takeIf { it.variationPlays > 0 }?.let { weeklyLeader ->
        stories += Story(
            weeklyLeader.coverUrl, weeklyLeader.circle,
            "Cette semaine, « ${weeklyLeader.name} »" + de(weeklyLeader) + " ajoute ${formatCount(weeklyLeader.variationPlays)} écoutes; son cumul atteint ${formatCount(weeklyLeader.plays)}."
        )
    }

    st.summary.biggestClimber?.let { climber ->
        val up = climber.movement as? Movement.Up ?: return@let
        stories += Story(
            climber.coverUrl, climber.circle,
            "« ${climber.name} »" + de(climber) + " réalise la plus forte progression hebdomadaire, gagnant ${up.places} place${if (up.places > 1) "s" else ""} jusqu'au #${climber.position}."
        )
    }

    val debutOrReturn = items.filter { it.movement is Movement.New || it.movement is Movement.Reentry }
        .minByOrNull { it.position }
    debutOrReturn?.let { entrant ->
        val isReturn = entrant.movement is Movement.Reentry
        stories += Story(
            entrant.coverUrl, entrant.circle,
            "« ${entrant.name} »" + de(entrant) + if (isReturn) " fait son retour dans le Top Global, à la ${entrant.position}ᵉ place."
            else " fait sa première entrée dans le Top Global, à la ${entrant.position}ᵉ place."
        )
    }

    st.summary.biggestFaller?.let { faller ->
        val down = faller.movement as? Movement.Down ?: return@let
        stories += Story(
            faller.coverUrl, faller.circle,
            "« ${faller.name} »" + de(faller) + " recule le plus cette semaine : ${down.places} place${if (down.places > 1) "s" else ""}, jusqu'au #${faller.position}."
        )
    }

    st.summary.numberOneRun.takeIf { it > 1 }?.let { run ->
        stories += Story(
            leader.coverUrl, leader.circle,
            "« ${leader.name} »" + de(leader) + " conserve la première place du Global depuis ${run} semaines consécutives."
        )
    }
    return stories.take(6)
}

/** Carrousel compact des histoires : swipe, commandes visibles et défilement auto contrôlable. */
@Composable
private fun StoriesCarousel(stories: List<Story>, period: Period) {
    val theme = Nova.theme
    val pagerState = rememberPagerState(pageCount = { stories.size })
    val scope = rememberCoroutineScope()
    var autoAdvancePaused by remember(stories.size) { mutableStateOf(false) }
    var autoAdvanceTarget by remember(pagerState) { mutableIntStateOf(-1) }
    var lastObservedPage by remember(pagerState) { mutableIntStateOf(pagerState.currentPage) }

    // Un geste manuel met en pause le défilement ; le bouton « Auto » permet de le reprendre.
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page != lastObservedPage) {
            if (page != autoAdvanceTarget) autoAdvancePaused = true
            autoAdvanceTarget = -1
            lastObservedPage = page
        }
    }
    LaunchedEffect(pagerState.currentPage, stories.size, autoAdvancePaused) {
        if (stories.size > 1 && !autoAdvancePaused) {
            delay(45_000)
            val nextPage = (pagerState.currentPage + 1) % stories.size
            autoAdvanceTarget = nextPage
            scope.launch { pagerState.animateScrollToPage(nextPage) }
        }
    }
    fun navigateTo(page: Int) {
        autoAdvancePaused = true
        autoAdvanceTarget = -1
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, stories.lastIndex)) }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (period == Period.GLOBAL) "Les coulisses du Global" else "Les coulisses du chart", color = theme.text, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                Text(
                    when (period) {
                        Period.MONTHLY -> "Un fait marquant de ce mois-ci"
                        Period.GLOBAL -> "Cumul all-time · changements de la semaine"
                        else -> "Un fait marquant de cette semaine"
                    },
                    color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
                )
            }
            TextButton(onClick = { autoAdvancePaused = !autoAdvancePaused }, enabled = stories.size > 1) {
                Text(if (autoAdvancePaused) "▶ Auto" else "⏸ Auto", color = theme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        HorizontalPager(state = pagerState, pageSpacing = 8.dp) { page ->
            val story = stories[page]
            Row(
                Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(theme.primary.copy(alpha = 0.18f), NovaColors.Gold.copy(alpha = 0.10f), theme.surface.copy(alpha = 0.9f))))
                    .border(1.dp, NovaColors.Gold.copy(alpha = 0.28f), RoundedCornerShape(16.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(story.coverUrl, story.text, circle = story.circle, size = 84)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("FAIT MARQUANT · ${page + 1}/${stories.size}", color = NovaColors.Gold, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(3.dp))
                    Text(story.text, color = theme.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val cur = pagerState.currentPage
            stories.forEachIndexed { index, _ ->
                Box(Modifier.size(22.dp).clickable { navigateTo(index) }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(if (index == cur) 9.dp else 7.dp).clip(RoundedCornerShape(50))
                            .background(if (index == cur) theme.primary else theme.textSecondary.copy(alpha = 0.5f))
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { navigateTo((cur - 1 + stories.size) % stories.size) }, enabled = stories.size > 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Histoire précédente", tint = theme.text)
            }
            IconButton(onClick = { navigateTo((cur + 1) % stories.size) }, enabled = stories.size > 1) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Histoire suivante", tint = theme.text)
            }
        }
    }
}
