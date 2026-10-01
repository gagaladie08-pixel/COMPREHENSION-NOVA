package com.novastats.app

import android.app.Application
import android.app.NotificationChannel
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

/**
 * Point d'entrée : conteneur de dépendances minimal (pas de Hilt pour l'instant — simple et lisible).
 */
class NovaStatsApp : Application() {

    val database: NovaDatabase by lazy { NovaDatabase.get(this) }
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
    val library: LibraryRepository by lazy { LibraryRepository(database) }
    val rebuilder: StatsRebuilder by lazy { StatsRebuilder(database) }
    val billboard: BillboardEngine by lazy { BillboardEngine(database) }
    val enricher: MetadataEnricher by lazy { MetadataEnricher(database, settings) }
    val editor: DataEditorManager by lazy { DataEditorManager(database, rebuilder) }
    val awards: AwardsEngine by lazy { AwardsEngine(database) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        EnrichmentWorker.schedulePeriodic(this)
        BackupWorker.schedulePeriodic(this)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Détection musicale", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Statut du service de détection NovaStats"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACHIEVEMENTS, "Panthéon & Hall of Fame", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Statuts Panthéon, Hall of Fame, Nova Awards"
            }
        )
        // Sons distincts par palier de certification : léger (Argent/Or) → moyen (Platine) → épique + vibration (Diamant)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        fun sound(raw: String): Uri = Uri.parse("android.resource://$packageName/raw/$raw")
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CERT_LIGHT, "Certifications 🥉 Argent · 🥈 Or", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Son léger"; setSound(sound("cert_light"), attrs); enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CERT_MID, "Certifications 🥇 Platine", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Son intermédiaire"; setSound(sound("cert_mid"), attrs); enableVibration(true); vibrationPattern = longArrayOf(0, 120)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CERT_EPIC, "Certifications 💎 Diamant & multiplicateurs", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Son épique + vibration"; setSound(sound("cert_epic"), attrs); enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 80, 150, 80, 400); enableLights(true); lightColor = 0xFF00FFFF.toInt()
            }
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "nova_service"
        const val CHANNEL_ACHIEVEMENTS = "nova_achievements"
        const val CHANNEL_CERT_LIGHT = "nova_cert_light"
        const val CHANNEL_CERT_MID = "nova_cert_mid"
        const val CHANNEL_CERT_EPIC = "nova_cert_epic"
        lateinit var instance: NovaStatsApp
            private set
    }
}
