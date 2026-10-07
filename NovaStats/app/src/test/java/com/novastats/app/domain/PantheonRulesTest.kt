package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PantheonRulesTest {

    private val none = ArtistCertSummary(emptyList(), emptyList())
    private fun certs(level: CertLevel, n: Int) = List(n) { Certification(level) }

    @Test
    fun `option B - seuils d'ecoutes`() {
        assertNull(PantheonRules.evaluate(424, none).status)
        assertEquals(PantheonStatus.STAR, PantheonRules.evaluate(425, none).status)
        assertEquals(PantheonStatus.SUPERSTAR, PantheonRules.evaluate(650, none).status)
        assertEquals(PantheonStatus.MEGASTAR, PantheonRules.evaluate(1250, none).status)
        assertEquals(PantheonStatus.LEGENDE, PantheonRules.evaluate(3650, none).status)
        assertEquals(PantheonStatus.MYTHIQUE, PantheonRules.evaluate(7000, none).status)
        assertTrue(PantheonRules.evaluate(7000, none).viaPlays)
    }

    @Test
    fun `option A - 5 chansons + 2 albums au niveau`() {
        val star = ArtistCertSummary(certs(CertLevel.SILVER, 5), certs(CertLevel.SILVER, 2))
        assertEquals(PantheonStatus.STAR, PantheonRules.evaluate(0, star).status)
        assertFalse(PantheonRules.evaluate(0, star).viaPlays)

        // 5 chansons Or mais 1 seul album Or → reste Star si albums Argent OK
        val notSuperstar = ArtistCertSummary(certs(CertLevel.GOLD, 5), certs(CertLevel.GOLD, 1) + certs(CertLevel.SILVER, 1))
        assertEquals(PantheonStatus.STAR, PantheonRules.evaluate(0, notSuperstar).status)

        val megastar = ArtistCertSummary(certs(CertLevel.DIAMOND, 3) + certs(CertLevel.PLATINUM, 2), certs(CertLevel.PLATINUM, 2))
        assertEquals(PantheonStatus.MEGASTAR, PantheonRules.evaluate(0, megastar).status)
    }

    @Test
    fun `mythique exige 2 titres et 2 albums a chaque niveau`() {
        val legende = ArtistCertSummary(certs(CertLevel.DIAMOND, 5), certs(CertLevel.DIAMOND, 2))
        assertEquals(PantheonStatus.LEGENDE, PantheonRules.evaluate(0, legende).status)

        val mythique = ArtistCertSummary(
            certs(CertLevel.DIAMOND, 5) + certs(CertLevel.PLATINUM, 2) + certs(CertLevel.GOLD, 2) + certs(CertLevel.SILVER, 2),
            certs(CertLevel.DIAMOND, 2) + certs(CertLevel.PLATINUM, 2) + certs(CertLevel.GOLD, 2) + certs(CertLevel.SILVER, 2)
        )
        assertEquals(PantheonStatus.MYTHIQUE, PantheonRules.evaluate(0, mythique).status)
    }

    @Test
    fun `le meilleur des deux chemins gagne`() {
        val star = ArtistCertSummary(certs(CertLevel.SILVER, 5), certs(CertLevel.SILVER, 2))
        val eval = PantheonRules.evaluate(1300, star)
        assertEquals(PantheonStatus.MEGASTAR, eval.status)
        assertTrue(eval.viaPlays)
    }

    @Test
    fun `statut suivant`() {
        assertEquals(PantheonStatus.STAR, PantheonRules.next(null))
        assertEquals(PantheonStatus.LEGENDE, PantheonRules.next(PantheonStatus.MEGASTAR))
        assertNull(PantheonRules.next(PantheonStatus.MYTHIQUE))
    }
}
