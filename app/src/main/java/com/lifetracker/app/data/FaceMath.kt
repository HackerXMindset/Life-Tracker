package com.lifetracker.app.data

import java.nio.ByteBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** The numbers behind face matching, kept free of the camera and the models so they can be tested. */
object FaceMath {
    /** The vector scaled to length 1 (a vector of zeros is returned as it is). */
    fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x
        val len = sqrt(sum)
        return if (len < 1e-12) v.copyOf() else FloatArray(v.size) { (v[it] / len).toFloat() }
    }

    /** How alike two faces are, from -1 to 1 (1 is the same direction). 0 when the sizes differ or one is empty. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i]
            na += a[i].toDouble() * a[i]
            nb += b[i].toDouble() * b[i]
        }
        if (na < 1e-12 || nb < 1e-12) return 0f
        return (dot / (sqrt(na) * sqrt(nb))).toFloat()
    }

    /** The best match of [probe] against the saved faces, or -1 when none are saved. */
    fun bestSimilarity(saved: List<FloatArray>, probe: FloatArray): Float = saved.maxOfOrNull { cosine(it, probe) } ?: -1f

    /** The average direction of several faces, scaled to length 1. */
    fun mean(list: List<FloatArray>): FloatArray {
        if (list.isEmpty()) return FloatArray(0)
        val out = FloatArray(list[0].size)
        for (v in list) for (i in out.indices) out[i] += v[i]
        return normalize(out)
    }

    /** What the face model expects: every value minus the average, divided by the spread (never by almost nothing). */
    fun standardize(pixels: FloatArray): FloatArray {
        if (pixels.isEmpty()) return pixels
        var sum = 0.0
        for (p in pixels) sum += p
        val mean = sum / pixels.size
        var sq = 0.0
        for (p in pixels) sq += (p - mean) * (p - mean)
        val std = max(sqrt(sq / pixels.size), 1.0 / sqrt(pixels.size.toDouble()))
        return FloatArray(pixels.size) { ((pixels[it] - mean) / std).toFloat() }
    }

    fun softmax(x: FloatArray): FloatArray {
        if (x.isEmpty()) return x
        val top = x.max()
        val e = FloatArray(x.size) { exp(x[it] - top) }
        val total = e.sum()
        return FloatArray(x.size) { e[it] / total }
    }

    /** The two photo-check models agree on a live face when, added together, "real" (the middle answer) is the biggest. Returns real or not, and how sure. */
    fun liveResult(first: FloatArray, second: FloatArray): Pair<Boolean, Float> {
        if (first.size != 3 || second.size != 3) return false to 0f
        val a = softmax(first)
        val b = softmax(second)
        val sum = FloatArray(3) { a[it] + b[it] }
        var best = 0
        for (i in 1..2) if (sum[i] > sum[best]) best = i
        return (best == 1) to (sum[best] / 2f)
    }

    /**
     * The part of the picture the photo check looks at: the face box made bigger by [scale] (but never past the picture), as left, top,
     * right and bottom. Taken from the open-source Silent-Face-Anti-Spoofing and deepface code.
     */
    fun scaledBox(srcW: Int, srcH: Int, x: Int, y: Int, w: Int, h: Int, scale: Float): IntArray {
        if (w <= 0 || h <= 0) return intArrayOf(0, 0, max(srcW - 1, 1), max(srcH - 1, 1))
        val s = min(min((srcH - 1f) / h, (srcW - 1f) / w), scale)
        val newW = w * s
        val newH = h * s
        val cx = w / 2 + x
        val cy = h / 2 + y
        var left = cx - newW / 2
        var top = cy - newH / 2
        var right = cx + newW / 2
        var bottom = cy + newH / 2
        if (left < 0) { right -= left; left = 0f }
        if (top < 0) { bottom -= top; top = 0f }
        if (right > srcW - 1) { left -= right - (srcW - 1); right = (srcW - 1).toFloat() }
        if (bottom > srcH - 1) { top -= bottom - (srcH - 1); bottom = (srcH - 1).toFloat() }
        return intArrayOf(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    /** Saved faces as bytes: how many, how long each, then the numbers. */
    fun encode(list: List<FloatArray>): ByteArray {
        val dim = list.firstOrNull()?.size ?: 0
        val buf = ByteBuffer.allocate(8 + list.size * dim * 4)
        buf.putInt(list.size).putInt(dim)
        for (v in list) for (x in v) buf.putFloat(x)
        return buf.array()
    }

    /** The saved faces read back, or an empty list when the bytes are not a list of faces. */
    fun decode(bytes: ByteArray): List<FloatArray> {
        if (bytes.size < 8) return emptyList()
        val buf = ByteBuffer.wrap(bytes)
        val count = buf.int
        val dim = buf.int
        if (count < 0 || dim < 0 || count > 1000 || dim > 4096 || bytes.size != 8 + count * dim * 4) return emptyList()
        return List(count) { FloatArray(dim) { buf.float } }
    }
}

/** What one camera picture showed. [similarity] is the best match with the saved faces, or -1 when there was no face to compare. */
data class FrameCheck(val faces: Int, val bigEnough: Boolean, val live: Boolean, val similarity: Float)

sealed class ScanResult {
    /** Still looking: [hint] says what to do. */
    data class Waiting(val hint: String) : ScanResult()
    object Passed : ScanResult()
    data class Failed(val reason: String) : ScanResult()
}

/**
 * Decides when a face scan has matched. It wants [needed] matching pictures in a row (one lucky picture is not enough), exactly one
 * face, big enough and live. It gives up after [timeoutMs], which counts as one failed scan.
 */
class FaceVerifier(private val threshold: Float, private val needed: Int = 3, private val timeoutMs: Long = 8_000L) {
    private var startedAt = -1L
    private var streak = 0
    private var lastHint = "Look at the camera"

    fun onFrame(frame: FrameCheck, nowMs: Long): ScanResult {
        if (startedAt < 0) startedAt = nowMs
        val hint = when {
            frame.faces == 0 -> "Look at the camera"
            frame.faces > 1 -> "Only you should be in view"
            !frame.bigEnough -> "Come a little closer"
            !frame.live -> "That does not look like a live face"
            frame.similarity >= threshold -> null
            else -> "Not recognised yet"
        }
        if (hint == null) streak++ else { streak = 0; lastHint = hint }
        if (streak >= needed) return ScanResult.Passed
        if (nowMs - startedAt >= timeoutMs) return ScanResult.Failed(lastHint)
        return ScanResult.Waiting(hint ?: "Hold still…")
    }

    /** Called on a timer, so a camera that sends nothing still runs out. */
    fun expire(nowMs: Long): ScanResult.Failed? =
        if (startedAt >= 0 && nowMs - startedAt >= timeoutMs) ScanResult.Failed(lastHint) else null
}

/** Collects the pictures used to save your face: one at a time, spaced out, all of the same person. */
class EnrolCollector(private val needed: Int = 6, private val minGapMs: Long = 450L, private val minAgreement: Float = 0.5f) {
    enum class Outcome { ACCEPTED, TOO_SOON, DIFFERENT_FACE }

    private val faces = ArrayList<FloatArray>()
    private var lastAt = -1L

    val count: Int get() = faces.size
    val done: Boolean get() = faces.size >= needed
    val target: Int get() = needed
    fun embeddings(): List<FloatArray> = faces.toList()

    fun add(embedding: FloatArray, nowMs: Long): Outcome {
        if (done) return Outcome.TOO_SOON
        if (lastAt >= 0 && nowMs - lastAt < minGapMs) return Outcome.TOO_SOON
        if (faces.isNotEmpty() && FaceMath.cosine(FaceMath.mean(faces), embedding) < minAgreement) return Outcome.DIFFERENT_FACE
        faces.add(FaceMath.normalize(embedding))
        lastAt = nowMs
        return Outcome.ACCEPTED
    }
}
