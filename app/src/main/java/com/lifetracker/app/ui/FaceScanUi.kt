package com.lifetracker.app.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lifetracker.app.data.FaceEngine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The activity behind a context (Android wraps it), as the kind the phone-lock prompt needs. */
internal fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Whether the camera may be used, and a way to ask. [onDenied] runs when the person says no. */
class CameraAccess(val granted: Boolean, val ask: () -> Unit)

@Composable
fun rememberCameraAccess(onDenied: () -> Unit = {}): CameraAccess {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val denied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        if (!it) denied()
    }
    return CameraAccess(granted) { launcher.launch(Manifest.permission.CAMERA) }
}

/**
 * Runs the front camera while [active], and calls [onAnalysis] on the main thread for each picture (a few a second): the face found,
 * its fingerprint and the live-face result. The camera stops as soon as it is not wanted or the screen goes away. [onError] gets the
 * reason when the camera or a model cannot start.
 */
@Composable
fun FaceScanEffect(active: Boolean, checkLive: Boolean, onError: (String) -> Unit, onAnalysis: (FaceEngine.Analysis) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestAnalysis by rememberUpdatedState(onAnalysis)
    val latestError by rememberUpdatedState(onError)
    val wantLive by rememberUpdatedState(checkLive)
    DisposableEffect(active, owner) {
        if (!active) return@DisposableEffect onDispose { }
        val main = ContextCompat.getMainExecutor(context)
        val worker = Executors.newSingleThreadExecutor()
        val stopped = AtomicBoolean(false)
        val failed = AtomicBoolean(false)
        var provider: ProcessCameraProvider? = null
        fun fail(text: String) {
            if (failed.compareAndSet(false, true)) main.execute { if (!stopped.get()) latestError(text) }
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val p = future.get()
                provider = p
                if (stopped.get()) return@addListener
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(worker) { image ->
                    try {
                        if (!stopped.get() && !failed.get()) {
                            val raw = image.toBitmap()
                            val turn = image.imageInfo.rotationDegrees
                            val upright = if (turn == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(turn.toFloat()) }, true)
                            val result = FaceEngine.get(context).analyze(upright, wantLive)
                            main.execute { if (!stopped.get()) latestAnalysis(result) }
                        }
                    } catch (e: Throwable) {
                        fail("The face check could not run: " + (e.message ?: e.javaClass.simpleName))
                    } finally {
                        image.close()
                    }
                }
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
            } catch (e: Exception) {
                fail("The front camera could not start: " + (e.message ?: e.javaClass.simpleName))
            }
        }, main)
        onDispose {
            stopped.set(true)
            runCatching { provider?.unbindAll() }
            worker.shutdown()
        }
    }
}
