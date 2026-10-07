package com.novastats.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRulesTest {
    @Test fun `media session complete is 100 and not reviewed`() {
        val v = ReviewRules.evaluate("SERVE", "Xlov", "MEDIA_SESSION")
        assertEquals(100, v.score); assertNull(v.reason); assertFalse(v.needsReview)
    }

    @Test fun `complete notification is 70 and NOT in the list`() {
        val v = ReviewRules.evaluate("CHOOM", "Babymonster", "NOTIFICATION")
        assertEquals(70, v.score); assertNull(v.reason); assertFalse(v.needsReview)
    }

    @Test fun `notification without artist is 50 - artiste manquant`() {
        val v = ReviewRules.evaluate("WE GO UP", null, "NOTIFICATION")
        assertEquals(50, v.score); assertEquals("Artiste manquant", v.reason); assertTrue(v.needsReview)
    }

    @Test fun `unknown title even from media session is reviewed`() {
        val v = ReviewRules.evaluate("Unknown", "LISA", "MEDIA_SESSION")
        assertEquals(50, v.score); assertEquals("Titre inconnu", v.reason); assertTrue(v.needsReview)
    }

    @Test fun `unknown words detection`() {
        assertTrue(ReviewRules.isUnknown("<unknown>")); assertTrue(ReviewRules.isUnknown("Artiste inconnu")); assertTrue(ReviewRules.isUnknown("Track 01"))
        assertFalse(ReviewRules.isUnknown("Goals")); assertFalse(ReviewRules.isUnknown("Tracks of my tears"))
    }
}
