package com.novastats.app.data.repository

import android.content.Context
import com.novastats.app.NovaStatsApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 🔗 Recalcul des liens artistes & versions (règles 10 / 11 + noms protégés) :
 * re-résout chaque écoute depuis les valeurs brutes du lecteur, puis recalcule toutes les stats.
 * Lancé automatiquement UNE fois après la mise à jour (0.11.0), puis à la demande (Réglages → Données).
 * [state] non nul = en cours (message de progression affiché par NovaApp dans une boîte bloquante).
 */
object RelinkJob {
    private const val PREFS = "nova_relink"
    /** v4 (0.11.2) : albums partagés (règle 12) + albums coupés par les duos (règle 13) — relance le recalcul une fois de plus après la mise à jour. */
    private const val KEY_DONE = "v4_done"
    private val mutex = Mutex()

    private val _state = MutableStateFlow<String?>(null)
    val state: StateFlow<String?> = _state
    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult

    fun isDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    private fun markDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()

    /** Au démarrage : charge les noms protégés ; lance le recalcul si jamais fait et s'il y a des écoutes. */
    suspend fun ensure(app: NovaStatsApp) {
        runCatching { app.library.loadArtistExceptions(seedDefaults = true) }
        if (isDone(app)) return
        if (app.database.scrobbleDao().countConfirmed() == 0) { markDone(app); return }
        run(app)
    }

    /** Recalcul complet (idempotent). Retourne un résumé. */
    suspend fun run(app: NovaStatsApp): String = mutex.withLock {
        _state.value = "Préparation…"
        try {
            val (moved, dups) = app.library.relinkAll { _state.value = it }
            _state.value = "Albums multi-artistes…"
            val albums = AlbumSharing.consolidate(app.database, app.library)
            app.library.clearCaches()
            app.rebuilder.rebuildAll(fullBillboard = true) { _state.value = it }
            app.library.clearCaches()
            markDone(app)
            val msg = "✅ Liens & versions recalculés — $moved écoute${if (moved > 1) "s" else ""} réattribuée${if (moved > 1) "s" else ""}" +
                (if (dups > 0) " · $dups doublon${if (dups > 1) "s" else ""} supprimé${if (dups > 1) "s" else ""}" else "") +
                (if (albums.merged > 0) " · ${albums.merged} album${if (albums.merged > 1) "s" else ""} fusionné${if (albums.merged > 1) "s" else ""} en multi-artistes" else "")
            _lastResult.value = msg
            msg
        } catch (e: Throwable) {
            com.novastats.app.util.CrashJournal.note(app, "RelinkJob", e)
            val msg = "⚠️ Recalcul interrompu : ${e.message ?: e.javaClass.simpleName}"
            _lastResult.value = msg
            msg
        } finally {
            _state.value = null
        }
    }
}
