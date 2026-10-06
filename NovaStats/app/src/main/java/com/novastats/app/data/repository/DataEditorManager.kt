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
class DataEditorManager(private val db: NovaDatabase, private val rebuilder: StatsRebuilder, private val library: LibraryRepository? = null) {

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    object Type {
        const val RENAME = "RENAME"; const val MERGE = "MERGE"; const val COVER_CHANGE = "COVER_CHANGE"
        const val ALBUM_CHANGE = "ALBUM_CHANGE"; const val ARTIST_CHANGE = "ARTIST_CHANGE"; const val DELETE_PLAY = "DELETE_PLAY"; const val REVIEWED = "REVIEWED"
        const val REVIEW_FIX = "REVIEW_FIX"; const val REVIEW_IGNORE = "REVIEW_IGNORE"; const val ALBUM_SHARED = "ALBUM_SHARED"
        const val LINK_VERSION = "LINK_VERSION"
    }

    private suspend fun log(type: String, entityType: String, entityId: Long, before: String?, after: String?, extra: String? = null) {
        db.editorDao().insertEdit(EditHistoryEntity(type = type, entityType = entityType, entityId = entityId, before = before, after = after, extraData = extra))
        db.editorDao().trimTo50()
    }

    private suspend fun <T> perform(message: String, rebuild: Boolean, block: suspend () -> T): T {
        _busy.value = true
        try {
            val r = runCatching { block() }
            // Les caches de résolution peuvent pointer vers des entités fusionnées/supprimées → toujours vidés
            library?.clearCaches()
            // Recalcul même en cas d'échec partiel (lot de fusions) pour ne jamais laisser des stats incohérentes
            if (rebuild) { _status.value = "$message · recalcul…"; rebuilder.rebuildAll(fullBillboard = true) }
            r.onFailure { t -> _status.value = "❌ ${t.message ?: t.javaClass.simpleName}"; throw t }
            _status.value = "✅ $message"
            return r.getOrThrow()
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
        val clash = al.artistId?.let { db.albumDao().findByTitleAndArtist(title, it) } ?: if (al.artistId == null) db.albumDao().findShared(title) else null
        clash?.takeIf { it.albumId != id }?.let { error("« $title » existe déjà pour cet artiste — utilise Fusionner") }
        db.albumDao().rename(id, title)
        log(Type.RENAME, "ALBUM", id, al.title, title)
    }

    /**
     * 🎭 Album multi-artistes (BO, album d'événement) : marque / retire la marque. Mémorisé comme correction ALBUM_SHARED
     * (survit aux ré-imports), puis les albums du même titre sont fusionnés en un album partagé — ou le partagé est
     * redécoupé par artiste principal — et tout est recalculé.
     */
    suspend fun setAlbumShared(id: Long, shared: Boolean) = perform(if (shared) "Album marqué multi-artistes" else "Marque multi-artistes retirée", rebuild = true) {
        val al = db.albumDao().getById(id) ?: error("Album introuvable")
        saveCorrection(TitleNormalizer.normalizeKey(al.title), if (shared) "1" else "0", LibraryRepository.CORRECTION_ALBUM_SHARED)
        library?.clearCaches()
        AlbumSharing.consolidate(db, library ?: LibraryRepository(db))
        log(Type.ALBUM_SHARED, "ALBUM", id, if (shared) "normal" else "multi-artistes", if (shared) "multi-artistes" else "normal")
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
        // Une transaction PAR paire : un échec n'annule pas les autres fusions
        val errors = ArrayList<String>()
        for ((from, into) in pairs) {
            if (from == into) continue
            val a = db.albumDao().getById(from) ?: continue
            val b = db.albumDao().getById(into) ?: continue
            runCatching { db.withTransaction { mergeAlbumsInternal(from, into); log(Type.MERGE, "ALBUM", from, a.title, b.title, extra = into.toString()) } }
                .onFailure { errors += "${a.title} : ${it.message ?: it.javaClass.simpleName}" }
        }
        if (errors.isNotEmpty()) error("${pairs.size - errors.size} fusion(s) faite(s), ${errors.size} échec(s) — ${errors.first()}")
    }

    private suspend fun mergeAlbumsInternal(from: Long, into: Long) {
        val a = db.albumDao().getById(from); val b = db.albumDao().getById(into)
        db.trackDao().moveAlbum(from, into)
        db.scrobbleDao().moveAlbum(from, into)
        db.trackLinkDao().retargetAlbumLinks(from, into)
        db.trackLinkDao().clearAlbumLinks(from)
        if (a != null && b != null) {
            db.albumDao().fillCover(into, a.coverUrl, a.coverSource)
            // Mémorisé : le prochain passage de ce nom d'album retombe sur la cible (sinon le doublon renaît à la prochaine écoute)
            if (TitleNormalizer.normalizeKey(a.title) != TitleNormalizer.normalizeKey(b.title) || a.title != b.title) {
                saveCorrection(a.title, b.title, "ALBUM")
                if (a.titleRaw != a.title) saveCorrection(a.titleRaw, b.title, "ALBUM")
            }
        }
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
        // Une transaction PAR paire : un échec n'annule pas les autres fusions
        val errors = ArrayList<String>()
        for ((from, into) in pairs) {
            if (from == into) continue
            val a = db.trackDao().getById(from) ?: continue
            val b = db.trackDao().getById(into) ?: continue
            runCatching { db.withTransaction { mergeTracksInternal(from, into); log(Type.MERGE, "TRACK", from, a.title, b.title, extra = into.toString()) } }
                .onFailure { errors += "${a.title} : ${it.message ?: it.javaClass.simpleName}" }
        }
        if (errors.isNotEmpty()) error("${pairs.size - errors.size} fusion(s) faite(s), ${errors.size} échec(s) — ${errors.first()}")
    }

    /** Lie une fiche de version/remix à son original sans fusionner ni déplacer ses écoutes. */
    suspend fun linkTrackAsVersion(versionId: Long, originalCandidateId: Long) = perform("Version liée à l'original", rebuild = true) {
        require(versionId != originalCandidateId) { "Choisis deux titres différents" }
        db.withTransaction {
            val version = db.trackDao().getById(versionId) ?: error("Version introuvable")
            var original = db.trackDao().getById(originalCandidateId) ?: error("Titre original introuvable")
            val visited = mutableSetOf<Long>()
            while (original.originalTrackId != null) {
                check(visited.add(original.trackId)) { "Cycle de versions détecté" }
                original = db.trackDao().getById(original.originalTrackId!!) ?: error("Titre racine introuvable")
            }
            require(original.trackId != versionId) { "Un titre ne peut pas être lié à lui-même ou à sa propre version" }
            require(version.originalTrackId != original.trackId) { "Cette version est déjà liée à ce titre" }
            require(db.trackDao().ownPlays(original.trackId) > 0) {
                "L'original doit avoir une écoute confirmée directement rattachée (pas seulement à ses versions)"
            }

            val childVersions = db.trackDao().versionsOf(versionId).map { it.trackId }
            db.trackDao().repointVersions(versionId, original.trackId)
            db.trackDao().setVersionLink(versionId, original.trackId, true)
            db.trackDao().flattenRoots()
            log(
                Type.LINK_VERSION, "TRACK", versionId,
                before = version.originalTrackId?.toString() ?: "ROOT",
                after = original.trackId.toString(),
                extra = "wasRemix=${if (version.isRemix) 1 else 0};children=${childVersions.joinToString(",")}"
            )
        }
    }

    private suspend fun mergeTracksInternal(from: Long, into: Long) {
        val source = db.trackDao().getById(from)
        var target = db.trackDao().getById(into) ?: error("Titre cible introuvable")
        // Liens de versions : si la cible était une version du doublon, elle devient racine ; les versions du doublon suivent
        if (target.originalTrackId == from) { db.trackDao().detachRoot(into); target = target.copy(originalTrackId = null) }
        db.trackDao().repointVersions(from, target.originalTrackId ?: into)
        // Même écoute journalisée deux fois (même instant) → une seule, puis transfert (UNIQUE track_id + started_at)
        db.scrobbleDao().dropClashing(from, into)
        db.scrobbleDao().moveTrack(from, into)
        // Les artistes du doublon (featurings) rejoignent la cible
        db.trackLinkDao().artistIdsForTrack(from).forEach { db.trackLinkDao().insertTrackArtist(TrackArtistEntity(trackId = into, artistId = it, isPrimary = it == target.artistId)) }
        db.trackLinkDao().clearTrackArtists(from)
        db.trackLinkDao().clearTrackAlbums(from)
        if (source != null) {
            if (target.coverUrl == null && source.coverUrl != null) db.trackDao().update(target.copy(coverUrl = source.coverUrl, coverSource = source.coverSource))
            // Mémorisé : la prochaine écoute arrivant sous l'ancien libellé retombe sur la cible (sinon le doublon renaît)
            if (source.title != target.title) saveCorrection(source.title, target.title, "TITLE")
            if (source.titleRaw != source.title && source.titleRaw != target.title) saveCorrection(source.titleRaw, target.title, "TITLE")
        }
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

    /* ---------------- ⚠️ À corriger (révision par écoute) ---------------- */

    /** Mémorise une règle « avant → après » (ou une valeur confirmée si avant == après). Idempotent. */
    private suspend fun saveCorrection(original: String, corrected: String, type: String) {
        val o = original.trim(); val c = corrected.trim()
        if (o.isEmpty() || c.isEmpty()) return
        val existing = db.editorDao().correction(o, type)
        db.editorDao().upsertCorrection(UserCorrectionEntity(id = existing?.id ?: 0, originalValue = o, correctedValue = c, correctionType = type, timesApplied = existing?.timesApplied ?: 0))
        library?.invalidateCorrections()
    }

    data class ReviewFix(
        val trackId: Long, val scrobbleIds: List<Long>, val allChecked: Boolean,
        val title: String, val artists: String, val album: String?, val coverUrl: String?,
        /** Valeurs brutes d'origine (pour la règle « avant → après »). */
        val rawTitle: String?, val rawArtist: String?, val rawAlbum: String?
    )

    /**
     * Valider : les écoutes **cochées** prennent les nouvelles valeurs (titre / artiste(s) / album / pochette), passent à 100 %
     * et sortent de la liste ; les autres restent. Une seule entrée d'historique (un seul Undo).
     * La règle « avant → après » n'est enregistrée que si toutes les écoutes étaient cochées.
     */
    suspend fun applyReviewFix(fix: ReviewFix) = perform("Correction appliquée (${fix.scrobbleIds.size} écoute${if (fix.scrobbleIds.size > 1) "s" else ""})", rebuild = true) {
        require(fix.scrobbleIds.isNotEmpty()) { "Aucune écoute cochée" }
        val title = fix.title.trim(); require(title.isNotEmpty()) { "Titre vide" }
        val artists = fix.artists.trim().ifEmpty { "Artiste inconnu" }
        val lib = library ?: error("Bibliothèque indisponible")
        val old = db.trackDao().getById(fix.trackId) ?: error("Titre introuvable")
        val oldArtistName = db.artistDao().getById(old.artistId)?.name ?: ""
        val oldAlbumTitle = old.albumId?.let { db.albumDao().getById(it)?.title }
        db.withTransaction {
            val target = lib.resolve(title, artists, fix.album?.takeIf { it.isNotBlank() }, old.durationMs, old.genre, applyCorrections = false)
            val mode: String
            if (target.trackId == old.trackId) {
                // Même identité (titre normalisé + artiste principal) : mise à jour sur place (affichage du titre, album)
                mode = "INPLACE"
                db.trackDao().setTitleAndAlbum(old.trackId, title, target.albumId)
                db.scrobbleDao().moveScrobbles(fix.scrobbleIds, old.trackId, target.primaryArtistId, target.albumId)
            } else {
                mode = "MOVE"
                db.scrobbleDao().moveScrobbles(fix.scrobbleIds, target.trackId, target.primaryArtistId, target.albumId)
            }
            if (!fix.coverUrl.isNullOrBlank()) db.trackDao().setCover(target.trackId, fix.coverUrl)
            db.scrobbleDao().clearReview(fix.scrobbleIds)
            if (fix.allChecked) {
                db.trackDao().markReviewed(target.trackId)
                fix.rawTitle?.takeIf { it.trim() != title }?.let { saveCorrection(it, title, "TITLE") }
                fix.rawArtist?.takeIf { it.isNotBlank() && it.trim() != artists }?.let { saveCorrection(it, artists, "ARTIST") }
                val album = fix.album?.trim().orEmpty()
                fix.rawAlbum?.takeIf { it.isNotBlank() && album.isNotEmpty() && it.trim() != album }?.let { saveCorrection(it, album, "ALBUM") }
            }
            log(
                Type.REVIEW_FIX, "TRACK", old.trackId,
                before = "${old.title} — $oldArtistName${oldAlbumTitle?.let { " · $it" } ?: ""}",
                after = "$title — $artists${fix.album?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                extra = "mode=$mode;ids=${fix.scrobbleIds.joinToString(",")};artist=${old.artistId};album=${old.albumId ?: ""};title=${old.title}"
            )
        }
        lib.clearCaches()
    }

    /** C'est correct (🟡) / confirmation : le titre et ses écoutes sortent de la liste sans rien changer. */
    suspend fun confirmReview(trackId: Long) = perform("Marqué comme correct", rebuild = false) {
        db.trackDao().markReviewed(trackId)
        db.scrobbleDao().clearReviewOfTrack(trackId)
        log(Type.REVIEWED, "TRACK", trackId, null, null)
    }

    /** Ignorer : données inchangées, sortie de la liste, valeurs brutes mémorisées comme confirmées (définitif). */
    suspend fun ignoreReview(trackId: Long, rawTitle: String?, rawArtist: String?) = perform("Ignoré — valeur confirmée", rebuild = false) {
        db.trackDao().markReviewed(trackId)
        db.scrobbleDao().clearReviewOfTrack(trackId)
        rawTitle?.let { saveCorrection(it, it, "TITLE") }
        rawArtist?.let { saveCorrection(it, it, "ARTIST") }
        log(Type.REVIEW_IGNORE, "TRACK", trackId, rawTitle, rawArtist)
    }

    /** Supprime plusieurs écoutes (irréversible) — une seule entrée d'historique. */
    suspend fun deleteScrobbles(ids: List<Long>) = perform("${ids.size} écoute${if (ids.size > 1) "s" else ""} supprimée${if (ids.size > 1) "s" else ""}", rebuild = true) {
        require(ids.isNotEmpty()) { "Aucune écoute cochée" }
        db.scrobbleDao().deleteMany(ids)
        log(Type.DELETE_PLAY, "SCROBBLE", ids.first(), ids.joinToString(","), null)
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
            Type.LINK_VERSION -> {
                val version = db.trackDao().getById(last.entityId)
                if (version == null) "⚠️ Lien non annulable : le titre version n'existe plus"
                else {
                    val extra = (last.extraData ?: "").split(";").mapNotNull { part ->
                        part.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
                    }.toMap()
                    val oldRootId = last.before?.takeUnless { it == "ROOT" }?.toLongOrNull()
                    val oldIsRemix = extra["wasRemix"] == "1"
                    val childIds = extra["children"]?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
                    db.withTransaction {
                        db.trackDao().setVersionLink(version.trackId, oldRootId, oldIsRemix)
                        childIds.forEach { childId ->
                            db.trackDao().getById(childId)?.let { child -> db.trackDao().setVersionLink(childId, version.trackId, child.isRemix) }
                        }
                        db.trackDao().flattenRoots()
                    }
                    library?.clearCaches()
                    rebuilder.rebuildAll(fullBillboard = true)
                    "↩️ Lien entre les titres annulé"
                }
            }
            Type.ARTIST_CHANGE -> { last.before?.toLongOrNull()?.let { setTrackArtist(last.entityId, it) }; "↩️ Artiste restauré" }
            Type.ALBUM_SHARED -> {
                // L'album d'origine peut avoir été fusionné / supprimé : on inverse la correction sur le titre, puis on reconsolide
                val title = db.editorDao().correctionsOfType(LibraryRepository.CORRECTION_ALBUM_SHARED).firstOrNull { it.correctedValue == (if (last.after == "multi-artistes") "1" else "0") && last.createdAt - it.createdAt < 5 * 60_000L }?.originalValue
                if (title == null) "⚠️ Marquage non annulable" else {
                    saveCorrection(title, if (last.after == "multi-artistes") "0" else "1", LibraryRepository.CORRECTION_ALBUM_SHARED)
                    library?.clearCaches()
                    AlbumSharing.consolidate(db, library ?: LibraryRepository(db))
                    "↩️ Marquage multi-artistes ${if (last.after == "multi-artistes") "retiré" else "rétabli"}"
                }
            }
            Type.ALBUM_CHANGE -> { setTrackAlbum(last.entityId, last.before?.toLongOrNull()); "↩️ Album restauré" }
            Type.REVIEWED, Type.REVIEW_IGNORE -> "ℹ️ Marquage « correct / ignoré » conservé"
            Type.REVIEW_FIX -> {
                val kv = (last.extraData ?: "").split(";").mapNotNull { it.split("=", limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] } }.toMap()
                val ids = kv["ids"]?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
                val oldArtist = kv["artist"]?.toLongOrNull()
                val oldAlbum = kv["album"]?.toLongOrNull()
                val oldTrack = db.trackDao().getById(last.entityId)
                if (ids.isEmpty() || oldTrack == null || oldArtist == null) "⚠️ Correction non annulable (titre d'origine disparu)"
                else {
                    db.withTransaction {
                        if (kv["mode"] == "INPLACE") db.trackDao().setTitleAndAlbum(oldTrack.trackId, kv["title"] ?: oldTrack.title, oldAlbum)
                        db.scrobbleDao().moveScrobbles(ids, oldTrack.trackId, oldArtist, oldAlbum)
                        db.scrobbleDao().reflag(ids, "Correction annulée")
                    }
                    library?.clearCaches()
                    rebuilder.rebuildAll(fullBillboard = true)
                    "↩️ Correction annulée — ${ids.size} écoute(s) de retour dans ⚠️ À corriger"
                }
            }
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
