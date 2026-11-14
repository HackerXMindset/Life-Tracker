package com.lifetracker.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.collectAsState
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
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.FoodEntity
import com.lifetracker.app.data.FoodNutrientEntity
import com.lifetracker.app.data.FoodAiMemory
import com.lifetracker.app.data.OpenFoodFacts
import com.lifetracker.app.data.PhotoQueue
import com.lifetracker.app.data.PhotoQueueNotifier
import com.lifetracker.app.data.QueuedPhoto
import com.lifetracker.app.data.PortionKind
import com.lifetracker.app.data.CompareResult
import com.lifetracker.app.data.CompareSettings
import com.lifetracker.app.data.FoodAiSettings
import com.lifetracker.app.data.FoodSanity
import com.lifetracker.app.data.FoodSource
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodPhotoAi
import com.lifetracker.app.data.MealPhotos
import com.lifetracker.app.data.PhotoAiException
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.PhotoFood
import com.lifetracker.app.data.RepeatMatch
import com.lifetracker.app.data.withNumbersOf
import com.lifetracker.app.data.atGrams
import com.lifetracker.app.data.onlyTicked
import com.lifetracker.app.data.separateItems
import com.lifetracker.app.data.withCookingFat
import com.lifetracker.app.data.toMeal
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperBackground
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperToggle
import com.lifetracker.app.ui.theme.Wobble
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the food on the check page comes from. */
sealed interface CheckSource {
    /** A photo of the plate. */
    object Meal : CheckSource

    /** A photo of the nutrition table on a pack. */
    object Label : CheckSource

    /** A barcode, looked up on Open Food Facts. */
    object Barcode : CheckSource

    /** A meal that was typed or spoken. */
    class Text(val text: String) : CheckSource
}

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
    /** What to read: a photo of the plate unless told otherwise. */
    initialSource: CheckSource = CheckSource.Meal,
    /** A photo that was taken without internet and is being read now. */
    queued: QueuedPhoto? = null,
    /** Choose the photo from the gallery instead of taking one. */
    fromGallery: Boolean = false,
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
    // The answers of every model on the compare list, when comparing is on, and the model whose answer was chosen.
    var options by remember { mutableStateOf<List<CompareResult>?>(null) }
    var chosenModel by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var readToken by remember { mutableStateOf(0) }
    // A meal you logged before that is the same dish as this reading, and whether you chose to use its numbers.
    var before by remember { mutableStateOf<MealEntity?>(null) }
    var useBefore by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(queued?.note.orEmpty()) }
    // The optional name typed before a meal photo is sent, and whether that question is on screen.
    var dishName by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var edits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editing by remember { mutableStateOf(false) }
    // The vitamins, minerals and nutrients of your own that can be added to the numbers under "Fix the numbers".
    val nutrientList by remember { AppDatabase.get(context).foodDao().observeNutrients() }.collectAsState(emptyList())
    val minute = remember {
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        if (queued != null) queued.minute
        else if (startMeal == null) now else FoodLogic.minuteFor(startMeal, date == LocalDate.now(), now)
    }
    var mealType by remember { mutableStateOf(startMeal ?: FoodLogic.mealTypeFor(minute)) }
    var ticked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var separate by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(initialSource) }
    var code by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf(false) }
    var offlineFail by remember { mutableStateOf(false) }
    var fromQueue by remember { mutableStateOf(queued != null) }

    // The photo picked from the gallery; the camera writes to a file of ours instead.
    var picked by remember { mutableStateOf<Uri?>(null) }

    fun photoUri(): Uri {
        picked?.let { return it }
        val dir = File(context.cacheDir, "food-photos").apply { mkdirs() }
        return FileProvider.getUriForFile(context, context.packageName + ".photos", File(dir, "snap.jpg"))
    }

    fun cameraUri(): Uri {
        val dir = File(context.cacheDir, "food-photos").apply { mkdirs() }
        return FileProvider.getUriForFile(context, context.packageName + ".photos", File(dir, "snap.jpg"))
    }

    /** What is sent along with the picture: the name given up front, then anything typed under "Not right?". */
    fun hint(): String = listOf(
        if (dishName.isNotBlank()) "The user says this dish is called \"${dishName.trim()}\". Use that as its name." else "",
        note.trim(),
    ).filter { it.isNotEmpty() }.joinToString(" ")

    fun accept(result: PhotoFood, model: String?) {
        food = result
        chosenModel = model
        readToken += 1
        name = dishName.trim().ifBlank { result.name }
        scale = 1f
        edits = emptyMap()
        editing = false
        ticked = result.items.indices.toSet()
        separate = false
        keep = source == CheckSource.Barcode || source == CheckSource.Label
        message = ""
    }

    fun read(strong: Boolean) {
        naming = false
        busy = true
        failed = false
        offlineFail = false
        options = null
        val comparing = source != CheckSource.Barcode && CompareSettings.active(context)
        message = when {
            comparing -> "Asking " + CompareSettings.models(context).size + " models…"
            source == CheckSource.Barcode -> "Looking the barcode up…"
            source is CheckSource.Text -> if (strong) "Asking the stronger model…" else "Reading what you wrote…"
            else -> if (strong) "Asking the stronger model…" else "Reading the photo…"
        }
        scope.launch {
            try {
                if (comparing) {
                    val s = source
                    val bytes = if (s is CheckSource.Text) null else (jpeg ?: FoodPhotoAi.prepare(context, photoUri()).also { jpeg = it })
                    val results = FoodPhotoAi.compare(
                        context, bytes,
                        when {
                            s is CheckSource.Text -> FoodPhotoAi.Read.TEXT
                            s == CheckSource.Label -> FoodPhotoAi.Read.LABEL
                            else -> FoodPhotoAi.Read.MEAL
                        },
                        hint(), (s as? CheckSource.Text)?.text.orEmpty(),
                    )
                    if (results.none { it.food != null }) {
                        throw PhotoAiException(
                            "No model could read it.\n" + results.joinToString("\n") { it.entry.model + ": " + it.error.orEmpty() },
                            offline = results.all { it.offline },
                        )
                    }
                    food = null
                    options = results
                    message = ""
                } else {
                    val result = when (val s = source) {
                        CheckSource.Barcode -> OpenFoodFacts.lookup(code)
                            ?: throw PhotoAiException("Open Food Facts has no numbers for this barcode. Photograph the nutrition label instead.")
                        is CheckSource.Text -> FoodPhotoAi.analyzeText(context, s.text, hint(), strong)
                        else -> {
                            val bytes = jpeg ?: FoodPhotoAi.prepare(context, photoUri()).also { jpeg = it }
                            if (s == CheckSource.Label) FoodPhotoAi.analyzeLabel(context, bytes, hint(), strong)
                            else FoodPhotoAi.analyze(context, bytes, hint(), strong)
                        }
                    }
                    accept(result, null)
                }
            } catch (e: PhotoAiException) {
                message = e.message.orEmpty()
                failed = true
                offlineFail = e.offline
            } catch (e: Exception) {
                message = "Something went wrong: " + (e.message ?: e.javaClass.simpleName)
                failed = true
            }
            busy = false
        }
    }

    fun scan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { barcode ->
                code = barcode.rawValue.orEmpty()
                if (code.isBlank()) {
                    message = "The barcode could not be read. Scan again, or type the number."
                    failed = true
                } else read(false)
            }
            .addOnCanceledListener { if (food == null && !busy && code.isBlank()) onClose() }
            .addOnFailureListener {
                message = "The barcode scanner is not available on this phone (it needs Google Play services). Type the number under the barcode instead."
                failed = true
            }
    }

    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) shot += 1
        else if (food == null && jpeg == null && !busy) onClose() // nothing taken and nothing to look at
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            picked = uri
            shot += 1
        } else if (food == null && jpeg == null && !busy) onClose()
    }

    fun proceed() {
        when {
            source == CheckSource.Barcode -> scan()
            !CompareSettings.keyReady(context) -> showKey = true
            !CompareSettings.noticeSeen(context) -> showNotice = true
            source is CheckSource.Text -> read(false)
            fromQueue && jpeg != null -> { fromQueue = false; read(false) }
            fromGallery -> { picked = null; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            else -> take.launch(cameraUri())
        }
    }

    LaunchedEffect(readToken) {
        before = null
        useBefore = false
        val read = food
        if (readToken > 0 && read != null && (source == CheckSource.Meal || source is CheckSource.Text)) {
            val today = LocalDate.now()
            val past = withContext(Dispatchers.IO) {
                AppDatabase.get(context).foodDao().mealsBetweenOnce(today.minusDays(90).toString(), today.toString())
            }
            before = RepeatMatch.find(read.name, past)
        }
    }

    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            if (queued != null) {
                jpeg = withContext(Dispatchers.IO) { PhotoQueue.jpeg(context, queued.id) }
                if (jpeg == null) {
                    message = "The saved photo is gone. Take the photo again."
                    failed = true
                    PhotoQueue.remove(context, queued.id)
                    PhotoQueueNotifier.refresh(context)
                    return@LaunchedEffect
                }
            }
            proceed()
        }
    }
    LaunchedEffect(shot) {
        if (shot > 0) {
            jpeg = null
            food = null
            options = null
            failed = false
            if (source == CheckSource.Meal) {
                // A plate photo waits for the question "Do you want to name it?" before anything is sent.
                busy = true
                message = "Getting the photo ready…"
                try {
                    jpeg = FoodPhotoAi.prepare(context, photoUri())
                    naming = true
                } catch (e: PhotoAiException) {
                    message = e.message.orEmpty()
                    failed = true
                } catch (e: Exception) {
                    message = "Something went wrong: " + (e.message ?: e.javaClass.simpleName)
                    failed = true
                }
                busy = false
            } else read(false)
        }
    }

    if (showKey) AiKeyDialog(onSaved = { showKey = false; proceed() }, onDismiss = { showKey = false; onClose() })
    if (showNotice) PhotoNoticeDialog(onContinue = { showNotice = false; proceed() }, onDismiss = { showNotice = false; onClose() })

    val animOn = rememberReadingAnimation()
    val phase = rememberReadPhase(busy, failed)
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
            Kicker(
                when (source) {
                    CheckSource.Barcode -> "Barcode"
                    CheckSource.Label -> "Nutrition label"
                    is CheckSource.Text -> "Your description"
                    else -> "Snap a meal"
                },
            )
            PaperButton("Close", onClose, style = PaperButtonStyle.Ghost, small = true)
        }
        Text(
            if (source == CheckSource.Barcode || source == CheckSource.Label) "Check this food" else "Check this meal",
            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = p.ink,
        )

        if (preview != null) {
            ReadingStage(phase, animOn, Modifier.fillMaxWidth()) {
                Image(
                    bitmap = preview, contentDescription = "Your photo", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
        } else if (animOn && phase != ReadPhase.Idle) {
            // Typed words and barcodes have no picture, so the same animation plays on an empty paper card.
            ReadingStage(phase, true, Modifier.fillMaxWidth().height(120.dp)) {
                Box(Modifier.matchParentSize().background(p.surface2, Wobble.Card))
            }
        }

        if (naming && !busy && food == null) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Do you want to name this dish?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("A name helps it get the food and the amount right. You can skip it.", fontSize = 12.sp, color = p.ink2)
                    PaperField(dishName, { dishName = it }, "Dish name", placeholder = "e.g. bhindi sabzi with roti", singleLine = true)
                    PaperButton("Send with this name", { read(false) }, Modifier.fillMaxWidth(), enabled = dishName.isNotBlank())
                    PaperButton("Skip, just read the photo", { dishName = ""; read(false) }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                    PaperButton("Take another photo", { proceed() }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                }
            }
        } else if (busy) {
            if (!animOn) {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Text(message, fontSize = 14.sp, color = p.ink2)
                }
            }
        } else if (failed) {
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(message, fontSize = 14.sp, color = p.bad)
                    when (source) {
                        CheckSource.Barcode -> {
                            PaperField(code, { code = it }, "Barcode number", singleLine = true, keyboardOptions = DecimalKeys)
                            PaperButton("Look it up", { read(false) }, Modifier.fillMaxWidth(), enabled = code.any { it.isDigit() })
                            PaperButton("Scan again", { scan() }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                            PaperButton(
                                "Photograph the label instead",
                                { source = CheckSource.Label; failed = false; jpeg = null; food = null; shot = 0; proceed() },
                                Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost,
                            )
                        }
                        is CheckSource.Text -> PaperButton("Try again", { read(false) }, Modifier.fillMaxWidth())
                        else -> {
                            PaperButton("Try again", { read(false) }, Modifier.fillMaxWidth(), enabled = jpeg != null)
                            if (offlineFail && jpeg != null) {
                                PaperButton(
                                    "Read it when I am online",
                                    {
                                        val bytes = jpeg
                                        scope.launch {
                                            if (bytes != null && queued == null) {
                                                withContext(Dispatchers.IO) {
                                                    PhotoQueue.add(
                                                        context, bytes,
                                                        QueuedPhoto(QueuedPhoto.newId(), source == CheckSource.Label, mealType, date.toString(), minute, hint()),
                                                    )
                                                }
                                            }
                                            PhotoQueue.schedule(context)
                                            Toast.makeText(context, "Saved. A notification will say when it can be read.", Toast.LENGTH_LONG).show()
                                            onClose()
                                        }
                                    },
                                    Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost,
                                )
                            }
                            PaperButton("Take another photo", { proceed() }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                        }
                    }
                }
            }
        }

        val choices = options
        if (choices != null && food == null && !busy && !failed) {
            CompareCards(choices, onPick = { r -> r.food?.let { accept(it, r.entry.model) } })
            PaperButton("Ask them again", { read(false) }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
        }

        val read = food
        val mine = before
        val f = if (read != null && mine != null && useBefore) read.withNumbersOf(mine) else read
        if (f != null && !busy) {
            val unit = if (f.drink) "ml" else "g"
            // The count-up and the marker swipes play once, for an answer that just came in, not when the page is reopened on an old one.
            val play = animOn && readToken > 0
            val grams = f.servingGrams * scale
            val shown = f.atGrams(grams)
            // Cooking oil, ghee or butter: what you set here replaces what the reader counted, 9 kcal a gram.
            val oilSet = edits["oil"]?.let { foodParse(it) }
            val part = shown.onlyTicked(ticked).let { base -> if (oilSet != null) base.withCookingFat(oilSet) else base }
            val split = f.canSplit

            fun number(key: String, auto: Double): Double = edits[key]?.let { foodParse(it) } ?: auto

            if (options != null) {
                PaperButton("See what the other models said", { food = null }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost, small = true)
            }
            Text("Is this what you ate?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = p.ink)
            PaperField(name, { name = it }, "What it is", singleLine = true)
            if (read != null && mine != null) {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Kicker("You have had this before")
                        val day = runCatching { LocalDate.parse(mine.date).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)) }.getOrDefault(mine.date)
                        Text(
                            "You logged \"${mine.name}\" on $day, at about ${Math.round(mine.kcal100)} kcal per 100 g. The same dish with the same numbers every time " +
                                "is more reliable than a fresh guess, and it keeps the weight the reader estimated for today.",
                            fontSize = 12.5.sp, color = p.ink2,
                        )
                        if (useBefore) {
                            PaperButton("Back to the reader's numbers", { useBefore = false; edits = emptyMap() }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost, small = true)
                        } else {
                            PaperButton("Use my numbers", { useBefore = true; edits = emptyMap() }, Modifier.fillMaxWidth(), small = true)
                        }
                    }
                }
            }
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
                                    Text(item.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1, modifier = Modifier.markerSwipe(readToken, play, i * 160))
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
                        CheckStat("kcal", number("kcal", part.kcal), readToken, play, Modifier.weight(1f))
                        CheckStat("protein g", number("protein", part.protein), readToken, play, Modifier.weight(1f))
                        CheckStat("carbs g", number("carbs", part.carbs), readToken, play, Modifier.weight(1f))
                        CheckStat("fat g", number("fat", part.fat), readToken, play, Modifier.weight(1f))
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
                        Kicker("More numbers for this much")
                        val extras = listOf(
                            Triple("fiber", "Fibre g", part.fiber), Triple("sugar", "Sugar g", part.sugar),
                            Triple("sodium", "Sodium mg", part.sodiumMg), Triple("sat", "Saturated fat g", part.sat),
                            Triple("mono", "Mono fat g", part.mono), Triple("poly", "Poly fat g", part.poly),
                        )
                        extras.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (key, label, auto) ->
                                    PaperField(
                                        edits[key] ?: foodNum(auto), { edits = edits + (key to it) }, label,
                                        Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys,
                                    )
                                }
                            }
                        }
                        val visible = nutrientList.filter { !it.hidden }
                        if (visible.isNotEmpty()) {
                            Kicker("Vitamins, minerals and your own")
                            visible.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { n ->
                                        val key = "n:" + n.id
                                        PaperField(
                                            edits[key] ?: foodNum(part.micro[n.id] ?: 0.0), { edits = edits + (key to it) },
                                            n.name + " " + n.unit, Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys,
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Text("Moving the slider puts the numbers back to what the photo reader said.", fontSize = 12.sp, color = p.ink2)
                    } else if (!(split && separate)) {
                        // The other numbers sit in the same tiles as the main four. Tap any of them to change it.
                        val shownExtras = listOfNotNull(
                            number("fiber", part.fiber).takeIf { it > 0 }?.let { "fibre g" to it },
                            number("sugar", part.sugar).takeIf { it > 0 }?.let { "sugar g" to it },
                            number("sodium", part.sodiumMg).takeIf { it > 0 }?.let { "sodium mg" to it },
                            number("sat", part.sat).takeIf { it > 0 }?.let { "saturated fat g" to it },
                            number("mono", part.mono).takeIf { it > 0 }?.let { "mono fat g" to it },
                            number("poly", part.poly).takeIf { it > 0 }?.let { "poly fat g" to it },
                        ) + nutrientList.filter { !it.hidden }.mapNotNull { n ->
                            number("n:" + n.id, part.micro[n.id] ?: 0.0).takeIf { it > 0 }?.let { (n.name + " " + n.unit).lowercase() to it }
                        }
                        shownExtras.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { (label, value) ->
                                    CheckStat(label, value, readToken, false, Modifier.weight(1f).clickable { editing = true })
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (shownExtras.isNotEmpty()) Text("Tap a number to change it, or use Fix the numbers to add one.", fontSize = 11.5.sp, color = p.ink2)
                    }
                    if (!(split && separate)) {
                        SanityNotes(FoodSanity.check(part.servingGrams, part.kcal, part.protein, part.carbs, part.fat))
                        PaperButton(
                            if (editing) "Done" else "Fix the numbers", { editing = !editing },
                            style = PaperButtonStyle.Ghost, small = true,
                        )
                    }
                }
            }

            if (!(split && separate) && !(useBefore && mine != null) && (source == CheckSource.Meal || source is CheckSource.Text)) {
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Kicker("Oil, ghee or butter")
                        Text(
                            "About ${Math.round(part.cookingFatG)} g is counted in the numbers above, at 9 kcal a gram (about ${Math.round(part.cookingFatG * 9)} kcal). " +
                                "Restaurant and delivery food usually has more than home cooking, so change it if the food looks oily.",
                            fontSize = 12.5.sp, color = p.ink2,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 5, 10, 20, 30).forEach { v ->
                                PaperPill("$v g", Math.round(part.cookingFatG).toInt() == v, { edits = edits + ("oil" to v.toString()) }, Modifier.weight(1f))
                            }
                        }
                        PaperField(
                            edits["oil"] ?: foodNum(part.cookingFatG), { edits = edits + ("oil" to it) }, "Grams of oil, ghee or butter",
                            singleLine = true, keyboardOptions = DecimalKeys,
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

            if (!(split && separate)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Also keep it as one of my foods", fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f))
                    PaperToggle(keep, { keep = it })
                }
            }

            PaperButton(
                "Yes, add this",
                {
                    val day = date.toString()
                    var keptFood: PhotoFood? = null
                    val meals = if (split && separate) {
                        shown.separateItems(ticked).map { it.toMeal(it.name, mealType, day, minute) }
                    } else {
                        val adjusted = part.copy(
                            kcal = number("kcal", part.kcal), protein = number("protein", part.protein),
                            carbs = number("carbs", part.carbs), fat = number("fat", part.fat),
                            fiber = number("fiber", part.fiber), sugar = number("sugar", part.sugar),
                            sodiumMg = number("sodium", part.sodiumMg), sat = number("sat", part.sat),
                            mono = number("mono", part.mono), poly = number("poly", part.poly),
                            micro = part.micro + nutrientList.mapNotNull { n ->
                                edits["n:" + n.id]?.let { foodParse(it) }?.let { n.id to it.coerceAtLeast(0.0) }
                            },
                        )
                        keptFood = if (keep) adjusted else null
                        listOf(adjusted.toMeal(name, mealType, day, minute))
                    }
                    val photo = jpeg
                    val kept = keptFood
                    val tag = FoodSource.tag(
                        when (source) {
                            CheckSource.Label -> FoodSource.LABEL
                            CheckSource.Barcode -> FoodSource.BARCODE
                            is CheckSource.Text -> FoodSource.TEXT
                            else -> FoodSource.PHOTO
                        },
                        edits.isNotEmpty(),
                        chosenModel,
                    )
                    scope.launch {
                        val dao = AppDatabase.get(context).foodDao()
                        // Keeping it as a food of your own means the next time it is one tap from Add food, and the meal points to it.
                        val keptId = if (kept != null) {
                            val per = kept.per100()
                            val entity = FoodEntity(
                                id = FoodLogic.newFoodId(), name = name.trim().ifEmpty { "Food" },
                                kcal100 = per.kcal, protein100 = per.protein, carbs100 = per.carbs, fat100 = per.fat,
                                fiber100 = per.fiber, sugar100 = per.sugar, sodium100 = per.sodiumMg,
                                sat100 = per.sat, mono100 = per.mono, poly100 = per.poly,
                                animal100 = 0.0, amino = "", liquid = kept.drink,
                                portionKind = PortionKind.Serving.key, portionGrams = kept.servingGrams,
                                category = "", cookedYield = 0.0, createdMs = System.currentTimeMillis(),
                            )
                            dao.upsertFood(entity)
                            dao.upsertFoodNutrients(per.micro.filter { it.value > 0 }.map { FoodNutrientEntity(entity.id, it.key, it.value) })
                            entity.id
                        } else null
                        for (m in meals) {
                            val meal = (if (keptId != null) m.copy(foodId = keptId) else m).copy(source = tag)
                            dao.upsertMeal(meal)
                            if (photo != null) MealPhotos.save(context, meal.id, photo)
                        }
                        // Remember a rename, so later prompts can warn the reader about foods it mixes up.
                        if ((source == CheckSource.Meal || source is CheckSource.Text) && !(split && separate)) {
                            FoodAiMemory.record(context, f.name, name)
                        }
                        if (queued != null) {
                            PhotoQueue.remove(context, queued.id)
                            PhotoQueueNotifier.refresh(context)
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
                    when (source) {
                        CheckSource.Barcode -> {
                            PaperButton("Scan again", { food = null; scan() }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                            PaperButton(
                                "Read the label instead",
                                { source = CheckSource.Label; jpeg = null; food = null; shot = 0; proceed() },
                                Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost,
                            )
                        }
                        else -> {
                            PaperField(
                                note, { note = it }, "Tell it more (optional)",
                                placeholder = if (source is CheckSource.Text) "e.g. the roti were large" else "e.g. 2 rotis, 1 katori dal",
                                singleLine = true,
                            )
                            PaperButton("Read again", { read(false) }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                            if (!CompareSettings.active(context)) {
                                PaperButton("Think harder", { read(true) }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                            }
                            if (source !is CheckSource.Text) {
                                PaperButton("Take another photo", { proceed() }, Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost)
                            }
                        }
                    }
                }
            }
            Text(
                when (source) {
                    CheckSource.Barcode -> "From Open Food Facts, where people type in the numbers, so check them against the pack."
                    CheckSource.Label -> "Read from the label by the AI, so check the numbers against the pack."
                    is CheckSource.Text -> "This is a guess made from your words, so check the numbers."
                    else -> "This is a guess made from a photo, so check the numbers."
                },
                fontSize = 12.sp, color = p.ink2,
            )
        }
    }
}

/** The same page as a window on top of the Add food window, adding to that window's meal and day. */
@Composable
fun PhotoCheckWindow(
    startMeal: String, date: LocalDate, source: CheckSource, onAdded: () -> Unit, onClose: () -> Unit, fromGallery: Boolean = false,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        PaperBackground {
            QuickPhotoScreen(onClose = onClose, startMeal = startMeal, date = date, onAdded = onAdded, initialSource = source, fromGallery = fromGallery)
        }
    }
}

@Composable
private fun CheckStat(label: String, value: Double, token: Int, play: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    Column(modifier) {
        CountUpText(value, token, play, 20.sp, FontWeight.Bold, p.ink)
        Text(label, fontSize = 12.sp, color = p.ink2)
    }
}
