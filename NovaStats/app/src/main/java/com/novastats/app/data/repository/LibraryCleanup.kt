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
 *     rattachées à leur racine en un lot ([DataEditorManager.linkVersionsBatch]), et les 4 doublons
 *     d'artistes (« The Black Eyed Peas », « RM », « Brendon Urie of Panic! At The Disco », « The Weekend »)
 *     sont fusionnés ([DataEditorManager.mergeArtistsBatch]), et les coquilles d'albums en double
 *     (MONTERO ×2, Set the World On Fire) rejoignent leur fiche propriétaire ([DataEditorManager.mergeAlbumsBatch]).
 *  7. « cas Upgrade U » : chansons dont l'enregistrement officiel est le duo (aucune version solo) et que des
 *     scrobbles incohérents avaient dédoublonnées → fusion complète en une seule carte ([DataEditorManager.mergeTracksBatch]).
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
            1266L to 797L,   // XLOV — « supernova » ← « supernova (Cover) »
            1301L to 1025L   // « Video Phone (Extended » (tag tronqué, le vrai remix 5:04) ← famille Video phone
        )
        val toLink = duplicatePairs.filter { (versionId, rootId) ->
            val version = db.trackDao().getById(versionId)
            val root = db.trackDao().getById(rootId)
            version != null && root != null && version.originalTrackId == null && root.originalTrackId == null
        }
        if (toLink.isNotEmpty()) runCatching { editor.linkVersionsBatch(toLink) }

        // ---- 6b. Doublons d'artistes vérifiés : même personne/groupe, crédit variable selon les sources ----
        val artistMerges = listOf(
            "The Black Eyed Peas" to "Black Eyed Peas",
            "RM" to "RM of BTS",
            "Brendon Urie of Panic! At The Disco" to "Brendon Urie",
            "The Weekend" to "The Weeknd"
        )
        val artistMergePairs = mutableListOf<Pair<Long, Long>>()
        for ((fromName, intoName) in artistMerges) {
            val from = db.artistDao().findByNameNoCase(fromName)
            val into = db.artistDao().findByNameNoCase(intoName)
            if (from != null && into != null && from.artistId != into.artistId) artistMergePairs += from.artistId to into.artistId
        }
        if (artistMergePairs.isNotEmpty()) runCatching { editor.mergeArtistsBatch(artistMergePairs) }

        // ---- 6c. Doublons d'albums : coquilles sans artiste créées par d'anciens imports → fiche propriétaire ----
        val albumMerges = listOf(
            410L to 267L,  // MONTERO (coquille) ← MONTERO [Lil Nas X]
            413L to 267L,  // MONTERO (coquille) ← idem
            401L to 108L   // Set the World On Fire (coquille) ← [Lil Nas X]
        )
        val validAlbumMerges = albumMerges.filter { (from, into) ->
            from != into && db.albumDao().getById(from) != null && db.albumDao().getById(into) != null
        }
        if (validAlbumMerges.isNotEmpty()) runCatching { editor.mergeAlbumsBatch(validAlbumMerges) }

        // ---- 7. « Cas Upgrade U » : l'enregistrement officiel EST le duo (pas de version solo existante).
        // Les fiches « Original » / « with X » pointent vers le même fichier : fusion complète en une carte
        // (mergeTracks), la survivante gardant le crédit complet. La correction de titre mémorisée empêche
        // le doublon de renaître au prochain scrobble. Sont exclus les titres ayant une vraie version solo
        // (Video Phone, BREAK MY SOUL, Karma, Say So…) : leurs variantes restent des versions.
        val sameRecordingMerges = listOf(
            809L to 1164L,   // Let Me Love You ← (with Lil Wayne)
            1288L to 1036L,  // SPAGHETTI ← (with J-hope)
            47L to 1139L,    // Stateside ← (with Zara Larsson)
            1009L to 398L,   // lovely ← (with Khalid)
            1115L to 849L,   // Bring Your Love ← (with Sabrina Carpenter)
            412L to 1014L,   // Bang Bang [Ariana] ← trio officiel
            527L to 1103L,   // Taki Taki [Cardi B] ← quatuor officiel
            231L to 1068L,   // Prisoner [Miley] ← [Dua Lipa, Miley Cyrus]
            1175L to 1068L,  // Prisoner (with Dua Lipa) ← idem
            973L to 1069L,   // One Kiss crédits inversés ← crédits complets
            1003L to 336L,   // Bed ← (with Ariana Grande)
            898L to 1137L,   // When I'm With You ← (with Tyla)
            100L to 1141L,   // Born Again ← (with Doja Cat, RAYE)
            445L to 1188L,   // ALREADY ← (with Major Lazer, Shatta Wale)
            65L to 1140L,    // Work ← (with Drake)
            1277L to 1273L,  // ENERGY ← (with Beam)
            109L to 1197L,   // Love Hangover ← (with Dominic Fike)
            1283L to 1196L,  // ExtraL ← (with Doechii)
            98L to 1136L,    // New Woman ← (with ROSALÍA)
            259L to 1146L,   // Talk That Talk ← (with JAY-Z)
            145L to 1150L,   // Killah ← (with Gesaffelstein)
            1122L to 894L,   // Bizarre ← (with Martin Garrix)
            97L to 1135L,    // APT. ← (with Bruno Mars)
            480L to 1151L,   // Everything Has Changed ← (with Ed Sheeran)
            1005L to 340L,   // Get On Your Knees ← (with Ariana Grande)
            1085L to 734L,   // Never Forget You ← (with MNEK)
            416L to 1143L,   // Don't Call Me Angel ← trio officiel
            1024L to 444L,   // MY POWER ← (with DJ LAG, Yemi Alade…)
            1019L to 434L,   // PROTECTOR ← (with Rumi Carter)
            897L to 1043L,   // GIRL LIKE ME (with Shakira) ← GIRL LIKE ME
            1117L to 1020L,  // Upgrade U (Album Version) ← Upgrade U
            1296L to 1237L,  // Golden ← Golden (with EJAE, AUDREY NUNA, REI AMI…)
            1297L to 1239L   // Le Son De Nos Voix ← Le Son De Nos Voix (with … Cast)
        )
        val validMerges = sameRecordingMerges.filter { (from, into) ->
            from != into && db.trackDao().getById(from) != null && db.trackDao().getById(into) != null
        }
        if (validMerges.isNotEmpty()) runCatching { editor.mergeTracksBatch(validMerges) }
    }
}
