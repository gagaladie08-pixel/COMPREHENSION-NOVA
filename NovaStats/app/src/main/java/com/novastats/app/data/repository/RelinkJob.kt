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
    /** v5 (0.22.11) : marqueurs de versions solo/membre et suffixes nus — réapplique les règles aux écoutes existantes une fois. */
    private const val KEY_DONE = "v5_done"
    /** Réparation groupée des totaux — relance le correctif lent v2 une dernière fois, sans re-résoudre les écoutes. */
    private const val KEY_ROOT_TOTALS_DONE = "root_totals_grouped_v3_done"
    private val mutex = Mutex()

    private val _state = MutableStateFlow<String?>(null)
    val state: StateFlow<String?> = _state
    private val _lastResult = MutableStateFlow<String?>(null)
    val lastResult: StateFlow<String?> = _lastResult

    fun isDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)
    private fun markDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    private fun isRootTotalsRepairDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ROOT_TOTALS_DONE, false)
    private fun markRootTotalsRepairDone(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ROOT_TOTALS_DONE, true).apply()

    /** Au démarrage : charge les noms protégés, puis effectue au besoin le rapprochement ou la réparation légère des totaux. */
    suspend fun ensure(app: NovaStatsApp) {
        runCatching { app.library.loadArtistExceptions(seedDefaults = true) }
        val confirmedCount = app.database.scrobbleDao().countConfirmed()
        if (!isDone(app)) {
            if (confirmedCount == 0) {
                markDone(app)
                markRootTotalsRepairDone(app)
                return
            }
            run(app)
        }
        if (isRootTotalsRepairDone(app)) return
        if (confirmedCount == 0) {
            markRootTotalsRepairDone(app)
            return
        }

        // Répare le bug d'agrégation des versions déjà liées sans réexécuter la résolution coûteuse des scrobbles.
        _state.value = "Réparation des totaux…"
        try {
            app.rebuilder.repairTrackRootTotals()
            app.library.clearCaches()
            markRootTotalsRepairDone(app)
            _lastResult.value = "✅ Totaux des versions réparés"
        } catch (e: Throwable) {
            com.novastats.app.util.CrashJournal.note(app, "RelinkJob.rootTotalsRepair", e)
            _lastResult.value = "⚠️ Réparation des totaux interrompue : ${e.message ?: e.javaClass.simpleName}"
        } finally {
            _state.value = null
        }
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
            markRootTotalsRepairDone(app)
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
