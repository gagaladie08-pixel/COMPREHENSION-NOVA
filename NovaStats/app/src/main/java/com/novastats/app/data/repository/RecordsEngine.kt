package com.novastats.app.data.repository

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.dao.PriorRow
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.RecordCacheEntity
import com.novastats.app.domain.ChartAppearance
import com.novastats.app.domain.Dates
import com.novastats.app.domain.Period
import com.novastats.app.domain.RecordCatalog
import com.novastats.app.domain.RecordCategory
import com.novastats.app.domain.RecordMath
import com.novastats.app.domain.RecordResult
import java.time.LocalDate

/**
 * 🏅 Moteur des 24 Records : lit les snapshots Billboard (+ certifications, Panthéon, Hall of Fame)
 * et écrit le Top 10 de chaque record dans `records_cache`.
 * Tous les records se créent automatiquement (import JSON compris) — aucune saisie manuelle.
 */
class RecordsEngine(private val db: NovaDatabase) {

    private val chartPeriods = listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY, Period.YEARLY)

    /** Owner map : track → artistes (main + featured), track → album, album → artiste. */
    private class Links(val trackArtists: Map<Long, Set<Long>>, val trackAlbum: Map<Long, Long>, val albumArtist: Map<Long, Long>)

    suspend fun rebuildAll(onProgress: (String) -> Unit = {}) {
        onProgress("Records…")
        val links = loadLinks()
        val out = ArrayList<RecordCacheEntity>(4000)
        val now = System.currentTimeMillis()

        // Séries par période (chargées une fois pour le multi-chart)
        val seriesByPeriod = HashMap<Period, Map<RecordCategory, Map<Long, List<ChartAppearance>>>>()
        for (p in chartPeriods) {
            onProgress("Records · ${p.label}…")
            val perCat = mapOf(
                RecordCategory.TRACK to toSeries(db.billboardDao().allTrackRows(p.dbName), p),
                RecordCategory.ARTIST to toSeries(db.billboardDao().allArtistRows(p.dbName), p),
                RecordCategory.ALBUM to toSeries(db.billboardDao().allAlbumRows(p.dbName), p)
            )
            seriesByPeriod[p] = perCat
            for ((cat, series) in perCat) chartRecords(p, cat, series, out, now)
            ownerRecords(p, perCat, links, out, now)
        }
        multiChart(seriesByPeriod, out, now)
        certificationRecords(links, out, now)
        pantheonRecords(out, now)
        hallOfFameRecords(out, now)

        db.withTransaction {
            db.recordDao().clear()
            out.chunked(500).forEach { db.recordDao().insertAll(it) }
        }
    }

    /* ------------------------------------------------------------------ */

    private suspend fun loadLinks(): Links {
        val ta = HashMap<Long, MutableSet<Long>>()
        db.trackLinkDao().allTrackArtists().forEach { ta.getOrPut(it.trackId) { HashSet() }.add(it.artistId) }
        val tracks = db.trackDao().allPlayed()
        tracks.forEach { t -> ta.getOrPut(t.trackId) { HashSet() }.add(t.artistId) }
        val trackAlbum = tracks.mapNotNull { t -> t.albumId?.let { t.trackId to it } }.toMap()
        val albumArtist = db.albumDao().all().associate { it.albumId to it.artistId }
        return Links(ta, trackAlbum, albumArtist)
    }

    private fun periodIndex(date: LocalDate, p: Period): Int = when (p) {
        Period.DAILY -> date.toEpochDay().toInt()
        Period.WEEKLY -> Math.floorDiv(date.toEpochDay() + 3, 7L).toInt() // semaines ISO (lundi)
        Period.MONTHLY -> date.year * 12 + date.monthValue
        Period.YEARLY -> date.year
        Period.GLOBAL -> 0
    }

    private fun toSeries(rows: List<PriorRow>, p: Period): Map<Long, List<ChartAppearance>> {
        val idx = HashMap<String, Int>()
        val map = HashMap<Long, MutableList<ChartAppearance>>()
        for (r in rows) {
            val i = idx.getOrPut(r.date) { periodIndex(Dates.parse(r.date), p) }
            map.getOrPut(r.entityId) { ArrayList() }.add(ChartAppearance(i, r.date, r.position, r.playCount))
        }
        map.values.forEach { it.sortBy { a -> a.periodIndex } }
        return map
    }

    private fun row(type: String, period: Period?, cat: RecordCategory, sub: String?, r: RecordResult, now: Long) =
        RecordCacheEntity(recordType = type, periodType = period?.dbName, category = cat.dbName, subcategory = sub, entityId = r.entityId, value = r.value, valueDate = r.date, extraData = r.extra, calculatedAt = now)

    private fun top(results: List<RecordResult>, asc: Boolean = false): List<RecordResult> =
        (if (asc) results.sortedBy { it.value } else results.sortedByDescending { it.value }).take(10)

    private fun emit(out: MutableList<RecordCacheEntity>, type: String, period: Period?, cat: RecordCategory, sub: String?, results: List<RecordResult>, now: Long, asc: Boolean = false) {
        top(results.filter { it.value > 0 }, asc).forEach { out += row(type, period, cat, sub, it, now) }
    }

    /* ---------------- Records par entité (1, 2, 3, 10, 11, 15-20, 22) ---------------- */

    private fun chartRecords(p: Period, cat: RecordCategory, series: Map<Long, List<ChartAppearance>>, out: MutableList<RecordCacheEntity>, now: Long) {
        fun each(f: (Long, List<ChartAppearance>) -> RecordResult?): List<RecordResult> = series.mapNotNull { (id, s) -> f(id, s)?.copy(entityId = id) }

        emit(out, "MOST_CUMULATIVE", p, cat, null, each { id, s -> RecordResult(id, RecordMath.cumulative(s).toDouble(), s.last().date) }, now)
        emit(out, "MOST_CUMULATIVE_TOP10", p, cat, null, each { id, s -> RecordResult(id, RecordMath.cumulative(s, 10).toDouble(), s.lastOrNull { it.position <= 10 }?.date) }, now)
        emit(out, "MOST_TIME_AT_1", p, cat, null, each { id, s -> RecordResult(id, RecordMath.timesAt1(s).toDouble(), s.lastOrNull { it.position == 1 }?.date) }, now)
        if (p != Period.YEARLY) {
            emit(out, "BIGGEST_PERIOD", p, cat, null, each { id, s -> s.maxByOrNull { it.plays }?.let { RecordResult(id, it.plays.toDouble(), it.date, "#${it.position} ce jour-là") } }, now)
        }
        emit(out, "BIGGEST_DEBUT", p, cat, null, each { id, s -> s.first().let { RecordResult(id, it.plays.toDouble(), it.date, "entré #${it.position}") } }, now)
        emit(out, "BIGGEST_COMEBACK", p, cat, null, each { _, s -> RecordMath.biggestComeback(s, RecordCatalog.comebackMinAbsence(p)) }, now)
        emit(out, "FASTEST_RISE", p, cat, null, each { id, s -> RecordMath.periodsToReach(s, 3)?.let { (n, d) -> RecordResult(id, (n + 1).toDouble(), d, if (n == 0) "Top 3 dès l'entrée" else "Top 3 après $n ${RecordCatalog.unitLabel(p, n > 1)}") } }, now, asc = true)
        for ((sub, max) in listOf("TOP5" to 5, "TOP10" to 10, "CHART" to Int.MAX_VALUE)) {
            emit(out, "MOST_CONSISTENT", p, cat, sub, each { id, s -> RecordMath.longestStreak(s, max).let { (n, d) -> RecordResult(id, n.toDouble(), d) } }, now)
        }
        emit(out, "BIGGEST_JUMP", p, cat, null, each { _, s -> RecordMath.biggestMove(s, jump = true) }, now)
        emit(out, "BIGGEST_FALL", p, cat, null, each { _, s -> RecordMath.biggestMove(s, jump = false) }, now)
        emit(out, "SLEEPER_HIT", p, cat, null, each { _, s -> RecordMath.sleeperHit(s) }, now)
        emit(out, "LONGEST_ROAD", p, cat, null, each { id, s -> RecordMath.periodsToReach(s, 1)?.let { (n, d) -> if (n > 0) RecordResult(id, n.toDouble(), d, "#1 après $n ${RecordCatalog.unitLabel(p, n > 1)}") else null } }, now)
        emit(out, "BIGGEST_CLIMBER", p, cat, null, each { _, s -> RecordMath.biggestClimber(s) }, now)
        emit(out, "MOST_BLOCKED_TOP5", p, cat, null, each { id, s -> RecordMath.blockedTop5(s)?.let { (n, peak) -> RecordResult(id, n.toDouble(), s.lastOrNull { it.position <= 5 }?.date, "meilleure position #$peak") } }, now)
    }

    /* ---------------- Records "propriétaire" (4-8, 23, 24) ---------------- */

    private fun ownerRecords(p: Period, perCat: Map<RecordCategory, Map<Long, List<ChartAppearance>>>, links: Links, out: MutableList<RecordCacheEntity>, now: Long) {
        val trackSeries = perCat.getValue(RecordCategory.TRACK)
        val albumSeries = perCat.getValue(RecordCategory.ALBUM)

        // Trois "vues" : ARTIST/SONGS (titres → artistes), ARTIST/ALBUMS (albums → artiste), ALBUM (titres → album)
        data class View(val cat: RecordCategory, val sub: String?, val series: Map<Long, List<ChartAppearance>>, val owners: (Long) -> Set<Long>)
        val views = listOf(
            View(RecordCategory.ARTIST, "SONGS", trackSeries) { links.trackArtists[it].orEmpty() },
            View(RecordCategory.ARTIST, "ALBUMS", albumSeries) { links.albumArtist[it]?.let { a -> setOf(a) }.orEmpty() },
            View(RecordCategory.ALBUM, null, trackSeries) { links.trackAlbum[it]?.let { a -> setOf(a) }.orEmpty() }
        )
        for (v in views) {
            val inChart = HashMap<Long, MutableSet<Long>>(); val top10 = HashMap<Long, MutableSet<Long>>(); val at1 = HashMap<Long, MutableSet<Long>>()
            val debut1 = HashMap<Long, MutableSet<Long>>(); val debut10 = HashMap<Long, MutableSet<Long>>()
            val lastDate = HashMap<Long, String>()
            // Simultanés : période → owner → nb d'items par zone
            val simultaneous = HashMap<String, HashMap<Long, IntArray>>() // zones : 5,10,20,50,all
            val zoneLimits = intArrayOf(5, 10, 20, 50, Int.MAX_VALUE)
            val zoneNames = listOf("TOP5", "TOP10", "TOP20", "TOP50", "ALL")
            for ((itemId, s) in v.series) {
                val owners = v.owners(itemId)
                if (owners.isEmpty()) continue
                val entry = s.first()
                for (o in owners) {
                    inChart.getOrPut(o) { HashSet() }.add(itemId)
                    if (s.any { it.position <= 10 }) top10.getOrPut(o) { HashSet() }.add(itemId)
                    if (s.any { it.position == 1 }) at1.getOrPut(o) { HashSet() }.add(itemId)
                    if (entry.position == 1) debut1.getOrPut(o) { HashSet() }.add(itemId)
                    if (entry.position <= 10) debut10.getOrPut(o) { HashSet() }.add(itemId)
                    lastDate[o] = maxOf(lastDate[o] ?: "", s.last().date)
                    for (a in s) {
                        val counts = simultaneous.getOrPut(a.date) { HashMap() }.getOrPut(o) { IntArray(5) }
                        for (z in zoneLimits.indices) if (a.position <= zoneLimits[z]) counts[z]++
                    }
                }
            }
            fun res(m: Map<Long, Set<Long>>) = m.map { (o, set) -> RecordResult(o, set.size.toDouble(), lastDate[o]) }
            emit(out, "MOST_SONGS_IN_CHART", p, v.cat, v.sub, res(inChart), now)
            emit(out, "MOST_SONGS_TOP10", p, v.cat, v.sub, res(top10), now)
            emit(out, "MOST_SONGS_AT_1", p, v.cat, v.sub, res(at1), now)
            emit(out, "MOST_DEBUT_1", p, v.cat, v.sub, res(debut1), now)
            emit(out, "MOST_DEBUT_TOP10", p, v.cat, v.sub, res(debut10), now)
            for (z in zoneLimits.indices) {
                val best = HashMap<Long, RecordResult>()
                for ((date, byOwner) in simultaneous) for ((o, counts) in byOwner) {
                    val c = counts[z]
                    val cur = best[o]
                    if (c > 0 && (cur == null || c > cur.value || (c.toDouble() == cur.value && date > (cur.date ?: "")))) best[o] = RecordResult(o, c.toDouble(), date)
                }
                val sub = if (v.cat == RecordCategory.ARTIST) "${v.sub}_${zoneNames[z]}" else zoneNames[z]
                emit(out, "MOST_SIMULTANEOUS", p, v.cat, sub, best.values.toList(), now)
            }
            // #1 successifs
            val numberOnes = v.series.flatMap { (itemId, s) -> s.filter { it.position == 1 }.map { RecordMath.NumberOne(it.periodIndex, it.date, itemId, v.owners(itemId)) } }
                .sortedBy { it.periodIndex }
            emit(out, "MOST_SUCCESSIVE_1", p, v.cat, v.sub, RecordMath.successiveNumberOnes(numberOnes).values.toList(), now)
        }
    }

    /* ---------------- 21. Multi-Chart Domination ---------------- */

    private fun multiChart(seriesByPeriod: Map<Period, Map<RecordCategory, Map<Long, List<ChartAppearance>>>>, out: MutableList<RecordCacheEntity>, now: Long) {
        for (cat in RecordCategory.entries) {
            // date → entité → position, pour chaque période
            fun index(p: Period): Map<String, Map<Long, Int>> {
                val m = HashMap<String, HashMap<Long, Int>>()
                seriesByPeriod[p]?.get(cat)?.forEach { (id, s) -> s.forEach { a -> m.getOrPut(a.date) { HashMap() }[id] = a.position } }
                return m
            }
            val d = index(Period.DAILY); val w = index(Period.WEEKLY); val mo = index(Period.MONTHLY); val y = index(Period.YEARLY)
            val counts = HashMap<String, HashMap<Long, RecordResult>>()
            for ((dateIso, daily) in d) {
                val date = Dates.parse(dateIso)
                val week = w[Dates.weekOf(date).fromIso].orEmpty()
                val month = mo[Dates.monthOf(date).fromIso].orEmpty()
                val year = y[Dates.yearOf(date).fromIso].orEmpty()
                for ((id, pd) in daily) {
                    val pw = week[id]; val pm = month[id]; val py = year[id]
                    fun bump(level: String, combo: String) {
                        val m = counts.getOrPut(level) { HashMap() }
                        val cur = m[id]
                        m[id] = RecordResult(id, (cur?.value ?: 0.0) + 1, dateIso, if (cur == null || combo < (cur.extra ?: "~")) combo else cur.extra)
                    }
                    if (pw != null && pm != null && py != null) bump("DWMY", "D#$pd · W#$pw · M#$pm · Y#$py")
                    if (pw != null && pm != null) bump("DWM", "D#$pd · W#$pw · M#$pm")
                    if (pw != null) bump("DW", "D#$pd · W#$pw")
                }
                // W+M et M+Y se mesurent sur les entités des charts hebdo / mensuels
                for ((id, pw) in week) { val pm = month[id] ?: continue
                    val m = counts.getOrPut("WM") { HashMap() }; val cur = m[id]
                    m[id] = RecordResult(id, (cur?.value ?: 0.0) + 1, dateIso, "W#$pw · M#$pm")
                }
                for ((id, pm) in month) { val py = year[id] ?: continue
                    val m = counts.getOrPut("MY") { HashMap() }; val cur = m[id]
                    m[id] = RecordResult(id, (cur?.value ?: 0.0) + 1, dateIso, "M#$pm · Y#$py")
                }
            }
            for (level in listOf("DWMY", "DWM", "DW", "WM", "MY")) emit(out, "MULTI_CHART", null, cat, level, counts[level]?.values?.toList().orEmpty(), now)
        }
    }

    /* ---------------- 9 & 12. Certifications ---------------- */

    private suspend fun certificationRecords(links: Links, out: MutableList<RecordCacheEntity>, now: Long) {
        val history = db.certificationDao().allHistory()
        // 9. Fastest certification (par niveau, par type) — temps depuis la première écoute
        for (type in listOf(EntityType.TRACK to RecordCategory.TRACK, EntityType.ALBUM to RecordCategory.ALBUM)) {
            for (level in RecordCatalog.certLevels) {
                val results = history.filter { it.entityType == type.first && it.level == level.dbName && it.timeToCertifyMs != null && it.timeToCertifyMs > 0 }
                    .groupBy { it.entityId }
                    .map { (id, rows) -> rows.minBy { it.timeToCertifyMs!! }.let { RecordResult(id, it.timeToCertifyMs!!.toDouble(), Dates.toIso(it.certifiedAt), if (it.multiplier > 1) "${it.multiplier}x" else null) } }
                emit(out, "FASTEST_CERT", null, type.second, level.dbName, results, now, asc = true)
            }
        }
        // 12. Most certifications : niveau atteint (historique) par titre / album
        val trackLevels = history.filter { it.entityType == EntityType.TRACK }.groupBy { it.level }.mapValues { (_, rows) -> rows.map { it.entityId }.toSet() }
        val albumLevels = history.filter { it.entityType == EntityType.ALBUM }.groupBy { it.level }.mapValues { (_, rows) -> rows.map { it.entityId }.toSet() }
        for (level in RecordCatalog.certLevels) {
            val certifiedTracks = trackLevels[level.dbName].orEmpty()
            val certifiedAlbums = albumLevels[level.dbName].orEmpty()
            val byArtistSongs = HashMap<Long, Int>(); val byAlbum = HashMap<Long, Int>(); val byArtistAlbums = HashMap<Long, Int>()
            certifiedTracks.forEach { t ->
                links.trackArtists[t].orEmpty().forEach { a -> byArtistSongs[a] = (byArtistSongs[a] ?: 0) + 1 }
                links.trackAlbum[t]?.let { al -> byAlbum[al] = (byAlbum[al] ?: 0) + 1 }
            }
            certifiedAlbums.forEach { al -> links.albumArtist[al]?.let { a -> byArtistAlbums[a] = (byArtistAlbums[a] ?: 0) + 1 } }
            emit(out, "MOST_CERTIFICATIONS", null, RecordCategory.ARTIST, "SONGS_${level.dbName}", byArtistSongs.map { (id, n) -> RecordResult(id, n.toDouble()) }, now)
            emit(out, "MOST_CERTIFICATIONS", null, RecordCategory.ARTIST, "ALBUMS_${level.dbName}", byArtistAlbums.map { (id, n) -> RecordResult(id, n.toDouble()) }, now)
            emit(out, "MOST_CERTIFICATIONS", null, RecordCategory.ALBUM, level.dbName, byAlbum.map { (id, n) -> RecordResult(id, n.toDouble()) }, now)
        }
    }

    /* ---------------- 9. Fastest Panthéon ---------------- */

    private suspend fun pantheonRecords(out: MutableList<RecordCacheEntity>, now: Long) {
        val history = db.pantheonDao().allHistory()
        for (status in RecordCatalog.pantheonLevels) {
            val results = history.filter { it.status == status.dbName && it.timeToReachMs != null && it.timeToReachMs > 0 }
                .map { RecordResult(it.artistId, it.timeToReachMs!!.toDouble(), Dates.toIso(it.dateReached), "${it.playCountAtStatus} écoutes") }
            emit(out, "FASTEST_PANTHEON", null, RecordCategory.ARTIST, status.dbName, results, now, asc = true)
        }
    }

    /* ---------------- 13 & 14. Hall of Fame / Global ---------------- */

    private suspend fun hallOfFameRecords(out: MutableList<RecordCacheEntity>, now: Long) {
        val all = db.hallOfFameDao().all()
        for (cat in RecordCategory.entries) {
            val mine = all.filter { it.entityType == cat.dbName }
            fun agg(rows: List<com.novastats.app.data.db.entity.HallOfFameEntity>) = rows.groupBy { it.entityId }.map { (id, r) ->
                val types = r.groupBy { it.entryType }.map { (t, l) -> "${l.size}× ${t.lowercase().replace('_', ' ')}" }.joinToString(", ")
                RecordResult(id, r.size.toDouble(), r.maxOf { it.entryDate }, types)
            }
            emit(out, "MOST_HOF", null, cat, null, agg(mine.filter { it.periodType != "GLOBAL" }), now)
            val global = mine.filter { it.periodType == "GLOBAL" }
            emit(out, "MOST_GLOBAL", null, cat, "TRIPLE_DEBUT", agg(global.filter { it.entryType == "TRIPLE_DEBUT" }), now)
            emit(out, "MOST_GLOBAL", null, cat, "LEGENDARY_RUN", agg(global.filter { it.entryType == "LEGENDARY_RUN" }), now)
            emit(out, "MOST_GLOBAL", null, cat, "ALL", agg(global), now)
        }
    }
}
