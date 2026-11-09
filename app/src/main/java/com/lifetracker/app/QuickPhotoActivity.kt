package com.lifetracker.app

import android.os.Bundle
import java.time.LocalDate
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifetracker.app.data.PhotoQueue
import com.lifetracker.app.data.QuickPhoto
import com.lifetracker.app.ui.CheckSource
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
        // A notification about a photo that was taken without internet comes with the id of that photo.
        val queued = intent.getStringExtra(QuickPhoto.EXTRA_QUEUED)?.let { PhotoQueue.get(this, it) }
        setContent {
            LifeTrackerTheme {
                PaperBackground {
                    QuickPhotoScreen(
                        onClose = { finish() },
                        startMeal = queued?.meal,
                        date = queued?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(),
                        initialSource = if (queued?.label == true) CheckSource.Label else CheckSource.Meal,
                        queued = queued,
                    )
                }
            }
        }
    }
}
