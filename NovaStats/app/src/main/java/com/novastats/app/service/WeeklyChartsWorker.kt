package com.novastats.app.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.novastats.app.MainActivity
import com.novastats.app.NovaStatsApp
import com.novastats.app.R
import com.novastats.app.data.db.dao.RankedEntry
import com.novastats.app.data.db.entity.NotificationFeedEntity
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.domain.Chart
import com.novastats.app.domain.DateRange
import com.novastats.app.domain.WeeklyChartHighlight
import com.novastats.app.domain.WeeklyChartsDigest
import com.novastats.app.domain.WeeklyChartsDigestBuilder
import com.novastats.app.domain.WeeklyChartsSchedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Envoie le lundi matin le bilan des leaders de la semaine complète précédente (données locales uniquement). */
class WeeklyChartsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as NovaStatsApp
        val zone = ZoneId.systemDefault()
        val scheduledMonday = inputData.getString(KEY_SCHEDULED_MONDAY)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.takeIf { it.dayOfWeek == java.time.DayOfWeek.MONDAY }
            ?: WeeklyChartsSchedule.nextRun(ZonedDateTime.now(zone)).toLocalDate()

        // Planifie le prochain lundi avant le travail : même si Android interrompt ce worker, le rendez-vous suivant reste posé.
        runCatching { scheduleAfter(applicationContext, scheduledMonday) }

        return try {
            if (SettingsRepository.Notif.WEEKLY_CHARTS in app.settings.disabledNotifications.first()) return Result.success()

            val week = WeeklyChartsSchedule.previousClosedWeek(scheduledMonday)
            val previousWeek = DateRange(week.from.minusWeeks(1), week.to.minusWeeks(1))
            val digest = buildDigest(app, week, previousWeek) ?: return Result.success()

            app.database.notificationFeedDao().insert(
                NotificationFeedEntity(
                    type = TYPE,
                    entityId = 0,
                    entityType = "CHARTS",
                    message = "${digest.title} — ${digest.body}"
                )
            )
            if (canPostNotifications()) postNotification(digest)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private suspend fun buildDigest(
        app: NovaStatsApp,
        week: DateRange,
        previousWeek: DateRange
    ): WeeklyChartsDigest? {
        val dao = app.database.billboardDao()
        val currentTracks = dao.rankTracks(week.from.toString(), week.to.toString(), 1).firstOrNull()
        val currentArtists = dao.rankArtists(week.from.toString(), week.to.toString(), 1).firstOrNull()
        val currentAlbums = dao.rankAlbums(week.from.toString(), week.to.toString(), 1).firstOrNull()
        val previousTracks = dao.rankTracks(previousWeek.from.toString(), previousWeek.to.toString(), 1).firstOrNull()
        val previousArtists = dao.rankArtists(previousWeek.from.toString(), previousWeek.to.toString(), 1).firstOrNull()
        val previousAlbums = dao.rankAlbums(previousWeek.from.toString(), previousWeek.to.toString(), 1).firstOrNull()

        val trackIds = listOfNotNull(currentTracks?.entityId, previousTracks?.entityId).distinct()
        val artistIds = listOfNotNull(currentArtists?.entityId, previousArtists?.entityId).distinct()
        val albumIds = listOfNotNull(currentAlbums?.entityId, previousAlbums?.entityId).distinct()
        val tracks = if (trackIds.isEmpty()) emptyMap() else app.database.trackDao().byIds(trackIds).associateBy { it.trackId }
        val artists = if (artistIds.isEmpty()) emptyMap() else app.database.artistDao().byIds(artistIds).associateBy { it.artistId }
        val albums = if (albumIds.isEmpty()) emptyMap() else app.database.albumDao().byIds(albumIds).associateBy { it.albumId }

        val highlights = mutableListOf<WeeklyChartHighlight>().apply {
            addHighlight(Chart.HOT_100, currentTracks, previousTracks, currentTracks?.let { tracks[it.entityId]?.title })
            addHighlight(Chart.ARTIST_50, currentArtists, previousArtists, currentArtists?.let { artists[it.entityId]?.name })
            addHighlight(Chart.ALBUMS_75, currentAlbums, previousAlbums, currentAlbums?.let { albums[it.entityId]?.title })
        }
        return WeeklyChartsDigestBuilder.build(week, highlights)
    }

    private fun MutableList<WeeklyChartHighlight>.addHighlight(
        chart: Chart,
        current: RankedEntry?,
        previous: RankedEntry?,
        name: String?
    ) {
        if (current == null || name.isNullOrBlank()) return
        add(
            WeeklyChartHighlight(
                chart = chart,
                entityId = current.entityId,
                name = name,
                plays = current.plays,
                previousLeaderId = previous?.entityId
            )
        )
    }

    private fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun postNotification(digest: WeeklyChartsDigest) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            PENDING_INTENT_REQUEST,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, NovaStatsApp.CHANNEL_WEEKLY_CHARTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(digest.title)
            .setContentText(digest.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(digest.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val KEY_SCHEDULED_MONDAY = "scheduled_monday"
        private const val UNIQUE_WORK_PREFIX = "nova_weekly_charts"
        private const val TYPE = "WEEKLY_CHARTS"
        private const val NOTIFICATION_ID = 7310
        private const val PENDING_INTENT_REQUEST = 7310

        /** Pose ou conserve le prochain rendez-vous, chaque semaine à 9 h locale. */
        fun schedule(context: Context) {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            enqueueFor(context.applicationContext, WeeklyChartsSchedule.nextRun(now).toLocalDate(), now)
        }

        private fun scheduleAfter(context: Context, currentMonday: LocalDate) {
            val now = ZonedDateTime.now(ZoneId.systemDefault())
            val nextMonday = WeeklyChartsSchedule.nextMondayAfter(currentMonday, now)
            enqueueFor(context.applicationContext, nextMonday, now)
        }

        private fun enqueueFor(context: Context, monday: LocalDate, now: ZonedDateTime) {
            val trigger = WeeklyChartsSchedule.at(monday, now.zone)
            val delay = Duration.between(now.toInstant(), trigger.toInstant()).toMillis().coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<WeeklyChartsWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_SCHEDULED_MONDAY to monday.toString()))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_WORK_PREFIX:$monday",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
