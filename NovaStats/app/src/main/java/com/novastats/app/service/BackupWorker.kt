package com.novastats.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.novastats.app.BuildConfig
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.importer.BackupExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 💾 Sauvegarde automatique quotidienne (option Paramètres → Données). Écrit un export JSON complet dans
 * `Android/data/com.novastats.app/files/backups/` et conserve les 7 plus récents.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NovaStatsApp
        val forced = inputData.getBoolean(KEY_FORCE, false)
        if (!forced && !app.settings.autoBackup.first()) return Result.success()
        return try {
            writeBackup(app)
            runCatching { AgentSync.push(app) }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val UNIQUE_PERIODIC = "nova_backup_daily"
        private const val UNIQUE_NOW = "nova_backup_now"
        private const val KEY_FORCE = "force"
        const val KEEP = 7

        fun dir(context: Context): File = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").apply { mkdirs() }

        fun existing(context: Context): List<File> = dir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedByDescending { it.lastModified() }

        suspend fun writeBackup(app: NovaStatsApp): File {
            val (text, _) = BackupExporter.build(app.database, BuildConfig.VERSION_NAME)
            val file = File(dir(app), BackupExporter.fileName())
            file.writeText(text)
            existing(app).drop(KEEP).forEach { it.delete() }
            app.settings.setLastBackupAt(System.currentTimeMillis())
            return file
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).setInitialDelay(2, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<BackupWorker>().setInputData(androidx.work.workDataOf(KEY_FORCE to true)).build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
