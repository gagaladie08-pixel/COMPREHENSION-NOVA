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
import androidx.work.workDataOf
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.api.EnrichmentState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Enrichissement en arrière-plan : traite un lot (10 artistes + 10 albums + 25 titres), puis se replanifie
 * tant qu'il reste des entités sans image. Respecte les limites de débit de chaque API via les RateLimiter.
 */
class EnrichmentWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NovaStatsApp
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        if (!manual && !app.settings.autoEnrich.first()) return Result.success()

        val result = try {
            app.enricher.runBatch()
        } catch (e: Exception) {
            EnrichmentState.log("⚠️ Lot interrompu : ${e.message}")
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        if (result.remaining > 0 && result.processed > 0) enqueue(applicationContext, manual = manual, delayMinutes = 1)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NOW = "nova_enrich_now"
        private const val UNIQUE_PERIODIC = "nova_enrich_daily"
        private const val KEY_MANUAL = "manual"

        /** Lance (ou enchaîne) un lot. [manual] ignore le réglage "enrichissement automatique". */
        fun enqueue(context: Context, manual: Boolean = false, delayMinutes: Long = 0) {
            val appContext = context.applicationContext
            val app = appContext as NovaStatsApp
            CoroutineScope(Dispatchers.IO).launch {
                val wifiOnly = runCatching { app.settings.enrichWifiOnly.first() }.getOrDefault(false)
                val request = OneTimeWorkRequestBuilder<EnrichmentWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly && !manual) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                    .setInputData(workDataOf(KEY_MANUAL to manual))
                    .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(appContext).enqueueUniqueWork(UNIQUE_NOW, if (manual) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.APPEND_OR_REPLACE, request)
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
