package com.novastats.app.data.api

import android.util.Log
import com.novastats.app.data.ApiKeys
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ApiCacheEntity
import com.novastats.app.data.db.entity.ApiReliabilityEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.TrackEntity
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.domain.ApiSource
import com.novastats.app.domain.DataType
import com.novastats.app.domain.Dates
import com.novastats.app.domain.MetaCandidate
import com.novastats.app.domain.MetadataMatching
import com.novastats.app.domain.ScoredCandidate
import com.novastats.app.domain.TitleNormalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Locale

/** État observable de l'enrichissement (affiché dans Réglages → APIs). */
object EnrichmentState {
    data class Snapshot(
        val running: Boolean = false,
        val current: String? = null,
        val processed: Int = 0,
        val found: Int = 0,
        val lastRunAt: Long? = null,
        val log: List<String> = emptyList()
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)

    fun start() = _state.update { it.copy(running = true, processed = 0, found = 0) }
    fun current(label: String?) = _state.update { it.copy(current = label) }
    fun done(found: Boolean) = _state.update { it.copy(processed = it.processed + 1, found = it.found + if (found) 1 else 0) }
    fun stop() = _state.update { it.copy(running = false, current = null, lastRunAt = System.currentTimeMillis()) }
    fun log(msg: String) {
        Log.d("NovaEnrich", msg)
        _state.update { it.copy(log = (listOf("${fmt.format(System.currentTimeMillis())} $msg") + it.log).take(40)) }
    }
}

/**
 * Cascade d'enrichissement (9 APIs) + stratégies de précision :
 *  normalisation · Levenshtein · durée ±5 s · consensus (3 APIs en parallèle) · score de confiance ·
 *  anomalies · cache intelligent (6 mois / 1 mois / négatif 7 jours) · blacklist · fiabilité dynamique.
 */
class MetadataEnricher(private val db: NovaDatabase, private val settings: SettingsRepository) {

    /** 🟡 Correspondance acceptée avec flag (70-89) → notification discrète (branché par NovaStatsApp). */
    var onTrackFlagged: ((trackId: Long, title: String, artist: String, proposal: String, score: Int) -> Unit)? = null

    private val musicBrainz = MusicBrainzApi()
    private val apis: Map<ApiSource, MusicApi> = listOf(
        ITunesApi(), SpotifyApi(), LastFmApi(), musicBrainz, TheAudioDbApi(), DeezerApi(), DiscogsApi(),
        FanartApi(musicBrainz), GoogleImagesApi { settings.tryConsumeGoogleQuota(Dates.today().format(Dates.ISO)) }
    ).associateBy { it.source }

    private fun hasKey(s: ApiSource): Boolean = when (s) {
        ApiSource.SPOTIFY -> ApiKeys.has(ApiKeys.spotifyClientId) && ApiKeys.has(ApiKeys.spotifyClientSecret)
        ApiSource.LASTFM -> ApiKeys.has(ApiKeys.lastFm)
        ApiSource.FANART -> ApiKeys.has(ApiKeys.fanart)
        ApiSource.GOOGLE -> ApiKeys.has(ApiKeys.googleApiKey) && ApiKeys.has(ApiKeys.googleEngineId)
        ApiSource.THEAUDIODB -> true // clé de test "2" par défaut
        else -> true
    }

    /**
     * ⚠️ À corriger — propositions pour le popup de correction : jusqu'à 3 candidats (source + score) issus des
     * sources sans clé ou configurées (hors Google), interrogées en parallèle. Un appui remplit tous les champs.
     */
    suspend fun proposeTrack(title: String, artist: String, album: String?, durationMs: Long?): List<ScoredCandidate> {
        if (title.isBlank()) return emptyList()
        val sources = ApiSource.entries.filter { !it.isLastResort && hasKey(it) && apis.containsKey(it) }.sortedBy { it.trackPriority }.take(6)
        val results: List<MetaCandidate> = coroutineScope {
            sources.map { s -> async { kotlinx.coroutines.withTimeoutOrNull(8_000L) { runCatching { apis.getValue(s).searchTrack(title, artist.ifBlank { "" }) }.getOrDefault(emptyList()) } ?: emptyList() } }.map { it.await() }
        }.flatten()
        val consensus = MetadataMatching.consensusSet(results)
        return results.map { c -> MetadataMatching.scoreTrack(c, title, artist, album, durationMs, c in consensus) }
            .filter { it.score >= 50 }
            .sortedByDescending { it.score }
            .distinctBy { TitleNormalizer.normalizeKey(it.candidate.name) + "|" + TitleNormalizer.normalizeKey(it.candidate.artist ?: "") }
            .take(3)
    }

    /** Sources configurées (pour l'écran Réglages). */
    fun configuredSources(): List<Pair<ApiSource, Boolean>> = ApiSource.entries.map { it to (!it.requiresKey || hasKey(it)) }

    /* ------------------------------------------------------------------ */
    /*  Lot de travail (appelé par EnrichmentWorker)                        */
    /* ------------------------------------------------------------------ */

    data class BatchResult(val processed: Int, val found: Int, val remaining: Int)

    suspend fun runBatch(artists: Int = 10, albums: Int = 10, tracks: Int = 25): BatchResult {
        val now = System.currentTimeMillis()
        var processed = 0; var found = 0
        EnrichmentState.start()
        try {
            db.artistDao().missingPhoto(now, artists).forEach { a ->
                EnrichmentState.current("🎤 ${a.name}")
                val ok = runCatching { enrichArtist(a) }.getOrElse { EnrichmentState.log("⚠️ ${a.name} : ${it.message}"); false }
                processed++; if (ok) found++; EnrichmentState.done(ok)
            }
            db.albumDao().missingCover(now, albums).forEach { al ->
                val artist = db.artistDao().getById(al.artistId)?.name ?: return@forEach
                EnrichmentState.current("💿 ${al.title}")
                val ok = runCatching { enrichAlbum(al, artist) }.getOrElse { EnrichmentState.log("⚠️ ${al.title} : ${it.message}"); false }
                processed++; if (ok) found++; EnrichmentState.done(ok)
            }
            db.trackDao().missingCover(now, tracks).forEach { t ->
                val artist = db.artistDao().getById(t.artistId)?.name ?: return@forEach
                val album = t.albumId?.let { db.albumDao().getById(it) }
                EnrichmentState.current("🎵 ${t.title}")
                val ok = runCatching { enrichTrack(t, artist, album) }.getOrElse { EnrichmentState.log("⚠️ ${t.title} : ${it.message}"); false }
                processed++; if (ok) found++; EnrichmentState.done(ok)
            }
        } finally {
            EnrichmentState.stop()
        }
        val later = System.currentTimeMillis()
        val remaining = db.artistDao().missingPhoto(later, 1).size + db.albumDao().missingCover(later, 1).size + db.trackDao().missingCover(later, 1).size
        EnrichmentState.log("Lot terminé : $processed traités, $found enrichis${if (remaining > 0) ", suite à venir" else ""}")
        return BatchResult(processed, found, remaining)
    }

    /* ------------------------------------------------------------------ */
    /*  Par entité                                                          */
    /* ------------------------------------------------------------------ */

    suspend fun enrichArtist(a: ArtistEntity): Boolean {
        val best = cascade(
            EntityType.ARTIST, a.artistId, DataType.PHOTO, { it.artistPriority },
            query = { api -> api.searchArtist(a.name) },
            score = { c, _ -> MetadataMatching.scoreArtist(c, a.name) }
        )
        if (best == null) { negativeCache(EntityType.ARTIST, a.artistId, DataType.PHOTO); return false }
        val c = best.candidate
        db.artistDao().update(
            a.copy(
                photoUrl = c.imageUrl, photoSource = c.source.label,
                bio = a.bio ?: c.bio, mbid = a.mbid ?: c.mbid, spotifyId = a.spotifyId ?: c.spotifyId
            )
        )
        // Une bio peut venir d'une autre source que la photo (Last.fm / TheAudioDB) — on la récupère si absente
        if (a.bio == null && c.bio == null) fetchBio(a)
        positiveCache(EntityType.ARTIST, a.artistId, DataType.PHOTO, best)
        EnrichmentState.log("✅ ${a.name} → photo ${c.source.label} (${best.score})")
        return true
    }

    private suspend fun fetchBio(a: ArtistEntity) {
        for (s in listOf(ApiSource.THEAUDIODB, ApiSource.LASTFM)) {
            if (!hasKey(s)) continue
            val bio = runCatching { apis.getValue(s).searchArtist(a.name) }.getOrNull()
                ?.firstOrNull { MetadataMatching.similarity(it.name, a.name) >= 0.85 && !it.bio.isNullOrBlank() }?.bio
            if (bio != null) { db.artistDao().getById(a.artistId)?.let { db.artistDao().update(it.copy(bio = bio)) }; return }
        }
    }

    suspend fun enrichAlbum(al: AlbumEntity, artistName: String): Boolean {
        val best = cascade(
            EntityType.ALBUM, al.albumId, DataType.COVER, { it.albumPriority },
            query = { api -> api.searchAlbum(al.title, artistName) },
            score = { c, consensus -> MetadataMatching.scoreAlbum(c, al.title, artistName, consensus) }
        )
        if (best == null || best.candidate.imageUrl == null) { negativeCache(EntityType.ALBUM, al.albumId, DataType.COVER); return false }
        val c = best.candidate
        db.albumDao().update(al.copy(coverUrl = c.imageUrl, coverSource = c.source.label, releaseDate = al.releaseDate ?: c.releaseDate, mbid = al.mbid ?: c.mbid))
        db.albumDao().propagateCoverToTracks(al.albumId, c.imageUrl, c.source.label)
        positiveCache(EntityType.ALBUM, al.albumId, DataType.COVER, best)
        EnrichmentState.log("✅ ${al.title} → pochette ${c.source.label} (${best.score})")
        return true
    }

    suspend fun enrichTrack(t: TrackEntity, artistName: String, album: AlbumEntity?): Boolean {
        val best = cascade(
            EntityType.TRACK, t.trackId, DataType.COVER, { it.trackPriority },
            query = { api -> api.searchTrack(t.title, artistName) },
            score = { c, consensus -> MetadataMatching.scoreTrack(c, t.title, artistName, album?.title, t.durationMs, consensus) }
        )
        if (best == null || best.candidate.imageUrl == null) { negativeCache(EntityType.TRACK, t.trackId, DataType.COVER); return false }
        val c = best.candidate
        db.trackDao().update(
            t.copy(
                coverUrl = c.imageUrl, coverSource = c.source.label,
                durationMs = t.durationMs ?: c.durationMs, genre = t.genre ?: c.genre, mbid = t.mbid ?: c.mbid,
                confidenceScore = minOf(t.confidenceScore, best.score),
                needsReview = t.needsReview || best.score < MetadataMatching.TRUSTED
            )
        )
        // L'album du titre n'a pas de pochette et la source confirme le même album → on la lui donne aussi
        if (album != null && album.coverUrl == null && c.album != null && MetadataMatching.similarity(c.album, album.title) >= 0.85) {
            db.albumDao().update(album.copy(coverUrl = c.imageUrl, coverSource = c.source.label))
            db.albumDao().propagateCoverToTracks(album.albumId, c.imageUrl, c.source.label)
            positiveCache(EntityType.ALBUM, album.albumId, DataType.COVER, best)
        }
        positiveCache(EntityType.TRACK, t.trackId, DataType.COVER, best)
        EnrichmentState.log("✅ ${t.title} → pochette ${c.source.label} (${best.score})")
        if (best.score < MetadataMatching.TRUSTED) runCatching { onTrackFlagged?.invoke(t.trackId, t.title, artistName, "${c.name}${c.artist?.let { " — $it" } ?: ""} · ${c.source.label}", best.score) }
        return true
    }

    /* ------------------------------------------------------------------ */
    /*  Cascade générique                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Interroge les sources par groupes de 3 en parallèle (consensus), score les candidats, s'arrête au premier
     * résultat ≥ 70. Google n'est appelé que si tout le reste a échoué.
     */
    private suspend fun cascade(
        entityType: String,
        entityId: Long,
        dataType: String,
        basePriority: (ApiSource) -> Int,
        query: suspend (MusicApi) -> List<MetaCandidate>,
        score: (MetaCandidate, Boolean) -> ScoredCandidate
    ): ScoredCandidate? {
        val reliability = db.apiCacheDao().allReliability().mapNotNull { r ->
            ApiSource.entries.firstOrNull { it.label == r.apiName }?.let { it to (r.successCount to r.failCount) }
        }.toMap()
        val order = MetadataMatching.orderSources(basePriority, reliability, ::hasKey)
        val blacklisted = db.apiCacheDao().blacklistedUrls(entityType, entityId).toSet()
        val seen = mutableListOf<MetaCandidate>()

        for (group in order.chunked(3)) {
            val results: List<Pair<ApiSource, Result<List<MetaCandidate>>>> = coroutineScope {
                group.map { s -> async { s to runCatching { query(apis.getValue(s)) } } }.map { it.await() }
            }
            results.forEach { (s, r) -> r.exceptionOrNull()?.let { EnrichmentState.log("⚠️ ${s.label} : ${it.message?.take(80)}") } }
            val fresh = results.flatMap { (_, r) -> r.getOrDefault(emptyList()) }
                .filter { it.imageUrl == null || it.imageUrl !in blacklisted }
            seen += fresh
            val consensus = MetadataMatching.consensusSet(seen)

            val scored = fresh.map { c ->
                if (c.source.isLastResort) ScoredCandidate(c, MetadataMatching.ACCEPT, listOf("dernier recours"))
                else score(c, c in consensus)
            }.filter { it.candidate.imageUrl != null } // on cherche toujours une image (pochette / photo)

            val best = scored.filter { it.score >= MetadataMatching.ACCEPT }.maxByOrNull { it.score }
            // Fiabilité : une source "réussit" si elle a fourni le résultat retenu, "échoue" si elle a répondu sans résultat acceptable
            for ((s, r) in results) {
                if (r.isFailure) { bump(s, success = false); continue }
                val provided = best != null && best.candidate.source == s
                val hadAcceptable = scored.any { it.candidate.source == s && it.score >= MetadataMatching.ACCEPT }
                bump(s, success = provided || hadAcceptable)
            }
            if (best != null) return best
        }
        return null
    }

    private suspend fun bump(s: ApiSource, success: Boolean) {
        val existing = db.apiCacheDao().reliabilityFor(s.label)
        val ok = (existing?.successCount ?: 0) + if (success) 1 else 0
        val ko = (existing?.failCount ?: 0) + if (success) 0 else 1
        val rate = if (ok + ko == 0) 100.0 else ok * 100.0 / (ok + ko)
        db.apiCacheDao().upsertReliability(
            ApiReliabilityEntity(
                id = existing?.id ?: 0, apiName = s.label, successCount = ok, failCount = ko, successRate = rate,
                currentPriority = maxOf(s.artistPriority, s.albumPriority, s.trackPriority), updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun positiveCache(entityType: String, entityId: Long, dataType: String, best: ScoredCandidate) {
        val ttl = MetadataMatching.cacheTtlMs(best.score) ?: return
        db.apiCacheDao().clearEntity(entityType, entityId, dataType)
        db.apiCacheDao().insert(
            ApiCacheEntity(
                entityType = entityType, entityId = entityId, source = best.candidate.source.label, dataType = dataType,
                cachedUrl = best.candidate.imageUrl, confidenceScore = best.score, expiresAt = System.currentTimeMillis() + ttl
            )
        )
    }

    private suspend fun negativeCache(entityType: String, entityId: Long, dataType: String) {
        db.apiCacheDao().clearEntity(entityType, entityId, dataType)
        db.apiCacheDao().insert(
            ApiCacheEntity(
                entityType = entityType, entityId = entityId, source = "NONE", dataType = dataType,
                cachedUrl = null, confidenceScore = 0, expiresAt = System.currentTimeMillis() + MetadataMatching.NEGATIVE_TTL_MS
            )
        )
        EnrichmentState.log("❌ $entityType #$entityId : rien trouvé (nouvel essai dans 7 jours)")
    }

    /** Stratégie 11 : l'utilisateur rejette une image → blacklistée pour cette entité, puis nouvelle cascade. */
    suspend fun rejectImage(entityType: String, entityId: Long, url: String) {
        val dataType = if (entityType == EntityType.ARTIST) DataType.PHOTO else DataType.COVER
        db.apiCacheDao().insert(
            ApiCacheEntity(
                entityType = entityType, entityId = entityId, source = "USER", dataType = dataType, cachedUrl = url,
                confidenceScore = 0, expiresAt = Long.MAX_VALUE, isRejected = true, isBlacklisted = true
            )
        )
        when (entityType) {
            EntityType.ARTIST -> db.artistDao().getById(entityId)?.let { db.artistDao().update(it.copy(photoUrl = null, photoSource = null)) }
            EntityType.ALBUM -> db.albumDao().getById(entityId)?.let { db.albumDao().update(it.copy(coverUrl = null, coverSource = null)) }
            else -> db.trackDao().getById(entityId)?.let { db.trackDao().update(it.copy(coverUrl = null, coverSource = null)) }
        }
    }
}
