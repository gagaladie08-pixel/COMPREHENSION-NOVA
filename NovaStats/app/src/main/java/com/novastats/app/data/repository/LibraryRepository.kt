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
    /** Dernière résolution par (titre, artiste principal) — pour [peekTrackId] (lecture en cours). */
    private val peekCache = HashMap<String, Resolved>()

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

    /**
     * Décision pure (testable) : la nouvelle écoute a-t-elle le même jeu d'invités qu'un titre existant ?
     *  SAME     → même titre.
     *  SUPERSET → la nouvelle écoute a des invités EN PLUS → version « Titre (with X) » liée au titre existant (root).
     *  SUBSET   → la nouvelle écoute a MOINS d'invités : c'est l'original ; le titre existant devient une version liée.
     *  OTHER    → jeux différents (A+B vs A+C) → version liée au root existant.
     */
    enum class GuestMatch { SAME, SUPERSET, SUBSET, OTHER }


    private suspend fun guestKeysOf(trackId: Long, primaryArtistId: Long): Set<String> {
        val ids = db.trackLinkDao().artistIdsForTrack(trackId).ifEmpty { listOf(primaryArtistId) }
        return ids.filter { it != primaryArtistId }.map { "#$it" }.toSet()
    }

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
        // Cascade artistes : 1) champ artiste du player (lui-même "A & B, C" — noms protégés jamais découpés)  2) feat. dans le titre
        val playerArtists = TitleNormalizer.splitArtists(rawArtists)
        val artistNames = (playerArtists + normalized.featuredArtists)
            .distinctBy { TitleNormalizer.normalizeKey(it) }
            .ifEmpty { listOf("Artiste inconnu") }
        val primaryName = artistNames.first()
        val guestNames = artistNames.drop(1)
        val playerKeys = playerArtists.map { TitleNormalizer.normalizeKey(it) }.toSet()
        // Invités venus du titre (« (feat. X) ») et absents du champ artiste
        val titleOnlyGuests = normalized.featuredArtists.filter { TitleNormalizer.normalizeKey(it) !in playerKeys }

        // Clé complète = titre + artiste principal + invités (triés) : deux jeux d'invités = deux versions
        val baseKey = TitleNormalizer.normalizeKey(normalized.title) + "|" + TitleNormalizer.normalizeKey(primaryName)
        val fullKey = baseKey + "|" + guestNames.map { TitleNormalizer.normalizeKey(it) }.sorted().joinToString(",")
        trackCache[fullKey]?.let { return it }

        val artistIds = artistNames.map { resolveArtist(it) }
        val primaryArtistId = artistIds.first()
        val guestIds = artistIds.drop(1)
        val guestKeys = guestIds.map { "#$it" }.toSet()
        // Album crédité uniquement s'il s'agit d'un album de l'artiste principal ; jamais pour une compilation
        val albumId = rawAlbum?.takeIf { it.isNotBlank() && !TitleNormalizer.isCompilation(it, albumArtist) }
            ?.let { resolveAlbum(it, primaryArtistId) }

        // ---- 1. Remix / version AVEC artiste featuring (« Song (Remix) feat. Drake ») → titre distinct « Song (feat. Drake) », lié à l'original
        val isRemixFeat = normalized.isVersion && titleOnlyGuests.isNotEmpty()
        if (isRemixFeat) {
            val title = "${normalized.title} (feat. ${titleOnlyGuests.joinToString(", ")})"
            val existing = db.trackDao().findByTitleAndArtist(title, primaryArtistId)
            // Original : même titre nu, crédité à l'un des artistes (l'artiste principal du remix peut différer)
            val root = db.trackDao().findRootByTitleAnyArtist(normalized.title, artistIds)
            val trackId = existing?.trackId ?: db.trackDao().insert(
                TrackEntity(title = title, titleRaw = rawTitle, artistId = primaryArtistId, albumId = albumId, durationMs = durationMs, genre = genre, isRemix = true, originalTrackId = root?.trackId)
            )
            if (existing != null && existing.originalTrackId == null && root != null && root.trackId != trackId) db.trackDao().linkToRoot(trackId, root.trackId)
            if (existing != null && existing.albumId == null && albumId != null) db.trackDao().update(existing.copy(albumId = albumId, durationMs = existing.durationMs ?: durationMs, genre = existing.genre ?: genre))
            return finish(trackId, primaryArtistId, albumId, artistIds, fullKey, baseKey)
        }

        // ---- 2. Titre « normal » : même titre + même artiste principal → même titre, SAUF si le jeu d'invités diffère (versions)
        val plainTitle = normalized.title
        val existingPlain = db.trackDao().findByTitleAndArtist(plainTitle, primaryArtistId)
        if (existingPlain == null) {
            // Première fois qu'on voit ce titre nu pour cet artiste. Existe-t-il déjà une version (remix feat., « (with X) ») orpheline ou liée ?
            val trackId = db.trackDao().insert(
                TrackEntity(title = plainTitle, titleRaw = rawTitle, artistId = primaryArtistId, albumId = albumId, durationMs = durationMs, genre = genre)
            )
            adoptOrphans(trackId, plainTitle, artistIds)
            return finish(trackId, primaryArtistId, albumId, artistIds, fullKey, baseKey)
        }

        val rootId = existingPlain.originalTrackId ?: existingPlain.trackId
        val existingGuests = guestKeysOf(existingPlain.trackId, primaryArtistId)
        val match = classify(existingGuests, guestKeys)
        if (existingPlain.albumId == null && albumId != null) {
            db.trackDao().update(existingPlain.copy(albumId = albumId, durationMs = existingPlain.durationMs ?: durationMs, genre = existingPlain.genre ?: genre))
        }
        val trackId: Long = when (match) {
            GuestMatch.SAME -> existingPlain.trackId
            GuestMatch.SUBSET -> {
                // La nouvelle écoute est l'ORIGINAL (moins d'invités) et le titre nu est déjà pris par la version « avec invité »
                // → on renomme la version, on crée l'original, on corrige le lien.
                val extra = existingGuests - guestKeys
                val extraNames = extra.mapNotNull { k -> db.artistDao().getById(k.drop(1).toLong())?.name }
                val versionTitle = "$plainTitle (with ${extraNames.joinToString(", ")})"
                val newRoot = db.trackDao().insert(
                    TrackEntity(title = plainTitle, titleRaw = rawTitle, artistId = primaryArtistId, albumId = albumId ?: existingPlain.albumId, durationMs = durationMs ?: existingPlain.durationMs, genre = genre ?: existingPlain.genre)
                )
                db.trackDao().setTitle(existingPlain.trackId, versionTitle)
                db.trackDao().linkToRoot(existingPlain.trackId, newRoot)
                // Les autres versions qui pointaient vers l'ancienne « racine » suivent
                db.trackDao().versionsOf(existingPlain.trackId).forEach { v -> db.trackDao().linkToRoot(v.trackId, newRoot) }
                adoptOrphans(newRoot, plainTitle, artistIds)
                trackCache.clear()
                newRoot
            }
            GuestMatch.SUPERSET, GuestMatch.OTHER -> {
                // Version « avec invité(s) » : réutilisée si une version liée au root a exactement ce jeu d'invités, sinon créée
                val versions = db.trackDao().versionsOf(rootId)
                val same = versions.firstOrNull { v -> guestKeysOf(v.trackId, primaryArtistId) == guestKeys }
                if (same != null) same.trackId else {
                    val extra = if (match == GuestMatch.SUPERSET) guestKeys - existingGuests else guestKeys
                    val extraNames = extra.mapNotNull { k -> db.artistDao().getById(k.drop(1).toLong())?.name }
                    val fromTitle = titleOnlyGuests.map { TitleNormalizer.normalizeKey(it) }.toSet()
                    val word = if (extraNames.isNotEmpty() && extraNames.all { TitleNormalizer.normalizeKey(it) in fromTitle }) "feat." else "with"
                    val versionTitle = "$plainTitle ($word ${extraNames.joinToString(", ")})"
                    db.trackDao().findByTitleAndArtist(versionTitle, primaryArtistId)?.trackId ?: db.trackDao().insert(
                        TrackEntity(title = versionTitle, titleRaw = rawTitle, artistId = primaryArtistId, albumId = albumId ?: existingPlain.albumId, durationMs = durationMs, genre = genre, isRemix = true, originalTrackId = rootId)
                    )
                }
            }
        }
        return finish(trackId, primaryArtistId, albumId, artistIds, fullKey, baseKey)
    }

    /** Liens artistes (TOUS les artistes présents, même si le titre était déjà connu) + album, cache, résultat. */
    private suspend fun finish(trackId: Long, primaryArtistId: Long, albumId: Long?, artistIds: List<Long>, fullKey: String, baseKey: String): Resolved {
        artistIds.forEachIndexed { i, id ->
            db.trackLinkDao().insertTrackArtist(TrackArtistEntity(trackId = trackId, artistId = id, isPrimary = i == 0))
        }
        albumId?.let { db.trackLinkDao().insertTrackAlbum(TrackAlbumEntity(trackId = trackId, albumId = it)) }
        return Resolved(trackId, primaryArtistId, albumId, artistIds).also { trackCache[fullKey] = it; peekCache[baseKey] = it }
    }

    /** Versions orphelines « Titre (feat. X) » / « Titre (with X) » d'un des artistes → rattachées au root qui vient d'apparaître. */
    private suspend fun adoptOrphans(rootId: Long, plainTitle: String, artistIds: List<Long>) {
        db.trackDao().orphanVersions(rootId, plainTitle, artistIds).forEach { v ->
            if (v.isRemix || v.title.startsWith("$plainTitle (feat.") || v.title.startsWith("$plainTitle (with")) db.trackDao().linkToRoot(v.trackId, rootId)
        }
    }

    companion object {
        /** Comparaison des jeux d'invités (clé « #artistId » ou toute clé stable). */
        fun classify(existing: Set<String>, incoming: Set<String>): GuestMatch = when {
            existing == incoming -> GuestMatch.SAME
            incoming.containsAll(existing) -> GuestMatch.SUPERSET
            existing.containsAll(incoming) -> GuestMatch.SUBSET
            else -> GuestMatch.OTHER
        }
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
        return peekCache[TitleNormalizer.normalizeKey(normalized.title) + "|" + TitleNormalizer.normalizeKey(primary)]?.trackId
    }

    fun clearCaches() { artistCache.clear(); albumCache.clear(); trackCache.clear(); peekCache.clear(); correctionsLoadedAt = 0L }

    /** Charge les noms protégés depuis la base (au démarrage et après édition) ; insère les valeurs par défaut la première fois. */
    suspend fun loadArtistExceptions(seedDefaults: Boolean) {
        val dao = db.artistExceptionDao()
        if (seedDefaults && dao.count() == 0) {
            TitleNormalizer.DEFAULT_NEVER_SPLIT.forEach { n -> dao.insert(com.novastats.app.data.db.entity.ArtistExceptionEntity(name = n, nameKey = TitleNormalizer.normalizeKey(n))) }
        }
        TitleNormalizer.setNeverSplit(dao.allList().map { it.name })
    }

    /**
     * 🔗 Re-résolution de TOUTES les écoutes à partir des valeurs brutes du lecteur : complète les liens artistes manquants,
     * crée les versions « (with X) », rattache les remix à leur original, applique les noms protégés.
     * Les écoutes sans valeurs brutes (anciennes) gardent leur titre. Retourne le nombre d'écoutes déplacées.
     */
    suspend fun relinkAll(onProgress: (String) -> Unit = {}): RelinkResult {
        clearCaches()
        loadArtistExceptions(seedDefaults = true)
        val rows = db.scrobbleDao().allForRelink()
        var moved = 0
        var duplicates = 0
        val artistName = HashMap<Long, String>()
        rows.forEachIndexed { i, r ->
            if (i % 250 == 0) onProgress("Liens & versions : $i / ${rows.size}")
            val title = r.rawTitle ?: db.trackDao().getById(r.trackId)?.titleRaw ?: return@forEachIndexed
            val artist = r.rawArtist ?: artistName.getOrPut(r.artistId) { db.artistDao().getById(r.artistId)?.name ?: "Artiste inconnu" }
            val resolved = runCatching { resolve(title, artist, r.rawAlbum) }.getOrNull() ?: return@forEachIndexed
            if (resolved.trackId != r.trackId || resolved.primaryArtistId != r.artistId || (resolved.albumId != null && resolved.albumId != r.albumId)) {
                // UNIQUE(track_id, started_at) : si une écoute du titre cible existe déjà au même instant, celle-ci est un doublon → supprimée
                val clash = if (resolved.trackId != r.trackId) db.scrobbleDao().findByTrackAndStart(resolved.trackId, r.startedAt) else null
                if (clash != null && clash.scrobbleId != r.scrobbleId) {
                    db.scrobbleDao().delete(r.scrobbleId); duplicates++
                } else {
                    runCatching { db.scrobbleDao().relink(r.scrobbleId, resolved.trackId, resolved.primaryArtistId, resolved.albumId ?: r.albumId) }
                        .onFailure { e -> if (e is android.database.sqlite.SQLiteConstraintException) { db.scrobbleDao().delete(r.scrobbleId); duplicates++ } else throw e }
                    moved++
                }
            }
        }
        db.trackDao().flattenRoots()
        return RelinkResult(moved, duplicates)
    }

    data class RelinkResult(val moved: Int, val duplicates: Int)
}
