package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YearEndRulesTest {

    @Test
    fun `points inverses et bornes valides`() {
        assertEquals(100, YearEndRules.pointsFor(1, 100))
        assertEquals(1, YearEndRules.pointsFor(100, 100))
        assertEquals(50, YearEndRules.pointsFor(1, 50))
        assertEquals(1, YearEndRules.pointsFor(75, 75))
        assertEquals(0, YearEndRules.pointsFor(0, 100))
        assertEquals(0, YearEndRules.pointsFor(101, 100))
    }

    @Test
    fun `seuil recurrent 20 semaines et rang 50`() {
        assertFalse(YearEndRules.becomesRecurrent(19, 51))
        assertFalse(YearEndRules.becomesRecurrent(20, 50))
        assertTrue(YearEndRules.becomesRecurrent(20, 51))
    }

    @Test
    fun `seuil recurrent 26 semaines et rang 25`() {
        assertFalse(YearEndRules.becomesRecurrent(25, 26))
        assertFalse(YearEndRules.becomesRecurrent(26, 25))
        assertTrue(YearEndRules.becomesRecurrent(26, 26))
    }

    @Test
    fun `seuil recurrent 52 semaines et rang 10`() {
        assertFalse(YearEndRules.becomesRecurrent(51, 11))
        assertFalse(YearEndRules.becomesRecurrent(52, 10))
        assertTrue(YearEndRules.becomesRecurrent(52, 11))
    }

}
