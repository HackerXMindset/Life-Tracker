package com.lifetracker.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.R
import com.lifetracker.app.data.FoodCat
import com.lifetracker.app.data.FoodLibraryData
import com.lifetracker.app.data.FoodLogic
import com.lifetracker.app.data.FoodRef
import com.lifetracker.app.data.FoodStats
import com.lifetracker.app.data.LibraryFood
import com.lifetracker.app.data.Loggable
import com.lifetracker.app.data.MealEntity
import com.lifetracker.app.data.NutrientEntity
import com.lifetracker.app.data.NutrientMath
import com.lifetracker.app.data.NutrientValues
import com.lifetracker.app.data.ReadyDish
import com.lifetracker.app.data.SavedMealEntity
import com.lifetracker.app.data.SavedMealItems
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperDialog
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperIconButton
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.PaperSheet
import com.lifetracker.app.ui.theme.PaperToggle
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

internal fun kc(v: Double): String = Math.round(v).toString()

internal val CARB_COLOR = Color(0xFFE0A100)
internal val PROTEIN_COLOR = Color(0xFF5B5FC7)
internal val FAT_COLOR = Color(0xFFD6457F)

internal fun pickDay(context: Context, start: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(
        context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) },
        start.year, start.monthValue - 1, start.dayOfMonth,
    ).show()
}

internal fun minuteNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

@Composable
internal fun SheetBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun SheetTitle(text: String, sub: String? = null) {
    val p = LocalPaper.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 2)
        if (!sub.isNullOrBlank()) Text(sub, fontSize = 12.sp, color = p.ink2)
    }
}

@Composable
internal fun MealPills(selected: String, onSelect: (String) -> Unit) {
    ChipFlow {
        MEAL_TYPES.forEach { t -> PaperPill(mealTypeLabel(t), selected == t, { onSelect(t) }, dot = mealColor(t)) }
    }
}

@Composable
internal fun TimeButton(minute: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    PaperButton(
        text = formatMinute(minute),
        onClick = { TimePickerDialog(context, { _, h, m -> onChange(h * 60 + m) }, minute / 60, minute % 60, false).show() },
        modifier = modifier,
        style = PaperButtonStyle.Ghost,
        small = true,
    )
}

@Composable
internal fun MacroCell(label: String, grams: Double, color: Color, modifier: Modifier = Modifier) {
    val p = LocalPaper.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(kc(grams) + " g", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(label, fontSize = 12.sp, color = p.ink2)
        }
    }
}

/** Vitamin and mineral lines for amounts already worked out for what was eaten. */
@Composable
private fun VitaminLines(nutrients: List<NutrientEntity>, amounts: Map<String, Double>) {
    val p = LocalPaper.current
    val rows = nutrients.filter { !it.hidden && (amounts[it.id] ?: 0.0) > 0.0 }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { n ->
            val v = amounts[n.id] ?: 0.0
            val share = NutrientMath.fraction(v, n.target)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(n.name, fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text(
                    foodNum(v) + " " + n.unit + if (share != null) "  (" + kc(share * 100) + "%)" else "",
                    fontSize = 12.sp, color = p.ink2,
                )
            }
        }
    }
}

// ---- the add-food window ----

private enum class Shelf { All, Dishes, Mine, Stars }

/** A food offered on the front of Add food with the amount one tap would add. */
private class Pick(val food: Loggable, val grams: Double)

/** What the "Added" bar is about, so it can take the meal back or reopen it with the amount to change. */
private class JustAdded(val id: String, val food: Loggable, val grams: Double, val meal: String)

private class Cell(
    val key: String,
    val name: String,
    val sub: String,
    val photos: List<Pair<String?, String>>,
    val starred: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun FoodCell(cell: Cell, modifier: Modifier) {
    val p = LocalPaper.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = cell.onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            if (cell.photos.size > 1) FoodPlate(cell.photos, 34.dp) else FoodPhoto(cell.photos.firstOrNull()?.first, cell.name, 64.dp)
            if (cell.starred) PaperIcon(R.drawable.ic_p_star_fill, size = 14.dp, tint = p.sun)
        }
        Text(cell.name, fontSize = 12.sp, color = p.ink, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 14.sp)
        Text(cell.sub, fontSize = 11.sp, color = p.ink2, maxLines = 1)
    }
}

@Composable
private fun FoodGrid(cells: List<Cell>) {
    cells.chunked(3).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { FoodCell(it, Modifier.weight(1f)) }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
fun AddFoodWindow(vm: FoodViewModel, startMeal: String, focus: Boolean, onClose: () -> Unit) {
    val p = LocalPaper.current
    val library by vm.library.collectAsState()
    val own by vm.ownFoods.collectAsState()
    val favs by vm.favourites.collectAsState()
    val rawRecent by vm.recentMeals.collectAsState()

    var query by remember { mutableStateOf("") }
    var shelf by remember { mutableStateOf(Shelf.All) }
    var cat by remember { mutableStateOf<FoodCat?>(null) }
    var limit by remember(query, shelf, cat) { mutableIntStateOf(60) }
    var logging by remember { mutableStateOf<Loggable?>(null) }
    var dish by remember { mutableStateOf<ReadyDish?>(null) }
    var creating by remember { mutableStateOf(false) }
    var added by remember { mutableIntStateOf(0) }
    var check by remember { mutableStateOf<CheckSource?>(null) }
    var browse by remember { mutableStateOf(false) }
    var quick by remember { mutableStateOf(false) }
    var just by remember { mutableStateOf<JustAdded?>(null) }
    var loggingGrams by remember { mutableStateOf<Double?>(null) }
    // The amount you ate last time for each food, so adding it again starts from that.
    val lastGrams = remember(rawRecent) { rawRecent.filter { it.foodId.isNotEmpty() }.distinctBy { it.foodId }.associate { it.foodId to it.grams } }
    // Starred and recently eaten foods come first among equally good matches.
    val boost = remember(rawRecent, favs) { favs.map { it.foodId }.toSet() + rawRecent.take(80).map { it.foodId }.filter { it.isNotEmpty() } }
    LaunchedEffect(just) {
        if (just != null) {
            delay(7000)
            just = null
        }
    }
    val speak = rememberSpeak { query = it }
    val day by vm.date.collectAsState()
    val focusRequester = remember { FocusRequester() }
    // The screen always has the same parts, top to bottom: search, ways to add, shelves, foods. "Browse all foods" only makes the list longer.

    val lib = library
    val starred = favs.map { it.foodId }.toSet()

    fun addNow(food: Loggable, grams: Double) {
        val minute = FoodLogic.minuteFor(startMeal, day == LocalDate.now(), minuteNow())
        val id = FoodLogic.newMealId()
        vm.logFood(food, grams, startMeal, day, minute, id)
        added += 1
        just = JustAdded(id, food, grams, startMeal)
    }

    fun libCell(f: LibraryFood, lib: FoodLibraryData) = Cell(
        key = f.ref, name = f.name, sub = kc(f.per100.kcal) + " kcal", photos = listOf(f.photoAsset to f.name),
        starred = f.ref in starred, onClick = { logging = FoodLogic.of(f, lib.microOf(f.id)) },
    )

    fun ownCell(e: com.lifetracker.app.data.FoodEntity) = Cell(
        key = e.id, name = e.name, sub = kc(e.kcal100) + " kcal", photos = listOf(null to e.name),
        starred = e.id in starred, onClick = { logging = FoodLogic.of(e, emptyMap()) },
    )

    fun dishCell(d: ReadyDish, lib: FoodLibraryData) = Cell(
        key = d.ref, name = d.name, sub = "${d.items.size} foods",
        photos = d.items.take(3).map { lib.food(it.foodId)?.photoAsset to (lib.food(it.foodId)?.name ?: it.foodId) },
        starred = false, onClick = { dish = d },
    )

    EditWindow(
        title = "Add food",
        sub = "To ${mealTypeLabel(startMeal)}" + if (added > 0) " · $added added" else "",
        onClose = onClose,
    ) {
        LaunchedEffect(Unit) { if (focus) runCatching { focusRequester.requestFocus() } }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(p.surface, RoundedCornerShape(24.dp))
                    .border(1.25.dp, p.pen, RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaperIcon(R.drawable.ic_p_search, size = 18.dp, tint = p.ink2)
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = p.ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(p.ink),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) Text("Search foods", color = p.ink2, fontSize = 15.sp)
                            inner()
                        }
                    },
                )
                Text(
                    if (query.isNotEmpty()) "Clear" else "Speak", fontSize = 12.sp, color = p.ink2,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { if (query.isNotEmpty()) query = "" else speak() }.padding(4.dp),
                )
            }
            PaperIconButton(R.drawable.ic_p_plus, "New food", onClick = { creating = true }, modifier = Modifier.size(48.dp))
        }

        just?.let { j ->
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Added " + j.food.name + ", " + kc(j.food.per100.kcal * j.grams / 100) + " kcal, to " + mealTypeLabel(j.meal),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.ink,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperButton(
                            "Undo", { vm.undoMeal(j.id); added = maxOf(0, added - 1); just = null },
                            Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true,
                        )
                        PaperButton(
                            "Change amount",
                            { vm.undoMeal(j.id); added = maxOf(0, added - 1); just = null; loggingGrams = j.grams; logging = j.food },
                            Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true,
                        )
                    }
                }
            }
        }

        AddRouteRow(onRoute = { check = it })

        if (query.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton("Describe it with AI", { check = CheckSource.Text(query.trim()) }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true)
                PaperButton("Just calories", { quick = true }, Modifier.weight(1f), style = PaperButtonStyle.Ghost, small = true)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperPill("All", shelf == Shelf.All && cat == null, { shelf = Shelf.All; cat = null })
            PaperPill("Ready dishes", shelf == Shelf.Dishes, { shelf = Shelf.Dishes; cat = null })
            PaperPill("My foods", shelf == Shelf.Mine, { shelf = Shelf.Mine; cat = null })
            PaperPill("Favourites", shelf == Shelf.Stars, { shelf = Shelf.Stars; cat = null })
            FoodCat.entries.forEach { c -> PaperPill(c.label, shelf == Shelf.All && cat == c, { shelf = Shelf.All; cat = c }) }
        }

        if (lib == null) {
            Text("Loading the food list...", color = p.ink2, fontSize = 13.sp)
        } else {
            val q = query.trim()
            var shownCount = 0
            var more = false

            @Composable
            fun Section(title: String?, cells: List<Cell>) {
                if (cells.isEmpty()) return
                shownCount += cells.size
                if (title != null) MonoLabel(title)
                FoodGrid(cells)
            }

            when {
                shelf == Shelf.Dishes -> {
                    val list = lib.searchDishes(q)
                    Section(null, list.take(limit).map { dishCell(it, lib) })
                    more = list.size > limit
                }
                shelf == Shelf.Mine -> {
                    val list = FoodLogic.searchOwn(own, q, limit = 500)
                    Section(null, list.take(limit).map { ownCell(it) })
                    more = list.size > limit
                }
                shelf == Shelf.Stars -> {
                    val list = favs.mapNotNull { f -> vm.loggable(f.foodId)?.let { f.foodId to it } }
                        .filter { q.isEmpty() || com.lifetracker.app.data.FoodLibrary.fold(it.second.name).contains(com.lifetracker.app.data.FoodLibrary.fold(q)) }
                    Section(
                        null,
                        list.take(limit).map { (ref, food) ->
                            Cell(ref, food.name, kc(food.per100.kcal) + " kcal", listOf(food.photoAsset to food.name), true) { logging = food }
                        },
                    )
                    more = list.size > limit
                    if (favs.isEmpty()) Text("Open a food and tap the star to keep it here.", color = p.ink2, fontSize = 13.sp)
                }
                cat != null -> {
                    val list = lib.search(q, cat, limit + 1, boost)
                    Section(null, list.take(limit).map { libCell(it, lib) })
                    more = list.size > limit
                }
                q.isEmpty() -> {
                    // Starred foods first, then what you eat most at this meal; one tap adds the amount you ate last time.
                    val eaten = FoodStats.quickPicks(rawRecent, starred, startMeal, 40).map { Pick(FoodLogic.ofMeal(it, lib), it.grams) }
                    val eatenRefs = eaten.map { it.food.ref }.toSet()
                    val untried = favs.mapNotNull { f -> if (f.foodId in eatenRefs) null else vm.loggable(f.foodId)?.let { Pick(it, it.startAmount) } }
                    val (starredPicks, otherPicks) = eaten.partition { it.food.ref in starred }
                    Section(
                        "Quick picks for " + mealTypeLabel(startMeal).lowercase(),
                        (starredPicks + untried + otherPicks).take(if (browse) 12 else 6).map { pick ->
                            val food = pick.food
                            Cell(
                                "q:" + food.ref + food.name, food.name,
                                kc(food.per100.kcal * pick.grams / 100) + " kcal · " + kc(pick.grams) + " " + food.unit,
                                listOf(food.photoAsset to food.name), food.ref in starred,
                            ) { addNow(food, pick.grams) }
                        },
                    )
                    if (browse) {
                        Section("My foods", own.take(9).map { ownCell(it) })
                        Section("All foods", lib.foods.take(limit).map { libCell(it, lib) })
                        more = lib.foods.size > limit
                    } else {
                        if (rawRecent.isEmpty() && favs.isEmpty()) Text("Foods you add show up here, so adding them again takes one tap.", color = p.ink2, fontSize = 13.sp)
                    }
                    PaperButton(
                        if (browse) "Show fewer foods" else "Browse all foods", { browse = !browse },
                        style = PaperButtonStyle.Ghost, small = true,
                    )
                }
                else -> {
                    Section("My foods", FoodLogic.searchOwn(own, q, limit = 12).map { ownCell(it) })
                    val list = lib.search(q, null, limit + 1, boost)
                    Section("Foods", list.take(limit).map { libCell(it, lib) })
                    more = list.size > limit
                    Section("Ready dishes", lib.searchDishes(q).take(6).map { dishCell(it, lib) })
                }
            }

            if (more) PaperButton("Show more", { limit += 60 }, style = PaperButtonStyle.Ghost, small = true)
            if (shownCount == 0 && shelf != Shelf.Stars && (q.isNotEmpty() || shelf != Shelf.All || cat != null)) {
                Text(
                    if (shelf == Shelf.Mine && q.isEmpty()) "You have not made any foods yet." else "Nothing found.",
                    color = p.ink2, fontSize = 13.sp,
                )
            }
            PaperButton(
                if (q.isEmpty()) "Make a new food" else "Make a new food called \"$q\"",
                { creating = true }, style = PaperButtonStyle.Ghost, small = true, icon = R.drawable.ic_p_plus,
            )
        }
    }

    check?.let { source ->
        PhotoCheckWindow(startMeal = startMeal, date = day, source = source, onAdded = { added += 1 }, onClose = { check = null })
    }
    if (quick) {
        QuickAddSheet(
            vm = vm, startMeal = startMeal, startName = query.trim(),
            onDone = { quick = false; added += 1 }, onDismiss = { quick = false },
        )
    }
    logging?.let { food ->
        LogFoodSheet(
            vm = vm, food = food, startMeal = startMeal, startGrams = loggingGrams ?: lastGrams[food.ref],
            onDone = { logging = null; loggingGrams = null; added += 1 }, onDismiss = { logging = null; loggingGrams = null },
        )
    }
    dish?.let { d ->
        if (lib != null) {
            DishSheet(
                vm = vm, dish = d, library = lib, startMeal = startMeal,
                onDone = { dish = null; added += 1 }, onDismiss = { dish = null },
            )
        }
    }
    if (creating) {
        FoodEditor(
            vm = vm, initial = null, presetName = query.trim(),
            onDismiss = { creating = false },
            onSaved = { logging = it },
        )
    }
}

// ---- a ready dish ----

@Composable
private fun DishSheet(
    vm: FoodViewModel,
    dish: ReadyDish,
    library: FoodLibraryData,
    startMeal: String,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPaper.current
    val date by vm.date.collectAsState()
    val parts = remember(dish) { FoodLogic.dishParts(dish, library) }
    var meal by remember { mutableStateOf(startMeal) }
    val today = date == LocalDate.now()
    var minute by remember { mutableIntStateOf(FoodLogic.minuteFor(startMeal, today, minuteNow())) }
    var touched by remember { mutableStateOf(false) }

    val kcal = parts.sumOf { (f, g) -> f.per100.kcal * g / 100 }
    val protein = parts.sumOf { (f, g) -> f.per100.protein * g / 100 }
    val carbs = parts.sumOf { (f, g) -> f.per100.carbs * g / 100 }
    val fat = parts.sumOf { (f, g) -> f.per100.fat * g / 100 }

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FoodPlate(parts.map { it.first.photoAsset to it.first.name }, 44.dp)
                SheetTitle(dish.name, "${kc(kcal)} kcal for all of it")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                MacroCell("Protein", protein, PROTEIN_COLOR, Modifier.weight(1f))
                MacroCell("Carbs", carbs, CARB_COLOR, Modifier.weight(1f))
                MacroCell("Fat", fat, FAT_COLOR, Modifier.weight(1f))
            }
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    parts.forEach { (food, grams) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FoodPhoto(food.photoAsset, food.name, 34.dp)
                            Text(food.name, fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(kc(grams) + " " + food.unit, fontSize = 12.sp, color = p.ink2)
                        }
                    }
                }
            }
            MealPills(meal) { t ->
                meal = t
                if (!touched) minute = FoodLogic.minuteFor(t, today, minuteNow())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Time", fontSize = 13.sp, color = p.ink2)
                TimeButton(minute, { minute = it; touched = true })
            }
            PaperButton(
                "Add everything to ${meal}", onClick = { vm.logDish(dish, meal, date, minute); onDone() },
                modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_p_plus,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- logging one food ----

@Composable
fun LogFoodSheet(vm: FoodViewModel, food: Loggable, startMeal: String, onDone: () -> Unit, onDismiss: () -> Unit, startGrams: Double? = null) {
    val p = LocalPaper.current
    val library by vm.library.collectAsState()
    val date by vm.date.collectAsState()
    val meals by vm.meals.collectAsState()
    val goal by vm.goal.collectAsState()
    val favs by vm.favourites.collectAsState()
    val own by vm.ownFoods.collectAsState()
    val nutrients by vm.nutrients.collectAsState()
    val today = date == LocalDate.now()

    var full by remember { mutableStateOf(food) }
    LaunchedEffect(food.ref) { full = vm.withYourNumbers(food) }
    var cooked by remember { mutableStateOf(false) }
    val shown = if (cooked && full.canCook) full.cooked() else full
    var amount by remember { mutableStateOf(foodNum(startGrams?.takeIf { it > 0 } ?: food.startAmount)) }
    var meal by remember { mutableStateOf(startMeal) }
    var minute by remember { mutableIntStateOf(FoodLogic.minuteFor(startMeal, today, minuteNow())) }
    var touched by remember { mutableStateOf(false) }
    var showVitamins by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    val grams = foodParse(amount) ?: 0.0
    val k = grams / 100
    val kcal = shown.per100.kcal * k
    val eatenToday = FoodStats.totals(meals).kcal
    val left = goal?.kcal?.takeIf { it > 0 }?.let { it - eatenToday - kcal }
    val starred = favs.any { it.foodId == food.ref }
    val ownEntity = own.firstOrNull { it.id == food.ref }
    val assetMicro = remember(library, food.ref) {
        if (FoodRef.isLibrary(food.ref)) library?.microOf(food.ref.removePrefix(FoodRef.LIBRARY)) ?: emptyMap() else emptyMap()
    }

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FoodPhoto(shown.photoAsset, shown.name, 64.dp)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(shown.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 2)
                    Text(
                        kc(shown.per100.kcal) + " kcal in 100 " + shown.unit + if (shown.hasPortion) ", 1 ${shown.portion!!.one} is ${kc(shown.portionGrams)} ${shown.unit}" else "",
                        fontSize = 12.sp, color = p.ink2,
                    )
                }
                if (food.ref.isNotEmpty()) {
                    PaperIconButton(
                        if (starred) R.drawable.ic_p_star_fill else R.drawable.ic_p_star,
                        if (starred) "Remove from favourites" else "Add to favourites",
                        onClick = { vm.toggleFavourite(food.ref, !starred) },
                    )
                }
            }

            if (full.canCook) {
                PaperSegmented(listOf("Raw", "Cooked"), if (cooked) 1 else 0, { i ->
                    cooked = i == 1
                    amount = foodNum((if (cooked) full.cooked() else full).startAmount)
                })
            }

            PaperField(
                value = amount, onValueChange = { amount = it }, label = "Amount (${shown.unit})",
                singleLine = true, keyboardOptions = DecimalKeys,
            )
            ChipFlow {
                if (shown.hasPortion) {
                    listOf(0.5, 1.0, 2.0, 3.0).forEach { n ->
                        val g = shown.portionGrams * n
                        val label = (if (n == 0.5) "1/2" else foodNum(n)) + " " + (if (n == 1.0) shown.portion!!.one else shown.portion!!.many)
                        PaperPill(label, Math.abs(grams - g) < 0.01, { amount = foodNum(g) })
                    }
                } else {
                    listOf(50.0, 100.0, 150.0, 200.0, 250.0).forEach { g ->
                        PaperPill(kc(g) + " " + shown.unit, Math.abs(grams - g) < 0.01, { amount = foodNum(g) })
                    }
                }
            }
            FoodLogic.portionText(shown, grams)?.let { Text("That is $it", fontSize = 12.sp, color = p.ink2) }

            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(kc(kcal), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                    Text("kcal", fontSize = 13.sp, color = p.ink2, modifier = Modifier.padding(bottom = 5.dp))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    MacroCell("Protein", shown.per100.protein * k, PROTEIN_COLOR, Modifier.weight(1f))
                    MacroCell("Carbs", shown.per100.carbs * k, CARB_COLOR, Modifier.weight(1f))
                    MacroCell("Fat", shown.per100.fat * k, FAT_COLOR, Modifier.weight(1f))
                }
                if (shown.per100.fiber > 0 || shown.per100.sugar > 0 || shown.per100.sodiumMg > 0) {
                    Text(
                        "Fibre ${kc(shown.per100.fiber * k)} g  ·  Sugar ${kc(shown.per100.sugar * k)} g  ·  Sodium ${kc(shown.per100.sodiumMg * k)} mg",
                        fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (left != null) {
                    Text(
                        if (left >= 0) "${kc(left)} kcal left today after this" else "${kc(-left)} kcal over today's goal after this",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (left >= 0) p.ink2 else p.bad,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            MealPills(meal) { t ->
                meal = t
                if (!touched) minute = FoodLogic.minuteFor(t, today, minuteNow())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Time", fontSize = 13.sp, color = p.ink2)
                TimeButton(minute, { minute = it; touched = true })
            }

            val vitamins = shown.micro.mapValues { it.value * k }
            if (vitamins.any { it.value > 0 }) {
                MonoLabel("Vitamins and minerals in this amount")
                VitaminLines(nutrients, vitamins)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (food.ref.isNotEmpty()) {
                    PaperButton("Vitamins and minerals", { showVitamins = true }, style = PaperButtonStyle.Ghost, small = true)
                }
                if (ownEntity != null) {
                    PaperButton("Edit this food", { editing = true }, style = PaperButtonStyle.Ghost, small = true)
                }
            }

            PaperButton(
                "Add to $meal", onClick = { vm.logFood(shown, grams, meal, date, minute); onDone() },
                modifier = Modifier.fillMaxWidth(), enabled = grams > 0, icon = R.drawable.ic_p_plus,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showVitamins) {
        VitaminValuesWindow(
            nutrients = nutrients, name = food.name, initial = full.micro,
            onSave = { edited ->
                vm.saveNumbers(food.ref, onlyChanged(assetMicro, edited))
                full = full.copy(micro = edited)
            },
            onClose = { showVitamins = false },
        )
    }
    if (editing && ownEntity != null) {
        FoodEditor(vm = vm, initial = ownEntity, onDismiss = { editing = false }, onSaved = { full = it })
    }
}

// ---- quick add ----

@Composable
fun QuickAddSheet(vm: FoodViewModel, startMeal: String, onDone: () -> Unit, onDismiss: () -> Unit, startName: String = "") {
    val p = LocalPaper.current
    val date by vm.date.collectAsState()
    val today = date == LocalDate.now()

    var name by remember { mutableStateOf(startName) }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var grams by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(true) }
    var meal by remember { mutableStateOf(startMeal) }
    var minute by remember { mutableIntStateOf(FoodLogic.minuteFor(startMeal, today, minuteNow())) }
    var touched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle("Quick add", "Type what you ate. The numbers are for the whole amount you ate.")
            PaperField(name, { name = it; error = null }, "Name", placeholder = "e.g. Lunch at the canteen", singleLine = true)
            PaperField(kcal, { kcal = it; error = null }, "Calories (kcal)", singleLine = true, keyboardOptions = DecimalKeys)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperField(protein, { protein = it }, "Protein g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
                PaperField(carbs, { carbs = it }, "Carbs g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
                PaperField(fat, { fat = it }, "Fat g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            }
            PaperField(
                grams, { grams = it }, "Weight in grams (optional)",
                singleLine = true, keyboardOptions = DecimalKeys,
            )
            MealPills(meal) { t ->
                meal = t
                if (!touched) minute = FoodLogic.minuteFor(t, today, minuteNow())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Time", fontSize = 13.sp, color = p.ink2)
                TimeButton(minute, { minute = it; touched = true })
            }
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Also save it as one of my foods", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
                        Text(
                            if (save) "It shows up in Add food, under My foods, as 1 serving." else "It is logged once and not kept.",
                            fontSize = 12.sp, color = p.ink2,
                        )
                    }
                    PaperToggle(save, { save = it })
                }
            }
            error?.let { Text(it, color = p.bad, fontSize = 13.sp) }
            PaperButton(
                "Add to $meal",
                onClick = {
                    val k = foodParse(kcal)
                    val g = foodParse(grams) ?: 0.0
                    when {
                        k == null || k < 0 -> error = "Enter the calories."
                        save && name.isBlank() -> error = "Give it a name to save it."
                        else -> {
                            vm.quickAdd(
                                name = name.ifBlank { "Quick add" }, grams = g, kcal = k,
                                protein = foodParse(protein) ?: 0.0, carbs = foodParse(carbs) ?: 0.0, fat = foodParse(fat) ?: 0.0,
                                mealType = meal, date = date, minuteOfDay = minute, save = save,
                            )
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_p_bolt,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- one logged entry ----

@Composable
fun EntrySheet(vm: FoodViewModel, meal: MealEntity, library: FoodLibraryData?, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val nutrients by vm.nutrients.collectAsState()
    val date by vm.date.collectAsState()

    var amount by remember { mutableStateOf(foodNum(meal.grams)) }
    var type by remember { mutableStateOf(meal.mealType) }
    var minute by remember { mutableIntStateOf(meal.minuteOfDay) }
    var confirmDelete by remember { mutableStateOf(false) }

    val grams = foodParse(amount) ?: 0.0
    val now = meal.copy(grams = grams, mealType = type, minuteOfDay = minute)
    val photo = library?.byRef(meal.foodId)?.photoAsset

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MealThumb(meal.id, photo, meal.name, 56.dp)
                SheetTitle(meal.name, "${kc(meal.kcal100)} kcal in 100 g")
            }
            PaperField(amount, { amount = it }, "Amount eaten (g)", singleLine = true, keyboardOptions = DecimalKeys)
            PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
                Text(kc(now.kcal) + " kcal", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    MacroCell("Protein", now.protein, PROTEIN_COLOR, Modifier.weight(1f))
                    MacroCell("Carbs", now.carbs, CARB_COLOR, Modifier.weight(1f))
                    MacroCell("Fat", now.fat, FAT_COLOR, Modifier.weight(1f))
                }
            }
            MealPills(type) { type = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Time", fontSize = 13.sp, color = p.ink2)
                TimeButton(minute, { minute = it })
            }
            val vitamins = NutrientMath.forMeal(now)
            if (vitamins.any { it.value > 0 }) {
                MonoLabel("Vitamins and minerals")
                VitaminLines(nutrients, vitamins)
            }
            PaperButton(
                "Save", onClick = { vm.addMeal(now); onDismiss() },
                modifier = Modifier.fillMaxWidth(), enabled = grams > 0,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PaperButton(
                    "Copy to a day",
                    { pickDay(context, date) { day -> vm.copyMeals(listOf(now), day); onDismiss() } },
                    style = PaperButtonStyle.Ghost, small = true,
                )
                if (!FoodRef.isMine(meal.foodId)) {
                    PaperButton(
                        "Keep as my food",
                        {
                            vm.saveFood(FoodLogic.foodFromMeal(meal, System.currentTimeMillis()), NutrientValues.decode(meal.extra))
                            onDismiss()
                        },
                        style = PaperButtonStyle.Ghost, small = true,
                    )
                }
                PaperButton("Delete", { confirmDelete = true }, style = PaperButtonStyle.Danger, small = true)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmDelete) {
        PaperDialog(
            title = "Delete ${meal.name}?",
            onDismiss = { confirmDelete = false },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = { confirmDelete = false; vm.deleteMeal(meal); onDismiss() },
        ) {
            Text("It is taken out of this day.", color = p.ink)
        }
    }
}

// ---- the menu of one meal ----

@Composable
fun MealMenuSheet(vm: FoodViewModel, mealType: String, meals: List<MealEntity>, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val date by vm.date.collectAsState()
    var name by remember { mutableStateOf(mealTypeLabel(mealType)) }
    var confirmClear by remember { mutableStateOf(false) }

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle(mealTypeLabel(mealType), if (meals.isEmpty()) "Nothing logged yet" else "${meals.size} items, ${kc(FoodStats.totals(meals).kcal)} kcal")
            if (meals.isNotEmpty()) {
                PaperField(name, { name = it }, "Save these foods as a meal called", singleLine = true)
                PaperButton(
                    "Save as a meal", { vm.saveMealAs(name, meals); onDismiss() },
                    modifier = Modifier.fillMaxWidth(), enabled = name.isNotBlank(), icon = R.drawable.ic_p_folder,
                )
                PaperButton(
                    "Copy to another day",
                    { pickDay(context, date.plusDays(1)) { day -> vm.copyMeals(meals, day); onDismiss() } },
                    modifier = Modifier.fillMaxWidth(), style = PaperButtonStyle.Ghost,
                )
                PaperButton("Clear this meal", { confirmClear = true }, modifier = Modifier.fillMaxWidth(), style = PaperButtonStyle.Danger)
            } else {
                Text("Add some food first, then you can save it as a meal here.", fontSize = 13.sp, color = p.ink2)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmClear) {
        PaperDialog(
            title = "Clear ${mealTypeLabel(mealType)}?",
            onDismiss = { confirmClear = false },
            confirmText = "Clear",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = { confirmClear = false; vm.deleteMeals(meals); onDismiss() },
        ) {
            Text("Every food in it is taken out of this day.", color = p.ink)
        }
    }
}

// ---- favourites ----

@Composable
fun FavouritesSheet(vm: FoodViewModel, onPick: (Loggable) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val favs by vm.favourites.collectAsState()
    val library by vm.library.collectAsState()
    val own by vm.ownFoods.collectAsState()

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle("Favourites", "Tap a food to log it")
            // Reading these two keeps the list fresh when the food list or your foods finish loading.
            val ready = library != null && own.size >= 0
            val rows = if (ready) favs.mapNotNull { f -> vm.loggable(f.foodId) } else emptyList()
            if (rows.isEmpty()) {
                Text("Open any food and tap the star to keep it here.", fontSize = 13.sp, color = p.ink2)
            }
            rows.forEach { food ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { onPick(food); onDismiss() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FoodPhoto(food.photoAsset, food.name, 44.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(food.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
                        Text(kc(food.per100.kcal) + " kcal in 100 " + food.unit, fontSize = 12.sp, color = p.ink2)
                    }
                    PaperIconButton(R.drawable.ic_p_star_fill, "Remove from favourites", onClick = { vm.toggleFavourite(food.ref, false) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- saved meals ----

@Composable
fun SavedMealsSheet(vm: FoodViewModel, mealType: String, onDone: () -> Unit, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val saved by vm.savedMeals.collectAsState()
    val library by vm.library.collectAsState()
    val date by vm.date.collectAsState()
    var renaming by remember { mutableStateOf<SavedMealEntity?>(null) }
    var deleting by remember { mutableStateOf<SavedMealEntity?>(null) }
    var newName by remember { mutableStateOf("") }
    val today = date == LocalDate.now()

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle("Saved meals", "Adds all of its foods to ${mealTypeLabel(mealType)}")
            if (saved.isEmpty()) {
                Text("Open the menu of a meal and choose Save as a meal.", fontSize = 13.sp, color = p.ink2)
            }
            saved.forEach { meal ->
                val items = SavedMealItems.decode(meal.items)
                PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FoodPlate(items.take(3).map { library?.byRef(it.foodId)?.photoAsset to it.name }, 34.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(meal.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
                            Text("${items.size} foods, ${kc(items.sumOf { it.kcal })} kcal", fontSize = 12.sp, color = p.ink2)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaperButton(
                            "Add", {
                                vm.logSavedMeal(meal, mealType, date, FoodLogic.minuteFor(mealType, today, minuteNow()))
                                onDone()
                            },
                            small = true, icon = R.drawable.ic_p_plus,
                        )
                        PaperButton("Rename", { renaming = meal; newName = meal.name }, style = PaperButtonStyle.Ghost, small = true)
                        PaperButton("Delete", { deleting = meal }, style = PaperButtonStyle.Danger, small = true)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    renaming?.let { meal ->
        PaperDialog(
            title = "Rename meal",
            onDismiss = { renaming = null },
            confirmText = "Save",
            confirmEnabled = newName.isNotBlank(),
            onConfirm = { vm.renameSavedMeal(meal, newName); renaming = null },
        ) {
            PaperField(newName, { newName = it }, "Name", singleLine = true)
        }
    }
    deleting?.let { meal ->
        PaperDialog(
            title = "Delete ${meal.name}?",
            onDismiss = { deleting = null },
            confirmText = "Delete",
            confirmStyle = PaperButtonStyle.Danger,
            dismissText = "Keep",
            onConfirm = { vm.deleteSavedMeal(meal); deleting = null },
        ) {
            Text("Days where you already added it are not changed.", color = p.ink)
        }
    }
}

// ---- goals ----

@Composable
fun GoalsSheet(vm: FoodViewModel, onDismiss: () -> Unit) {
    val p = LocalPaper.current
    val goal by vm.goal.collectAsState()
    val date by vm.date.collectAsState()
    var kcal by remember { mutableStateOf(foodNum(goal?.kcal ?: 0.0)) }
    var protein by remember { mutableStateOf(foodNum(goal?.protein ?: 0.0)) }
    var carbs by remember { mutableStateOf(foodNum(goal?.carbs ?: 0.0)) }
    var fat by remember { mutableStateOf(foodNum(goal?.fat ?: 0.0)) }
    var error by remember { mutableStateOf<String?>(null) }

    val fromMacros = (foodParse(protein) ?: 0.0) * 4 + (foodParse(carbs) ?: 0.0) * 4 + (foodParse(fat) ?: 0.0) * 9

    PaperSheet(onDismiss = onDismiss) {
        SheetBody {
            SheetTitle("Daily goals", "From ${TimelineTop.title(date, LocalDate.now())} on. Earlier days keep their own goal.")
            PaperField(kcal, { kcal = it; error = null }, "Calories (kcal)", singleLine = true, keyboardOptions = DecimalKeys)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperField(protein, { protein = it }, "Protein g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
                PaperField(carbs, { carbs = it }, "Carbs g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
                PaperField(fat, { fat = it }, "Fat g", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            }
            if (fromMacros > 0) {
                Text("Those macros add up to ${kc(fromMacros)} kcal.", fontSize = 12.sp, color = p.ink2)
                PaperButton(
                    "Use ${kc(fromMacros)} as the calorie goal", { kcal = foodNum(Math.round(fromMacros).toDouble()) },
                    style = PaperButtonStyle.Ghost, small = true,
                )
            }
            error?.let { Text(it, color = p.bad, fontSize = 13.sp) }
            PaperButton(
                "Save goals",
                onClick = {
                    val k = foodParse(kcal)
                    if (k == null || k <= 0) {
                        error = "Enter the calories you aim for."
                    } else {
                        vm.setGoal(k, foodParse(protein) ?: 0.0, foodParse(carbs) ?: 0.0, foodParse(fat) ?: 0.0)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
