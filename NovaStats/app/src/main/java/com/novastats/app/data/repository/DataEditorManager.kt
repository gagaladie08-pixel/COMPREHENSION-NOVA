package com.novastats.app.data.repository

import androidx.room.withTransaction
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.db.entity.AlbumEntity
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.data.db.entity.EditHistoryEntity
import com.novastats.app.data.db.entity.ScrobbleEntity
import com.novastats.app.data.db.entity.TrackAlbumEntity
import com.novastats.app.data.db.entity.TrackArtistEntity
import com.novastats.app.data.db.entity.TrackEntity
import com.novastats.app.data.db.entity.UserCorrectionEntity
import com.novastats.app.domain.TitleNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val operationMutex = Mutex()

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

    private suspend fun <T> perform(message: String, rebuild: Boolean, block: suspend () -> T): T = operationMutex.withLock {
        _busy.value = true
        try {
            val r = runCatching { block() }
            r.exceptionOrNull()?.takeIf { it is CancellationException }?.let { throw it }
            // Les caches de résolution peuvent pointer vers des entités fusionnées/supprimées → toujours vidés
            library?.clearCaches()
            // Recalcul même en cas d'échec partiel (lot de fusions) pour ne jamais laisser des stats incohérentes
            if (rebuild) { _status.value = "$message · recalcul…"; rebuilder.rebuildAll(fullBillboard = true) }
            r.onFailure { t -> _status.value = "❌ ${t.message ?: t.javaClass.simpleName}"; throw t }
            _status.value = "✅ $message"
            r.getOrThrow()
        } finally { _busy.value = false }
    }

    /* ---------------- Renommer ---------------- */

    suspend fun renameArtist(id: Long, newName: String) = perform("Artiste renommé", rebuild = false) {
        db.withTransaction {
            val a = db.artistDao().getById(id) ?: error("Artiste introuvable")
            val name = newName.trim(); require(name.isNotEmpty()) { "Nom vide" }
            db.artistDao().findByNameNoCase(name)?.takeIf { it.artistId != id }?.let { error("« $name » existe déjà — utilise Fusionner") }
            val corrections = snapshotCorrections(listOf("ARTIST" to a.name))
            db.artistDao().rename(id, name)
            saveCorrection(a.name, name, "ARTIST")
            log(Type.RENAME, "ARTIST", id, a.name, name, extra = withRowsExtra(null, "corrections", corrections))
        }
    }

    suspend fun renameAlbum(id: Long, newTitle: String) = perform("Album renommé", rebuild = false) {
        db.withTransaction {
            val al = db.albumDao().getById(id) ?: error("Album introuvable")
            val title = newTitle.trim(); require(title.isNotEmpty()) { "Titre vide" }
            val clash = al.artistId?.let { db.albumDao().findByTitleAndArtist(title, it) } ?: if (al.artistId == null) db.albumDao().findShared(title) else null
            clash?.takeIf { it.albumId != id }?.let { error("« $title » existe déjà pour cet artiste — utilise Fusionner") }
            val entries = listOf("ALBUM" to al.title, "ALBUM" to al.titleRaw)
            val corrections = snapshotCorrections(entries)
            db.albumDao().rename(id, title)
            entries.forEach { (type, original) -> saveCorrection(original, title, type) }
            log(Type.RENAME, "ALBUM", id, al.title, title, extra = withRowsExtra(null, "corrections", corrections))
        }
    }

    /**
     * 🎭 Album multi-artistes (BO, album d'événement) : marque / retire la marque. Mémorisé comme correction ALBUM_SHARED
     * (survit aux ré-imports), puis les albums du même titre sont fusionnés en un album partagé — ou le partagé est
     * redécoupé par artiste principal — et tout est recalculé.
     */
    suspend fun setAlbumShared(id: Long, shared: Boolean) = perform(if (shared) "Album marqué multi-artistes" else "Marque multi-artistes retirée", rebuild = true) {
        db.withTransaction {
            val al = db.albumDao().getById(id) ?: error("Album introuvable")
            val original = TitleNormalizer.normalizeKey(al.title)
            val corrections = snapshotCorrections(listOf(LibraryRepository.CORRECTION_ALBUM_SHARED to original))
            saveCorrection(original, if (shared) "1" else "0", LibraryRepository.CORRECTION_ALBUM_SHARED)
            library?.clearCaches()
            AlbumSharing.consolidate(db, library ?: LibraryRepository(db))
            log(
                Type.ALBUM_SHARED, "ALBUM", id,
                if (shared) "normal" else "multi-artistes", if (shared) "multi-artistes" else "normal",
                withRowsExtra(null, "corrections", corrections)
            )
        }
    }

    suspend fun renameTrack(id: Long, newTitle: String) = perform("Titre renommé", rebuild = false) {
        db.withTransaction {
            val t = db.trackDao().getById(id) ?: error("Titre introuvable")
            val title = newTitle.trim(); require(title.isNotEmpty()) { "Titre vide" }
            db.trackDao().findByTitleAndArtist(title, t.artistId)?.takeIf { it.trackId != id }?.let { error("« $title » existe déjà pour cet artiste — utilise Fusionner") }
            val entries = listOf("TITLE" to t.title, "TITLE" to t.titleRaw)
            val corrections = snapshotCorrections(entries)
            db.trackDao().rename(id, title)
            entries.forEach { (type, original) -> saveCorrection(original, title, type) }
            log(Type.RENAME, "TRACK", id, t.title, title, extra = withRowsExtra(null, "corrections", corrections))
        }
    }

    /* ---------------- Images (galerie / web) ---------------- */

    suspend fun setArtistPhoto(id: Long, url: String?) = perform("Photo mise à jour", rebuild = false) {
        val cleanedUrl = url?.takeIf(String::isNotBlank)
        db.withTransaction {
            val a = db.artistDao().getById(id) ?: error("Artiste introuvable")
            db.artistDao().setPhoto(id, cleanedUrl)
            val extra = withRowsExtra(null, "previousSource", listOf(listOf(a.photoSource)))
            log(Type.COVER_CHANGE, "ARTIST", id, a.photoUrl, cleanedUrl, extra)
        }
    }

    suspend fun setAlbumCover(id: Long, url: String?) = perform("Pochette mise à jour", rebuild = false) {
        db.withTransaction {
            val al = db.albumDao().getById(id) ?: error("Album introuvable")
            val affected = if (url.isNullOrBlank()) db.albumDao().tracksForUserCoverRemoval(id)
                else db.albumDao().tracksForUserCoverUpdate(id)
            db.albumDao().setCover(id, url?.takeIf(String::isNotBlank))
            if (url.isNullOrBlank()) db.albumDao().clearUserAlbumCoverFromTracks(id)
            else db.albumDao().applyUserAlbumCoverToTracks(id, url)
            var extra = withRowsExtra(null, "previousSource", listOf(listOf(al.coverSource)))
            extra = withRowsExtra(extra, "trackCovers", affected.map { listOf(it.trackId.toString(), it.coverUrl, it.coverSource) })
            extra = withRowsExtra(extra, "appliedCover", listOf(listOf(url?.takeIf(String::isNotBlank))))
            log(Type.COVER_CHANGE, "ALBUM", id, al.coverUrl, url?.takeIf(String::isNotBlank), extra)
        }
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
        for (t in db.trackDao().allByArtistId(from)) {
            val existing = db.trackDao().findByTitleAndArtist(t.title, into)
            if (existing != null && existing.trackId != t.trackId) mergeTracksInternal(t.trackId, existing.trackId)
        }
        db.trackDao().moveArtist(from, into)
        db.trackLinkDao().dropDuplicateLinks(from, into)
        db.trackLinkDao().moveArtist(from, into)
        db.trackLinkDao().normalizePrimaryLinksForArtist(into)
        db.trackLinkDao().ensurePrimaryLinksForArtist(into)
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
        var succeeded = 0
        for ((from, into) in pairs) {
            if (from == into) continue
            val a = db.albumDao().getById(from) ?: continue
            val b = db.albumDao().getById(into) ?: continue
            try {
                db.withTransaction { mergeAlbumsInternal(from, into); log(Type.MERGE, "ALBUM", from, a.title, b.title, extra = into.toString()) }
                succeeded++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                errors += "${a.title} : ${failure.message ?: failure.javaClass.simpleName}"
            }
        }
        if (errors.isNotEmpty()) error("$succeeded fusion(s) faite(s), ${errors.size} échec(s) — ${errors.first()}")
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
        var succeeded = 0
        for ((from, into) in pairs) {
            if (from == into) continue
            val a = db.trackDao().getById(from) ?: continue
            val b = db.trackDao().getById(into) ?: continue
            try {
                db.withTransaction { mergeTracksInternal(from, into); log(Type.MERGE, "TRACK", from, a.title, b.title, extra = into.toString()) }
                succeeded++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                errors += "${a.title} : ${failure.message ?: failure.javaClass.simpleName}"
            }
        }
        if (errors.isNotEmpty()) error("$succeeded fusion(s) faite(s), ${errors.size} échec(s) — ${errors.first()}")
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
        db.trackLinkDao().copyAlbumLinks(from, into)
        target.albumId?.let { db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = into, albumId = it)) }
        db.trackLinkDao().clearTrackArtists(from)
        db.trackLinkDao().clearTrackAlbums(from)
        if (source != null) {
            val mergedTarget = target.copy(
                albumId = target.albumId ?: source.albumId,
                durationMs = target.durationMs ?: source.durationMs,
                genre = target.genre ?: source.genre,
                coverUrl = target.coverUrl ?: source.coverUrl,
                coverSource = if (target.coverUrl == null) source.coverSource else target.coverSource
            )
            if (mergedTarget != target) db.trackDao().update(mergedTarget)
            mergedTarget.albumId?.let { db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = into, albumId = it)) }
            // Mémorisé : la prochaine écoute arrivant sous l'ancien libellé retombe sur la cible (sinon le doublon renaît)
            if (source.title != target.title) saveCorrection(source.title, target.title, "TITLE")
            if (source.titleRaw != source.title && source.titleRaw != target.title) saveCorrection(source.titleRaw, target.title, "TITLE")
        }
        db.trackDao().delete(from)
    }

    /* ---------------- Changer artiste / album d'un titre ---------------- */

    suspend fun setTrackArtist(trackId: Long, artistId: Long) = perform("Artiste du titre modifié", rebuild = true) {
        db.withTransaction { setTrackArtistInternal(trackId, artistId) }
    }

    private suspend fun setTrackArtistInternal(trackId: Long, artistId: Long) {
        val t = db.trackDao().getById(trackId) ?: error("Titre introuvable")
        val newArtist = db.artistDao().getById(artistId) ?: error("Artiste introuvable")
        db.trackDao().findByTitleAndArtist(t.title, artistId)?.takeIf { it.trackId != trackId }?.let { error("Ce titre existe déjà chez cet artiste — utilise Fusionner") }
        val previousLinks = db.trackLinkDao().trackArtistsForTrack(trackId)
        val rawArtists = db.scrobbleDao().allOfTrack(trackId).mapNotNull { it.rawArtist?.takeIf(String::isNotBlank) }.distinct()
        val oldArtistName = db.artistDao().getById(t.artistId)?.name
        val correctionEntries = (rawArtists.ifEmpty { listOfNotNull(oldArtistName) }).map { "ARTIST" to it }
        val correctionStates = snapshotCorrections(correctionEntries)

        db.trackDao().setPrimaryArtist(trackId, artistId)
        db.trackLinkDao().clearTrackArtists(trackId)
        db.trackLinkDao().insertTrackArtist(TrackArtistEntity(trackId = trackId, artistId = artistId, isPrimary = true))
        previousLinks.filter { !it.isPrimary && it.artistId != artistId }.forEach { link ->
            db.trackLinkDao().insertTrackArtist(link.copy(id = 0, artistId = link.artistId, isPrimary = false))
        }
        db.scrobbleDao().setArtistForTrack(trackId, artistId)
        correctionEntries.forEach { (_, raw) -> saveCorrection(raw, newArtist.name, "ARTIST") }

        var extra = withRowsExtra(
            null, "artistLinks", previousLinks.map { listOf(it.artistId.toString(), it.isPrimary.toString(), it.role) }
        )
        extra = withRowsExtra(extra, "corrections", correctionStates)
        log(Type.ARTIST_CHANGE, "TRACK", trackId, t.artistId.toString(), artistId.toString(), extra)
    }

    suspend fun setTrackAlbum(trackId: Long, albumId: Long?) = perform("Album du titre modifié", rebuild = true) {
        db.withTransaction { setTrackAlbumInternal(trackId, albumId) }
    }

    private suspend fun setTrackAlbumInternal(trackId: Long, albumId: Long?) {
        val t = db.trackDao().getById(trackId) ?: error("Titre introuvable")
        val targetAlbum = albumId?.let { db.albumDao().getById(it) ?: error("Album introuvable") }
        val rawAlbums = db.scrobbleDao().allOfTrack(trackId).mapNotNull { it.rawAlbum?.takeIf(String::isNotBlank) }.distinct()
        val oldAlbumTitle = t.albumId?.let { db.albumDao().getById(it)?.title }
        val correctionEntries = (rawAlbums.ifEmpty { listOfNotNull(oldAlbumTitle) }).map { "ALBUM" to it }
        val correctionStates = snapshotCorrections(correctionEntries)

        db.trackDao().setAlbum(trackId, albumId)
        t.albumId?.let { db.trackLinkDao().unlinkTrackAlbum(trackId, it) }
        albumId?.let { db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = trackId, albumId = it)) }
        db.scrobbleDao().setAlbumForTrack(trackId, albumId)
        correctionEntries.forEach { (_, raw) -> saveCorrection(raw, targetAlbum?.title.orEmpty(), "ALBUM") }
        val extra = withRowsExtra(null, "corrections", correctionStates)
        log(Type.ALBUM_CHANGE, "TRACK", trackId, t.albumId?.toString(), albumId?.toString(), extra)
    }

    /** Crée un album pour cet artiste (ou le réutilise) puis l'affecte au titre, atomiquement. */
    suspend fun setTrackAlbumByName(trackId: Long, albumTitle: String) = perform("Album du titre modifié", rebuild = true) {
        db.withTransaction {
            val t = db.trackDao().getById(trackId) ?: error("Titre introuvable")
            val rawTitle = albumTitle.trim(); require(rawTitle.isNotEmpty()) { "Titre d'album vide" }
            val title = TitleNormalizer.normalizeAlbumTitle(rawTitle)
            require(title.isNotEmpty()) { "Titre d'album invalide" }
            val id = db.albumDao().findByTitleAndArtist(title, t.artistId)?.albumId
                ?: db.albumDao().insert(AlbumEntity(title = title, titleRaw = rawTitle, artistId = t.artistId))
            setTrackAlbumInternal(trackId, id)
        }
    }

    /** Crée l'artiste s'il n'existe pas, puis l'affecte au titre, atomiquement. */
    suspend fun setTrackArtistByName(trackId: Long, artistName: String) = perform("Artiste du titre modifié", rebuild = true) {
        db.withTransaction {
            db.trackDao().getById(trackId) ?: error("Titre introuvable")
            val name = artistName.trim(); require(name.isNotEmpty()) { "Nom d'artiste vide" }
            val id = db.artistDao().findByNameNoCase(name)?.artistId
                ?: db.artistDao().insert(ArtistEntity(name = name, nameRaw = name))
            setTrackArtistInternal(trackId, id)
        }
    }

    /* ---------------- ⚠️ À corriger (révision par écoute) ---------------- */

    /** Mémorise une règle « avant → après » (ou une valeur confirmée si avant == après). Idempotent. */
    private suspend fun saveCorrection(original: String, corrected: String, type: String) {
        val o = original.trim(); val c = corrected.trim()
        if (o.isEmpty() || (c.isEmpty() && type != "ALBUM")) return
        val existing = findCorrection(o, type)
        db.editorDao().upsertCorrection(
            UserCorrectionEntity(
                id = existing?.id ?: 0,
                originalValue = existing?.originalValue ?: o,
                correctedValue = c,
                correctionType = type,
                timesApplied = existing?.timesApplied ?: 0,
                createdAt = System.currentTimeMillis()
            )
        )
        library?.invalidateCorrections()
    }

    private suspend fun findCorrection(original: String, type: String): UserCorrectionEntity? {
        db.editorDao().correction(original, type)?.let { return it }
        val key = TitleNormalizer.normalizeKey(original)
        return db.editorDao().correctionsOfType(type)
            .filter { TitleNormalizer.normalizeKey(it.originalValue) == key }
            .maxByOrNull { it.createdAt }
    }

    /** Sauvegarde l'état antérieur des règles exactement touchées, pour que Undo soit réellement réversible. */
    private suspend fun snapshotCorrections(entries: List<Pair<String, String>>): List<List<String?>> {
        val result = ArrayList<List<String?>>()
        val seen = HashSet<Pair<String, String>>()
        for ((type, raw) in entries) {
            val original = raw.trim()
            if (original.isEmpty() || !seen.add(type to TitleNormalizer.normalizeKey(original))) continue
            val previous = findCorrection(original, type)
            result += listOf(
                type, original, if (previous == null) "0" else "1", previous?.id?.toString(),
                previous?.originalValue, previous?.correctedValue, previous?.timesApplied?.toString(), previous?.createdAt?.toString()
            )
        }
        return result
    }

    private suspend fun restoreCorrectionSnapshots(rows: List<List<String?>>) {
        for (row in rows) {
            val type = row.getOrNull(0) ?: continue
            val requestedOriginal = row.getOrNull(1) ?: continue
            if (row.getOrNull(2) != "1") {
                db.editorDao().deleteCorrection(requestedOriginal, type)
                continue
            }
            val id = row.getOrNull(3)?.toLongOrNull() ?: continue
            val previousOriginal = row.getOrNull(4) ?: continue
            val previousCorrected = row.getOrNull(5) ?: continue
            val timesApplied = row.getOrNull(6)?.toIntOrNull() ?: 0
            val createdAt = row.getOrNull(7)?.toLongOrNull() ?: System.currentTimeMillis()
            db.editorDao().upsertCorrection(
                UserCorrectionEntity(
                    id = id, originalValue = previousOriginal, correctedValue = previousCorrected,
                    correctionType = type, timesApplied = timesApplied, createdAt = createdAt
                )
            )
        }
        library?.invalidateCorrections()
    }

    private fun withRowsExtra(base: String?, key: String, rows: List<List<String?>>): String? {
        if (rows.isEmpty()) return base
        return listOfNotNull(base?.takeIf { it.isNotBlank() }, "$key=${EditorUndoCodec.encodeRows(rows)}").joinToString(";")
    }

    private fun parseExtraData(extra: String?): Map<String, String> = (extra ?: "").split(";").mapNotNull { part ->
        part.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }.toMap()

    private fun trackStateRow(track: TrackEntity): List<String?> = listOf(
        track.trackId.toString(), track.title, track.albumId?.toString(), track.coverUrl, track.coverSource,
        track.confidenceScore.toString(), track.needsReview.toString(), track.durationMs?.toString(), track.genre,
        track.titleRaw, track.artistId.toString(), track.isRemix.toString(), track.originalTrackId?.toString()
    )

    private fun scrobbleStateRow(scrobble: ScrobbleEntity): List<String?> = listOf(
        scrobble.scrobbleId.toString(), scrobble.trackId.toString(), scrobble.artistId.toString(),
        scrobble.albumId?.toString(), scrobble.confidenceScore.toString(), scrobble.needsReview.toString(), scrobble.reviewReason
    )

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
        val album = fix.album?.trim()?.takeIf { it.isNotEmpty() }
        val lib = library ?: error("Bibliothèque indisponible")
        db.withTransaction {
            val old = db.trackDao().getById(fix.trackId) ?: error("Titre introuvable")
            val tracksBefore = db.trackDao().allList().associateBy { it.trackId }
            val artistIdsBefore = db.artistDao().allIds().toSet()
            val albumIdsBefore = db.albumDao().all().map { it.albumId }.toSet()
            val artistLinksBefore = db.trackLinkDao().allTrackArtists().groupBy { it.trackId }
            val albumLinksBefore = db.trackLinkDao().allTrackAlbums().groupBy { it.trackId }
            val requestedIds = fix.scrobbleIds.distinct()
            require(requestedIds.size == fix.scrobbleIds.size) { "Une écoute est sélectionnée plusieurs fois" }
            val selectedPlays = db.scrobbleDao().byIds(requestedIds)
            require(selectedPlays.size == requestedIds.size) { "Une ou plusieurs écoutes sont introuvables" }
            require(selectedPlays.all { it.trackId == fix.trackId }) { "Les écoutes sélectionnées ne correspondent pas à ce titre" }
            val scrobbleStates = selectedPlays.map(::scrobbleStateRow)
            val oldArtistName = db.artistDao().getById(old.artistId)?.name ?: ""
            val oldAlbumTitle = old.albumId?.let { db.albumDao().getById(it)?.title }

            val correctionEntries = mutableListOf<Pair<String, String>>()
            if (fix.allChecked) {
                fix.rawTitle?.takeIf { it.trim() != title }?.let { correctionEntries += "TITLE" to it }
                fix.rawArtist?.takeIf { it.isNotBlank() && it.trim() != artists }?.let { correctionEntries += "ARTIST" to it }
                fix.rawAlbum?.takeIf { it.isNotBlank() && it.trim() != album.orEmpty() }?.let { correctionEntries += "ALBUM" to it }
            }
            val correctionKeys = correctionEntries + album?.let {
                LibraryRepository.CORRECTION_ALBUM_SHARED to TitleNormalizer.normalizeKey(TitleNormalizer.normalizeAlbumTitle(it))
            }.orEmpty()
            val correctionStates = snapshotCorrections(correctionKeys)
            val target = lib.resolve(title, artists, album, old.durationMs, old.genre, applyCorrections = false)
            val mode = if (target.trackId == old.trackId) "INPLACE" else "MOVE"

            if (mode == "INPLACE") {
                // Même identité (titre normalisé + artiste principal) : mise à jour sur place.
                db.trackDao().setTitleAndAlbum(old.trackId, title, target.albumId)
                if (old.albumId != target.albumId) {
                    old.albumId?.let { db.trackLinkDao().unlinkTrackAlbum(old.trackId, it) }
                    target.albumId?.let { db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = old.trackId, albumId = it)) }
                }
            }
            db.scrobbleDao().moveScrobbles(fix.scrobbleIds, target.trackId, target.primaryArtistId, target.albumId)
            if (!fix.coverUrl.isNullOrBlank()) db.trackDao().setCover(target.trackId, fix.coverUrl)
            db.scrobbleDao().clearReview(fix.scrobbleIds)
            if (fix.allChecked) {
                // Le statut est porté à la fois par les écoutes et par la fiche titre. En cas de déplacement,
                // il faut donc aussi libérer la fiche source, sinon elle reste bloquée dans la file.
                db.trackDao().markReviewed(old.trackId)
                db.trackDao().markReviewed(target.trackId)
                correctionEntries.forEach { (type, raw) ->
                    val corrected = when (type) {
                        "TITLE" -> title
                        "ARTIST" -> artists
                        else -> album.orEmpty()
                    }
                    saveCorrection(raw, corrected, type)
                }
            }

            // Capturer après toutes les mutations : resolve(), la modification INPLACE, les liens, la pochette,
            // le déplacement des écoutes et leur validation doivent tous être réversibles par une seule Undo.
            val tracksAfter = db.trackDao().allList().associateBy { it.trackId }
            val changedTracks = tracksBefore.values.filter { before -> tracksAfter[before.trackId]?.let { it != before } == true }
            val newTrackIds = (tracksAfter.keys - tracksBefore.keys).sorted()
            val newArtistIds = (db.artistDao().allIds().toSet() - artistIdsBefore).sorted()
            val newAlbumIds = (db.albumDao().all().map { it.albumId }.toSet() - albumIdsBefore).sorted()
            val artistLinksAfter = db.trackLinkDao().allTrackArtists().groupBy { it.trackId }
            val albumLinksAfter = db.trackLinkDao().allTrackAlbums().groupBy { it.trackId }
            val changedArtistLinkTrackIds = tracksBefore.keys.filter { artistLinksBefore[it].orEmpty() != artistLinksAfter[it].orEmpty() }.sorted()
            val changedAlbumLinkTrackIds = tracksBefore.keys.filter { albumLinksBefore[it].orEmpty() != albumLinksAfter[it].orEmpty() }.sorted()
            val trackArtistRows = changedArtistLinkTrackIds.flatMap { trackId ->
                artistLinksBefore[trackId].orEmpty().map { link -> listOf(trackId.toString(), link.artistId.toString(), link.isPrimary.toString(), link.role) }
            }
            val trackAlbumRows = changedAlbumLinkTrackIds.flatMap { trackId ->
                albumLinksBefore[trackId].orEmpty().map { link -> listOf(trackId.toString(), link.albumId.toString(), link.trackNumber?.toString(), link.isPrimary.toString()) }
            }
            val trackStates = changedTracks.map(::trackStateRow)

            var extra = "mode=$mode;ids=${fix.scrobbleIds.joinToString(",")};artist=${old.artistId};album=${old.albumId ?: ""};title=${EditorUndoCodec.encodeRows(listOf(listOf(old.title)))}"
            extra = withRowsExtra(extra, "corrections", correctionStates) ?: extra
            extra = withRowsExtra(extra, "scrobbles", scrobbleStates) ?: extra
            extra = withRowsExtra(extra, "tracks", trackStates) ?: extra
            extra = withRowsExtra(extra, "trackArtistTrackIds", changedArtistLinkTrackIds.map { listOf(it.toString()) }) ?: extra
            extra = withRowsExtra(extra, "trackArtistLinks", trackArtistRows) ?: extra
            extra = withRowsExtra(extra, "trackAlbumTrackIds", changedAlbumLinkTrackIds.map { listOf(it.toString()) }) ?: extra
            extra = withRowsExtra(extra, "trackAlbumLinks", trackAlbumRows) ?: extra
            extra = withRowsExtra(extra, "newTrackIds", newTrackIds.map { listOf(it.toString()) }) ?: extra
            extra = withRowsExtra(extra, "newAlbumIds", newAlbumIds.map { listOf(it.toString()) }) ?: extra
            extra = withRowsExtra(extra, "newArtistIds", newArtistIds.map { listOf(it.toString()) }) ?: extra
            log(
                Type.REVIEW_FIX, "TRACK", old.trackId,
                before = "${old.title} — $oldArtistName${oldAlbumTitle?.let { " · $it" } ?: ""}",
                after = "$title — $artists${album?.let { " · $it" } ?: ""}",
                extra = extra
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
    suspend fun undo(): String = operationMutex.withLock {
        _busy.value = true
        try {
            val last = db.editorDao().last() ?: return@withLock "Rien à annuler"
            val msg = when (last.type) {
                Type.RENAME -> {
                    val extra = parseExtraData(last.extraData)
                    db.withTransaction {
                        when (last.entityType) {
                            "ARTIST" -> db.artistDao().rename(last.entityId, last.before ?: "")
                            "ALBUM" -> db.albumDao().rename(last.entityId, last.before ?: "")
                            else -> db.trackDao().rename(last.entityId, last.before ?: "")
                        }
                        restoreCorrectionSnapshots(EditorUndoCodec.decodeRows(extra["corrections"]))
                    }
                    "↩️ Renommage annulé (« ${last.before} »)"
                }
                Type.COVER_CHANGE -> {
                    val extra = parseExtraData(last.extraData)
                    val hasPreviousSource = extra.containsKey("previousSource")
                    val previousSource = EditorUndoCodec.decodeRows(extra["previousSource"]).firstOrNull()?.firstOrNull()
                    if (last.entityType == "ARTIST") {
                        if (hasPreviousSource) db.artistDao().restorePhoto(last.entityId, last.before, previousSource)
                        else db.artistDao().setPhoto(last.entityId, last.before)
                    } else {
                        db.withTransaction {
                            if (hasPreviousSource) db.albumDao().restoreCover(last.entityId, last.before, previousSource)
                            else db.albumDao().setCover(last.entityId, last.before)
                            val applied = EditorUndoCodec.decodeRows(extra["appliedCover"]).firstOrNull()?.firstOrNull()
                            if (extra.containsKey("appliedCover")) {
                                EditorUndoCodec.decodeRows(extra["trackCovers"]).forEach { row ->
                                    val trackId = row.getOrNull(0)?.toLongOrNull() ?: return@forEach
                                    db.albumDao().restoreUserAlbumTrackCover(
                                        trackId, row.getOrNull(1), row.getOrNull(2), applied
                                    )
                                }
                            }
                        }
                    }
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
                Type.ARTIST_CHANGE -> {
                    val oldArtist = last.before?.toLongOrNull()
                    if (oldArtist == null || db.trackDao().getById(last.entityId) == null) "⚠️ Artiste non restaurable"
                    else {
                        val links = EditorUndoCodec.decodeRows(parseExtraData(last.extraData)["artistLinks"])
                        db.withTransaction {
                            db.trackDao().setPrimaryArtist(last.entityId, oldArtist)
                            db.scrobbleDao().setArtistForTrack(last.entityId, oldArtist)
                            db.trackLinkDao().clearTrackArtists(last.entityId)
                            var hasPrimary = false
                            links.forEach { row ->
                                val artistId = row.getOrNull(0)?.toLongOrNull() ?: return@forEach
                                val primary = artistId == oldArtist
                                db.trackLinkDao().insertTrackArtist(
                                    TrackArtistEntity(
                                        trackId = last.entityId, artistId = artistId, isPrimary = primary,
                                        role = if (primary) "main" else row.getOrNull(2) ?: "featured"
                                    )
                                )
                                if (artistId == oldArtist) hasPrimary = true
                            }
                            if (!hasPrimary) db.trackLinkDao().insertTrackArtist(TrackArtistEntity(trackId = last.entityId, artistId = oldArtist, isPrimary = true))
                            restoreCorrectionSnapshots(EditorUndoCodec.decodeRows(parseExtraData(last.extraData)["corrections"]))
                        }
                        library?.clearCaches()
                        rebuilder.rebuildAll(fullBillboard = true)
                        "↩️ Artiste restauré"
                    }
                }
                Type.ALBUM_SHARED -> {
                    val snapshots = EditorUndoCodec.decodeRows(parseExtraData(last.extraData)["corrections"])
                    if (snapshots.isNotEmpty()) {
                        db.withTransaction {
                            restoreCorrectionSnapshots(snapshots)
                            library?.clearCaches()
                            AlbumSharing.consolidate(db, library ?: LibraryRepository(db))
                        }
                        "↩️ Marquage multi-artistes ${if (last.after == "multi-artistes") "retiré" else "rétabli"}"
                    } else {
                        // Compatibilité avec les anciennes entrées d'historique qui ne contiennent pas d'instantané.
                        val title = db.editorDao().correctionsOfType(LibraryRepository.CORRECTION_ALBUM_SHARED).firstOrNull {
                            it.correctedValue == (if (last.after == "multi-artistes") "1" else "0") && last.createdAt - it.createdAt in 0L until 5 * 60_000L
                        }?.originalValue
                        if (title == null) "⚠️ Marquage non annulable" else {
                            saveCorrection(title, if (last.after == "multi-artistes") "0" else "1", LibraryRepository.CORRECTION_ALBUM_SHARED)
                            library?.clearCaches()
                            AlbumSharing.consolidate(db, library ?: LibraryRepository(db))
                            "↩️ Marquage multi-artistes ${if (last.after == "multi-artistes") "retiré" else "rétabli"}"
                        }
                    }
                }
                Type.ALBUM_CHANGE -> {
                    val oldAlbum = last.before?.toLongOrNull()
                    val currentTrack = db.trackDao().getById(last.entityId)
                    if (currentTrack == null) "⚠️ Album non restaurable"
                    else {
                        db.withTransaction {
                            db.trackDao().setAlbum(last.entityId, oldAlbum)
                            currentTrack.albumId?.let { db.trackLinkDao().unlinkTrackAlbum(last.entityId, it) }
                            oldAlbum?.let { db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = last.entityId, albumId = it)) }
                            db.scrobbleDao().setAlbumForTrack(last.entityId, oldAlbum)
                            restoreCorrectionSnapshots(EditorUndoCodec.decodeRows(parseExtraData(last.extraData)["corrections"]))
                        }
                        library?.clearCaches()
                        rebuilder.rebuildAll(fullBillboard = true)
                        "↩️ Album restauré"
                    }
                }
                Type.REVIEWED, Type.REVIEW_IGNORE -> "ℹ️ Marquage « correct / ignoré » conservé"
                Type.REVIEW_FIX -> {
                    val kv = parseExtraData(last.extraData)
                    val ids = kv["ids"]?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
                    val oldArtist = kv["artist"]?.toLongOrNull()
                    val oldAlbum = kv["album"]?.toLongOrNull()
                    val oldTitle = EditorUndoCodec.decodeRows(kv["title"]).firstOrNull()?.firstOrNull() ?: kv["title"]
                    val scrobbleStates = EditorUndoCodec.decodeRows(kv["scrobbles"])
                    val trackStates = EditorUndoCodec.decodeRows(kv["tracks"])
                    val artistLinkTrackIds = EditorUndoCodec.decodeRows(kv["trackArtistTrackIds"]).mapNotNull { it.firstOrNull()?.toLongOrNull() }
                    val artistLinkRows = EditorUndoCodec.decodeRows(kv["trackArtistLinks"])
                    val albumLinkTrackIds = EditorUndoCodec.decodeRows(kv["trackAlbumTrackIds"]).mapNotNull { it.firstOrNull()?.toLongOrNull() }
                    val albumLinkRows = EditorUndoCodec.decodeRows(kv["trackAlbumLinks"])
                    val newTrackIds = EditorUndoCodec.decodeRows(kv["newTrackIds"]).mapNotNull { it.firstOrNull()?.toLongOrNull() }
                    val newAlbumIds = EditorUndoCodec.decodeRows(kv["newAlbumIds"]).mapNotNull { it.firstOrNull()?.toLongOrNull() }
                    val newArtistIds = EditorUndoCodec.decodeRows(kv["newArtistIds"]).mapNotNull { it.firstOrNull()?.toLongOrNull() }
                    val oldTrack = db.trackDao().getById(last.entityId)
                    if (ids.isEmpty() || oldTrack == null || (scrobbleStates.isEmpty() && oldArtist == null)) "⚠️ Correction non annulable (titre d'origine disparu)"
                    else {
                        db.withTransaction {
                            if (trackStates.isNotEmpty()) {
                                trackStates.forEach { row ->
                                    val trackId = row.getOrNull(0)?.toLongOrNull() ?: return@forEach
                                    val title = row.getOrNull(1) ?: return@forEach
                                    val current = db.trackDao().getById(trackId)
                                    val titleRaw = row.getOrNull(9) ?: title
                                    val artistId = row.getOrNull(10)?.toLongOrNull() ?: current?.artistId ?: oldTrack.artistId
                                    val confidence = row.getOrNull(5)?.toIntOrNull() ?: 100
                                    val needsReview = row.getOrNull(6)?.toBooleanStrictOrNull() ?: false
                                    val isRemix = row.getOrNull(11)?.toBooleanStrictOrNull() ?: current?.isRemix ?: false
                                    val originalTrackId = if (row.size > 12) row.getOrNull(12)?.toLongOrNull() else current?.originalTrackId
                                    db.trackDao().restoreEditorState(
                                        trackId, title, titleRaw, artistId, row.getOrNull(2)?.toLongOrNull(), row.getOrNull(3), row.getOrNull(4), confidence, needsReview,
                                        row.getOrNull(7)?.toLongOrNull(), row.getOrNull(8), isRemix, originalTrackId
                                    )
                                }
                            } else if (kv["mode"] == "INPLACE") {
                                db.trackDao().setTitleAndAlbum(oldTrack.trackId, oldTitle ?: oldTrack.title, oldAlbum)
                            }

                            artistLinkTrackIds.forEach { db.trackLinkDao().clearTrackArtists(it) }
                            artistLinkRows.forEach { row ->
                                val trackId = row.getOrNull(0)?.toLongOrNull() ?: return@forEach
                                val artistId = row.getOrNull(1)?.toLongOrNull() ?: return@forEach
                                val primary = row.getOrNull(2)?.toBooleanStrictOrNull() ?: false
                                val role = row.getOrNull(3) ?: if (primary) "main" else "featured"
                                db.trackLinkDao().insertTrackArtist(TrackArtistEntity(trackId = trackId, artistId = artistId, isPrimary = primary, role = role))
                            }
                            albumLinkTrackIds.forEach { db.trackLinkDao().clearTrackAlbums(it) }
                            albumLinkRows.forEach { row ->
                                val trackId = row.getOrNull(0)?.toLongOrNull() ?: return@forEach
                                val albumId = row.getOrNull(1)?.toLongOrNull() ?: return@forEach
                                val trackNumber = row.getOrNull(2)?.toIntOrNull()
                                val primary = row.getOrNull(3)?.toBooleanStrictOrNull() ?: true
                                db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = trackId, albumId = albumId, trackNumber = trackNumber, isPrimary = primary))
                            }

                            if (scrobbleStates.isNotEmpty()) {
                                scrobbleStates.forEach { row ->
                                    val scrobbleId = row.getOrNull(0)?.toLongOrNull() ?: return@forEach
                                    val trackId = row.getOrNull(1)?.toLongOrNull() ?: return@forEach
                                    val artistId = row.getOrNull(2)?.toLongOrNull() ?: return@forEach
                                    val confidence = row.getOrNull(4)?.toIntOrNull() ?: 100
                                    val needsReview = row.getOrNull(5)?.toBooleanStrictOrNull() ?: false
                                    db.scrobbleDao().restoreReviewFixState(
                                        scrobbleId, trackId, artistId, row.getOrNull(3)?.toLongOrNull(), confidence, needsReview, row.getOrNull(6)
                                    )
                                }
                            } else {
                                db.scrobbleDao().moveScrobbles(ids, oldTrack.trackId, oldArtist!!, oldAlbum)
                                db.scrobbleDao().reflag(ids, "Correction annulée")
                            }
                            val pendingTracks = newTrackIds.toMutableSet()
                            while (pendingTracks.isNotEmpty()) {
                                var deletedAny = false
                                for (trackId in pendingTracks.toList().asReversed()) {
                                    if (db.trackDao().deleteIfUnused(trackId) > 0) {
                                        pendingTracks.remove(trackId)
                                        deletedAny = true
                                    }
                                }
                                if (!deletedAny) break // Une entité devenue utilisée depuis la correction est conservée.
                            }
                            newAlbumIds.forEach { db.albumDao().deleteIfUnused(it) }
                            newArtistIds.forEach { db.artistDao().deleteIfUnused(it) }
                            restoreCorrectionSnapshots(EditorUndoCodec.decodeRows(kv["corrections"]))
                        }
                        library?.clearCaches()
                        rebuilder.rebuildAll(fullBillboard = true)
                        "↩️ Correction annulée — ${ids.size} écoute(s) restaurée(s)"
                    }
                }
                else -> "⚠️ La suppression d'une écoute est irréversible"
            }
            library?.clearCaches()
            db.editorDao().deleteEdit(last.id)
            _status.value = msg
            msg
        } finally {
            _busy.value = false
        }
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
