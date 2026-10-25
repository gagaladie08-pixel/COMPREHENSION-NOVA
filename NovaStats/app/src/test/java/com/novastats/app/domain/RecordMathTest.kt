package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordMathTest {

    private fun s(vararg p: Pair<Int, Int>) = p.map { (idx, pos) -> RecordAppearance(idx, "2026-01-%02d".format(idx + 1), pos, 10) }

    @Test
    fun `catalogue expose les 40 records`() {
        assertEquals(40, RecordCatalog.ALL.map { it.number }.distinct().size)
        assertTrue(RecordCatalog.ALL.all { it.categories.isNotEmpty() })
        // sous-sections uniquement pour des catégories proposées
        RecordCatalog.ALL.forEach { d -> d.subs.keys.forEach { c -> assertTrue("${d.id}/$c", c in d.categories) } }
    }

    @Test
    fun `cumulative et seuils top10 et top5`() {
        val series = s(0 to 5, 1 to 12, 2 to 1, 3 to 1)
        assertEquals(4, RecordMath.cumulative(series))
        assertEquals(3, RecordMath.cumulative(series, 10))
        assertEquals(3, RecordMath.cumulative(series, 5))
        assertEquals(2, RecordMath.timesAt1(series))

        // Le cumul Top 5 inclut les #1 et compte les périodes non consécutives.
        assertEquals(2, RecordMath.cumulative(s(0 to 5, 1 to 6, 3 to 4), 5))
    }

    @Test
    fun `Most Songs in Top 5 est un record distinct par element et non cumulatif`() {
        val record = RecordCatalog.byId("MOST_SONGS_TOP5")!!
        assertEquals(32, record.number)
        assertEquals(RecordUnit.COUNT, record.unit)
        assertEquals(listOf(RecordCategory.ARTIST, RecordCategory.ALBUM), record.categories)
        assertTrue(record.periods.isNotEmpty())
    }

    @Test
    fun `zone Top 5 compte chaque titre distinct une seule fois`() {
        val series = mapOf(
            10L to s(0 to 15, 1 to 8, 2 to 4, 3 to 1), // un même titre franchit plusieurs paliers
            20L to s(0 to 10, 1 to 6, 2 to 5),
            30L to s(0 to 6, 1 to 7),
            40L to s(0 to 5)
        )
        assertEquals(setOf(10L, 20L, 40L), RecordMath.distinctItemsInZone(series, 5))
        assertEquals(setOf(10L, 20L, 30L, 40L), RecordMath.distinctItemsInZone(series, 10))
        assertEquals(setOf(10L), RecordMath.distinctItemsInZone(series, 1))
    }

    @Test
    fun `Most Debut Top 5 est un palier distinct et garde les cadences`() {
        val record = RecordCatalog.byId("MOST_DEBUT_TOP5")!!
        assertEquals(35, record.number)
        assertEquals(RecordUnit.COUNT, record.unit)
        assertEquals(RecordGroup.DEBUT, RecordCatalog.groupOf[record.id])
        assertEquals(listOf(RecordCategory.ARTIST, RecordCategory.ALBUM), record.categories)
        assertEquals(listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY, Period.YEARLY), record.periods)
    }

    @Test
    fun `debut Top 5 exige une entree immediate et est emboite entre Top 1 et Top 10`() {
        val series = mapOf(
            10L to s(0 to 1, 1 to 14),  // entrée #1
            20L to s(0 to 5, 1 to 2),   // entrée #5
            30L to s(0 to 6, 1 to 1),   // entrée #6 : #1 plus tard ne devient pas un début Top 5
            40L to s(0 to 10, 1 to 4),  // entrée #10 : #4 plus tard ne devient pas un début Top 5
            50L to emptyList()
        )
        val directTop1 = RecordMath.distinctItemsDebutingInZone(series, 1)
        val directTop5 = RecordMath.distinctItemsDebutingInZone(series, 5)
        val directTop10 = RecordMath.distinctItemsDebutingInZone(series, 10)
        assertEquals(setOf(10L), directTop1)
        assertEquals(setOf(10L, 20L), directTop5)
        assertEquals(setOf(10L, 20L, 30L, 40L), directTop10)
        assertTrue(directTop1.all { it in directTop5 })
        assertTrue(directTop5.all { it in directTop10 })
        assertEquals(emptySet<Long>(), RecordMath.distinctItemsDebutingInZone(series, 0))
    }

    @Test
    fun `conversion de palier et denominateur vide`() {
        assertEquals(40, RecordMath.conversionPercent(2, 5))
        assertEquals(0, RecordMath.conversionPercent(0, 5))
        assertNull(RecordMath.conversionPercent(0, 0))
    }

    @Test
    fun `plus longue serie consecutive`() {
        val series = s(0 to 3, 1 to 4, 3 to 2, 4 to 6, 5 to 5, 6 to 9)
        assertEquals(4 to "2026-01-07", RecordMath.longestStreak(series, 10))
        assertEquals(2 to "2026-01-02", RecordMath.longestStreak(series, 5))
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
        assertEquals(3 to 2, RecordMath.blockedTop(s(0 to 2, 1 to 3, 2 to 4, 3 to 9), 5))
        assertNull(RecordMath.blockedTop(s(0 to 2, 1 to 1), 5))
    }

    @Test
    fun `periodes pour atteindre`() {
        assertEquals(2 to "2026-01-03", RecordMath.periodsToReach(s(0 to 9, 1 to 4, 2 to 1), 1))
        assertNull(RecordMath.periodsToReach(s(0 to 9), 1))
    }

    @Test
    fun `most successive exige deux titres distincts dans une suite continue`() {
        val artist = setOf(7L)
        val ones = listOf(
            RecordMath.NumberOne(0, "2026-01-01", 100, artist),
            RecordMath.NumberOne(1, "2026-01-02", 101, artist),
            RecordMath.NumberOne(2, "2026-01-03", 101, artist),
            RecordMath.NumberOne(3, "2026-01-04", 200, setOf(8L)),
            RecordMath.NumberOne(4, "2026-01-05", 102, artist)
        )
        val results = RecordMath.successiveNumberOnes(ones)
        assertEquals(2.0, results.getValue(7L).value, 0.0)
        assertFalse("Un seul titre au #1 ne forme pas une succession", 8L in results)

        val best = RecordMath.bestSuccessiveOneRun(ones, 7L)!!
        assertEquals(listOf(100L, 101L), best.distinctEntityIds)
        assertEquals(3, best.periodCount)
        assertEquals("2026-01-01", best.first.date)
        assertEquals("2026-01-03", best.last.date)
        val relay = RecordMath.successiveOneSegments(best)
        assertEquals(listOf(100L, 101L), relay.map { it.entityId })
        assertEquals(1, relay[0].periodCount)
        assertEquals(2, relay[1].periodCount)
        assertEquals("2026-01-02", relay[1].first.date)
        assertEquals("2026-01-03", relay[1].last.date)
    }

    @Test
    fun `meme titre repete ou periode manquante ne qualifie pas`() {
        val artist = setOf(7L)
        val sameTitle = listOf(
            RecordMath.NumberOne(0, "2026-01-01", 100, artist),
            RecordMath.NumberOne(1, "2026-01-02", 100, artist),
            RecordMath.NumberOne(2, "2026-01-03", 100, artist)
        )
        assertTrue(RecordMath.successiveNumberOnes(sameTitle).isEmpty())

        val gap = listOf(
            RecordMath.NumberOne(0, "2026-01-01", 100, artist),
            RecordMath.NumberOne(2, "2026-01-03", 101, artist)
        )
        assertTrue(RecordMath.successiveNumberOnes(gap).isEmpty())
    }

    @Test
    fun `duree adaptative`() {
        assertEquals("5 h 0 min", RecordCatalog.formatDurationAdaptive(5 * 3_600_000L))
        assertEquals("3 j 0 h 0 min", RecordCatalog.formatDurationAdaptive(3 * 24 * 3_600_000L))
        assertEquals("45 j 2 h 7 min", RecordCatalog.formatDurationAdaptive(45 * 24 * 3_600_000L + 2 * 3_600_000L + 7 * 60_000L))
        assertEquals("32 min", RecordCatalog.formatDurationAdaptive(32 * 60_000L))
    }
}
