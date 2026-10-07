package com.novastats.app.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.novastats.app.MainActivity
import com.novastats.app.NovaStatsApp
import com.novastats.app.R
import com.novastats.app.data.db.entity.NotificationFeedEntity
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.ui.navigation.PendingNav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Notification discrète pour une proposition de correspondance musicale à confirmer ou corriger. */
object ReviewNotifier {
    private const val ACTION_CONFIRM = "com.novastats.app.REVIEW_CONFIRM"
    private const val EXTRA_TRACK = "track_id"
    private const val EXTRA_NOTIFICATION = "notif_id"
    private const val BASE_ID = 60_000
    private const val DEDUPE_WINDOW_MS = 24L * 60 * 60 * 1_000

    suspend fun notifyFlagged(context: Context, trackId: Long, title: String, artist: String, proposal: String, score: Int) {
        val app = context.applicationContext as NovaStatsApp
        val now = System.currentTimeMillis()
        val feed = app.database.notificationFeedDao()
        val message = "🟡 À vérifier : $title — $artist · proposition $proposal ($score %)."
        val alreadyReported = feed.existsRecentMessage(TYPE, trackId, message, now - DEDUPE_WINDOW_MS)
        if (alreadyReported) return

        feed.insert(NotificationFeedEntity(type = TYPE, entityId = trackId, entityType = "TRACK", message = message, createdAt = now))
        if (SettingsRepository.Notif.REVIEW in app.settings.disabledNotifications.first() || !NotificationAccess.canPost(context)) return

        val id = notificationId(trackId)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(PendingNav.EXTRA_OPEN, PendingNav.TARGET_REVIEW)
            putExtra(PendingNav.EXTRA_TRACK, trackId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val confirmIntent = Intent(context, ReviewActionReceiver::class.java).apply {
            action = ACTION_CONFIRM
            putExtra(EXTRA_TRACK, trackId)
            putExtra(EXTRA_NOTIFICATION, id)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            confirmIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, NovaStatsApp.CHANNEL_REVIEW)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🟡 Correspondance à vérifier")
            .setContentText("$title · $artist")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message\n\nConfirme la proposition ou ouvre l'Éditeur pour la corriger."))
            .setSubText("NovaStats · contrôle qualité")
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(0xFFFFC857.toInt())
            .setContentIntent(openPendingIntent)
            .addAction(0, "✅ C'est correct", confirmPendingIntent)
            .addAction(0, "✏️ Corriger", openPendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    private fun notificationId(trackId: Long): Int {
        val hash = (trackId xor (trackId ushr 32)).toInt() and 0x7FFFFFFF
        return BASE_ID + (hash % 1_000_000)
    }

    class ReviewActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_CONFIRM) return
            val trackId = intent.getLongExtra(EXTRA_TRACK, -1L)
            if (trackId <= 0) return
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
            val app = context.applicationContext as NovaStatsApp
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val confirmed = runCatching { app.editor.confirmReview(trackId) }.isSuccess
                    if (confirmed) runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private const val TYPE = "REVIEW"
}
