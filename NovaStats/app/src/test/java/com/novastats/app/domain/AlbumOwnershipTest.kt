package com.novastats.app.domain

import com.novastats.app.domain.AlbumOwnership.AlbumInfo
import com.novastats.app.domain.AlbumOwnership.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règle 13 — albums normaux coupés par les duos. */
class AlbumOwnershipTest {

    private val DUA = 1L; private val CALVIN = 2L; private val GARRIX = 3L; private val SEAN = 4L; private val QUEEN = 5L; private val ABBA = 6L

    @Test
    fun `duo ecrit partenaire en premier rejoint l'album de l'artiste commun`() {
        // « One Kiss » (Calvin Harris & Dua Lipa) : seul album homonyme existant = celui de Dua Lipa → choisi
        val dua = Candidate(albumId = 10, ownerId = DUA, tracksCreditingOwner = 12, playCount = 300)
        assertEquals(10L, AlbumOwnership.pick(listOf(dua)))
        // Deux candidats (Calvin a déjà un album homonyme avec le seul duo) → l'artiste commun (le plus de titres)
        val calvin = Candidate(albumId = 11, ownerId = CALVIN, tracksCreditingOwner = 1, playCount = 900)
        assertEquals(10L, AlbumOwnership.pick(listOf(calvin, dua)))
        assertNull(AlbumOwnership.pick(emptyList()))
    }

    @Test
    fun `duo arrive avant les titres solo donne un seul album apres consolidation`() {
        val calvinAlbum = AlbumInfo(11, CALVIN, mapOf(100L to setOf(CALVIN, DUA)), playCount = 40)         // créé en premier
        val duaAlbum = AlbumInfo(10, DUA, mapOf(101L to setOf(DUA), 102L to setOf(DUA), 103L to setOf(DUA)), playCount = 90)
        val plans = AlbumOwnership.plan(listOf(calvinAlbum, duaAlbum))
        assertEquals(1, plans.size)
        assertEquals(10L, plans[0].keepId)
        assertEquals(listOf(11L), plans[0].absorbed)
        assertNull(plans[0].newOwner) // déjà possédé par l'artiste commun
    }

    @Test
    fun `deux albums homonymes d'artistes sans lien ne sont pas fusionnes`() {
        val queen = AlbumInfo(20, QUEEN, mapOf(200L to setOf(QUEEN), 201L to setOf(QUEEN)), playCount = 50)
        val abba = AlbumInfo(21, ABBA, mapOf(210L to setOf(ABBA)), playCount = 30)
        assertTrue(AlbumOwnership.plan(listOf(queen, abba)).isEmpty())
    }

    @Test
    fun `album avec plusieurs duos differents garde un seul proprietaire`() {
        val calvin = AlbumInfo(11, CALVIN, mapOf(100L to setOf(CALVIN, DUA)), playCount = 40)
        val garrix = AlbumInfo(12, GARRIX, mapOf(110L to setOf(GARRIX, DUA)), playCount = 20)
        val sean = AlbumInfo(13, SEAN, mapOf(120L to setOf(SEAN, DUA)), playCount = 10)
        val dua = AlbumInfo(10, DUA, mapOf(101L to setOf(DUA), 102L to setOf(DUA)), playCount = 90)
        val plans = AlbumOwnership.plan(listOf(calvin, garrix, sean, dua))
        assertEquals(1, plans.size)
        assertEquals(10L, plans[0].keepId)
        assertEquals(setOf(11L, 12L, 13L), plans[0].absorbed.toSet())
        assertNull(plans[0].newOwner)
    }

    @Test
    fun `sans album solo le plus ecoute est garde et change de proprietaire`() {
        // Que des duos avec Dua Lipa, aucun album à son nom → l'album le plus écouté est conservé et devient le sien
        val calvin = AlbumInfo(11, CALVIN, mapOf(100L to setOf(CALVIN, DUA)), playCount = 40)
        val garrix = AlbumInfo(12, GARRIX, mapOf(110L to setOf(GARRIX, DUA)), playCount = 20)
        val plans = AlbumOwnership.plan(listOf(calvin, garrix))
        assertEquals(1, plans.size)
        assertEquals(11L, plans[0].keepId)
        assertEquals(DUA, plans[0].newOwner)
        assertEquals(listOf(12L), plans[0].absorbed)
    }

    @Test
    fun `albums partages de la regle 12 non concernes`() {
        // Un album partagé n'a pas de propriétaire : il ne passe jamais par la règle 13 ; la détection reste celle de la règle 12
        assertTrue(TitleNormalizer.isSharedAlbum("F1 The Album"))
        assertTrue(TitleNormalizer.isSharedAlbum("K-Pop Demon Hunters: Soundtrack From the Netflix Film"))
        // et un album normal homonyme coupé par un duo n'en devient pas un
        assertTrue(!TitleNormalizer.isSharedAlbum("Dua Lipa (Complete Edition)"))
        assertTrue(!TitleNormalizer.isSharedAlbum("reputation", "Taylor Swift"))
    }
}
