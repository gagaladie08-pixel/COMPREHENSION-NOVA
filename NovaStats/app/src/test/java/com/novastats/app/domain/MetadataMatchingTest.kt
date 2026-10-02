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
    fun `score piste — exact avec durée ±3 s et album = 90, consensus = 100`() {
        val c = cand(ApiSource.ITUNES, "CHOOM", "BABYMONSTER", "DRIP", duration = 180_000)
        val s = MetadataMatching.scoreTrack(c, "CHOOM", "Babymonster", "DRIP", 182_000, consensus = false)
        assertEquals(90, s.score) // 30 + 30 + 20 (durée exacte) + 10 (album)
        assertEquals(100, MetadataMatching.scoreTrack(c, "CHOOM", "Babymonster", "DRIP", 182_000, consensus = true).score)
        // ±4 s → +10 seulement
        assertEquals(80, MetadataMatching.scoreTrack(c, "CHOOM", "Babymonster", "DRIP", 184_000, consensus = false).score)
    }

    @Test
    fun `garde-fous 0_8_5 — durée différente pénalisée, artiste approximatif plafonné à 69`() {
        val wrongDuration = cand(ApiSource.ITUNES, "CHOOM", "Babymonster", duration = 200_000)
        assertEquals(45, MetadataMatching.scoreTrack(wrongDuration, "CHOOM", "Babymonster", null, 180_000, consensus = false).score) // 60 − 15
        // « Lila » ≈ « Lisa » (similarité 0,75 → +10 seulement) : même avec durée exacte, album et consensus, jamais accepté sans révision
        val nearArtist = cand(ApiSource.DEEZER, "SERVE", "Lila", "Alter Ego", duration = 180_000)
        val s = MetadataMatching.scoreTrack(nearArtist, "SERVE", "Lisa", "Alter Ego", 180_000, consensus = true)
        assertEquals(MetadataMatching.ACCEPT - 1, s.score)
        assertTrue(s.reasons.any { it.startsWith("artiste non confirmé") })
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
        assertEquals(70, MetadataMatching.scoreArtist(cand(ApiSource.DEEZER, "LISA", null, image = null), "Lisa").score)
        assertEquals(0, MetadataMatching.scoreArtist(cand(ApiSource.DEEZER, "Lissa Bennett", null), "Lisa").score)
        assertEquals(100, MetadataMatching.scoreAlbum(cand(ApiSource.ITUNES, "Alter Ego", "LISA"), "Alter Ego", "Lisa", false).score)
    }

    @Test
    fun `consensus — deux sources d'accord sur l'artiste ET l'album ou la durée`() {
        val a = cand(ApiSource.ITUNES, "X", "Y", "Album A", duration = 200_000)
        val b = cand(ApiSource.DEEZER, "X", "Y", "Album A")
        val c = cand(ApiSource.LASTFM, "X", "Y", "Other", duration = 300_000)
        val set = MetadataMatching.consensusSet(listOf(a, b, c))
        assertTrue(a in set && b in set)
        assertFalse(c in set)
        // Même titre et même album, mais artistes différents → pas de consensus
        val d = cand(ApiSource.ITUNES, "X", "Someone", "Album A", duration = 200_000)
        val e = cand(ApiSource.DEEZER, "X", "Other", "Album A", duration = 200_000)
        assertTrue(MetadataMatching.consensusSet(listOf(d, e)).isEmpty())
        // Candidats « album » (sans durée ni album) : accord sur le titre exact + artiste
        val f = cand(ApiSource.ITUNES, "Alter Ego", "Lisa")
        val g = cand(ApiSource.DEEZER, "ALTER EGO", "LISA")
        assertEquals(2, MetadataMatching.consensusSet(listOf(f, g)).size)
    }

    @Test
    fun `cache — 6 mois, 1 mois, rien`() {
        assertEquals(183L * 24 * 3_600_000, MetadataMatching.cacheTtlMs(95))
        assertEquals(30L * 24 * 3_600_000, MetadataMatching.cacheTtlMs(75))
        assertNull(MetadataMatching.cacheTtlMs(60))
    }

    @Test
    fun `ordre de cascade — clés, fiabilité, YouTube dernier, Spotify et Google retirés`() {
        val all = MetadataMatching.orderSources({ it.trackPriority }, emptyMap()) { true }
        assertEquals(listOf(ApiSource.ITUNES, ApiSource.DEEZER, ApiSource.MUSICBRAINZ, ApiSource.LASTFM, ApiSource.DISCOGS, ApiSource.GENIUS, ApiSource.YOUTUBE), all)
        assertFalse(ApiSource.SPOTIFY in all); assertFalse(ApiSource.GOOGLE in all)
        // iTunes en panne (2 succès / 18 échecs) → recule de 3 places ; sans clé Genius → absent
        val degraded = MetadataMatching.orderSources({ it.trackPriority }, mapOf(ApiSource.ITUNES to (2 to 18))) { it != ApiSource.GENIUS }
        assertEquals(ApiSource.DEEZER, degraded.first())
        assertFalse(ApiSource.GENIUS in degraded)
        assertEquals(ApiSource.YOUTUBE, degraded.last())
        val artists = MetadataMatching.orderSources({ it.artistPriority }, emptyMap()) { true }
        assertEquals(listOf(ApiSource.DEEZER, ApiSource.FANART, ApiSource.WIKIDATA, ApiSource.THEAUDIODB, ApiSource.LASTFM, ApiSource.GENIUS, ApiSource.YOUTUBE), artists)
    }

    @Test
    fun `contexte bibliothèque — bonus, pénalité, titre absent, non vérifiable`() {
        val base = ScoredCandidate(cand(ApiSource.ITUNES, "Money", "LISA"), 90, listOf("titre +30", "artiste +30"))
        val known = setOf("rockstar", "born again", "new woman", "fxck up the world", "moonlit floor")
        // L'album candidat contient le titre + 2 titres connus → +20
        val ok = MetadataMatching.libraryCheck(base, listOf("Money", "Rockstar", "New Woman", "Elastigirl"), "money", known)
        assertEquals(100, ok.score)
        assertTrue(ok.reasons.any { it.startsWith("2 titres en commun") })
        // Homonyme : aucun titre en commun alors qu'on en connaît 5 et que la liste en a 4 → −25
        val homonym = MetadataMatching.libraryCheck(base, listOf("Money", "Blue Skies", "Rain", "Old Town"), "money", known)
        assertEquals(65, homonym.score)
        // Titre absent de l'album (mauvaise édition) et rien en commun → −30 −25
        val wrong = MetadataMatching.libraryCheck(base, listOf("Blue Skies", "Rain", "Old Town", "Sunset"), "money", known)
        assertEquals(35, wrong.score)
        // Pas assez de titres connus pour trancher → inchangé
        assertEquals(90, MetadataMatching.libraryCheck(base, listOf("Money", "Blue Skies"), "money", setOf("rockstar")).score)
        // Titres pollués par les notifications : « Rockstar (Official Video) », « LISA - New Woman » → quand même reconnus
        val polluted = setOf("rockstar official video", "lisa new woman ft rosalia", "moonlit floor kiss me", "fxck up the world", "born again")
        val tolerant = MetadataMatching.libraryCheck(base, listOf("Money", "Rockstar", "New Woman", "Elastigirl"), "money", polluted)
        assertEquals(100, tolerant.score)
        assertTrue(tolerant.reasons.any { it.startsWith("2 titres en commun") })
        assertTrue(MetadataMatching.titlesMatch("diamonds", "rihanna diamonds"))
        assertTrue(!MetadataMatching.titlesMatch("work", "homework"))
        // Source non vérifiable : 90 → 89 (🟡 À vérifier), 80 inchangé
        assertEquals(89, MetadataMatching.libraryCheck(base, null, "money", known).score)
        assertEquals(80, MetadataMatching.libraryCheck(base.copy(score = 80), null, "money", known).score)
    }
}
