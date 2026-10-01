package com.novastats.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalNovaTheme = staticCompositionLocalOf { NovaThemes.DEFAULT }

/** Polices résolues du thème courant (titre / corps). */
@Immutable
data class NovaFontSet(val title: FontFamily, val body: FontFamily)

val LocalNovaFonts = staticCompositionLocalOf { NovaFontSet(FontFamily.Default, FontFamily.Default) }

/** Accès rapide au thème NovaStats courant depuis n'importe quel composable. */
object Nova {
    val theme: NovaTheme
        @Composable get() = LocalNovaTheme.current

    val fonts: NovaFontSet
        @Composable get() = LocalNovaFonts.current

    /** Forme des cartes selon le thème (angles vifs, coupés ou arrondis). */
    val cardShape: Shape
        @Composable get() = shapeFor(theme, theme.cornerDp.toFloat())

    /** Forme des petits éléments (chips, pastilles). */
    val chipShape: Shape
        @Composable get() = shapeFor(theme, (theme.cornerDp / 2f).coerceAtLeast(2f))

    /**
     * Couleur « texte secondaire » thématisée : Bad Angel alterne blanc / rouge à chaque changement
     * d'onglet (contraste qui bascule). Les autres thèmes renvoient [NovaTheme.textSecondary].
     */
    val dualAccent: Color
        @Composable get() {
            val t = theme
            return if (t.signature == Signature.DUAL_CONTRAST) (if (ThemeEvents.dualPhase) t.secondary else t.primary) else t.primary
        }

    /** Pinceau arc-en-ciel rotatif (Survivor) ou null si le thème n'en a pas. */
    val rainbowBrush: Brush?
        @Composable get() = if (theme.rainbowTextSecondary) rememberRainbowBrush() else null
}

fun shapeFor(theme: NovaTheme, radiusDp: Float): Shape = when {
    theme.icons == IconStyle.SPIKY -> CutCornerShape((radiusDp + 6f).dp)
    radiusDp <= 0f -> RoundedCornerShape(0.dp)
    else -> RoundedCornerShape(radiusDp.dp)
}

/** Typographie Material construite à partir des deux polices du thème. */
fun novaTypography(fonts: NovaFontSet, theme: NovaTheme): Typography {
    val d = Typography()
    val title = fonts.title
    val body = fonts.body
    // Les polices « display » (Bebas, Anton, Bungee…) sont déjà massives : on module la graisse.
    val heavy = when (theme.titleFont) {
        "Bebas Neue", "Anton", "Archivo Black", "Bungee", "Abril Fatface", "Audiowide", "Righteous", "Unica One", "DM Serif Display" -> FontWeight.Normal
        else -> FontWeight.Bold
    }
    val titleSpacing = when (theme.titleFont) {
        "Orbitron", "Bebas Neue", "Anton", "Cinzel" -> 1.2.sp
        else -> 0.sp
    }
    fun TextStyle.t(w: FontWeight = heavy) = copy(fontFamily = title, fontWeight = w, letterSpacing = titleSpacing)
    fun TextStyle.b(w: FontWeight? = null) = copy(fontFamily = body, fontWeight = w ?: fontWeight)
    return Typography(
        displayLarge = d.displayLarge.t(), displayMedium = d.displayMedium.t(), displaySmall = d.displaySmall.t(),
        headlineLarge = d.headlineLarge.t(), headlineMedium = d.headlineMedium.t(), headlineSmall = d.headlineSmall.t(),
        titleLarge = d.titleLarge.t(), titleMedium = d.titleMedium.t(if (heavy == FontWeight.Normal) FontWeight.Normal else FontWeight.SemiBold),
        titleSmall = d.titleSmall.t(if (heavy == FontWeight.Normal) FontWeight.Normal else FontWeight.SemiBold),
        bodyLarge = d.bodyLarge.b(), bodyMedium = d.bodyMedium.b(), bodySmall = d.bodySmall.b(),
        labelLarge = d.labelLarge.b(FontWeight.SemiBold), labelMedium = d.labelMedium.b(FontWeight.SemiBold),
        labelSmall = d.labelSmall.b(FontWeight.Bold).copy(letterSpacing = 1.2.sp)
    )
}

@Composable
fun NovaStatsTheme(theme: NovaTheme = NovaThemes.DEFAULT, content: @Composable () -> Unit) {
    val onPrimary = if (theme.primary.luminance() > 0.5f) Color.Black else Color.White
    val scheme = if (theme.isLight) {
        lightColorScheme(
            primary = theme.primary, onPrimary = onPrimary,
            secondary = theme.secondary, onSecondary = theme.text,
            tertiary = theme.accent,
            background = theme.background, onBackground = theme.text,
            surface = theme.surface, onSurface = theme.text,
            surfaceVariant = theme.surface, onSurfaceVariant = theme.textSecondary,
            outline = theme.textSecondary.copy(alpha = 0.4f)
        )
    } else {
        darkColorScheme(
            primary = theme.primary, onPrimary = onPrimary,
            secondary = theme.secondary, onSecondary = theme.text,
            tertiary = theme.accent,
            background = theme.background, onBackground = theme.text,
            surface = theme.surface, onSurface = theme.text,
            surfaceVariant = theme.surface, onSurfaceVariant = theme.textSecondary,
            outline = theme.textSecondary.copy(alpha = 0.4f)
        )
    }
    val ctx = LocalContext.current
    val online = remember { NovaFonts.isProviderAvailable(ctx) }
    val fonts = remember(theme.titleFont, theme.bodyFont, online) {
        NovaFontSet(NovaFonts.family(theme.titleFont, online), NovaFonts.family(theme.bodyFont, online))
    }
    val typography = remember(fonts, theme.id) { novaTypography(fonts, theme) }
    val r = theme.cornerDp.toFloat()
    val shapes = remember(theme.id) {
        Shapes(
            extraSmall = shapeFor(theme, (r / 4f).coerceAtLeast(1f)),
            small = shapeFor(theme, (r / 2f).coerceAtLeast(2f)),
            medium = shapeFor(theme, r.coerceAtLeast(2f) * 0.75f),
            large = shapeFor(theme, r.coerceAtLeast(2f)),
            extraLarge = shapeFor(theme, r.coerceAtLeast(2f) * 1.5f)
        )
    }
    CompositionLocalProvider(LocalNovaTheme provides theme, LocalNovaFonts provides fonts) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
