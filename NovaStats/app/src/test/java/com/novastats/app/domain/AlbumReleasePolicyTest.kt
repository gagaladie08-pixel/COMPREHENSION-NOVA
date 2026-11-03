package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumReleasePolicyTest {

    @Test
    fun `release types prioritize original album before single and compilation`() {
        assertEquals(AlbumReleaseKind.ALBUM, AlbumReleasePolicy.classify("Album"))
        assertEquals(AlbumReleaseKind.EP, AlbumReleasePolicy.classify("Extended Play"))
        assertEquals(AlbumReleaseKind.SINGLE, AlbumReleasePolicy.classify("Single"))
        assertEquals(AlbumReleaseKind.COMPILATION, AlbumReleasePolicy.classify("Compilation"))
        assertEquals(AlbumReleaseKind.COMPILATION, AlbumReleasePolicy.classify(null, "NOW That's What I Call Music! 89"))
        assertTrue(AlbumReleaseKind.ALBUM.priority < AlbumReleaseKind.SINGLE.priority)
        assertTrue(AlbumReleaseKind.SINGLE.priority < AlbumReleaseKind.COMPILATION.priority)
        assertTrue(AlbumReleasePolicy.isAutoLinkable(AlbumReleaseKind.ALBUM))
        assertFalse(AlbumReleasePolicy.isAutoLinkable(AlbumReleaseKind.COMPILATION))
        assertFalse(AlbumReleasePolicy.isAutoLinkable(AlbumReleaseKind.UNKNOWN))
    }

    @Test
    fun `tracklist corroboration refuses a different audio version`() {
        assertTrue(AlbumReleasePolicy.trackVersionsCompatible("Blinding Lights (Live)", "Blinding Lights (Live)"))
        assertFalse(AlbumReleasePolicy.trackVersionsCompatible("Blinding Lights", "Blinding Lights (Live)"))
        assertFalse(AlbumReleasePolicy.trackVersionsCompatible("Blinding Lights (Live)", "Blinding Lights (Remix)"))
        assertFalse(AlbumReleasePolicy.trackVersionsCompatible("Blinding Lights (Live)", "Blinding Lights (Live at Wembley)"))
        assertFalse(AlbumReleasePolicy.trackVersionsCompatible("Blinding Lights (Remix)", "Blinding Lights (DJ Remix)"))
    }

    @Test
    fun `cover and interface video tags are handled separately`() {
        assertFalse(AlbumReleasePolicy.trackVersionsCompatible("Song", "Song (Cover)"))
        assertTrue(AlbumReleasePolicy.trackVersionsCompatible("Song (Cover)", "Song (Cover)"))
        assertTrue(AlbumReleasePolicy.trackVersionsCompatible("Song (Official Video)", "Song"))
        assertFalse(AlbumReleasePolicy.trackVersionsCompatible("Song", "Song", isRemix = true))
    }
}
