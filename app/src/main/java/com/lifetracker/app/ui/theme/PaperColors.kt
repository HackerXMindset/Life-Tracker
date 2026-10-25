package com.lifetracker.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Shared light and dark theme palettes. The light theme is clean creamy white with neutral surfaces and subtle dividers.
 */
@Immutable
class PaperColors(
    val dark: Boolean,
    /** Main screen background. */
    val bg: Color,
    /** Card, sheet, and menu surfaces. */
    val surface: Color,
    /** Secondary surface for selected tracks and empty slots. */
    val surface2: Color,
    /** Primary text and button colour. */
    val ink: Color,
    /** Secondary text. */
    val ink2: Color,
    /** Subtle divider and outline. */
    val line: Color,
    /** Green accent for positive signals. */
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    /** Warm highlight and current-time indicator. */
    val sun: Color,
    /** Error and warning colour. */
    val bad: Color,
    /** Card and control outline colour. */
    val pen: Color,
    /** Decorative shadow colour (transparent in light mode). */
    val pencil: Color,
    /** Optional background vignette (disabled in light mode). */
    val vig: Color,
)

val LightPaper = PaperColors(
    dark = false,
    bg = Color(0xFFFFFCF7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF5F5F2),
    ink = Color(0xFF202124),
    ink2 = Color(0xFF62676D),
    line = Color(0xFFE1E4E8),
    accent = Color(0xFF2E6B4F),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE8F1EA),
    sun = Color(0xFFF0A23A),
    bad = Color(0xFFB42318),
    pen = Color(0xFFE1E4E8),
    pencil = Color(0x00000000),
    vig = Color(0x00000000),
)

val DarkPaper = PaperColors(
    dark = true,
    bg = Color(0xFF1F1A14),
    surface = Color(0xFF2A231B),
    surface2 = Color(0xFF382F23),
    ink = Color(0xFFF0E3C4),
    ink2 = Color(0xFFB8A583),
    line = Color(0xFF4D4130),
    accent = Color(0xFF8FCB9F),
    accentInk = Color(0xFF1F1A14),
    accentSoft = Color(0xFF2F3D2E),
    sun = Color(0xFFF0B04A),
    bad = Color(0xFFE5806A),
    pen = Color(0x8CF0E3C4),
    pencil = Color(0x59000000),
    vig = Color(0x73000000),
)

val LocalPaper = staticCompositionLocalOf { LightPaper }
