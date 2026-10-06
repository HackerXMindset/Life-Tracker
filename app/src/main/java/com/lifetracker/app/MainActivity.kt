package com.lifetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifetracker.app.ui.LifeTrackerApp
import com.lifetracker.app.ui.theme.LifeTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LifeTrackerTheme {
                LifeTrackerApp()
            }
        }
    }
}
