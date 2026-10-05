package com.novastats.app.service

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.novastats.app.NovaStatsApp
import com.novastats.app.R
import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.db.entity.NotificationFeedEntity
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.data.repository.StatsRebuilder
import com.novastats.app.domain.CertLevel
import com.novastats.app.domain.PantheonStatus
import com.novastats.app.ui.navigation.NovaTab
import com.novastats.app.ui.navigation.PendingNav
import kotlinx.coroutines.flow.first

/** Notifications dédiées aux certifications, au Panthéon et au Hall of Fame. */
object AchievementNotifier {
    private const val GROUP = "com.novastats.app.ACHIEVEMENTS"
    private const val SUMMARY_ID = 70_006
    private const val SUMMARY_REQUEST = 70_006
    private const val FIRST_SCROBBLE_ID = 7_777

    private data class PostedAchievement(
        val title: String,
        val text: String,
        val route: String
    )

    /** Toute nouveauté rejoint le fil local ; les alertes système respectent permission, canal et interrupteur. */
    suspend fun notify(context: Context, news: List<StatsRebuilder.Achievement>) {
        if (news.isEmpty()) return
        val app = context.applicationContext as NovaStatsApp
        val disabled = app.settings.disabledNotifications.first()
        val canPost = NotificationAccess.canPost(context)
        val manager = NotificationManagerCompat.from(context)
        val posted = mutableListOf<PostedAchievement>()

        news.forEach { achievement ->
            val (title, text, key) = describe(achievement) ?: return@forEach
            val message = "$title — $text"
            val feed = app.database.notificationFeedDao()
            val duplicate = feed.existsRecentMessage(
                achievement.kind,
                achievement.entityId,
                message,
                System.currentTimeMillis() - 60L * 60 * 1_000
            )
            if (duplicate) return@forEach
            feed.insert(
                NotificationFeedEntity(
                    type = achievement.kind,
                    entityId = achievement.entityId,
                    entityType = achievement.entityType,
                    message = message
                )
            )
            if (!canPost || key in disabled) return@forEach

            val route = routeFor(key)
            val id = notificationId(achievement)
            val pendingIntent = PendingIntent.getActivity(
                context,
                id,
                PendingNav.tabIntent(context, route),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, channelFor(key))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n\nAppuie pour retrouver ce palier dans NovaStats."))
                .setSubText("NovaStats · nouvelle récompense")
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setColor(colorFor(key))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(GROUP)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            runCatching { manager.notify(id, notification) }
            posted += PostedAchievement(title, text, route)
        }

        if (posted.size > 1) postGroupSummary(context, posted)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            com.novastats.app.ui.theme.ThemeEvents.unlocked()
        }
    }

    /** Première écoute : annoncée une fois, même si Android ou le réglage utilisateur bloque la bannière. */
    suspend fun firstScrobble(context: Context, display: String) {
        val app = context.applicationContext as NovaStatsApp
        if (!app.settings.claimFirstScrobbleNotification()) return

        val title = "🎉 Ta première écoute !"
        val body = "$display — ton histoire musicale commence maintenant."
        app.database.notificationFeedDao().insert(
            NotificationFeedEntity(
                type = "FIRST_SCROBBLE",
                entityId = 0,
                entityType = "TRACK",
                message = "$title $body 🏆 Premier Scrobble débloqué !"
            )
        )
        if (SettingsRepository.Notif.FIRST_SCROBBLE in app.settings.disabledNotifications.first() || !NotificationAccess.canPost(context)) return

        val pendingIntent = PendingIntent.getActivity(
            context,
            FIRST_SCROBBLE_ID,
            PendingNav.tabIntent(context, NovaTab.STATS.route),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, NovaStatsApp.CHANNEL_FIRST_SCROBBLE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\n🏆 Premier Scrobble débloqué : le début de tes charts, records et récompenses."))
            .setSubText("NovaStats · ton premier scrobble")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(0xFF7C6CFF.toInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(FIRST_SCROBBLE_ID, notification) }
    }

    private fun postGroupSummary(context: Context, posted: List<PostedAchievement>) {
        val route = posted.map { it.route }.distinct().singleOrNull() ?: NovaTab.HOME.route
        val title = "✨ ${posted.size} nouvelles récompenses"
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        posted.take(6).forEach { inbox.addLine("${it.title} · ${it.text}") }
        if (posted.size > 6) inbox.addLine("et ${posted.size - 6} autre(s)…")
        val pendingIntent = PendingIntent.getActivity(
            context,
            SUMMARY_REQUEST,
            PendingNav.tabIntent(context, route),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val summary = NotificationCompat.Builder(context, NovaStatsApp.CHANNEL_ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Certifications, statuts et intronisations : ton écoute a marqué l'histoire.")
            .setStyle(inbox)
            .setSubText("NovaStats · récapitulatif")
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setColor(0xFF00D4FF.toInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary) }
    }

    private fun notificationId(a: StatsRebuilder.Achievement): Int {
        val hash = "${a.kind}:${a.entityType}:${a.entityId}:${a.level}".hashCode() and 0x7FFFFFFF
        return if (hash == 0) 1 else hash
    }

    private fun channelFor(key: String): String = when (key) {
        SettingsRepository.Notif.CERT_SILVER -> NovaStatsApp.CHANNEL_CERT_SILVER
        SettingsRepository.Notif.CERT_GOLD -> NovaStatsApp.CHANNEL_CERT_GOLD
        SettingsRepository.Notif.CERT_PLATINUM -> NovaStatsApp.CHANNEL_CERT_PLATINUM
        SettingsRepository.Notif.CERT_DIAMOND -> NovaStatsApp.CHANNEL_CERT_DIAMOND
        SettingsRepository.Notif.CERT_MULTIPLIERS -> NovaStatsApp.CHANNEL_CERT_MULTIPLIERS
        SettingsRepository.Notif.P_STAR -> NovaStatsApp.CHANNEL_PANTHEON_STAR
        SettingsRepository.Notif.P_SUPERSTAR -> NovaStatsApp.CHANNEL_PANTHEON_SUPERSTAR
        SettingsRepository.Notif.P_MEGASTAR -> NovaStatsApp.CHANNEL_PANTHEON_MEGASTAR
        SettingsRepository.Notif.P_LEGENDE -> NovaStatsApp.CHANNEL_PANTHEON_LEGENDE
        SettingsRepository.Notif.P_MYTHIQUE -> NovaStatsApp.CHANNEL_PANTHEON_MYTHIQUE
        SettingsRepository.Notif.HOF -> NovaStatsApp.CHANNEL_HALL_OF_FAME
        else -> NovaStatsApp.CHANNEL_ACHIEVEMENTS
    }

    private fun routeFor(key: String): String = when (key) {
        SettingsRepository.Notif.CERT_SILVER, SettingsRepository.Notif.CERT_GOLD,
        SettingsRepository.Notif.CERT_PLATINUM, SettingsRepository.Notif.CERT_DIAMOND,
        SettingsRepository.Notif.CERT_MULTIPLIERS -> NovaTab.CERTIFICATIONS.route
        SettingsRepository.Notif.P_STAR, SettingsRepository.Notif.P_SUPERSTAR,
        SettingsRepository.Notif.P_MEGASTAR, SettingsRepository.Notif.P_LEGENDE,
        SettingsRepository.Notif.P_MYTHIQUE -> NovaTab.PANTHEON.route
        SettingsRepository.Notif.HOF -> NovaTab.HALL_OF_FAME.route
        else -> NovaTab.HOME.route
    }

    private fun colorFor(key: String): Int = when (key) {
        SettingsRepository.Notif.HOF -> 0xFF00D4FF.toInt()
        SettingsRepository.Notif.P_STAR, SettingsRepository.Notif.P_SUPERSTAR,
        SettingsRepository.Notif.P_MEGASTAR, SettingsRepository.Notif.P_LEGENDE,
        SettingsRepository.Notif.P_MYTHIQUE -> 0xFFB68CFF.toInt()
        else -> 0xFFFFD166.toInt()
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
            Triple("$label — nouvelle certification", "$kind « ${a.name} » vient d'atteindre le palier $label !", key)
        }
        "PANTHEON" -> {
            val status = PantheonStatus.fromDb(a.level) ?: return null
            val key = when (status) {
                PantheonStatus.STAR -> SettingsRepository.Notif.P_STAR
                PantheonStatus.SUPERSTAR -> SettingsRepository.Notif.P_SUPERSTAR
                PantheonStatus.MEGASTAR -> SettingsRepository.Notif.P_MEGASTAR
                PantheonStatus.LEGENDE -> SettingsRepository.Notif.P_LEGENDE
                PantheonStatus.MYTHIQUE -> SettingsRepository.Notif.P_MYTHIQUE
            }
            Triple("${status.emoji} ${a.name} entre au Panthéon", "Nouveau statut : ${status.label.uppercase()} — ta fidélité est récompensée.", key)
        }
        "HALL_OF_FAME" -> Triple(
            "🏛️ Hall of Fame — nouvelle intronisation",
            "« ${a.name} » rejoint le Hall of Fame · ${a.level.lowercase().replace('_', ' ')}.",
            SettingsRepository.Notif.HOF
        )
        else -> null
    }
}
