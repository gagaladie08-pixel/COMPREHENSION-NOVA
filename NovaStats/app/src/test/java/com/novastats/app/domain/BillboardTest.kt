package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BillboardTest {

    private val d = { s: String -> LocalDate.parse(s) }

    @Test
    fun `limites par chart et période`() {
        assertEquals(75, Chart.HOT_100.limit(Period.DAILY)); assertEquals(100, Chart.HOT_100.limit(Period.WEEKLY))
        assertEquals(25, Chart.ARTIST_50.limit(Period.DAILY)); assertEquals(50, Chart.ARTIST_50.limit(Period.GLOBAL))
        assertEquals(50, Chart.ALBUMS_75.limit(Period.DAILY)); assertEquals(75, Chart.ALBUMS_75.limit(Period.MONTHLY))
    }

    @Test
    fun `ancres et intervalles`() {
        val wed = d("2026-09-30") // mercredi
        assertEquals(wed, BillboardDates.anchor(Period.DAILY, wed))
        assertEquals(d("2026-09-28"), BillboardDates.anchor(Period.WEEKLY, wed))
        assertEquals(d("2026-09-28"), BillboardDates.anchor(Period.GLOBAL, wed))
        assertEquals(d("2026-09-01"), BillboardDates.anchor(Period.MONTHLY, wed))
        assertEquals(d("2026-01-01"), BillboardDates.anchor(Period.YEARLY, wed))

        assertEquals(DateRange(d("2026-09-28"), d("2026-10-04")), BillboardDates.range(Period.WEEKLY, d("2026-09-28"), wed))
        // Global : depuis toujours jusqu'à aujourd'hui (semaine en cours) ou fin de semaine (semaine passée)
        assertEquals(DateRange(d("1970-01-01"), wed), BillboardDates.range(Period.GLOBAL, d("2026-09-28"), wed))
        assertEquals(DateRange(d("1970-01-01"), d("2026-09-27")), BillboardDates.range(Period.GLOBAL, d("2026-09-21"), wed))
    }

    @Test
    fun `dernière période publiée et close`() {
        val today = d("2026-09-30")
        // La période en cours n'est jamais publiée : la dernière est la semaine / le jour / le mois précédent
        assertEquals(d("2026-09-21"), BillboardDates.latest(Period.WEEKLY, today))
        assertEquals(d("2026-09-29"), BillboardDates.latest(Period.DAILY, today))
        assertEquals(d("2026-08-01"), BillboardDates.latest(Period.MONTHLY, today))
        assertEquals(d("2025-01-01"), BillboardDates.latest(Period.YEARLY, today))
        assertTrue(BillboardDates.isCurrent(Period.WEEKLY, d("2026-09-21"), today))
        assertFalse(BillboardDates.isCurrent(Period.WEEKLY, d("2026-09-28"), today))
        assertFalse(BillboardDates.isClosed(Period.WEEKLY, d("2026-09-28"), today))
        assertTrue(BillboardDates.isClosed(Period.WEEKLY, d("2026-09-21"), today))
        assertTrue(BillboardDates.isClosed(Period.DAILY, d("2026-09-29"), today))
        assertFalse(BillboardDates.isClosed(Period.DAILY, today, today))
    }

    @Test
    fun `toutes les ancres`() {
        val anchors = BillboardDates.allAnchors(Period.WEEKLY, d("2026-09-10"), d("2026-09-30"))
        assertEquals(listOf(d("2026-09-07"), d("2026-09-14"), d("2026-09-21")), anchors)
        assertEquals(2, BillboardDates.allAnchors(Period.MONTHLY, d("2026-07-15"), d("2026-09-30")).size)
        assertTrue(BillboardDates.allAnchors(Period.MONTHLY, d("2026-09-15"), d("2026-09-30")).isEmpty())
    }

    @Test
    fun `mouvements`() {
        assertEquals(Movement.New, Movement.of(isNew = true, isReentry = false, previousPosition = null, position = 1))
        assertEquals(Movement.Reentry, Movement.of(false, true, null, 14))
        assertEquals(Movement.Same, Movement.of(false, false, 5, 5))
        assertEquals(Movement.Up(4), Movement.of(false, false, 9, 5))
        assertEquals(Movement.Down(3), Movement.of(false, false, 2, 5))
        assertEquals("▼3", Movement.Down(3).label())
    }

    @Test
    fun `stats d'historique avec rupture`() {
        val anchors = BillboardDates.allAnchors(Period.WEEKLY, d("2026-08-03"), d("2026-09-30"))
        val app = listOf(
            ChartAppearance(d("2026-08-03"), 12, 10),
            ChartAppearance(d("2026-08-10"), 1, 30),
            ChartAppearance(d("2026-08-17"), 1, 25),
            // absente le 24/08 → sortie puis retour
            ChartAppearance(d("2026-08-31"), 1, 40),
            ChartAppearance(d("2026-09-07"), 4, 20)
        )
        val s = ChartHistory.stats(app, anchors)
        assertEquals(5, s.periodsInChart)
        assertEquals(12, s.firstEntry!!.position)
        assertEquals(d("2026-08-10"), s.peak!!.date)
        assertEquals(3, s.timesAtPeak)
        assertEquals(2, s.longestRunAt1)      // 10/08 + 17/08 (le 31/08 est isolé)
        assertEquals(2, s.longestRunTop10)
        assertEquals(0, s.currentRunAt1)      // dernière apparition #4
        assertEquals(40, s.maxPlays)

        val onlyOnes = ChartHistory.stats(app.take(3), anchors)
        assertEquals(2, onlyOnes.currentRunAt1)
    }

    @Test
    fun `règles hall of fame`() {
        assertTrue(HallOfFameRules.isDirectDebut(Period.WEEKLY, 1, true))
        assertFalse(HallOfFameRules.isDirectDebut(Period.DAILY, 1, true))
        assertFalse(HallOfFameRules.isDirectDebut(Period.WEEKLY, 2, true))
        assertTrue(HallOfFameRules.isLongRun(Period.WEEKLY, 3))
        assertFalse(HallOfFameRules.isLongRun(Period.WEEKLY, 2))
        assertTrue(HallOfFameRules.isLongRun(Period.MONTHLY, 2))
        assertTrue(HallOfFameRules.isLegendaryRun(Period.WEEKLY, 10))
        assertFalse(HallOfFameRules.isLegendaryRun(Period.DAILY, 10))
    }
}
