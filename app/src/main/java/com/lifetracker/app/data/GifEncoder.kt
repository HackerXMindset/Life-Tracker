package com.lifetracker.app.data

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Writes an animated GIF (version 89a) one frame at a time, so a long replay never has to be held in memory. The colours are a
 * fixed [palette] of up to 256 entries (0xRRGGBB), every frame is a [ByteArray] of palette positions, row after row. The picture
 * data is packed with the standard variable-width LZW code. No Android in here, so it can be tested.
 */
class GifEncoder(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    private val palette: IntArray,
    private val loop: Boolean = true,
) {
    private var started = false
    private var done = false
    private val bits: Int = run {
        var b = 1
        while ((1 shl b) < palette.size && b < 8) b++
        b
    }

    /** The smallest LZW code size GIF allows for this many colours (at least 2). */
    private val minCode: Int get() = maxOf(2, bits)

    private fun short(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun start() {
        if (started) return
        started = true
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        short(width)
        short(height)
        out.write(0x80 or ((bits - 1) shl 4) or (bits - 1))
        out.write(0)
        out.write(0)
        val size = 1 shl bits
        for (i in 0 until size) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF)
            out.write((c shr 8) and 0xFF)
            out.write(c and 0xFF)
        }
        if (loop) {
            out.write(0x21); out.write(0xFF); out.write(11)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(3); out.write(1); short(0); out.write(0)
        }
    }

    /** Adds one frame shown for [delayCs] hundredths of a second. [indices] holds width x height palette positions. */
    fun addFrame(indices: ByteArray, delayCs: Int) {
        require(indices.size == width * height) { "A frame must be width x height long" }
        check(!done) { "The GIF is already finished" }
        start()
        // Graphic control extension: leave the frame in place, then show the next one on top.
        out.write(0x21); out.write(0xF9); out.write(4)
        out.write(0x04)
        short(delayCs)
        out.write(0); out.write(0)
        out.write(0x2C)
        short(0); short(0); short(width); short(height)
        out.write(0)
        out.write(minCode)
        val packed = lzw(indices, minCode)
        var at = 0
        while (at < packed.size) {
            val n = minOf(255, packed.size - at)
            out.write(n)
            out.write(packed, at, n)
            at += n
        }
        out.write(0)
    }

    /** Writes the end of the file. The stream is flushed but not closed. */
    fun finish() {
        if (done) return
        start()
        out.write(0x3B)
        out.flush()
        done = true
    }

    companion object {
        /** The LZW codes for [data], packed least significant bit first. */
        internal fun lzw(data: ByteArray, minCode: Int): ByteArray {
            val clear = 1 shl minCode
            val eoi = clear + 1
            val out = ByteArrayOutputStream(data.size / 2 + 16)
            var cur = 0
            var curBits = 0
            var codeSize = minCode + 1
            var next = eoi + 1
            val dict = HashMap<Int, Int>()

            fun emit(code: Int) {
                cur = cur or (code shl curBits)
                curBits += codeSize
                while (curBits >= 8) {
                    out.write(cur and 0xFF)
                    cur = cur ushr 8
                    curBits -= 8
                }
            }

            emit(clear)
            if (data.isNotEmpty()) {
                var prefix = data[0].toInt() and 0xFF
                for (i in 1 until data.size) {
                    val b = data[i].toInt() and 0xFF
                    val key = (prefix shl 8) or b
                    val known = dict[key]
                    if (known != null) {
                        prefix = known
                    } else {
                        emit(prefix)
                        if (next < 4096) {
                            if (next >= (1 shl codeSize) && codeSize < 12) codeSize++
                            dict[key] = next
                            next++
                        } else {
                            emit(clear)
                            dict.clear()
                            next = eoi + 1
                            codeSize = minCode + 1
                        }
                        prefix = b
                    }
                }
                emit(prefix)
                if (next < 4096 && next >= (1 shl codeSize) && codeSize < 12) codeSize++
            }
            emit(eoi)
            if (curBits > 0) out.write(cur and 0xFF)
            return out.toByteArray()
        }
    }
}
