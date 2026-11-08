package com.lifetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifetracker.app.ui.QuickPhotoScreen
import com.lifetracker.app.ui.theme.LifeTrackerTheme
import com.lifetracker.app.ui.theme.PaperBackground

/**
 * Opened by the home-screen button, the icon shortcut and the quick-settings tile: starts the camera, has the photo read,
 * and shows the check page. Closing it returns to wherever you were.
 */
class QuickPhotoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeTrackerTheme {
                PaperBackground {
                    QuickPhotoScreen(onClose = { finish() })
                }
            }
        }
    }
}
