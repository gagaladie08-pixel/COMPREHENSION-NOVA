package com.novastats.app.domain

import com.novastats.app.data.repository.LibraryRepository
import com.novastats.app.data.repository.LibraryRepository.GuestMatch
import org.junit.Assert.assertEquals
import org.junit.Test

/** 🔒 Noms protégés (jamais découpés) + décision « même titre / version avec invité ». */
class ArtistExceptionsTest {

    @Test
    fun protectedNameIsNotSplit() {
        TitleNormalizer.setNeverSplit(TitleNormalizer.DEFAULT_NEVER_SPLIT)
        assertEquals(listOf("HUNTR/X"), TitleNormalizer.splitArtists("HUNTR/X"))
        assertEquals(listOf("huntr/x"), TitleNormalizer.splitArtists("huntr/x"))
        assertEquals(listOf("AC/DC"), TitleNormalizer.splitArtists("AC/DC"))
        assertEquals(listOf("Tyler, The Creator"), TitleNormalizer.splitArtists("Tyler, The Creator"))
    }

    @Test
    fun protectedNameInsideListIsKept() {
        TitleNormalizer.setNeverSplit(TitleNormalizer.DEFAULT_NEVER_SPLIT)
        assertEquals(listOf("HUNTR/X", "Future"), TitleNormalizer.splitArtists("HUNTR/X feat. Future"))
        assertEquals(listOf("Future", "HUNTR/X"), TitleNormalizer.splitArtists("Future & HUNTR/X"))
        assertEquals(listOf("Tyler, The Creator", "Kali Uchis"), TitleNormalizer.splitArtists("Tyler, The Creator, Kali Uchis"))
        assertEquals(listOf("Earth, Wind & Fire", "Lizzo"), TitleNormalizer.splitArtists("Earth, Wind & Fire x Lizzo"))
    }

    @Test
    fun unprotectedNamesStillSplit() {
        TitleNormalizer.setNeverSplit(TitleNormalizer.DEFAULT_NEVER_SPLIT)
        assertEquals(listOf("LE SSERAFIM", "Santos Bravos"), TitleNormalizer.splitArtists("LE SSERAFIM & Santos Bravos"))
        assertEquals(listOf("Rosé", "Bruno Mars"), TitleNormalizer.splitArtists("Rosé, Bruno Mars"))
        assertEquals(listOf("The Weeknd", "Ariana Grande"), TitleNormalizer.splitArtists("The Weeknd feat. Ariana Grande"))
    }

    @Test
    fun editableListTakesEffectImmediately() {
        TitleNormalizer.setNeverSplit(listOf("Bob & Bobette"))
        assertEquals(listOf("Bob & Bobette", "Alice"), TitleNormalizer.splitArtists("Bob & Bobette, Alice"))
        TitleNormalizer.setNeverSplit(emptyList())
        assertEquals(listOf("Bob", "Bobette", "Alice"), TitleNormalizer.splitArtists("Bob & Bobette, Alice"))
        TitleNormalizer.setNeverSplit(TitleNormalizer.DEFAULT_NEVER_SPLIT)
    }

    @Test
    fun featInTitleStillExtracted() {
        TitleNormalizer.setNeverSplit(TitleNormalizer.DEFAULT_NEVER_SPLIT)
        val n = TitleNormalizer.normalizeTitle("Golden (feat. HUNTR/X)")
        assertEquals("Golden", n.title)
        assertEquals(listOf("HUNTR/X"), n.featuredArtists)
    }

    @Test
    fun guestMatchDecision() {
        // BOOMPALA : LE SSERAFIM (connu, sans invité) puis LE SSERAFIM & Santos Bravos → version « (with Santos Bravos) »
        assertEquals(GuestMatch.SUPERSET, LibraryRepository.classify(emptySet(), setOf("#909")))
        // Même jeu d'invités → même titre
        assertEquals(GuestMatch.SAME, LibraryRepository.classify(setOf("#909"), setOf("#909")))
        // Version avec invité connue d'abord, puis l'original seul → l'existant devient la version
        assertEquals(GuestMatch.SUBSET, LibraryRepository.classify(setOf("#909"), emptySet()))
        // Invités différents → autre version liée au même root
        assertEquals(GuestMatch.OTHER, LibraryRepository.classify(setOf("#1"), setOf("#2")))
    }
}
