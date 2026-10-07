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
import com.novastats.app.data.db.entity.TrackAlbumEntity
import com.novastats.app.util.runCatchingCancellable
import com.novastats.app.util.RebuildAudit
import com.novastats.app.domain.ArtistCertSummary
import com.novastats.app.domain.Certification
import com.novastats.app.domain.CertificationRules
import com.novastats.app.domain.Dates
import com.novastats.app.domain.PantheonRules
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.domain.ScrobbleRules
import com.novastats.app.domain.StreakCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Recalcule toutes les tables dérivées à partir des scrobbles confirmés.
 * Appelé après un import, une suppression d'écoute, une fusion dans l'éditeur…
 *
 * Ordre : agrégats → daily_plays / daily_stats / streaks / sessions → certifications → Panthéon.
 * (Snapshots, Billboard, Hall of Fame, Records et Awards seront branchés sur ce pipeline.)
 */
class StatsRebuilder(private val db: NovaDatabase, private val library: LibraryRepository? = null) {

    /** Nouveauté détectée après un recalcul (pour les notifications). */
    data class Achievement(val kind: String, val entityType: String, val entityId: Long, val level: String, val name: String)

    companion object {
        /** Une seule reconstruction globale à la fois, même si plusieurs composants créent leur propre Rebuilder. */
        private val rebuildMutex = Mutex()
    }

    /** Recalcule les caches des racines en un seul scan groupé des scrobbles. À appeler dans une transaction. */
    private suspend fun recomputeRootAggregatesFromScrobbles() {
        val totals = db.trackDao().rootAggregatesFromScrobbles()
        db.trackDao().clearRootAggregates()
        for (total in totals) {
            db.trackDao().updateRootAggregate(total.rootId, total.playCount, total.totalDurationMs)
        }
    }

    /** À appeler dans une transaction pour ne publier aucune fenêtre avec des compteurs partiellement recalculés. */
    private suspend fun recomputeEntityAggregatesInTransaction() {
        db.trackDao().recomputeAggregates()
        db.trackDao().recomputeDiscoveryRanks()
        db.artistDao().recomputeAggregates()
        db.albumDao().recomputeAggregates()
        db.albumDao().alignTrackCovers()
        db.albumDao().alignUserAlbumTrackCovers()
    }

    /** Rebuild les vues qui dépendent directement des écoutes, à appeler dans la transaction de leur source. */
    private suspend fun recomputeDailyAggregatesInTransaction() {
        db.dailyPlayDao().clear()
        db.dailyPlayDao().rebuildFromScrobbles()
        db.dailyStatsDao().clear()
        db.dailyStatsDao().rebuildFromScrobbles()
        recomputeRootAggregatesFromScrobbles()
    }

    /**
     * @param fullBillboard true = reconstruit tous les snapshots (import) ; false = ne recalcule que la période
     *                      courante (après une écoute).
     * @return les nouvelles certifications / statuts Panthéon / entrées Hall of Fame apparus pendant ce recalcul
     *         (vide après un import complet, pour ne pas inonder de notifications).
     */
    suspend fun rebuildAll(fullBillboard: Boolean = true, onProgress: (String) -> Unit = {}): List<Achievement> {
        val context = RebuildAudit.context ?: return rebuildAllAudited(fullBillboard, onProgress)
        RebuildAudit.log(context, db, "🧮 Recalcul lancé (complet=$fullBillboard)")
        return try {
            rebuildAllAudited(fullBillboard, onProgress).also {
                RebuildAudit.log(context, db, "✅ Recalcul terminé (complet=$fullBillboard)")
            }
        } catch (t: Throwable) {
            // L'exception remonte inchangée : seule une ligne de diagnostic est ajoutée.
            val state = runCatching { RebuildAudit.format(RebuildAudit.snapshot(db)) }
                .getOrDefault("comptage indisponible")
            RebuildAudit.write(context, "❌ Recalcul interrompu (${t.javaClass.simpleName}: ${t.message}) — $state")
            throw t
        }
    }

    /** Corps du recalcul. Le verrou est pris par [rebuildAll] — ne pas ré-entrer. */
    private suspend fun rebuildAllAudited(fullBillboard: Boolean, onProgress: (String) -> Unit): List<Achievement> = rebuildMutex.withLock {
        val certsBefore: Map<Pair<String, Long>, Int> = if (fullBillboard) emptyMap() else db.certificationDao().allCurrent().associate { (it.entityType to it.entityId) to it.toDomain().rank }
        val pantheonBefore: Map<Long, String> = if (fullBillboard) emptyMap() else db.pantheonDao().allCurrent().associate { it.artistId to it.currentStatus }
        val hofBefore: Set<List<String>> = if (fullBillboard) emptySet() else db.hallOfFameDao().all().map { listOf(it.entityType, it.entityId.toString(), it.periodType, it.entryType) }.toSet()

        // Chaque étape est journalisée AVANT d'être exécutée : si le process meurt en route,
        // la dernière ligne du journal nomme l'étape en cours et le comptage qui la précédait.
        val auditContext = RebuildAudit.context
        suspend fun audit(step: String) {
            if (auditContext != null) RebuildAudit.step(auditContext, db, "🧮 $step")
        }

        audit("1/8 Versions + agrégats + écoutes quotidiennes")
        onProgress("Versions, agrégats titres et écoutes quotidiennes…")
        // Le repli d'une version déplace les scrobbles du root et supprime parfois le dernier titre visible.
        // Publier les compteurs + daily_plays dans la même transaction évite que les listes paraissent vides.
        db.withTransaction {
            collapseEmptyRootsInTransaction()
            recomputeEntityAggregatesInTransaction()
            recomputeDailyAggregatesInTransaction()
        }
        // Règle 13 : un duo arrivé avant les titres solo a créé un album homonyme chez le partenaire → fusion continue.
        // Les play_count sont déjà à jour pour choisir le bon album à conserver.
        if (runCatchingCancellable { AlbumSharing.mergeDuoSplits(db) }.getOrDefault(0) > 0) {
            library?.clearCaches()
            db.withTransaction {
                recomputeEntityAggregatesInTransaction()
                recomputeDailyAggregatesInTransaction()
            }
        }

        audit("2/8 Streaks")
        onProgress("Streaks…")
        rebuildStreaks()

        audit("3/8 Sessions")
        onProgress("Sessions…")
        rebuildSessions()

        audit("4/8 Certifications (vide certifications puis recalcule)")
        onProgress("Certifications…")
        rebuildCertifications()

        audit("5/8 Panthéon (vide pantheon_status puis recalcule)")
        onProgress("Panthéon…")
        rebuildPantheon()

        audit("6/8 Billboard")
        onProgress("Billboard…")
        val billboard = BillboardEngine(db)
        if (fullBillboard) billboard.rebuildAll(onProgress) else billboard.refreshCurrent()

        audit("7/8 Records (vide records_cache puis recalcule)")
        onProgress("Records…")
        RecordsEngine(db).rebuildAll(onProgress)

        audit("8/8 Nova Awards")
        onProgress("Nova Awards…")
        if (fullBillboard) AwardsEngine(db).rebuildAll() else AwardsEngine(db).refreshAll()

        if (fullBillboard) return@withLock emptyList()
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
        news
    }

    /** Répare rapidement les totaux depuis les scrobbles confirmés, sans re-résoudre chaque écoute ni reconstruire les charts. */
    suspend fun repairTrackRootTotals() = rebuildMutex.withLock {
        db.withTransaction {
            db.dailyPlayDao().clear()
            db.dailyPlayDao().rebuildFromScrobbles()
            // Agrégation groupée (une seule lecture de scrobbles, pas de requête par racine).
            recomputeRootAggregatesFromScrobbles()
        }
    }

    /* ---------------- Versions ---------------- */

    /**
     * Une « version avec invité » n'existe que face à une version SOLO réellement écoutée. Si le root solo n'a aucune
     * écoute confirmée propre (créé par un libellé incomplet, ou vidé par un ré-import / une correction), il n'y a pas de
     * version solo : les versions sont repliées dans le root (écoutes + artistes + albums), qui redevient un titre unique
     * crédité à tous les artistes. L'identifiant du root est conservé (certifications, historique Billboard, records).
     */
    /** Caller owns the Room transaction so moving scrobbles and deleting their version rows stays atomic. */
    private suspend fun collapseEmptyRootsInTransaction() {
        val roots = db.trackDao().emptyRootsWithVersions()
        if (roots.isEmpty()) return
        // 🔎 Diagnostic : c'est le seul endroit du recalcul qui SUPPRIME des écoutes
        // (dropDuplicatesAgainst) et des titres (delete). On encadre chaque version.
        val auditContext = RebuildAudit.context
        var before: RebuildAudit.Counts? = if (auditContext != null) RebuildAudit.snapshot(db) else null
        for (root in roots) {
            val versions = db.trackDao().versionsOf(root.trackId)
            var rootAlbumId = root.albumId
            for (v in versions) {
                db.scrobbleDao().dropDuplicatesAgainst(v.trackId, root.trackId)
                db.scrobbleDao().moveAll(v.trackId, root.trackId)
                db.trackLinkDao().copyArtistLinks(v.trackId, root.trackId)
                db.trackLinkDao().copyAlbumLinks(v.trackId, root.trackId)
                if (rootAlbumId == null && v.albumId != null) {
                    db.trackDao().setAlbum(root.trackId, v.albumId)
                    rootAlbumId = v.albumId
                }
                rootAlbumId?.let { db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = root.trackId, albumId = it)) }
                db.trackDao().fillCover(root.trackId, v.coverUrl, v.coverSource)
                db.trackDao().delete(v.trackId) // track_artists / track_albums en cascade
                val previous = before
                if (auditContext != null && previous != null) {
                    val after = RebuildAudit.snapshot(db)
                    RebuildAudit.diff(previous, after)?.let { delta ->
                        RebuildAudit.write(auditContext, "🧬 Repli « ${v.title} » → racine #${root.trackId} : $delta")
                    }
                    before = after
                }
            }
        }
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
            // Albums partagés (artist_id NULL) : jamais crédités à un artiste
            db.albumDao().getById(c.entityId)?.artistId?.let { owner -> albumsByArtist.getOrPut(owner) { mutableListOf() } += cert }
        }

        db.withTransaction {
            db.pantheonDao().clear()
            db.pantheonDao().clearHistory()
            // Le statut est aussi dénormalisé dans artists pour les classements et pop-ups : efface les valeurs
            // précédentes avant le recalcul, notamment quand une suppression fait retomber un artiste sous le seuil.
            db.artistDao().clearPantheonStatus()
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
