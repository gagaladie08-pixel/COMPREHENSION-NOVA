package com.novastats.app.data.repository

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.EditHistoryEntity
import com.novastats.app.data.db.entity.TrackArtistEntity
import com.novastats.app.data.db.entity.TrackEntity
import com.novastats.app.data.db.entity.UserCorrectionEntity
import com.novastats.app.domain.TitleNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 🛠️ Éditeur de données — logique métier (singleton par application).
 * Actions : renommer / fusionner / changer artiste ou album / supprimer une écoute / marquer corrigé.
 * Historique (max 50) dans `edit_history`, Undo = dernière action (une fusion ne restaure que le nom).
 * Après chaque action structurelle : recalcul complet des statistiques (Billboard, certifs, records…).
 */
class DataEditorManager(private val db: NovaDatabase, private val rebuilder: StatsRebuilder) {

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    object Type {
        const val RENAME = "RENAME"; const val MERGE = "MERGE"; const val COVER_CHANGE = "COVER_CHANGE"
        const val ALBUM_CHANGE = "ALBUM_CHANGE"; const val ARTIST_CHANGE = "ARTIST_CHANGE"; const val DELETE_PLAY = "DELETE_PLAY"; const val REVIEWED = "REVIEWED"
    }

    private suspend fun log(type: String, entityType: String, entityId: Long, before: String?, after: String?, extra: String? = null) {
        db.editorDao().insertEdit(EditHistoryEntity(type = type, entityType = entityType, entityId = entityId, before = before, after = after, extraData = extra))
        db.editorDao().trimTo50()
    }

    private suspend fun <T> perform(message: String, rebuild: Boolean, block: suspend () -> T): T {
        _busy.value = true
        try {
            val r = block()
            if (rebuild) { _status.value = "$message · recalcul…"; rebuilder.rebuildAll(fullBillboard = true) }
            _status.value = "✅ $message"
            return r
        } catch (t: Throwable) {
            _status.value = "❌ ${t.message ?: t.javaClass.simpleName}"
            throw t
        } finally { _busy.value = false }
    }

    /* ---------------- Renommer ---------------- */

    suspend fun renameArtist(id: Long, newName: String) = perform("Artiste renommé", rebuild = false) {
        val a = db.artistDao().getById(id) ?: error("Artiste introuvable")
        val name = newName.trim(); require(name.isNotEmpty()) { "Nom vide" }
        db.artistDao().findByNameNoCase(name)?.takeIf { it.artistId != id }?.let { error("« $name » existe déjà — utilise Fusionner") }
        db.artistDao().rename(id, name)
        db.editorDao().upsertCorrection(UserCorrectionEntity(originalValue = a.name, correctedValue = name, correctionType = "ARTIST"))
        log(Type.RENAME, "ARTIST", id, a.name, name)
    }

    suspend fun renameAlbum(id: Long, newTitle: String) = perform("Album renommé", rebuild = false) {
        val al = db.albumDao().getById(id) ?: error("Album introuvable")
        val title = newTitle.trim(); require(title.isNotEmpty()) { "Titre vide" }
        db.albumDao().findByTitleAndArtist(title, al.artistId)?.takeIf { it.albumId != id }?.let { error("« $title » existe déjà pour cet artiste — utilise Fusionner") }
        db.albumDao().rename(id, title)
        log(Type.RENAME, "ALBUM", id, al.title, title)
    }

    suspend fun renameTrack(id: Long, newTitle: String) = perform("Titre renommé", rebuild = false) {
        val t = db.trackDao().getById(id) ?: error("Titre introuvable")
        val title = newTitle.trim(); require(title.isNotEmpty()) { "Titre vide" }
        db.trackDao().findByTitleAndArtist(title, t.artistId)?.takeIf { it.trackId != id }?.let { error("« $title » existe déjà pour cet artiste — utilise Fusionner") }
        db.trackDao().rename(id, title)
        db.editorDao().upsertCorrection(UserCorrectionEntity(originalValue = t.title, correctedValue = title, correctionType = "TRACK"))
        log(Type.RENAME, "TRACK", id, t.title, title)
    }

    /* ---------------- Images (galerie / web) ---------------- */

    suspend fun setArtistPhoto(id: Long, url: String?) = perform("Photo mise à jour", rebuild = false) {
        val a = db.artistDao().getById(id) ?: error("Artiste introuvable")
        db.artistDao().setPhoto(id, url); log(Type.COVER_CHANGE, "ARTIST", id, a.photoUrl, url)
    }

    suspend fun setAlbumCover(id: Long, url: String?) = perform("Pochette mise à jour", rebuild = false) {
        val al = db.albumDao().getById(id) ?: error("Album introuvable")
        db.albumDao().setCover(id, url); db.albumDao().propagateCoverToTracks(id, url ?: "", "USER")
        log(Type.COVER_CHANGE, "ALBUM", id, al.coverUrl, url)
    }

    /* ---------------- Fusions ---------------- */

    /** Fusionne [from] dans [into] : titres, liens, écoutes, albums ; [from] marqué MERGED. */
    suspend fun mergeArtists(from: Long, into: Long) = perform("Artistes fusionnés", rebuild = true) {
        db.withTransaction { mergeArtistsInternal(from, into) }
    }

    /** Fusion de plusieurs paires (from → into) en une seule transaction + un seul recalcul. */
    suspend fun mergeArtistsBatch(pairs: List<Pair<Long, Long>>) = perform("${pairs.size} fusion(s) d'artistes", rebuild = true) {
        db.withTransaction {
            for ((from, into) in pairs) {
                if (from == into || db.artistDao().getById(from) == null || db.artistDao().getById(into) == null) continue
                mergeArtistsInternal(from, into)
            }
        }
    }

    private suspend fun mergeArtistsInternal(from: Long, into: Long) {
        require(from != into) { "Même artiste" }
        val a = db.artistDao().getById(from) ?: error("Artiste introuvable")
        val b = db.artistDao().getById(into) ?: error("Artiste cible introuvable")
        // Albums homonymes → fusion, sinon simple transfert
        for (al in db.albumDao().all().filter { it.artistId == from }) {
            val existing = db.albumDao().findByTitleAndArtist(al.title, into)
            if (existing != null && existing.albumId != al.albumId) mergeAlbumsInternal(al.albumId, existing.albumId)
        }
        db.albumDao().moveArtist(from, into)
        // Titres homonymes → fusion des écoutes
        for (t in db.trackDao().allPlayed().filter { it.artistId == from }) {
            val existing = db.trackDao().findByTitleAndArtist(t.title, into)
            if (existing != null && existing.trackId != t.trackId) mergeTracksInternal(t.trackId, existing.trackId)
        }
        db.trackDao().moveArtist(from, into)
        db.trackLinkDao().dropDuplicateLinks(from, into)
        db.trackLinkDao().moveArtist(from, into)
        db.scrobbleDao().moveArtist(from, into)
        db.artistDao().markMerged(from, into)
        db.editorDao().upsertCorrection(UserCorrectionEntity(originalValue = a.name, correctedValue = b.name, correctionType = "ARTIST"))
        log(Type.MERGE, "ARTIST", from, a.name, b.name, extra = into.toString())
    }

    suspend fun mergeAlbums(from: Long, into: Long) = perform("Albums fusionnés", rebuild = true) {
        require(from != into) { "Même album" }
        val a = db.albumDao().getById(from) ?: error("Album introuvable")
        val b = db.albumDao().getById(into) ?: error("Album cible introuvable")
        db.withTransaction {
            mergeAlbumsInternal(from, into)
            log(Type.MERGE, "ALBUM", from, a.title, b.title, extra = into.toString())
        }
    }

    suspend fun mergeAlbumsBatch(pairs: List<Pair<Long, Long>>) = perform("${pairs.size} fusion(s) d'albums", rebuild = true) {
        db.withTransaction {
            for ((from, into) in pairs) {
                if (from == into) continue
                val a = db.albumDao().getById(from) ?: continue
                val b = db.albumDao().getById(into) ?: continue
                mergeAlbumsInternal(from, into)
                log(Type.MERGE, "ALBUM", from, a.title, b.title, extra = into.toString())
            }
        }
    }

    private suspend fun mergeAlbumsInternal(from: Long, into: Long) {
        db.trackDao().moveAlbum(from, into)
        db.scrobbleDao().moveAlbum(from, into)
        db.trackLinkDao().clearAlbumLinks(from)
        db.albumDao().delete(from)
    }

    suspend fun mergeTracks(from: Long, into: Long) = perform("Titres fusionnés", rebuild = true) {
        require(from != into) { "Même titre" }
        val a = db.trackDao().getById(from) ?: error("Titre introuvable")
        val b = db.trackDao().getById(into) ?: error("Titre cible introuvable")
        db.withTransaction {
            mergeTracksInternal(from, into)
            log(Type.MERGE, "TRACK", from, a.title, b.title, extra = into.toString())
        }
    }

    suspend fun mergeTracksBatch(pairs: List<Pair<Long, Long>>) = perform("${pairs.size} fusion(s) de titres", rebuild = true) {
        db.withTransaction {
            for ((from, into) in pairs) {
                if (from == into) continue
                val a = db.trackDao().getById(from) ?: continue
                val b = db.trackDao().getById(into) ?: continue
                mergeTracksInternal(from, into)
                log(Type.MERGE, "TRACK", from, a.title, b.title, extra = into.toString())
            }
        }
    }

    private suspend fun mergeTracksInternal(from: Long, into: Long) {
        db.scrobbleDao().moveTrack(from, into)
        // Les artistes du doublon (featurings) rejoignent la cible
        val target = db.trackDao().getById(into)
        db.trackLinkDao().artistIdsForTrack(from).forEach { db.trackLinkDao().insertTrackArtist(TrackArtistEntity(trackId = into, artistId = it, isPrimary = it == target?.artistId)) }
        db.trackLinkDao().clearTrackArtists(from)
        db.trackLinkDao().clearTrackAlbums(from)
        db.trackDao().delete(from)
    }

    /* ---------------- Changer artiste / album d'un titre ---------------- */

    suspend fun setTrackArtist(trackId: Long, artistId: Long) = perform("Artiste du titre modifié", rebuild = true) {
        val t = db.trackDao().getById(trackId) ?: error("Titre introuvable")
        db.withTransaction {
            db.trackDao().findByTitleAndArtist(t.title, artistId)?.takeIf { it.trackId != trackId }?.let { error("Ce titre existe déjà chez cet artiste — utilise Fusionner") }
            db.trackDao().setPrimaryArtist(trackId, artistId)
            db.trackLinkDao().clearTrackArtists(trackId)
            db.trackLinkDao().insertTrackArtist(TrackArtistEntity(trackId = trackId, artistId = artistId, isPrimary = true))
            db.scrobbleDao().setArtistForTrack(trackId, artistId)
            log(Type.ARTIST_CHANGE, "TRACK", trackId, t.artistId.toString(), artistId.toString())
        }
    }

    suspend fun setTrackAlbum(trackId: Long, albumId: Long?) = perform("Album du titre modifié", rebuild = true) {
        val t = db.trackDao().getById(trackId) ?: error("Titre introuvable")
        db.withTransaction {
            db.trackDao().setAlbum(trackId, albumId)
            db.scrobbleDao().setAlbumForTrack(trackId, albumId)
            log(Type.ALBUM_CHANGE, "TRACK", trackId, t.albumId?.toString(), albumId?.toString())
        }
    }

    /** Crée un album pour cet artiste (ou le réutilise) puis l'affecte au titre. */
    suspend fun setTrackAlbumByName(trackId: Long, albumTitle: String) {
        val t = db.trackDao().getById(trackId) ?: error("Titre introuvable")
        val title = TitleNormalizer.normalizeAlbumTitle(albumTitle)
        val id = db.albumDao().findByTitleAndArtist(title, t.artistId)?.albumId
            ?: db.albumDao().insert(AlbumEntity(title = title, titleRaw = albumTitle, artistId = t.artistId))
        setTrackAlbum(trackId, id)
    }

    /** Crée l'artiste s'il n'existe pas, puis l'affecte au titre. */
    suspend fun setTrackArtistByName(trackId: Long, artistName: String) {
        val name = artistName.trim(); require(name.isNotEmpty()) { "Nom vide" }
        val id = db.artistDao().findByNameNoCase(name)?.artistId ?: db.artistDao().insert(ArtistEntity(name = name, nameRaw = name))
        setTrackArtist(trackId, id)
    }

    /* ---------------- Écoutes / à corriger ---------------- */

    suspend fun deleteScrobble(id: Long) = perform("Écoute supprimée", rebuild = true) {
        val s = db.scrobbleDao().getById(id) ?: error("Écoute introuvable")
        db.scrobbleDao().delete(id)
        log(Type.DELETE_PLAY, "SCROBBLE", id, "${s.trackId}@${s.startedAt}", null)
    }

    suspend fun markReviewed(trackId: Long) = perform("Marqué comme correct", rebuild = false) {
        db.trackDao().markReviewed(trackId); log(Type.REVIEWED, "TRACK", trackId, null, null)
    }

    /* ---------------- Undo ---------------- */

    /** Annule la dernière action (renommage complet ; fusion : nom restauré uniquement ; suppression : irréversible). */
    suspend fun undo(): String {
        val last = db.editorDao().last() ?: return "Rien à annuler"
        val msg = when (last.type) {
            Type.RENAME -> {
                when (last.entityType) {
                    "ARTIST" -> db.artistDao().rename(last.entityId, last.before ?: "")
                    "ALBUM" -> db.albumDao().rename(last.entityId, last.before ?: "")
                    else -> db.trackDao().rename(last.entityId, last.before ?: "")
                }
                "↩️ Renommage annulé (« ${last.before} »)"
            }
            Type.COVER_CHANGE -> {
                if (last.entityType == "ARTIST") db.artistDao().setPhoto(last.entityId, last.before) else db.albumDao().setCover(last.entityId, last.before)
                "↩️ Image restaurée"
            }
            Type.MERGE -> {
                if (last.entityType == "ARTIST") {
                    // Le nom est restauré (artiste réactivé, sans ses titres — limite documentée)
                    db.artistDao().getById(last.entityId)?.let { db.artistDao().update(it.copy(isMerged = false, mergedIntoId = null)) }
                    "↩️ Nom « ${last.before} » restauré — les titres restent fusionnés"
                } else "⚠️ Une fusion d'${if (last.entityType == "ALBUM") "albums" else "titres"} ne peut pas être annulée"
            }
            Type.ARTIST_CHANGE -> { last.before?.toLongOrNull()?.let { setTrackArtist(last.entityId, it) }; "↩️ Artiste restauré" }
            Type.ALBUM_CHANGE -> { setTrackAlbum(last.entityId, last.before?.toLongOrNull()); "↩️ Album restauré" }
            Type.REVIEWED -> "ℹ️ Marquage « correct » conservé"
            else -> "⚠️ La suppression d'une écoute est irréversible"
        }
        db.editorDao().deleteEdit(last.id)
        _status.value = msg
        return msg
    }

    /* ---------------- Suggestions de fusion ---------------- */

    /** Artistes dont le nom normalisé est identique : chaque doublon → le plus écouté du groupe. */
    fun suggestArtistMerges(artists: List<ArtistEntity>): List<Pair<ArtistEntity, ArtistEntity>> =
        artists.filter { TitleNormalizer.normalizeKey(it.name).isNotBlank() }
            .groupBy { TitleNormalizer.normalizeKey(it.name) }.values.filter { it.size > 1 }
            .flatMap { g -> g.sortedByDescending { it.playCount }.let { s -> s.drop(1).map { it to s[0] } } }

    /** Albums du même artiste : normalisation identique ou l'un contient l'autre (max 200 paires). */
    fun suggestAlbumMerges(albums: List<AlbumEntity>): List<Pair<AlbumEntity, AlbumEntity>> {
        val out = ArrayList<Pair<AlbumEntity, AlbumEntity>>()
        for (group in albums.groupBy { it.artistId }.values) {
            val sorted = group.sortedByDescending { it.playCount }
            for (i in sorted.indices) for (j in i + 1 until sorted.size) {
                val a = TitleNormalizer.normalizeKey(sorted[i].title); val b = TitleNormalizer.normalizeKey(sorted[j].title)
                if (a.isNotBlank() && b.isNotBlank() && (a == b || a.contains(b) || b.contains(a))) out += sorted[j] to sorted[i]
                if (out.size >= 200) return out
            }
        }
        return out
    }

    /** Titres en double : même artiste principal + même titre normalisé ; chaque doublon → le plus écouté du groupe. */
    fun suggestTrackMerges(tracks: List<TrackEntity>): List<Pair<TrackEntity, TrackEntity>> =
        tracks.filter { TitleNormalizer.normalizeKey(it.title).isNotBlank() }
            .groupBy { it.artistId to TitleNormalizer.normalizeKey(it.title) }.values.filter { it.size > 1 }
            .flatMap { g -> g.sortedByDescending { it.playCount }.let { s -> s.drop(1).map { it to s[0] } } }
}
