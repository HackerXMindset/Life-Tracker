package com.lifetracker.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.EnrolCollector
import com.lifetracker.app.data.FaceEngine
import com.lifetracker.app.data.FaceMath
import com.lifetracker.app.data.FaceStore
import com.lifetracker.app.data.LockMode
import com.lifetracker.app.data.LockRules
import com.lifetracker.app.data.LockSettings
import com.lifetracker.app.data.PhoneLock
import com.lifetracker.app.data.Strictness
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.PaperToggle

private const val SETTINGS_TAB = "Settings"

/** Settings → Lock: which screens are locked and how, how long they stay open, and your saved face. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    var sub by remember { mutableStateOf("") }
    var faceSaved by remember { mutableStateOf(FaceStore.exists(context)) }
    val screens = remember { Tab.entries.map { it.title } }
    val modes = remember { mutableStateMapOf<String, LockMode>().apply { screens.forEach { put(it, LockSettings.mode(context, it)) } } }
    var stay by remember { mutableIntStateOf(LockSettings.stayMinutes(context)) }
    var strictness by remember { mutableStateOf(LockSettings.strictness(context)) }
    var blockPhoto by remember { mutableStateOf(LockSettings.blockPhoto(context)) }
    var note by remember { mutableStateOf("") }

    when (sub) {
        "enrol" -> {
            FaceEnrolScreen(onDone = { faceSaved = FaceStore.exists(context); sub = "" }, onBack = { sub = "" })
            return
        }
        "test" -> {
            FaceTestScreen(onBack = { sub = "" })
            return
        }
    }
    BackHandler(onBack = onBack)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { SettingsTop("Lock", "Keep each screen private", onBack) }
        item {
            Text(
                "Lock a screen so that opening it asks for your fingerprint, face, pattern or PIN (Phone lock), or scans your face first (Face scan). " +
                    "Each screen is locked on its own, so someone you hand the Food screen to cannot open the Timeline.",
                fontSize = 13.sp, color = p.ink2,
            )
        }
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Screens", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    screens.forEach { screen ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(screen, fontSize = 14.sp, color = p.ink)
                            PaperSegmented(
                                options = LockMode.entries.map { it.label },
                                selected = LockMode.entries.indexOf(modes[screen] ?: LockMode.OFF),
                                onSelect = { index ->
                                    val chosen = LockMode.entries[index]
                                    when {
                                        chosen != LockMode.OFF && !PhoneLock.available(context) ->
                                            note = PhoneLock.statusText(context) + " It is also what the PIN fallback uses."
                                        chosen == LockMode.FACE && !faceSaved ->
                                            note = "Add your face first (My face, below), then choose Face scan."
                                        else -> {
                                            note = ""
                                            modes[screen] = chosen
                                            LockSettings.setMode(context, screen, chosen)
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (note.isNotEmpty()) Text(note, fontSize = 12.5.sp, color = p.bad)
                    if (LockRules.settingsLeftOpen(modes.toMap(), SETTINGS_TAB)) {
                        Text("Lock Settings too. Otherwise anyone with the phone can open it and switch the locks off.", fontSize = 12.5.sp, color = p.bad)
                    }
                    Text("Phone lock: " + PhoneLock.statusText(context), fontSize = 12.sp, color = p.ink2)
                }
            }
        }
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Stay unlocked", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LockRules.STAY_OPTIONS.forEach { (minutes, label) ->
                            PaperPill(label, stay == minutes, { stay = minutes; LockSettings.setStayMinutes(context, minutes) })
                        }
                    }
                    Text(
                        "A screen stays open while you use it. It locks again this long after you leave it or the app. \"Every time\" locks it the moment you leave. " +
                            "Closing the app locks everything.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
            }
        }
        item {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("My face", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(
                        if (faceSaved) "Your face is saved on this phone, as numbers only, encrypted and out of backups."
                        else "No face saved yet. Face scan needs one. It takes a few seconds with the front camera.",
                        fontSize = 12.5.sp, color = p.ink2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperButton(if (faceSaved) "Add it again" else "Add my face", { sub = "enrol" }, small = true)
                        if (faceSaved) PaperButton("Test the scan", { sub = "test" }, style = PaperButtonStyle.Ghost, small = true)
                    }
                    if (faceSaved) {
                        PaperButton(
                            "Remove my face",
                            { FaceStore.clear(context); faceSaved = false },
                            style = PaperButtonStyle.Ghost, small = true,
                        )
                        Text("Screens set to Face scan use the phone lock until a face is saved again.", fontSize = 12.sp, color = p.ink2)
                    }
                    Text("How closely it must match", fontSize = 13.sp, color = p.ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Strictness.entries.forEach { s ->
                            PaperPill(s.label, strictness == s, { strictness = s; LockSettings.setStrictness(context, s) })
                        }
                    }
                    Text(
                        "Strict turns away more people but may also turn you away in poor light. Use \"Test the scan\" to see your own score and choose.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Turn away a photo of me", fontSize = 14.sp, color = p.ink)
                            Text("Checks the face is live, not a picture held to the camera. A video or a mask can still fool it.", fontSize = 12.sp, color = p.ink2)
                        }
                        PaperToggle(blockPhoto, { blockPhoto = it; LockSettings.setBlockPhoto(context, it) })
                    }
                    Text(
                        "After ${LockRules.MAX_FACE_ATTEMPTS} scans that do not match, the phone PIN is asked for. This is a strong casual lock, not a bank-grade one.",
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
            }
        }
    }
}

/** Saves your face: a few pictures of you, one at a time. */
@Composable
fun FaceEnrolScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    BackHandler(onBack = onBack)
    var problem by remember { mutableStateOf("") }
    val camera = rememberCameraAccess { problem = "Camera access was refused. Allow it in the phone's settings for this app." }
    val collector = remember { EnrolCollector() }
    var count by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Look at the camera. Turn your head a little between pictures.") }
    val blockPhoto = remember { LockSettings.blockPhoto(context) }
    LaunchedEffect(Unit) { if (!camera.granted) camera.ask() }
    FaceScanEffect(
        active = camera.granted && !finished && problem.isEmpty(),
        checkLive = blockPhoto,
        onError = { problem = it },
        onAnalysis = { a ->
            val embedding = a.embedding
            when {
                a.faces == 0 -> hint = "Look at the camera"
                a.faces > 1 -> hint = "Only you should be in view"
                !a.bigEnough || embedding == null -> hint = "Come a little closer"
                a.live == false -> hint = "That does not look like a live face"
                else -> when (collector.add(embedding, SystemClock.elapsedRealtime())) {
                    EnrolCollector.Outcome.ACCEPTED -> {
                        count = collector.count
                        hint = "Good. Turn your head a little."
                        if (collector.done) {
                            try {
                                FaceStore.save(context, collector.embeddings())
                                finished = true
                            } catch (e: Exception) {
                                problem = "Your face could not be saved: " + (e.message ?: e.javaClass.simpleName)
                            }
                        }
                    }
                    EnrolCollector.Outcome.DIFFERENT_FACE -> hint = "That looks like a different face. Only you, please."
                    EnrolCollector.Outcome.TOO_SOON -> {}
                }
            }
        },
    )
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsTop("Add my face", "Takes a few seconds", onBack)
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    problem.isNotEmpty() -> Text(problem, fontSize = 13.sp, color = p.bad)
                    finished -> Text("Saved. Your face is stored on this phone as numbers only.", fontSize = 14.sp, color = p.ink)
                    !camera.granted -> Text("Camera access is needed to see your face.", fontSize = 13.sp, color = p.ink2)
                    else -> {
                        Text(hint, fontSize = 15.sp, color = p.ink)
                        Text("$count of ${collector.target} pictures", fontSize = 13.sp, color = p.ink2)
                    }
                }
            }
        }
        if (finished) PaperButton("Done", onDone, Modifier.fillMaxWidth())
        else if (!camera.granted) PaperButton("Allow the camera", { camera.ask() }, Modifier.fillMaxWidth())
        Text("Hold the phone at arm's length in good light, with no one else in view. The camera is only on while this page is open.", fontSize = 12.sp, color = p.ink2)
    }
}

/** Shows what a scan sees right now, so you can check it knows you and choose the strictness. */
@Composable
fun FaceTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val p = LocalPaper.current
    BackHandler(onBack = onBack)
    var problem by remember { mutableStateOf("") }
    val camera = rememberCameraAccess { problem = "Camera access was refused." }
    val saved = remember { FaceStore.load(context) }
    val threshold = remember { LockSettings.strictness(context).threshold }
    val blockPhoto = remember { LockSettings.blockPhoto(context) }
    var last by remember { mutableStateOf<FaceEngine.Analysis?>(null) }
    LaunchedEffect(Unit) { if (!camera.granted) camera.ask() }
    FaceScanEffect(
        active = camera.granted && problem.isEmpty(),
        checkLive = blockPhoto,
        onError = { problem = it },
        onAnalysis = { last = it },
    )
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsTop("Test the scan", "What the camera sees now", onBack)
        PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val a = last
                when {
                    problem.isNotEmpty() -> Text(problem, fontSize = 13.sp, color = p.bad)
                    saved.isEmpty() -> Text("No saved face to compare with. Add your face first.", fontSize = 13.sp, color = p.ink2)
                    a == null -> Text("Starting the camera…", fontSize = 13.sp, color = p.ink2)
                    else -> {
                        val similarity = a.embedding?.let { FaceMath.bestSimilarity(saved, it) }
                        Text("Faces seen: ${a.faces}" + if (a.faces == 1 && !a.bigEnough) " (too far away)" else "", fontSize = 14.sp, color = p.ink)
                        if (similarity != null) {
                            Text("Match with your face: " + String.format(java.util.Locale.US, "%.2f", similarity) + " (needs " + String.format(java.util.Locale.US, "%.2f", threshold) + ")", fontSize = 14.sp, color = p.ink)
                        }
                        if (blockPhoto && a.live != null) {
                            Text("Live face: " + (if (a.live) "yes" else "no, looks like a picture") + " (" + String.format(java.util.Locale.US, "%.2f", a.liveScore) + ")", fontSize = 14.sp, color = p.ink)
                        }
                        val verdict = when {
                            a.faces != 1 || !a.bigEnough || similarity == null -> "Would not open: look at the camera, alone, close enough."
                            blockPhoto && a.live == false -> "Would not open: looks like a picture."
                            similarity >= threshold -> "Would open."
                            else -> "Would not open: not close enough to your face."
                        }
                        Text(verdict, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (verdict == "Would open.") p.ink else p.bad)
                        Text("${a.millis} ms for this picture", fontSize = 12.sp, color = p.ink2)
                    }
                }
            }
        }
        if (!camera.granted) PaperButton("Allow the camera", { camera.ask() }, Modifier.fillMaxWidth())
        Text(
            "Try it in the light you usually have, with and without glasses. If you are turned away too often, choose Relaxed. If you want fewer strangers let in, choose Strict.",
            fontSize = 12.sp, color = p.ink2,
        )
    }
}
