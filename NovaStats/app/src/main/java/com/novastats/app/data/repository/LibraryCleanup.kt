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
 *     sur le même album) → fusion avec le vrai titre ;
 *  4. crédit fantôme vérifié contre les crédits officiels : Lil Nas X n'est sur aucune chanson de
 *     KPop Demon Hunters (tracklist Republic Records) → retiré des 3 fiches qui le portaient ;
 *  5. anti-récidive : les détections abandonnées (annulées depuis 24 h, ou en attente depuis 14 j)
 *     sont effacées, puis les fiches artistes/albums qui ne référencent plus rien sont purgées.
 *     Les vraies voix des Saja Boys sont préservées (liste blanche).
 *  6. doublons vérifiés : la même œuvre en deux fiches jamais rattachées (« Messy (From F1 The Movie) » /
 *     « Messy », crédits d'artistes variables d'une source à l'autre…) → les 22 paires connues sont
 *     rattachées à leur racine en un lot ([DataEditorManager.linkVersionsBatch]), et le doublon d'artiste
 *     « The Black Eyed Peas » est fusionné avec « Black Eyed Peas ».
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
                    // Le vrai titre peut être crédité « Artiste, Invité… » : l'artiste de la fiche poubelle doit s'y retrouver
                    (db.artistDao().getById(o.artistId)?.name?.lowercase()?.contains(ownArtist) == true)
            } ?: continue
            runCatching { editor.mergeTracks(t.trackId, target.trackId) }
        }

        // ---- 4. Crédit fantôme « Lil Nas X » sur KPop Demon Hunters (absent des crédits officiels) ----
        val lnx = db.artistDao().findByNameNoCase("Lil Nas X")
        if (lnx != null) {
            val hits = db.trackDao().allPlayed().filter { t ->
                if (t.artistId == lnx.artistId) return@filter false
                val k = t.title.trim().lowercase()
                val cible = k == "what it sounds like" || k == "dévoile" || k.startsWith("briller")
                cible && db.trackLinkDao().artistIdsForTrack(t.trackId).contains(lnx.artistId)
            }
            if (hits.isNotEmpty()) runCatching {
                editor.removeArtistCredits(hits.map { it.trackId to lnx.artistId }, "Lil Nas X")
            }
        }

        // ---- 5. Anti-récidive : détections abandonnées + fiches artistes/albums vides ----
        runCatching {
            editor.purgeEmptyShells(
                keepArtists = listOf(quarantine, "Danny Chung", "Kevin Woo", "Neckwav", "samUIL Lee")
            )
        }

        // ---- 6. Doublons vérifiés (même œuvre, deux fiches) → rattachés à leur racine, en un lot ----
        val duplicatePairs = listOf(
            932L to 115L,    // ROSÉ — « Messy » ← « Messy (From F1 The Movie) »
            1218L to 903L,   // Rihanna — « Lift Me Up » ← « Lift Me Up (From Black Panther…) »
            102L to 930L,    // LISA — « Moonlit Floor » ← « Moonlit Floor (Kiss Me) »
            1117L to 1020L,  // Beyoncé — « Upgrade U (Album Version) » ← « Upgrade U »
            1162L to 1154L,  // Lil Nas X — « Old Town Road » 3ᵉ fiche ← racine liée
            1163L to 1157L,  // Lil Nas X — « INDUSTRY BABY » 3ᵉ fiche ← racine liée
            1300L to 968L,   // Ariana Grande — « yes, and » ← « yes, and? »
            1228L to 508L,   // Taylor Swift — « Enchanted » ← « Enchanted (Taylor's Version) »
            805L to 1229L,   // Taylor Swift — « Fearless (Taylor's Version) » ← « Fearless »
            500L to 910L,    // Taylor Swift — « Red (Taylor's Version) » ← « Red »
            479L to 501L,    // Taylor Swift — « State Of Grace » ← « State Of Grace (Taylor's Version) »
            655L to 482L,    // Taylor Swift — « Welcome To New York (Taylor's Version) » ← « Welcome To New York »
            503L to 639L,    // Taylor Swift — « Actually Romantic (Track by Track) » ← « Actually Romantic »
            1294L to 795L,   // XLOV — « EXTANCY » ← « EXTANCY (Wumuti&Rui) »
            635L to 457L,    // Lady Gaga — « Joanne (Where Do You Think You're Goin'?) » ← « Joanne »
            973L to 1069L,   // One Kiss — crédits inversés [Calvin Harris, Dua Lipa] ← [Dua Lipa, Calvin Harris]
            1216L to 254L,   // Rihanna — « Friend Of Mine » ← « Friend Of Mine - from the Smurfs Movie Soundtrack »
            231L to 1068L,   // « Prisoner » [Miley seule] ← [Dua Lipa, Miley Cyrus] (1175 suit via repointVersions)
            1124L to 1043L,  // « GIRL LIKE ME » [The Black Eyed Peas] ← [Black Eyed Peas, Shakira] (897 suit)
            412L to 1014L,   // « Bang Bang » [Ariana seule] ← [Jessie J, Nicki Minaj, Ariana Grande]
            527L to 1103L,   // « Taki Taki » [Cardi B] ← [DJ Snake, Cardi B, Ozuna, Selena Gomez] (racine = crédit complet)
            1266L to 797L    // XLOV — « supernova » ← « supernova (Cover) »
        )
        val toLink = duplicatePairs.filter { (versionId, rootId) ->
            val version = db.trackDao().getById(versionId)
            val root = db.trackDao().getById(rootId)
            version != null && root != null && version.originalTrackId == null && root.originalTrackId == null
        }
        if (toLink.isNotEmpty()) runCatching { editor.linkVersionsBatch(toLink) }

        // « The Black Eyed Peas » = « Black Eyed Peas » : même groupe, crédit variable selon les sources.
        val theBep = db.artistDao().findByNameNoCase("The Black Eyed Peas")
        val bep = db.artistDao().findByNameNoCase("Black Eyed Peas")
        if (theBep != null && bep != null && theBep.artistId != bep.artistId) runCatching {
            editor.mergeArtists(theBep.artistId, bep.artistId)
        }
    }
}
