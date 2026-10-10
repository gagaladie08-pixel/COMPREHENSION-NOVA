package com.novastats.app.data.repository

import android.util.Log
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.entity.ArtistEntity
import com.novastats.app.domain.TitleNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 🧹 Nettoyage automatique de l'existant (règle 3) — lancé une fois par process au démarrage.
 *
 * Répare les pollutions déjà présentes dans la bibliothèque :
 *  1. artistes avec suffixe d'interface (« X • Recommandé Pour Vous ») → fusion avec le vrai X, ou renommage ;
 *  2. titres portant l'empreinte d'un fichier/émission + artistes sans nom (« <unknown> », « Unknown », vide)
 *     → déplacés vers l'artiste de quarantaine « 🗂️ À trier », doublons fusionnés ;
 *  3. crédits de featuring pris comme titre (« Cardi B, Bad Bunny J Balvin » alors que le vrai titre existe
 *     sur le même album) → fusion avec le vrai titre.
 *
 * Aucune écoute n'est supprimée : on renomme, fusionne et déplace uniquement, via [DataEditorManager]
 * (chaque geste est tracé dans l'historique d'édition). Idempotent : un second passage ne trouve plus rien.
 */
object LibraryCleanup {

    private const val TAG = "LibraryCleanup"

    @Volatile
    private var attempted = false
    private val mutex = Mutex()

    /** À appeler sur le flux de démarrage (après StartupRepair). Silencieux : les échecs ne bloquent pas l'app. */
    suspend fun run(app: NovaStatsApp) {
        withContext(Dispatchers.IO) {
            if (attempted) return@withContext
            attempted = true
            mutex.withLock {
                runCatching { cleanup(app) }
                    .onFailure { Log.w(TAG, "nettoyage ignoré", it) }
            }
        }
    }

    private suspend fun cleanup(app: NovaStatsApp) {
        val db = app.database
        val editor = app.editor
        val quarantine = LibraryRepository.QUARANTINE_ARTIST

        // ---- 1. Artistes avec suffixe d'interface → vrai nom (fusion si le vrai artiste existe) ----
        val renames = mutableListOf<Pair<Long, String>>()
        val merges = mutableListOf<Pair<Long, Long>>()
        for (a in db.artistDao().allPlayedList()) {
            val clean = TitleNormalizer.cleanArtistName(a.name)
            if (clean == a.name || clean.isBlank()) continue
            val target = db.artistDao().findByNameNoCase(clean)?.takeIf { it.artistId != a.artistId }
            if (target != null) merges += a.artistId to target.artistId else renames += a.artistId to clean
        }

        // ---- 2. Artistes sans nom → quarantaine ; titres « dump » isolés → quarantaine ----
        var qId = db.artistDao().findByNameNoCase(quarantine)?.artistId
        val nameless = db.artistDao().allPlayedList().filter {
            val n = it.name.trim()
            n.isEmpty() || n.equals("<unknown>", true) || n.equals("unknown", true)
        }
        val dumpedTracks = db.trackDao().allPlayed().filter { t ->
            TitleNormalizer.isFileDump(t.titleRaw.ifBlank { t.title }) || TitleNormalizer.isFileDump(t.title)
        }
        val needsQuarantine = nameless.isNotEmpty() || dumpedTracks.isNotEmpty()
        if (needsQuarantine && qId == null) {
            qId = db.artistDao().insert(ArtistEntity(name = quarantine, nameRaw = quarantine))
        }

        if (merges.isNotEmpty()) editor.mergeArtistsBatch(merges)
        for ((id, name) in renames) runCatching { editor.renameArtist(id, name) }

        if (needsQuarantine && qId != null) {
            // Artistes sans nom : fusion complète dans la quarantaine (aucune écoute perdue)
            val namelessPairs = nameless.map { it.artistId to qId!! }.filter { (from, into) -> from != into }
            if (namelessPairs.isNotEmpty()) editor.mergeArtistsBatch(namelessPairs)
            // Titres « dump » rattachés à de vrais artistes : déplacement un par un
            for (t in dumpedTracks) {
                if (t.artistId != qId) runCatching { editor.setTrackArtist(t.trackId, qId!!) }
            }
            // Doublons créés sous la quarantaine (même titre arrivé via deux artistes) → fusion
            val underQ = db.trackDao().allPlayed().filter { it.artistId == qId }
                .groupBy { TitleNormalizer.normalizeKey(it.title) }
            for ((_, group) in underQ) {
                val keep = group.minByOrNull { it.trackId } ?: continue
                for (dup in group.filter { it.trackId != keep.trackId }) {
                    runCatching { editor.mergeTracks(dup.trackId, keep.trackId) }
                }
            }
        }

        // ---- 3. Crédits de featuring pris comme titre → fusion avec le vrai titre du même album ----
        val played = db.trackDao().allPlayed()
        val creditPattern = Regex("^[\\p{L}\\p{N} .''&-]+(, ?[\\p{L}\\p{N} .''&-]+)+$")
        for (t in played) {
            val title = t.title.trim()
            if (!creditPattern.matches(title)) continue
            // Le « titre » commence par le nom de son propre artiste : c'est une ligne de crédits
            val ownArtist = db.artistDao().getById(t.artistId)?.name?.lowercase() ?: continue
            if (!title.lowercase().startsWith(ownArtist)) continue
            val target = played.firstOrNull { o ->
                o.trackId != t.trackId && o.albumId == t.albumId && o.albumId != null &&
                    !creditPattern.matches(o.title.trim()) &&
                    db.trackDao().getById(o.trackId)?.artistId == t.artistId
            } ?: continue
            runCatching { editor.mergeTracks(t.trackId, target.trackId) }
        }
    }
}
