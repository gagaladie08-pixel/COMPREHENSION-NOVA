package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Règles des records 25-30 + Fastest Rise (exemples du cahier des charges). */
class RecordMathExtensionTest {

    private fun a(idx: Int, pos: Int) = RecordAppearance(idx, "d$idx", pos, 1)

    @Test
    fun `lifespan compte les absences, inclusif, minimum 2 apparitions`() {
        // lundi (0) et mercredi (2) dans le Top 10, mardi absent → 3 jours
        val s = listOf(a(0, 5), a(2, 8))
        assertEquals(3.0, RecordMath.lifespan(s, 10)!!.value, 0.0)
        // une seule apparition → non classé
        assertNull(RecordMath.lifespan(listOf(a(0, 1)), 10))
        // la zone compte : #15 n'est pas dans le Top 10
        assertNull(RecordMath.lifespan(listOf(a(0, 5), a(2, 15)), 10))
        assertEquals(3.0, RecordMath.lifespan(listOf(a(0, 5), a(2, 15)), 20)!!.value, 0.0)
    }

    @Test
    fun `re-entries compte des la premiere periode manquee`() {
        // lundi classé, mardi absent, mercredi de retour = 1 retour
        assertEquals(1.0, RecordMath.reentries(listOf(a(0, 3), a(2, 4)), 10)!!.value, 0.0)
        // consécutif = aucun retour
        assertNull(RecordMath.reentries(listOf(a(0, 3), a(1, 4), a(2, 2)), 10))
        // sortie de la ZONE (pas du chart) = retour : #8 → #15 → #9 en Top 10
        assertEquals(1.0, RecordMath.reentries(listOf(a(0, 8), a(1, 15), a(2, 9)), 10)!!.value, 0.0)
        // deux retours
        assertEquals(2.0, RecordMath.reentries(listOf(a(0, 1), a(3, 1), a(10, 1)), 10)!!.value, 0.0)
    }

    @Test
    fun `longest absence mesure la plus longue pause`() {
        val r = RecordMath.longestAbsence(listOf(a(0, 1), a(3, 2), a(10, 9)), 10)!!
        assertEquals(6.0, r.value, 0.0)
        assertEquals("d10", r.date)
        assertNull(RecordMath.longestAbsence(listOf(a(0, 1), a(1, 2)), 10))
    }

    @Test
    fun `fastest rise exclut les entrees directes et compte en periodes calendaires`() {
        // entré #12, Top 10 dès la période suivante → 1
        assertEquals(1.0, RecordMath.fastestRise(listOf(a(0, 12), a(1, 7)), 10)!!.value, 0.0)
        // entré directement dans la zone → exclu
        assertNull(RecordMath.fastestRise(listOf(a(0, 7), a(1, 3)), 10))
        // absences comprises : entré idx 0, Top 3 à idx 4 → 4
        assertEquals(4.0, RecordMath.fastestRise(listOf(a(0, 30), a(4, 2)), 3)!!.value, 0.0)
        // jamais atteint
        assertNull(RecordMath.fastestRise(listOf(a(0, 30), a(4, 12)), 3))
    }

    @Test
    fun `listening streak en jours consecutifs`() {
        assertEquals(Triple(3, 10, 12), RecordMath.longestDayStreak(listOf(1, 10, 11, 12, 20, 21)))
        assertEquals(Triple(1, 5, 5), RecordMath.longestDayStreak(listOf(5)))
        assertNull(RecordMath.longestDayStreak(emptyList()))
        // doublons tolérés
        assertEquals(Triple(2, 3, 4), RecordMath.longestDayStreak(listOf(3, 3, 4)))
    }

    @Test
    fun `podium sweep standard et solo`() {
        // titres 1,2,3 : 1 = X solo, 2 = Y feat X, 3 = X solo
        val owners = mapOf(1L to setOf(10L), 2L to setOf(20L, 10L), 3L to setOf(10L))
        val (std, solo) = RecordMath.sweepOwners(listOf(1, 2, 3), { owners[it].orEmpty() }, { owners[it]!!.size == 1 })
        assertEquals(setOf(10L), std)      // X balaie en Standard (feat. compris)
        assertEquals(emptySet<Long>(), solo) // pas en Solo (titre 2 a un featuring)
        // tout solo du même artiste → les deux
        val only = mapOf(1L to setOf(10L), 2L to setOf(10L), 3L to setOf(10L))
        val (std2, solo2) = RecordMath.sweepOwners(listOf(1, 2, 3), { only[it].orEmpty() }, { true })
        assertEquals(setOf(10L), std2); assertEquals(setOf(10L), solo2)
        // zone partielle (un titre d'un autre artiste) → personne
        val mixed = mapOf(1L to setOf(10L), 2L to setOf(30L), 3L to setOf(10L))
        assertEquals(emptySet<Long>(), RecordMath.sweepOwners(listOf(1, 2, 3), { mixed[it].orEmpty() }, { true }).first)
    }

    @Test
    fun `catalogue : 30 records, Most Records exclu de lui-meme via les groupes`() {
        assertEquals(30, RecordCatalog.ALL.map { it.number }.distinct().size)
        assertEquals(RecordGroup.PALMARES, RecordCatalog.groupOf["MOST_RECORDS"])
        assertEquals(RecordGroup.PALMARES, RecordGroup.entries.first())
        assertEquals(5, RecordCatalog.byId("FASTEST_RISE")!!.subs[RecordCategory.TRACK]!!.size)
        assertEquals(6, RecordCatalog.byId("PODIUM_SWEEP")!!.subs[RecordCategory.ARTIST]!!.size)
    }
}
