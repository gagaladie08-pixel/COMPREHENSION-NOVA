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

    suspend fun rebuildAll(onProgress: (String) -> Unit = {}) {
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
