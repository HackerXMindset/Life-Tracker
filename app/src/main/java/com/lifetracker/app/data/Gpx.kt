package com.lifetracker.app.data

import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/**
 * Reading and writing position readings as GPX, the plain track file almost every mapping app and logger understands
 * (GPSLogger, Google Earth, OsmAnd, Strava...), and as CSV. No Android in it, so it can be tested.
 *
 * A written file has one track segment for each stretch of readings, a new one after a silence of 30 minutes, and each
 * point carries its accuracy and (when the phone measured one) its speed in an `extensions` block, so reading the file
 * back gives exactly what was written. Files from other apps are read too: a point needs a position and a time; its
 * accuracy comes from `acc` if present, else from `hdop` (times 5 m), else a guess from the source (`network` 50 m,
 * `cell` 150 m, anything else 20 m).
 */
object Gpx {
    /** A silence this long starts a new track segment. */
    const val GAP_MS = 30 * 60_000L

    private const val HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<gpx version=\"1.1\" creator=\"Life Tracker\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n"

    private fun xml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun number(v: Double): String = String.format(Locale.ENGLISH, "%.7f", v)

    private fun point(out: Appendable, ms: Long, lat: Double, lng: Double, acc: Float, speed: Float) {
        out.append("<trkpt lat=\"").append(number(lat)).append("\" lon=\"").append(number(lng)).append("\"><time>")
            .append(Instant.ofEpochMilli(ms).toString()).append("</time><extensions><acc>")
            .append(Math.round(acc).toString()).append("</acc>")
        if (speed >= 0f) out.append("<speed>").append(String.format(Locale.ENGLISH, "%.2f", speed)).append("</speed>")
        out.append("</extensions></trkpt>\n")
    }

    /** Writes readings, oldest first, in as many calls to [add] as you like: one `<trk>` with a segment per stretch. */
    class Writer(private val out: Appendable, private val name: String) {
        private var segmentOpen = false
        private var lastMs = Long.MIN_VALUE

        fun begin() {
            out.append(HEADER).append("<trk><name>").append(xml(name)).append("</name>\n")
        }

        fun add(fixes: List<RawFixEntity>) {
            for (f in fixes) {
                if (!segmentOpen || f.ms - lastMs > GAP_MS) {
                    if (segmentOpen) out.append("</trkseg>\n")
                    out.append("<trkseg>\n")
                    segmentOpen = true
                }
                point(out, f.ms, f.lat, f.lng, f.acc, f.speed)
                lastMs = f.ms
            }
        }

        fun end() {
            if (segmentOpen) out.append("</trkseg>\n")
            segmentOpen = false
            out.append("</trk>\n</gpx>\n")
        }
    }

    /** One track for each trip, named with when it was, how far and how you travelled. */
    fun writeTrips(out: Appendable, trips: List<Pair<String, List<TripPointEntity>>>) {
        out.append(HEADER)
        for ((name, points) in trips) {
            out.append("<trk><name>").append(xml(name)).append("</name>\n<trkseg>\n")
            for (p in points.sortedBy { it.ms }) point(out, p.ms, p.lat, p.lng, p.acc, -1f)
            out.append("</trkseg>\n</trk>\n")
        }
        out.append("</gpx>\n")
    }

    /** What reading a file gave: the readings, and how many points were left out for having no position or time. */
    class Parsed(val fixes: List<RawFixEntity>, val skipped: Int)

    fun parse(text: String): Parsed = parse(text.byteInputStream(Charsets.UTF_8))

    fun parse(input: InputStream): Parsed {
        val factory = SAXParserFactory.newInstance()
        // A track file has no business with outside entities; ask for that where the parser allows it.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        val handler = PointHandler()
        factory.newSAXParser().parse(InputSource(input), handler)
        return Parsed(handler.fixes.sortedBy { it.ms }.distinctBy { it.ms }, handler.skipped)
    }

    private class PointHandler : DefaultHandler() {
        val fixes = ArrayList<RawFixEntity>()
        var skipped = 0

        private var inPoint = false
        private var lat = Double.NaN
        private var lng = Double.NaN
        private var timeMs: Long? = null
        private var acc: Float? = null
        private var hdop: Float? = null
        private var speed: Float? = null
        private var src = ""
        private val text = StringBuilder()

        private fun name(qName: String?, localName: String?): String {
            val n = if (!localName.isNullOrEmpty()) localName else qName.orEmpty()
            return n.substringAfter(':').lowercase(Locale.ENGLISH)
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            text.setLength(0)
            if (name(qName, localName) == "trkpt") {
                inPoint = true
                lat = attributes.getValue("lat")?.trim()?.toDoubleOrNull() ?: Double.NaN
                lng = attributes.getValue("lon")?.trim()?.toDoubleOrNull() ?: Double.NaN
                timeMs = null
                acc = null
                hdop = null
                speed = null
                src = ""
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (!inPoint) return
            val value = text.toString().trim()
            when (name(qName, localName)) {
                "time" -> timeMs = runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
                "acc" -> acc = value.toFloatOrNull()
                "hdop" -> hdop = value.toFloatOrNull()
                "speed" -> speed = value.toFloatOrNull()
                "src" -> src = value.lowercase(Locale.ENGLISH)
                "trkpt" -> {
                    inPoint = false
                    val t = timeMs
                    if (t == null || !lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                        skipped++
                    } else {
                        val accuracy = acc ?: hdop?.let { it * 5f } ?: when (src) {
                            "network" -> 50f
                            "cell" -> 150f
                            else -> 20f
                        }
                        fixes.add(RawFixEntity(t, lat, lng, accuracy.coerceAtLeast(1f), speed?.takeIf { it >= 0f } ?: -1f))
                    }
                }
            }
            text.setLength(0)
        }
    }

    /** One line for each reading: `time_ms,iso_time,lat,lng,accuracy_m,speed_ms` (speed empty when the phone did not say). */
    fun csvHeader(): String = "time_ms,iso_time,lat,lng,accuracy_m,speed_ms\n"

    fun csvRows(out: Appendable, fixes: List<RawFixEntity>) {
        for (f in fixes) {
            out.append(f.ms.toString()).append(',').append(Instant.ofEpochMilli(f.ms).toString()).append(',')
                .append(number(f.lat)).append(',').append(number(f.lng)).append(',')
                .append(Math.round(f.acc).toString()).append(',')
                .append(if (f.speed >= 0f) String.format(Locale.ENGLISH, "%.2f", f.speed) else "").append('\n')
        }
    }
}
