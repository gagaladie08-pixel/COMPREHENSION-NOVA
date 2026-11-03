package com.novastats.app.util

import android.content.Context
import androidx.room.withTransaction
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.service.DetectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 🔎 Audit des données — diagnostic « l'app se vide après une écoute ».
 *
 * Compte les lignes réellement présentes en base AVANT / APRÈS chaque étape destructive du recalcul,
 * et écrit le résultat dans les deux journaux déjà lisibles depuis l'app :
 *  - Réglages → Service & diagnostic → Journal de détection (`DetectionState.log`, persisté) ;
 *  - Réglages → Service & diagnostic → Plantages (`CrashJournal`, survit à la mort du process).
 *
 * Lecture de l'audit :
 *  - `conf` qui BAISSE  → des écoutes sont supprimées (perte sèche, `scrobbles`).
 *  - `conf` stable mais `daily` à 0 → les tables dérivées n'ont pas été reconstruites.
 *  - `orph` qui MONTE  → des titres ont été supprimés alors que leurs écoutes existent encore
 *                         (récupérable : chaque écoute garde raw_title / raw_artist / raw_album).
 *
 * Aucun ordre d'exécution n'est ajouté : les requêtes sont des COUNT préparés une fois,
 * exécutés dans une transaction Room donc jamais sur le thread principal.
 */
object RebuildAudit {

    @Volatile
    private var installed = false

    /** Contexte applicatif, posé par [install] : permet de journaliser depuis les moteurs (sans Context). */
    @Volatile
    var context: Context? = null
        private set

    data class Counts(
        /** Écoutes confirmées (status = CONFIRMED) : la donnée brute. */
        val confirmed: Int,
        /** Toutes les écoutes, y compris PENDING / CANCELLED. */
        val allScrobbles: Int,
        val tracks: Int,
        val artists: Int,
        val albums: Int,
        /** Somme des écoutes agrégées par jour (source des classements par période). */
        val dailyPlays: Int,
        /** Écoutes dont le titre n'existe plus en base. */
        val orphans: Int
    )

    private const val SQL_CONFIRMED = "SELECT COUNT(*) FROM scrobbles WHERE status = 'CONFIRMED'"
    private const val SQL_ALL = "SELECT COUNT(*) FROM scrobbles"
    private const val SQL_TRACKS = "SELECT COUNT(*) FROM tracks"
    private const val SQL_ARTISTS = "SELECT COUNT(*) FROM artists"
    private const val SQL_ALBUMS = "SELECT COUNT(*) FROM albums"
    private const val SQL_DAILY = "SELECT IFNULL(SUM(play_count), 0) FROM daily_plays"
    private const val SQL_ORPHANS =
        "SELECT COUNT(*) FROM scrobbles s WHERE NOT EXISTS (SELECT 1 FROM tracks t WHERE t.track_id = s.track_id)"

    /** Installe un comptage au démarrage du process : on sait combien il y avait d'écoutes en ouvrant l'app. */
    fun install(app: NovaStatsApp) {
        if (installed) return
        installed = true
        context = app.applicationContext
        app.launchIoTask {
            runCatchingCancellable { log(app, app.database, "🔎 État au lancement") }
        }
    }

    /**
     * Comptage courant. Appelé dans une transaction Room (suspend), donc hors thread principal.
     * Dans une transaction ouverte, SQLite lit l'état de LA connexion : on ne voit jamais l'état
     * intermédiaire d'une autre transaction, ce qui évite les faux « tout est vide ».
     */
    suspend fun snapshot(db: NovaDatabase): Counts = withContext(Dispatchers.IO) {
        db.withTransaction {
            val w = db.openHelper.writableDatabase
            Counts(
                confirmed = scalar(w, SQL_CONFIRMED),
                allScrobbles = scalar(w, SQL_ALL),
                tracks = scalar(w, SQL_TRACKS),
                artists = scalar(w, SQL_ARTISTS),
                albums = scalar(w, SQL_ALBUMS),
                dailyPlays = scalar(w, SQL_DAILY),
                orphans = scalar(w, SQL_ORPHANS)
            )
        }
    }

    /** Étape du recalcul : on journalise AVANT de l'exécuter, pour savoir où le process s'est arrêté. */
    suspend fun step(context: Context, db: NovaDatabase, label: String) =
        log(context, db, label)

    /** Comptage simple, sans libellé d'étape. Le comptage lui-même est protégé : le diagnostic ne casse rien. */
    suspend fun log(context: Context, db: NovaDatabase, label: String) {
        runCatchingCancellable {
            val counts = snapshot(db)
            write(context, "$label — ${format(counts)}")
        }
    }

    fun format(c: Counts) =
        "écoutes=${c.confirmed}/${c.allScrobbles} · titres=${c.tracks} · artistes=${c.artists}" +
            " · albums=${c.albums} · daily=${c.dailyPlays} · orphelines=${c.orphans}"

    /** Écrit dans les deux journaux ; n'échoue jamais (le diagnostic ne doit pas casser le recalcul). */
    fun write(context: Context, line: String) {
        runCatching { DetectionState.log(line) }
        runCatching {
            CrashJournal.note(
                context, "🔎 AUDIT",
                RuntimeException("${System.currentTimeMillis()} · $line")
            )
        }
    }

    /** Comparaison avant / après une opération : ne journalise que s'il y a une variation à signaler. */
    fun diff(before: Counts, after: Counts): String? {
        val parts = mutableListOf<String>()
        if (after.confirmed != before.confirmed) parts += "écoutes confirmées ${before.confirmed} → ${after.confirmed}"
        if (after.allScrobbles != before.allScrobbles) parts += "écoutes totales ${before.allScrobbles} → ${after.allScrobbles}"
        if (after.tracks != before.tracks) parts += "titres ${before.tracks} → ${after.tracks}"
        if (after.artists != before.artists) parts += "artistes ${before.artists} → ${after.artists}"
        if (after.albums != before.albums) parts += "albums ${before.albums} → ${after.albums}"
        if (after.orphans != before.orphans) parts += "écoutes orphelines ${before.orphans} → ${after.orphans}"
        if (after.dailyPlays != before.dailyPlays) parts += "écoutes agrégées (daily) ${before.dailyPlays} → ${after.dailyPlays}"
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    // SupportSQLiteStatement n'est pas Closeable : close() explicite dans un finally.
    private fun scalar(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Int {
        val statement = db.compileStatement(sql)
        return try {
            statement.simpleQueryForLong().toInt()
        } finally {
            runCatching { statement.close() }
        }
    }
}
