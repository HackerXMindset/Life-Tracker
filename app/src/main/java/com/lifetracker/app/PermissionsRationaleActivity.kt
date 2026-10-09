package com.lifetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifetracker.app.ui.theme.LifeTrackerTheme

/** What Health Connect shows when it asks why this app wants your steps and sleep. */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifeTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Your steps and sleep", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "This app reads your steps and any sleep that other apps saved in Health Connect, only to show them on your Timeline. " +
                                "They are copied into this app's own database on this phone. They are never sent anywhere, " +
                                "and there is no account or server. You can take the permission back at any time in Health Connect.",
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
