package com.novastats.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La règle d'alerte « recalcul devenu lent ». La 0.22.15 a failli perdre la bibliothèque à cause
 * d'un UPDATE en O(n²) : rien ne signalait la lenteur avant le gel. Ces tests fixent le seuil et
 * l'anti-répétition.
 */
class RebuildHealthTest {

    private fun line(ms: Long) = "07/10 13:02:11  ⏱️ Étape 1 terminée en $ms ms"

    @Test
    fun `lit la duree de l etape 1`() {
        assertEquals(57L, RebuildHealth.lastStep1Ms(listOf(line(57))))
        assertEquals(121482L, RebuildHealth.lastStep1Ms(listOf(line(121482))))
    }

    @Test
    fun `sans mesure, aucune duree`() {
        assertNull(RebuildHealth.lastStep1Ms(emptyList()))
        assertNull(
            RebuildHealth.lastStep1Ms(
                listOf(
                    "07/10 13:02:11  🔎 État au lancement — écoutes=10980/11434",
                    "07/10 13:02:12  🧮 2/8 Streaks — écoutes=10980/11434"
                )
            )
        )
    }

    @Test
    fun `c est la derniere mesure qui compte, pas la premiere`() {
        // Journal du plus récent au plus ancien : un recalcul rapide après un lent = plus d'alerte.
        val log = listOf(line(57), line(121482))
        assertEquals(57L, RebuildHealth.lastStep1Ms(log))
    }

    @Test
    fun `seuil de 15 secondes, borne incluse`() {
        assertFalse(RebuildHealth.isSlow(null))
        assertFalse(RebuildHealth.isSlow(57))
        assertFalse(RebuildHealth.isSlow(RebuildHealth.SLOW_STEP1_MS - 1))
        assertTrue(RebuildHealth.isSlow(RebuildHealth.SLOW_STEP1_MS))
        assertTrue(RebuildHealth.isSlow(121482))
    }

    @Test
    fun `une meme ligne n alerte qu une fois`() {
        val slow = line(600000)
        // Première fois : on alerte.
        assertTrue(RebuildHealth.shouldAlert(slow, null))
        // Lancement suivant, même journal : plus d'alerte.
        assertFalse(RebuildHealth.shouldAlert(slow, slow))
        // Un NOUVEAU recalcul lent est une nouvelle information : on alerte de nouveau.
        assertTrue(RebuildHealth.shouldAlert(line(900000), slow))
    }

    @Test
    fun `un recalcul rapide n alerte jamais`() {
        assertFalse(RebuildHealth.shouldAlert(line(57), null))
        assertFalse(RebuildHealth.shouldAlert(null, null))
    }

    @Test
    fun `durees lisibles`() {
        // Division entière : en dessous d'une seconde, on affiche « < 1 s », pas « 0 s ».
        assertEquals("< 1 s", RebuildHealth.humanize(57))
        assertEquals("< 1 s", RebuildHealth.humanize(999))
        assertEquals("1 s", RebuildHealth.humanize(1000))
        assertEquals("42 s", RebuildHealth.humanize(42_000))
        assertEquals("1 min 30 s", RebuildHealth.humanize(90_000))
        assertEquals("2 min 1 s", RebuildHealth.humanize(121_482))
        assertEquals("10 min 0 s", RebuildHealth.humanize(600_000))
    }
}
