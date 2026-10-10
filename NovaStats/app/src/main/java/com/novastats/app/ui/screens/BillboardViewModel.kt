package com.novastats.app.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.novastats.app.NovaStatsApp
import com.novastats.app.service.AchievementNotifier
import com.novastats.app.data.db.dao.BillboardDao
import com.novastats.app.data.db.dao.PriorRow
import com.novastats.app.domain.BillboardDates
import com.novastats.app.domain.Chart
import com.novastats.app.domain.ChartAppearance
import com.novastats.app.domain.ChartHistory
import com.novastats.app.domain.ChartHistoryStats
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Movement
import com.novastats.app.domain.Period
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Ligne de chart unifiée (titre / artiste / album) pour l'affichage. */
data class ChartItem(
    val entityId: Long,
    val position: Int,
    val name: String,
    val secondary: String?,
    val coverUrl: String?,
    val circle: Boolean,
    val plays: Int,
    /** Durée totale écoutée sur la période (⏱ tri par durées). */
    val durationMs: Long = 0,
    val variationPlays: Int,
    val movement: Movement,
    val periodsInChart: Int,
    val peakPosition: Int,
    val timesAtPeak: Int,
    val isPlaysPeak: Boolean
)

/** Bandeau résumé de la période. */
data class ChartSummary(
    val newEntries: Int = 0,
    val exits: Int = 0,
    val reentries: Int = 0,
    val numberOne: ChartItem? = null,
    val numberOneRun: Int = 0,
    val biggestClimber: ChartItem? = null,
    /** Plus forte régression (chute de places) de la période. */
    val biggestFaller: ChartItem? = null,
    /** Totaux de la période (toutes écoutes) et de la période précédente — pour « ⏱️ 17h 38min ↑ +13 % ». */
    val totalPlays: Int = 0,
    val prevPlays: Int = 0,
    val totalDurationMs: Long = 0,
    val prevDurationMs: Long = 0
)

/** Filtre de mouvement (cases au-dessus du bandeau). */
enum class MovementFilter(val emoji: String, val label: String) {
    UP("↑", "Progressions"), DOWN("↓", "Régressions"), NEW("✨", "Nouveautés"), REENTRY("🔁", "Retours");

    fun matches(m: Movement): Boolean = when (this) {
        UP -> m is Movement.Up
        DOWN -> m is Movement.Down
        NEW -> m is Movement.New
        REENTRY -> m is Movement.Reentry
    }
}

data class BillboardUiState(
    val chart: Chart = Chart.HOT_100,
    val period: Period = Period.WEEKLY,
    val anchor: LocalDate = BillboardDates.latest(Period.WEEKLY, Dates.today()),
    val query: String = "",
    val movementFilter: MovementFilter? = null,
    /** ⏱ true = classement réordonné par durées écoutées (positions recalculées 1..n). */
    val sortDuration: Boolean = false,
    val isCurrent: Boolean = true,
    val hasAnyData: Boolean = false,
    val items: List<ChartItem> = emptyList(),
    val summary: ChartSummary = ChartSummary()
) {
    val limit: Int get() = chart.limit(period)
    val filtered: List<ChartItem>
        get() {
            val base = items.filter { item ->
                (query.isBlank() || item.name.contains(query, ignoreCase = true) || (item.secondary?.contains(query, ignoreCase = true) == true)) &&
                    (movementFilter == null || movementFilter.matches(item.movement))
            }
            // En mode durées : ordre par minutes écoutées, positions réaffichées 1..n (le snapshot reste intact).
            return if (sortDuration) base.sortedByDescending { it.durationMs }.mapIndexed { i, it2 -> it2.copy(position = i + 1) } else base
        }

    /** Compteur par type de mouvement (sur la liste complète, hors recherche). */
    fun count(filter: MovementFilter): Int = items.count { filter.matches(it.movement) }
}

/** 🏅 L'autre moitié de la carte : certifications / Panthéon / Hall of Fame (fiche historique). */
data class EntityExtras(
    val certLines: List<String> = emptyList(),
    val pantheonLabel: String? = null,
    val hofLines: List<String> = emptyList()
)

/** Fiche historique (appui long). */
data class EntityHistory(
    val item: ChartItem,
    val chart: Chart,
    val period: Period,
    val appearances: List<ChartAppearance>,
    val anchors: List<LocalDate>,
    val stats: ChartHistoryStats,
    val extras: EntityExtras = EntityExtras()
)

@OptIn(ExperimentalCoroutinesApi::class)
class BillboardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NovaStatsApp
    private val db = app.database
    private val dao: BillboardDao get() = db.billboardDao()

    private val chart = MutableStateFlow(Chart.HOT_100)
    private val period = MutableStateFlow(Period.WEEKLY)
    private val anchor = MutableStateFlow(BillboardDates.latest(Period.WEEKLY, Dates.today()))
    private val query = MutableStateFlow("")
    private val movementFilter = MutableStateFlow<MovementFilter?>(null)
    private val sortDuration = MutableStateFlow(false)
    private val textAndFilter = combine(query, movementFilter, sortDuration) { q, f, d -> Triple(q, f, d) }

    private val hasAnyData: Flow<Boolean> = db.scrobbleDao().countConfirmedFlow().map { it > 0 }

    private data class Selection(val chart: Chart, val period: Period, val anchor: LocalDate)

    private val selection = combine(chart, period, anchor) { c, p, a -> Selection(c, p, a) }

    /** Lignes du snapshot sélectionné (Flow Room → mise à jour live). */
    private val items: Flow<List<ChartItem>> = selection.flatMapLatest { s -> rowsFor(s.chart, s.period, s.anchor) }

    /** Lignes du snapshot précédent (pour compter les sorties). */
    private val previousItems: Flow<List<ChartItem>> = selection.flatMapLatest { s ->
        rowsFor(s.chart, s.period, BillboardDates.previous(s.period, s.anchor))
    }

    private val summary: Flow<ChartSummary> = combine(items, previousItems, selection) { cur, prev, s ->
        val curIds = cur.map { it.entityId }.toSet()
        val top = cur.firstOrNull()
        val run = top?.let { runAt1(s.chart, s.period, it.entityId, s.anchor) } ?: 0
        val range = BillboardDates.range(s.period, s.anchor)
        val prevRange = BillboardDates.range(s.period, BillboardDates.previous(s.period, s.anchor))
        val dp = db.dailyPlayDao()
        ChartSummary(
            biggestFaller = cur.filter { it.movement is Movement.Down }.maxByOrNull { (it.movement as Movement.Down).places },
            totalPlays = dp.playsBetween(range.fromIso, range.toIso),
            prevPlays = dp.playsBetween(prevRange.fromIso, prevRange.toIso),
            totalDurationMs = dp.durationBetween(range.fromIso, range.toIso),
            prevDurationMs = dp.durationBetween(prevRange.fromIso, prevRange.toIso),
            newEntries = cur.count { it.movement is Movement.New },
            exits = prev.count { it.entityId !in curIds },
            reentries = cur.count { it.movement is Movement.Reentry },
            numberOne = top,
            numberOneRun = run,
            biggestClimber = cur.filter { it.movement is Movement.Up }.maxByOrNull { (it.movement as Movement.Up).places }
        )
    }

    val state: StateFlow<BillboardUiState> = combine(selection, textAndFilter, hasAnyData, items, summary) { s, (q, f, d), any, list, sum ->
        BillboardUiState(
            chart = s.chart, period = s.period, anchor = s.anchor, query = q, movementFilter = f, sortDuration = d,
            isCurrent = BillboardDates.isCurrent(s.period, s.anchor),
            hasAnyData = any, items = list, summary = sum
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillboardUiState())

    private val _history = MutableStateFlow<EntityHistory?>(null)
    val history: StateFlow<EntityHistory?> = _history

    init {
        // Publie les snapshots manquants (nouveau jour / nouvelle semaine) — la période en cours n'a pas de snapshot
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val newInductions = app.billboard.refreshCurrent(collectNewInductions = true)
                if (newInductions.isNotEmpty()) AchievementNotifier.notify(app, newInductions)
            }
        }
    }

    /* ------------------------------ actions ------------------------------ */

    fun selectChart(c: Chart) { chart.value = c }

    fun selectPeriod(p: Period) {
        period.value = p
        anchor.value = BillboardDates.latest(p, Dates.today())
    }

    fun previousPeriod() { anchor.value = BillboardDates.previous(period.value, anchor.value) }

    fun nextPeriod() {
        if (!BillboardDates.isCurrent(period.value, anchor.value)) anchor.value = BillboardDates.next(period.value, anchor.value)
    }

    fun goToDate(date: LocalDate) {
        val latest = BillboardDates.latest(period.value, Dates.today())
        anchor.value = minOf(BillboardDates.anchor(period.value, date), latest)
    }

    fun search(q: String) { query.value = q }

    /** ▶ écoutes / ⏱ durées : bascule l'ordre du classement. */
    fun toggleSort() { sortDuration.value = !sortDuration.value }

    /** Appui sur une case : active le filtre, ré-appui : le retire. */
    fun toggleMovementFilter(f: MovementFilter) { movementFilter.value = if (movementFilter.value == f) null else f }

    fun openHistory(item: ChartItem) {
        val c = chart.value; val p = period.value
        viewModelScope.launch {
            val (rows, extras) = withContext(Dispatchers.IO) { historyRows(c, p, item.entityId) to loadExtras(c, item.entityId) }
            val appearances = rows.map { ChartAppearance(Dates.parse(it.date), it.position, it.playCount) }
            val first = appearances.minOfOrNull { it.date } ?: anchor.value
            val anchors = BillboardDates.allAnchors(p, first, Dates.today())
            _history.value = EntityHistory(item, c, p, appearances, anchors, ChartHistory.stats(appearances, anchors), extras)
        }
    }

    /** 🏅 Certifications, statut Panthéon, intronisations Hall of Fame de l'entité. */
    private suspend fun loadExtras(c: Chart, id: Long): EntityExtras {
        val type = c.entityType
        val certFmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.FRANCE)
        val certLines = if (type != "ARTIST") db.certificationDao().history(id, type).map { hst ->
            val lvl = com.novastats.app.domain.CertLevel.entries.firstOrNull { it.dbName == hst.level }
            "${lvl?.emoji ?: "🏅"} ${lvl?.label ?: hst.level}${if (hst.multiplier > 1) " ×${hst.multiplier}" else ""} — ${certFmt.format(java.util.Date(hst.certifiedAt))}"
        } else emptyList()
        val pantheonLabel = if (type == "ARTIST")
            db.artistDao().byIds(listOf(id)).firstOrNull()?.pantheonStatus
                ?.let { com.novastats.app.domain.PantheonStatus.fromDb(it) }?.let { "${it.emoji} ${it.label}" }
        else null
        val hofLines = db.hallOfFameDao().ofEntity(id, type).map { e ->
            when (e.entryType) {
                "DIRECT_DEBUT" -> "⚡ Entrée directe"
                "LONG_RUN" -> "👑 Long règne (${e.weeksAt1} ${BillboardDates.unitLabel(Period.WEEKLY, e.weeksAt1)})"
                "TRIPLE_DEBUT" -> "🚀 Triple début"
                else -> "🌟 Règne légendaire"
            } + " — ${e.periodType}"
        }
        return EntityExtras(certLines, pantheonLabel, hofLines)
    }

    private val _compare = MutableStateFlow<EntityHistory?>(null)
    val compare: StateFlow<EntityHistory?> = _compare

    /** ⚔️ Seconde courbe : charge l'historique de l'entité choisie. */
    fun compareWith(item: ChartItem) {
        val c = chart.value; val p = period.value
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { historyRows(c, p, item.entityId) }
            val appearances = rows.map { ChartAppearance(Dates.parse(it.date), it.position, it.playCount) }
            val first = appearances.minOfOrNull { it.date } ?: anchor.value
            val anchors = BillboardDates.allAnchors(p, first, Dates.today())
            _compare.value = EntityHistory(item, c, p, appearances, anchors, ChartHistory.stats(appearances, anchors))
        }
    }

    fun clearCompare() { _compare.value = null }

    /** 📤 Rend la carte du parcours (+ face-à-face) et ouvre la feuille de partage. */
    fun shareHistory(theme: com.novastats.app.ui.theme.NovaTheme) {
        val h = _history.value ?: return
        val cmp = _compare.value
        viewModelScope.launch {
            runCatching {
                val f = withContext(Dispatchers.IO) { com.novastats.app.ui.share.ShareCards.renderChartStory(app, h, cmp, theme) }
                com.novastats.app.ui.share.ShareCards.share(app, f)
            }
        }
    }

    fun closeHistory() { _history.value = null }

    /* ------------------------------ data ------------------------------ */

    private fun rowsFor(chart: Chart, period: Period, anchor: LocalDate): Flow<List<ChartItem>> =
        dao.observeSnapshotId(period.dbName, anchor.format(Dates.ISO)).flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else when (chart) {
                Chart.HOT_100 -> dao.trackRows(id).map { rows ->
                    rows.map { r ->
                        ChartItem(
                            r.row.trackId, r.row.position, r.title, r.artistName, r.coverUrl, false,
                            r.row.playCount, r.row.totalDurationMs, r.row.variationPlays,
                            Movement.of(r.row.isNew, r.row.isReentry, r.row.previousPosition, r.row.position),
                            r.row.daysInChart, r.row.peakPosition, r.row.timesAtPeak, r.row.isPlaysPeak
                        )
                    }
                }
                Chart.ARTIST_50 -> dao.artistRows(id).map { rows ->
                    rows.map { r ->
                        val status = r.pantheonStatus?.let { com.novastats.app.domain.PantheonStatus.fromDb(it) }?.let { "${it.emoji} ${it.label}" }
                        ChartItem(
                            r.row.artistId, r.row.position, r.name, status, r.photoUrl, true,
                            r.row.playCount, r.row.totalDurationMs, r.row.variationPlays,
                            Movement.of(r.row.isNew, r.row.isReentry, r.row.previousPosition, r.row.position),
                            r.row.daysInChart, r.row.peakPosition, r.row.timesAtPeak, r.row.isPlaysPeak
                        )
                    }
                }
                Chart.ALBUMS_75 -> dao.albumRows(id).map { rows ->
                    rows.map { r ->
                        ChartItem(
                            r.row.albumId, r.row.position, r.title, r.artistName, r.coverUrl, false,
                            r.row.playCount, r.row.totalDurationMs, r.row.variationPlays,
                            Movement.of(r.row.isNew, r.row.isReentry, r.row.previousPosition, r.row.position),
                            r.row.daysInChart, r.row.peakPosition, r.row.timesAtPeak, r.row.isPlaysPeak
                        )
                    }
                }
            }
        }

    private suspend fun historyRows(chart: Chart, period: Period, id: Long): List<PriorRow> = when (chart) {
        Chart.HOT_100 -> dao.trackHistory(period.dbName, id)
        Chart.ARTIST_50 -> dao.artistHistory(period.dbName, id)
        Chart.ALBUMS_75 -> dao.albumHistory(period.dbName, id)
    }

    /** Depuis combien de périodes consécutives (jusqu'à [anchor] inclus) l'entité est #1. */
    private suspend fun runAt1(chart: Chart, period: Period, id: Long, anchor: LocalDate): Int {
        val rows = historyRows(chart, period, id).filter { it.date <= anchor.format(Dates.ISO) }
        if (rows.isEmpty()) return 0
        val appearances = rows.map { ChartAppearance(Dates.parse(it.date), it.position, it.playCount) }
        val anchors = BillboardDates.allAnchors(period, appearances.minOf { it.date }, anchor)
        return ChartHistory.stats(appearances, anchors).currentRunAt1
    }
}
