package com.novastats.app.service

import com.novastats.app.service.ScrobbleTracker.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleTrackerTest {

    private var now = 1_000_000L
    private val tracker = ScrobbleTracker(thresholdSec = 30, clock = { now })
    private val a = ScrobbleTracker.TrackKey("Song A", "Artist", "Album", 200_000, "com.spotify.music")
    private val b = ScrobbleTracker.TrackKey("Song B", "Artist", "Album", 200_000, "com.spotify.music")

    private fun advance(ms: Long) { now += ms }

    @Test
    fun `validation au seuil`() {
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION")
        advance(29_000)
        assertTrue(tracker.onTick().isEmpty())
        advance(1_000)
        val ev = tracker.onTick()
        assertEquals(1, ev.size)
        assertTrue(ev[0] is Event.Validated)
    }

    @Test
    fun `skip avant seuil - rien de logge`() {
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION")
        advance(10_000)
        val ev = tracker.onTrackChanged(b, isPlaying = true, source = "MEDIA_SESSION")
        val ended = ev.filterIsInstance<Event.Ended>().single()
        assertEquals(false, ended.wasValidated)
        assertEquals(10_000, ended.listenedMs)
    }

    @Test
    fun `pause courte - timer cumule`() {
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION")
        advance(20_000)
        tracker.onPlaybackStateChanged(isPlaying = false)
        advance(5 * 60_000) // 5 min de pause (< 10 min)
        tracker.onPlaybackStateChanged(isPlaying = true)
        advance(10_000)
        val ev = tracker.onTick()
        assertTrue(ev.single() is Event.Validated) // 20 s + 10 s = 30 s
    }

    @Test
    fun `pause longue - nouvelle ecoute`() {
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION")
        advance(40_000)
        tracker.onTick()
        tracker.onPlaybackStateChanged(isPlaying = false)
        advance(11 * 60_000) // ≥ 10 min
        val ev = tracker.onPlaybackStateChanged(isPlaying = true)
        assertTrue(ev.any { it is Event.Ended && it.wasValidated })
        assertTrue(ev.any { it is Event.Started })
        assertEquals(0, tracker.current!!.listenedMs(now))
    }

    @Test
    fun `loop - chaque relecture est une nouvelle ecoute`() {
        tracker.onTrackChanged(a, isPlaying = true, positionMs = 0, source = "MEDIA_SESSION")
        advance(190_000)
        tracker.onTick()
        tracker.onTrackChanged(a, isPlaying = true, positionMs = 190_000, source = "MEDIA_SESSION")
        val ev = tracker.onTrackChanged(a, isPlaying = true, positionMs = 500, source = "MEDIA_SESSION")
        assertTrue(ev.any { it is Event.Ended && it.wasValidated })
        assertTrue(ev.any { it is Event.Started })
    }

    @Test
    fun `hot reload du seuil`() {
        tracker.updateThreshold(15)
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION")
        advance(15_000)
        assertTrue(tracker.onTick().single() is Event.Validated)
    }
}
