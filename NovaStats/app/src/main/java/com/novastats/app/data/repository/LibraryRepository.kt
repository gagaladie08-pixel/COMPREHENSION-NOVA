package com.novastats.app.data.repository

import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.TrackAlbumEntity
import com.novastats.app.data.db.entity.TrackArtistEntity
import com.novastats.app.data.db.entity.TrackEntity
import com.novastats.app.domain.TitleNormalizer

/**
 * Résolution "métadonnées brutes → entités en base" (artiste / album / titre).
 * Utilisé par le service de détection ET par l'importeur.
 *
 * Règles appliquées :
 *  - normalisation du titre (versions fusionnées, feat. extraits)
 *  - artistes multiples : chaque artiste reçoit un lien track_artists (même poids)
 *  - même titre / artistes différents → titres distincts (clé = titre + artiste principal)
 *  - album créé par (titre album, artiste principal)
 */
class LibraryRepository(private val db: NovaDatabase) {

    data class Resolved(val trackId: Long, val primaryArtistId: Long, val albumId: Long?, val artistIds: List<Long>)

    // Caches mémoire (clé normalisée) — évitent des milliers de requêtes lors d'un import.
    private val artistCache = HashMap<String, Long>()
    private val albumCache = HashMap<String, Long>()
    private val trackCache = HashMap<String, Resolved>()

    /* ---------- Apprentissage éditeur : corrections mémorisées (user_corrections) ---------- */

    private var corrections: Map<String, Map<String, com.novastats.app.data.db.entity.UserCorrectionEntity>> = emptyMap()
    private var correctionsLoadedAt = 0L

    private suspend fun corrections(): Map<String, Map<String, com.novastats.app.data.db.entity.UserCorrectionEntity>> {
        val now = System.currentTimeMillis()
        if (now - correctionsLoadedAt > 30_000L) {
            corrections = db.editorDao().allCorrections().groupBy { it.correctionType }
                .mapValues { (_, rows) -> rows.associateBy { TitleNormalizer.normalizeKey(it.originalValue) } }
            correctionsLoadedAt = now
        }
        return corrections
    }

    /** Applique une correction connue « avant → après » (juste après le cache, sans appel API). */
    private suspend fun corrected(type: String, raw: String?): String? {
        if (raw.isNullOrBlank()) return raw
        val c = corrections()[type]?.get(TitleNormalizer.normalizeKey(raw)) ?: return raw
        if (c.correctedValue != c.originalValue) runCatching { db.editorDao().bumpCorrection(c.id) }
        return c.correctedValue
    }

    /** Valeur « confirmée » par l'utilisateur (Ignorer / C'est correct) : ne rentre plus dans ⚠️ À corriger. */
    suspend fun isConfirmed(type: String, raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return corrections()[type]?.containsKey(TitleNormalizer.normalizeKey(raw)) == true
    }

    fun invalidateCorrections() { correctionsLoadedAt = 0L }

    suspend fun resolve(
        rawTitleIn: String,
        rawArtistsIn: String,
        rawAlbumIn: String?,
        durationMs: Long? = null,
        genre: String? = null,
        albumArtist: String? = null,
        applyCorrections: Boolean = true
    ): Resolved {
        val rawTitle = if (applyCorrections) corrected("TITLE", rawTitleIn) ?: rawTitleIn else rawTitleIn
        val rawArtists = if (applyCorrections) corrected("ARTIST", rawArtistsIn) ?: rawArtistsIn else rawArtistsIn
        val rawAlbum = if (applyCorrections) corrected("ALBUM", rawAlbumIn) else rawAlbumIn
        val normalized = TitleNormalizer.normalizeTitle(rawTitle)
        // Cascade artistes : 1) feat. dans le titre  2) champ artiste du player (lui-même "A & B, C")
        val playerArtists = TitleNormalizer.splitArtists(rawArtists)
        val artistNames = (playerArtists + normalized.featuredArtists)
            .distinctBy { TitleNormalizer.normalizeKey(it) }
            .ifEmpty { listOf("Artiste inconnu") }

        // Remix / version AVEC artiste featuring → titre distinct, lié à l'original.
        // Sans featuring → fusion invisible avec l'original (remix DJ/EDM inclus).
        val playerKeys = playerArtists.map { TitleNormalizer.normalizeKey(it) }.toSet()
        val extraFeatured = normalized.featuredArtists.filter { TitleNormalizer.normalizeKey(it) !in playerKeys }
        val isRemixFeat = normalized.isVersion && extraFeatured.isNotEmpty()
        val title = if (isRemixFeat) "${normalized.title} (feat. ${extraFeatured.joinToString(", ")})" else normalized.title

        val primaryName = artistNames.first()
        val trackKey = TitleNormalizer.normalizeKey(title) + "|" + TitleNormalizer.normalizeKey(primaryName)
        trackCache[trackKey]?.let { return it }

        val artistIds = artistNames.map { resolveArtist(it) }
        val primaryArtistId = artistIds.first()
        // Album crédité uniquement s'il s'agit d'un album de l'artiste principal ; jamais pour une compilation
        val albumId = rawAlbum?.takeIf { it.isNotBlank() && !TitleNormalizer.isCompilation(it, albumArtist) }
            ?.let { resolveAlbum(it, primaryArtistId) }

        val existing = db.trackDao().findByTitleAndArtist(title, primaryArtistId)
        val originalId = if (isRemixFeat) db.trackDao().findByTitleAndArtist(normalized.title, primaryArtistId)?.trackId else null
        val trackId = existing?.trackId ?: db.trackDao().insert(
            TrackEntity(
                title = title,
                titleRaw = rawTitle,
                artistId = primaryArtistId,
                albumId = albumId,
                durationMs = durationMs,
                genre = genre,
                isRemix = isRemixFeat,
                originalTrackId = originalId
            )
        )
        if (existing != null && existing.albumId == null && albumId != null) {
            db.trackDao().update(existing.copy(albumId = albumId, durationMs = existing.durationMs ?: durationMs, genre = existing.genre ?: genre))
        }

        // Chaque artiste présent (principal + featured) est lié au titre → reçoit l'écoute à poids égal
        artistIds.forEachIndexed { i, id ->
            db.trackLinkDao().insertTrackArtist(TrackArtistEntity(trackId = trackId, artistId = id, isPrimary = i == 0))
        }
        albumId?.let { db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = trackId, albumId = it)) }

        return Resolved(trackId, primaryArtistId, albumId, artistIds).also { trackCache[trackKey] = it }
    }

    suspend fun resolveArtist(rawName: String): Long {
        val key = TitleNormalizer.normalizeKey(rawName)
        artistCache[key]?.let { return it }
        val name = rawName.trim()
        // Insensible à la casse : "LISA", "Lisa" et "lisa" sont le même artiste (normalisation du texte)
        val id = db.artistDao().findByName(name)?.artistId
            ?: db.artistDao().findByNameNoCase(name)?.artistId
            ?: db.artistDao().insert(ArtistEntity(name = name, nameRaw = rawName))
        artistCache[key] = id
        return id
    }

    suspend fun resolveAlbum(rawTitle: String, artistId: Long): Long {
        val title = TitleNormalizer.normalizeAlbumTitle(rawTitle) // Deluxe / Expanded / Japan Edition… fusionnés
        val key = TitleNormalizer.normalizeKey(title) + "|" + artistId
        albumCache[key]?.let { return it }
        val id = db.albumDao().findByTitleAndArtist(title, artistId)?.albumId
            ?: db.albumDao().insert(AlbumEntity(title = title, titleRaw = rawTitle, artistId = artistId))
        albumCache[key] = id
        return id
    }

    /** Identifiant du titre s'il est déjà connu (cache mémoire uniquement — pas d'accès disque, pas de création). */
    fun peekTrackId(rawTitle: String, rawArtists: String): Long? {
        val normalized = TitleNormalizer.normalizeTitle(rawTitle)
        val primary = (TitleNormalizer.splitArtists(rawArtists) + normalized.featuredArtists).firstOrNull() ?: "Artiste inconnu"
        return trackCache[TitleNormalizer.normalizeKey(normalized.title) + "|" + TitleNormalizer.normalizeKey(primary)]?.trackId
    }

    fun clearCaches() { artistCache.clear(); albumCache.clear(); trackCache.clear(); correctionsLoadedAt = 0L }
}
