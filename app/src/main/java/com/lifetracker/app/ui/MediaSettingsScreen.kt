package com.lifetracker.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.MediaAccounts
import com.lifetracker.app.data.MediaClients
import com.lifetracker.app.data.MediaException
import com.lifetracker.app.data.MediaNames
import com.lifetracker.app.data.MediaParsers
import com.lifetracker.app.data.MediaService
import com.lifetracker.app.data.MediaSettings
import com.lifetracker.app.data.MediaSuggest
import com.lifetracker.app.data.MediaSync
import com.lifetracker.app.data.MediaSyncState
import com.lifetracker.app.data.MediaSyncWorker
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private fun openLink(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Settings → Shows and manga: your accounts, which app goes with which account, and how long things are taken to be. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    val accounts = remember { MediaAccounts(context) }
    val settings = remember { MediaSettings(context) }
    val state = remember { MediaSyncState(context) }
    var tick by remember { mutableIntStateOf(0) }
    val connected = remember(tick) { MediaService.entries.associateWith { accounts.connected(it) } }
    val appMap = remember(tick) { settings.appMap() }
    val usageApps by remember { AppDatabase.get(context).usageDao().observeApps() }.collectAsState(initial = emptyList())

    var aniListText by remember { mutableStateOf("") }
    var malText by remember { mutableStateOf("") }
    var clientText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var working by remember { mutableStateOf("") }
    var deviceCode by remember { mutableStateOf<MediaParsers.DeviceCode?>(null) }
    var lastLine by remember(tick) { mutableStateOf(state.lastLine) }
    var chapterMinutes by remember { mutableIntStateOf(settings.minutesPerChapter) }
    var lookback by remember { mutableIntStateOf(settings.lookbackDays) }
    var pickApp by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }

    fun connectedNow() {
        MediaSyncWorker.schedule(context)
        tick++
        scope.launch {
            val outcome = runCatching { MediaSync.run(context) }.getOrNull()
            lastLine = outcome?.line ?: state.lastLine
        }
    }

    // While a Simkl code is showing, ask Simkl every few seconds whether it was typed in.
    LaunchedEffect(deviceCode) {
        val code = deviceCode ?: return@LaunchedEffect
        val clientId = accounts.simklClientId
        var wait = code.intervalSeconds
        val deadline = System.currentTimeMillis() + 10 * 60_000L
        while (isActive && System.currentTimeMillis() < deadline) {
            delay(wait * 1000L)
            val result = try {
                MediaClients.simklPoll(clientId, code.deviceCode)
            } catch (e: MediaException) {
                MediaParsers.TokenPoll.Failed(e.message ?: "Could not reach Simkl.")
            }
            when (result) {
                is MediaParsers.TokenPoll.Token -> {
                    accounts.simklToken = result.accessToken
                    deviceCode = null
                    message = "Simkl is connected."
                    connectedNow()
                    return@LaunchedEffect
                }
                is MediaParsers.TokenPoll.Pending -> {}
                is MediaParsers.TokenPoll.SlowDown -> wait += 5
                is MediaParsers.TokenPoll.Failed -> {
                    message = result.message
                    deviceCode = null
                    return@LaunchedEffect
                }
            }
        }
        message = "The code ran out. Start again."
        deviceCode = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("Shows and manga", "Fill the Timeline from your lists", onBack) }
        item {
            Text(
                "Connect the accounts your reading and watching apps send progress to. Kairos checks them every 15 minutes or so and puts what you read " +
                    "and watched on the Timeline, at the time it happened and as long as it took. Tell it which app goes with which account and it uses " +
                    "the time you spent in that app. Anything with no app time is added by itself. Time you wrote down is never covered, and an entry you delete stays deleted.",
                fontSize = 13.sp, color = p.ink2,
            )
        }
        if (message.isNotEmpty()) item { Text(message, fontSize = 13.sp, color = p.ink, fontWeight = FontWeight.SemiBold) }

        // ---- AniList ----
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AniList", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    if (connected[MediaService.ANILIST] == true) {
                        Text("Connected as ${accounts.aniListUser}.", fontSize = 13.sp, color = p.ink2)
                        PaperButton("Remove", {
                            accounts.disconnect(MediaService.ANILIST); state.clear(MediaService.ANILIST); tick++
                        }, style = PaperButtonStyle.Ghost, small = true)
                    } else {
                        Text("Just your AniList name or profile link. Your list must be public (no sign-in is needed).", fontSize = 12.5.sp, color = p.ink2)
                        PaperField(aniListText, { aniListText = it }, "AniList name or link", Modifier.fillMaxWidth(), singleLine = true)
                        PaperButton(if (working == "anilist") "Checking…" else "Connect", {
                            val name = MediaNames.aniList(aniListText)
                            if (name == null) {
                                message = "That does not look like an AniList name."
                            } else {
                                working = "anilist"; message = ""
                                scope.launch {
                                    try {
                                        val id = MediaClients.aniListUserId(name)
                                        accounts.aniListUser = name; accounts.aniListId = id
                                        state.clear(MediaService.ANILIST); state.lastSyncMs = 0L
                                        aniListText = ""; message = "AniList is connected."
                                        connectedNow()
                                    } catch (e: MediaException) {
                                        message = e.message ?: "Could not check AniList."
                                    }
                                    working = ""
                                }
                            }
                        }, small = true, enabled = working.isEmpty() && aniListText.isNotBlank())
                    }
                }
            }
        }

        // ---- MyAnimeList ----
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MyAnimeList", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    if (connected[MediaService.MAL] == true) {
                        Text("Connected as ${accounts.malUser}.", fontSize = 13.sp, color = p.ink2)
                        PaperButton("Remove", { accounts.disconnect(MediaService.MAL); tick++ }, style = PaperButtonStyle.Ghost, small = true)
                    } else {
                        Text(
                            "Just your MyAnimeList name or profile link, and the list must be public. It is read through Jikan, a free service that shows public MyAnimeList lists, so no sign-in is needed.",
                            fontSize = 12.5.sp, color = p.ink2,
                        )
                        PaperField(malText, { malText = it }, "MyAnimeList name or link", Modifier.fillMaxWidth(), singleLine = true)
                        PaperButton(if (working == "mal") "Checking…" else "Connect", {
                            val name = MediaNames.myAnimeList(malText)
                            if (name == null) {
                                message = "That does not look like a MyAnimeList name."
                            } else {
                                working = "mal"; message = ""
                                scope.launch {
                                    try {
                                        if (MediaClients.malUserExists(name)) {
                                            accounts.malUser = name; state.lastSyncMs = 0L
                                            malText = ""; message = "MyAnimeList is connected."
                                            connectedNow()
                                        } else {
                                            message = "MyAnimeList has no user with that name."
                                        }
                                    } catch (e: MediaException) {
                                        message = e.message ?: "Could not check MyAnimeList."
                                    }
                                    working = ""
                                }
                            }
                        }, small = true, enabled = working.isEmpty() && malText.isNotBlank())
                    }
                }
            }
        }

        // ---- Simkl ----
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simkl", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    val code = deviceCode
                    when {
                        connected[MediaService.SIMKL] == true -> {
                            Text("Signed in.", fontSize = 13.sp, color = p.ink2)
                            PaperButton("Remove", {
                                accounts.disconnect(MediaService.SIMKL); state.clear(MediaService.SIMKL); tick++
                            }, style = PaperButtonStyle.Ghost, small = true)
                        }
                        code != null -> {
                            Text("Type this code at Simkl:", fontSize = 13.sp, color = p.ink2)
                            Text(code.userCode, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = p.ink)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaperButton("Open Simkl", { openLink(context, code.verificationUrl) }, small = true)
                                PaperButton("Cancel", { deviceCode = null }, style = PaperButtonStyle.Ghost, small = true)
                            }
                            Text("Waiting for you to allow it…", fontSize = 12.sp, color = p.ink2)
                        }
                        else -> {
                            Text(
                                "Simkl needs a small app of your own, once, for its sign-in. It takes about a minute: open Simkl's developer page, make a new app named Kairos " +
                                    "(for the redirect address use urn:ietf:wg:oauth:2.0:oob), copy its Client ID, and paste it below.",
                                fontSize = 12.5.sp, color = p.ink2,
                            )
                            PaperButton("Open Simkl developer page", { openLink(context, "https://simkl.com/settings/developer/") }, style = PaperButtonStyle.Ghost, small = true)
                            PaperField(
                                clientText, { clientText = it }, "Client ID", Modifier.fillMaxWidth(), singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            )
                            PaperButton(if (working == "simkl") "Asking Simkl…" else "Get my code", {
                                val id = MediaNames.simklClientId(clientText)
                                if (id == null) {
                                    message = "That does not look like a Client ID. Copy the whole thing from your Simkl app."
                                } else {
                                    working = "simkl"; message = ""
                                    scope.launch {
                                        try {
                                            val started = MediaClients.simklDeviceStart(id)
                                            accounts.simklClientId = id
                                            deviceCode = started
                                            openLink(context, started.verificationUrl)
                                        } catch (e: MediaException) {
                                            message = e.message ?: "Could not reach Simkl."
                                        }
                                        working = ""
                                    }
                                }
                            }, small = true, enabled = working.isEmpty() && clientText.isNotBlank())
                        }
                    }
                }
            }
        }

        // ---- Apps ----
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Which app goes with which account", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(
                        "Time spent in an app is used for what its account notes. Mihon sends to AniList, Kotatsu to MyAnimeList, CloudStream to Simkl, for example.",
                        fontSize = 12.5.sp, color = p.ink2,
                    )
                    val shown = usageApps.filter { !it.ignored && (it.pkg in appMap || MediaSuggest.forPackage(it.pkg) != null) }
                    if (usageApps.isEmpty()) {
                        Text("No apps seen yet. Switch on Phone usage first, so Kairos knows which apps you use.", fontSize = 12.5.sp, color = p.ink2)
                    }
                    shown.forEach { app ->
                        val chosen = appMap[app.pkg]
                        val suggestion = MediaSuggest.forPackage(app.pkg)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(app.label, fontSize = 14.sp, color = p.ink)
                            if (chosen == null && suggestion != null) Text("Usually goes with ${suggestion.label}", fontSize = 12.sp, color = p.ink2)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaperPill("None", chosen == null, { settings.setApp(app.pkg, null); tick++ })
                                MediaService.entries.forEach { s ->
                                    PaperPill(s.label, chosen == s, { settings.setApp(app.pkg, s); tick++ })
                                }
                            }
                        }
                    }
                    PaperButton("Add another app", { search = ""; pickApp = true }, style = PaperButtonStyle.Ghost, small = true, enabled = usageApps.isNotEmpty())
                }
            }
        }

        // ---- Sizes ----
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("How long a chapter takes", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MediaSettings.CHAPTER_OPTIONS.forEach { minutes ->
                            PaperPill("$minutes min", chapterMinutes == minutes, { chapterMinutes = minutes; settings.minutesPerChapter = minutes })
                        }
                    }
                    Text(
                        "Used when a chapter has no app time to take. Episodes and movies use the length the service knows.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                    Text("How far back to fill", fontSize = 14.sp, color = p.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MediaSettings.LOOKBACK_OPTIONS.forEach { (days, label) ->
                            PaperPill(label, lookback == days, {
                                lookback = days; settings.lookbackDays = days
                                state.clear(MediaService.ANILIST); state.clear(MediaService.SIMKL); state.lastSyncMs = 0L
                            })
                        }
                    }
                    Text("Used for the first check and for entries you add to a service for a day in the past.", fontSize = 12.sp, color = p.ink2)
                }
            }
        }

        // ---- Check now ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton(if (working == "sync") "Checking…" else "Check now", {
                    working = "sync"
                    scope.launch {
                        val outcome = runCatching { MediaSync.run(context) }.getOrNull()
                        lastLine = outcome?.line ?: "Connect an account first (and keep Shows and manga switched on in Features)."
                        working = ""
                    }
                }, Modifier.fillMaxWidth(), enabled = working.isEmpty())
                if (lastLine.isNotEmpty()) Text("Last check: $lastLine", fontSize = 12.5.sp, color = p.ink2)
            }
        }
    }

    if (pickApp) {
        PaperDialog("Choose an app", { pickApp = false }) {
            PaperField(search, { search = it }, "Search", Modifier.fillMaxWidth(), singleLine = true)
            val matches = usageApps.filter { !it.ignored && it.pkg !in appMap && (search.isBlank() || it.label.contains(search, ignoreCase = true)) }.take(8)
            if (matches.isEmpty()) Text("No app matches.", fontSize = 13.sp, color = p.ink2)
            matches.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaperButton(app.label, {
                        settings.setApp(app.pkg, MediaSuggest.forPackage(app.pkg) ?: MediaService.ANILIST)
                        tick++; pickApp = false
                    }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost, small = true)
                }
            }
        }
    }
}
