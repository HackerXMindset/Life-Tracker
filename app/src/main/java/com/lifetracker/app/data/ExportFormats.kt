package com.lifetracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** The file formats a day, a range or all of your readings can be saved as. */
enum class ExportFormat(val id: String, val label: String, val extension: String, val mime: String) {
    GPX("gpx", "GPX", "gpx", "application/gpx+xml"),
    KML("kml", "KML", "kml", "application/vnd.google-earth.kml+xml"),
    GEOJSON("geojson", "GeoJSON", "geojson", "application/geo+json"),
    CSV("csv", "CSV", "csv", "text/csv");

    companion object {
        fun of(id: String): ExportFormat = entries.firstOrNull { it.id == id } ?: GPX
    }
}

/** The span of time to export, and a name for the file. Plain code. */
object ExportRange {
    /** Start (included) and end (not included) of a day in [zone]. */
    fun day(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
        date.atStartOfDay(zone).toInstant().toEpochMilli() to date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Start of the first day to the end of the last day, both included; null when the last day is before the first. */
    fun range(first: LocalDate, last: LocalDate, zone: ZoneId): Pair<Long, Long>? =
        if (last.isBefore(first)) null else day(first, zone).first to day(last, zone).second

    /** "life-tracker-readings-2026-10-26.gpx", with the dates of a day or range, or nothing for everything. */
    fun fileName(format: ExportFormat, from: LocalDate?, to: LocalDate?, today: LocalDate): String {
        val part = when {
            from == null -> "all-$today"
            to == null || to == from -> from.toString()
            else -> "$from-to-$to"
        }
        return "life-tracker-readings-$part.${format.extension}"
    }

    /** The first and last day a time span touches (the end is not included), in [zone]. */
    fun days(fromMs: Long, toMs: Long, zone: ZoneId): Pair<LocalDate, LocalDate> {
        val a = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
        val b = Instant.ofEpochMilli((toMs - 1).coerceAtLeast(fromMs)).atZone(zone).toLocalDate()
        return a to b
    }
}

/** What an export asks for: the span of time, the file name, or what is wrong with the request. */
class ExportPlan(val fromMs: Long, val toMs: Long, val fileName: String, val problem: String?)

object ExportPlans {
    const val ALL = 0
    const val DAY = 1
    const val RANGE = 2

    /** Works out the span and file name for a choice on the Export card. Dates are written 2026-10-26. */
    fun plan(scope: Int, fromText: String, toText: String, format: ExportFormat, zone: ZoneId, today: LocalDate, nowMs: Long): ExportPlan {
        fun bad(text: String) = ExportPlan(0L, 0L, "", text)
        fun date(t: String): LocalDate? = runCatching { LocalDate.parse(t.trim()) }.getOrNull()
        return when (scope) {
            DAY -> {
                val d = date(fromText) ?: return bad("Write the day as 2026-10-26.")
                val (a, b) = ExportRange.day(d, zone)
                ExportPlan(a, b, ExportRange.fileName(format, d, null, today), null)
            }
            RANGE -> {
                val a = date(fromText) ?: return bad("Write the first day as 2026-10-01.")
                val b = date(toText) ?: return bad("Write the last day as 2026-10-07.")
                val r = ExportRange.range(a, b, zone) ?: return bad("The last day is before the first day.")
                ExportPlan(r.first, r.second, ExportRange.fileName(format, a, b, today), null)
            }
            else -> ExportPlan(0L, nowMs + 1, ExportRange.fileName(format, null, null, today), null)
        }
    }
}

/**
 * Writes position readings in a chosen format, oldest first, in as many calls to [add] as you like. The same readings can be
 * read back by the importers (C8b). A silence of 30 minutes starts a new line on the map in every format.
 */
class ReadingsWriter(private val out: Appendable, private val format: ExportFormat, private val name: String) {
    private val gpx = if (format == ExportFormat.GPX) Gpx.Writer(out, name) else null
    private val segment = ArrayList<RawFixEntity>()
    private var lastMs = Long.MIN_VALUE
    private var segments = 0
    private var began = false

    fun begin() {
        began = true
        when (format) {
            ExportFormat.GPX -> gpx!!.begin()
            ExportFormat.KML -> out.append(KML_HEAD).append(esc(name)).append("</name>\n")
            ExportFormat.GEOJSON -> out.append("{\"type\":\"FeatureCollection\",\"name\":").append(quote(name)).append(",\"features\":[")
            ExportFormat.CSV -> out.append(Gpx.csvHeader())
        }
    }

    fun add(fixes: List<RawFixEntity>) {
        if (!began) begin()
        when (format) {
            ExportFormat.GPX -> gpx!!.add(fixes)
            ExportFormat.CSV -> Gpx.csvRows(out, fixes)
            else -> for (f in fixes) {
                if (segment.isNotEmpty() && f.ms - lastMs > Gpx.GAP_MS) flush()
                segment += f
                lastMs = f.ms
            }
        }
    }

    fun end() {
        if (!began) begin()
        when (format) {
            ExportFormat.GPX -> gpx!!.end()
            ExportFormat.KML -> {
                flush()
                out.append("</Document>\n</kml>\n")
            }
            ExportFormat.GEOJSON -> {
                flush()
                out.append("]}\n")
            }
            ExportFormat.CSV -> Unit
        }
    }

    private fun flush() {
        if (segment.isEmpty()) return
        segments++
        if (format == ExportFormat.KML) {
            out.append("<Placemark><name>").append(esc(name)).append(' ').append(segments.toString()).append("</name><gx:Track>\n")
            segment.forEach { out.append("<when>").append(Instant.ofEpochMilli(it.ms).toString()).append("</when>\n") }
            segment.forEach { out.append("<gx:coord>").append(num(it.lng)).append(' ').append(num(it.lat)).append("</gx:coord>\n") }
            out.append("</gx:Track></Placemark>\n")
        } else {
            if (segments > 1) out.append(',')
            out.append("{\"type\":\"Feature\",\"properties\":{\"name\":").append(quote("$name $segments"))
            out.append(",\"coordTimes\":[")
            segment.forEachIndexed { i, f -> if (i > 0) out.append(','); out.append('"').append(Instant.ofEpochMilli(f.ms).toString()).append('"') }
            out.append("],\"accuracy\":[")
            segment.forEachIndexed { i, f -> if (i > 0) out.append(','); out.append(Math.round(f.acc).toString()) }
            out.append("]},\"geometry\":{\"type\":")
            if (segment.size == 1) {
                out.append("\"Point\",\"coordinates\":[").append(num(segment[0].lng)).append(',').append(num(segment[0].lat)).append("]}}")
            } else {
                out.append("\"LineString\",\"coordinates\":[")
                segment.forEachIndexed { i, f ->
                    if (i > 0) out.append(',')
                    out.append('[').append(num(f.lng)).append(',').append(num(f.lat)).append(']')
                }
                out.append("]}}")
            }
        }
        segment.clear()
    }

    private fun num(v: Double): String = String.format(Locale.ENGLISH, "%.7f", v)

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private companion object {
        const val KML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\">\n<Document>\n<name>"
    }
}

/** When the automatic export runs and what each run covers. */
object ExportSchedule {
    const val OFF = "off"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"

    fun label(mode: String): String = when (mode) {
        DAILY -> "Every day"
        WEEKLY -> "Every week"
        else -> "Off"
    }

    /** How often the background job runs, in hours; 0 for off. */
    fun hours(mode: String): Long = when (mode) {
        DAILY -> 24L
        WEEKLY -> 24L * 7
        else -> 0L
    }

    /** The span a run covers: from where the last run ended (or the first reading) to [nowMs]; null when there is nothing to cover. */
    fun span(lastEndMs: Long, firstReadingMs: Long?, nowMs: Long): Pair<Long, Long>? {
        val from = if (lastEndMs > 0L) lastEndMs else firstReadingMs ?: return null
        return if (from < nowMs) from to nowMs else null
    }
}
