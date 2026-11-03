package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistChartNumberOnesTest {
    @Test
    fun perfectAllKillRequiresSameArtistAcrossAllThreeCharts() {
        val valid = ArtistChartNumberOnes.Snapshot(
            date = "2026-10-12",
            hot100ArtistIds = setOf(7L, 8L),
            artist50LeaderId = 7L,
            albums75ArtistIds = setOf(7L)
        )
        assertEquals(7L, ArtistChartNumberOnes.perfectAllKillArtist(valid))
        assertNull(ArtistChartNumberOnes.perfectAllKillArtist(valid.copy(hot100ArtistIds = setOf(8L))))
        assertNull(ArtistChartNumberOnes.perfectAllKillArtist(valid.copy(artist50LeaderId = 8L)))
        assertNull(ArtistChartNumberOnes.perfectAllKillArtist(valid.copy(albums75ArtistIds = emptySet())))
        assertNull(ArtistChartNumberOnes.perfectAllKillArtist(valid.copy(artist50LeaderId = null)))
    }

    @Test
    fun cumulativePeriodsStaySeparatedAndDuplicateDatesCountOnce() {
        val snapshots = listOf(
            ArtistChartNumberOnes.Snapshot("2026-10-01", setOf(1L, 2L), 1L, setOf(1L)),
            ArtistChartNumberOnes.Snapshot("2026-10-01", setOf(1L), 1L, setOf(2L)),
            ArtistChartNumberOnes.Snapshot("2026-10-02", setOf(2L), 2L, emptySet())
        )

        val counts = ArtistChartNumberOnes.cumulativeDates(snapshots)
        assertEquals(setOf("2026-10-01"), counts.getValue(ArtistChartNumberOnes.HOT_100).getValue(1L))
        assertTrue(ArtistChartNumberOnes.ARTIST_50 !in counts)
        assertEquals(setOf("2026-10-01"), counts.getValue(ArtistChartNumberOnes.ALBUMS_75).getValue(1L))
        assertEquals(setOf("2026-10-01", "2026-10-02"), counts.getValue(ArtistChartNumberOnes.HOT_100).getValue(2L))
        assertTrue(counts.getValue(ArtistChartNumberOnes.ALBUMS_75).getValue(2L).size == 1)
    }

    @Test
    fun perfectAllKillDatesAreIdempotentAndCadenceSpecific() {
        val events = listOf(
            ArtistChartNumberOnes.PerfectAllKillEvent(7L, "DAILY", "2026-10-01"),
            ArtistChartNumberOnes.PerfectAllKillEvent(7L, "DAILY", "2026-10-01"),
            ArtistChartNumberOnes.PerfectAllKillEvent(7L, "DAILY", "2026-10-02"),
            ArtistChartNumberOnes.PerfectAllKillEvent(7L, "WEEKLY", "2026-10-01"),
            ArtistChartNumberOnes.PerfectAllKillEvent(8L, "DAILY", "2026-10-01")
        )
        val daily = ArtistChartNumberOnes.perfectAllKillDates(events, "DAILY")
        val weekly = ArtistChartNumberOnes.perfectAllKillDates(events, "WEEKLY")
        assertEquals(setOf("2026-10-01", "2026-10-02"), daily.getValue(7L))
        assertEquals(setOf("2026-10-01"), weekly.getValue(7L))
        assertEquals(setOf("2026-10-01"), daily.getValue(8L))
    }
}
