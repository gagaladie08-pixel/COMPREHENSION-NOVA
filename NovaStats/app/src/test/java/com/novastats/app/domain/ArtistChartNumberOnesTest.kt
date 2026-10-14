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
        assertEquals(setOf("2026-10-01"), counts.getValue(ArtistChartNumberOnes.ARTIST_50).getValue(1L))
        assertEquals(setOf("2026-10-01"), counts.getValue(ArtistChartNumberOnes.ALBUMS_75).getValue(1L))
        assertEquals(setOf("2026-10-01", "2026-10-02"), counts.getValue(ArtistChartNumberOnes.HOT_100).getValue(2L))
        assertTrue(counts.getValue(ArtistChartNumberOnes.ALBUMS_75).getValue(2L).size == 1)
    }
}
