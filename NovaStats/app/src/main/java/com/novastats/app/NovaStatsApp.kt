package com.novastats.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.novastats.app.data.api.MetadataEnricher
import com.novastats.app.data.db.NovaDatabase
import com.novastats.app.data.repository.BillboardEngine
import com.novastats.app.data.repository.LibraryRepository
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.data.repository.StatsRebuilder
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

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        EnrichmentWorker.schedulePeriodic(this)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Détection musicale", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Statut du service de détection NovaStats"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACHIEVEMENTS, "Certifications & Panthéon", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Nouvelles certifications, statuts Panthéon, Hall of Fame"
            }
        )
    }

    companion object {
        const val CHANNEL_SERVICE = "nova_service"
        const val CHANNEL_ACHIEVEMENTS = "nova_achievements"
        lateinit var instance: NovaStatsApp
            private set
    }
}
