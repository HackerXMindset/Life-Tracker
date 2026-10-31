package com.lifetracker.app.data

import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GifEncoderTest {
    /** A plain GIF decoder for the tests: unpacks the codes the encoder wrote. */
    private fun unlzw(packed: ByteArray, minCode: Int): ByteArray {
        val clear = 1 shl minCode
        val eoi = clear + 1
        val dict = ArrayList<ByteArray>()
        fun reset() {
            dict.clear()
            for (i in 0 until clear) dict += byteArrayOf(i.toByte())
            dict += ByteArray(0)
            dict += ByteArray(0)
        }
        reset()
        var size = minCode + 1
        var prev: ByteArray? = null
        val out = ByteArrayOutputStream()
        var bit = 0
        while (true) {
            var code = 0
            for (k in 0 until size) {
                val byte = packed[(bit + k) / 8].toInt() and 0xFF
                code = code or (((byte shr ((bit + k) % 8)) and 1) shl k)
            }
            bit += size
            if (code == clear) {
                reset(); size = minCode + 1; prev = null
                continue
            }
            if (code == eoi) break
            val p = prev
            val entry = if (code < dict.size) dict[code] else p!! + p[0]
            out.write(entry)
            if (p != null) {
                dict += p + entry[0]
                if (dict.size == (1 shl size) && size < 12) size++
            }
            prev = entry
        }
        return out.toByteArray()
    }

    @Test
    fun packedPicturesComeBackExactly() {
        val rnd = Random(7)
        // Flat colour with a few noisy bits, long enough for the code table to fill and restart several times.
        val flat = ByteArray(200_000) { if (rnd.nextInt(10) == 0) rnd.nextInt(216).toByte() else 5 }
        assertArrayEquals(flat, unlzw(GifEncoder.lzw(flat, 8), 8))
        // Pure noise is the worst case for the table.
        val noise = ByteArray(60_000) { rnd.nextInt(256).toByte() }
        assertArrayEquals(noise, unlzw(GifEncoder.lzw(noise, 8), 8))
        // Few colours, so the codes start narrow.
        val few = ByteArray(30_000) { rnd.nextInt(4).toByte() }
        assertArrayEquals(few, unlzw(GifEncoder.lzw(few, 2), 2))
        assertArrayEquals(ByteArray(0), unlzw(GifEncoder.lzw(ByteArray(0), 8), 8))
        assertArrayEquals(byteArrayOf(3), unlzw(GifEncoder.lzw(byteArrayOf(3), 8), 8))
    }

    @Test
    fun theFileHasTheRightPartsInTheRightOrder() {
        val out = ByteArrayOutputStream()
        val gif = GifEncoder(out, 4, 3, ReplayGifRules.PALETTE)
        gif.addFrame(ByteArray(12) { 1 }, 10)
        gif.addFrame(ByteArray(12) { 2 }, 10)
        gif.finish()
        val b = out.toByteArray()
        assertEquals("GIF89a", String(b, 0, 6, Charsets.US_ASCII))
        assertEquals(4, b[6].toInt()); assertEquals(0, b[7].toInt())
        assertEquals(3, b[8].toInt()); assertEquals(0, b[9].toInt())
        assertEquals(0xF7, b[10].toInt() and 0xFF)
        assertEquals(0x3B, b.last().toInt())
        assertEquals(2, countOf(b, byteArrayOf(0x21, 0xF9.toByte(), 4)))
        assertEquals(1, countOf(b, "NETSCAPE2.0".toByteArray()))
        assertTrue(b.size > 6 + 7 + 768)
    }

    private fun countOf(b: ByteArray, pattern: ByteArray): Int {
        var n = 0
        for (i in 0..b.size - pattern.size) if ((pattern.indices).all { b[i + it] == pattern[it] }) n++
        return n
    }

    @Test
    fun aFrameOfTheWrongSizeIsRefused() {
        val gif = GifEncoder(ByteArrayOutputStream(), 4, 3, ReplayGifRules.PALETTE)
        val failed = runCatching { gif.addFrame(ByteArray(5), 10) }.isFailure
        assertTrue(failed)
    }
}
