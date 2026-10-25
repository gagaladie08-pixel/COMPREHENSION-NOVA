package com.novastats.app.ui.navigation

import com.novastats.app.ui.theme.SurvivorGlyph
import org.junit.Assert.assertEquals
import org.junit.Test

class SurvivorNavigationIdentityTest {
    @Test fun `les neuf onglets Survivor ont chacun leur glyphe dessine`() {
        assertEquals(9, NovaTab.entries.size)
        assertEquals(9, NovaTab.entries.map { it.survivorGlyph }.toSet().size)
        assertEquals(SurvivorGlyph.HOME, NovaTab.HOME.survivorGlyph)
        assertEquals(SurvivorGlyph.SETTINGS, NovaTab.SETTINGS.survivorGlyph)
    }
}
