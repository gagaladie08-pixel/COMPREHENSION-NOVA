package com.novastats.app.ui.navigation

import com.novastats.app.ui.theme.NovaTabGlyph
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationGlyphIdentityTest {
    @Test fun `les neuf onglets ont chacun leur glyphe semantique dessine`() {
        assertEquals(9, NovaTab.entries.size)
        assertEquals(9, NovaTab.entries.map { it.iconGlyph }.toSet().size)
        assertEquals(NovaTabGlyph.HOME, NovaTab.HOME.iconGlyph)
        assertEquals(NovaTabGlyph.SETTINGS, NovaTab.SETTINGS.iconGlyph)
    }
}
