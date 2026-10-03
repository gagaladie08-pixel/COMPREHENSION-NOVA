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
import com.novastats.app.domain.BillboardDates
import com.novastats.app.domain.Chart
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Movement
import com.novastats.app.domain.Period
import com.novastats.app.ui.theme.Nova
import com.novastats.app.ui.theme.NovaColors
import java.time.Instant
import java.time.ZoneOffset

private val Bronze = Color(0xFFCD7F32)

/**
 * 🏆 Billboard — Nova Hot 100 · Nova Artist 50 · Nova 75 Albums.
 * Classements figés par période (snapshots), navigation dans l'historique, fiche détaillée par appui long.
 * Périodes + bandeau défilent avec la liste ; la rangée des charts reste collée en haut. Top 25 + « Voir plus » (+20).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BillboardScreen(vm: BillboardViewModel = viewModel()) {
    val theme = Nova.theme
    val state by vm.state.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }

    var searching by remember { mutableStateOf(false) }

    val rows = state.filtered
    val limit = state.limit
    val unit = BillboardDates.unitLabel(state.period, 2)
    // Lignes à afficher : uniquement le classement réel (plus de places vides « — »)
    val totalRows = rows.size
    // Top 25 → « Voir plus » (+20) ; remis à 25 à chaque changement de chart / période / date / recherche
    var visible by remember(state.chart, state.period, state.anchor, state.query) { mutableIntStateOf(TOP_INITIAL) }
    val shown = minOf(visible, totalRows)
    val listState = rememberLazyListState()
    LaunchedEffect(state.period, state.anchor) { listState.scrollToItem(0) }

    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        /* ---------- En-tête défilant : périodes → navigation → bandeau ---------- */
        item(key = "header") {
            Column(Modifier.fillMaxWidth()) {
                // 1. Périodes
                PeriodSegment(state.period) { vm.selectPeriod(it) }

                // 2. Navigation dans l'historique (appui long sur la date → calendrier)
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::previousPeriod) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Période précédente", tint = theme.text) }
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                            .combinedClickable(onClick = {}, onLongClick = { showPicker = true })
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            BillboardDates.label(state.period, state.anchor), color = theme.text, fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (state.isCurrent) {
                            Spacer(Modifier.width(8.dp))
                            Badge("LIVE", NovaColors.Down)
                        }
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
                            Box(Modifier.tabIndicatorOffset(positions[chartIndex]).padding(horizontal = 24.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(theme.primary))
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
            items(rows.subList(0, realShown), key = { it.entityId }) { item ->
                ChartRow(item, state.period, onLongPress = { vm.openHistory(item) })
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

    history?.let { ChartHistoryDialog(it, onDismiss = vm::closeHistory) }
}

/* ------------------------------------------------------------------------- */

@Composable
private fun SummaryBanner(state: BillboardUiState) {
    val theme = Nova.theme
    val s = state.summary
    val cells = listOf(
        StripCell("${state.items.size}", "Classés"),
        StripCell("${s.newEntries}", "Nouveautés", NovaColors.DirectDebut),
        StripCell("${s.exits}", "Sorties", NovaColors.Down),
        StripCell("${s.reentries}", "Retours", theme.secondary)
    )
    val hasDetails = state.items.isNotEmpty() && (s.numberOne != null || s.biggestClimber != null || s.totalPlays > 0)
    val details: (@Composable () -> Unit)? = if (!hasDetails) null else {
        {
            // ⏱️ Temps d'écoute et ▶ écoutes de la période, variation vs période précédente (maquette utilisateur)
            if (s.totalPlays > 0 || s.prevPlays > 0) {
                TrendLine("⏱️", formatDuration(s.totalDurationMs), s.totalDurationMs.toDouble(), s.prevDurationMs.toDouble(), formatDuration(s.prevDurationMs))
                TrendLine("▶", "${formatCount(s.totalPlays)} écoutes", s.totalPlays.toDouble(), s.prevPlays.toDouble(), "${formatCount(s.prevPlays)} écoutes")
                Spacer(Modifier.height(4.dp))
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
    val runWord = when (state.period) { Period.DAILY -> "Day"; Period.WEEKLY -> "Week"; Period.MONTHLY -> "Month"; Period.YEARLY -> "Year"; Period.GLOBAL -> "Period" }
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
            Text("${one.plays}× écoutes", color = theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("👑 $runWord $run", color = theme.accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** « ⏱️ 17h 38min ↑ +13 % » + « vs période précédente : 15h 29min ». */
@Composable
private fun TrendLine(emoji: String, value: String, cur: Double, prev: Double, prevLabel: String) {
    val theme = Nova.theme
    val pct = if (prev <= 0.0) null else ((cur - prev) / prev * 100).toInt()
    val up = pct == null || pct >= 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$emoji ", style = MaterialTheme.typography.titleMedium)
        Text(value, color = NovaColors.Gold, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            when { pct == null -> if (cur > 0) "nouveau" else ""; pct >= 0 -> "↑ +$pct %"; else -> "↓ $pct %" },
            color = if (up) NovaColors.Up else NovaColors.Down, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium
        )
    }
    Text("vs période précédente : $prevLabel", color = theme.textSecondary, style = MaterialTheme.typography.bodySmall)
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
private fun ChartRow(item: ChartItem, period: Period, onLongPress: () -> Unit) {
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

    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (isOne) Modifier.padding(horizontal = 8.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, NovaColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(14.dp)) else Modifier)
            .background(bg)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = if (isOne) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Position + mouvement
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("#${item.position}", color = posColor, fontWeight = FontWeight.Black, fontSize = if (isOne) 22.sp else 16.sp)
            when (val m = item.movement) {
                Movement.New -> Badge("NEW", NovaColors.DirectDebut)
                Movement.Reentry -> Badge("↩ RE", theme.secondary)
                else -> Text(m.label(), color = mColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
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
            // PEAK = nouveau record de POSITION (meilleure place jamais atteinte, pour la 1re fois) ; NP = nouveau record d'ÉCOUTES
            val positionPeak = m !is Movement.New && item.position == item.peakPosition && item.timesAtPeak <= 1
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (positionPeak) { Badge("PEAK", NovaColors.Up); Spacer(Modifier.width(4.dp)) }
                Text("${formatCount(item.plays)} ▶", color = if (isOne) NovaColors.Gold else theme.primary, fontWeight = FontWeight.Bold, fontSize = if (isOne) 17.sp else 15.sp)
            }
            if (item.isPlaysPeak) Text("NP", color = NovaColors.Up, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            if (m !is Movement.New && m !is Movement.Reentry) {
                val v = item.variationPlays
                val (txt, col) = when {
                    v > 0 -> "+$v" to NovaColors.Up
                    v < 0 -> "$v" to NovaColors.Down
                    else -> "=" to NovaColors.Neutral
                }
                Text(txt, color = col, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

