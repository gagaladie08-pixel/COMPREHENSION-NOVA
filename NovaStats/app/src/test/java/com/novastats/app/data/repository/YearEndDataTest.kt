package com.novastats.app.data.repository

import com.novastats.app.data.db.dao.PeriodSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class YearEndDataTest {
    @Test
    fun `delta annuel ne deborde pas au dela de Int`() {
        val current = PeriodSummary(3_000_000_000L, 0L, 0, 0, 0, 0)
        val previous = PeriodSummary(1_500_000_000L, 0L, 0, 0, 0, 0)
        val data = YearEndData(
            year = 2026,
            fromIso = "2025-12-01",
            toIso = "2026-11-30",
            calendarYear = false,
            summary = current,
            previous = previous,
            tracks = emptyList(),
            artists = emptyList(),
            albums = emptyList(),
            bestDay = null,
            newArtists = 0,
            weeksCounted = 52,
            inProgress = true
        )

        assertEquals(1_500_000_000L, data.playsDelta)
        assertEquals(100, data.playsDeltaPct)
    }
}
