package com.lifetracker.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.lifetracker.app.data.ActivityTypeEntity
import com.lifetracker.app.data.ActivityStats

/** The colour an activity is drawn in. One stored colour; in dark mode it is lightened so it stays readable. */
@Composable
fun ActivityTypeEntity.displayColor(): Color {
    val base = Color(color.toInt())
    return if (isSystemInDarkTheme()) lerp(base, Color.White, 0.3f) else base
}

/** Colours offered when making or editing an activity. */
val ACTIVITY_PALETTE: List<Long> = listOf(
    0xFF5B5FC7L, 0xFF3F51B5L, 0xFF2878B4L, 0xFF1A9AA8L,
    0xFF2E9E6BL, 0xFF17785AL, 0xFF6BA32AL, 0xFFE0A100L,
    0xFFC28410L, 0xFFCC5F22L, 0xFFB83A3AL, 0xFFD6457FL,
    0xFF8A5FC2L, 0xFF8C5A3CL, 0xFF738078L, 0xFF4A5568L,
)

/** "At least 8h a day", "At most 2h 30m a day" or "No goal". */
fun goalText(type: ActivityTypeEntity): String = when {
    type.goalMinutes <= 0 -> "No goal"
    type.goalKind == ActivityTypeEntity.GOAL_AT_MOST -> "At most ${formatDuration(type.goalMinutes)} a day"
    else -> "At least ${formatDuration(type.goalMinutes)} a day"
}

/** The activity for a stored key, or a plain grey one if it has since gone missing. */
fun List<ActivityTypeEntity>.byId(id: String): ActivityTypeEntity = ActivityStats.find(this, id)
