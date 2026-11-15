package com.lifetracker.app.data

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBackupsTest {
    private val min = 60_000L
    private val t0 = 1_800_000_000_000L

    // A small writer for the protocol buffers format, enough to build a Mihon backup.
    private fun varint(v: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var x = v
        while (x and 0x7f.inv().toLong() != 0L) { out.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }
        out.write(x.toInt())
        return out.toByteArray()
    }
    private fun key(n: Int, wire: Int) = varint(((n shl 3) or wire).toLong())
    private fun vf(n: Int, v: Long) = key(n, 0) + varint(v)
    private fun lf(n: Int, b: ByteArray) = key(n, 2) + varint(b.size.toLong()) + b
    private fun sf(n: Int, s: String) = lf(n, s.toByteArray())
    private fun ff(n: Int, f: Float): ByteArray {
        val bits = f.toRawBits()
        return key(n, 5) + byteArrayOf(bits.toByte(), (bits shr 8).toByte(), (bits shr 16).toByte(), (bits shr 24).toByte())
    }

    private fun backup(duration46: Long, duration47: Long): ByteArray {
        val manga = sf(2, "/series/1") + sf(3, "Solo Leveling") +
            lf(16, sf(1, "/c/46") + ff(9, 46f)) + lf(16, sf(1, "/c/47") + ff(9, 47f)) +
            lf(18, vf(1, 2) + vf(100, 777)) + lf(18, vf(1, 1) + vf(100, 555)) +
            lf(104, sf(1, "/c/46") + vf(2, t0) + vf(3, duration46)) +
            lf(104, sf(1, "/c/47") + vf(2, t0 + 8 * min) + vf(3, duration47))
        val raw = lf(1, manga)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(raw) }
        return out.toByteArray()
    }

    @Test
    fun aMihonBackupGivesEachChapterWithItsTrackerIdsAndTime() {
        val rows = MediaBackups.mihonRows(backup(300_000, 420_000))
        assertEquals(2, rows.size)
        val r = rows.first { it.number == 46.0 }
        assertEquals("Solo Leveling", r.title)
        assertEquals(t0, r.lastReadMs)
        assertEquals(300_000L, r.durationMs)
        assertEquals(setOf("anilist:manga:777", "mal:manga:555"), r.ids)
    }

    @Test
    fun theFirstReadTakesAllTheTimeAndLaterOnesOnlyWhatGrew() {
        val first = MediaBackups.mihonEvents(MediaBackups.mihonRows(backup(300_000, 420_000)), emptyMap(), t0 - 60 * min)
        assertEquals(2, first.events.size)
        val e46 = first.events.first { it.numFrom == 46.0 }
        assertEquals(5 * min, e46.exactMs)
        assertEquals("ch 46", e46.label)
        assertEquals(MediaService.MIHON, e46.service)

        // Nothing changed: nothing new.
        val same = MediaBackups.mihonEvents(MediaBackups.mihonRows(backup(300_000, 420_000)), first.snapshot, t0 - 60 * min)
        assertTrue(same.events.isEmpty())

        // Chapter 46 was read again for 3 more minutes.
        val again = MediaBackups.mihonEvents(MediaBackups.mihonRows(backup(480_000, 420_000)), first.snapshot, t0 - 60 * min)
        assertEquals(1, again.events.size)
        assertEquals(3 * min, again.events.single().exactMs)
    }

    @Test
    fun aChapterWithNoRecordedTimeIsStillReportedOnceButWithoutAMeasuredTime() {
        val first = MediaBackups.mihonEvents(MediaBackups.mihonRows(backup(0, 0)), emptyMap(), t0 - 60 * min)
        assertEquals(2, first.events.size)
        assertNull(first.events.first().exactMs)
        assertTrue(MediaBackups.mihonEvents(MediaBackups.mihonRows(backup(0, 0)), first.snapshot, t0 - 60 * min).events.isEmpty())
    }

    @Test
    fun chaptersOlderThanTheLookbackAreIgnored() {
        val r = MediaBackups.mihonEvents(MediaBackups.mihonRows(backup(300_000, 420_000)), emptyMap(), t0 + 4 * min)
        assertEquals(1, r.events.size)
        assertEquals(47.0, r.events.single().numFrom)
    }

    @Test(expected = MediaException::class)
    fun aFileThatIsNotABackupIsRefused() {
        MediaBackups.mihonRows(byteArrayOf(0x0a, 0x7f, 1, 2, 3))
    }

    private fun kotatsuZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            add("history", """[{"manga_id":9,"created_at":1,"updated_at":2,"chapter_id":3,"page":1,"scroll":0.0,"manga":{"id":9,"title":"Berserk","url":"u","public_url":"p","cover_url":"c","source":"s"}}]""")
            add("scrobbling", """[{"scrobbler":3,"id":1,"manga_id":9,"target_id":2,"status":null,"chapter":1,"comment":null,"rating":0.0},{"scrobbler":2,"id":2,"manga_id":9,"target_id":30002,"status":null,"chapter":1,"comment":null,"rating":0.0}]""")
            add(
                "statistics",
                """[{"manga_id":9,"started_at":$t0,"duration":${6 * min},"pages":20},{"manga_id":9,"started_at":${t0 + 9 * min},"duration":${4 * min},"pages":12},{"manga_id":9,"started_at":${t0 + 120 * min},"duration":${5 * min},"pages":15}]""",
            )
        }
        return out.toByteArray()
    }

    @Test
    fun kotatsuSessionsCloseTogetherAreOneStretchWithTheirRealTime() {
        val events = MediaBackups.kotatsuEvents(MediaBackups.kotatsuData(kotatsuZip()), t0 - min).sortedBy { it.atMs }
        assertEquals(2, events.size)
        val first = events[0]
        assertEquals("Berserk", first.title)
        assertEquals(10 * min, first.exactMs)
        assertEquals(t0 + 13 * min, first.atMs)
        assertEquals(setOf("mal:manga:2", "anilist:manga:30002"), first.ids)
        assertEquals(5 * min, events[1].exactMs)
        assertNotNull(events[1].id)
    }

    @Test(expected = MediaException::class)
    fun aFileThatIsNotAKotatsuBackupIsRefused() {
        MediaBackups.kotatsuData(byteArrayOf(1, 2, 3, 4))
    }
}
