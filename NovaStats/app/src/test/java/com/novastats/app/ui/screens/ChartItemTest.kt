package com.novastats.app.ui.screens

import com.novastats.app.domain.Movement
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartItemTest {

    private fun chartItem(
        position: Int = 1,
        previousPosition: Int? = 4,
        peakPosition: Int = 1,
        timesAtPeak: Int = 2,
        officialPosition: Int = position
    ) = ChartItem(
        entityId = 1L,
        position = position,
        name = "Test",
        secondary = null,
        coverUrl = null,
        circle = false,
        plays = 100,
        durationMs = 1_000L,
        variationPlays = 10,
        movement = previousPosition?.let { previous ->
            when {
                officialPosition < previous -> Movement.Up(previous - officialPosition)
                officialPosition > previous -> Movement.Down(officialPosition - previous)
                else -> Movement.Same
            }
        } ?: Movement.Reentry,
        periodsInChart = 4,
        peakPosition = peakPosition,
        timesAtPeak = timesAtPeak,
        isPlaysPeak = false,
        previousPosition = previousPosition,
        officialPosition = officialPosition
    )

    @Test
    fun `repeak is marked only when the item returns to its best rank`() {
        assertTrue(chartItem(position = 1, previousPosition = 4, timesAtPeak = 2).isRepeak)
        assertTrue(chartItem(position = 10, officialPosition = 1, previousPosition = 4, timesAtPeak = 2).isRepeak)
        assertTrue(chartItem(position = 1, previousPosition = null, timesAtPeak = 2).isRepeak)
        assertFalse(chartItem(position = 1, previousPosition = 1, timesAtPeak = 3).isRepeak)
        assertFalse(chartItem(position = 2, previousPosition = 4, timesAtPeak = 2).isRepeak)
        assertFalse(chartItem(position = 1, officialPosition = 2, previousPosition = 4, timesAtPeak = 2).isRepeak)
        assertFalse(chartItem(position = 1, previousPosition = 4, timesAtPeak = 1).isRepeak)
    }
}
