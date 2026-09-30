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
import com.novastats.app.MainActivity
import com.novastats.app.NovaStatsApp
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.NotificationFeedEntity
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.data.repository.StatsRebuilder
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.PantheonStatus
import kotlinx.coroutines.flow.first

/**
 * 🔔 Notifications de succès : certifications (par niveau + multiplicateurs), statuts Panthéon, intronisations
 * Hall of Fame — chacune désactivable dans ⚙️ Paramètres → Notifications. Toute nouveauté est aussi
 * consignée dans `notifications_feed` (fil de l'Accueil), notification système ou pas.
 */
object AchievementNotifier {

    suspend fun notify(context: Context, news: List<StatsRebuilder.Achievement>) {
        if (news.isEmpty()) return
        val app = context.applicationContext as NovaStatsApp
        val disabled = app.settings.disabledNotifications.first()
        val canPost = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        for ((i, a) in news.withIndex()) {
            val (title, text, key) = describe(a) ?: continue
            app.database.notificationFeedDao().insert(NotificationFeedEntity(type = a.kind, entityId = a.entityId, entityType = a.entityType, message = "$title — $text"))
            if (!canPost || key in disabled) continue
            val intent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            val pi = PendingIntent.getActivity(context, a.entityId.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(context, NovaStatsApp.CHANNEL_ACHIEVEMENTS)
                .setSmallIcon(android.R.drawable.star_on)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 100_000).toInt() + i, n) }
        }
    }

    /** (titre, texte, clé de réglage) ou null si inconnu. */
    fun describe(a: StatsRebuilder.Achievement): Triple<String, String, String>? = when (a.kind) {
        "CERTIFICATION" -> {
            val levelName = a.level.substringBefore(':')
            val multiplier = a.level.substringAfter(':', "1").toIntOrNull() ?: 1
            val level = CertLevel.entries.firstOrNull { it.dbName == levelName } ?: return null
            val kind = if (a.entityType == EntityType.ALBUM) "Album" else "Titre"
            val key = when {
                multiplier > 1 -> SettingsRepository.Notif.CERT_MULTIPLIERS
                level == CertLevel.SILVER -> SettingsRepository.Notif.CERT_SILVER
                level == CertLevel.GOLD -> SettingsRepository.Notif.CERT_GOLD
                level == CertLevel.PLATINUM -> SettingsRepository.Notif.CERT_PLATINUM
                else -> SettingsRepository.Notif.CERT_DIAMOND
            }
            val label = if (multiplier > 1) "${multiplier}x ${level.emoji} ${level.label}" else "${level.emoji} ${level.label}"
            Triple("$label — nouvelle certification", "$kind « ${a.name} » vient d'être certifié $label !", key)
        }
        "PANTHEON" -> {
            val st = PantheonStatus.fromDb(a.level) ?: return null
            val key = when (st) {
                PantheonStatus.STAR -> SettingsRepository.Notif.P_STAR
                PantheonStatus.SUPERSTAR -> SettingsRepository.Notif.P_SUPERSTAR
                PantheonStatus.MEGASTAR -> SettingsRepository.Notif.P_MEGASTAR
                PantheonStatus.LEGENDE -> SettingsRepository.Notif.P_LEGENDE
                PantheonStatus.MYTHIQUE -> SettingsRepository.Notif.P_MYTHIQUE
            }
            Triple("${st.emoji} ${a.name} entre au Panthéon", "Nouveau statut : ${st.label.uppercase()}", key)
        }
        "HALL_OF_FAME" -> Triple("🏛️ Hall of Fame — nouvelle intronisation", "« ${a.name} » · ${a.level.lowercase().replace('_', ' ')}", SettingsRepository.Notif.HOF)
        else -> null
    }
}
