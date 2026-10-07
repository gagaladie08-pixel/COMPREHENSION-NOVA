package com.novastats.app.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Santé du service de détection, **persistée** (SharedPreferences) pour survivre à la mort du process :
 *  - battement de cœur (dernier tick du service) → « dernier signe de vie il y a X »
 *  - nombre de relances par le watchdog et raison de la dernière
 *  - journal des 60 derniers événements (le journal en mémoire de [DetectionState] disparaît quand le process est tué)
 */
object ServiceHealth {
    private const val PREFS = "nova_service_health"
    private const val K_HEARTBEAT = "heartbeat"
    private const val K_CONNECTED_AT = "connected_at"
    private const val K_DISCONNECTED_AT = "disconnected_at"
    private const val K_RESTARTS = "restarts"
    private const val K_LAST_RESTART = "last_restart"
    private const val K_LAST_RESTART_REASON = "last_restart_reason"
    private const val K_LOG = "log"
    private const val LOG_SEP = "\u0001"
    private const val LOG_MAX = 60

    /** Au-delà de ce délai sans battement, le service est considéré endormi / mort. */
    const val STALE_MS = 3 * 60_000L
    private const val HEARTBEAT_EVERY_MS = 20_000L

    data class Snapshot(
        val lastHeartbeat: Long = 0,
        val connectedAt: Long = 0,
        val disconnectedAt: Long = 0,
        val restarts: Int = 0,
        val lastRestart: Long = 0,
        val lastRestartReason: String? = null
    ) {
        fun isStale(now: Long = System.currentTimeMillis()) = lastHeartbeat <= 0L || now - lastHeartbeat > STALE_MS
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state
    private var lastHeartbeatWrite = 0L

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Snapshot {
        val p = prefs(context)
        val s = Snapshot(
            p.getLong(K_HEARTBEAT, 0), p.getLong(K_CONNECTED_AT, 0), p.getLong(K_DISCONNECTED_AT, 0),
            p.getInt(K_RESTARTS, 0), p.getLong(K_LAST_RESTART, 0), p.getString(K_LAST_RESTART_REASON, null)
        )
        _state.value = s
        return s
    }

    /** Battement de cœur (appelé à chaque tick du service) — écrit sur disque au plus toutes les 20 s. */
    fun heartbeat(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHeartbeatWrite < HEARTBEAT_EVERY_MS) return
        lastHeartbeatWrite = now
        prefs(context).edit().putLong(K_HEARTBEAT, now).apply()
        _state.value = _state.value.copy(lastHeartbeat = now)
    }

    fun connected(context: Context, value: Boolean) {
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(if (value) K_CONNECTED_AT else K_DISCONNECTED_AT, now).apply()
        _state.value = if (value) _state.value.copy(connectedAt = now) else _state.value.copy(disconnectedAt = now)
        if (value) heartbeat(context, force = true)
    }

    fun recordRestart(context: Context, reason: String) {
        val now = System.currentTimeMillis()
        val n = prefs(context).getInt(K_RESTARTS, 0) + 1
        prefs(context).edit().putInt(K_RESTARTS, n).putLong(K_LAST_RESTART, now).putString(K_LAST_RESTART_REASON, reason).apply()
        _state.value = _state.value.copy(restarts = n, lastRestart = now, lastRestartReason = reason)
    }

    fun persistedLog(context: Context): List<String> =
        prefs(context).getString(K_LOG, null)?.split(LOG_SEP)?.filter { it.isNotBlank() }.orEmpty()

    fun appendLog(context: Context, line: String) {
        val lines = (listOf(line) + persistedLog(context)).take(LOG_MAX)
        prefs(context).edit().putString(K_LOG, lines.joinToString(LOG_SEP)).apply()
    }

    /** « il y a 42 s / 3 min / 2 h » */
    fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
        if (ts <= 0L) return "jamais"
        val s = (now - ts) / 1000
        return when {
            s < 60 -> "il y a ${s}s"
            s < 3600 -> "il y a ${s / 60} min"
            s < 86_400 -> "il y a ${s / 3600} h"
            else -> "il y a ${s / 86_400} j"
        }
    }
}
