package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CertificationRulesTest {

    @Test
    fun `seuils chansons`() {
        val t = CertificationRules.TRACK
        assertNull(t.current(24))
        assertEquals(Certification(CertLevel.SILVER), t.current(25))
        assertEquals(Certification(CertLevel.GOLD), t.current(50))
        assertEquals(Certification(CertLevel.PLATINUM), t.current(100))
        assertEquals(Certification(CertLevel.PLATINUM, 1), t.current(199))
        assertEquals(Certification(CertLevel.PLATINUM, 2), t.current(200))
        assertEquals(Certification(CertLevel.PLATINUM, 3), t.current(300))
        assertEquals(Certification(CertLevel.PLATINUM, 3), t.current(349))
        assertEquals(Certification(CertLevel.DIAMOND, 1), t.current(350))
        assertEquals(Certification(CertLevel.DIAMOND, 2), t.current(700))
        assertEquals(Certification(CertLevel.DIAMOND, 4), t.current(1400))
    }

    @Test
    fun `seuils albums`() {
        val a = CertificationRules.ALBUM
        assertEquals(Certification(CertLevel.SILVER), a.current(50))
        assertEquals(Certification(CertLevel.GOLD), a.current(100))
        assertEquals(Certification(CertLevel.PLATINUM), a.current(200))
        assertEquals(Certification(CertLevel.PLATINUM, 2), a.current(400))
        assertEquals(Certification(CertLevel.PLATINUM, 3), a.current(699))
        assertEquals(Certification(CertLevel.DIAMOND, 1), a.current(700))
        assertEquals(Certification(CertLevel.DIAMOND, 3), a.current(2100))
    }

    @Test
    fun `radar - prochain palier et restant`() {
        val t = CertificationRules.TRACK
        assertEquals(Certification(CertLevel.SILVER), t.next(0))
        assertEquals(25, t.remainingToNext(0))
        assertEquals(Certification(CertLevel.PLATINUM, 2), t.next(120))
        assertEquals(80, t.remainingToNext(120))
        assertEquals(Certification(CertLevel.PLATINUM, 3), t.next(200))
        assertEquals(Certification(CertLevel.DIAMOND), t.next(300))
        assertEquals(50, t.remainingToNext(300))
        assertEquals(Certification(CertLevel.DIAMOND, 2), t.next(350))
        assertEquals(350, t.remainingToNext(350))
    }

    @Test
    fun `paliers atteints pour dates retroactives`() {
        val reached = CertificationRules.TRACK.allReached(720)
        assertEquals(
            listOf(
                Certification(CertLevel.SILVER), Certification(CertLevel.GOLD),
                Certification(CertLevel.PLATINUM, 1), Certification(CertLevel.PLATINUM, 2), Certification(CertLevel.PLATINUM, 3),
                Certification(CertLevel.DIAMOND, 1), Certification(CertLevel.DIAMOND, 2)
            ),
            reached
        )
    }
}
