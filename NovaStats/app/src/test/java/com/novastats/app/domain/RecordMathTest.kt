package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordMathTest {

    private fun s(vararg p: Pair<Int, Int>) = p.map { (idx, pos) -> ChartAppearance(idx, "2026-01-%02d".format(idx + 1), pos, 10) }

    @Test
    fun `catalogue expose les 24 records`() {
        assertEquals(24, RecordCatalog.ALL.map { it.number }.distinct().size)
        assertTrue(RecordCatalog.ALL.all { it.categories.isNotEmpty() })
        // sous-sections uniquement pour des catégories proposées
        RecordCatalog.ALL.forEach { d -> d.subs.keys.forEach { c -> assertTrue("${d.id}/$c", c in d.categories) } }
    }

    @Test
    fun `cumulative et top10`() {
        val series = s(0 to 5, 1 to 12, 2 to 1, 3 to 1)
        assertEquals(4, RecordMath.cumulative(series))
        assertEquals(3, RecordMath.cumulative(series, 10))
        assertEquals(2, RecordMath.timesAt1(series))
    }

    @Test
    fun `plus longue serie consecutive`() {
        val series = s(0 to 3, 1 to 4, 3 to 2, 4 to 6, 5 to 5, 6 to 9)
        assertEquals(4 to "2026-01-07", RecordMath.longestStreak(series, 10))
        assertEquals(3 to "2026-01-06", RecordMath.longestStreak(series, 5))
    }

    @Test
    fun `comeback apres absence`() {
        val series = s(0 to 40, 1 to 45, 10 to 3)
        val r = RecordMath.biggestComeback(series, minAbsence = 5)
        assertNotNull(r); assertEquals(42.0, r!!.value, 0.0)
        assertNull(RecordMath.biggestComeback(series, minAbsence = 20))
    }

    @Test
    fun `saut et chute`() {
        val series = s(0 to 30, 1 to 5, 2 to 28)
        assertEquals(25.0, RecordMath.biggestMove(series, jump = true)!!.value, 0.0)
        assertEquals(23.0, RecordMath.biggestMove(series, jump = false)!!.value, 0.0)
    }

    @Test
    fun `sleeper hit et climber`() {
        val series = s(0 to 45, 1 to 40, 2 to 30, 3 to 8)
        assertEquals(37.0, RecordMath.sleeperHit(series)!!.value, 0.0)
        assertEquals(37.0, RecordMath.biggestClimber(series)!!.value, 0.0)
        assertNull(RecordMath.sleeperHit(s(0 to 10, 1 to 2)))
    }

    @Test
    fun `bloque au top 5 sans numero 1`() {
        assertEquals(3 to 2, RecordMath.blockedTop5(s(0 to 2, 1 to 3, 2 to 4, 3 to 9)))
        assertNull(RecordMath.blockedTop5(s(0 to 2, 1 to 1)))
    }

    @Test
    fun `periodes pour atteindre`() {
        assertEquals(2 to "2026-01-03", RecordMath.periodsToReach(s(0 to 9, 1 to 4, 2 to 1), 1))
        assertNull(RecordMath.periodsToReach(s(0 to 9), 1))
    }

    @Test
    fun `numeros 1 successifs avec titres differents`() {
        val artist = setOf(7L)
        val ones = listOf(
            RecordMath.NumberOne(0, "2026-01-01", 100, artist),
            RecordMath.NumberOne(1, "2026-01-02", 101, artist),
            RecordMath.NumberOne(2, "2026-01-03", 101, artist),
            RecordMath.NumberOne(3, "2026-01-04", 200, setOf(8L)),
            RecordMath.NumberOne(4, "2026-01-05", 102, artist)
        )
        val r = RecordMath.successiveNumberOnes(ones)
        assertEquals(2.0, r.getValue(7L).value, 0.0)
        assertEquals(1.0, r.getValue(8L).value, 0.0)
    }

    @Test
    fun `duree adaptative`() {
        assertEquals("5 h", RecordCatalog.formatDurationAdaptive(5 * 3_600_000L))
        assertEquals("3 j", RecordCatalog.formatDurationAdaptive(3 * 24 * 3_600_000L))
        assertTrue(RecordCatalog.formatDurationAdaptive(45 * 24 * 3_600_000L).endsWith("mois"))
    }
}
