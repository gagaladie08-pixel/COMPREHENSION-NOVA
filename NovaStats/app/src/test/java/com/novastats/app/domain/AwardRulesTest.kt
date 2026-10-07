package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AwardRulesTest {
    @Test
    fun `streak le plus long`() {
        val (n, end) = AwardRules.longestStreak(listOf("2026-01-01", "2026-01-02", "2026-01-04", "2026-01-05", "2026-01-06", "2026-01-09"))
        assertEquals(3, n)
        assertEquals("2026-01-06", end)
        assertEquals(0 to null, AwardRules.longestStreak(emptyList()))
    }

    @Test
    fun `déblocage après 2 mois`() {
        val first = LocalDate.of(2026, 4, 28)
        assertFalse(AwardRules.isUnlocked(first, LocalDate.of(2026, 6, 26)))
        assertTrue(AwardRules.isUnlocked(first, LocalDate.of(2026, 6, 27)))
        assertFalse(AwardRules.isUnlocked(null, LocalDate.of(2026, 6, 27)))
    }

    @Test
    fun `score de progression`() {
        assertEquals(0.0, AwardRules.riseScore(3, 0), 0.0)
        assertEquals(40.0, AwardRules.riseScore(20, 0), 0.0)
        assertEquals(2.5, AwardRules.riseScore(25, 10), 0.0)
    }
}
