package com.novastats.app.data.api

import android.util.Log
import com.novastats.app.data.ApiKeys
import com.novastats.app.data.api.HttpJson.HttpException
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
import kotlinx.coroutines.ensureActive
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
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
    private val dayFmt = SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE)
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
        val text = "${dayFmt.format(System.currentTimeMillis())} · ${describe(t)}"
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
        _state.update { it.copy(log = (listOf("${fmt.format(System.currentTimeMillis())} $msg") + it.log).take(40)) }
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
    var onTrackFlagged: ((trackId: Long, title: String, artist: String, proposal: String, score: Int) -> Unit)? = null

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
        val list = runCatching { api.albumTracks(id) }.getOrNull()
        tracklistCache.put(key, list ?: NONE)
        return list
    }

    /** Titres connus de l'artiste candidat ; les sources « mbid » (Fanart, Wikidata, TheAudioDB, Last.fm) passent par MusicBrainz. */
    private suspend fun artistTitlesOf(c: MetaCandidate): List<String>? {
        val id = c.externalArtistId ?: c.mbid ?: return null
        val api = apis[c.source] ?: return null
        val key = "${c.source.name}|$id"
        artistTitlesCache.get(key)?.let { return it.takeIf { l -> l !== NONE } }
        var list = runCatching { api.artistTitles(id) }.getOrNull()
        if (list == null && c.mbid != null) list = runCatching { musicBrainz.artistTitles(c.mbid) }.getOrNull()
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
            sources.flatMap { s -> queries.map { q -> async { kotlinx.coroutines.withTimeoutOrNull(8_000L) { runCatching { apis.getValue(s).searchArtist(q) }.getOrDefault(emptyList()) } ?: emptyList() } } }.map { it.await() }
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
            sources.flatMap { s -> queries.map { q -> async { kotlinx.coroutines.withTimeoutOrNull(8_000L) { runCatching { apis.getValue(s).searchAlbum(q, artist) }.getOrDefault(emptyList()) } ?: emptyList() } } }.map { it.await() }
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
            sources.map { s -> async { kotlinx.coroutines.withTimeoutOrNull(8_000L) { runCatching { apis.getValue(s).searchTrack(title, artist.ifBlank { "" }) }.getOrDefault(emptyList()) } ?: emptyList() } }.map { it.await() }
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
            val artist = al?.let { db.artistDao().getById(it.artistId)?.name }
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
        db.artistDao().update(
            a.copy(
                photoUrl = c.imageUrl, photoSource = c.source.label,
                bio = a.bio ?: c.bio, mbid = a.mbid ?: c.mbid, spotifyId = a.spotifyId ?: c.spotifyId
            )
        )
        // Une bio peut venir d'une autre source que la photo (Last.fm / TheAudioDB) — on la récupère si absente
        if (a.bio == null && c.bio == null) fetchBio(a)
        positiveCache(EntityType.ARTIST, a.artistId, DataType.PHOTO, best)
        EnrichmentState.log("✅ ${a.name} → photo ${c.source.label} (${best.score})${libraryNote(best)}")
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
        db.albumDao().update(al.copy(coverUrl = c.imageUrl, coverSource = c.source.label, releaseDate = al.releaseDate ?: c.releaseDate, mbid = al.mbid ?: c.mbid))
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
                db.trackDao().update(t.copy(coverUrl = album.coverUrl, coverSource = album.coverSource))
                EnrichmentState.log("✅ ${t.title} → pochette de l'album « ${album.title} » (déjà résolue)")
                return true
            }
            if (album.coverUrl == null && enrichAlbum(album, artistName, mustContain = t.title)) {
                val resolved = db.albumDao().getById(album.albumId)
                if (resolved?.coverUrl != null) db.trackDao().update(t.copy(coverUrl = resolved.coverUrl, coverSource = resolved.coverSource))
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
        EnrichmentState.log("✅ ${t.title} → pochette ${c.source.label} (${best.score})${libraryNote(best)}")
        if (best.score < MetadataMatching.TRUSTED) runCatching { onTrackFlagged?.invoke(t.trackId, t.title, artistName, "${c.name}${c.artist?.let { " — $it" } ?: ""} · ${c.source.label}", best.score) }
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
                group.map { s -> async { s to runCatching { query(apis.getValue(s)) } } }.map { it.await() }
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
