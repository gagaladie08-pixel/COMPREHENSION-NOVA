package com.novastats.app.data.repository

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.PriorRow
import com.novastats.app.data.db.dao.RankedEntry
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.HallOfFameBadgeEntity
import com.novastats.app.data.db.entity.HallOfFameEntity
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
import com.novastats.app.domain.ArtistChartNumberOnes
import com.novastats.app.domain.HallOfFameRules
import com.novastats.app.domain.Period
import java.time.LocalDate

/**
 * Moteur du Billboard : calcule les snapshots (classements figés) de chaque période à partir de `daily_plays`.
 *
 *  - Seules les périodes CLOSES ont un snapshot (Daily = hier, Weekly = semaine dernière, Monthly = mois dernier…) :
 *    la période en cours n'est jamais publiée, comme le vrai Billboard.
 *  - [refreshCurrent] (après chaque écoute / ouverture) publie les snapshots manquants jusqu'à la dernière période close.
 *  - Global : total all-time arrêté à la fin de chaque semaine ; mouvements vs semaine précédente.
 *  - Chaque snapshot ne dépend que des snapshots STRICTEMENT antérieurs → recalcul idempotent.
 *  - Alimente le Hall of Fame (Direct Debut / Long Run / Triple Debut / All-Kill / Legendary Run) sur périodes closes.
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
                computeSnapshot(period, anchor, today)
            }
        }
    }

    /**
     * Publie les snapshots manquants jusqu'à la dernière période close (rattrape les jours sans écoute ni ouverture),
     * et re-fige la dernière publiée (une écoute confirmée juste après minuit peut encore appartenir à la veille).
     */
    suspend fun refreshCurrent(
        today: LocalDate = Dates.today(),
        collectNewInductions: Boolean = false
    ): List<StatsRebuilder.Achievement> {
        val first = db.dailyPlayDao().firstDate()?.let { Dates.parse(it) } ?: return emptyList()
        fun inductionKey(entry: HallOfFameEntity) = listOf(
            entry.entityType, entry.entityId.toString(), entry.periodType, entry.entryType,
            entry.entryDate.takeIf { entry.entryType == HallOfFameRules.PERFECT_ALL_KILL }.orEmpty()
        )
        val previousHof = if (collectNewInductions) db.hallOfFameDao().all()
            .mapTo(mutableSetOf(), ::inductionKey)
        else emptySet()
        for (period in listOf(Period.WEEKLY, Period.MONTHLY, Period.YEARLY, Period.GLOBAL, Period.DAILY)) {
            val latest = BillboardDates.latest(period, today)
            if (BillboardDates.range(period, latest, today).to < first) continue
            val lastPublished = db.snapshotDao().latestDate(period.dbName)?.let { Dates.parse(it) }
            val from = if (lastPublished == null || lastPublished < first) first else lastPublished
            for (anchor in BillboardDates.allAnchors(period, from, today)) computeSnapshot(period, anchor, today)
        }
        // Triple Debut : un jour dont le #1 est aussi #1 de sa semaine ET de son mois — réévalué quand semaine/mois se publient.
        val week = BillboardDates.range(Period.WEEKLY, BillboardDates.latest(Period.WEEKLY, today), today)
        val month = BillboardDates.range(Period.MONTHLY, BillboardDates.latest(Period.MONTHLY, today), today)
        for (range in listOf(week, month)) {
            var day = range.from
            while (!day.isAfter(range.to)) { evaluateTripleDebut(day); day = day.plusDays(1) }
        }
        if (!collectNewInductions) return emptyList()
        val newInductions = mutableListOf<StatsRebuilder.Achievement>()
        for (entry in db.hallOfFameDao().all()) {
            val key = inductionKey(entry)
            if (key in previousHof) continue
            val name = when (entry.entityType) {
                EntityType.ALBUM -> db.albumDao().getById(entry.entityId)?.title
                EntityType.ARTIST -> db.artistDao().getById(entry.entityId)?.name
                else -> db.trackDao().getById(entry.entityId)?.title
            }
            newInductions += StatsRebuilder.Achievement("HALL_OF_FAME", entry.entityType, entry.entityId, entry.entryType, name ?: "—")
        }
        return newInductions
    }

    /**
     * Amorçage idempotent de Perfect All Kill depuis les snapshots conservés.
     * Ajoute seulement les distinctions manquantes; aucune ligne source ou autre distinction n'est purgée.
     */
    suspend fun reconcilePerfectAllKillsFromSnapshots(): Int {
        val activeArtistIds = db.artistDao().allPlayedList().mapTo(HashSet()) { it.artistId }
        val creditedByTrack = HashMap<Long, MutableSet<Long>>()
        db.trackLinkDao().allTrackArtists().forEach { link ->
            creditedByTrack.getOrPut(link.trackId) { HashSet() }.add(link.artistId)
        }
        db.trackDao().allPlayed().forEach { track ->
            creditedByTrack.getOrPut(track.trackId) { HashSet() }.add(track.artistId)
        }
        val albumOwners = db.albumDao().all().associate { it.albumId to it.artistId }
        var added = 0

        for (period in listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY, Period.YEARLY)) {
            val hot100Leaders = dao.allTrackRows(period.dbName)
                .filter { it.position == 1 }.associate { it.date to it.entityId }
            val artist50Leaders = dao.allArtistRows(period.dbName)
                .filter { it.position == 1 }.associate { it.date to it.entityId }
            val album75Leaders = dao.allAlbumRows(period.dbName)
                .filter { it.position == 1 }.associate { it.date to it.entityId }
            val sharedDates = hot100Leaders.keys.intersect(artist50Leaders.keys)
                .intersect(album75Leaders.keys).sorted()

            val candidates = sharedDates.mapNotNull { date ->
                val artist50Id = artist50Leaders[date]?.takeIf { it in activeArtistIds } ?: return@mapNotNull null
                val trackId = hot100Leaders[date] ?: return@mapNotNull null
                val albumId = album75Leaders[date] ?: return@mapNotNull null
                val hot100Artists = creditedByTrack[trackId].orEmpty().filterTo(linkedSetOf()) { it in activeArtistIds }
                val albumArtistId = albumOwners[albumId]?.takeIf { it in activeArtistIds }
                val snapshot = ArtistChartNumberOnes.Snapshot(
                    date = date,
                    hot100ArtistIds = hot100Artists,
                    artist50LeaderId = artist50Id,
                    albums75ArtistIds = setOfNotNull(albumArtistId)
                )
                ArtistChartNumberOnes.perfectAllKillArtist(snapshot)?.let { it to date }
            }

            if (candidates.isNotEmpty()) db.withTransaction {
                for ((artistId, date) in candidates) {
                    val hofDao = db.hallOfFameDao()
                    if (hofDao.existsOnDate(artistId, EntityType.ARTIST, period.dbName, HallOfFameRules.PERFECT_ALL_KILL, date) > 0) continue
                    val hofId = hofDao.insert(
                        HallOfFameEntity(
                            entityId = artistId,
                            entityType = EntityType.ARTIST,
                            periodType = period.dbName,
                            entryType = HallOfFameRules.PERFECT_ALL_KILL,
                            entryDate = date,
                            reignStart = null,
                            reignEnd = null,
                            weeksAt1 = 0,
                            playCountAtEntry = 0
                        )
                    )
                    if (hofId > 0L) {
                        hofDao.insertBadge(HallOfFameBadgeEntity(hofId = hofId, badgeType = HallOfFameRules.PERFECT_ALL_KILL, badgeDate = date))
                        added++
                    }
                }
            }
        }
        return added
    }

    /** Calcule (ou recalcule) les 3 charts d'un snapshot. */
    suspend fun computeSnapshot(period: Period, anchor: LocalDate, today: LocalDate = Dates.today()) {
        val iso = anchor.format(Dates.ISO)
        val range = BillboardDates.range(period, anchor, today)
        val globalWeekRange = if (period == Period.GLOBAL) BillboardDates.range(Period.WEEKLY, anchor, today) else null
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
            val tracks = globalWeekRange?.let { week ->
                dao.rankGlobalTracks(range.fromIso, range.toIso, week.fromIso, week.toIso, Chart.HOT_100.limit(period))
            } ?: dao.rankTracks(range.fromIso, range.toIso, Chart.HOT_100.limit(period))
            val trackRows = build(tracks, dao.priorTrackRows(period.dbName, iso), prevIso, iso, anchors, useWeeklyPlays = globalWeekRange != null)
            dao.clearTrackRows(snapshotId)
            db.snapshotDao().insertTracks(trackRows.map { it.toTrackEntity(snapshotId) })

            // ---- Artist 50
            val artists = globalWeekRange?.let { week ->
                dao.rankGlobalArtists(range.fromIso, range.toIso, week.fromIso, week.toIso, Chart.ARTIST_50.limit(period))
            } ?: dao.rankArtists(range.fromIso, range.toIso, Chart.ARTIST_50.limit(period))
            val artistRows = build(artists, dao.priorArtistRows(period.dbName, iso), prevIso, iso, anchors, useWeeklyPlays = globalWeekRange != null)
            dao.clearArtistRows(snapshotId)
            db.snapshotDao().insertArtists(artistRows.map { it.toArtistEntity(snapshotId) })

            // ---- 75 Albums
            val albums = globalWeekRange?.let { week ->
                dao.rankGlobalAlbums(range.fromIso, range.toIso, week.fromIso, week.toIso, Chart.ALBUMS_75.limit(period))
            } ?: dao.rankAlbums(range.fromIso, range.toIso, Chart.ALBUMS_75.limit(period))
            val albumRows = build(albums, dao.priorAlbumRows(period.dbName, iso), prevIso, iso, anchors, useWeeklyPlays = globalWeekRange != null)
            dao.clearAlbumRows(snapshotId)
            db.snapshotDao().insertAlbums(albumRows.map { it.toAlbumEntity(snapshotId) })

            // ---- Hall of Fame (périodes closes uniquement)
            if (BillboardDates.isClosed(period, anchor, today)) {
                evaluateHallOfFame(period, anchor, EntityType.TRACK, trackRows.firstOrNull())
                evaluateHallOfFame(period, anchor, EntityType.ARTIST, artistRows.firstOrNull())
                evaluateHallOfFame(period, anchor, EntityType.ALBUM, albumRows.firstOrNull())
                evaluatePerfectAllKill(period, anchor, trackRows, artistRows, albumRows)
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

    private fun build(
        ranked: List<RankedEntry>, prior: List<PriorRow>, prevIso: String, iso: String,
        anchors: List<LocalDate>, useWeeklyPlays: Boolean = false
    ): List<Computed> {
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
                variationPlays = if (useWeeklyPlays) e.weeklyPlays else prev?.let { e.plays - it.playCount } ?: 0,
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

    private suspend fun evaluateHallOfFame(period: Period, anchor: LocalDate, entityType: String, top: Computed?) {
        top ?: return
        val iso = anchor.format(Dates.ISO)
        val id = top.entry.entityId
        // Série #1 : les périodes précédentes consécutives (si la précédente était bien #1) + celle-ci.
        val runAt1 = if (top.previousPosition == 1) top.stats.currentRunAt1 + 1 else 1
        val totalAt1 = countAt1(period, entityType, id, iso) + 1

        suspend fun induct(entryType: String, periodType: String, reignStart: String?) =
            induct(id, entityType, entryType, periodType, iso, reignStart, if (period == Period.WEEKLY) totalAt1 else 0, top.entry.plays)

        if (HallOfFameRules.isDirectDebut(period, top.position, top.isNew)) induct(HallOfFameRules.DIRECT_DEBUT, period.dbName, iso)
        if (HallOfFameRules.isLongRun(period, runAt1)) induct(HallOfFameRules.LONG_RUN, period.dbName, reignStartIso(period, anchor, runAt1))
        if (HallOfFameRules.isLegendaryRun(period, totalAt1)) induct(HallOfFameRules.LEGENDARY_RUN, Period.GLOBAL.dbName, null)

        if (period == Period.DAILY) evaluateTripleDebut(anchor)
    }

    /** 💎 Perfect All Kill : un même artiste relie les trois #1 du snapshot de cette cadence. */
    private suspend fun evaluatePerfectAllKill(
        period: Period,
        anchor: LocalDate,
        tracks: List<Computed>,
        artists: List<Computed>,
        albums: List<Computed>
    ) {
        if (period !in listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY, Period.YEARLY)) return
        val trackId = tracks.firstOrNull()?.entry?.entityId ?: return
        val artistId = artists.firstOrNull()?.entry?.entityId ?: return
        val albumId = albums.firstOrNull()?.entry?.entityId ?: return
        val creditedArtistIds = db.trackLinkDao().artistIdsForTrack(trackId).toMutableSet()
        db.trackDao().getById(trackId)?.artistId?.let(creditedArtistIds::add)
        val albumOwnerId = db.albumDao().getById(albumId)?.artistId ?: return
        val activeArtist = db.artistDao().getById(artistId) ?: return
        if (activeArtist.isMerged) return
        val iso = anchor.format(Dates.ISO)
        val qualifyingArtist = ArtistChartNumberOnes.perfectAllKillArtist(
            ArtistChartNumberOnes.Snapshot(
                date = iso,
                hot100ArtistIds = creditedArtistIds,
                artist50LeaderId = artistId,
                albums75ArtistIds = setOf(albumOwnerId)
            )
        ) ?: return
        if (qualifyingArtist != artistId) return
        induct(
            qualifyingArtist, EntityType.ARTIST, HallOfFameRules.PERFECT_ALL_KILL,
            period.dbName, iso, reignStart = null, weeksAt1 = 0, plays = 0, reignEnd = null
        )
    }

    /** 💥 All-Kill + 🌍 vrai Triple Debut (Global) : même #1 le jour [day], sa semaine et son mois. */
    private suspend fun evaluateTripleDebut(day: LocalDate) {
        val iso = day.format(Dates.ISO)
        val week = BillboardDates.anchor(Period.WEEKLY, day).format(Dates.ISO)
        val month = BillboardDates.anchor(Period.MONTHLY, day).format(Dates.ISO)
        for (entityType in listOf(EntityType.TRACK, EntityType.ARTIST, EntityType.ALBUM)) {
            val daily = numberOne(Period.DAILY, iso, entityType) ?: continue
            if (numberOne(Period.WEEKLY, week, entityType) != daily || numberOne(Period.MONTHLY, month, entityType) != daily) continue
            // 💥 All-Kill : triple #1 le même jour, sans condition de début.
            induct(daily, entityType, HallOfFameRules.ALL_KILL, Period.GLOBAL.dbName, iso, iso, 0, 0)
            // 🌍 vrai Triple Debut : en plus, ce jour est sa toute première apparition (jour, semaine, mois) dans les charts.
            if (!appearedBefore(Period.DAILY, iso, entityType, daily) &&
                !appearedBefore(Period.WEEKLY, week, entityType, daily) &&
                !appearedBefore(Period.MONTHLY, month, entityType, daily)
            ) {
                induct(daily, entityType, HallOfFameRules.TRIPLE_DEBUT, Period.GLOBAL.dbName, iso, iso, 0, 0)
            }
        }
    }

    /** L'entité a-t-elle déjà paru dans ce chart avant [beforeIso] ? (vrai début = non) */
    private suspend fun appearedBefore(period: Period, beforeIso: String, entityType: String, id: Long): Boolean = when (entityType) {
        EntityType.TRACK -> dao.appearedBeforeTrack(period.dbName, beforeIso, id) > 0
        EntityType.ARTIST -> dao.appearedBeforeArtist(period.dbName, beforeIso, id) > 0
        else -> dao.appearedBeforeAlbum(period.dbName, beforeIso, id) > 0
    }

    private suspend fun induct(
        id: Long,
        entityType: String,
        entryType: String,
        periodType: String,
        iso: String,
        reignStart: String?,
        weeksAt1: Int,
        plays: Int,
        reignEnd: String? = iso
    ) {
        val hofDao = db.hallOfFameDao()
        val exists = if (entryType == HallOfFameRules.PERFECT_ALL_KILL)
            hofDao.existsOnDate(id, entityType, periodType, entryType, iso) > 0
        else hofDao.exists(id, entityType, periodType, entryType) > 0
        if (exists) return
        val hofId = hofDao.insert(
            HallOfFameEntity(
                entityId = id, entityType = entityType, periodType = periodType, entryType = entryType,
                entryDate = iso, reignStart = reignStart, reignEnd = reignEnd, weeksAt1 = weeksAt1, playCountAtEntry = plays
            )
        )
        db.hallOfFameDao().insertBadge(HallOfFameBadgeEntity(hofId = hofId, badgeType = entryType, badgeDate = iso))
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
