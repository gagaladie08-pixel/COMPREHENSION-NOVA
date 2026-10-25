package com.novastats.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.novastats.app.ui.theme.prideFlagFor
import com.novastats.app.ui.theme.isPride
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.dao.PeriodSummary
import com.novastats.app.data.db.dao.StatsTrendBucket
import com.novastats.app.domain.DateRange
import com.novastats.app.domain.Dates
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.Period
import com.novastats.app.domain.StatsTopNRecordRules
import com.novastats.app.domain.TitleNormalizer
import androidx.compose.runtime.produceState
import kotlinx.coroutines.flow.first
import com.novastats.app.ui.theme.Nova
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val statsTabs = listOf("Titres", "Artistes", "Albums")
private const val TOP_LIMIT = StatsTopNRecordRules.STATS_TOP_LIMIT

/**
 * 📊 Stats — 3 sous-onglets Titres / Artistes / Albums + icône recherche (temps réel, portée = sous-onglet actif).
 * Périodes : jour · semaine calendaire en cours · mois · année · global. Résumé compact + courbe temporelle
 * interactive (heure, jour ou mois selon la période), puis classement Titres / Artistes / Albums.
 * Top 300, tri écoutes puis temps cumulé. Appui long sur une ligne → popup de détail sur la période choisie.
 *
 * Mise en page : périodes et résumé défilent; les sous-onglets, la recherche et le tri restent collés en haut.
 * Classement : Top 25 affiché, bouton « Voir plus » = +20 à chaque appui (jusqu'à 300).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatsScreen() {
    val app = LocalContext.current.applicationContext as NovaStatsApp
    val db = app.database
    val theme = Nova.theme

    var tab by remember { mutableIntStateOf(0) }
    var period by remember { mutableStateOf(Period.WEEKLY) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    var versus by remember { mutableStateOf(false) }
    val range = remember(period) { Dates.statsRangeFor(period) }

    val summary by remember(range) { db.scrobbleDao().summary(range.fromMs, range.toMs) }
        .collectAsStateWithLifecycle(initialValue = PeriodSummary(0L, 0L, 0, 0, 0, 0))

    val tracks by remember(period) {
        if (period == Period.GLOBAL) db.trackDao().topAllTime(TOP_LIMIT) else db.trackDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val artists by remember(period) {
        if (period == Period.GLOBAL) db.artistDao().topAllTime(TOP_LIMIT) else db.artistDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val albums by remember(period) {
        if (period == Period.GLOBAL) db.albumDao().topAllTime(TOP_LIMIT) else db.albumDao().topForPeriod(range.fromIso, range.toIso, TOP_LIMIT)
    }.collectAsStateWithLifecycle(initialValue = emptyList())



    val q = TitleNormalizer.normalizeKey(query)
    fun matches(vararg fields: String?) = q.isBlank() || fields.any { it != null && TitleNormalizer.normalizeKey(it).contains(q) }

    DetailPopupHost(detail) { detail = null }

    // ⚔️ Comparer deux titres / artistes (écran plein, retour = Stats)
    if (versus) {
        BackHandler { versus = false }
        VersusScreen { versus = false }
        return
    }

    // ▶ écoutes / ⏱ durées : même classement, autre ordre — le tri par minutes révèle d'autres gagnants.
    var byDuration by remember { mutableStateOf(false) }
    var trendMetric by remember { mutableStateOf(StatsTrendMetric.PLAYS) }
    var selectedTrendIndex by remember(period) { mutableIntStateOf(-1) }

    val trendEnd = minOf(range.to, Dates.today())
    val trendToIso = trendEnd.format(Dates.ISO)
    val trendBuckets by remember(period, range, trendToIso) {
        when (period) {
            Period.DAILY -> db.scrobbleDao().hourlyStatsTrend(range.fromMs, range.toMs)
            Period.WEEKLY, Period.MONTHLY -> db.dailyStatsDao().dayStatsTrend(range.fromIso, trendToIso)
            Period.YEARLY, Period.GLOBAL -> db.dailyStatsDao().monthStatsTrend(range.fromIso, trendToIso)
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val trendPoints = remember(period, range, trendBuckets, trendEnd) {
        buildStatsTrendPoints(period, range, trendBuckets, trendEnd)
    }
    val trendValues = remember(trendPoints, trendMetric) {
        trendPoints.map { point ->
            if (trendMetric == StatsTrendMetric.PLAYS) point.playCount.toFloat()
            else point.totalDurationMs / 3_600_000f
        }
    }
    val trendMax = trendValues.maxOrNull() ?: 0f
    val trendGrid = when {
        trendMax <= 0f -> listOf(0f)
        trendMetric == StatsTrendMetric.PLAYS && trendMax < 4f -> listOf(0f, trendMax)
        else -> listOf(0f, trendMax / 2f, trendMax).distinct()
    }
    val defaultTrendIndex = if (period == Period.DAILY) java.time.LocalTime.now(Dates.zone).hour else trendPoints.lastIndex
    val effectiveTrendIndex = selectedTrendIndex.takeIf { it in trendPoints.indices }
        ?: defaultTrendIndex.coerceIn(0, (trendPoints.lastIndex).coerceAtLeast(0))
    val dominanceInsight = remember(q, tracks, summary) {
        if (q.isNotBlank() || summary.playCount <= 0L) null
        else tracks.firstOrNull()?.let { top ->
            val share = (top.periodPlays.toLong() * 100L / summary.playCount).toInt()
            if (share >= 10) "🎯 « ${top.track.title} » pèse $share % de tes écoutes sur la période" else null
        }
    }

    val filteredTracks = remember(tracks, q, byDuration) { (if (byDuration) tracks.sortedByDescending { it.periodDurationMs } else tracks).mapIndexed { i, t -> i + 1 to t }.filter { (_, t) -> matches(t.track.title, t.artistName, t.albumTitle) } }
    val filteredArtists = remember(artists, q, byDuration) { (if (byDuration) artists.sortedByDescending { it.periodDurationMs } else artists).mapIndexed { i, a -> i + 1 to a }.filter { (_, a) -> matches(a.artist.name) } }
    val filteredAlbums = remember(albums, q, byDuration) { (if (byDuration) albums.sortedByDescending { it.periodDurationMs } else albums).mapIndexed { i, al -> i + 1 to al }.filter { (_, al) -> matches(al.album.title, al.artistName) } }

    val total = when (tab) { 0 -> filteredTracks.size; 1 -> filteredArtists.size; else -> filteredAlbums.size }
    // Le podium met en avant le top 3 : la liste démarre alors à la 4ᵉ place (pas de doublon).
    val podiumOn = q.isBlank() && total >= 3
    val listTotal = if (podiumOn) total - 3 else total
    // Top 25 → « Voir plus » (+20) ; remis à 25 à chaque changement de sous-onglet / période / recherche
    var visible by remember(tab, period, q) { mutableIntStateOf(TOP_INITIAL) }
    val shown = minOf(visible, listTotal)

    val listState = rememberLazyListState()
    // Changement de période : on remonte en haut pour revoir le bandeau
    LaunchedEffect(period) { listState.scrollToItem(0) }

    // 🎨 Photo de ton artiste n°1 sur la période, en fond d'écran
    val artUrl by produceState<String?>(initialValue = null, period) {
        val r = Dates.statsRangeFor(period)
        value = runCatching { db.artistDao().topForPeriod(r.fromIso, r.toIso, 1).first().firstOrNull()?.artist?.photoUrl }.getOrNull()
    }

    ScreenBackdrop(artUrl) {
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        /* ---------- Résumé compact : périodes → deux chiffres clés → repères secondaires ---------- */
        item(key = "header") {
            Column(Modifier.fillMaxWidth()) {
                PeriodSegment(period) { period = it }
                val days = if (period == Period.GLOBAL) summary.activeDays else elapsedDays(range)
                val avg = if (days > 0) summary.playCount.toFloat() / days else 0f
                SummaryStrip(
                    cells = listOf(
                        StripCell(formatCount(summary.playCount), "Écoutes"),
                        StripCell(formatDuration(summary.totalDurationMs), "Temps écouté")
                    ),
                    content = {
                        Text(
                            "${formatCount(summary.distinctTracks)} titres · ${formatCount(summary.distinctArtists)} artistes · ${formatAverage(avg)} écoutes/j",
                            color = theme.textSecondary, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Text(
                    periodCaption(period, range),
                    color = theme.textSecondary.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), textAlign = TextAlign.Center
                )
            }
        }

        /* ---------- Courbe d'activité, action de comparaison intégrée à l'en-tête ---------- */
        item(key = "trend") {
            StatsTrendCard(
                period = period,
                points = trendPoints,
                values = trendValues,
                gridValues = trendGrid,
                metric = trendMetric,
                selectedIndex = effectiveTrendIndex,
                dominanceInsight = dominanceInsight,
                onMetricChange = { trendMetric = it },
                onPointSelected = { selectedTrendIndex = it },
                onCompare = { versus = true }
            )
        }

        /* ---------- Sous-onglets collants : Titres / Artistes / Albums + loupe (+ champ de recherche) ---------- */
        stickyHeader(key = "tabs") {
            Column(Modifier.fillMaxWidth().background(theme.background)) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TabRow(
                        selectedTabIndex = tab,
                        modifier = Modifier.weight(1f),
                        containerColor = theme.background,
                        contentColor = theme.primary,
                        indicator = { positions ->
                            Box(Modifier.tabIndicatorOffset(positions[tab]).padding(horizontal = 28.dp).height(if (Nova.isPride) 5.dp else 3.dp).clip(RoundedCornerShape(2.dp)).background(if (Nova.isPride) Brush.horizontalGradient(prideFlagFor(tab + 1)) else SolidColor(theme.primary)))
                        },
                        divider = {}
                    ) {
                        statsTabs.forEachIndexed { i, label ->
                            Tab(
                                selected = tab == i, onClick = { tab = i },
                                text = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.titleSmall) },
                                selectedContentColor = theme.primary, unselectedContentColor = theme.textSecondary
                            )
                        }
                    }
                    Box(Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) CloseIcon else SearchIcon, contentDescription = "Rechercher", tint = if (searching || query.isNotBlank()) theme.primary else theme.textSecondary)
                        }
                    }
                }
                if (searching) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        singleLine = true,
                        placeholder = { Text("Rechercher dans ${statsTabs[tab].lowercase()}…", color = theme.textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.primary, unfocusedBorderColor = theme.textSecondary.copy(alpha = 0.4f),
                            focusedTextColor = theme.text, unfocusedTextColor = theme.text, cursorColor = theme.primary
                        )
                    )
                }
                SortToggle(byDuration) { byDuration = it }
                Spacer(Modifier.height(2.dp))
            }
        }

        /* ---------- Podium 🥇🥈🥉 (masqué pendant une recherche) ---------- */
        if (podiumOn) {
            item(key = "podium") {
                PodiumRow(
                    when (tab) {
                        0 -> filteredTracks.take(3).map { (pos, t) ->
                            PodiumItem(pos, t.track.title, t.artistName, t.periodPlays, t.track.coverUrl, false) { detail = DetailTarget.Track(t.track.trackId, period) }
                        }
                        1 -> filteredArtists.take(3).map { (pos, a) ->
                            PodiumItem(pos, a.artist.name, null, a.periodPlays, a.artist.photoUrl, true) { detail = DetailTarget.Artist(a.artist.artistId, period) }
                        }
                        else -> filteredAlbums.take(3).map { (pos, al) ->
                            PodiumItem(pos, al.album.title, al.artistName, al.periodPlays, al.album.coverUrl, false) { detail = DetailTarget.Album(al.album.albumId, period) }
                        }
                    }
                )
            }
        }

        /* ---------- Classement ---------- */
        if (total == 0) {
            item(key = "empty") {
                Box(Modifier.fillParentMaxHeight(0.6f)) {
                    if (q.isNotBlank()) EmptyState("🔍", "Aucun résultat pour « $query »", "dans le Top $TOP_LIMIT ${statsTabs[tab].lowercase()} de la période")
                    else EmptyState("📊", "Aucune écoute sur cette période", "Ton classement s'enrichit à chaque écoute")
                }
            }
        } else when (tab) {
            // Liste démarrée après le podium (drop 3) quand il est affiché ; les positions restent réelles.
            0 -> itemsIndexed((if (podiumOn) filteredTracks.drop(3) else filteredTracks).take(shown), key = { _, (_, t) -> "t" + t.track.trackId }) { _, (pos, t) ->
                RankRow(
                    pos, t.track.title, listOfNotNull(t.artistName, t.albumTitle).joinToString(" · "), t.periodPlays, t.periodDurationMs, t.track.coverUrl,
                    onLongClick = { detail = DetailTarget.Track(t.track.trackId, period) }
                )
            }
            1 -> itemsIndexed((if (podiumOn) filteredArtists.drop(3) else filteredArtists).take(shown), key = { _, (_, a) -> "a" + a.artist.artistId }) { _, (pos, a) ->
                val sub = PantheonStatus.fromDb(a.artist.pantheonStatus)?.let { "${it.emoji} ${it.label.uppercase()}" }
                RankRow(
                    pos, a.artist.name, sub, a.periodPlays, a.periodDurationMs, a.artist.photoUrl, circle = true,
                    onLongClick = { detail = DetailTarget.Artist(a.artist.artistId, period) }
                )
            }
            else -> itemsIndexed((if (podiumOn) filteredAlbums.drop(3) else filteredAlbums).take(shown), key = { _, (_, al) -> "al" + al.album.albumId }) { _, (pos, al) ->
                RankRow(
                    pos, al.album.title, al.artistName, al.periodPlays, al.periodDurationMs, al.album.coverUrl,
                    onLongClick = { detail = DetailTarget.Album(al.album.albumId, period) }
                )
            }
        }
        if (listTotal > shown) item(key = "more") { LoadMoreButton(listTotal - shown) { visible += TOP_STEP } }
        item(key = "bottom") { Box(Modifier.height(24.dp)) }
    }
    }

}

private enum class StatsTrendMetric { PLAYS, DURATION }

private data class StatsTrendPoint(
    val detailLabel: String,
    val axisLabel: String?,
    val playCount: Long,
    val totalDurationMs: Long
)

private fun buildStatsTrendPoints(
    period: Period,
    range: DateRange,
    buckets: List<StatsTrendBucket>,
    today: java.time.LocalDate
): List<StatsTrendPoint> {
    val byBucket = buckets.associateBy { it.bucket }
    fun point(bucket: String, detailLabel: String, axisLabel: String?) = StatsTrendPoint(
        detailLabel = detailLabel,
        axisLabel = axisLabel,
        playCount = byBucket[bucket]?.playCount ?: 0L,
        totalDurationMs = byBucket[bucket]?.totalDurationMs ?: 0L
    )
    fun monthPoints(start: YearMonth, end: YearMonth): List<StatsTrendPoint> {
        if (end.isBefore(start)) return emptyList()
        val count = ChronoUnit.MONTHS.between(start, end).toInt() + 1
        return (0 until count).map { index ->
            val month = start.plusMonths(index.toLong())
            val bucket = month.toString()
            val axisLabel = if (index == 0 || index == (count - 1) / 2 || index == count - 1) {
                month.format(DateTimeFormatter.ofPattern(if (period == Period.GLOBAL) "MMM yy" else "MMM", Locale.FRANCE))
            } else null
            val detail = month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE))
                .replaceFirstChar { it.titlecase(Locale.FRANCE) }
            point(bucket, detail, axisLabel)
        }
    }

    return when (period) {
        Period.DAILY -> (0..23).map { hour ->
            val bucket = hour.toString().padStart(2, '0')
            val axisLabel = if (hour == 0 || hour == 11 || hour == 23) "$bucket h" else null
            point(bucket, "$bucket h", axisLabel)
        }
        Period.WEEKLY, Period.MONTHLY -> {
            val end = minOf(range.to, today)
            if (end.isBefore(range.from)) emptyList()
            else {
                val count = ChronoUnit.DAYS.between(range.from, end).toInt() + 1
                (0 until count).map { index ->
                    val date = range.from.plusDays(index.toLong())
                    val axisLabel = if (index == 0 || index == (count - 1) / 2 || index == count - 1) {
                        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRANCE))
                    } else null
                    val detail = date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRANCE))
                        .replaceFirstChar { it.titlecase(Locale.FRANCE) }
                    point(date.format(Dates.ISO), detail, axisLabel)
                }
            }
        }
        Period.YEARLY -> monthPoints(YearMonth.from(range.from), YearMonth.from(minOf(range.to, today)))
        Period.GLOBAL -> {
            val end = YearMonth.from(minOf(range.to, today))
            val start = buckets.mapNotNull { runCatching { YearMonth.parse(it.bucket) }.getOrNull() }.minOrNull()
            if (start == null) emptyList() else monthPoints(start, end)
        }
    }
}

@Composable
private fun StatsTrendCard(
    period: Period,
    points: List<StatsTrendPoint>,
    values: List<Float>,
    gridValues: List<Float>,
    metric: StatsTrendMetric,
    selectedIndex: Int,
    dominanceInsight: String?,
    onMetricChange: (StatsTrendMetric) -> Unit,
    onPointSelected: (Int) -> Unit,
    onCompare: () -> Unit
) {
    val theme = Nova.theme
    val selectedPoint = points.getOrNull(selectedIndex)
    val hasActivity = points.any { it.playCount > 0L }

    NovaCard {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Évolution dans le temps", color = theme.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when (period) {
                            Period.DAILY -> "Par heure"
                            Period.WEEKLY, Period.MONTHLY -> "Par jour"
                            Period.YEARLY, Period.GLOBAL -> "Par mois"
                        },
                        color = theme.textSecondary, style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(theme.primary.copy(alpha = 0.12f))
                        .clickable(onClick = onCompare).padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚔️", fontSize = 14.sp)
                    Spacer(Modifier.width(5.dp))
                    Text("Comparer", color = theme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaFilterChip(
                    selected = metric == StatsTrendMetric.PLAYS,
                    onClick = { onMetricChange(StatsTrendMetric.PLAYS) },
                    label = { Text("Écoutes") }, flagKey = "stats-trend-plays"
                )
                NovaFilterChip(
                    selected = metric == StatsTrendMetric.DURATION,
                    onClick = { onMetricChange(StatsTrendMetric.DURATION) },
                    label = { Text("Durée") }, flagKey = "stats-trend-duration"
                )
            }
            if (hasActivity) selectedPoint?.let { point ->
                Text(
                    "${point.detailLabel} · ${formatCount(point.playCount)} écoutes · ${formatDuration(point.totalDurationMs)}",
                    color = theme.textSecondary, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }
            if (hasActivity && values.isNotEmpty()) {
                NovaCurveChart(
                    values = values,
                    modifier = Modifier.fillMaxWidth().height(142.dp),
                    color = theme.primary,
                    yLabel = { value -> formatTrendAxisValue(metric, value) },
                    gridValues = gridValues,
                    showPoints = points.size <= 12,
                    xLabels = points.map { it.axisLabel },
                    selectedIndex = selectedIndex.takeIf { it in points.indices },
                    onPointSelected = onPointSelected
                )
            } else {
                Text(
                    "Le graphique apparaîtra dès que cette période contiendra des écoutes.",
                    color = theme.textSecondary, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = TextAlign.Center
                )
            }
            if (dominanceInsight != null) {
                Text(
                    dominanceInsight, color = theme.textSecondary, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTrendAxisValue(metric: StatsTrendMetric, value: Float): String = when (metric) {
    StatsTrendMetric.PLAYS -> when {
        value >= 1_000_000f -> "${(value / 1_000_000f).roundToInt()}M"
        value >= 1_000f -> "${(value / 1_000f).roundToInt()}k"
        else -> value.roundToInt().toString()
    }
    StatsTrendMetric.DURATION -> {
        val minutes = (value * 60f).roundToInt()
        when {
            minutes < 60 -> "${minutes}m"
            value < 10f -> "${String.format(Locale.FRANCE, "%.1f", value).removeSuffix(",0")}h"
            else -> "${value.roundToInt()}h"
        }
    }
}

private data class PodiumItem(
    val pos: Int, val title: String, val subtitle: String?, val plays: Int,
    val coverUrl: String?, val circle: Boolean, val onClick: () -> Unit
)

/** 🥇🥈🥉 Le top 3 en cartes, ordre podium [2ᵉ, 1ᵉʳ, 3ᵉ] — le n°1 plus grand au centre. */
@Composable
private fun PodiumRow(items: List<PodiumItem>) {
    val theme = Nova.theme
    if (items.isEmpty()) return
    val order = listOfNotNull(items.getOrNull(1), items.getOrNull(0), items.getOrNull(2))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom
    ) {
        order.forEach { it ->
            val first = it.pos == 1
            Column(
                Modifier.weight(1f).clickable { it.onClick() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (first) "🥇" else if (it.pos == 2) "🥈" else "🥉", fontSize = if (first) 26.sp else 20.sp)
                Spacer(Modifier.height(4.dp))
                CoverArt(it.coverUrl, it.title, circle = it.circle, size = if (first) 84 else 64)
                Spacer(Modifier.height(6.dp))
                Text(
                    it.title, color = theme.text, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = if (first) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
                Text("${formatCount(it.plays)} ▶", color = theme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun elapsedDays(range: com.novastats.app.domain.DateRange): Int {
    val end = minOf(range.to, Dates.today())
    return (java.time.temporal.ChronoUnit.DAYS.between(range.from, end) + 1).toInt().coerceAtLeast(1)
}

/* Icônes vectorielles minimalistes (pas de dépendance material-icons-extended) */
private val SearchIcon: ImageVector by lazy {
    ImageVector.Builder("search", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)) {
            moveTo(15.5f, 14f); horizontalLineToRelative(-0.79f); lineToRelative(-0.28f, -0.27f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f); curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f); reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
            reflectiveCurveTo(5.91f, 16f, 9.5f, 16f); curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f); lineToRelative(0.27f, 0.28f); verticalLineToRelative(0.79f)
            lineToRelative(5f, 4.99f); lineTo(20.49f, 19f); lineToRelative(-4.99f, -5f); close()
            moveTo(9.5f, 14f); curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f); reflectiveCurveTo(7.01f, 5f, 9.5f, 5f); reflectiveCurveTo(14f, 7.01f, 14f, 9.5f); reflectiveCurveTo(11.99f, 14f, 9.5f, 14f); close()
        }
    }.build()
}

private val CloseIcon: ImageVector by lazy {
    ImageVector.Builder("close", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.White)) {
            moveTo(19f, 6.41f); lineTo(17.59f, 5f); lineTo(12f, 10.59f); lineTo(6.41f, 5f); lineTo(5f, 6.41f); lineTo(10.59f, 12f)
            lineTo(5f, 17.59f); lineTo(6.41f, 19f); lineTo(12f, 13.41f); lineTo(17.59f, 19f); lineTo(19f, 17.59f); lineTo(13.41f, 12f); close()
        }
    }.build()
}
