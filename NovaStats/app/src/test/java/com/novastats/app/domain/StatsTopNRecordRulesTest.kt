package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTopNRecordRulesTest {

    @Test
    fun `thresholds match the all-time Stats leaderboard`() {
        assertEquals(300, StatsTopNRecordRules.STATS_TOP_LIMIT)
        assertEquals(listOf(10, 20, 40, 50, 70, 100, 150, 200, 250, 300), StatsTopNRecordRules.thresholds)

        val expectedIds = setOf(
            "MOST_TRACKS_BY_ARTIST_IN_STATS_TOP_N",
            "MOST_ALBUMS_BY_ARTIST_IN_STATS_TOP_N",
            "MOST_TRACKS_BY_ALBUM_IN_STATS_TOP_N"
        )
        val records = expectedIds.map { RecordCatalog.byId(it)!! }
        assertEquals(38, records.first { it.id == "MOST_TRACKS_BY_ARTIST_IN_STATS_TOP_N" }.number)
        assertEquals(39, records.first { it.id == "MOST_ALBUMS_BY_ARTIST_IN_STATS_TOP_N" }.number)
        assertEquals(40, records.first { it.id == "MOST_TRACKS_BY_ALBUM_IN_STATS_TOP_N" }.number)
        records.forEach { record ->
            assertTrue(record.periods.isEmpty())
            assertEquals(StatsTopNRecordRules.thresholds.map { "TOP$it" }, record.subs.values.single().map { it.dbName })
            assertEquals(RecordGroup.LISTENING, RecordCatalog.groupOf[record.id])
        }
        assertFalse(records.first { it.id == "MOST_TRACKS_BY_ARTIST_IN_STATS_TOP_N" }.categories.contains(RecordCategory.ALBUM))
    }

    @Test
    fun `artist track ranking credits every collaboration and counts distinct tracks`() {
        val rankedTracks = listOf(
            StatsTopNRecordRules.RankedTrack(1, 10, setOf(7, 8)),
            StatsTopNRecordRules.RankedTrack(2, 10, setOf(7)),
            StatsTopNRecordRules.RankedTrack(3, 11, setOf(8))
        )

        assertEquals(mapOf(7L to 2, 8L to 1), StatsTopNRecordRules.artistTrackCounts(rankedTracks, 2))
        assertEquals(mapOf(7L to 2, 8L to 2), StatsTopNRecordRules.artistTrackCounts(rankedTracks, 3))
    }

    @Test
    fun `Top N is strictly limited at the boundary without adding tied rows`() {
        val rankedTracks = listOf(
            StatsTopNRecordRules.RankedTrack(1, 10, setOf(7)),
            StatsTopNRecordRules.RankedTrack(2, 10, setOf(7)),
            StatsTopNRecordRules.RankedTrack(3, 11, setOf(8)),
            // A fourth entry tied with the boundary is not present in the Stats Top 3.
            StatsTopNRecordRules.RankedTrack(4, 12, setOf(8))
        )

        assertEquals(mapOf(7L to 2, 8L to 1), StatsTopNRecordRules.artistTrackCounts(rankedTracks, 3))
    }

    @Test
    fun `artist album ranking uses the album owner and only the current top N`() {
        val rankedAlbums = listOf(
            StatsTopNRecordRules.RankedAlbum(10, 7),
            StatsTopNRecordRules.RankedAlbum(11, 8),
            StatsTopNRecordRules.RankedAlbum(12, 7),
            StatsTopNRecordRules.RankedAlbum(13, 9)
        )

        assertEquals(mapOf(7L to 2, 8L to 1), StatsTopNRecordRules.artistAlbumCounts(rankedAlbums, 3))
        assertEquals(mapOf(7L to 1, 8L to 1), StatsTopNRecordRules.artistAlbumCounts(rankedAlbums, 2))
    }

    @Test
    fun `album counts tracks from the global track ranking and ignores ineligible albums`() {
        val rankedTracks = listOf(
            StatsTopNRecordRules.RankedTrack(1, 10, setOf(7)),
            StatsTopNRecordRules.RankedTrack(2, 10, setOf(8)),
            StatsTopNRecordRules.RankedTrack(3, 20, setOf(9)),
            StatsTopNRecordRules.RankedTrack(4, 10, setOf(7)),
            StatsTopNRecordRules.RankedTrack(5, 30, setOf(7))
        )

        assertEquals(
            mapOf(10L to 2, 20L to 1),
            StatsTopNRecordRules.albumTrackCounts(rankedTracks, 3, eligibleAlbumIds = setOf(10L, 20L))
        )
        assertEquals(
            mapOf(10L to 3),
            StatsTopNRecordRules.albumTrackCounts(rankedTracks, 4, eligibleAlbumIds = setOf(10))
        )
    }

    @Test
    fun `counts are recalculated from current ranking not accumulated as history`() {
        val earlierRanking = listOf(
            StatsTopNRecordRules.RankedTrack(1, 10, setOf(7)),
            StatsTopNRecordRules.RankedTrack(2, 10, setOf(7))
        )
        val currentRanking = listOf(
            StatsTopNRecordRules.RankedTrack(2, 10, setOf(7)),
            StatsTopNRecordRules.RankedTrack(3, 20, setOf(8))
        )

        assertEquals(2, StatsTopNRecordRules.artistTrackCounts(earlierRanking, 2)[7L])
        assertEquals(mapOf(7L to 1, 8L to 1), StatsTopNRecordRules.artistTrackCounts(currentRanking, 2))
    }
}
