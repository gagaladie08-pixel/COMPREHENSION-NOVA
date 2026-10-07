package com.novastats.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import com.novastats.app.data.api.MetadataEnricher
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.repository.AwardsEngine
import com.novastats.app.data.repository.BillboardEngine
import com.novastats.app.data.repository.DataEditorManager
import com.novastats.app.data.repository.LibraryRepository
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.data.repository.StatsRebuilder
import com.novastats.app.service.BackupWorker
import com.novastats.app.service.EnrichmentWorker
import com.novastats.app.service.NovaAwardsUnlockWorker
import com.novastats.app.service.WeeklyChartsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Point d'entrée : conteneur de dépendances minimal (pas de Hilt pour l'instant — simple et lisible). */
class NovaStatsApp : Application() {

    val database: NovaDatabase by lazy { NovaDatabase.get(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val library: LibraryRepository by lazy { LibraryRepository(database) }
    val rebuilder: StatsRebuilder by lazy { StatsRebuilder(database, library) }
    val billboard: BillboardEngine by lazy { BillboardEngine(database) }
    val recordExplainer: com.novastats.app.data.repository.RecordExplainer by lazy { com.novastats.app.data.repository.RecordExplainer(database) }
    val enricher: MetadataEnricher by lazy {
        MetadataEnricher(database, settings).also { enricher ->
            enricher.onTrackFlagged = { trackId, title, artist, proposal, score ->
                com.novastats.app.service.ReviewNotifier.notifyFlagged(this@NovaStatsApp, trackId, title, artist, proposal, score)
            }
        }
    }
    val editor: DataEditorManager by lazy { DataEditorManager(database, rebuilder, library) }
    val awards: AwardsEngine by lazy { AwardsEngine(database) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Tâches courtes liées à la durée de vie du processus (la file WorkManager prend ensuite le relais). */
    fun launchIoTask(block: suspend CoroutineScope.() -> Unit) = appScope.launch(Dispatchers.IO, block = block)

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.novastats.app.util.CrashJournal.install(this)
        // 🔎 Diagnostic « l'app se vide » : comptage des écoutes au lancement, puis autour de chaque recalcul.
        com.novastats.app.util.RebuildAudit.install(this)
        createNotificationChannels()
        com.novastats.app.data.api.EnrichmentState.attach(this)
        EnrichmentWorker.schedulePeriodic(this)
        BackupWorker.schedulePeriodic(this)
        WeeklyChartsWorker.schedule(this)
        appScope.launch(Dispatchers.IO) {
            runCatching { database.scrobbleDao().firstScrobbleAt().first() }.getOrNull()
                ?.let { firstPlay -> NovaAwardsUnlockWorker.schedule(this@NovaStatsApp, firstPlay) }
        }
        // ✨ 16ᵉ thème : celui créé par l'utilisateur (null s'il n'en a pas encore)
        com.novastats.app.ui.theme.NovaThemes.customTheme = com.novastats.app.ui.theme.CustomThemeStore.load(this)?.toTheme()
        com.novastats.app.service.DetectionState.bind(this)
        // 🩹 Un recalcul tué en route laissait les classements vides jusqu'à la prochaine écoute.
        // Après bind() : le journal persisté est alors déjà rechargé en mémoire.
        appScope.launch(Dispatchers.IO) {
            runCatching { com.novastats.app.util.StartupRepair.run(this@NovaStatsApp) }
        }
        com.novastats.app.service.Watchdog.schedule(this)
        com.novastats.app.ui.theme.IconSwitcher.install(this)
        appScope.launch { runCatching { com.novastats.app.data.repository.RelinkJob.ensure(this@NovaStatsApp) } }
        appScope.launch {
            kotlinx.coroutines.flow.combine(settings.themeId, settings.dynamicIcon) { id, dyn ->
                if (dyn) id else com.novastats.app.ui.theme.NovaThemes.DEFAULT.id
            }.collect { id -> com.novastats.app.ui.theme.IconSwitcher.request(id) }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannelGroup(NotificationChannelGroup(CHANNEL_GROUP_NOVA, "NovaStats · Récompenses & actus"))
        val audio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun sound(raw: String): Uri = Uri.parse("android.resource://$packageName/raw/$raw")
        fun premiumChannel(
            id: String,
            name: String,
            description: String,
            importance: Int,
            soundName: String,
            vibration: LongArray
        ) {
            manager.createNotificationChannel(NotificationChannel(id, name, importance).apply {
                group = CHANNEL_GROUP_NOVA
                this.description = description
                setSound(sound(soundName), audio)
                enableVibration(vibration.isNotEmpty())
                if (vibration.isNotEmpty()) vibrationPattern = vibration
                enableLights(true)
                lightColor = 0xFF00D4FF.toInt()
            })
        }

        // Remplace les anciens canaux partagés : Android fige leur son dès la première création.
        listOf(CHANNEL_CERT_LIGHT, CHANNEL_CERT_MID, CHANNEL_CERT_EPIC).forEach(manager::deleteNotificationChannel)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Détection musicale", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Statut permanent du service de détection NovaStats"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW, "🟡 À vérifier", NotificationManager.IMPORTANCE_LOW).apply {
                group = CHANNEL_GROUP_NOVA
                description = "Correspondance musicale à confirmer dans l'Éditeur"
                setSound(null, null)
                enableVibration(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACHIEVEMENTS, "Récompenses Nova · récapitulatif", NotificationManager.IMPORTANCE_DEFAULT).apply {
                group = CHANNEL_GROUP_NOVA
                description = "Récapitulatif silencieux des nouveautés obtenues pendant une écoute"
                setSound(null, null)
                enableVibration(false)
            }
        )
        premiumChannel(CHANNEL_CERT_SILVER, "Certification · Argent", "Petit carillon pour un nouveau palier Argent", NotificationManager.IMPORTANCE_DEFAULT, "cert_silver", longArrayOf())
        premiumChannel(CHANNEL_CERT_GOLD, "Certification · Or", "Carillon doré pour un nouveau palier Or", NotificationManager.IMPORTANCE_DEFAULT, "cert_gold", longArrayOf(0, 75))
        premiumChannel(CHANNEL_CERT_PLATINUM, "Certification · Platine", "Accord lumineux pour un nouveau palier Platine", NotificationManager.IMPORTANCE_DEFAULT, "cert_platinum", longArrayOf(0, 90, 55, 90))
        premiumChannel(CHANNEL_CERT_DIAMOND, "Certification · Diamant", "Fanfare cristalline et vibration pour Diamant", NotificationManager.IMPORTANCE_HIGH, "cert_diamond", longArrayOf(0, 130, 60, 130))
        premiumChannel(CHANNEL_CERT_MULTIPLIERS, "Certification · Multiplicateurs", "Signature ascendante pour chaque nouveau multiplicateur Diamant", NotificationManager.IMPORTANCE_HIGH, "cert_multiplier", longArrayOf(0, 150, 70, 150, 70, 220))

        premiumChannel(CHANNEL_PANTHEON_STAR, "Panthéon · Star", "Carillon léger — entrée au rang Star", NotificationManager.IMPORTANCE_DEFAULT, "pantheon_star", longArrayOf(0, 70))
        premiumChannel(CHANNEL_PANTHEON_SUPERSTAR, "Panthéon · Superstar", "Motif ascendant — entrée au rang Superstar", NotificationManager.IMPORTANCE_DEFAULT, "pantheon_superstar", longArrayOf(0, 90, 55, 90))
        premiumChannel(CHANNEL_PANTHEON_MEGASTAR, "Panthéon · Megastar", "Accord ample — entrée au rang Megastar", NotificationManager.IMPORTANCE_HIGH, "pantheon_megastar", longArrayOf(0, 120, 60, 120))
        premiumChannel(CHANNEL_PANTHEON_LEGENDE, "Panthéon · Légende", "Fanfare royale — entrée au rang Légende", NotificationManager.IMPORTANCE_HIGH, "pantheon_legende", longArrayOf(0, 130, 70, 130, 70, 180))
        premiumChannel(CHANNEL_PANTHEON_MYTHIQUE, "Panthéon · Mythique", "Carillon holographique — entrée au rang Mythique", NotificationManager.IMPORTANCE_HIGH, "pantheon_mythique", longArrayOf(0, 190, 80, 190, 80, 360))
        premiumChannel(CHANNEL_HALL_OF_FAME, "Hall of Fame", "Intronisations et nouveaux records historiques", NotificationManager.IMPORTANCE_HIGH, "hall_of_fame", longArrayOf(0, 120, 60, 120))
        premiumChannel(CHANNEL_FIRST_SCROBBLE, "Première écoute", "Petit jingle de bienvenue dans NovaStats", NotificationManager.IMPORTANCE_DEFAULT, "first_scrobble", longArrayOf(0, 80))
        premiumChannel(CHANNEL_WEEKLY_CHARTS, "Charts hebdomadaires", "Résumé local des trois charts de la semaine écoulée", NotificationManager.IMPORTANCE_DEFAULT, "weekly_charts", longArrayOf(0, 80, 55, 80))
        premiumChannel(CHANNEL_AWARDS_UNLOCK, "Nova Awards", "Ouverture de ton palmarès annuel", NotificationManager.IMPORTANCE_HIGH, "awards_unlock", longArrayOf(0, 130, 70, 130, 70, 260))
    }

    companion object {
        const val CHANNEL_GROUP_NOVA = "nova_notifications"
        const val CHANNEL_SERVICE = "nova_service"
        const val CHANNEL_REVIEW = "nova_review"
        const val CHANNEL_ACHIEVEMENTS = "nova_achievements_v2"
        const val CHANNEL_WEEKLY_CHARTS = "nova_weekly_charts_v2"
        const val CHANNEL_CERT_LIGHT = "nova_cert_light"
        const val CHANNEL_CERT_MID = "nova_cert_mid"
        const val CHANNEL_CERT_EPIC = "nova_cert_epic"
        const val CHANNEL_CERT_SILVER = "nova_cert_silver_v2"
        const val CHANNEL_CERT_GOLD = "nova_cert_gold_v2"
        const val CHANNEL_CERT_PLATINUM = "nova_cert_platinum_v2"
        const val CHANNEL_CERT_DIAMOND = "nova_cert_diamond_v2"
        const val CHANNEL_CERT_MULTIPLIERS = "nova_cert_multipliers_v2"
        const val CHANNEL_PANTHEON_STAR = "nova_pantheon_star_v2"
        const val CHANNEL_PANTHEON_SUPERSTAR = "nova_pantheon_superstar_v2"
        const val CHANNEL_PANTHEON_MEGASTAR = "nova_pantheon_megastar_v2"
        const val CHANNEL_PANTHEON_LEGENDE = "nova_pantheon_legende_v2"
        const val CHANNEL_PANTHEON_MYTHIQUE = "nova_pantheon_mythique_v2"
        const val CHANNEL_HALL_OF_FAME = "nova_hall_of_fame_v2"
        const val CHANNEL_FIRST_SCROBBLE = "nova_first_scrobble_v2"
        const val CHANNEL_AWARDS_UNLOCK = "nova_awards_unlock_v2"
        lateinit var instance: NovaStatsApp
            private set
    }
}
