package com.lifetracker.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.FoodAiSettings
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodPhotoAi
import com.lifetracker.app.data.MealPhotos
import com.lifetracker.app.data.PhotoAiException
import com.lifetracker.app.data.PhotoFood
import com.lifetracker.app.data.atGrams
import com.lifetracker.app.data.onlyTicked
import com.lifetracker.app.data.separateItems
import com.lifetracker.app.data.toMeal
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperBackground
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperToggle
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.launch

/**
 * The page behind the "Snap a meal" button: opens the camera, has the photo read, and asks "Is this what you ate?" before anything
 * is added. You can rename it, change how much, fix any number, pick the meal, or ask again with a hint or the bigger model.
 */
@Composable
fun QuickPhotoScreen(
    onClose: () -> Unit,
    /** The meal to add to; null picks one from the time of day. */
    startMeal: String? = null,
    /** The day to add to. */
    date: LocalDate = LocalDate.now(),
    onAdded: () -> Unit = {},
) {
    val context = LocalContext.current
    val p = LocalPaper.current
    val scope = rememberCoroutineScope()

    // These survive the phone turning or the app being closed while the camera is open.
    var started by rememberSaveable { mutableStateOf(false) }
    var shot by rememberSaveable { mutableStateOf(0) }

    var showKey by remember { mutableStateOf(false) }
    var showNotice by remember { mutableStateOf(false) }
    var jpeg by remember { mutableStateOf<ByteArray?>(null) }
    var food by remember { mutableStateOf<PhotoFood?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var scale by remember { mutableFloatStateOf(1f) }
    var edits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editing by remember { mutableStateOf(false) }
    val minute = remember {
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        if (startMeal == null) now else FoodLogic.minuteFor(startMeal, date == LocalDate.now(), now)
    }
    var mealType by remember { mutableStateOf(startMeal ?: FoodLogic.mealTypeFor(minute)) }
    var ticked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var separate by remember { mutableStateOf(false) }

    fun photoUri(): Uri {
        val dir = File(context.cacheDir, "food-photos").apply { mkdirs() }
        return FileProvider.getUriForFile(context, context.packageName + ".photos", File(dir, "snap.jpg"))
    }

    fun read(strong: Boolean) {
        busy = true
        failed = false
        message = if (strong) "Asking the stronger model…" else "Reading the photo…"
        scope.launch {
            try {
                val bytes = jpeg ?: FoodPhotoAi.prepare(context, photoUri()).also { jpeg = it }
                val result = FoodPhotoAi.analyze(context, bytes, note, strong)
                food = result
                name = result.name
                scale = 1f
                edits = emptyMap()
                editing = false
                ticked = result.items.indices.toSet()
                separate = false
                message = ""
            } catch (e: PhotoAiException) {
                message = e.message.orEmpty()
                failed = true
            } catch (e: Exception) {
                message = "Something went wrong: " + (e.message ?: e.javaClass.simpleName)
                failed = true
            }
            busy = false
        }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) shot += 1
        else if (food == null && jpeg == null && !busy) onClose() // nothing taken and nothing to look at
    }

    fun proceed() {
        when {
            FoodAiSettings.key(context).isEmpty() -> showKey = true
            !FoodAiSettings.noticeSeen(context) -> showNotice = true
            else -> take.launch(photoUri())
        }
    }

    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            proceed()
        }
    }
    LaunchedEffect(shot) {
        if (shot > 0) {
            jpeg = null
            food = null
            read(false)
        }
    }

    if (showKey) GeminiKeyDialog(onSaved = { showKey = false; proceed() }, onDismiss = { showKey = false; onClose() })
    if (showNotice) PhotoNoticeDialog(onContinue = { showNotice = false; proceed() }, onDismiss = { showNotice = false; onClose() })

    val preview = remember(jpeg) {
        jpeg?.let { b ->
            BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Kicker("Snap a meal")
            PaperButton("Close", onClose, style = PaperButtonStyle.Ghost, small = true)
        }
        Text("Check this meal", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = p.ink)

        if (preview != null) {
            Image(
                bitmap = preview, contentDescription = "Your photo", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
            )
        }

        if (busy) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Text(message, fontSize = 14.sp, color = p.ink2)
            }
        } else if (failed) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(message, fontSize = 14.sp, color = p.bad)
                    PaperButton("Try again", { read(false) }, Modifier.fillMaxWidth(), enabled = shot > 0)
                    PaperButton("Take another photo", { proceed() }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                }
            }
        }

        val f = food
        if (f != null && !busy) {
            val unit = if (f.drink) "ml" else "g"
            val grams = f.servingGrams * scale
            val shown = f.atGrams(grams)
            val part = shown.onlyTicked(ticked)
            val split = f.canSplit

            fun number(key: String, auto: Double): Double = edits[key]?.let { foodParse(it) } ?: auto

            Text("Is this what you ate?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.ink)
            PaperField(name, { name = it }, "What it is", singleLine = true)
            if (split) {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Kicker("On the plate")
                        shown.items.forEachIndexed { i, item ->
                            val on = i in ticked
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { ticked = if (on) ticked - i else ticked + i },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = on, onCheckedChange = { ticked = if (it) ticked + i else ticked - i },
                                    colors = CheckboxDefaults.colors(checkedColor = p.ink, checkmarkColor = p.surface, uncheckedColor = p.ink2),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
                                    Text(
                                        (if (item.grams > 0) Math.round(item.grams).toString() + " $unit · " else "") + Math.round(item.kcal) + " kcal",
                                        fontSize = 12.sp, color = p.ink2,
                                    )
                                }
                            }
                        }
                        Text("Untick anything you did not eat.", fontSize = 12.sp, color = p.ink2)
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Log each food on its own line", fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f))
                            PaperToggle(separate, { separate = it; if (it) { editing = false } })
                        }
                    }
                }
            } else if (f.ingredients.isNotEmpty()) {
                Text("Seen: " + f.ingredients.joinToString(", "), fontSize = 12.5.sp, color = p.ink2)
            }

            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Kicker("How much")
                    Text("${Math.round(grams)} $unit", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(
                        "The photo reader guessed ${Math.round(f.servingGrams)} $unit." +
                            (if (f.weightGuessed) " It gave no weight, so this is only a placeholder." else "") +
                            " Slide to match your plate.",
                        fontSize = 12.5.sp, color = p.ink2,
                    )
                    Slider(
                        value = scale,
                        onValueChange = { scale = it; edits = emptyMap() },
                        valueRange = 0.25f..3f,
                        colors = SliderDefaults.colors(thumbColor = p.ink, activeTrackColor = p.ink, inactiveTrackColor = p.surface2),
                    )
                }
            }

            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kicker("For this much")
                    Row(Modifier.fillMaxWidth()) {
                        CheckStat("kcal", Math.round(number("kcal", part.kcal)).toString(), Modifier.weight(1f))
                        CheckStat("protein g", Math.round(number("protein", part.protein)).toString(), Modifier.weight(1f))
                        CheckStat("carbs g", Math.round(number("carbs", part.carbs)).toString(), Modifier.weight(1f))
                        CheckStat("fat g", Math.round(number("fat", part.fat)).toString(), Modifier.weight(1f))
                    }
                    if (editing && !(split && separate)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaperField(
                                edits["kcal"] ?: foodNum(part.kcal), { edits = edits + ("kcal" to it) }, "Calories",
                                Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys,
                            )
                            PaperField(
                                edits["protein"] ?: foodNum(part.protein), { edits = edits + ("protein" to it) }, "Protein g",
                                Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaperField(
                                edits["carbs"] ?: foodNum(part.carbs), { edits = edits + ("carbs" to it) }, "Carbs g",
                                Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys,
                            )
                            PaperField(
                                edits["fat"] ?: foodNum(part.fat), { edits = edits + ("fat" to it) }, "Fat g",
                                Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys,
                            )
                        }
                        Text("Moving the slider puts the numbers back to what the photo reader said.", fontSize = 12.sp, color = p.ink2)
                    }
                    if (!(split && separate)) {
                        PaperButton(
                            if (editing) "Done" else "Fix the numbers", { editing = !editing },
                            style = PaperButtonStyle.Ghost, small = true,
                        )
                    }
                }
            }

            Kicker("Add it to")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf(FoodLogic.BREAKFAST, FoodLogic.LUNCH), listOf(FoodLogic.DINNER, FoodLogic.SNACK)).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { type ->
                            PaperPill(type.replaceFirstChar { it.uppercase() }, mealType == type, { mealType = type }, Modifier.weight(1f))
                        }
                    }
                }
            }

            PaperButton(
                "Yes, add this",
                {
                    val day = date.toString()
                    val meals = if (split && separate) {
                        shown.separateItems(ticked).map { it.toMeal(it.name, mealType, day, minute) }
                    } else {
                        val adjusted = part.copy(
                            kcal = number("kcal", part.kcal), protein = number("protein", part.protein),
                            carbs = number("carbs", part.carbs), fat = number("fat", part.fat),
                        )
                        listOf(adjusted.toMeal(name, mealType, day, minute))
                    }
                    val photo = jpeg
                    scope.launch {
                        val dao = AppDatabase.get(context).foodDao()
                        for (meal in meals) {
                            dao.upsertMeal(meal)
                            if (photo != null) MealPhotos.save(context, meal.id, photo)
                        }
                        val total = meals.sumOf { it.kcal }
                        Toast.makeText(context, "Added " + Math.round(total) + " kcal to " + mealType, Toast.LENGTH_SHORT).show()
                        onAdded()
                        onClose()
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = !split || ticked.isNotEmpty(),
            )

            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kicker("Not right?")
                    PaperField(note, { note = it }, "Tell it what it is (optional)", placeholder = "e.g. 2 rotis, 1 katori dal", singleLine = true)
                    PaperButton("Read again", { read(false) }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                    PaperButton("Think harder", { read(true) }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                    PaperButton("Take another photo", { proceed() }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                }
            }
            Text("This is a guess made from a photo, so check the numbers.", fontSize = 12.sp, color = p.ink2)
        }
    }
}

/** The same page as a window on top of the Add food window, adding to that window's meal and day. */
@Composable
fun PhotoCheckWindow(startMeal: String, date: LocalDate, onAdded: () -> Unit, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PaperBackground {
            QuickPhotoScreen(onClose = onClose, startMeal = startMeal, date = date, onAdded = onAdded)
        }
    }
}

@Composable
private fun CheckStat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    Column(modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink)
        Text(label, fontSize = 12.sp, color = p.ink2)
    }
}
