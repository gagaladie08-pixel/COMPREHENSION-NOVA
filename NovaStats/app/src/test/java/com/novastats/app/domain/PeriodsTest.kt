package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PeriodsTest {

    @Test
    fun `semaine ISO lundi-dimanche`() {
        val r = Dates.weekOf(LocalDate.of(2026, 9, 30)) // mercredi
        assertEquals(LocalDate.of(2026, 9, 28), r.from)
        assertEquals(LocalDate.of(2026, 10, 4), r.to)
    }

    @Test
    fun `stats weekly = semaine calendaire en cours (lundi vers ancre), billboard weekly = semaine ISO`() {
        val anchor = LocalDate.of(2026, 9, 30)
        val stats = Dates.statsRangeFor(Period.WEEKLY, anchor)
        assertEquals(LocalDate.of(2026, 9, 28), stats.from)
        assertEquals(anchor, stats.to)
        assertEquals(LocalDate.of(2026, 9, 28), Dates.rangeFor(Period.WEEKLY, anchor).from)
        assertEquals(anchor, Dates.statsRangeFor(Period.DAILY, anchor).from)
        assertEquals("2026-09-01", Dates.statsRangeFor(Period.MONTHLY, anchor).fromIso)
        assertEquals("2026-01-01", Dates.statsRangeFor(Period.YEARLY, anchor).fromIso)
    }

    @Test
    fun `mois et annee calendaires`() {
        assertEquals("2026-02-01", Dates.monthOf(LocalDate.of(2026, 2, 14)).fromIso)
        assertEquals("2026-02-28", Dates.monthOf(LocalDate.of(2026, 2, 14)).toIso)
        assertEquals("2026-12-31", Dates.yearOf(LocalDate.of(2026, 2, 14)).toIso)
    }

    @Test
    fun `streak courant et record`() {
        val today = LocalDate.of(2026, 9, 30)
        val dates = listOf(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4), // 4 jours
            LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30) // 3 jours (en cours)
        )
        val r = StreakCalculator.compute(dates, today)
        assertEquals(3, r.current)
        assertEquals(4, r.best)
        assertEquals(LocalDate.of(2026, 9, 4), r.bestEndDate)
    }

    @Test
    fun `streak casse si derniere ecoute avant hier`() {
        val today = LocalDate.of(2026, 9, 30)
        val r = StreakCalculator.compute(listOf(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 28)), today)
        assertEquals(0, r.current)
        assertEquals(2, r.best)
    }

    @Test
    fun `limites billboard`() {
        assertEquals(75, BillboardLimits.hot100(Period.DAILY))
        assertEquals(100, BillboardLimits.hot100(Period.WEEKLY))
        assertEquals(25, BillboardLimits.artist50(Period.DAILY))
        assertEquals(50, BillboardLimits.albums75(Period.DAILY))
        assertEquals(75, BillboardLimits.albums75(Period.GLOBAL))
    }
}
