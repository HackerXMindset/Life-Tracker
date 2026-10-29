package com.lifetracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lifetracker.app.data.LiveSnapshot
import com.lifetracker.app.data.LiveSource
import kotlinx.coroutines.delay

/**
 * The live picture, looked at again every few seconds, but only while the screen is on and this screen is showing
 * (the lifecycle stops the loop when the app goes to the background). Change [refresh] to look again at once.
 */
@Composable
fun rememberLive(withPath: Boolean, everyMs: Long = 5_000L, refresh: Int = 0): State<LiveSnapshot?> {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf<LiveSnapshot?>(null) }
    LaunchedEffect(owner, withPath, refresh) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                runCatching { LiveSource.load(context, withPath) }.getOrNull()?.let { state.value = it }
                delay(everyMs)
            }
        }
    }
    return state
}
