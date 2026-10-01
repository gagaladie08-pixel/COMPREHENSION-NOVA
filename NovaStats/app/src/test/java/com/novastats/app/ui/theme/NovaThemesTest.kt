package com.novastats.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Conformité du système de thèmes à THEMES.md. */
class NovaThemesTest {
    @Test fun `15 themes, identifiants uniques, Cyber Nova par defaut`() {
        assertEquals(15, NovaThemes.ALL.size)
        assertEquals(15, NovaThemes.ALL.map { it.id }.toSet().size)
        assertEquals("cyber_nova", NovaThemes.DEFAULT.id)
        assertEquals(NovaThemes.DEFAULT, NovaThemes.byId("inconnu"))
        assertEquals(NovaThemes.DEFAULT, NovaThemes.byId(null))
    }

    @Test fun `seuls Pink Y2K et Cloud Nine sont clairs`() {
        assertEquals(setOf("pink_y2k", "cloud_nine"), NovaThemes.ALL.filter { it.isLight }.map { it.id }.toSet())
    }

    @Test fun `chaque theme a ses polices, son effet et sa transition`() {
        NovaThemes.ALL.forEach { t ->
            assertTrue(t.id, t.titleFont.isNotBlank()); assertTrue(t.id, t.bodyFont.isNotBlank())
            assertTrue(t.id, t.effects.isNotBlank()); assertTrue(t.id, t.inspiration.isNotBlank())
            assertTrue(t.id, t.transitionMs in 100..500)
        }
        // Les 15 effets signature sont tous distincts
        assertEquals(15, NovaThemes.ALL.map { it.signature }.toSet().size)
        assertEquals(15, NovaThemes.ALL.map { it.icons }.toSet().size)
    }

    @Test fun `palettes conformes au cahier des charges`() {
        fun hex(c: androidx.compose.ui.graphics.Color) = "#%02X%02X%02X".format((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
        assertEquals("#FF006E", hex(NovaThemes.CYBER_NOVA.primary)); assertEquals("#00FFF0", hex(NovaThemes.CYBER_NOVA.accent))
        assertEquals("#FFD700", hex(NovaThemes.NEON_DISCO.secondary))
        assertEquals("#FFF0F5", hex(NovaThemes.PINK_Y2K.background))
        assertEquals("#2D5A27", hex(NovaThemes.AFRICAN_CONFESSIONS.secondary))
        assertEquals("#CC0033", hex(NovaThemes.BAD_ANGEL.primary))
    }

    @Test fun `specificites de style`() {
        assertEquals(MotionEasing.CUT, NovaThemes.VILLAIN_ERA.easing)
        assertEquals(CurveShape.ANGULAR, NovaThemes.VILLAIN_ERA.chart.shape)
        assertEquals(MotionEasing.RANDOM, NovaThemes.CHAOS_BORN.easing)
        assertTrue(NovaThemes.SURVIVOR.chart.rainbow); assertTrue(NovaThemes.SURVIVOR.rainbowTextSecondary)
        assertTrue(NovaThemes.CYBER_NOVA.chart.scanReveal)
        assertTrue(NovaThemes.BAD_ANGEL.chart.alternatingGlow)
        assertEquals(0, NovaThemes.VILLAIN_ERA.cornerDp)
        assertFalse(NovaThemes.CLOUD_NINE.chart.glowDp < 10f)
        assertEquals("100-400 ms variable", NovaThemes.CHAOS_BORN.transitionLabel)
    }

    @Test fun `arc-en-ciel decale reste dans la palette`() {
        val c = rainbowColors(0.37f, 7)
        assertEquals(7, c.size)
        c.forEach { assertTrue(it.alpha == 1f) }
    }
}
