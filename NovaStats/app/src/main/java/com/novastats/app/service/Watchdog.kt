package com.novastats.app.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.novastats.app.NovaStatsApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 🐕 Chien de garde de la détection.
 *
 * Vérifie que le listener est **réellement** vivant (connecté + battement de cœur récent), pas seulement que la
 * permission est cochée. Sinon : toggle du composant (astuce standard pour forcer Android à relier le listener),
 * `requestRebind`, et relance du service premier plan.
 *
 * Déclenché : toutes les 15 min (WorkManager — minimum autorisé), à chaque ouverture de l'app, au boot.
 */
object Watchdog {
    private const val TAG = "NovaWatchdog"
    private const val UNIQUE = "nova_watchdog"

    data class Status(
        val permission: Boolean,
        val connected: Boolean,
        val heartbeatAgeMs: Long,
        val batteryExempt: Boolean
    ) {
        /** Permission OK mais service endormi / mort. */
        val asleep get() = permission && (!connected || heartbeatAgeMs > ServiceHealth.STALE_MS)
    }

    fun status(context: Context): Status {
        val health = ServiceHealth.load(context)
        val now = System.currentTimeMillis()
        return Status(
            permission = NovaListenerService.isEnabled(context),
            connected = DetectionState.state.value.listenerConnected,
            heartbeatAgeMs = if (health.lastHeartbeat <= 0L) Long.MAX_VALUE else now - health.lastHeartbeat,
            batteryExempt = isBatteryExempt(context)
        )
    }

    fun isBatteryExempt(context: Context): Boolean =
        runCatching { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(false)

    /**
     * Vérification + relance si nécessaire. Retourne true si une relance a été tentée.
     * [fromForeground] = appelé depuis l'app visible (le service premier plan peut toujours démarrer).
     */
    fun check(context: Context, reason: String, fromForeground: Boolean = false): Boolean {
        val st = status(context)
        if (!st.permission) return false
        // Toujours s'assurer que le service premier plan tourne (il est inoffensif s'il tourne déjà)
        NovaKeepAliveService.start(context)
        if (!st.asleep) return false
        revive(context, "$reason · ${if (!st.connected) "listener déconnecté" else "pas de battement depuis ${st.heartbeatAgeMs / 1000}s"}")
        return true
    }

    /** Force la reconnexion du listener (toggle composant + rebind) et relance le service premier plan. */
    fun revive(context: Context, reason: String) {
        Log.i(TAG, "Relance du listener : $reason")
        ServiceHealth.recordRestart(context, reason)
        DetectionState.log("🐕 Watchdog : relance ($reason)")
        val component = ComponentName(context, NovaListenerService::class.java)
        runCatching {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            NotificationListenerService.requestRebind(component)
        }.onFailure { Log.w(TAG, "Rebind impossible", it); DetectionState.error(it) }
        NovaKeepAliveService.start(context)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE)
}

class WatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as NovaStatsApp
        if (!app.settings.watchdogEnabled.first()) return Result.success()
        DetectionState.bind(applicationContext)
        Watchdog.check(applicationContext, "périodique")
        return Result.success()
    }
}
