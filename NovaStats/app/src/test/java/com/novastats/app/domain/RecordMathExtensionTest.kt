package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règles des records 25-37 + Fastest Rise et paliers d'écoutes (exemples du cahier des charges). */
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
    fun `debut simultane au numero un exige titre album et meme periode`() {
        val tracks = mapOf(
            1L to listOf(a(1, 1), a(2, 5)),
            2L to listOf(a(1, 1)), // même artiste et même date : ne compte pas deux fois
            3L to listOf(a(1, 1)), // album classé #1, mais pas encore à sa première apparition
            4L to listOf(a(1, 2))  // entrée du titre pas au #1
        )
        val albums = mapOf(
            10L to listOf(RecordAppearance(1, "album-date-in-same-period", 1, 1), a(2, 4)),
            11L to listOf(a(2, 1)),
            12L to listOf(a(1, 1)),
            13L to listOf(a(1, 2))
        )

        val result = RecordMath.simultaneousNo1Debuts(
            trackSeries = tracks,
            albumSeries = albums,
            albumForTrack = mapOf(1L to 10L, 2L to 10L, 3L to 11L, 4L to 12L),
            artistForAlbum = mapOf(10L to 100L, 11L to 100L, 12L to 100L, 13L to 100L),
            artistsForTrack = mapOf(1L to setOf(100L), 2L to setOf(100L), 3L to setOf(200L), 4L to setOf(100L))
        )

        val match = result.getValue(100L).single()
        assertEquals(1, match.periodIndex)
        assertEquals("d1", match.trackDate)
        assertEquals("album-date-in-same-period", match.albumDate)
        assertEquals(1L, match.trackId)
        assertEquals(10L, match.albumId)
    }

    @Test
    fun `paliers de vitesse ne sont exposes qu'une fois atteints`() {
        val milestones = ListeningMilestoneAccumulator(step = 25)
        for (play in 1..60) {
            val at = play * 1_000L
            milestones.addPlay(entityId = 1L, atMs = at)
            if (play <= 24) milestones.addPlay(entityId = 2L, atMs = at)
        }

        val reached = milestones.results()
        assertEquals(setOf(25, 50), reached.keys)
        assertEquals(1, reached.getValue(25).size)
        assertEquals(1L, reached.getValue(25).single().entityId)
        assertEquals(24_000L, reached.getValue(25).single().elapsedMs)
        assertEquals(49_000L, reached.getValue(50).single().elapsedMs)
    }

    @Test
    fun `pas des paliers et durees du record local`() {
        assertEquals(25, RecordCatalog.fastestListenStep(RecordCategory.TRACK))
        assertEquals(100, RecordCatalog.fastestListenStep(RecordCategory.ARTIST))
        assertEquals(50, RecordCatalog.fastestListenStep(RecordCategory.ALBUM))
        val record = RecordCatalog.byId("FASTEST_LISTEN_MILESTONE")!!
        assertEquals(37, record.number)
        assertEquals(RecordUnit.DURATION_MS, record.unit)
        assertTrue(record.ascending)
        assertEquals(RecordGroup.SPEED, RecordCatalog.groupOf[record.id])
        assertEquals("100 écoutes", RecordCatalog.subLabel(record, RecordCategory.ARTIST, "100"))
        val simultaneousDebuts = RecordCatalog.byId("SIMULTANEOUS_NO1_DEBUT")!!
        assertEquals(36, simultaneousDebuts.number)
        assertEquals(RecordUnit.COUNT, simultaneousDebuts.unit)
        assertEquals(listOf(RecordCategory.ARTIST), simultaneousDebuts.categories)
        assertEquals(RecordGroup.DEBUT, RecordCatalog.groupOf[simultaneousDebuts.id])
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
    fun `multi chart exige les echelles choisies et top2 dans chacune`() {
        val ranks = mapOf("D" to 2, "W" to 1, "M" to 3, "Y" to 2)
        assertTrue(RecordMath.multiChartQualifies("DWMY", ranks))
        assertTrue(RecordMath.multiChartQualifies("DW_TOP2", ranks))
        assertFalse(RecordMath.multiChartQualifies("DWMY_TOP2", ranks)) // #3 mensuel bloque le Grand Chelem Top 2
        assertFalse(RecordMath.multiChartQualifies("DWMY", ranks - "Y")) // l'annuel est obligatoire
        assertTrue(RecordMath.multiChartQualifies("DWMY_TOP2", mapOf("D" to 1, "W" to 2, "M" to 2, "Y" to 1)))
        assertEquals("DWMY", RecordCatalog.multiChartBase("DWMY_TOP2"))
        assertTrue(RecordCatalog.multiChartIsTop2("DWMY_TOP2"))
    }

    @Test
    fun `catalogue 37 records et groupes`() {
        assertEquals(37, RecordCatalog.ALL.map { it.number }.distinct().size)
        assertEquals(RecordGroup.DURATION, RecordCatalog.groupOf["MOST_CUMULATIVE_TOP5"])
        assertTrue(RecordCatalog.byId("MOST_CUMULATIVE_TOP5")!!.title.contains("Top 5"))
        val cumulativeNoOne = RecordCatalog.byId("MOST_CUMULATIVE_ARTIST_NO1")!!
        assertEquals(33, cumulativeNoOne.number)
        assertEquals(listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY), cumulativeNoOne.periods)
        assertEquals(listOf(RecordCategory.ARTIST), cumulativeNoOne.categories)
        assertEquals(
            listOf(ArtistChartNumberOnes.HOT_100, ArtistChartNumberOnes.ALBUMS_75),
            cumulativeNoOne.subs.getValue(RecordCategory.ARTIST).map { it.dbName }
        )
        assertTrue(ArtistChartNumberOnes.ARTIST_50 !in cumulativeNoOne.subs.getValue(RecordCategory.ARTIST).map { it.dbName })
        val perfectAllKill = RecordCatalog.byId("MOST_PERFECT_ALL_KILL")!!
        assertEquals(34, perfectAllKill.number)
        assertEquals(RecordUnit.COUNT, perfectAllKill.unit)
        assertEquals(listOf(Period.DAILY, Period.WEEKLY, Period.MONTHLY, Period.YEARLY), perfectAllKill.periods)
        assertEquals(listOf(RecordCategory.ARTIST), perfectAllKill.categories)
        assertEquals(RecordGroup.TOTALS, RecordCatalog.groupOf[perfectAllKill.id])
        assertTrue(perfectAllKill.id in RecordCatalog.MAJOR)
        assertEquals(RecordGroup.DOMINATION, RecordCatalog.groupOf["MOST_SONGS_TOP5"])
        assertEquals(RecordUnit.COUNT, RecordCatalog.byId("MOST_SONGS_TOP5")!!.unit)
        assertEquals(RecordGroup.PALMARES, RecordCatalog.groupOf["MOST_RECORDS"])
        assertEquals(RecordGroup.PALMARES, RecordGroup.entries.first())
        assertEquals(5, RecordCatalog.byId("FASTEST_RISE")!!.subs[RecordCategory.TRACK]!!.size)
        assertEquals(8, RecordCatalog.byId("PODIUM_SWEEP")!!.subs[RecordCategory.ARTIST]!!.size)
        assertEquals(10, RecordCatalog.byId("MULTI_CHART")!!.subs[RecordCategory.TRACK]!!.size)
    }
}
