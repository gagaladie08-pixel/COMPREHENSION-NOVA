package com.novastats.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `formatElapsed` — « en N jours » affiché dans les pop-ups Certifications et Panthéon.
 * Kotlin pur : exécuté par `:app:testDebugUnitTest` dans la CI.
 */
class FormatElapsedTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `rien a afficher sans duree ni date`() {
        assertNull(formatElapsed(null, null, now))
    }

    @Test
    fun `duree negative ignoree`() {
        assertNull(formatElapsed(-5_000L, null, now))
    }

    @Test
    fun `repli sur la date dobtention quand la duree na pas ete memorisee`() {
        // Anciennes données : time_to_certify_ms absent, mais certified_at présent.
        assertEquals("en 3 jours", formatElapsed(null, now - 3L * 86_400_000, now))
    }

    @Test
    fun `moins dune minute`() {
        assertEquals("en moins d'une minute", formatElapsed(20_000L, null, now))
        assertEquals("en moins d'une minute", formatElapsed(0L, null, now))
    }

    @Test
    fun `minutes puis heures`() {
        assertEquals("en 45 min", formatElapsed(45L * 60_000, null, now))
        assertEquals("en 59 min", formatElapsed(59L * 60_000, null, now))
        assertEquals("en 1 h", formatElapsed(60L * 60_000, null, now))
        assertEquals("en 23 h", formatElapsed(23L * 60 * 60_000, null, now))
    }

    @Test
    fun `jours au singulier et au pluriel`() {
        assertEquals("en 1 jour", formatElapsed(86_400_000L, null, now))
        assertEquals("en 3 jours", formatElapsed(3L * 86_400_000, null, now))
        assertEquals("en 13 jours", formatElapsed(13L * 86_400_000, null, now))
    }

    @Test
    fun `semaines puis mois`() {
        assertEquals("en 2 semaines", formatElapsed(14L * 86_400_000, null, now))
        assertEquals("en 8 semaines", formatElapsed(59L * 86_400_000, null, now))
        assertEquals("en 2 mois", formatElapsed(60L * 86_400_000, null, now))
        assertEquals("en 12 mois", formatElapsed(364L * 86_400_000, null, now))
    }

    @Test
    fun `annees seules ou avec le reste en mois`() {
        assertEquals("en 1 an", formatElapsed(365L * 86_400_000, null, now))
        assertEquals("en 1 an et 2 mois", formatElapsed((365L + 61) * 86_400_000, null, now))
        assertEquals("en 2 ans", formatElapsed(2L * 365 * 86_400_000, null, now))
        assertEquals("en 3 ans", formatElapsed(3L * 365 * 86_400_000, null, now))
    }

    @Test
    fun `la duree memorisee prime sur la date`() {
        // certified_at très récent mais time_to_certify_ms renseigné : c'est la durée mémorisée qui gagne.
        assertEquals("en 7 jours", formatElapsed(7L * 86_400_000, now - 1_000L, now))
    }
}
