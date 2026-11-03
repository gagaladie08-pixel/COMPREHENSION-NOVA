package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumAssociationPolicyTest {

    @Test
    fun `only plays at or after association time receive the album`() {
        assertEquals(null, AlbumAssociationPolicy.albumIdAt(42L, 1_000L, 999L))
        assertEquals(42L, AlbumAssociationPolicy.albumIdAt(42L, 1_000L, 1_000L))
        assertEquals(42L, AlbumAssociationPolicy.albumIdAt(42L, null, 999L))
        assertEquals(null, AlbumAssociationPolicy.albumIdAt(null, 1_000L, 2_000L))
    }

    @Test
    fun `backup merge keeps the later effective date to avoid retroactive links`() {
        assertEquals(2_000L, AlbumAssociationPolicy.latestAssociationSince(1_000L, 2_000L))
        assertEquals(2_000L, AlbumAssociationPolicy.latestAssociationSince(2_000L, 1_000L))
        assertEquals(1_000L, AlbumAssociationPolicy.latestAssociationSince(null, 1_000L))
        assertEquals(null, AlbumAssociationPolicy.latestAssociationSince(null, null))
    }
}
