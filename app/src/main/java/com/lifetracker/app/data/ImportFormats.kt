package com.lifetracker.app.data

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

/** What a file turned out to be. */
enum class ImportKind(val label: String) {
    GPX("GPX"),
    KML("KML"),
    GEOJSON("GeoJSON"),
    CSV("CSV"),
    TIMELINE_RECORDS("Google Timeline (Records.json)"),
    TIMELINE_NEW("Google Timeline (on-phone export)"),
    TIMELINE_OLD("Google Timeline (monthly Semantic Location History)"),
    UNKNOWN("an unknown file type"),
}

/** Position readings kept in plain arrays, so a million of them take tens of megabytes, not hundreds. */
class FixBuffer {
    var size = 0
        private set
    private var ms = LongArray(1024)
    private var lat = DoubleArray(1024)
    private var lng = DoubleArray(1024)
    private var acc = FloatArray(1024)
    private var speed = FloatArray(1024)

    fun add(timeMs: Long, la: Double, ln: Double, accuracy: Float, speedMs: Float) {
        if (size == ms.size) {
            val n = size * 2
            ms = ms.copyOf(n)
            lat = lat.copyOf(n)
            lng = lng.copyOf(n)
            acc = acc.copyOf(n)
            speed = speed.copyOf(n)
        }
        ms[size] = timeMs
        lat[size] = la
        lng[size] = ln
        acc[size] = accuracy
        speed[size] = speedMs
        size++
    }

    fun ms(i: Int) = ms[i]
    fun lat(i: Int) = lat[i]
    fun lng(i: Int) = lng[i]
    fun acc(i: Int) = acc[i]

    fun fix(i: Int) = RawFixEntity(ms[i], lat[i], lng[i], acc[i], speed[i], source = FixSource.IMPORTED)
}

/** What reading a file gave: the readings, and how many points were left out for having no usable position or time. */
class ImportParse(val kind: ImportKind, val fixes: FixBuffer, var skipped: Int, val note: String = "")

/** Finds out what a file is and reads its position readings. Plain code (no Android), so it can be tested. */
object ImportFormats {
    /** The accuracy given to a reading from a file that does not say, in metres. */
    const val DEFAULT_ACC = 20f

    /** What a file is, from the start of its text (the first few thousand characters are enough). */
    fun detect(head: String): ImportKind {
        val t = head.trimStart('﻿', ' ', '\n', '\r', '\t')
        if (t.startsWith("<")) {
            val low = t.lowercase(Locale.ENGLISH)
            return when {
                low.contains("<gpx") -> ImportKind.GPX
                low.contains("<kml") -> ImportKind.KML
                else -> ImportKind.UNKNOWN
            }
        }
        if (t.startsWith("{") || t.startsWith("[")) {
            return when {
                t.contains("\"semanticSegments\"") || t.contains("\"rawSignals\"") -> ImportKind.TIMELINE_NEW
                t.contains("\"locations\"") && (t.contains("latitudeE7") || t.contains("\"timestamp")) -> ImportKind.TIMELINE_RECORDS
                t.contains("\"timelineObjects\"") -> ImportKind.TIMELINE_OLD
                t.startsWith("[") && t.contains("\"timelinePath\"") -> ImportKind.TIMELINE_NEW
                t.startsWith("[") && t.contains("\"startTime\"") && t.contains("\"endTime\"") -> ImportKind.TIMELINE_NEW
                t.contains("\"FeatureCollection\"") || t.contains("\"Feature\"") || t.contains("\"LineString\"") ||
                    t.contains("\"Point\"") || t.contains("\"MultiLineString\"") || t.contains("\"coordinates\"") -> ImportKind.GEOJSON
                else -> ImportKind.UNKNOWN
            }
        }
        val first = t.lineSequence().firstOrNull().orEmpty().lowercase(Locale.ENGLISH)
        val columns = csvColumns(first)
        if (columns != null) return ImportKind.CSV
        return ImportKind.UNKNOWN
    }

    /** Reads [input] as [kind]. [zone] is used for times in a CSV that have no time zone. */
    fun parse(kind: ImportKind, input: InputStream, zone: ZoneId = ZoneId.systemDefault()): ImportParse = when (kind) {
        ImportKind.GPX -> parseGpx(input)
        ImportKind.KML -> parseKml(input)
        ImportKind.GEOJSON -> parseGeoJson(utf8(input))
        ImportKind.CSV -> parseCsv(utf8(input), zone)
        ImportKind.TIMELINE_RECORDS -> parseRecords(utf8(input))
        ImportKind.TIMELINE_NEW -> parseTimelineNew(utf8(input))
        ImportKind.TIMELINE_OLD -> ImportParse(
            kind, FixBuffer(), 0,
            "These are the old monthly Semantic Location History files. They hold visits and trips, not readings, and are not read yet.",
        )
        ImportKind.UNKNOWN -> ImportParse(kind, FixBuffer(), 0, "The app could not tell what this file is.")
    }

    private fun utf8(input: InputStream): Reader = InputStreamReader(input, Charsets.UTF_8).buffered(1 shl 16)

    private fun valid(lat: Double, lng: Double): Boolean =
        lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0 && !(lat == 0.0 && lng == 0.0)

    private fun add(out: ImportParse, ms: Long?, lat: Double, lng: Double, acc: Float?, speed: Float?) {
        if (ms == null || ms <= 0L || !valid(lat, lng)) {
            out.skipped++
            return
        }
        out.fixes.add(ms, lat, lng, (acc ?: DEFAULT_ACC).coerceAtLeast(1f), speed?.takeIf { it >= 0f } ?: -1f)
    }

    /** A time as text or number: ISO with or without a zone, epoch milliseconds or epoch seconds. */
    fun parseTime(raw: String?, zone: ZoneId = ZoneId.of("UTC")): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        s.toDoubleOrNull()?.let { n ->
            if (!n.isFinite() || n <= 0.0) return null
            return if (n >= 1e11) n.toLong() else (n * 1000.0).toLong()
        }
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(s.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli() }
        return null
    }

    /** "12.34°, 56.78°" or "geo:12.34,56.78" or "12.34, 56.78" as (latitude, longitude). */
    fun parseLatLng(text: String?): Pair<Double, Double>? {
        if (text == null) return null
        val numbers = Regex("-?\\d+(?:\\.\\d+)?").findAll(text).map { it.value.toDouble() }.toList()
        return if (numbers.size >= 2) numbers[0] to numbers[1] else null
    }

    // --- GPX ---------------------------------------------------------------------------------------------------------

    private fun parseGpx(input: InputStream): ImportParse {
        val g = Gpx.parse(input)
        val out = ImportParse(ImportKind.GPX, FixBuffer(), g.skipped)
        g.fixes.forEach { add(out, it.ms, it.lat, it.lng, it.acc, it.speed) }
        return out
    }

    // --- KML ---------------------------------------------------------------------------------------------------------

    private fun parseKml(input: InputStream): ImportParse {
        val factory = SAXParserFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        val out = ImportParse(ImportKind.KML, FixBuffer(), 0)
        factory.newSAXParser().parse(InputSource(input), KmlHandler(out))
        return out
    }

    private class KmlHandler(val out: ImportParse) : DefaultHandler() {
        private val text = StringBuilder()
        private var inTrack = false
        private val whens = ArrayList<String>()
        private val coords = ArrayList<String>()
        private var inPlacemark = false
        private var placeWhen: String? = null
        private var placeCoord: String? = null
        private var geometry = ""

        private fun name(qName: String?, localName: String?): String {
            val n = if (!localName.isNullOrEmpty()) localName else qName.orEmpty()
            return n.substringAfter(':').lowercase(Locale.ENGLISH)
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            text.setLength(0)
            when (name(qName, localName)) {
                "track" -> {
                    inTrack = true
                    whens.clear()
                    coords.clear()
                }
                "placemark" -> {
                    inPlacemark = true
                    placeWhen = null
                    placeCoord = null
                    geometry = ""
                }
                "point", "linestring", "linearring", "polygon" -> geometry = name(qName, localName)
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val value = text.toString().trim()
            when (name(qName, localName)) {
                "when" -> if (inTrack) whens += value else if (inPlacemark) placeWhen = value
                "begin" -> if (inPlacemark && !inTrack && placeWhen == null) placeWhen = value
                "coord" -> if (inTrack) coords += value
                "coordinates" -> if (inPlacemark && !inTrack) {
                    val tokens = value.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (geometry == "point" && tokens.size == 1) placeCoord = tokens[0] else out.skipped += tokens.size
                }
                "track" -> {
                    inTrack = false
                    val n = minOf(whens.size, coords.size)
                    out.skipped += kotlin.math.abs(whens.size - coords.size)
                    for (i in 0 until n) {
                        val p = coords[i].split(Regex("\\s+"))
                        add(out, parseTime(whens[i]), p.getOrNull(1)?.toDoubleOrNull() ?: Double.NaN, p.getOrNull(0)?.toDoubleOrNull() ?: Double.NaN, null, null)
                    }
                }
                "placemark" -> {
                    inPlacemark = false
                    val c = placeCoord
                    if (c != null) {
                        val p = c.split(',')
                        add(out, parseTime(placeWhen), p.getOrNull(1)?.toDoubleOrNull() ?: Double.NaN, p.getOrNull(0)?.toDoubleOrNull() ?: Double.NaN, null, null)
                    }
                }
            }
            text.setLength(0)
        }
    }

    // --- GeoJSON -----------------------------------------------------------------------------------------------------

    private class Geo {
        val positions = ArrayList<DoubleArray>()
        val times = ArrayList<String?>()
        val accuracy = ArrayList<Double>()
        var scalarTime: String? = null
        var scalarAcc: Double? = null
    }

    private fun parseGeoJson(reader: Reader): ImportParse {
        val out = ImportParse(ImportKind.GEOJSON, FixBuffer(), 0)
        val j = JsonPull(reader)
        geoObject(j, out)
        return out
    }

    /** One GeoJSON object: a FeatureCollection, a Feature, or a geometry on its own. */
    private fun geoObject(j: JsonPull, out: ImportParse) {
        val geo = Geo()
        j.obj { key ->
            when (key) {
                "features" -> j.arr { geoObject(j, out) }
                "geometry" -> geometry(j, geo)
                "properties" -> properties(j, geo)
                "coordinates" -> coordinates(j, geo)
                "geometries" -> j.arr { geoObject(j, out) }
                else -> j.skip()
            }
        }
        emit(geo, out)
    }

    private fun geometry(j: JsonPull, geo: Geo) {
        j.obj { key -> if (key == "coordinates") coordinates(j, geo) else j.skip() }
    }

    /** Any nesting of arrays of positions: a position is an array whose first element is a number. */
    private fun coordinates(j: JsonPull, geo: Geo) {
        if (j.peek() != JsonPull.Token.BEGIN_ARRAY) {
            j.skip()
            return
        }
        j.next()
        if (j.peek() == JsonPull.Token.NUMBER) {
            val p = ArrayList<Double>()
            while (j.peek() != JsonPull.Token.END_ARRAY && j.peek() != JsonPull.Token.END) {
                j.dbl()?.let { p += it }
            }
            j.next()
            if (p.size >= 2) geo.positions += p.toDoubleArray()
        } else {
            while (j.peek() != JsonPull.Token.END_ARRAY && j.peek() != JsonPull.Token.END) coordinates(j, geo)
            j.next()
        }
    }

    private fun properties(j: JsonPull, geo: Geo) {
        j.obj { key ->
            when (key.lowercase(Locale.ENGLISH)) {
                "coordtimes", "times", "timestamps" -> flatten(j) { geo.times += it }
                "time", "timestamp", "datetime", "when" -> geo.scalarTime = j.str()
                "accuracy", "acc" -> {
                    if (j.peek() == JsonPull.Token.BEGIN_ARRAY) flatten(j) { s -> s?.toDoubleOrNull()?.let { geo.accuracy += it } }
                    else geo.scalarAcc = j.dbl()
                }
                else -> j.skip()
            }
        }
    }

    private fun flatten(j: JsonPull, sink: (String?) -> Unit) {
        when (j.peek()) {
            JsonPull.Token.BEGIN_ARRAY -> j.arr { flatten(j, sink) }
            else -> sink(j.str())
        }
    }

    private fun emit(geo: Geo, out: ImportParse) {
        if (geo.positions.isEmpty()) return
        geo.positions.forEachIndexed { i, p ->
            val time = geo.times.getOrNull(i)
                ?: p.getOrNull(3)?.let { it.toLong().toString() }
                ?: if (geo.positions.size == 1) geo.scalarTime else null
            val acc = geo.accuracy.getOrNull(i) ?: geo.scalarAcc
            add(out, parseTime(time), p[1], p[0], acc?.toFloat(), null)
        }
    }

    // --- Google Timeline ---------------------------------------------------------------------------------------------

    /** Records.json: {"locations":[{"latitudeE7":..,"longitudeE7":..,"accuracy":..,"timestamp":".." or "timestampMs":".."}]} */
    private fun parseRecords(reader: Reader): ImportParse {
        val out = ImportParse(ImportKind.TIMELINE_RECORDS, FixBuffer(), 0)
        val j = JsonPull(reader)
        j.obj { key ->
            if (key == "locations") {
                j.arr {
                    var lat: Double? = null
                    var lng: Double? = null
                    var acc: Float? = null
                    var time: String? = null
                    j.obj { k ->
                        when (k) {
                            "latitudeE7" -> lat = j.dbl()?.let { it / 1e7 }
                            "longitudeE7" -> lng = j.dbl()?.let { it / 1e7 }
                            "accuracy" -> acc = j.dbl()?.toFloat()
                            "timestamp", "timestampMs" -> time = j.str()
                            else -> j.skip()
                        }
                    }
                    add(out, parseTime(time), lat ?: Double.NaN, lng ?: Double.NaN, acc, null)
                }
            } else {
                j.skip()
            }
        }
        return out
    }

    /**
     * The export Google Timeline makes on the phone: `semanticSegments` (each with a `timelinePath` of points and times) and
     * `rawSignals` (positions with a time and accuracy). Visits and activity segments in it are not read.
     */
    private fun parseTimelineNew(reader: Reader): ImportParse {
        val out = ImportParse(ImportKind.TIMELINE_NEW, FixBuffer(), 0)
        val j = JsonPull(reader)
        if (j.peek() == JsonPull.Token.BEGIN_ARRAY) {
            j.arr { segment(j, out) }
        } else {
            j.obj { key ->
                when (key) {
                    "semanticSegments" -> j.arr { segment(j, out) }
                    "rawSignals" -> j.arr { signal(j, out) }
                    else -> j.skip()
                }
            }
        }
        return out
    }

    private fun segment(j: JsonPull, out: ImportParse) {
        j.obj { key ->
            if (key == "timelinePath") {
                j.arr {
                    var point: String? = null
                    var time: String? = null
                    j.obj { k ->
                        when (k) {
                            "point" -> point = j.str()
                            "time" -> time = j.str()
                            else -> j.skip()
                        }
                    }
                    val ll = parseLatLng(point)
                    add(out, parseTime(time), ll?.first ?: Double.NaN, ll?.second ?: Double.NaN, null, null)
                }
            } else {
                j.skip()
            }
        }
    }

    private fun signal(j: JsonPull, out: ImportParse) {
        j.obj { key ->
            if (key == "position") {
                var latLng: String? = null
                var acc: Float? = null
                var time: String? = null
                var speed: Float? = null
                j.obj { k ->
                    when (k) {
                        "LatLng", "latLng" -> latLng = j.str()
                        "accuracyMeters" -> acc = j.dbl()?.toFloat()
                        "timestamp" -> time = j.str()
                        "speedMetersPerSecond" -> speed = j.dbl()?.toFloat()
                        else -> j.skip()
                    }
                }
                val ll = parseLatLng(latLng)
                add(out, parseTime(time), ll?.first ?: Double.NaN, ll?.second ?: Double.NaN, acc, speed)
            } else {
                j.skip()
            }
        }
    }

    // --- CSV ---------------------------------------------------------------------------------------------------------

    private val TIME_NAMES = listOf("time_ms", "timestamp_ms", "timestampms", "iso_time", "time", "timestamp", "datetime", "date_time", "utc_time", "date")
    private val LAT_NAMES = listOf("lat", "latitude")
    private val LNG_NAMES = listOf("lng", "lon", "long", "longitude")
    private val ACC_NAMES = listOf("accuracy_m", "accuracy", "acc", "hacc", "horizontal_accuracy")
    private val SPEED_NAMES = listOf("speed_ms", "speed")

    class CsvColumns(val delimiter: Char, val time: Int, val lat: Int, val lng: Int, val acc: Int, val speed: Int)

    /** Finds the delimiter and the columns from a header line, or null when it has no latitude, longitude and time columns. */
    fun csvColumns(header: String): CsvColumns? {
        val delimiter = listOf(',', ';', '\t').maxByOrNull { d -> header.count { it == d } } ?: ','
        val names = splitCsv(header, delimiter).map { it.trim().trim('"').lowercase(Locale.ENGLISH) }
        fun find(options: List<String>): Int = options.map { names.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
        val lat = find(LAT_NAMES)
        val lng = find(LNG_NAMES)
        val time = find(TIME_NAMES)
        if (lat < 0 || lng < 0 || time < 0) return null
        return CsvColumns(delimiter, time, lat, lng, find(ACC_NAMES), find(SPEED_NAMES))
    }

    /** One CSV line split at the delimiter, with "quoted, values" kept whole. */
    fun splitCsv(line: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == delimiter && !quoted -> {
                    out += sb.toString()
                    sb.setLength(0)
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private fun parseCsv(reader: Reader, zone: ZoneId): ImportParse {
        val out = ImportParse(ImportKind.CSV, FixBuffer(), 0)
        val lines = reader.buffered().lineSequence().iterator()
        if (!lines.hasNext()) return out
        val header = lines.next().trimStart('﻿')
        val cols = csvColumns(header)
            ?: return ImportParse(ImportKind.CSV, FixBuffer(), 0, "The first line of a CSV needs columns for latitude, longitude and time.")
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val p = splitCsv(line, cols.delimiter)
            add(
                out,
                parseTime(p.getOrNull(cols.time), zone),
                p.getOrNull(cols.lat)?.trim()?.toDoubleOrNull() ?: Double.NaN,
                p.getOrNull(cols.lng)?.trim()?.toDoubleOrNull() ?: Double.NaN,
                if (cols.acc >= 0) p.getOrNull(cols.acc)?.trim()?.toFloatOrNull() else null,
                if (cols.speed >= 0) p.getOrNull(cols.speed)?.trim()?.toFloatOrNull() else null,
            )
        }
        return out
    }
}

/** What an import would do, worked out before anything is added. */
class ImportPreview(
    val kind: ImportKind,
    val found: Int,
    val added: Int,
    val alreadyHere: Int,
    val tooInaccurate: Int,
    val skipped: Int,
    val firstMs: Long,
    val lastMs: Long,
    val days: Int,
    val note: String,
) {
    /** The preview in plain words. */
    fun text(zone: ZoneId): String {
        if (found == 0 && note.isNotEmpty()) return note
        val sb = StringBuilder()
        sb.append("This is ").append(kind.label).append(". It has ").append(found).append(" reading").append(if (found == 1) "" else "s")
        if (found > 0) {
            val a = Instant.ofEpochMilli(firstMs).atZone(zone).toLocalDate()
            val b = Instant.ofEpochMilli(lastMs).atZone(zone).toLocalDate()
            sb.append(" on ").append(days).append(" day").append(if (days == 1) "" else "s").append(", from ").append(a).append(" to ").append(b)
        }
        sb.append(".\n")
        sb.append(added).append(" are new, ").append(alreadyHere).append(" are already here (same second), ")
            .append(tooInaccurate).append(" are too inaccurate and ").append(skipped).append(" have no usable position or time.")
        if (note.isNotEmpty()) sb.append("\n").append(note)
        return sb.toString()
    }
}

/** Compares what a file holds with what is already stored. */
object ImportPlanner {
    /** The readings of [parse] that would be added (by index), and the preview. [existingSeconds] are the seconds that have a reading. */
    class Plan(val preview: ImportPreview, val indexes: IntArray)

    fun plan(parse: ImportParse, existingSeconds: Set<Long>, maxAccuracyM: Float, zone: ZoneId): Plan {
        val buf = parse.fixes
        val seen = HashSet<Long>()
        val take = ArrayList<Int>()
        var already = 0
        var inaccurate = 0
        var first = Long.MAX_VALUE
        var last = Long.MIN_VALUE
        val days = HashSet<Long>()
        for (i in 0 until buf.size) {
            val ms = buf.ms(i)
            first = minOf(first, ms)
            last = maxOf(last, ms)
            days += Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()
            val sec = ms / 1000L
            when {
                buf.acc(i) > maxAccuracyM -> inaccurate++
                sec in existingSeconds || !seen.add(sec) -> already++
                else -> take += i
            }
        }
        val preview = ImportPreview(
            parse.kind, buf.size, take.size, already, inaccurate, parse.skipped,
            if (buf.size == 0) 0L else first, if (buf.size == 0) 0L else last, days.size, parse.note,
        )
        return Plan(preview, take.toIntArray())
    }
}
