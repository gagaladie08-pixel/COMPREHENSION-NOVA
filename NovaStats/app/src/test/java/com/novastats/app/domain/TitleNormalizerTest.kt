package com.novastats.app.domain

import org.junit.Assert.assertEquals
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
