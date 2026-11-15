package com.lifetracker.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.lifetracker.app.data.ComickAuth
import com.lifetracker.app.ui.LifeTrackerApp
import com.lifetracker.app.ui.theme.PaperBackground
import com.lifetracker.app.ui.theme.LifeTrackerTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleSignIn(intent)
        setContent {
            LifeTrackerTheme {
                PaperBackground {
                    LifeTrackerApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSignIn(intent)
    }

    /** Comick's sign-in comes back to the app as an address that starts with the app's own name. */
    private fun handleSignIn(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != ComickAuth.SCHEME) return
        val url = data.toString()
        val app = applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { ComickAuth.handleCallback(app, url) } }
    }
}
