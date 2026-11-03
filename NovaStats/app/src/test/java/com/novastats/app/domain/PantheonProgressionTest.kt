package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PantheonProgressionTest {
    private fun event(type: String, id: Long, level: CertLevel, at: Long, multiplier: Int = 1) =
        PantheonCertificationEvent(type, id, Certification(level, multiplier), at)

    @Test
    fun `date de certification Star = dernier certificat manquant`() {
        val events = buildList {
            repeat(5) { add(event("TRACK", it.toLong(), CertLevel.SILVER, 100L + it)) }
            repeat(2) { add(event("ALBUM", it.toLong(), CertLevel.SILVER, 200L + it)) }
        }
        val dates = PantheonProgression.certificationDates(events)
        assertEquals(201L, dates[PantheonStatus.STAR])
        assertNull(dates[PantheonStatus.SUPERSTAR])
    }

    @Test
    fun `palier renvoie la premiere voie disponible, ou les deux a egalite`() {
        assertEquals(PantheonPathDate(10L, PantheonRoute.CERTIFICATIONS), PantheonProgression.earliest(20L, 10L))
        assertEquals(PantheonPathDate(10L, PantheonRoute.PLAYS), PantheonProgression.earliest(10L, 20L))
        assertEquals(PantheonPathDate(10L, PantheonRoute.BOTH), PantheonProgression.earliest(10L, 10L))
        assertNull(PantheonProgression.earliest(null, null))
    }

    @Test
    fun `les ecoutes ne comptent pas dans le parcours de certifications`() {
        val onlyTracks = (0 until 5).map { event("TRACK", it.toLong(), CertLevel.SILVER, 10L) }
        assertNull(PantheonProgression.certificationDates(onlyTracks)[PantheonStatus.STAR])
    }

    @Test
    fun `le statut mythique attend les conditions exactes de chaque niveau`() {
        val events = buildList {
            var id = 0L
            for (level in CertLevel.entries) {
                val tracks = if (level == CertLevel.DIAMOND) 5 else 2
                repeat(tracks) { add(event("TRACK", id++, level, 100L + level.ordinal)) }
                repeat(2) { add(event("ALBUM", id++, level, 100L + level.ordinal)) }
            }
        }
        val dates = PantheonProgression.certificationDates(events)
        assertEquals(103L, dates[PantheonStatus.MYTHIQUE])
    }
}
