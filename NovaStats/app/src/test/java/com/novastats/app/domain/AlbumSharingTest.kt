package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 💿 Règle 12 — albums multi-artistes (BO, albums d'événements, « Various Artists »). */
class AlbumSharingTest {

    @Test
    fun `mots-cles du titre, insensibles a la casse et aux accents`() {
        listOf(
            "K-Pop Demon Hunters: Soundtrack From the Netflix Film", "F1 The Album", "Listen Up! The Official FIFA World Cup Album",
            "Barbie The Album", "Music From the Motion Picture Twilight", "ENCANTO (Original Motion Picture Soundtrack)",
            "la la land soundtrack", "Intouchables — Bande Originale du Film"
        ).forEach { assertTrue(it, TitleNormalizer.isSharedAlbum(it)) }
    }

    @Test
    fun `album normal avec featurings inchange`() {
        listOf("Dua Lipa (Complete Edition)", "Loud", "reputation", "BORN PINK", "Alter Ego", "Thriller", "Greatest Hits", "THE ALBUM", "The Album").forEach {
            assertFalse(it, TitleNormalizer.isSharedAlbum(it, "Rihanna"))
        }
        assertFalse(TitleNormalizer.isSharedAlbum("THE ALBUM"))            // BLACKPINK : album normal
        assertTrue(TitleNormalizer.isSharedAlbum("Barbie The Album"))     // mot-clé accolé à un nom
    }

    @Test
    fun `various artists = album partage, pas une compilation`() {
        assertTrue(TitleNormalizer.isSharedAlbum("Summer Mix", "Various Artists"))
        assertTrue(TitleNormalizer.isSharedAlbum("Summer Mix", "Artistes divers"))
        assertFalse(TitleNormalizer.isCompilation("Summer Mix", "Various Artists"))
        // Les vraies compilations restent bannies
        assertTrue(TitleNormalizer.isCompilation("NOW That's What I Call Music! 89", null))
        assertTrue(TitleNormalizer.isCompilation("NRJ Hits 2026", "Various Artists"))
        assertTrue(TitleNormalizer.isCompilation("Top Hits", null))
    }

    @Test
    fun `marquage manuel prime sur la detection`() {
        assertFalse(TitleNormalizer.sharedAlbumDecision("0", "F1 The Album", null))          // retrait manuel
        assertTrue(TitleNormalizer.sharedAlbumDecision("1", "Dua Lipa (Complete Edition)", null)) // ajout manuel
        assertTrue(TitleNormalizer.sharedAlbumDecision(null, "F1 The Album", null))
        assertFalse(TitleNormalizer.sharedAlbumDecision(null, "Loud", null))
    }

    @Test
    fun `etiquette d'affichage, pas un artiste`() {
        assertEquals("Artistes variés", TitleNormalizer.SHARED_ALBUM_LABEL)
    }
}
