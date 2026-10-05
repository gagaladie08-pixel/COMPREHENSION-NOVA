package com.novastats.app.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyChartsTest {

    private val zone = ZoneId.of("Africa/Ouagadougou")

    @Test
    fun mondayMorningSchedulesForThatMonday() {
        val now = ZonedDateTime.of(2026, 10, 5, 8, 30, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone),
            WeeklyChartsSchedule.nextRun(now)
        )
    }

    @Test
    fun mondayAtOrAfterNineSchedulesForNextWeek() {
        val now = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 10, 12, 9, 0, 0, 0, zone),
            WeeklyChartsSchedule.nextRun(now)
        )
    }

    @Test
    fun delayedWorkerSkipsMissedWeeksInsteadOfCatchingUpInARow() {
        val next = WeeklyChartsSchedule.nextMondayAfter(
            currentMonday = LocalDate.of(2026, 10, 5),
            now = ZonedDateTime.of(2026, 10, 20, 11, 0, 0, 0, zone)
        )

        assertEquals(LocalDate.of(2026, 10, 26), next)
    }

    @Test
    fun previousClosedWeekIsMondayThroughSunday() {
        val range = WeeklyChartsSchedule.previousClosedWeek(LocalDate.of(2026, 10, 12))

        assertEquals(LocalDate.of(2026, 10, 5), range.from)
        assertEquals(LocalDate.of(2026, 10, 11), range.to)
    }

    @Test
    fun digestNamesLeadersAndComparesThemToThePreviousWeek() {
        val range = DateRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11))
        val digest = WeeklyChartsDigestBuilder.build(
            range,
            listOf(
                WeeklyChartHighlight(Chart.HOT_100, 10, "Titre phare", 1, previousLeaderId = 10),
                WeeklyChartHighlight(Chart.ARTIST_50, 20, "Artiste vedette", 7, previousLeaderId = 21),
                WeeklyChartHighlight(Chart.ALBUMS_75, 30, "Album vedette", 3, previousLeaderId = null)
            )
        )

        assertNotNull(digest)
        assertEquals("Cette semaine dans tes charts", digest!!.title)
        assertTrue(digest.summary.contains("Titre phare"))
        assertTrue(digest.body.contains("conserve la tête"))
        assertTrue(digest.body.contains("prend la tête"))
        assertTrue(digest.body.contains("Album vedette"))
        assertTrue(digest.body.contains("Du 5 oct. au 11 oct."))
    }

    @Test
    fun digestIsOmittedWhenThereWereNoChartLeaders() {
        val digest = WeeklyChartsDigestBuilder.build(
            DateRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11)),
            listOf(WeeklyChartHighlight(Chart.HOT_100, 1, "  ", 0, previousLeaderId = null))
        )

        assertNull(digest)
    }
}
