package com.novastats.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.novastats.app.MainActivity
import com.novastats.app.NovaStatsApp

/**
 * Service **au premier plan** compagnon de la détection.
 *
 * Le `NotificationListenerService` seul est gelé / tué par les surcouches (MIUI, ColorOS, EMUI, One UI…) dès que
 * l'app quitte l'écran. Ce service affiche une notification discrète et silencieuse « 🎧 NovaStats veille » :
 * le process passe en priorité premier plan et n'est plus gelé. Il ne fait rien d'autre (pas de CPU, pas de wakelock).
 *
 * Démarré par : le listener (à la connexion), l'app (à l'ouverture), le BootReceiver et le watchdog.
 */
class NovaKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DetectionState.bind(this)
        runCatching { goForeground(lastTrackText()) }.onFailure { Log.w(TAG, "startForeground impossible", it); DetectionState.error(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { goForeground(intent?.getStringExtra(EXTRA_TEXT) ?: lastTrackText()) }.onFailure { Log.w(TAG, "startForeground impossible", it) }
        return START_STICKY
    }

    override fun onDestroy() {
        DetectionState.log("Service premier plan arrêté")
        super.onDestroy()
    }

    private fun lastTrackText(): String = DetectionState.state.value.lastTrack?.let { "Dernier titre : $it" } ?: "Lance ta musique, je m'occupe du reste."

    private fun goForeground(text: String) {
        val notif = buildNotification(this, text)
        // Android 14+ exige un type ; avant, le type est facultatif (et `specialUse` n'existe pas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    companion object {
        private const val TAG = "NovaKeepAlive"
        const val NOTIFICATION_ID = 4242
        private const val EXTRA_TEXT = "text"

        fun buildNotification(context: Context, text: String): Notification {
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Builder(context, NovaStatsApp.CHANNEL_SERVICE)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("🎧 NovaStats veille")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        /** Démarre (ou rafraîchit) le service premier plan. Silencieux si Android refuse (lancement depuis l'arrière-plan). */
        fun start(context: Context, text: String? = null): Boolean = runCatching {
            val i = Intent(context, NovaKeepAliveService::class.java)
            if (text != null) i.putExtra(EXTRA_TEXT, text)
            ContextCompat.startForegroundService(context, i)
            true
        }.onFailure { Log.w(TAG, "Lancement refusé : ${it.javaClass.simpleName}", it) }.getOrDefault(false)

        fun stop(context: Context) = runCatching { context.stopService(Intent(context, NovaKeepAliveService::class.java)) }
    }
}
