package com.novastats.app.data.repository

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.CertificationEntity
import com.novastats.app.data.db.entity.CertificationHistoryEntity
import com.novastats.app.data.db.entity.DailyStreakEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.PantheonHistoryEntity
import com.novastats.app.data.db.entity.PantheonStatusEntity
import com.novastats.app.data.db.entity.SessionEntity
import com.novastats.app.domain.ArtistCertSummary
import com.novastats.app.domain.Certification
import com.novastats.app.domain.CertificationRules
import com.novastats.app.domain.Dates
import com.novastats.app.domain.PantheonRules
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.ScrobbleRules
import com.novastats.app.domain.StreakCalculator
import kotlinx.coroutines.flow.first

/**
 * Recalcule toutes les tables dérivées à partir des scrobbles confirmés.
 * Appelé après un import, une suppression d'écoute, une fusion dans l'éditeur…
 *
 * Ordre : agrégats → daily_plays / daily_stats / streaks / sessions → certifications → Panthéon.
 * (Snapshots, Billboard, Hall of Fame, Records et Awards seront branchés sur ce pipeline.)
 */
class StatsRebuilder(private val db: NovaDatabase) {

    /** Nouveauté détectée après un recalcul (pour les notifications). */
    data class Achievement(val kind: String, val entityType: String, val entityId: Long, val level: String, val name: String)

    /**
     * @param fullBillboard true = reconstruit tous les snapshots (import) ; false = ne recalcule que la période
     *                      courante (après une écoute).
     * @return les nouvelles certifications / statuts Panthéon / entrées Hall of Fame apparus pendant ce recalcul
     *         (vide après un import complet, pour ne pas inonder de notifications).
     */
    suspend fun rebuildAll(fullBillboard: Boolean = true, onProgress: (String) -> Unit = {}): List<Achievement> {
        val certsBefore: Map<Pair<String, Long>, Int> = if (fullBillboard) emptyMap() else db.certificationDao().allCurrent().associate { (it.entityType to it.entityId) to it.toDomain().rank }
        val pantheonBefore: Map<Long, String> = if (fullBillboard) emptyMap() else db.pantheonDao().allCurrent().associate { it.artistId to it.currentStatus }
        val hofBefore: Set<List<String>> = if (fullBillboard) emptySet() else db.hallOfFameDao().all().map { listOf(it.entityType, it.entityId.toString(), it.periodType, it.entryType) }.toSet()

        onProgress("Agrégats titres / artistes / albums…")
        db.withTransaction {
            db.trackDao().recomputeAggregates()
            db.trackDao().recomputeDiscoveryRanks()
            db.artistDao().recomputeAggregates()
            db.albumDao().recomputeAggregates()
        }

        onProgress("Écoutes quotidiennes…")
        db.withTransaction {
            db.dailyPlayDao().clear()
            db.dailyPlayDao().rebuildFromScrobbles()
            db.dailyStatsDao().clear()
            db.dailyStatsDao().rebuildFromScrobbles()
        }

        onProgress("Streaks…")
        rebuildStreaks()

        onProgress("Sessions…")
        rebuildSessions()

        onProgress("Certifications…")
        rebuildCertifications()

        onProgress("Panthéon…")
        rebuildPantheon()

        onProgress("Billboard…")
        val billboard = BillboardEngine(db)
        if (fullBillboard) billboard.rebuildAll(onProgress) else billboard.refreshCurrent()

        onProgress("Records…")
        RecordsEngine(db).rebuildAll(onProgress)

        onProgress("Nova Awards…")
        if (fullBillboard) AwardsEngine(db).rebuildAll() else AwardsEngine(db).refreshAll()

        if (fullBillboard) return emptyList()
        val news = ArrayList<Achievement>()
        for (c in db.certificationDao().allCurrent()) {
            val rank = c.toDomain().rank
            if ((certsBefore[c.entityType to c.entityId] ?: -1) < rank) {
                val name = if (c.entityType == EntityType.ALBUM) db.albumDao().getById(c.entityId)?.title else db.trackDao().getById(c.entityId)?.title
                news += Achievement("CERTIFICATION", c.entityType, c.entityId, c.level + if (c.multiplier > 1) ":${c.multiplier}" else "", name ?: "—")
            }
        }
        for (p in db.pantheonDao().allCurrent()) {
            val before = PantheonStatus.fromDb(pantheonBefore[p.artistId])?.ordinal ?: -1
            val after = PantheonStatus.fromDb(p.currentStatus)?.ordinal ?: -1
            if (after > before) news += Achievement("PANTHEON", EntityType.ARTIST, p.artistId, p.currentStatus, db.artistDao().getById(p.artistId)?.name ?: "—")
        }
        for (h in db.hallOfFameDao().all()) {
            if (listOf(h.entityType, h.entityId.toString(), h.periodType, h.entryType) !in hofBefore) {
                val name = when (h.entityType) {
                    EntityType.ALBUM -> db.albumDao().getById(h.entityId)?.title
                    EntityType.ARTIST -> db.artistDao().getById(h.entityId)?.name
                    else -> db.trackDao().getById(h.entityId)?.title
                }
                news += Achievement("HALL_OF_FAME", h.entityType, h.entityId, h.entryType, name ?: "—")
            }
        }
        return news
    }

    /* ---------------- Streaks ---------------- */

    suspend fun rebuildStreaks() {
        val dates = db.scrobbleDao().activeDates().map { Dates.parse(it) }
        val rows = mutableListOf<DailyStreakEntity>()
        var run = 0; var best = 0; var bestDate: String? = null
        var prev: java.time.LocalDate? = null
        for (d in dates) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            if (run > best) { best = run; bestDate = d.format(Dates.ISO) }
            rows += DailyStreakEntity(date = d.format(Dates.ISO), hasPlay = true, currentStreak = run, bestStreak = best, bestStreakDate = bestDate)
            prev = d
        }
        val today = Dates.today()
        val result = StreakCalculator.compute(dates, today)
        if (dates.lastOrNull() != today) {
            rows += DailyStreakEntity(date = today.format(Dates.ISO), hasPlay = false, currentStreak = result.current, bestStreak = best, bestStreakDate = bestDate)
        }
        db.withTransaction {
            db.dailyStreakDao().clear()
            db.dailyStreakDao().upsertAll(rows)
        }
    }

    /* ---------------- Sessions (gap = 15 min) ---------------- */

    suspend fun rebuildSessions() {
        val scrobbles = db.scrobbleDao().allConfirmedOrdered()
        db.withTransaction {
            db.sessionDao().clear()
            if (scrobbles.isEmpty()) return@withTransaction
            var start = scrobbles.first().startedAt
            var end = scrobbles.first().startedAt + scrobbles.first().durationListenedMs
            var count = 1
            var app = scrobbles.first().sourceApp
            for (s in scrobbles.drop(1)) {
                if (s.startedAt - end > ScrobbleRules.SESSION_GAP_MS) {
                    db.sessionDao().insert(SessionEntity(startedAt = start, endedAt = end, totalDurationMs = end - start, trackCount = count, sourceApp = app))
                    start = s.startedAt; count = 0; app = s.sourceApp
                }
                end = maxOf(end, s.startedAt + s.durationListenedMs)
                count++
            }
            db.sessionDao().insert(SessionEntity(startedAt = start, endedAt = end, totalDurationMs = end - start, trackCount = count, sourceApp = app))
        }
    }

    /* ---------------- Certifications (dates rétroactives) ---------------- */

    suspend fun rebuildCertifications() {
        val tracks = db.trackDao().topAllTime(limit = Int.MAX_VALUE).first()
        val albums = db.albumDao().topAllTime(limit = Int.MAX_VALUE).first()
        db.withTransaction {
            db.certificationDao().clear()
            db.certificationDao().clearHistory()
            for (t in tracks) {
                certify(EntityType.TRACK, t.track.trackId, t.track.playCount, t.track.firstPlayedAt, CertificationRules.TRACK) { n ->
                    db.scrobbleDao().nthPlayOfTrack(t.track.trackId, n)
                }
            }
            for (a in albums) {
                certify(EntityType.ALBUM, a.album.albumId, a.album.playCount, a.album.firstPlayedAt, CertificationRules.ALBUM) { n ->
                    db.scrobbleDao().nthPlayOfAlbum(a.album.albumId, n)
                }
            }
        }
    }

    private suspend fun certify(
        type: String, id: Long, playCount: Int, firstPlayedAt: Long?,
        thresholds: CertificationRules.Thresholds,
        nthPlayTime: suspend (Int) -> Long?
    ) {
        val reached = thresholds.allReached(playCount)
        if (reached.isEmpty()) return
        var currentCert: Certification? = null
        var currentAt = 0L
        for (cert in reached) {
            val required = thresholds.required(cert)
            val at = nthPlayTime(required) ?: System.currentTimeMillis()
            val timeTo = firstPlayedAt?.let { at - it }
            db.certificationDao().insertHistory(
                CertificationHistoryEntity(entityId = id, entityType = type, level = cert.level.dbName, multiplier = cert.multiplier, certifiedAt = at, playCountAtCert = required, timeToCertifyMs = timeTo)
            )
            currentCert = cert; currentAt = at
        }
        currentCert?.let { c ->
            db.certificationDao().upsert(
                CertificationEntity(entityId = id, entityType = type, level = c.level.dbName, multiplier = c.multiplier, playCountAtCert = thresholds.required(c), certifiedAt = currentAt, timeToCertifyMs = firstPlayedAt?.let { currentAt - it })
            )
        }
    }

    /* ---------------- Panthéon (permanent, Option A ou B) ---------------- */

    suspend fun rebuildPantheon() {
        val artists = db.artistDao().allByPlays().first()
        val trackCerts = db.certificationDao().byType(EntityType.TRACK).first()
        val albumCerts = db.certificationDao().byType(EntityType.ALBUM).first()
        val tracksByArtist = HashMap<Long, MutableList<Certification>>()
        val albumsByArtist = HashMap<Long, MutableList<Certification>>()
        for (c in trackCerts) {
            val cert = c.toDomain()
            db.trackLinkDao().artistIdsForTrack(c.entityId).forEach { tracksByArtist.getOrPut(it) { mutableListOf() } += cert }
        }
        for (c in albumCerts) {
            val cert = c.toDomain()
            db.albumDao().getById(c.entityId)?.let { albumsByArtist.getOrPut(it.artistId) { mutableListOf() } += cert }
        }

        db.withTransaction {
            db.pantheonDao().clear()
            db.pantheonDao().clearHistory()
            for (a in artists) {
                val summary = ArtistCertSummary(tracksByArtist[a.artistId].orEmpty(), albumsByArtist[a.artistId].orEmpty())
                val eval = PantheonRules.evaluate(a.playCount, summary)
                val status = eval.status ?: continue
                // Historique : chaque statut jusqu'au statut actuel, daté via le seuil d'écoutes (approximation rétroactive)
                var statusDate = a.firstPlayedAt ?: System.currentTimeMillis()
                for (s in PantheonStatus.entries) {
                    if (s.ordinal > status.ordinal) break
                    val at = db.scrobbleDao().nthPlayOfArtist(a.artistId, s.playsThreshold) ?: a.lastPlayedAt ?: System.currentTimeMillis()
                    statusDate = at
                    db.pantheonDao().insertHistory(
                        PantheonHistoryEntity(artistId = a.artistId, status = s.dbName, dateReached = at, timeToReachMs = a.firstPlayedAt?.let { at - it }, playCountAtStatus = minOf(a.playCount, s.playsThreshold))
                    )
                }
                db.pantheonDao().upsert(
                    PantheonStatusEntity(artistId = a.artistId, currentStatus = status.dbName, statusDate = statusDate, timeToStatusMs = a.firstPlayedAt?.let { statusDate - it }, reachedViaPlays = eval.viaPlays)
                )
                db.artistDao().setPantheonStatus(a.artistId, status.dbName, statusDate)
            }
        }
    }

    private fun CertificationEntity.toDomain() = Certification(
        level = com.novastats.app.domain.CertLevel.valueOf(level), multiplier = multiplier
    )
}
