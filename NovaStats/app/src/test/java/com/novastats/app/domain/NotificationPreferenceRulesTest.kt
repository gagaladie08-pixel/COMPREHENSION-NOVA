package com.novastats.app.domain

import com.novastats.app.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferenceRulesTest {

    private val categories = SettingsRepository.Notif.ALL

    @Test
    fun disablingAllAddsEveryCategoryInOnePureUpdate() {
        val disabled = NotificationPreferenceRules.setAllEnabled(
            disabled = setOf("LEGACY_CATEGORY"),
            categories = categories,
            enabled = false
        )

        assertEquals(categories.toSet() + "LEGACY_CATEGORY", disabled)
        assertFalse(NotificationPreferenceRules.areAllEnabled(disabled, categories))
    }

    @Test
    fun enablingAllClearsCurrentCategoriesButKeepsUnknownPreferences() {
        val disabled = NotificationPreferenceRules.setAllEnabled(
            disabled = categories.toSet() + "LEGACY_CATEGORY",
            categories = categories,
            enabled = true
        )

        assertEquals(setOf("LEGACY_CATEGORY"), disabled)
        assertTrue(NotificationPreferenceRules.areAllEnabled(disabled, categories))
    }

    @Test
    fun individualToggleChangesOnlyItsOwnCategory() {
        val before = setOf(SettingsRepository.Notif.REVIEW, SettingsRepository.Notif.WEEKLY_CHARTS)
        val after = NotificationPreferenceRules.setEnabled(before, SettingsRepository.Notif.REVIEW, enabled = true)

        assertEquals(setOf(SettingsRepository.Notif.WEEKLY_CHARTS), after)
    }

    @Test
    fun allToggleStateReflectsTheKnownCategoriesNotObsoleteKeys() {
        assertTrue(NotificationPreferenceRules.areAllEnabled(setOf("LEGACY_CATEGORY"), categories))
        assertFalse(NotificationPreferenceRules.areAllEnabled(setOf(SettingsRepository.Notif.REVIEW), categories))
    }
}
