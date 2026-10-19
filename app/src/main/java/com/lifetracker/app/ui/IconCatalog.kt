package com.lifetracker.app.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R

/**
 * The pictures an app, an activity or a place can wear. An icon is kept as a short piece of text: either a picture's name
 * (Streak's names such as "dumbbell" or "bookOpen") or an emoji. The drawing of each name is a line of text in
 * `res/raw/icon_paths.txt` (name, a tab, then the outline), read once and turned into a shape the first time it is drawn.
 */
object IconCatalog {
    /** Names match whatever the capitals, dashes or spaces: "bookOpen", "book-open" and "Book open" are the same picture. */
    fun norm(name: String): String = name.filter { it.isLetterOrDigit() }.lowercase()

    /** Streak's rule: a value that starts above U+2000 is an emoji, anything else is a picture name. */
    fun isEmoji(value: String): Boolean = value.isNotEmpty() && value.codePointAt(0) > 0x2000

    /** Reads the lines "name, tab, outline" into a map from the plain form of the name to its outline text. */
    fun parse(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        text.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab > 0 && tab < line.length - 1) out[norm(line.substring(0, tab))] = line.substring(tab + 1)
        }
        return out
    }

    /** True when [value] is something that can be drawn: an emoji or a name this app has a picture for. */
    fun drawable(context: Context, value: String): Boolean =
        value.isNotEmpty() && (isEmoji(value) || outlines(context).containsKey(norm(value)))

    @Volatile
    private var loaded: Map<String, String>? = null
    private val shapes = HashMap<String, Path>()

    private fun outlines(context: Context): Map<String, String> =
        loaded ?: synchronized(this) {
            loaded ?: parse(context.applicationContext.resources.openRawResource(R.raw.icon_paths).bufferedReader().use { it.readText() })
                .also { loaded = it }
        }

    /** The shape for a picture name (drawn on a 24 by 24 square), or null if there is none. */
    fun shape(context: Context, name: String): Path? {
        val key = norm(name)
        shapes[key]?.let { return it }
        val text = outlines(context)[key] ?: return null
        val path = PathParser().parsePathString(text).toPath()
        shapes[key] = path
        return path
    }
}

/**
 * Draws [icon] (a picture name or an emoji) in [tint] at [size]. An empty or unknown icon shows [fallback] (a first
 * letter, if one is given) so nothing ever shows as a blank hole.
 */
@Composable
fun IconGlyph(icon: String, tint: Color, size: Dp, fallback: String = "") {
    val context = LocalContext.current
    val shape = remember(icon) { if (icon.isEmpty() || IconCatalog.isEmoji(icon)) null else IconCatalog.shape(context, icon) }
    when {
        icon.isNotEmpty() && IconCatalog.isEmoji(icon) -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text(text = icon, fontSize = (size.value * 0.78f).sp, maxLines = 1)
        }
        shape != null -> Canvas(Modifier.size(size)) {
            val s = this.size.minDimension / 24f
            withTransform({ scale(s, s, Offset.Zero) }) { drawPath(shape, color = tint) }
        }
        fallback.isNotEmpty() -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text(text = fallback.take(1).uppercase(), color = tint, fontSize = (size.value * 0.62f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        else -> Box(Modifier.size(size))
    }
}

/** A little round badge with the icon on it, used beside names in lists. */
@Composable
fun IconBadge(icon: String, color: Color, fallback: String, size: Dp = 28.dp) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        IconGlyph(icon = icon, tint = color, size = size * 0.8f, fallback = fallback)
    }
}
