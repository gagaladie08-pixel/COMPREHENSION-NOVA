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

    suspend fun resolve(
        rawTitle: String,
        rawArtists: String,
        rawAlbum: String?,
        durationMs: Long? = null,
        genre: String? = null
    ): Resolved {
        val normalized = TitleNormalizer.normalizeTitle(rawTitle)
        val artistNames = (TitleNormalizer.splitArtists(rawArtists) + normalized.featuredArtists)
            .distinctBy { TitleNormalizer.normalizeKey(it) }
            .ifEmpty { listOf("Artiste inconnu") }

        val primaryName = artistNames.first()
        val trackKey = TitleNormalizer.normalizeKey(normalized.title) + "|" + TitleNormalizer.normalizeKey(primaryName)
        trackCache[trackKey]?.let { return it }

        val artistIds = artistNames.map { resolveArtist(it) }
        val primaryArtistId = artistIds.first()
        val albumId = rawAlbum?.takeIf { it.isNotBlank() }?.let { resolveAlbum(it, primaryArtistId) }

        val existing = db.trackDao().findByTitleAndArtist(normalized.title, primaryArtistId)
        val trackId = existing?.trackId ?: db.trackDao().insert(
            TrackEntity(
                title = normalized.title,
                titleRaw = rawTitle,
                artistId = primaryArtistId,
                albumId = albumId,
                durationMs = durationMs,
                genre = genre
            )
        )
        if (existing != null && existing.albumId == null && albumId != null) {
            db.trackDao().update(existing.copy(albumId = albumId, durationMs = existing.durationMs ?: durationMs, genre = existing.genre ?: genre))
        }

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
        val id = db.artistDao().findByName(name)?.artistId
            ?: db.artistDao().insert(ArtistEntity(name = name, nameRaw = rawName))
        artistCache[key] = id
        return id
    }

    suspend fun resolveAlbum(rawTitle: String, artistId: Long): Long {
        val title = TitleNormalizer.normalizeTitle(rawTitle).title // "Deluxe", "Remastered" fusionnés
        val key = TitleNormalizer.normalizeKey(title) + "|" + artistId
        albumCache[key]?.let { return it }
        val id = db.albumDao().findByTitleAndArtist(title, artistId)?.albumId
            ?: db.albumDao().insert(AlbumEntity(title = title, titleRaw = rawTitle, artistId = artistId))
        albumCache[key] = id
        return id
    }

    fun clearCaches() { artistCache.clear(); albumCache.clear(); trackCache.clear() }
}
