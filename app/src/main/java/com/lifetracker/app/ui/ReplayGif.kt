package com.lifetracker.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.lifetracker.app.data.GifEncoder
import com.lifetracker.app.data.PlaceEntity
import com.lifetracker.app.data.ReplayGifRules
import com.lifetracker.app.data.ReplayRules
import com.lifetracker.app.data.TripEntity
import java.io.OutputStream
import java.time.ZoneId
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** The colours of the picture, as ARGB. */
internal class GifColors(val background: Int, val ink: Int, val ink2: Int, val accent: Int, val card: Int)

/** What the user chose for the GIF. */
internal class GifChoice(val sizePx: Int, val seconds: Int, val fitDay: Boolean, val speed: Double, val skip: Boolean, val smart: ReplayRules.Smart?)

/**
 * Draws the replay frame by frame (the path on plain paper, not map tiles, so it works offline and never waits for a map) and
 * writes each frame to [out] as it goes. [onProgress] gets 0 to 1. Stops quickly when the coroutine is cancelled.
 */
internal suspend fun makeReplayGif(
    out: OutputStream,
    samples: List<ReplayRules.Sample>,
    events: List<ReplayRules.Event>,
    places: List<PlaceEntity>,
    trips: List<TripEntity>,
    startMs: Long,
    endMs: Long,
    quiet: List<ReplayRules.Quiet>,
    choice: GifChoice,
    colors: GifColors,
    onProgress: (Float) -> Unit,
) {
    val size = choice.sizePx
    val speed = if (choice.fitDay) ReplayGifRules.fitSpeed(startMs, endMs, choice.skip, quiet, choice.seconds, choice.smart, samples, events) else choice.speed
    val times = ReplayGifRules.frameTimes(startMs, endMs, speed, choice.skip, quiet, ReplayGifRules.framesFor(choice.seconds), choice.smart, samples, events)
    val projection = ReplayGifRules.Projection(samples, size, size / 10)
    val zone = ZoneId.systemDefault()

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pixels = IntArray(size * size)
    val frame = ByteArray(size * size)
    val gif = GifEncoder(out, size, size, ReplayGifRules.PALETTE)

    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }

    fun pathOf(part: List<Pair<Double, Double>>): Path {
        val p = Path()
        part.forEachIndexed { i, pt ->
            val x = projection.x(pt.second)
            val y = projection.y(pt.first)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        return p
    }

    // The whole day's path, faint, so the way ahead can be seen.
    val faint = ReplayRules.trail(samples, endMs).map { pathOf(it) }
    var cardTitle = ""
    var cardUntil = -1
    var previous = startMs
    try {
        times.forEachIndexed { i, clock ->
            coroutineContext.ensureActive()
            val hit = ReplayRules.crossed(events, previous - 1, clock)
            if (hit.isNotEmpty()) {
                cardTitle = ReplayRules.clockText(hit.last().ms, zone) + " " + hit.last().title
                cardUntil = i + ReplayGifRules.FPS * 2
            }
            previous = clock

            canvas.drawColor(colors.background)
            for (p in places.filter { !it.archived }) {
                val r = projection.pixels(p.radiusM.toDouble())
                fill.color = colors.accent
                fill.alpha = 50
                canvas.drawCircle(projection.x(p.lng), projection.y(p.lat), maxOf(r, 3f), fill)
            }
            line.strokeWidth = size / 120f + 1f
            line.color = colors.ink2
            line.alpha = 70
            faint.forEach { canvas.drawPath(it, line) }
            line.strokeWidth = size / 80f + 1.5f
            line.color = colors.accent
            line.alpha = 255
            ReplayRules.trail(samples, clock).forEach { canvas.drawPath(pathOf(it), line) }

            ReplayRules.positionAt(samples, clock)?.let { here ->
                val x = projection.x(here.second)
                val y = projection.y(here.first)
                val trip = trips.firstOrNull { clock in it.startMs..it.endMs }
                val icon = if (trip != null) modeIcon(trip.mode) else "📍"
                fill.color = colors.card
                fill.alpha = 230
                canvas.drawCircle(x, y, size * 0.055f, fill)
                text.color = colors.ink
                text.textSize = size * 0.07f
                text.textAlign = Paint.Align.CENTER
                canvas.drawText(icon, x, y + size * 0.025f, text)
            }

            text.color = colors.ink
            text.textSize = size * 0.075f
            text.textAlign = Paint.Align.LEFT
            canvas.drawText(ReplayRules.clockText(clock, zone), size * 0.04f, size * 0.095f, text)

            if (i <= cardUntil && cardTitle.isNotEmpty()) {
                text.textSize = size * 0.045f
                val w = minOf(text.measureText(cardTitle) + size * 0.06f, size * 0.94f)
                val box = RectF(size * 0.03f, size * 0.88f, size * 0.03f + w, size * 0.96f)
                fill.color = colors.card
                fill.alpha = 235
                canvas.drawRoundRect(box, size * 0.02f, size * 0.02f, fill)
                text.color = colors.ink
                canvas.drawText(ellipsize(cardTitle, text, w - size * 0.04f), box.left + size * 0.03f, box.bottom - size * 0.025f, text)
            }

            bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
            for (k in pixels.indices) frame[k] = ReplayGifRules.paletteIndex(pixels[k]).toByte()
            gif.addFrame(frame, 100 / ReplayGifRules.FPS)
            onProgress((i + 1f) / times.size)
        }
        gif.finish()
    } finally {
        bitmap.recycle()
    }
}

private fun ellipsize(s: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(s) <= maxWidth) return s
    var n = s.length
    while (n > 1 && paint.measureText(s.substring(0, n) + "...") > maxWidth) n--
    return s.substring(0, n) + "..."
}
