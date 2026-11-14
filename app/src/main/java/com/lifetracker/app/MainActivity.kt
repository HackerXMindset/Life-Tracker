package com.lifetracker.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.lifetracker.app.ui.LifeTrackerApp
import com.lifetracker.app.ui.theme.PaperBackground
import com.lifetracker.app.ui.theme.LifeTrackerTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeTrackerTheme {
                PaperBackground {
                    LifeTrackerApp()
                }
            }
        }
    }
}
