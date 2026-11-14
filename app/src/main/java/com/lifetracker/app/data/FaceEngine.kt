package com.lifetracker.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

/**
 * Looks at one camera picture: finds the face (ML Kit), turns it into a list of numbers that is alike for the same person
 * (a FaceNet model), and checks that it is a live face and not a photo (two MiniFASNet models). The models and the way they are
 * fed come from the open-source projects OnDevice-Face-Recognition-Android and FaceRecognition_With_FaceNet_Android (Apache 2.0).
 * Everything happens on the phone.
 */
class FaceEngine private constructor(private val context: Context) {
    /** [embedding] is null when there was not exactly one big enough face. [live] is null when the photo check was not asked for. */
    class Analysis(
        val faces: Int,
        val bigEnough: Boolean,
        val embedding: FloatArray?,
        val live: Boolean?,
        val liveScore: Float,
        val millis: Long,
    )

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.15f)
                .build(),
        )
    }
    private val embedder by lazy { Interpreter(mapAsset("facenet.tflite"), Interpreter.Options().apply { numThreads = 4 }) }
    private val spoofA by lazy { Interpreter(mapAsset("spoof_model_scale_2_7.tflite"), Interpreter.Options().apply { numThreads = 2 }) }
    private val spoofB by lazy { Interpreter(mapAsset("spoof_model_scale_4_0.tflite"), Interpreter.Options().apply { numThreads = 2 }) }

    private fun mapAsset(name: String): MappedByteBuffer =
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).use { it.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength) }
        }

    /** Runs on a background thread. Throws when a model cannot be loaded or run, with the reason. */
    @Synchronized
    fun analyze(frame: Bitmap, checkLive: Boolean): Analysis {
        val started = SystemClock.elapsedRealtime()
        fun took() = SystemClock.elapsedRealtime() - started
        val faces = Tasks.await(detector.process(InputImage.fromBitmap(frame, 0)))
        if (faces.size != 1) return Analysis(faces.size, false, null, null, 0f, took())
        val box = faces[0].boundingBox
        if (box.width() < frame.width * MIN_FACE_SHARE) return Analysis(1, false, null, null, 0f, took())
        val embedding = embed(frame, box)
        var live: Boolean? = null
        var score = 0f
        if (checkLive) {
            val (isLive, s) = liveness(frame, box)
            live = isLive
            score = s
        }
        return Analysis(1, true, embedding, live, score, took())
    }

    private fun clamp(box: Rect, w: Int, h: Int): Rect {
        val left = box.left.coerceIn(0, w - 1)
        val top = box.top.coerceIn(0, h - 1)
        val right = box.right.coerceIn(left + 1, w)
        val bottom = box.bottom.coerceIn(top + 1, h)
        return Rect(left, top, right, bottom)
    }

    private fun embed(frame: Bitmap, box: Rect): FloatArray {
        val size = embedder.getInputTensor(0).shape()[1]
        val dim = embedder.getOutputTensor(0).shape().last()
        val r = clamp(box, frame.width, frame.height)
        val crop = Bitmap.createBitmap(frame, r.left, r.top, r.width(), r.height())
        val scaled = Bitmap.createScaledBitmap(crop, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val floats = FloatArray(size * size * 3)
        var i = 0
        for (p in pixels) {
            floats[i++] = ((p shr 16) and 0xFF).toFloat()
            floats[i++] = ((p shr 8) and 0xFF).toFloat()
            floats[i++] = (p and 0xFF).toFloat()
        }
        val input = ByteBuffer.allocateDirect(floats.size * 4).order(ByteOrder.nativeOrder())
        for (x in FaceMath.standardize(floats)) input.putFloat(x)
        input.rewind()
        val out = Array(1) { FloatArray(dim) }
        embedder.run(input, out)
        return FaceMath.normalize(out[0])
    }

    private fun spoofInput(frame: Bitmap, box: Rect, scale: Float): ByteBuffer {
        val r = FaceMath.scaledBox(frame.width, frame.height, box.left, box.top, box.width(), box.height(), scale)
        val left = r[0].coerceIn(0, frame.width - 1)
        val top = r[1].coerceIn(0, frame.height - 1)
        val width = (r[2] - r[0]).coerceIn(1, frame.width - left)
        val height = (r[3] - r[1]).coerceIn(1, frame.height - top)
        val scaled = Bitmap.createScaledBitmap(Bitmap.createBitmap(frame, left, top, width, height), SPOOF_SIZE, SPOOF_SIZE, true)
        val pixels = IntArray(SPOOF_SIZE * SPOOF_SIZE)
        scaled.getPixels(pixels, 0, SPOOF_SIZE, 0, 0, SPOOF_SIZE, SPOOF_SIZE)
        val input = ByteBuffer.allocateDirect(pixels.size * 3 * 4).order(ByteOrder.nativeOrder())
        // The model was trained on blue, green, red.
        for (p in pixels) {
            input.putFloat((p and 0xFF).toFloat())
            input.putFloat(((p shr 8) and 0xFF).toFloat())
            input.putFloat(((p shr 16) and 0xFF).toFloat())
        }
        input.rewind()
        return input
    }

    private fun liveness(frame: Bitmap, box: Rect): Pair<Boolean, Float> {
        val a = Array(1) { FloatArray(3) }
        val b = Array(1) { FloatArray(3) }
        spoofA.run(spoofInput(frame, box, 2.7f), a)
        spoofB.run(spoofInput(frame, box, 4.0f), b)
        return FaceMath.liveResult(a[0], b[0])
    }

    companion object {
        /** A face narrower than this share of the picture is too far away to count. */
        const val MIN_FACE_SHARE = 0.30f
        private const val SPOOF_SIZE = 80

        @Volatile private var instance: FaceEngine? = null

        fun get(context: Context): FaceEngine =
            instance ?: synchronized(this) { instance ?: FaceEngine(context.applicationContext).also { instance = it } }
    }
}
