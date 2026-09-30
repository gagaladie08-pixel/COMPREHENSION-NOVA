package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataMatchingTest {

    private fun cand(src: ApiSource, name: String, artist: String?, album: String? = null, image: String? = "https://img/x.jpg", duration: Long? = null) =
        MetaCandidate(src, name, artist, album, image, duration)

    @Test
    fun `similarité tolère accents, casse et feat`() {
        assertEquals(1.0, MetadataMatching.similarity("Déjà Vu", "deja vu"), 0.001)
        assertTrue(MetadataMatching.similarity("SERVE (feat. Lisa)", "SERVE") >= 0.85)
        assertTrue(MetadataMatching.similarity("Blinding Lights", "Blinding Lihgts") >= 0.85)
        assertTrue(MetadataMatching.similarity("Hello", "Goodbye World") < 0.5)
    }

    @Test
    fun `score piste — exact avec durée et album = 80, consensus = 100`() {
        val c = cand(ApiSource.ITUNES, "CHOOM", "BABYMONSTER", "DRIP", duration = 180_000)
        val s = MetadataMatching.scoreTrack(c, "CHOOM", "Babymonster", "DRIP", 182_000, consensus = false)
        assertEquals(80, s.score)
        assertEquals(100, MetadataMatching.scoreTrack(c, "CHOOM", "Babymonster", "DRIP", 182_000, consensus = true).score)
    }

    @Test
    fun `score piste — mauvais artiste rejeté, titre générique rejeté`() {
        assertEquals(0, MetadataMatching.scoreTrack(cand(ApiSource.DEEZER, "CHOOM", "Someone Else"), "CHOOM", "Babymonster", null, null, false).score)
        assertEquals(0, MetadataMatching.scoreTrack(cand(ApiSource.DEEZER, "Track 01", "Babymonster"), "Track 01", "Babymonster", null, null, false).score)
    }

    @Test
    fun `anomalies — pas d'image et durée suspecte pénalisées`() {
        val noImg = cand(ApiSource.LASTFM, "CHOOM", "Babymonster", image = null)
        assertEquals(45, MetadataMatching.scoreTrack(noImg, "CHOOM", "Babymonster", null, null, false).score) // 60 − 15
        val tooLong = cand(ApiSource.LASTFM, "CHOOM", "Babymonster", duration = 25 * 60_000L)
        assertEquals(50, MetadataMatching.scoreTrack(tooLong, "CHOOM", "Babymonster", null, null, false).score) // 60 − 10
    }

    @Test
    fun `score artiste et album`() {
        assertEquals(100, MetadataMatching.scoreArtist(cand(ApiSource.FANART, "LISA", null), "Lisa").score)
        assertEquals(70, MetadataMatching.scoreArtist(cand(ApiSource.SPOTIFY, "LISA", null, image = null), "Lisa").score)
        assertEquals(0, MetadataMatching.scoreArtist(cand(ApiSource.SPOTIFY, "Lissa Bennett", null), "Lisa").score)
        assertEquals(100, MetadataMatching.scoreAlbum(cand(ApiSource.ITUNES, "Alter Ego", "LISA"), "Alter Ego", "Lisa", false).score)
    }

    @Test
    fun `consensus — deux sources d'accord sur l'album ou la durée`() {
        val a = cand(ApiSource.ITUNES, "X", "Y", "Album A", duration = 200_000)
        val b = cand(ApiSource.SPOTIFY, "X", "Y", "Album A")
        val c = cand(ApiSource.LASTFM, "X", "Y", "Other", duration = 300_000)
        val set = MetadataMatching.consensusSet(listOf(a, b, c))
        assertTrue(a in set && b in set)
        assertFalse(c in set)
    }

    @Test
    fun `cache — 6 mois, 1 mois, rien`() {
        assertEquals(183L * 24 * 3_600_000, MetadataMatching.cacheTtlMs(95))
        assertEquals(30L * 24 * 3_600_000, MetadataMatching.cacheTtlMs(75))
        assertNull(MetadataMatching.cacheTtlMs(60))
    }

    @Test
    fun `ordre de cascade — clés, fiabilité, Google dernier`() {
        val all = MetadataMatching.orderSources({ it.trackPriority }, emptyMap()) { true }
        assertEquals(listOf(ApiSource.ITUNES, ApiSource.SPOTIFY, ApiSource.LASTFM, ApiSource.MUSICBRAINZ, ApiSource.THEAUDIODB, ApiSource.DEEZER, ApiSource.DISCOGS, ApiSource.GOOGLE), all)
        // iTunes en panne (2 succès / 18 échecs) → recule de 3 places ; sans clé Spotify → absent
        val degraded = MetadataMatching.orderSources({ it.trackPriority }, mapOf(ApiSource.ITUNES to (2 to 18))) { it != ApiSource.SPOTIFY }
        assertEquals(ApiSource.LASTFM, degraded.first())
        assertFalse(ApiSource.SPOTIFY in degraded)
        assertEquals(ApiSource.GOOGLE, degraded.last())
        val artists = MetadataMatching.orderSources({ it.artistPriority }, emptyMap()) { true }
        assertEquals(listOf(ApiSource.FANART, ApiSource.THEAUDIODB, ApiSource.SPOTIFY, ApiSource.LASTFM, ApiSource.DEEZER, ApiSource.GOOGLE), artists)
    }
}
