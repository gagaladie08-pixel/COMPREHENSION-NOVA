package com.novastats.app.ui.screens

import com.novastats.app.data.repository.YearEndRow
import com.novastats.app.domain.Chart
import com.novastats.app.domain.YearEndRules
import org.junit.Assert.assertTrue
import org.junit.Test

class YearEndStoryTest {

    @Test
    fun `recurrence story names the exact local trigger and preserved achievement`() {
        val row = YearEndRow(
            id = 1L,
            name = "Lueur",
            subtitle = "Nova",
            imageUrl = null,
            points = 1_420,
            plays = 180,
            durationMs = 900_000L,
            weeks = 20,
            peak = 1,
            weeksAt1 = 2,
            recurrent = true,
            recurrentThreshold = YearEndRules.RecurrentThreshold(20, 50),
            recurrentAtPosition = 51,
            recurrentAfterWeeks = 20
        )
        val entry = YearEndPopupEntry(Chart.HOT_100, 2, row, "2025", 35)

        val story = yearEndStory(entry)

        assertTrue(story.anecdote.contains("rang #51"))
        assertTrue(story.anecdote.contains("rang protégé #50"))
        assertTrue(story.achievement.contains("20 semaines"))
        assertTrue(story.achievement.contains("Top 50"))
        assertTrue(story.achievement.contains("#2"))
    }

    @Test
    fun `artist story mentions its weekly track peak and correct chart`() {
        val row = YearEndRow(
            id = 2L,
            name = "Étoile",
            subtitle = "6 titres",
            imageUrl = null,
            points = 540,
            plays = 90,
            durationMs = 500_000L,
            weeks = 7,
            peak = 3,
            weeksAt1 = 0,
            extra = 6
        )
        val entry = YearEndPopupEntry(Chart.ARTIST_50, 4, row, "2025", 35)

        val story = yearEndStory(entry)

        assertTrue(story.anecdote.contains("6 titres distincts"))
        assertTrue(story.anecdote.contains("Artist 50"))
        assertTrue(story.achievement.contains("Top 5"))
        assertTrue(story.achievement.contains("#4"))
    }

    @Test
    fun `album exploit uses albums chart weekly number one value`() {
        val row = YearEndRow(
            id = 3L,
            name = "L'Après",
            subtitle = "Artistes variés",
            imageUrl = null,
            points = 920,
            plays = 120,
            durationMs = 700_000L,
            weeks = 12,
            peak = 1,
            weeksAt1 = 1
        )
        val entry = YearEndPopupEntry(Chart.ALBUMS_75, 1, row, "2025", 35)

        val story = yearEndStory(entry)

        assertTrue(story.achievement.contains("Albums 75"))
        assertTrue(story.achievement.contains("75 points"))
        assertTrue(story.achievement.contains("rang annuel #1"))
    }
}
