package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.LiveRules
import com.lifetracker.app.data.LiveSnapshot
import com.lifetracker.app.data.TrackService
import com.lifetracker.app.data.TripRules
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard

/** "12 s ago", "4 min ago", "2 h ago". */
internal fun agoText(nowMs: Long, thenMs: Long): String {
    val s = ((nowMs - thenMs) / 1000L).coerceAtLeast(0L)
    return when {
        s < 60 -> "$s s ago"
        s < 3600 -> "${s / 60} min ago"
        else -> "${s / 3600} h ago"
    }
}

/** "5 min · 430 m · 5 km/h". */
internal fun movingNumbers(snap: LiveSnapshot): String {
    val live = snap.live
    val min = ((snap.nowMs - live.sinceMs) / 60_000L).toInt().coerceAtLeast(0)
    return (if (min < 1) "just started" else formatDuration(min)) + " · " + TripRules.distanceText(live.distanceM) + " · " + live.kmh + " km/h"
}

/**
 * Where you are right now, on a map that follows you. Under the map: whether recording is running, the last reading and how
 * exact it is, how often readings are taken and which profile is in force. It only reads, except the Pause and Resume button.
 */
@Composable
fun LiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val paper = LocalPaper.current
    var refresh by remember { mutableIntStateOf(0) }
    val snap by rememberLive(withPath = true, everyMs = 4_000L, refresh = refresh)
    val db = remember { AppDatabase.get(context) }
    val places by db.placeDao().observePlaces().collectAsState(emptyList())
    val areas by db.mapDao().observeAreas().collectAsState(emptyList())
    var centre by remember { mutableIntStateOf(0) }
    var following by remember { mutableStateOf(true) }
    BackHandler(onBack = onBack)

    val s = snap
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            SettingsTop("On the move", if (s == null) "Looking for your position..." else LiveRules.sinceText(s.live, s.nowMs), onBack)
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LiveMap(
                fix = s?.last,
                moving = s?.live?.state == LiveRules.State.MOVING,
                path = s?.path.orEmpty(),
                places = places,
                areas = areas,
                centreRequest = centre,
                onFollowing = { following = it },
                modifier = Modifier.fillMaxSize(),
            )
            if (!following) {
                PaperButton("Centre on me", { centre++; following = true }, small = true, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (s != null) {
                SetCard {
                    KvRow("Recording", when (s.live.state) {
                        LiveRules.State.OFF -> "Off (GPS all day is not switched on)"
                        LiveRules.State.PAUSED -> "Paused by you"
                        LiveRules.State.WAITING -> "On, waiting for a position"
                        else -> "On"
                    }, first = true)
                    KvRow("Last reading", s.last?.let { agoText(s.nowMs, it.ms) + (if (it.acc > 0f) ", within " + it.acc.toInt() + " m" else "") } ?: "none yet")
                    KvRow("How often", "about every " + s.intervalSec + " s")
                    KvRow("Profile in force", s.profileName.ifEmpty { "none (your normal settings)" })
                    if (s.status.isNotEmpty()) KvRow("Right now", s.status)
                }
                if (s.allDay) {
                    PaperButton(
                        if (s.paused) "Resume recording" else "Pause recording",
                        { TrackService.setPaused(context, !s.paused); refresh++ },
                        style = if (s.paused) PaperButtonStyle.Dark else PaperButtonStyle.Ghost,
                        small = true,
                    )
                }
            }
        }
    }
}

/** The "On the move" row at the top of today on the Timeline. Tap it for the live map. */
@Composable
internal fun OnTheMoveRow(snap: LiveSnapshot, onClick: () -> Unit) {
    val paper = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(paper.accent, CircleShape))
            Column(modifier = Modifier.weight(1f)) {
                Text("On the move", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = paper.ink)
                Text(movingNumbers(snap), fontSize = 12.sp, color = paper.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Live map", fontSize = 12.sp, color = paper.ink2)
        }
    }
}
