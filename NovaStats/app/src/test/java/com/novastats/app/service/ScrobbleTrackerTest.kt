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

    /** Simule le ticker du service (1 tick / s) pendant [ms] — le dernier pas n'émet pas de tick, pour laisser le test observer. */
    private fun advance(ms: Long) {
        var left = ms
        while (left > 1_000) { now += 1_000; tracker.onTick(); left -= 1_000 }
        now += left
    }

    /** Saut d'horloge SANS tick (process gelé / listener déconnecté). */
    private fun freeze(ms: Long) { now += ms }

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
        assertEquals(2, ev.size)
        assertTrue(ev[0] is Event.Ended)
        assertTrue(ev[1] is Event.Started)
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
    fun `position inconnue sur meme titre ne declenche pas un faux loop`() {
        tracker.onTrackChanged(a, isPlaying = true, positionMs = 0, source = "MEDIA_SESSION")
        for (i in 1..190) { now += 1_000; tracker.onTick(positionMs = i * 1_000L) }
        val current = tracker.current
        val events = tracker.onTrackChanged(a, isPlaying = true, positionMs = null, source = "MEDIA_SESSION")
        assertTrue(events.none { it is Event.Ended || it is Event.Started })
        assertEquals(current, tracker.current)
    }

    @Test
    fun `hot reload du seuil`() {
        tracker.updateThreshold(15)
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION")
        advance(15_000)
        assertTrue(tracker.onTick().single() is Event.Validated)
    }

    /* ---------- Robustesse 0.7.1 ---------- */

    @Test
    fun `gel du process - le trou n'est pas compte`() {
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION")
        advance(10_000)
        freeze(20 * 60_000) // 20 min sans aucun tick ni événement
        val ev = tracker.onTrackChanged(b, isPlaying = true, source = "MEDIA_SESSION")
        assertTrue(ev.any { it is Event.Gap })
        val ended = ev.filterIsInstance<Event.Ended>().single()
        assertEquals(false, ended.wasValidated)
        assertTrue("écouté = ${ended.listenedMs}", ended.listenedMs in 9_000..10_000)
        assertEquals(b, tracker.current!!.key)
    }

    @Test
    fun `gel court - on reprend le comptage apres le trou`() {
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION")
        advance(10_000)
        freeze(60_000) // 1 min gelée (< pause longue) : non comptée, mais l'écoute continue
        val ev = tracker.onTick()
        assertTrue(ev.single() is Event.Gap)
        advance(21_000)
        assertTrue(tracker.onTick().single() is Event.Validated) // 9 s comptés avant le gel + 21 s = 30 s
        assertTrue(tracker.current!!.listenedMs(now) in 29_000..31_000)
    }

    @Test
    fun `plafond - jamais plus que la duree du morceau`() {
        tracker.onTrackChanged(a, isPlaying = true, source = "MEDIA_SESSION") // 200 s
        advance(22 * 60_000) // 22 min de ticks (lecteur qui a enchaîné sans qu'on voie les changements)
        val ended = tracker.onTrackChanged(b, isPlaying = true, source = "MEDIA_SESSION").filterIsInstance<Event.Ended>().single()
        assertTrue(ended.wasValidated)
        assertEquals(200_000 + 5_000, ended.listenedMs)
    }

    @Test
    fun `position reelle - lecteur fige = pause implicite`() {
        tracker.onTrackChanged(a, isPlaying = true, positionMs = 0, source = "MEDIA_SESSION")
        // 20 s où la position avance normalement
        for (i in 1..20) { now += 1_000; tracker.onTick(positionMs = i * 1_000L) }
        // puis la position reste bloquée à 20 s pendant 60 s (événement pause manqué)
        for (i in 1..60) { now += 1_000; tracker.onTick(positionMs = 20_000) }
        val listened = tracker.current!!.listenedMs(now)
        assertTrue("écouté = $listened", listened in 20_000..36_000)
        assertEquals(null, tracker.current!!.playingSince)
        // la position repart → reprise du comptage
        for (i in 1..15) { now += 1_000; tracker.onTick(positionMs = 20_000 + i * 1_000L) }
        assertTrue(tracker.current!!.playingSince != null)
    }
}
