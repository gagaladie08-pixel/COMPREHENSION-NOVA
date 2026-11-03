package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Noms d'album factices (« Unknown », « Cover »…) et marqueurs d'édition.
 *
 * Ces règles décident si une écoute reçoit un album : un faux nom d'album ne doit jamais créer
 * d'entité (sinon un album « Unknown » apparaît dans les charts et le vrai album n'est jamais cherché).
 */
class AlbumPlaceholderTest {

    @Test
    fun `les placeholders des lecteurs sont reconnus`() {
        listOf("Unknown", "unknown", "UNKNOWN ALBUM", "Unknown Album", "Inconnu", "album inconnu", "null", "None", "n/a", "Cover", "no album", "<unknown>")
            .forEach { assertTrue("« $it » doit être considéré comme factice", TitleNormalizer.isPlaceholderAlbum(it)) }
    }

    @Test
    fun `un album vide ou null est factice`() {
        assertTrue(TitleNormalizer.isPlaceholderAlbum(null))
        assertTrue(TitleNormalizer.isPlaceholderAlbum("   "))
        assertNull(TitleNormalizer.realAlbumOrNull(null))
        assertNull(TitleNormalizer.realAlbumOrNull("Unknown"))
    }

    @Test
    fun `les vrais albums ne sont pas touches`() {
        listOf("ANTI", "I,God", "UXLXVE - EP", "I'mma Be - EP", "Short n' Sweet", "Cover Me", "Music", "Unknown Pleasures", "Video Games")
            .forEach { assertFalse("« $it » ne doit pas être factice", TitleNormalizer.isPlaceholderAlbum(it)) }
    }

    @Test
    fun `realAlbumOrNull conserve le nom exact`() {
        assertEquals("I,God", TitleNormalizer.realAlbumOrNull("I,God"))
        assertEquals("UXLXVE - EP", TitleNormalizer.realAlbumOrNull("  UXLXVE - EP  "))
    }

    @Test
    fun `les editions sont fusionnees avec l'album principal`() {
        assertEquals("ANTI", TitleNormalizer.normalizeAlbumTitle("ANTI (Deluxe)"))
        assertEquals("Dua Lipa", TitleNormalizer.normalizeAlbumTitle("Dua Lipa (Complete Edition)"))
        assertEquals("Future Nostalgia", TitleNormalizer.normalizeAlbumTitle("Future Nostalgia (The Moonlight Edition)"))
        assertEquals("Born Pink", TitleNormalizer.normalizeAlbumTitle("Born Pink - Japan Edition"))
    }

    @Test
    fun `un mot-cle court ne coupe plus un titre au milieu d'un mot`() {
        // « us » de « Music » et « tour » ne sont plus des marqueurs : ces albums gardent leur nom complet
        assertEquals("Immortal (Music from the Cirque du Soleil Show)", TitleNormalizer.normalizeAlbumTitle("Immortal (Music from the Cirque du Soleil Show)"))
        assertEquals("Beautiful (Tourist)", TitleNormalizer.normalizeAlbumTitle("Beautiful (Tourist)"))
        // Le marqueur reste actif quand c'est un vrai mot
        assertEquals("Beautiful", TitleNormalizer.normalizeAlbumTitle("Beautiful (Tour Version)"))
        assertEquals("Loud", TitleNormalizer.normalizeAlbumTitle("Loud (US Edition)"))
    }

    @Test
    fun `les suffixes qui ne sont pas des editions sont conserves`() {
        assertEquals("I ONE - EP", TitleNormalizer.normalizeAlbumTitle("I ONE - EP"))
        assertEquals("Greatest Hits Vol. 2", TitleNormalizer.normalizeAlbumTitle("Greatest Hits Vol. 2"))
    }

    @Test
    fun `un rattachement automatique exige une liste de pistes verifiee`() {
        assertTrue(AlbumAutoLinkPolicy.shouldAutoLink(92, tracklistVerified = true, kind = AlbumReleaseKind.EP))
        assertFalse(AlbumAutoLinkPolicy.shouldAutoLink(92, tracklistVerified = false, kind = AlbumReleaseKind.EP))
        assertTrue(AlbumAutoLinkPolicy.shouldAutoLink(96, tracklistVerified = false, kind = AlbumReleaseKind.ALBUM))
    }

    @Test
    fun `compilations et type inconnu restent en confirmation manuelle`() {
        assertFalse(AlbumAutoLinkPolicy.shouldAutoLink(100, tracklistVerified = true, kind = AlbumReleaseKind.COMPILATION))
        assertFalse(AlbumAutoLinkPolicy.shouldAutoLink(100, tracklistVerified = true, kind = AlbumReleaseKind.UNKNOWN))
    }
}
