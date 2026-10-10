package com.novastats.app.util

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.novastats.app.NovaStatsApp
import com.novastats.app.R
import com.novastats.app.service.DetectionState
import com.novastats.app.service.NotificationAccess
import com.novastats.app.ui.navigation.NovaTab
import com.novastats.app.ui.navigation.PendingNav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 🐢 Signale un recalcul devenu anormalement lent, avant que l'app ne gèle.
 *
 * La règle (seuil, repérage de la ligne, anti-répetition) vit dans [RebuildHealth], sans
 * dépendance Android, et est testée sur JVM. Ce fichier ne fait qu'afficher.
 *
 * Une même ligne de journal ne produit qu'une alerte : la dernière ligne signalée est mémorisée
 * dans un fichier interne, sinon l'app renotifierait à chaque ouverture tant qu'aucun nouveau
 * recalcul n'a eu lieu.
 */
object StartupHealthAlert {

    private const val NOTIFICATION_ID = 4710
    private const val STATE_FILE = "slow_rebuild_alerted.txt"

    @Volatile
    private var attempted = false
    private val mutex = Mutex()

    suspend fun run(app: NovaStatsApp) {
        withContext(Dispatchers.IO) {
            if (attempted) return@withContext
            attempted = true
            mutex.withLock {
                val context = app.applicationContext
                val log = runCatching { DetectionState.state.value.log }.getOrDefault(emptyList())
                val slowLine = log.firstOrNull { RebuildHealth.lastStep1Ms(listOf(it)) != null }
                val lastNotified = readState(context)
                if (!RebuildHealth.shouldAlert(slowLine, lastNotified)) return@withLock
                val ms = RebuildHealth.lastStep1Ms(listOf(slowLine!!)) ?: return@withLock

                writeState(context, slowLine)
                RebuildAudit.write(context, "🐢 Étape 1 du dernier recalcul : ${RebuildHealth.humanize(ms)} (seuil ${RebuildHealth.SLOW_STEP1_MS / 1000} s)")
                notify(context, ms)
            }
        }
    }

    private fun notify(context: Context, ms: Long) {
        if (!runCatching { NotificationAccess.canPost(context) }.getOrDefault(false)) return
        val duration = RebuildHealth.humanize(ms)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            PendingNav.tabIntent(context, NovaTab.SETTINGS.route),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, NovaStatsApp.CHANNEL_WEEKLY_CHARTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("🐢 Recalcul devenu lent")
            .setContentText("L'étape 1 a pris $duration (seuil ${RebuildHealth.SLOW_STEP1_MS / 1000} s).")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "L'étape 1 du dernier recalcul a pris $duration, au-delà du seuil de " +
                        "${RebuildHealth.SLOW_STEP1_MS / 1000} secondes.\n\n" +
                        "Ce n'est pas une panne : ta bibliothèque a grandi au point qu'une requête " +
                        "redevient coûteuse. C'est le signe avant-coureur du gel qu'a connu la 0.22.15 — " +
                        "mieux vaut le traiter maintenant.\n\n" +
                        "Réglages → Service & diagnostic → Journal de détection montre le détail étape par étape."
                )
            )
            .setSubText("NovaStats · maintenance")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(0xFFFF9F43.toInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    private fun stateFile(context: Context) = File(context.filesDir, STATE_FILE)

    private fun readState(context: Context): String? =
        runCatching { stateFile(context).takeIf { it.exists() }?.readText()?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun writeState(context: Context, line: String) {
        runCatching { stateFile(context).writeText(line) }
    }
}
