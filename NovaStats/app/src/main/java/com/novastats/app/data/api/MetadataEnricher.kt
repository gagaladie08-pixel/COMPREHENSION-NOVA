package com.novastats.app.data.api

import android.util.Log
import androidx.room.withTransaction
import com.novastats.app.data.ApiKeys
import com.novastats.app.data.api.HttpJson.HttpException
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ApiCacheEntity
import com.novastats.app.data.db.entity.ApiReliabilityEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.EditHistoryEntity
import com.novastats.app.data.db.entity.TrackAlbumEntity
import com.novastats.app.data.db.entity.TrackEntity
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.domain.ApiSource
import com.novastats.app.domain.AlbumReleaseKind
import com.novastats.app.domain.AlbumReleasePolicy
import com.novastats.app.domain.DataType
import com.novastats.app.domain.Dates
import com.novastats.app.domain.MetaCandidate
import com.novastats.app.domain.MetadataMatching
import com.novastats.app.domain.ScoredCandidate
import com.novastats.app.util.runCatchingCancellable
import com.novastats.app.domain.TitleNormalizer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Proposition d'album vérifiée pour un morceau; sérialisée dans api_cache pour l'afficher plus tard dans l'éditeur. */
@Serializable
data class AlbumMatchSuggestion(
    val albumTitle: String,
    val artistName: String,
    val releaseKind: String,
    val releaseDate: String? = null,
    val coverUrl: String? = null,
    val confidence: Int,
    val sources: List<String> = emptyList(),
    val tracklistVerified: Boolean = false,
    val albumMbid: String? = null,
    val isCompilation: Boolean = false,
    val isShared: Boolean = false,
    val reasons: List<String> = emptyList()
) {
    val kind: AlbumReleaseKind
        get() = runCatching { AlbumReleaseKind.valueOf(releaseKind) }.getOrDefault(AlbumReleaseKind.UNKNOWN)
}

@Serializable
private data class AlbumMatchSuggestionPayload(val candidates: List<AlbumMatchSuggestion>)

private data class TrackAlbumEvidence(
    val source: ApiSource,
    val apiCandidate: MetaCandidate,
    val albumTitle: String,
    val trackTitle: String,
    val trackArtist: String,
    val durationMs: Long?,
    val releaseDate: String?,
    val coverUrl: String?,
    val externalAlbumId: String?,
    val albumMbid: String?,
    val releaseType: String?,
    val tracklistVerified: Boolean,
    val score: Int
)

/** État observable de l'enrichissement (affiché dans Réglages → APIs). */
object EnrichmentState {
    data class Snapshot(
        val running: Boolean = false,
        val current: String? = null,
        val processed: Int = 0,
        val found: Int = 0,
        /** Nombre total d'entités du passage en cours (0 = lot standard, inconnu). */
        val total: Int = 0,
        /** Libellé du passage en cours (« Ré-enrichissement complet », « Ré-enrichissement (12) »…). */
        val mode: String? = null,
        val lastRunAt: Long? = null,
        val log: List<String> = emptyList(),
        /** Dernière erreur HTTP/réseau par source (label → « 12/10 09:13 · HTTP 401 · clé ou token refusé »). */
        val errors: Map<String, String> = emptyMap()
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.FRANCE).withZone(ZoneId.systemDefault())
    private val dayFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRANCE).withZone(ZoneId.systemDefault())
    private var prefs: android.content.SharedPreferences? = null

    /** Branche la persistance des dernières erreurs (SharedPreferences) — appelé par NovaStatsApp. */
    fun attach(context: android.content.Context) {
        val p = context.getSharedPreferences("nova_api_health", android.content.Context.MODE_PRIVATE)
        prefs = p
        val stored = p.all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
        _state.update { it.copy(errors = stored) }
    }

    /** Une source vient d'échouer (exception réseau / HTTP) : mémorise une explication lisible. */
    fun error(sourceLabel: String, t: Throwable) {
        val text = "${dayFmt.format(Instant.ofEpochMilli(System.currentTimeMillis()))} · ${describe(t)}"
        _state.update { it.copy(errors = it.errors + (sourceLabel to text)) }
        prefs?.edit()?.putString(sourceLabel, text)?.apply()
    }

    /** Une source vient de répondre correctement : efface son erreur mémorisée. */
    fun clearError(sourceLabel: String) {
        if (sourceLabel !in _state.value.errors) return
        _state.update { it.copy(errors = it.errors - sourceLabel) }
        prefs?.edit()?.remove(sourceLabel)?.apply()
    }

    fun clearAllErrors() {
        _state.update { it.copy(errors = emptyMap()) }
        prefs?.edit()?.clear()?.apply()
    }

    /** Traduit une exception en cause probable, affichée telle quelle dans Réglages → APIs. */
    fun describe(t: Throwable): String = when (t) {
        is HttpException -> "HTTP ${t.code} · " + when (t.code) {
            400 -> "requête refusée (paramètres)"
            401 -> "clé ou token refusé"
            403 -> "accès interdit (quota épuisé, droits ou abonnement requis)"
            404 -> "introuvable"
            429 -> "trop de requêtes (quota par minute dépassé)"
            in 500..599 -> "serveur en panne"
            else -> "erreur"
        }
        is java.net.UnknownHostException -> "pas de réseau (DNS)"
        is java.net.SocketTimeoutException -> "délai dépassé"
        is javax.net.ssl.SSLException -> "erreur SSL"
        is java.io.IOException -> "erreur réseau : ${t.message?.take(60) ?: t.javaClass.simpleName}"
        else -> "${t.javaClass.simpleName}${t.message?.let { " : ${it.take(60)}" } ?: ""}"
    }

    fun start(total: Int = 0, mode: String? = null) = _state.update { it.copy(running = true, processed = 0, found = 0, total = total, mode = mode) }
    fun current(label: String?) = _state.update { it.copy(current = label) }
    fun done(found: Boolean) = _state.update { it.copy(processed = it.processed + 1, found = it.found + if (found) 1 else 0) }
    fun stop() = _state.update { it.copy(running = false, current = null, mode = null, lastRunAt = System.currentTimeMillis()) }
    fun log(msg: String) {
        Log.d("NovaEnrich", msg)
        _state.update { it.copy(log = (listOf("${fmt.format(Instant.ofEpochMilli(System.currentTimeMillis()))} $msg") + it.log).take(40)) }
    }
}

/**
 * Cascade d'enrichissement (sources gratuites : iTunes · Deezer · MusicBrainz/CAA · Last.fm · Discogs · Genius ·
 * Fanart.tv · Wikidata · TheAudioDB · YouTube en dernier recours) + stratégies de précision :
 *  normalisation · Levenshtein · durée ±5 s · consensus (3 APIs en parallèle) · score de confiance ·
 *  anomalies · cache intelligent (6 mois / 1 mois / négatif 7 jours) · blacklist · fiabilité dynamique.
 */
class MetadataEnricher(private val db: NovaDatabase, private val settings: SettingsRepository) {

    /** 🟡 Correspondance acceptée avec flag (70-89) → notification discrète (branché par NovaStatsApp). */
    var onTrackFlagged: (suspend (trackId: Long, title: String, artist: String, proposal: String, score: Int) -> Unit)? = null
    /** Invalidation du cache de résolution après l'association d'un album trouvé par cascade. */
    var onAlbumLinked: (suspend (trackId: Long) -> Unit)? = null

    private val albumMatchJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val albumMatchSources = listOf(ApiSource.MUSICBRAINZ, ApiSource.DEEZER, ApiSource.ITUNES, ApiSource.LASTFM, ApiSource.THEAUDIODB)
    private val musicBrainz = MusicBrainzApi()
    private val apis: Map<ApiSource, MusicApi> = listOf(
        ITunesApi(), DeezerApi(), musicBrainz, LastFmApi(), DiscogsApi(), GeniusApi(),
        FanartApi(musicBrainz), WikidataApi(), TheAudioDbApi(),
        YouTubeApi { settings.tryConsumeGoogleQuota(Dates.today().format(Dates.ISO)) }
    ).associateBy { it.source }

    private fun hasKey(s: ApiSource): Boolean = when (s) {
        ApiSource.LASTFM -> ApiKeys.has(ApiKeys.lastFm)
        ApiSource.FANART -> ApiKeys.has(ApiKeys.fanart)
        ApiSource.DISCOGS -> ApiKeys.has(ApiKeys.discogsToken) // sans token : HTTP 401 → on n'appelle pas
        ApiSource.GENIUS -> ApiKeys.has(ApiKeys.genius)
        ApiSource.YOUTUBE -> ApiKeys.has(ApiKeys.youtube)
        ApiSource.SPOTIFY, ApiSource.GOOGLE -> false // retirées
        ApiSource.THEAUDIODB -> true // clé publique "123" par défaut
        else -> true
    }

    /* ------------------------------------------------------------------ */
    /*  Contexte bibliothèque (0.8.7) : vérification par les titres déjà enregistrés  */
    /* ------------------------------------------------------------------ */

    private val tracklistCache = android.util.LruCache<String, List<String>>(400)
    private val artistTitlesCache = android.util.LruCache<String, List<String>>(200)
    private val NONE = emptyList<String>()

    /** Liste de pistes de l'album du candidat (null = source sans vérification possible). Mise en cache par (source, id). */
    private suspend fun tracklistOf(c: MetaCandidate): List<String>? {
        val id = c.externalAlbumId ?: return null
        val api = apis[c.source] ?: return null
        val key = "${c.source.name}|$id"
        tracklistCache.get(key)?.let { return it.takeIf { l -> l !== NONE } }
        val list = runCatchingCancellable { api.albumTracks(id) }.getOrNull()
        tracklistCache.put(key, list ?: NONE)
        return list
    }

    /**
     * Recherche les albums d'un morceau sans utiliser l'album brut du lecteur. Une réponse en cache évite de
     * rappeler les mêmes sources; [forceRefresh] est réservé à l'action explicite de l'utilisateur.
     */
    suspend fun findTrackAlbumCandidates(trackId: Long, forceRefresh: Boolean = false): List<AlbumMatchSuggestion> {
        val track = db.trackDao().getById(trackId) ?: return emptyList()
        if (track.albumId != null || db.trackLinkDao().trackAlbumsForTrack(trackId).isNotEmpty() || db.scrobbleDao().hasAlbumForTrack(trackId)) return emptyList()
        if (forceRefresh) {
            db.apiCacheDao().clearEntity(EntityType.TRACK, trackId, DataType.ALBUM_MATCH)
        } else {
            db.apiCacheDao().valid(EntityType.TRACK, trackId, DataType.ALBUM_MATCH)?.let { cached ->
                if (cached.source == "NONE") return emptyList()
                val decoded = runCatching {
                    albumMatchJson.decodeFromString(AlbumMatchSuggestionPayload.serializer(), cached.cachedUrl.orEmpty()).candidates
                }.getOrNull()
                if (decoded != null) {
                    val ranked = rankAlbumSuggestions(decoded)
                    automaticAlbumSuggestion(ranked)?.let { suggestion ->
                        if (applyTrackAlbumSuggestion(trackId, suggestion, automatic = true)) return emptyList()
                    }
                    return ranked
                }
                db.apiCacheDao().clearEntity(EntityType.TRACK, trackId, DataType.ALBUM_MATCH)
            }
        }
        val artistName = db.artistDao().getById(track.artistId)?.name?.takeIf { it.isNotBlank() } ?: return emptyList()
        val suggestions = discoverAlbumCandidates(track, artistName)
        val automatic = automaticAlbumSuggestion(suggestions)
        if (automatic != null && applyTrackAlbumSuggestion(trackId, automatic, automatic = true)) return emptyList()
        val now = System.currentTimeMillis()
        db.apiCacheDao().clearEntity(EntityType.TRACK, trackId, DataType.ALBUM_MATCH)
        db.apiCacheDao().insert(
            ApiCacheEntity(
                entityType = EntityType.TRACK,
                entityId = trackId,
                source = if (suggestions.isEmpty()) "NONE" else "ALBUM_MATCH",
                dataType = DataType.ALBUM_MATCH,
                cachedUrl = suggestions.takeIf { it.isNotEmpty() }?.let {
                    albumMatchJson.encodeToString(AlbumMatchSuggestionPayload.serializer(), AlbumMatchSuggestionPayload(it))
                },
                confidenceScore = suggestions.maxOfOrNull { it.confidence } ?: 0,
                expiresAt = now + if (suggestions.isEmpty()) MetadataMatching.NEGATIVE_TTL_MS else 30L * 24 * 3_600_000
            )
        )
        return suggestions
    }

    /** Applique une proposition vérifiée au morceau pour les prochaines écoutes seulement. */
    suspend fun confirmTrackAlbumSuggestion(trackId: Long, suggestion: AlbumMatchSuggestion): Boolean =
        applyTrackAlbumSuggestion(trackId, suggestion, automatic = false)

    /** Sélection unique, vérifiée par liste de pistes et corroborée par deux catalogues minimum. */
    private fun automaticAlbumSuggestion(suggestions: List<AlbumMatchSuggestion>): AlbumMatchSuggestion? {
        val only = suggestions.singleOrNull() ?: return null
        return only.takeIf {
            it.confidence >= 95 && it.tracklistVerified && it.sources.distinct().size >= 2 &&
                AlbumReleasePolicy.isAutoLinkable(it.kind)
        }
    }

    private suspend fun applyTrackAlbumSuggestion(
        trackId: Long,
        suggestion: AlbumMatchSuggestion,
        automatic: Boolean
    ): Boolean {
        val linkedAt = System.currentTimeMillis()
        val albumId = db.withTransaction {
            val track = db.trackDao().getById(trackId) ?: return@withTransaction null
            if (track.albumId != null || db.trackLinkDao().trackAlbumsForTrack(trackId).isNotEmpty() || db.scrobbleDao().hasAlbumForTrack(trackId)) {
                return@withTransaction null
            }
            val rawTitle = suggestion.albumTitle.trim()
            val title = TitleNormalizer.normalizeAlbumTitle(rawTitle)
            if (title.isBlank()) return@withTransaction null
            val shared = suggestion.isShared || suggestion.isCompilation
            val ownerId = if (shared) null else track.artistId
            val existing = if (shared) db.albumDao().findShared(title) else db.albumDao().findByTitleAndArtist(title, track.artistId)
            val linkedAlbumId = existing?.albumId ?: db.albumDao().insert(
                AlbumEntity(
                    title = title,
                    titleRaw = rawTitle,
                    artistId = ownerId,
                    coverUrl = suggestion.coverUrl,
                    coverSource = suggestion.coverUrl?.let { suggestion.sources.firstOrNull() },
                    releaseDate = suggestion.releaseDate,
                    isCompilation = suggestion.isCompilation,
                    mbid = suggestion.albumMbid
                )
            )
            if (existing != null) {
                val enriched = existing.copy(
                    coverUrl = existing.coverUrl ?: suggestion.coverUrl,
                    coverSource = existing.coverSource ?: suggestion.coverUrl?.let { suggestion.sources.firstOrNull() },
                    releaseDate = existing.releaseDate ?: suggestion.releaseDate,
                    mbid = existing.mbid ?: suggestion.albumMbid,
                    isCompilation = existing.isCompilation || suggestion.isCompilation
                )
                if (enriched != existing) db.albumDao().update(enriched)
            }
            db.trackDao().setAlbumAssociation(trackId, linkedAlbumId, linkedAt)
            db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = trackId, albumId = linkedAlbumId))
            db.editorDao().insertEdit(
                EditHistoryEntity(
                    type = if (automatic) "API_ALBUM_MATCH" else "API_ALBUM_CONFIRM",
                    entityType = "TRACK",
                    entityId = trackId,
                    before = "Aucun album",
                    after = title,
                    extraData = "albumId=$linkedAlbumId;score=${suggestion.confidence};sources=${suggestion.sources.joinToString(",")};kind=${suggestion.releaseKind};futureOnly=1"
                )
            )
            db.editorDao().trimTo50()
            linkedAlbumId
        } ?: return false
        db.apiCacheDao().clearEntity(EntityType.TRACK, trackId, DataType.ALBUM_MATCH)
        onAlbumLinked?.invoke(trackId)
        EnrichmentState.log(
            "💿 ${if (automatic) "Album associé" else "Album confirmé"} pour le titre #$trackId → « ${suggestion.albumTitle} » (${suggestion.confidence} %, prochaines écoutes uniquement)"
        )
        return albumId > 0L
    }

    private suspend fun discoverAlbumCandidates(track: TrackEntity, artistName: String): List<AlbumMatchSuggestion> {
        val enabled = albumMatchSources.filter { hasKey(it) && apis.containsKey(it) }
        val rawTrackTitle = track.titleRaw.takeIf { it.isNotBlank() } ?: track.title
        val versionTags = audibleVersionTags(rawTrackTitle)
        val searchTitle = if (versionTags.isNotEmpty()) rawTrackTitle else track.title
        val comparableTitle = TitleNormalizer.normalizeTitle(rawTrackTitle).title
        val directResults = mutableListOf<Pair<ApiSource, MetaCandidate>>()

        for (group in enabled.chunked(3)) {
            val results = coroutineScope {
                group.map { source -> async {
                    val result = runCatchingCancellable {
                        withTimeoutOrNull(18_000L) {
                            if (source == ApiSource.MUSICBRAINZ) musicBrainz.searchTrackAlbumCandidates(searchTitle, artistName)
                            else apis.getValue(source).searchTrack(searchTitle, artistName)
                        }
                    }
                    val found = result.getOrNull()
                    when {
                        result.isFailure -> EnrichmentState.error(source.label, result.exceptionOrNull()!!)
                        found == null -> EnrichmentState.log("⚠️ ${source.label} : délai dépassé pour l'album de « ${track.title} »")
                        else -> EnrichmentState.clearError(source.label)
                    }
                    source to found.orEmpty()
                } }.map { it.await() }
            }
            results.forEach { (source, found) -> directResults += found.map { source to it } }
        }

        fun evidence(source: ApiSource, candidate: MetaCandidate): TrackAlbumEvidence? {
            val albumTitle = candidate.album?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (!trackTitleMatchesForAlbum(track, candidate.name)) return null
            val candidateTitle = TitleNormalizer.normalizeTitle(candidate.name).title
            val score = preliminaryAlbumMatchScore(
                comparableTitle, artistName, candidateTitle, candidate.artist, track.durationMs, candidate.durationMs
            ) ?: return null
            return TrackAlbumEvidence(
                source = source,
                apiCandidate = candidate,
                albumTitle = albumTitle,
                trackTitle = candidate.name,
                trackArtist = candidate.artist.orEmpty(),
                durationMs = candidate.durationMs,
                releaseDate = candidate.releaseDate,
                coverUrl = candidate.imageUrl,
                externalAlbumId = candidate.externalAlbumId,
                albumMbid = candidate.externalAlbumId.takeIf { source == ApiSource.MUSICBRAINZ },
                releaseType = candidate.releaseType,
                tracklistVerified = false,
                score = (score - albumEditionPenalty(albumTitle)).coerceIn(0, 80)
            )
        }

        val primaryEvidence = directResults.mapNotNull { (source, candidate) -> evidence(source, candidate) }
        val candidates = primaryEvidence.toMutableList()

        // Discogs ne fournit pas le titre de la piste dans sa recherche de sortie : il ne sert qu'à confirmer
        // quelques albums déjà proposés par une source piste→album, jamais à les inventer seul.
        if (hasKey(ApiSource.DISCOGS) && primaryEvidence.isNotEmpty()) {
            val seeds = primaryEvidence.distinctBy { TitleNormalizer.normalizeKey(TitleNormalizer.normalizeAlbumTitle(it.albumTitle)) }
                .sortedByDescending { it.score }.take(3)
            val discogs = apis[ApiSource.DISCOGS] as? DiscogsApi
            if (discogs != null) {
                val discogsResults = coroutineScope {
                    seeds.map { seed -> async {
                        val result = runCatchingCancellable {
                            withTimeoutOrNull(18_000L) { discogs.searchAlbum(seed.albumTitle, artistName) }
                        }
                        when {
                            result.isFailure -> EnrichmentState.error(ApiSource.DISCOGS.label, result.exceptionOrNull()!!)
                            result.getOrNull() == null -> EnrichmentState.log("⚠️ Discogs : délai dépassé pour « ${seed.albumTitle} »")
                            else -> EnrichmentState.clearError(ApiSource.DISCOGS.label)
                        }
                        seed to result.getOrNull().orEmpty()
                    } }.map { it.await() }
                }.flatMap { (seed, found) -> found.map { seed to it } }
                discogsResults.forEach { (seed, candidate) ->
                    val sameAlbum = MetadataMatching.similarity(
                        TitleNormalizer.normalizeAlbumTitle(candidate.name), TitleNormalizer.normalizeAlbumTitle(seed.albumTitle)
                    ) >= 0.85
                    if (sameAlbum) {
                        val score = preliminaryAlbumMatchScore(
                            comparableTitle, artistName, comparableTitle, artistName, track.durationMs, null
                        ) ?: 0
                        candidates += TrackAlbumEvidence(
                            source = ApiSource.DISCOGS,
                            apiCandidate = candidate,
                            albumTitle = candidate.name,
                            trackTitle = track.title,
                            trackArtist = artistName,
                            durationMs = null,
                            releaseDate = candidate.releaseDate ?: seed.releaseDate,
                            coverUrl = candidate.imageUrl ?: seed.coverUrl,
                            externalAlbumId = candidate.externalAlbumId,
                            albumMbid = null,
                            releaseType = candidate.releaseType,
                            tracklistVerified = false,
                            score = (score - albumEditionPenalty(candidate.name)).coerceIn(0, 80)
                        )
                    }
                }
            }
        }

        val toVerify = candidates.distinctBy {
            "${it.source.name}|${it.externalAlbumId ?: TitleNormalizer.normalizeKey(TitleNormalizer.normalizeAlbumTitle(it.albumTitle))}"
        }.sortedByDescending { it.score }.take(18)
        val verified = coroutineScope {
            toVerify.map { item -> async {
                val titles = if (item.externalAlbumId == null) null else runCatchingCancellable { tracklistOf(item.apiCandidate) }.getOrNull()
                val present = titles?.any { title -> trackTitleMatchesForAlbum(track, title) } == true
                if (titles != null && !present) null
                else {
                    val kind = AlbumReleasePolicy.classify(item.releaseType, item.albumTitle)
                    val durationReason = item.durationMs?.let { duration ->
                        val wantedDuration = track.durationMs
                        if (wantedDuration == null) null else {
                            val diff = kotlin.math.abs(duration - wantedDuration)
                            when {
                                diff <= MetadataMatching.EXACT_DURATION_MS -> "durée concordante"
                                diff <= MetadataMatching.DURATION_TOLERANCE_MS -> "durée proche"
                                else -> "durée différente"
                            }
                        }
                    }
                    val score = (item.score + if (present) 20 else 0).coerceIn(0, 100)
                    item.copy(tracklistVerified = present, score = score) to listOfNotNull(
                        "titre et artiste concordants",
                        durationReason,
                        if (present) "titre et version présents dans la liste de pistes" else "liste de pistes non vérifiable",
                        kind.label
                    )
                }
            } }.map { it.await() }
        }.filterNotNull()

        if (verified.isEmpty()) return emptyList()
        val knownKinds = verified.groupBy {
            TitleNormalizer.normalizeKey(TitleNormalizer.normalizeAlbumTitle(it.first.albumTitle))
        }.mapValues { (_, rows) ->
            rows.map { AlbumReleasePolicy.classify(it.first.releaseType, it.first.albumTitle) }
                .filter { it != AlbumReleaseKind.UNKNOWN }.toSet()
        }
        val groupable = verified.filter { (item, _) ->
            val kind = AlbumReleasePolicy.classify(item.releaseType, item.albumTitle)
            kind != AlbumReleaseKind.UNKNOWN || knownKinds[
                TitleNormalizer.normalizeKey(TitleNormalizer.normalizeAlbumTitle(item.albumTitle))
            ].isNullOrEmpty()
        }
        val grouped = groupable.groupBy { (item, _) ->
            val titleKey = TitleNormalizer.normalizeKey(TitleNormalizer.normalizeAlbumTitle(item.albumTitle))
            val kind = AlbumReleasePolicy.classify(item.releaseType, item.albumTitle)
            titleKey to kind
        }
        val suggestions = grouped.map { (key, rows) ->
            val items = rows.map { it.first }
            val best = items.maxByOrNull { it.score }!!
            val sources = items.map { it.source.label }.distinct().sorted()
            val kind = key.second
            val score = (best.score + if (sources.size >= 2) 5 else 0).coerceAtMost(100)
            val isCompilation = kind == AlbumReleaseKind.COMPILATION
            AlbumMatchSuggestion(
                albumTitle = best.albumTitle,
                artistName = artistName,
                releaseKind = kind.name,
                releaseDate = items.mapNotNull { it.releaseDate }.minOrNull(),
                coverUrl = best.coverUrl ?: items.firstNotNullOfOrNull { it.coverUrl },
                confidence = score,
                sources = sources,
                tracklistVerified = items.any { it.tracklistVerified },
                albumMbid = items.firstNotNullOfOrNull { it.albumMbid },
                isCompilation = isCompilation,
                isShared = isCompilation || TitleNormalizer.isSharedAlbum(best.albumTitle, null),
                reasons = rows.flatMap { it.second }.distinct() + if (sources.size >= 2) "plusieurs sources concordent" else "source unique"
            )
        }
        // Une sortie vérifiée de type Album/EP prévaut sur le single; compilation seulement en dernier recours.
        val verifiedPriority = suggestions.filter { it.tracklistVerified }.minOfOrNull { it.kind.priority }
        val preferred = if (verifiedPriority == null) suggestions else suggestions.filter { it.kind.priority == verifiedPriority }
        return rankAlbumSuggestions(preferred)
    }

    private fun trackTitleMatchesForAlbum(track: TrackEntity, candidateTitle: String): Boolean {
        val wantedRaw = track.titleRaw.takeIf { it.isNotBlank() } ?: track.title
        if (!AlbumReleasePolicy.trackVersionsCompatible(wantedRaw, candidateTitle, track.isRemix)) return false
        val wanted = TitleNormalizer.normalizeTitle(wantedRaw).title
        val candidate = candidateTitle.replace(
            Regex("""^\s*(?:[A-Z]\d+(?:-\d+)?|\d+(?:-\d+)*)(?:\s*[.)-]\s*|\s+)"""), ""
        ).let { TitleNormalizer.normalizeTitle(it).title }
        return MetadataMatching.titlesMatch(TitleNormalizer.normalizeKey(wanted), TitleNormalizer.normalizeKey(candidate))
    }


    private fun albumEditionPenalty(title: String): Int =
        if (TitleNormalizer.normalizeKey(TitleNormalizer.normalizeAlbumTitle(title)) != TitleNormalizer.normalizeKey(title)) 6 else 0

    private fun preliminaryAlbumMatchScore(
        wantedTitle: String,
        wantedArtist: String,
        candidateTitle: String,
        candidateArtist: String?,
        wantedDuration: Long?,
        candidateDuration: Long?
    ): Int? {
        val titleSimilarity = MetadataMatching.similarity(candidateTitle, wantedTitle)
        val artistSimilarity = candidateArtist?.let { MetadataMatching.similarity(it, wantedArtist) } ?: 0.0
        if (titleSimilarity < 0.85 || artistSimilarity < 0.85) return null
        fun points(similarity: Double, max: Int): Int = when {
            similarity >= 0.999 -> max
            similarity >= 0.90 -> (max * 0.85).toInt()
            else -> (max * 0.70).toInt()
        }
        var score = points(titleSimilarity, 35) + points(artistSimilarity, 35)
        if (wantedDuration != null && candidateDuration != null) {
            val diff = kotlin.math.abs(wantedDuration - candidateDuration)
            score += when {
                diff <= MetadataMatching.EXACT_DURATION_MS -> 10
                diff <= MetadataMatching.DURATION_TOLERANCE_MS -> 6
                diff > MetadataMatching.WRONG_DURATION_MS -> -15
                else -> 0
            }
        }
        return score.coerceIn(0, 80)
    }

    private fun rankAlbumSuggestions(suggestions: List<AlbumMatchSuggestion>): List<AlbumMatchSuggestion> =
        suggestions.sortedWith(compareBy({ it.kind.priority }, { -it.confidence }, { it.releaseDate ?: "9999" }, { it.albumTitle.lowercase(Locale.ROOT) }))


    /** Titres connus de l'artiste candidat ; les sources « mbid » (Fanart, Wikidata, TheAudioDB, Last.fm) passent par MusicBrainz. */
    private suspend fun artistTitlesOf(c: MetaCandidate): List<String>? {
        val id = c.externalArtistId ?: c.mbid ?: return null
        val api = apis[c.source] ?: return null
        val key = "${c.source.name}|$id"
        artistTitlesCache.get(key)?.let { return it.takeIf { l -> l !== NONE } }
        var list = runCatchingCancellable { api.artistTitles(id) }.getOrNull()
        if (list == null && c.mbid != null) list = runCatchingCancellable { musicBrainz.artistTitles(c.mbid) }.getOrNull()
        artistTitlesCache.put(key, list ?: NONE)
        return list
    }

    /** Titres connus d'un artiste : crédités (principal + featuring) et fiches dont le nom contient l'artiste. Noms trop courts : fiche seule. */
    private suspend fun knownTitlesOf(artistId: Long, artistName: String?, exclude: String? = null): Set<String> {
        val name = artistName?.trim().orEmpty()
        val titles = if (name.length >= 3) db.trackDao().titlesCreditedTo(artistId, name) else db.trackDao().titlesOfArtist(artistId)
        return keysOf(titles, exclude)
    }

    private fun keysOf(titles: List<String>, exclude: String? = null): Set<String> {
        val ex = exclude?.let { TitleNormalizer.normalizeKey(it) }
        return titles.map { TitleNormalizer.normalizeKey(it) }.filter { it.isNotBlank() && it != ex }.toSet()
    }

    /**
     * ⚠️ À corriger — propositions pour le popup de correction : jusqu'à 3 candidats (source + score) issus des
     * sources sans clé ou configurées (hors Google), interrogées en parallèle. Un appui remplit tous les champs.
     */
    /**
     * Popup artiste → « 🖼️ Autres photos » : toutes les sources photo interrogées en parallèle, notées et vérifiées
     * avec les titres de ta bibliothèque ; une par image, les 6 meilleures. Le choix est sauvegardé en 👤 USER.
     */
    suspend fun proposeArtist(artistId: Long, name: String, keywords: String = ""): List<ScoredCandidate> {
        if (name.isBlank()) return emptyList()
        val known = knownTitlesOf(artistId, name)
        val sources = ApiSource.entries.filter { !it.retired && !it.isLastResort && it.artistPriority > 0 && hasKey(it) && apis.containsKey(it) }.sortedBy { it.artistPriority }.take(7)
        val queries = listOf(name) + (if (keywords.isBlank()) emptyList() else listOf("$name $keywords"))
        val results: List<MetaCandidate> = coroutineScope {
            sources.flatMap { s -> queries.map { q -> async { kotlinx.coroutines.withTimeoutOrNull(8_000L) { runCatchingCancellable { apis.getValue(s).searchArtist(q) }.getOrDefault(emptyList()) } ?: emptyList() } } }.map { it.await() }
        }.flatten().filter { !it.imageUrl.isNullOrBlank() }
        val top = results.map { c -> keywordBoost(MetadataMatching.scoreArtist(c, name), keywords) }
            .filter { it.score >= 50 }
            .sortedByDescending { it.score }
            .distinctBy { it.candidate.imageUrl }
            .take(6)
        return top.map { sc -> MetadataMatching.libraryCheck(sc, artistTitlesOf(sc.candidate), null, known, minKnown = 2, minList = 3, penalty = 40) }
            .sortedByDescending { it.score }
    }

    /** Popup album → « 🖼️ Autres pochettes » : mêmes règles que l'album (liste de pistes croisée avec tes titres de l'album). */
    suspend fun proposeAlbum(albumId: Long, title: String, artist: String, keywords: String = ""): List<ScoredCandidate> {
        if (title.isBlank()) return emptyList()
        val known = keysOf(db.trackDao().titlesOfAlbum(albumId))
        val sources = ApiSource.entries.filter { !it.retired && !it.isLastResort && it.albumPriority > 0 && hasKey(it) && apis.containsKey(it) }.sortedBy { it.albumPriority }.take(7)
        val queries = listOf(title) + (if (keywords.isBlank()) emptyList() else listOf("$title $keywords"))
        val results: List<MetaCandidate> = coroutineScope {
            sources.flatMap { s -> queries.map { q -> async { kotlinx.coroutines.withTimeoutOrNull(8_000L) { runCatchingCancellable { apis.getValue(s).searchAlbum(q, artist) }.getOrDefault(emptyList()) } ?: emptyList() } } }.map { it.await() }
        }.flatten().filter { !it.imageUrl.isNullOrBlank() }
        val consensus = MetadataMatching.consensusSet(results)
        val top = results.map { c -> keywordBoost(MetadataMatching.scoreAlbum(c, title, artist, c in consensus), keywords) }
            .filter { it.score >= 50 }
            .sortedByDescending { it.score }
            .distinctBy { it.candidate.imageUrl }
            .take(6)
        return top.map { sc -> MetadataMatching.libraryCheck(sc, tracklistOf(sc.candidate), null, known, minKnown = 2, minList = 2, penalty = 30) }
            .sortedByDescending { it.score }
    }

    /** Mots-clés de l'utilisateur présents dans le nom / l'album / l'artiste du candidat → +10 (orientation des propositions). */
    private fun keywordBoost(sc: ScoredCandidate, keywords: String): ScoredCandidate {
        val words = keywords.split(' ').map { TitleNormalizer.normalizeKey(it) }.filter { it.length >= 3 }
        if (words.isEmpty() || sc.score <= 0) return sc
        val hay = TitleNormalizer.normalizeKey(listOfNotNull(sc.candidate.name, sc.candidate.album, sc.candidate.artist).joinToString(" "))
        val hits = words.count { hay.contains(it) }
        return if (hits > 0) sc.copy(score = (sc.score + 10).coerceAtMost(100), reasons = sc.reasons + "mot-clé ×$hits +10") else sc
    }

    suspend fun proposeTrack(title: String, artist: String, album: String?, durationMs: Long?, trackId: Long? = null): List<ScoredCandidate> {
        if (title.isBlank()) return emptyList()
        val known = trackId?.let { id -> db.trackDao().getById(id)?.let { t -> knownTitlesOf(t.artistId, artist, exclude = t.title) } } ?: emptySet()
        val wanted = TitleNormalizer.normalizeKey(title)
        val sources = ApiSource.entries.filter { !it.retired && !it.isLastResort && it.trackPriority > 0 && hasKey(it) && apis.containsKey(it) }.sortedBy { it.trackPriority }.take(6)
        val results: List<MetaCandidate> = coroutineScope {
            sources.map { s -> async { kotlinx.coroutines.withTimeoutOrNull(8_000L) { runCatchingCancellable { apis.getValue(s).searchTrack(title, artist.ifBlank { "" }) }.getOrDefault(emptyList()) } ?: emptyList() } }.map { it.await() }
        }.flatten()
        val consensus = MetadataMatching.consensusSet(results)
        val top = results.map { c -> MetadataMatching.scoreTrack(c, title, artist, album, durationMs, c in consensus) }
            .filter { it.score >= 50 }
            .sortedByDescending { it.score }
            .distinctBy { TitleNormalizer.normalizeKey(it.candidate.name) + "|" + TitleNormalizer.normalizeKey(it.candidate.artist ?: "") }
            .take(3)
        // Vérification bibliothèque (liste de pistes de l'album candidat vs titres connus de l'artiste)
        return top.map { sc -> MetadataMatching.libraryCheck(sc, tracklistOf(sc.candidate), wanted, known) }.sortedByDescending { it.score }
    }

    /** Sources (pour l'écran Réglages) : actives d'abord, puis sans clé, puis retirées. */
    fun configuredSources(): List<Pair<ApiSource, Boolean>> = ApiSource.entries
        .map { it to (!it.retired && (!it.requiresKey || hasKey(it))) }
        .sortedWith(compareBy({ it.first.retired }, { !it.second }, { minOf(it.first.trackPriority.takeIf { p -> p > 0 } ?: 99, it.first.artistPriority.takeIf { p -> p > 0 } ?: 99) }))

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
                val ok = runCatchingCancellable { enrichArtist(a) }.getOrElse { EnrichmentState.log("⚠️ ${a.name} : ${it.message}"); false }
                processed++; if (ok) found++; EnrichmentState.done(ok)
            }
            db.albumDao().missingCover(now, albums).forEach { al ->
                // Album partagé (BO…) : recherche avec l'artiste principal du titre le plus écouté
                val artist = (al.artistId?.let { db.artistDao().getById(it)?.name }
                    ?: db.trackDao().inAlbum(al.albumId).maxByOrNull { it.playCount }?.let { t -> db.artistDao().getById(t.artistId)?.name })
                    ?: return@forEach
                EnrichmentState.current("💿 ${al.title}")
                val ok = runCatchingCancellable { enrichAlbum(al, artist) }.getOrElse { EnrichmentState.log("⚠️ ${al.title} : ${it.message}"); false }
                processed++; if (ok) found++; EnrichmentState.done(ok)
            }
            db.trackDao().missingAlbumAssociation(now, minOf(tracks, 5)).forEach { track ->
                EnrichmentState.current("💿 Recherche d'album · ${track.title}")
                val suggestions = runCatchingCancellable { findTrackAlbumCandidates(track.trackId) }
                    .getOrElse { EnrichmentState.log("⚠️ Album de ${track.title} : ${it.message}"); emptyList() }
                val linked = db.trackDao().getById(track.trackId)?.albumId != null
                if (!linked && suggestions.isNotEmpty()) {
                    val preview = suggestions.take(3).joinToString(" · ") { "${it.albumTitle} (${it.confidence} %)" }
                    EnrichmentState.log("🔎 Propositions d'album pour « ${track.title} » : $preview${if (suggestions.size > 3) " · …" else ""} — confirmation requise")
                }
                val ok = linked || suggestions.isNotEmpty()
                processed++; if (ok) found++; EnrichmentState.done(ok)
            }
            db.trackDao().missingCover(now, tracks).forEach { t ->
                val artist = db.artistDao().getById(t.artistId)?.name ?: return@forEach
                val album = t.albumId?.let { db.albumDao().getById(it) }
                EnrichmentState.current("🎵 ${t.title}")
                val ok = runCatchingCancellable { enrichTrack(t, artist, album) }.getOrElse { EnrichmentState.log("⚠️ ${t.title} : ${it.message}"); false }
                processed++; if (ok) found++; EnrichmentState.done(ok)
            }
        } finally {
            EnrichmentState.stop()
        }
        val later = System.currentTimeMillis()
        val remaining = db.artistDao().missingPhoto(later, 1).size + db.albumDao().missingCover(later, 1).size +
            db.trackDao().missingCover(later, 1).size + db.trackDao().missingAlbumAssociation(later, 1).size
        EnrichmentState.log("Lot terminé : $processed traités, $found enrichis${if (remaining > 0) ", suite à venir" else ""}")
        return BatchResult(processed, found, remaining)
    }

    /* ------------------------------------------------------------------ */
    /*  Ré-enrichissement forcé (boutons « Tout ré-enrichir » / « Ré-enrichir… »)  */
    /* ------------------------------------------------------------------ */

    data class RefreshTarget(val type: String, val id: Long) {
        fun encode() = "$type:$id"
        companion object { fun decode(s: String): RefreshTarget? = s.split(':').takeIf { it.size == 2 }?.let { (t, i) -> i.toLongOrNull()?.let { RefreshTarget(t, it) } } }
    }

    /**
     * Ré-enrichit une entité : vide son cache (hors liste noire) puis relance la cascade complète.
     * L'image actuelle n'est remplacée que si un résultat ≥ 70 est trouvé (sinon elle est conservée).
     */
    suspend fun refresh(target: RefreshTarget): Boolean = when (target.type) {
        EntityType.ARTIST -> {
            db.apiCacheDao().clearEntity(EntityType.ARTIST, target.id, DataType.PHOTO)
            val a = db.artistDao().getById(target.id)
            if (a == null) false else { EnrichmentState.current("🎤 ${a.name}"); enrichArtist(a) }
        }
        EntityType.ALBUM -> {
            db.apiCacheDao().clearEntity(EntityType.ALBUM, target.id, DataType.COVER)
            val al = db.albumDao().getById(target.id)
            val artist = al?.let { a -> a.artistId?.let { db.artistDao().getById(it)?.name }
                ?: db.trackDao().inAlbum(a.albumId).maxByOrNull { it.playCount }?.let { t -> db.artistDao().getById(t.artistId)?.name } }
            if (al == null || artist == null) false else { EnrichmentState.current("💿 ${al.title}"); enrichAlbum(al, artist, overwriteTracks = true) }
        }
        else -> {
            db.apiCacheDao().clearEntity(EntityType.TRACK, target.id, DataType.COVER)
            val t = db.trackDao().getById(target.id)
            val artist = t?.let { db.artistDao().getById(it.artistId)?.name }
            if (t == null || artist == null) false else {
                EnrichmentState.current("🎵 ${t.title}")
                enrichTrack(t, artist, t.albumId?.let { db.albumDao().getById(it) })
            }
        }
    }

    /** Ré-enrichit une sélection (page « Ré-enrichir… »). Annulable (WorkManager). */
    suspend fun refreshSelected(targets: List<RefreshTarget>, mode: String = "Ré-enrichissement (${targets.size})"): BatchResult {
        var processed = 0; var found = 0
        EnrichmentState.start(total = targets.size, mode = mode)
        EnrichmentState.log("🔄 $mode : ${targets.size} élément${if (targets.size > 1) "s" else ""}")
        try {
            for (t in targets) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val ok = try { refresh(t) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { EnrichmentState.log("⚠️ ${t.type} #${t.id} : ${EnrichmentState.describe(e)}"); false }
                processed++; if (ok) found++; EnrichmentState.done(ok)
            }
        } finally {
            EnrichmentState.stop()
            EnrichmentState.log("🔄 $mode terminé : $processed traités, $found mis à jour")
        }
        return BatchResult(processed, found, 0)
    }

    /**
     * « Tout ré-enrichir » : artistes → albums → titres, les plus écoutés d'abord. Les images choisies à la main
     * (source USER) sont conservées ; les URL rejetées restent en liste noire.
     */
    suspend fun refreshAll(): BatchResult {
        val targets = db.artistDao().idsForRefresh().map { RefreshTarget(EntityType.ARTIST, it) } +
            db.albumDao().idsForRefresh().map { RefreshTarget(EntityType.ALBUM, it) } +
            db.trackDao().idsForRefresh().map { RefreshTarget(EntityType.TRACK, it) }
        return refreshSelected(targets, mode = "Ré-enrichissement complet")
    }

    /* ------------------------------------------------------------------ */
    /*  Par entité                                                          */
    /* ------------------------------------------------------------------ */

    suspend fun enrichArtist(a: ArtistEntity): Boolean {
        val known = knownTitlesOf(a.artistId, a.name)
        val best = cascade(
            EntityType.ARTIST, a.artistId, DataType.PHOTO, { it.artistPriority },
            query = { api -> api.searchArtist(a.name) },
            score = { c, _ -> MetadataMatching.scoreArtist(c, a.name) },
            // Homonymes : les titres connus du candidat doivent recouper au moins un des tiens
            verify = { sc -> MetadataMatching.libraryCheck(sc, artistTitlesOf(sc.candidate), null, known, minKnown = 2, minList = 3, penalty = 40) }
        )
        if (best == null) { negativeCache(EntityType.ARTIST, a.artistId, DataType.PHOTO); return false }
        val c = best.candidate
        db.artistDao().saveEnrichment(a.artistId, c.imageUrl, c.source.label, c.bio, c.mbid, c.spotifyId)
        // Une bio peut venir d'une autre source que la photo (Last.fm / TheAudioDB) — on la récupère si absente
        if (a.bio == null && c.bio == null) fetchBio(a)
        positiveCache(EntityType.ARTIST, a.artistId, DataType.PHOTO, best)
        EnrichmentState.log("✅ ${a.name} → photo ${c.source.label} (${best.score})${libraryNote(best)}")
        return true
    }

    private suspend fun fetchBio(a: ArtistEntity) {
        for (s in listOf(ApiSource.THEAUDIODB, ApiSource.LASTFM)) {
            if (!hasKey(s)) continue
            val bio = runCatchingCancellable { apis.getValue(s).searchArtist(a.name) }.getOrNull()
                ?.firstOrNull { MetadataMatching.similarity(it.name, a.name) >= 0.85 && !it.bio.isNullOrBlank() }?.bio
            if (bio != null) { db.artistDao().getById(a.artistId)?.let { db.artistDao().update(it.copy(bio = bio)) }; return }
        }
    }

    /**
     * [mustContain] : titre qui doit figurer dans la liste de pistes de l'album candidat (appel depuis enrichTrack).
     * [overwriteTracks] : remplace aussi la pochette des titres de l'album qui en avaient déjà une (ré-enrichissement).
     */
    suspend fun enrichAlbum(al: AlbumEntity, artistName: String, mustContain: String? = null, overwriteTracks: Boolean = false): Boolean {
        val known = keysOf(db.trackDao().titlesOfAlbum(al.albumId), exclude = mustContain)
        val best = cascade(
            EntityType.ALBUM, al.albumId, DataType.COVER, { it.albumPriority },
            query = { api -> api.searchAlbum(al.title, artistName) },
            score = { c, consensus -> MetadataMatching.scoreAlbum(c, al.title, artistName, consensus) },
            verify = { sc -> MetadataMatching.libraryCheck(sc, tracklistOf(sc.candidate), mustContain?.let { TitleNormalizer.normalizeKey(it) }, known, minKnown = 2, minList = 2, penalty = 30) }
        )
        if (best == null || best.candidate.imageUrl == null) { negativeCache(EntityType.ALBUM, al.albumId, DataType.COVER); return false }
        val c = best.candidate
        db.albumDao().saveEnrichment(al.albumId, c.imageUrl, c.source.label, c.releaseDate, c.mbid)
        if (overwriteTracks) db.albumDao().overwriteCoverOfTracks(al.albumId, c.imageUrl, c.source.label)
        else db.albumDao().propagateCoverToTracks(al.albumId, c.imageUrl, c.source.label)
        positiveCache(EntityType.ALBUM, al.albumId, DataType.COVER, best)
        EnrichmentState.log("✅ ${al.title} → pochette ${c.source.label} (${best.score})${libraryNote(best)}")
        return true
    }

    private fun libraryNote(best: ScoredCandidate): String =
        best.reasons.lastOrNull { it.contains("bibliothèque") || it.contains("non vérifiable") || it.contains("absent de cet album") }?.let { " · $it" } ?: ""

    suspend fun enrichTrack(t: TrackEntity, artistName: String, album: AlbumEntity?): Boolean {
        // 1. Album connu : on résout d'abord l'album (liste de pistes vérifiée : doit contenir ce titre) — tous les titres
        //    de l'album héritent de la même pochette, et un album déjà résolu n'appelle aucune API.
        if (album != null) {
            if (album.coverUrl != null && t.coverUrl == null) {
                db.trackDao().applyAlbumCoverIfMissing(t.trackId, album.coverUrl, album.coverSource)
                EnrichmentState.log("✅ ${t.title} → pochette de l'album « ${album.title} » (déjà résolue)")
                return true
            }
            if (album.coverUrl == null && enrichAlbum(album, artistName, mustContain = t.title)) {
                val resolved = db.albumDao().getById(album.albumId)
                if (resolved?.coverUrl != null) db.trackDao().applyAlbumCoverIfMissing(t.trackId, resolved.coverUrl, resolved.coverSource)
                return true
            }
        }
        // 2. Recherche par titre, vérifiée par les titres connus de l'artiste
        val known = knownTitlesOf(t.artistId, artistName, exclude = t.title)
        val wanted = TitleNormalizer.normalizeKey(t.title)
        val best = cascade(
            EntityType.TRACK, t.trackId, DataType.COVER, { it.trackPriority },
            query = { api -> api.searchTrack(t.title, artistName) },
            score = { c, consensus -> MetadataMatching.scoreTrack(c, t.title, artistName, album?.title, t.durationMs, consensus) },
            verify = { sc -> MetadataMatching.libraryCheck(sc, tracklistOf(sc.candidate), wanted, known) }
        )
        if (best == null || best.candidate.imageUrl == null) { negativeCache(EntityType.TRACK, t.trackId, DataType.COVER); return false }
        val c = best.candidate
        db.trackDao().saveEnrichment(
            id = t.trackId,
            coverUrl = c.imageUrl,
            coverSource = c.source.label,
            durationMs = c.durationMs,
            genre = c.genre,
            mbid = c.mbid,
            confidenceScore = best.score,
            needsReview = best.score < MetadataMatching.TRUSTED
        )
        // L'album du titre n'a pas de pochette et la source confirme le même album → on la lui donne aussi
        if (album != null && album.coverUrl == null && c.album != null && MetadataMatching.similarity(c.album, album.title) >= 0.85) {
            db.albumDao().saveEnrichment(album.albumId, c.imageUrl, c.source.label, null, null)
            db.albumDao().propagateCoverToTracks(album.albumId, c.imageUrl, c.source.label)
            positiveCache(EntityType.ALBUM, album.albumId, DataType.COVER, best)
        }
        positiveCache(EntityType.TRACK, t.trackId, DataType.COVER, best)
        EnrichmentState.log("✅ ${t.title} → pochette ${c.source.label} (${best.score})${libraryNote(best)}")
        if (best.score < MetadataMatching.TRUSTED) {
            try {
                onTrackFlagged?.invoke(t.trackId, t.title, artistName, "${c.name}${c.artist?.let { " — $it" } ?: ""} · ${c.source.label}", best.score)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                EnrichmentState.log("⚠️ Notification « À vérifier » impossible : ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return true
    }

    /* ------------------------------------------------------------------ */
    /*  Cascade générique                                                   */
    /* ------------------------------------------------------------------ */

    /**
     * Interroge les sources par groupes de 3 en parallèle (consensus), score les candidats, s'arrête au premier
     * résultat ≥ 70. YouTube (dernier recours, score forfaitaire 70 → 🟡 À vérifier) n'est appelé que si tout le
     * reste a échoué.
     */
    private suspend fun cascade(
        entityType: String,
        entityId: Long,
        dataType: String,
        basePriority: (ApiSource) -> Int,
        query: suspend (MusicApi) -> List<MetaCandidate>,
        score: (MetaCandidate, Boolean) -> ScoredCandidate,
        /** Vérification « bibliothèque » appliquée aux 3 meilleurs candidats (≥ 50) de chaque groupe. */
        verify: suspend (ScoredCandidate) -> ScoredCandidate = { it }
    ): ScoredCandidate? {
        val reliability = db.apiCacheDao().allReliability().mapNotNull { r ->
            ApiSource.entries.firstOrNull { it.label == r.apiName }?.let { it to (r.successCount to r.failCount) }
        }.toMap()
        val order = MetadataMatching.orderSources(basePriority, reliability, ::hasKey)
        val blacklisted = db.apiCacheDao().blacklistedUrls(entityType, entityId).toSet()
        val seen = mutableListOf<MetaCandidate>()

        for (group in order.chunked(3)) {
            val results: List<Pair<ApiSource, Result<List<MetaCandidate>>>> = coroutineScope {
                group.map { s -> async { s to runCatchingCancellable { query(apis.getValue(s)) } } }.map { it.await() }
            }
            results.forEach { (s, r) ->
                val e = r.exceptionOrNull()
                if (e != null) { EnrichmentState.error(s.label, e); EnrichmentState.log("⚠️ ${s.label} : ${EnrichmentState.describe(e)}") }
                else EnrichmentState.clearError(s.label)
            }
            val fresh = results.flatMap { (_, r) -> r.getOrDefault(emptyList()) }
                .filter { it.imageUrl == null || it.imageUrl !in blacklisted }
            seen += fresh
            val consensus = MetadataMatching.consensusSet(seen)

            val rawScored = fresh.map { c ->
                if (c.source.isLastResort) ScoredCandidate(c, MetadataMatching.ACCEPT, listOf("dernier recours"))
                else score(c, c in consensus)
            }.filter { it.candidate.imageUrl != null } // on cherche toujours une image (pochette / photo)
            // Vérification bibliothèque : 3 meilleurs candidats ≥ 50 ; les autres ne peuvent pas dépasser 89
            val toVerify = rawScored.filter { it.score >= 50 && !it.candidate.source.isLastResort }.sortedByDescending { it.score }.take(3).toSet()
            val scored = rawScored.map { sc ->
                if (sc in toVerify) verify(sc)
                else if (sc.score >= MetadataMatching.TRUSTED) sc.copy(score = MetadataMatching.TRUSTED - 1, reasons = sc.reasons + "non vérifié → max ${MetadataMatching.TRUSTED - 1}")
                else sc
            }

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
