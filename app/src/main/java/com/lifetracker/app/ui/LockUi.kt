package com.lifetracker.app.ui

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lifetracker.app.R
import com.lifetracker.app.data.FaceMath
import com.lifetracker.app.data.FaceStore
import com.lifetracker.app.data.FaceVerifier
import com.lifetracker.app.data.FrameCheck
import com.lifetracker.app.data.LockMode
import com.lifetracker.app.data.LockRules
import com.lifetracker.app.data.LockSettings
import com.lifetracker.app.data.LockState
import com.lifetracker.app.data.PhoneLock
import com.lifetracker.app.data.ScanResult
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import kotlinx.coroutines.delay

/**
 * Wraps a screen so it only shows once it is unlocked, if you locked it in Settings → Lock. Nothing of the screen is drawn
 * (or loaded) while it is locked. A screen stays open while you use it and locks again after the time you chose, counted from when
 * you leave it or the app.
 */
@Composable
fun LockGate(screen: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mode = LockRules.effectiveMode(LockSettings.mode(context, screen), FaceStore.exists(context))
    if (mode == LockMode.OFF) {
        content()
        return
    }
    DisposableEffect(screen, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> LockState.reenter(screen)
                Lifecycle.Event.ON_STOP -> LockState.leaveAll(LockSettings.stayMinutes(context))
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            LockState.leave(screen, LockSettings.stayMinutes(context))
        }
    }
    if (LockState.isOpen(screen)) content() else LockScreen(screen, mode)
}

@Composable
private fun LockScreen(screen: String, mode: LockMode) {
    val p = LocalPaper.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_p_lock), contentDescription = null, tint = p.ink, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text("$screen is locked", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (mode == LockMode.FACE) FaceLockPanel(screen) else PhoneLockPanel(screen)
    }
}

private fun openSecuritySettings(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun PhoneLockPanel(screen: String) {
    val context = LocalContext.current
    val p = LocalPaper.current
    var message by remember { mutableStateOf("") }
    val available = PhoneLock.available(context)
    fun ask() {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            message = "The phone lock could not be opened here."
            return
        }
        PhoneLock.prompt(activity, "Unlock $screen", "Use your fingerprint, face, pattern or PIN", pinOnly = false) { ok, why ->
            if (ok) LockState.unlock(screen) else message = why
        }
    }
    LaunchedEffect(screen) { if (available) ask() }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!available) {
            Text(PhoneLock.statusText(context), fontSize = 13.sp, color = p.bad, textAlign = TextAlign.Center)
            PaperButton("Open the phone's security settings", { openSecuritySettings(context) }, style = PaperButtonStyle.Ghost)
        } else {
            if (message.isNotEmpty()) Text(message, fontSize = 13.sp, color = p.ink2, textAlign = TextAlign.Center)
            PaperButton("Unlock", { ask() })
        }
    }
}

@Composable
private fun FaceLockPanel(screen: String) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val saved = remember { FaceStore.load(context) }
    val threshold = remember { LockSettings.strictness(context).threshold }
    val blockPhoto = remember { LockSettings.blockPhoto(context) }
    var problem by remember { mutableStateOf("") }
    val camera = rememberCameraAccess { problem = "Camera access was refused." }
    var scanKey by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val misses = LockState.misses(screen)
    val pinNow = LockRules.needsPin(misses) || problem.isNotEmpty() || saved.isEmpty()
    val verifier = remember(scanKey) { FaceVerifier(threshold) }

    fun askPin() {
        val activity = context.findFragmentActivity()
        if (activity == null || !PhoneLock.pinAvailable(context)) {
            message = PhoneLock.statusText(context)
            return
        }
        PhoneLock.prompt(activity, "Unlock $screen", "Enter your phone PIN, pattern or password", pinOnly = true) { ok, why ->
            if (ok) LockState.unlock(screen) else message = why
        }
    }

    fun handle(result: ScanResult) {
        when (result) {
            ScanResult.Passed -> {
                scanning = false
                LockState.unlock(screen)
            }
            is ScanResult.Failed -> {
                scanning = false
                LockState.addMiss(screen)
                hint = "Not recognised: " + result.reason.replaceFirstChar { it.lowercase() }
            }
            is ScanResult.Waiting -> hint = result.hint
        }
    }

    LaunchedEffect(camera.granted, scanKey, pinNow) {
        if (pinNow) return@LaunchedEffect
        if (!camera.granted) {
            camera.ask()
            return@LaunchedEffect
        }
        hint = "Look at the camera"
        scanning = true
    }
    LaunchedEffect(pinNow) { if (pinNow) askPin() }
    LaunchedEffect(scanning, scanKey) {
        while (scanning) {
            delay(300)
            verifier.expire(SystemClock.elapsedRealtime())?.let { handle(it) }
        }
    }
    FaceScanEffect(
        active = scanning && !pinNow,
        checkLive = blockPhoto,
        onError = { problem = it; scanning = false },
        onAnalysis = { a ->
            val similarity = a.embedding?.let { FaceMath.bestSimilarity(saved, it) } ?: -1f
            handle(verifier.onFrame(FrameCheck(a.faces, a.bigEnough, a.live ?: true, similarity), SystemClock.elapsedRealtime()))
        },
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        when {
            pinNow -> {
                val why = when {
                    problem.isNotEmpty() -> problem + " Use your phone PIN instead."
                    saved.isEmpty() -> "No face is saved. Use your phone PIN, or add your face in Settings → Lock."
                    else -> "Your face was not recognised ${LockRules.MAX_FACE_ATTEMPTS} times. Use your phone PIN."
                }
                Text(why, fontSize = 13.sp, color = p.ink2, textAlign = TextAlign.Center)
                if (message.isNotEmpty()) Text(message, fontSize = 12.5.sp, color = p.bad, textAlign = TextAlign.Center)
                PaperButton("Use my phone PIN", { askPin() })
            }
            scanning -> Text(hint.ifEmpty { "Look at the camera" }, fontSize = 15.sp, color = p.ink, textAlign = TextAlign.Center)
            else -> {
                if (hint.isNotEmpty()) {
                    Text(hint + " (" + misses + " of " + LockRules.MAX_FACE_ATTEMPTS + ")", fontSize = 13.sp, color = p.bad, textAlign = TextAlign.Center)
                }
                if (!camera.granted) Text("Camera access is needed to scan your face.", fontSize = 13.sp, color = p.ink2, textAlign = TextAlign.Center)
                PaperButton("Scan my face", { if (camera.granted) scanKey++ else camera.ask() })
            }
        }
    }
}
