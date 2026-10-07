package com.novastats.app.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.work.workDataOf
import androidx.core.app.NotificationCompat
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.api.EnrichmentState
import com.novastats.app.data.api.MetadataEnricher
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Enrichissement en arrière-plan : traite un lot (10 artistes + 10 albums + 25 titres), puis se replanifie
 * tant qu'il reste des entités sans image. Respecte les limites de débit de chaque API via les RateLimiter.
 */
class EnrichmentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NovaStatsApp
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        val mode = inputData.getString(KEY_MODE) ?: MODE_BATCH
        if (mode == MODE_BATCH && !manual && !app.settings.autoEnrich.first()) return Result.success()

        // Ré-enrichissement (long) : service de premier plan WorkManager pour ne pas être tué après 10 min
        if (mode != MODE_BATCH) {
            try {
                setForeground(foregroundInfo(mode))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                EnrichmentState.log("⚠️ Impossible de démarrer le premier plan : ${failure.message ?: failure.javaClass.simpleName}")
                return Result.failure()
            }
        }

        val result = try {
            when (mode) {
                MODE_ALL -> app.enricher.refreshAll()
                MODE_SELECTED -> app.enricher.refreshSelected(
                    inputData.getStringArray(KEY_TARGETS).orEmpty().mapNotNull { MetadataEnricher.RefreshTarget.decode(it) }
                )
                else -> app.enricher.runBatch()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            EnrichmentState.log("⏹️ Ré-enrichissement arrêté")
            throw e
        } catch (e: Exception) {
            EnrichmentState.log("⚠️ Lot interrompu : ${e.message}")
            return if (mode == MODE_BATCH && runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (mode == MODE_BATCH && result.remaining > 0 && result.processed > 0) enqueue(applicationContext, manual = manual, delayMinutes = 1)
        return Result.success()
    }

    private fun foregroundInfo(mode: String): ForegroundInfo {
        val title = if (mode == MODE_ALL) "🔄 Ré-enrichissement complet" else "🔄 Ré-enrichissement"
        val notification = NotificationCompat.Builder(applicationContext, NovaStatsApp.CHANNEL_SERVICE)
            .setSmallIcon(com.novastats.app.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Pochettes et photos en cours de mise à jour… (Réglages → APIs pour suivre ou arrêter)")
            .setOngoing(true).setSilent(true)
            .setProgress(0, 0, true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, notification)
    }

    companion object {
        private const val UNIQUE_NOW = "nova_enrich_now"
        private const val UNIQUE_PERIODIC = "nova_enrich_daily"
        private const val KEY_MANUAL = "manual"
        private const val KEY_MODE = "mode"
        private const val KEY_TARGETS = "targets"
        private const val MODE_BATCH = "batch"
        private const val MODE_ALL = "all"
        private const val MODE_SELECTED = "selected"
        private const val NOTIF_ID = 4243

        /** « Tout ré-enrichir » (artistes → albums → titres, hors images choisies à la main). */
        fun enqueueRefreshAll(context: Context) = enqueueRefresh(context, MODE_ALL, emptyArray())

        /** « Ré-enrichir… » : sélection d'artistes / albums / titres. */
        fun enqueueRefresh(context: Context, targets: List<MetadataEnricher.RefreshTarget>) =
            enqueueRefresh(context, MODE_SELECTED, targets.map { it.encode() }.toTypedArray())

        private fun enqueueRefresh(context: Context, mode: String, targets: Array<String>) {
            val request = OneTimeWorkRequestBuilder<EnrichmentWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_MANUAL to true, KEY_MODE to mode, KEY_TARGETS to targets))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request)
        }

        /** Arrête le passage en cours (lot ou ré-enrichissement). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NOW)
        }

        /** Lance (ou enchaîne) un lot. [manual] ignore le réglage "enrichissement automatique". */
        fun enqueue(context: Context, manual: Boolean = false, delayMinutes: Long = 0) {
            val appContext = context.applicationContext
            val app = appContext as NovaStatsApp
            app.launchIoTask {
                try {
                    val wifiOnly = app.settings.enrichWifiOnly.first()
                    val request = OneTimeWorkRequestBuilder<EnrichmentWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly && !manual) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                        .setInputData(workDataOf(KEY_MANUAL to manual))
                        .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                        .build()
                    WorkManager.getInstance(appContext).enqueueUniqueWork(UNIQUE_NOW, if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE, request)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    EnrichmentState.log("⚠️ Planification impossible : ${failure.message ?: failure.javaClass.simpleName}")
                }
            }
        }

        /** Passage quotidien (stratégie 15 : enrichissement progressif des entités encore incomplètes). */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<EnrichmentWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
