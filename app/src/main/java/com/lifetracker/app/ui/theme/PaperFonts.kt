package com.lifetracker.app.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Space Mono, the demo's font. The two files (`SpaceMono-Regular.ttf`, `SpaceMono-Bold.ttf`) go in
 * `app/src/main/assets/fonts/`. Until they are there, the phone's own monospace font is used, so the app still works.
 */
object PaperFonts {
    private const val FOLDER = "fonts"
    private const val REGULAR = "SpaceMono-Regular.ttf"
    private const val BOLD = "SpaceMono-Bold.ttf"

    fun family(context: Context): FontFamily {
        val files = runCatching { context.assets.list(FOLDER)?.toSet() }.getOrNull().orEmpty()
        val fonts = buildList {
            if (REGULAR in files) add(Font("$FOLDER/$REGULAR", context.assets, FontWeight.Normal))
            if (BOLD in files) add(Font("$FOLDER/$BOLD", context.assets, FontWeight.Bold))
        }
        return if (fonts.isEmpty()) FontFamily.Monospace else FontFamily(fonts)
    }

    /** Material's text styles, all in [family]. */
    fun typography(family: FontFamily): Typography {
        fun TextStyle.f() = copy(fontFamily = family)
        val d = Typography()
        return Typography(
            displayLarge = d.displayLarge.f(), displayMedium = d.displayMedium.f(), displaySmall = d.displaySmall.f(),
            headlineLarge = d.headlineLarge.f(), headlineMedium = d.headlineMedium.f(), headlineSmall = d.headlineSmall.f(),
            titleLarge = d.titleLarge.f(), titleMedium = d.titleMedium.f(), titleSmall = d.titleSmall.f(),
            bodyLarge = d.bodyLarge.f(), bodyMedium = d.bodyMedium.f(), bodySmall = d.bodySmall.f(),
            labelLarge = d.labelLarge.f(), labelMedium = d.labelMedium.f(), labelSmall = d.labelSmall.f(),
        )
    }
}
