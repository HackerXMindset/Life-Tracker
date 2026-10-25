package com.lifetracker.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * The Material colours, filled from the paper colours so every screen that has not been redrawn yet still picks up the
 * new look. Primary is ink (dark buttons, as in the demo); secondary is the green for "good".
 */
private fun scheme(p: PaperColors) = if (p.dark) {
    darkColorScheme(
        primary = p.ink, onPrimary = p.bg,
        secondary = p.accent, onSecondary = p.accentInk,
        secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink,
        tertiary = p.sun, onTertiary = p.bg,
        background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.surface2, onSurfaceVariant = p.ink2,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surface2,
        outline = p.line, outlineVariant = p.line,
        error = p.bad, onError = p.bg,
    )
} else {
    lightColorScheme(
        primary = p.ink, onPrimary = p.bg,
        secondary = p.accent, onSecondary = p.accentInk,
        secondaryContainer = p.accentSoft, onSecondaryContainer = p.ink,
        tertiary = p.sun, onTertiary = p.ink,
        background = p.bg, onBackground = p.ink,
        surface = p.surface, onSurface = p.ink,
        surfaceVariant = p.surface2, onSurfaceVariant = p.ink2,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surface2,
        outline = p.line, outlineVariant = p.line,
        error = p.bad, onError = p.accentInk,
    )
}

// Material's own parts (dialogs, menus) can only take evenly rounded corners, so they get the average of the wobbly ones.
// The wobbly shapes themselves are used by the paper parts (PaperCard, PaperButton and so on).
private val PaperShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun LifeTrackerTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember { AppTheme.ensureLoaded(context); BackgroundSettings.ensureLoaded(context) }
    val dark = ThemeModes.isDark(AppTheme.mode, isSystemInDarkTheme())
    val paper = if (dark) DarkPaper else LightPaper
    val family = remember { PaperFonts.family(context) }
    val typography = remember(family) { PaperFonts.typography(family) }

    // The clock and battery icons at the top must be dark on cream paper and light on dark paper.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    CompositionLocalProvider(LocalPaper provides paper) {
        MaterialTheme(
            colorScheme = scheme(paper),
            typography = typography,
            shapes = PaperShapes,
            content = content,
        )
    }
}
