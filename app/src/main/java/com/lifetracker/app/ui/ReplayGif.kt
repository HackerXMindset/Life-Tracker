package com.lifetracker.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.content.Context
import com.lifetracker.app.data.MapEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import kotlin.coroutines.resume
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

/** A picture of the real map behind the GIF and how to find a place on it. */
internal class GifBase(val bitmap: Bitmap, private val snap: org.maplibre.android.snapshotter.MapSnapshot, private val oneMetre: Float) {
    fun point(lat: Double, lng: Double): PointF = snap.pixelForLatLng(LatLng(lat, lng))
    fun pixels(metres: Double): Float = (metres * oneMetre).toFloat()
}

/**
 * Asks the map library for one picture of the map (your chosen style) that fits the whole stretch, the same size as the GIF.
 * Null when it cannot be made (no internet and nothing saved, a style that will not load, or too slow); the GIF then uses plain paper.
 */
internal suspend fun loadGifBase(context: Context, styleUrl: String, samples: List<ReplayRules.Sample>, size: Int): GifBase? {
    if (samples.isEmpty()) return null
    val projection = ReplayGifRules.Projection(samples, size, size / 10)
    return withTimeoutOrNull(45_000L) {
        withContext(Dispatchers.Main) {
            MapEngine.start(context)
            var snapshotter: MapSnapshotter? = null
            try {
                suspendCancellableCoroutine { cont ->
                    val options = MapSnapshotter.Options(size, size)
                        .withPixelRatio(1f)
                        .withStyleBuilder(Style.Builder().fromUri(styleUrl))
                        .withCameraPosition(
                            CameraPosition.Builder().target(LatLng(projection.centerLat, projection.centerLng)).zoom(projection.mapZoom()).build(),
                        )
                    val shooter = MapSnapshotter(context.applicationContext, options)
                    snapshotter = shooter
                    cont.invokeOnCancellation { shooter.cancel() }
                    shooter.start(
                        { snap ->
                            val a = snap.pixelForLatLng(LatLng(projection.centerLat, projection.centerLng))
                            val b = snap.pixelForLatLng(LatLng(projection.centerLat, projection.centerLng + 0.001))
                            val metresPerDegree = 111_320.0 * kotlin.math.cos(Math.toRadians(projection.centerLat))
                            val oneMetre = (kotlin.math.abs(b.x - a.x) / (0.001 * metresPerDegree)).toFloat()
                            if (cont.isActive) cont.resume(GifBase(snap.bitmap, snap, oneMetre))
                        },
                        { _ -> if (cont.isActive) cont.resume(null) },
                    )
                }
            } finally {
                snapshotter?.cancel()
            }
        }
    }
}

/**
 * Draws the replay frame by frame (on the map picture [base], or plain paper when there is none) and
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
    base: GifBase?,
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

    fun px(lat: Double, lng: Double): PointF = base?.point(lat, lng) ?: PointF(projection.x(lng), projection.y(lat))
    fun metres(m: Double): Float = base?.pixels(m) ?: projection.pixels(m)

    fun pathOf(part: List<Pair<Double, Double>>): Path {
        val p = Path()
        part.forEachIndexed { i, pt ->
            val q = px(pt.first, pt.second)
            val x = q.x
            val y = q.y
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

            if (base != null) canvas.drawBitmap(base.bitmap, 0f, 0f, null) else canvas.drawColor(colors.background)
            for (p in places.filter { !it.archived }) {
                val r = metres(p.radiusM.toDouble())
                val c = px(p.lat, p.lng)
                fill.color = colors.accent
                fill.alpha = 50
                canvas.drawCircle(c.x, c.y, maxOf(r, 3f), fill)
            }
            line.strokeWidth = size / 120f + 1f
            line.color = if (base != null) colors.ink else colors.ink2
            line.alpha = if (base != null) 110 else 70
            faint.forEach { canvas.drawPath(it, line) }
            val travelled = ReplayRules.trail(samples, clock).map { pathOf(it) }
            if (base != null) {
                // A light edge under the path so it stands out from the streets.
                line.strokeWidth = size / 80f + 4.5f
                line.color = colors.background
                line.alpha = 230
                travelled.forEach { canvas.drawPath(it, line) }
            }
            line.strokeWidth = size / 80f + 1.5f
            line.color = colors.accent
            line.alpha = 255
            travelled.forEach { canvas.drawPath(it, line) }

            ReplayRules.positionAt(samples, clock)?.let { here ->
                val where = px(here.first, here.second)
                val x = where.x
                val y = where.y
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
