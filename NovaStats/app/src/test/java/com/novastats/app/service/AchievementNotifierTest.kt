package com.novastats.app.service

import com.novastats.app.data.db.entity.EntityType
import com.novastats.app.data.repository.SettingsRepository
import com.novastats.app.data.repository.StatsRebuilder
import com.novastats.app.domain.PantheonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementNotifierTest {

    @Test
    fun certificationLevelsUseTheirOwnNotificationCategories() {
        val levels = listOf(
            "SILVER" to SettingsRepository.Notif.CERT_SILVER,
            "GOLD" to SettingsRepository.Notif.CERT_GOLD,
            "PLATINUM" to SettingsRepository.Notif.CERT_PLATINUM,
            "DIAMOND" to SettingsRepository.Notif.CERT_DIAMOND
        )

        levels.forEachIndexed { index, (level, expectedCategory) ->
            val description = AchievementNotifier.describe(
                achievement(kind = "CERTIFICATION", entityType = EntityType.TRACK, level = "$level:1", name = "Titre $index")
            )

            assertEquals("Certification $level", expectedCategory, description?.third)
            assertTrue(description?.second.orEmpty().contains("Titre $index"))
        }
    }

    @Test
    fun albumAndMultiplierCertificationsKeepTheirSpecificLabelsAndCategory() {
        val album = AchievementNotifier.describe(
            achievement(kind = "CERTIFICATION", entityType = EntityType.ALBUM, level = "GOLD:1", name = "Album test")
        )
        val multiplier = AchievementNotifier.describe(
            achievement(kind = "CERTIFICATION", entityType = EntityType.TRACK, level = "DIAMOND:3", name = "Titre test")
        )

        assertTrue(album?.second.orEmpty().startsWith("Album « Album test »"))
        assertEquals(SettingsRepository.Notif.CERT_MULTIPLIERS, multiplier?.third)
        assertTrue(multiplier?.first.orEmpty().contains("3x"))
    }

    @Test
    fun everyPantheonStatusUsesItsMatchingCategory() {
        val expectedCategories = listOf(
            SettingsRepository.Notif.P_STAR,
            SettingsRepository.Notif.P_SUPERSTAR,
            SettingsRepository.Notif.P_MEGASTAR,
            SettingsRepository.Notif.P_LEGENDE,
            SettingsRepository.Notif.P_MYTHIQUE
        )

        val actualCategories = PantheonStatus.entries.map { status ->
            AchievementNotifier.describe(
                achievement(kind = "PANTHEON", level = status.dbName, name = "Artiste test")
            )?.third
        }

        assertEquals(expectedCategories, actualCategories)
    }

    @Test
    fun hallOfFameUsesItsCategoryAndUnknownEventsAreIgnored() {
        val hallOfFame = AchievementNotifier.describe(
            achievement(kind = "HALL_OF_FAME", level = "GLOBAL_RECORD", name = "Titre historique")
        )

        assertEquals(SettingsRepository.Notif.HOF, hallOfFame?.third)
        assertNull(AchievementNotifier.describe(achievement(kind = "UNKNOWN", level = "x", name = "?")))
        assertNull(AchievementNotifier.describe(achievement(kind = "PANTHEON", level = "NOT_A_STATUS", name = "?")))
    }

    @Test
    fun settingsExposeExactlyFifteenUniqueNotificationCategories() {
        assertEquals(15, SettingsRepository.Notif.ALL.size)
        assertEquals(SettingsRepository.Notif.ALL.size, SettingsRepository.Notif.ALL.toSet().size)
    }

    private fun achievement(
        kind: String,
        entityType: String = EntityType.ARTIST,
        level: String,
        name: String
    ) = StatsRebuilder.Achievement(
        kind = kind,
        entityType = entityType,
        entityId = 42L,
        level = level,
        name = name
    )
}
