package com.novastats.app.data.repository

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.PriorRow
import com.novastats.app.data.db.dao.RankedEntry
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.HallOfFameBadgeEntity
import com.novastats.app.data.db.entity.HallOfFameEntity
import com.novastats.app.data.db.entity.NotificationFeedEntity
import com.novastats.app.data.db.entity.SnapshotAlbumEntity
import com.novastats.app.data.db.entity.SnapshotArtistEntity
import com.novastats.app.data.db.entity.SnapshotEntity
import com.novastats.app.data.db.entity.SnapshotTrackEntity
import com.novastats.app.domain.BillboardDates
import com.novastats.app.domain.Chart
import com.novastats.app.domain.ChartAppearance
import com.novastats.app.domain.ChartHistory
import com.novastats.app.domain.ChartHistoryStats
import com.novastats.app.domain.Dates
import com.novastats.app.domain.HallOfFameRules
import com.novastats.app.domain.Period
import java.time.LocalDate

/**
 * Moteur du Billboard : calcule les snapshots (classements figés) de chaque période à partir de `daily_plays`.
 *
 *  - Périodes passées : figées (recalculées seulement par [rebuildAll]).
 *  - Période courante (LIVE) : recalculée à chaque écoute via [refreshCurrent].
 *  - Global : total all-time arrêté à la fin de chaque semaine ; mouvements vs semaine précédente.
 *  - Chaque snapshot ne dépend que des snapshots STRICTEMENT antérieurs → recalcul idempotent.
 *  - Alimente le Hall of Fame (Direct Debut / Long Run / Triple Debut / Legendary Run) sur périodes closes.
 */
class BillboardEngine(private val db: NovaDatabase) {

    private val dao get() = db.billboardDao()

    /** Reconstruction complète (après import / recalcul manuel). */
    suspend fun rebuildAll(onProgress: (String) -> Unit = {}) {
        val first = db.dailyPlayDao().firstDate()?.let { Dates.parse(it) }
        db.withTransaction {
            db.snapshotDao().clearTracks(); db.snapshotDao().clearArtists(); db.snapshotDao().clearAlbums(); db.snapshotDao().clearAll()
            db.hallOfFameDao().clearBadges(); db.hallOfFameDao().clear()
        }
        if (first == null) return
        val today = Dates.today()
        // Weekly / Monthly avant Daily : le Triple Debut compare le #1 du jour aux #1 semaine + mois.
        for (period in listOf(Period.WEEKLY, Period.MONTHLY, Period.YEARLY, Period.GLOBAL, Period.DAILY)) {
            val anchors = BillboardDates.allAnchors(period, first, today)
            anchors.forEachIndexed { i, anchor ->
                if (i % 10 == 0) onProgress("Billboard ${period.label} : ${i + 1}/${anchors.size}")
                computeSnapshot(period, anchor, today, notify = false)
            }
        }
    }

    /** Recalcule la période courante (et la précédente, pour la figer / évaluer le Hall of Fame). */
    suspend fun refreshCurrent(today: LocalDate = Dates.today()) {
        if (db.dailyPlayDao().firstDate() == null) return
        for (period in listOf(Period.WEEKLY, Period.MONTHLY, Period.YEARLY, Period.GLOBAL, Period.DAILY)) {
            val current = BillboardDates.anchor(period, today)
            val previous = BillboardDates.previous(period, current)
            if (db.snapshotDao().find(period.dbName, previous.format(Dates.ISO)) != null || db.dailyPlayDao().firstDate()!! <= previous.format(Dates.ISO)) {
                computeSnapshot(period, previous, today, notify = true)
            }
            computeSnapshot(period, current, today, notify = true)
        }
    }

    /** Calcule (ou recalcule) les 3 charts d'un snapshot. */
    suspend fun computeSnapshot(period: Period, anchor: LocalDate, today: LocalDate = Dates.today(), notify: Boolean) {
        val iso = anchor.format(Dates.ISO)
        val range = BillboardDates.range(period, anchor, today)
        val prevIso = BillboardDates.previous(period, anchor).format(Dates.ISO)
        val firstDate = db.dailyPlayDao().firstDate()?.let { Dates.parse(it) } ?: anchor
        // Suite complète des ancres jusqu'à celle-ci : permet de savoir si deux apparitions sont consécutives
        val anchors = BillboardDates.allAnchors(period, minOf(firstDate, anchor), anchor)

        db.withTransaction {
            val snapshotId = db.snapshotDao().find(period.dbName, iso)?.snapshotId
                ?: db.snapshotDao().insert(
                    SnapshotEntity(
                        type = period.dbName, date = iso,
                        weekNumber = if (period == Period.WEEKLY || period == Period.GLOBAL) Dates.isoWeekNumber(anchor) else null,
                        month = if (period == Period.MONTHLY) anchor.monthValue else null,
                        year = anchor.year
                    )
                )

            // ---- Hot 100
            val tracks = dao.rankTracks(range.fromIso, range.toIso, Chart.HOT_100.limit(period))
            val trackRows = build(tracks, dao.priorTrackRows(period.dbName, iso), prevIso, iso, anchors)
            dao.clearTrackRows(snapshotId)
            db.snapshotDao().insertTracks(trackRows.map { it.toTrackEntity(snapshotId) })

            // ---- Artist 50
            val artists = dao.rankArtists(range.fromIso, range.toIso, Chart.ARTIST_50.limit(period))
            val artistRows = build(artists, dao.priorArtistRows(period.dbName, iso), prevIso, iso, anchors)
            dao.clearArtistRows(snapshotId)
            db.snapshotDao().insertArtists(artistRows.map { it.toArtistEntity(snapshotId) })

            // ---- 75 Albums
            val albums = dao.rankAlbums(range.fromIso, range.toIso, Chart.ALBUMS_75.limit(period))
            val albumRows = build(albums, dao.priorAlbumRows(period.dbName, iso), prevIso, iso, anchors)
            dao.clearAlbumRows(snapshotId)
            db.snapshotDao().insertAlbums(albumRows.map { it.toAlbumEntity(snapshotId) })

            // ---- Hall of Fame (périodes closes uniquement)
            if (BillboardDates.isClosed(period, anchor, today)) {
                evaluateHallOfFame(period, anchor, EntityType.TRACK, trackRows.firstOrNull(), notify)
                evaluateHallOfFame(period, anchor, EntityType.ARTIST, artistRows.firstOrNull(), notify)
                evaluateHallOfFame(period, anchor, EntityType.ALBUM, albumRows.firstOrNull(), notify)
            }
        }
    }

    /* ------------------------------------------------------------------ */

    /** Ligne calculée, indépendante du type d'entité. */
    data class Computed(
        val entry: RankedEntry,
        val position: Int,
        val previousPosition: Int?,
        val movement: Int?,
        val isNew: Boolean,
        val isReentry: Boolean,
        val periodsInChart: Int,
        val peakPosition: Int,
        val peakDate: String,
        val timesAtPeak: Int,
        val debutPosition: Int,
        val debutDate: String,
        val variationPlays: Int,
        val isPlaysPeak: Boolean,
        val stats: ChartHistoryStats
    )

    private fun build(ranked: List<RankedEntry>, prior: List<PriorRow>, prevIso: String, iso: String, anchors: List<LocalDate>): List<Computed> {
        val byEntity = prior.groupBy { it.entityId }
        val prevRows = prior.filter { it.date == prevIso }.associateBy { it.entityId }
        return ranked.mapIndexed { i, e ->
            val pos = i + 1
            val history = byEntity[e.entityId].orEmpty()
            val stats = ChartHistory.stats(history.map { ChartAppearance(Dates.parse(it.date), it.position, it.playCount) }, anchors)
            val prev = prevRows[e.entityId]
            val isNew = history.isEmpty()
            val isReentry = !isNew && prev == null
            val peak = stats.peak
            val (peakPos, peakDate, times) = when {
                peak == null || pos < peak.position -> Triple(pos, iso, 1)
                pos == peak.position -> Triple(peak.position, peak.date.format(Dates.ISO), stats.timesAtPeak + 1)
                else -> Triple(peak.position, peak.date.format(Dates.ISO), stats.timesAtPeak)
            }
            Computed(
                entry = e, position = pos,
                previousPosition = prev?.position,
                movement = prev?.let { it.position - pos },
                isNew = isNew, isReentry = isReentry,
                periodsInChart = stats.periodsInChart + 1,
                peakPosition = peakPos, peakDate = peakDate, timesAtPeak = times,
                debutPosition = stats.firstEntry?.position ?: pos,
                debutDate = stats.firstEntry?.date?.format(Dates.ISO) ?: iso,
                variationPlays = prev?.let { e.plays - it.playCount } ?: 0,
                isPlaysPeak = !isNew && e.plays > stats.maxPlays,
                stats = stats
            )
        }
    }

    private fun Computed.toTrackEntity(snapshotId: Long) = SnapshotTrackEntity(
        snapshotId = snapshotId, trackId = entry.entityId, position = position, playCount = entry.plays,
        totalDurationMs = entry.durationMs, previousPosition = previousPosition, movement = movement,
        isNew = isNew, isReentry = isReentry, daysInChart = periodsInChart, weeksInChart = periodsInChart, monthsInChart = periodsInChart,
        peakPosition = peakPosition, peakDate = peakDate, timesAtPeak = timesAtPeak,
        debutPosition = debutPosition, debutDate = debutDate, variationPlays = variationPlays, isPlaysPeak = isPlaysPeak
    )

    private fun Computed.toArtistEntity(snapshotId: Long) = SnapshotArtistEntity(
        snapshotId = snapshotId, artistId = entry.entityId, position = position, playCount = entry.plays,
        totalDurationMs = entry.durationMs, previousPosition = previousPosition, movement = movement,
        isNew = isNew, isReentry = isReentry, daysInChart = periodsInChart, weeksInChart = periodsInChart, monthsInChart = periodsInChart,
        peakPosition = peakPosition, peakDate = peakDate, timesAtPeak = timesAtPeak,
        debutPosition = debutPosition, debutDate = debutDate, variationPlays = variationPlays,
        distinctTracks = entry.distinctTracks, distinctAlbums = entry.distinctAlbums, isPlaysPeak = isPlaysPeak
    )

    private fun Computed.toAlbumEntity(snapshotId: Long) = SnapshotAlbumEntity(
        snapshotId = snapshotId, albumId = entry.entityId, position = position, playCount = entry.plays,
        totalDurationMs = entry.durationMs, previousPosition = previousPosition, movement = movement,
        isNew = isNew, isReentry = isReentry, daysInChart = periodsInChart, weeksInChart = periodsInChart, monthsInChart = periodsInChart,
        peakPosition = peakPosition, peakDate = peakDate, timesAtPeak = timesAtPeak,
        debutPosition = debutPosition, debutDate = debutDate, variationPlays = variationPlays,
        distinctTracks = entry.distinctTracks, isPlaysPeak = isPlaysPeak
    )

    /* ------------------------------ Hall of Fame ------------------------------ */

    private suspend fun evaluateHallOfFame(period: Period, anchor: LocalDate, entityType: String, top: Computed?, notify: Boolean) {
        top ?: return
        val iso = anchor.format(Dates.ISO)
        val id = top.entry.entityId
        // Série #1 : les périodes précédentes consécutives (si la précédente était bien #1) + celle-ci
        val runAt1 = if (top.previousPosition == 1) top.stats.currentRunAt1 + 1 else 1
        val totalAt1 = countAt1(period, entityType, id, iso) + 1

        suspend fun induct(entryType: String, periodType: String, reignStart: String?) {
            if (db.hallOfFameDao().exists(id, entityType, periodType, entryType) > 0) return
            val hofId = db.hallOfFameDao().insert(
                HallOfFameEntity(
                    entityId = id, entityType = entityType, periodType = periodType, entryType = entryType,
                    entryDate = iso, reignStart = reignStart, reignEnd = iso,
                    weeksAt1 = if (period == Period.WEEKLY) totalAt1 else 0, playCountAtEntry = top.entry.plays
                )
            )
            db.hallOfFameDao().insertBadge(HallOfFameBadgeEntity(hofId = hofId, badgeType = entryType, badgeDate = iso))
            if (notify) db.notificationFeedDao().insert(
                NotificationFeedEntity(type = "HOF", entityId = id, entityType = entityType, message = "🏛️ Nouvelle entrée au Hall of Fame : ${entryType.replace('_', ' ')}")
            )
        }

        if (HallOfFameRules.isDirectDebut(period, top.position, top.isNew)) induct(HallOfFameRules.DIRECT_DEBUT, period.dbName, iso)
        if (HallOfFameRules.isLongRun(period, runAt1)) induct(HallOfFameRules.LONG_RUN, period.dbName, reignStartIso(period, anchor, runAt1))
        if (HallOfFameRules.isLegendaryRun(period, totalAt1)) induct(HallOfFameRules.LEGENDARY_RUN, Period.GLOBAL.dbName, null)

        if (period == Period.DAILY) {
            val week = BillboardDates.anchor(Period.WEEKLY, anchor).format(Dates.ISO)
            val month = BillboardDates.anchor(Period.MONTHLY, anchor).format(Dates.ISO)
            val weekly = numberOne(Period.WEEKLY, week, entityType)
            val monthly = numberOne(Period.MONTHLY, month, entityType)
            if (weekly == id && monthly == id) induct(HallOfFameRules.TRIPLE_DEBUT, Period.GLOBAL.dbName, iso)
        }
    }

    private fun reignStartIso(period: Period, anchor: LocalDate, run: Int): String {
        var a = anchor
        repeat(run - 1) { a = BillboardDates.previous(period, a) }
        return a.format(Dates.ISO)
    }

    private suspend fun countAt1(period: Period, entityType: String, id: Long, beforeIso: String): Int = when (entityType) {
        EntityType.TRACK -> dao.trackHistory(period.dbName, id)
        EntityType.ARTIST -> dao.artistHistory(period.dbName, id)
        else -> dao.albumHistory(period.dbName, id)
    }.count { it.position == 1 && it.date < beforeIso }

    private suspend fun numberOne(period: Period, iso: String, entityType: String): Long? = when (entityType) {
        EntityType.TRACK -> dao.numberOneTrack(period.dbName, iso)
        EntityType.ARTIST -> dao.numberOneArtist(period.dbName, iso)
        else -> dao.numberOneAlbum(period.dbName, iso)
    }
}
