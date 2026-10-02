package com.novastats.app.service

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.novastats.app.MainActivity
import com.novastats.app.NovaStatsApp
import com.novastats.app.R
import com.novastats.app.ui.navigation.PendingNav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 🟡 À vérifier — notification discrète quand une correspondance API est acceptée avec flag (score 70-89) :
 * [✅ C'est correct] confirme sans ouvrir l'app · [✏️ Corriger] ouvre l'Éditeur sur ⚠️ À corriger.
 */
object ReviewNotifier {
    private const val ACTION_CONFIRM = "com.novastats.app.REVIEW_CONFIRM"
    private const val EXTRA_TRACK = "track_id"
    private const val BASE_ID = 60_000

    fun notifyFlagged(context: Context, trackId: Long, title: String, artist: String, proposal: String, score: Int) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val id = BASE_ID + (trackId % 10_000).toInt()
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(PendingNav.EXTRA_OPEN, PendingNav.TARGET_REVIEW); putExtra(PendingNav.EXTRA_TRACK, trackId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val confirm = Intent(context, ReviewActionReceiver::class.java).apply { action = ACTION_CONFIRM; putExtra(EXTRA_TRACK, trackId); putExtra("notif_id", id) }
        val confirmPi = PendingIntent.getBroadcast(context, id, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, NovaStatsApp.CHANNEL_REVIEW)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🟡 À vérifier : $title")
            .setContentText("$artist · proposition $proposal ($score %)")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$artist\nValeur proposée : $proposal (score $score %). Confirme ou corrige."))
            .setContentIntent(openPi)
            .addAction(0, "✅ C'est correct", confirmPi)
            .addAction(0, "✏️ Corriger", openPi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    class ReviewActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_CONFIRM) return
            val trackId = intent.getLongExtra(EXTRA_TRACK, -1L); if (trackId <= 0) return
            val notifId = intent.getIntExtra("notif_id", 0)
            val app = context.applicationContext as NovaStatsApp
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { app.editor.confirmReview(trackId) }
                runCatching { NotificationManagerCompat.from(context).cancel(notifId) }
                pending.finish()
            }
        }
    }
}
