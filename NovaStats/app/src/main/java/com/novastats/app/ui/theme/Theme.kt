package com.novastats.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val LocalNovaTheme = staticCompositionLocalOf { NovaThemes.DEFAULT }

/** Accès rapide au thème NovaStats courant depuis n'importe quel composable. */
object Nova {
    val theme: NovaTheme
        @Composable get() = LocalNovaTheme.current
}

private val NovaTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = Typography().labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
)

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
    CompositionLocalProvider(LocalNovaTheme provides theme) {
        MaterialTheme(colorScheme = scheme, typography = NovaTypography, content = content)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
