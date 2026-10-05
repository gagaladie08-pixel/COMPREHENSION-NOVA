package com.novastats.app.service

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.novastats.app.NovaStatsApp
import com.novastats.app.R
import com.novastats.app.data.db.entity.NotificationFeedEntity
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.domain.AwardRules
import com.novastats.app.domain.Dates
import com.novastats.app.ui.navigation.NovaTab
import com.novastats.app.ui.navigation.PendingNav
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Débloque et annonce les Nova Awards une fois les 60 jours d'utilisation écoulés. */
class NovaAwardsUnlockWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NovaStatsApp
        return try {
            val firstPlayAt = app.database.scrobbleDao().firstScrobbleAt().first() ?: return Result.success()
            val unlockDate = AwardRules.unlockDate(Dates.toLocalDate(firstPlayAt))
            if (Dates.today().isBefore(unlockDate)) {
                schedule(applicationContext, firstPlayAt, retryAfterEarlyRun = true)
                return Result.success()
            }
            if (app.settings.awardsUnlockNotified.first()) return Result.success()
            app.awards.refreshAll(Dates.today())

            val year = Dates.today().year
            val title = "🏆 Tes Nova Awards sont débloqués !"
            val body = "Ton palmarès $year t'attend : découvre les 9 récompenses tirées de tes écoutes."
            val feed = app.database.notificationFeedDao()
            if (!feed.existsEvent("AWARDS_UNLOCK", year.toLong())) {
                feed.insert(
                    NotificationFeedEntity(
                        type = "AWARDS_UNLOCK",
                        entityId = year.toLong(),
                        entityType = "AWARDS",
                        message = "$title — $body"
                    )
                )
            }

            val disabled = app.settings.disabledNotifications.first()
            // Réservation atomique après calcul + écriture du fil : les retries ne perdent pas l'annonce.
            if (!app.settings.claimAwardsUnlockNotification()) return Result.success()
            if (SettingsRepository.Notif.AWARDS_UNLOCK !in disabled && NotificationAccess.canPost(applicationContext)) {
                postNotification(year, title, body)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun postNotification(year: Int, title: String, body: String) {
        val intent = PendingNav.tabIntent(applicationContext, NovaTab.AWARDS.route)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, NovaStatsApp.CHANNEL_AWARDS_UNLOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nOuvre la cérémonie pour voir tes gagnants et partager ton année musicale."))
            .setSubText("NovaStats · cérémonie annuelle $year")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(0xFFFFD700.toInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val WORK_NAME = "nova_awards_unlock"
        private const val NOTIFICATION_ID = 7_791
        private const val REQUEST_CODE = 7_791

        /** Planifie l'annonce à 9 h locale, le jour où l'écran Awards devient accessible. */
        fun schedule(context: Context, firstPlayAt: Long, retryAfterEarlyRun: Boolean = false) {
            val zone = ZoneId.systemDefault()
            val unlockDate = AwardRules.unlockDate(Dates.toLocalDate(firstPlayAt))
            val now = ZonedDateTime.now(zone)
            val trigger = ZonedDateTime.of(unlockDate, LocalTime.of(9, 0), zone)
            val delay = Duration.between(now.toInstant(), trigger.toInstant()).toMillis().coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<NovaAwardsUnlockWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            val workName = if (retryAfterEarlyRun) "$WORK_NAME:$unlockDate:retry:${System.currentTimeMillis()}" else "$WORK_NAME:$unlockDate"
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
