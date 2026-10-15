package com.lifetracker.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The paper look's colours, taken from the demo (Life-Tracker-paper.html): cream paper, pen ink, a lighter sheet laid
 * on top, green only for "good", an orange highlighter, and red pencil for problems.
 */
@Immutable
class PaperColors(
    val dark: Boolean,
    /** The paper itself. */
    val bg: Color,
    /** A lighter sheet laid on the paper: cards, sheets, the menu bar. */
    val surface: Color,
    /** Worn paper: empty slots, the track of a switch. */
    val surface2: Color,
    /** Pen. Main text and dark buttons. */
    val ink: Color,
    /** Pencil. Secondary text. */
    val ink2: Color,
    /** A faint ruled line. */
    val line: Color,
    /** Green ink, only for "good" signals. */
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    /** Highlighter, and the "now" hand. */
    val sun: Color,
    /** Red pencil. */
    val bad: Color,
    /** The wobbly outline of cards and buttons. */
    val pen: Color,
    /** The hard shadow under cards and buttons. */
    val pencil: Color,
    /** The darkening at the edges of the sheet. */
    val vig: Color,
)

val LightPaper = PaperColors(
    dark = false,
    bg = Color(0xFFF2E4C5),
    surface = Color(0xFFFAF0DA),
    surface2 = Color(0xFFE4D2AB),
    ink = Color(0xFF2A2118),
    ink2 = Color(0xFF6B5B44),
    line = Color(0xFFC9B38A),
    accent = Color(0xFF2E6B4F),
    accentInk = Color(0xFFFAF0DA),
    accentSoft = Color(0xFFDCE2BF),
    sun = Color(0xFFE39A2D),
    bad = Color(0xFFB4432F),
    pen = Color(0xBD2A2118),
    pencil = Color(0x26463012),
    vig = Color(0x33825C22),
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
