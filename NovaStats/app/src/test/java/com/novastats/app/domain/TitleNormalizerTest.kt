package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleNormalizerTest {

    @Test
    fun `versions fusionnees invisiblement`() {
        listOf(
            "Blinding Lights", "Blinding Lights (Remix)", "Blinding Lights (Acoustic)", "Blinding Lights (Live)",
            "Blinding Lights (DJ Mix)", "Blinding Lights - Remastered 2021", "Blinding Lights (Slowed + Reverb)",
            "Blinding Lights (Official Video)"
        ).forEach { assertEquals(it, "Blinding Lights", TitleNormalizer.normalizeTitle(it).title) }
    }

    @Test
    fun `parentheses non-version conservees`() {
        assertEquals("Baby (I'm Yours)", TitleNormalizer.normalizeTitle("Baby (I'm Yours)").title)
    }

    @Test
    fun `feat extrait du titre`() {
        val n = TitleNormalizer.normalizeTitle("Save Your Tears (feat. Ariana Grande)")
        assertEquals("Save Your Tears", n.title)
        assertEquals(listOf("Ariana Grande"), n.featuredArtists)
    }

    @Test
    fun `variantes feat normalisees et multiples`() {
        listOf("APT. (Feat. Bruno Mars)", "APT. (FT. Bruno Mars)", "APT. ft. Bruno Mars", "APT. [featuring Bruno Mars]", "APT. (with Bruno Mars)")
            .forEach {
                val n = TitleNormalizer.normalizeTitle(it)
                assertEquals(it, "APT.", n.title)
                assertEquals(it, listOf("Bruno Mars"), n.featuredArtists)
            }
        val n = TitleNormalizer.normalizeTitle("SG (feat. Ozuna) [ft. LISA & Megan Thee Stallion]")
        assertEquals("SG", n.title)
        assertEquals(listOf("Ozuna", "LISA", "Megan Thee Stallion"), n.featuredArtists)
    }

    @Test
    fun `remix avec featuring signale comme version`() {
        val remix = TitleNormalizer.normalizeTitle("Blinding Lights (Remix) (feat. Rosalía)")
        assertEquals("Blinding Lights", remix.title)
        assertEquals(listOf("Rosalía"), remix.featuredArtists)
        assertTrue(remix.isVersion)
        // Remix sans featuring : fusion invisible (pas de titre distinct)
        val plain = TitleNormalizer.normalizeTitle("Blinding Lights (EDM Remix)")
        assertTrue(plain.isVersion)
        assertTrue(plain.featuredArtists.isEmpty())
        // Featuring sans version : simple crédit, pas de titre séparé
        assertFalse(TitleNormalizer.normalizeTitle("Save Your Tears (feat. Ariana Grande)").isVersion)
    }

    @Test
    fun `editions d'album fusionnees`() {
        listOf(
            "BORN PINK", "BORN PINK (Deluxe)", "BORN PINK (Japan Edition)", "BORN PINK - UK Edition", "BORN PINK (Expanded Edition)",
            "BORN PINK (Platinum Edition)", "BORN PINK (International Version)", "BORN PINK Deluxe Edition", "BORN PINK (Explicit)"
        ).forEach { assertEquals(it, "BORN PINK", TitleNormalizer.normalizeAlbumTitle(it)) }
        assertEquals("Ruby Vol. 2", TitleNormalizer.normalizeAlbumTitle("Ruby Vol. 2"))
        assertEquals("Thriller", TitleNormalizer.normalizeAlbumTitle("Thriller 25th Anniversary"))
    }

    @Test
    fun `compilations jamais creditees`() {
        assertTrue(TitleNormalizer.isCompilation("NOW That's What I Call Music! 89", null))
        assertTrue(TitleNormalizer.isCompilation("Summer Hits", "Various Artists"))
        assertTrue(TitleNormalizer.isCompilation("NRJ Hits 2026", null))
        assertFalse(TitleNormalizer.isCompilation("BORN PINK", "BLACKPINK"))
        assertFalse(TitleNormalizer.isCompilation("Greatest Hits", "Queen"))
    }

    @Test
    fun `emojis conserves et mots indesirables nettoyes`() {
        assertEquals("Butter 🧈", TitleNormalizer.normalizeTitle("Butter 🧈 ™").title)
    }

    @Test
    fun `split artistes multiples`() {
        assertEquals(listOf("Babymonster"), TitleNormalizer.splitArtists("Babymonster"))
        assertEquals(listOf("Jung Kook", "Latto"), TitleNormalizer.splitArtists("Jung Kook feat. Latto"))
        assertEquals(listOf("Burna Boy", "Ed Sheeran", "Wizkid"), TitleNormalizer.splitArtists("Burna Boy, Ed Sheeran & Wizkid"))
    }

    @Test
    fun `cle normalisee`() {
        assertEquals("beyonce", TitleNormalizer.normalizeKey("Beyoncé"))
        assertEquals(TitleNormalizer.normalizeKey("The Weeknd"), TitleNormalizer.normalizeKey("the  WEEKND!"))
    }

    @Test
    fun levenshtein() {
        assertEquals(0, TitleNormalizer.levenshtein("nova", "nova"))
        assertEquals(1, TitleNormalizer.levenshtein("nova", "novas"))
        assertEquals(3, TitleNormalizer.levenshtein("kitten", "sitting"))
    }
}
