package com.lifetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lifetracker.app.R
import com.lifetracker.app.data.Feature
import com.lifetracker.app.data.Features
import com.lifetracker.app.data.Rule
import com.lifetracker.app.data.KIND_EXPENSE
import com.lifetracker.app.data.KIND_INCOME
import com.lifetracker.app.data.RuleKinds
import com.lifetracker.app.data.RuleLogEntity
import com.lifetracker.app.data.RulePart
import com.lifetracker.app.data.RuleSummary
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonBox
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperToggle
import com.lifetracker.app.ui.theme.Wobble
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rules: when something happens, do something. A list of your rules, each with a switch, and the line of what ran and why. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RulesScreen(onBack: () -> Unit, vm: RulesViewModel = viewModel()) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val rules by vm.rules.collectAsState()
    val log by vm.log.collectAsState()
    val places by vm.places.collectAsState()
    val categories by vm.categories.collectAsState()
    val habits by vm.habits.collectAsState()
    val activities by vm.activities.collectAsState()
    val featureChanges by Features.changes.collectAsState()
    val featureOn = featureChanges >= 0 && Features.isOn(context, Feature.RULES)
    var editing by remember { mutableStateOf<Rule?>(null) }
    var creating by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    fun placeName(id: String) = places.firstOrNull { it.id == id }?.name ?: "a place that is gone"
    fun categoryName(id: String) = categories.firstOrNull { it.id == id }?.name ?: "a kind that is gone"
    fun habitName(id: String) = habits.firstOrNull { it.id == id }?.name ?: "a habit that is gone"
    fun activityName(id: String) = activities.firstOrNull { it.id == id }?.name ?: "an activity that is gone"
    fun ruleName(id: String) = rules.firstOrNull { it.id == id }?.name ?: "a rule that is gone"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingsTop("Rules", "When something happens, do something", onBack) }
        item {
            Text(
                "A rule has one thing that sets it off (arriving somewhere, charging starting, a time of day, Wi-Fi, an app, a call...), optional conditions " +
                    "(only on weekdays, only if charging, only if a habit is not done yet) and things to do (tick a habit, add a Timeline entry, send a notification, " +
                    "start a focus timer, log money...). A rule that is set to \"I press Run now\" shows up as a button at the top. " +
                    "Rules use only what the phone already notices; nothing goes online. A rule that runs 10 times in 10 minutes is switched off " +
                    "by itself, so nothing can loop for ever.",
                fontSize = 12.5.sp,
                color = p.ink2,
            )
        }
        if (!featureOn) {
            item { Text("Rules are switched off in Settings > Features, so none of them runs.", fontSize = 13.sp, color = p.bad) }
        }
        val buttons = rules.filter { it.enabled && it.trigger.kind == RuleKinds.BUTTON }
        if (buttons.isNotEmpty()) {
            item { PaperSection("Buttons", "One tap does what the rule says: \"this happened\" for things the phone cannot notice") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEach { b -> PaperButton(b.name, onClick = { vm.runNow(b.id) }) }
                }
            }
            item { PaperSection("Your rules") }
        }
        items(rules, key = { it.id }) { r ->
            RuleCard(
                rule = r,
                trigger = RuleSummary.trigger(r.trigger, ::placeName, ::categoryName, ::habitName),
                conditions = r.conditions.map { RuleSummary.condition(it, ::placeName, ::categoryName, ::habitName) },
                actions = r.actions.map { RuleSummary.action(it, ::habitName, ::activityName, ::ruleName) },
                onClick = { editing = r },
                onToggle = { vm.setEnabled(r.id, it) },
            )
        }
        item {
            PaperButtonBox(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                PaperIcon(R.drawable.ic_p_plus, size = 18.dp, tint = LocalContentColor.current)
                Text("New rule")
            }
        }
        item { PaperSection("What ran and why", "The latest 100 lines. Held-back rules say why.") }
        if (log.isEmpty()) {
            item { Text("Nothing yet. A rule shows up here each time it runs or is held back.", fontSize = 13.sp, color = p.ink2) }
        } else {
            items(log, key = { it.id }) { line -> LogLine(line) }
            item { PaperButton("Clear this list", onClick = vm::clearLog, style = PaperButtonStyle.Ghost, small = true) }
        }
    }

    if (creating) {
        RuleEditor(
            initial = null, vm = vm, onClose = { creating = false },
        )
    }
    editing?.let { r -> RuleEditor(initial = r, vm = vm, onClose = { editing = null }) }
}

@Composable
private fun RuleCard(rule: Rule, trigger: String, conditions: List<String>, actions: List<String>, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (rule.enabled) 1f else 0.6f)
            .background(p.surface, shape)
            .border(1.25.dp, p.line, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(rule.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 2)
            Text("When $trigger", fontSize = 12.5.sp, color = p.ink2)
            if (conditions.isNotEmpty()) Text(conditions.joinToString(", ").replaceFirstChar { it.uppercase() }, fontSize = 12.5.sp, color = p.ink2)
            Text("Then " + actions.joinToString(", then "), fontSize = 12.5.sp, color = p.ink2)
            Text(
                if (rule.runs == 0) "Has not run yet" else "Ran ${rule.runs} time" + (if (rule.runs == 1) "" else "s") + ", last " + whenText(rule.lastRunMs),
                fontSize = 11.5.sp,
                color = p.ink2,
            )
        }
        PaperToggle(checked = rule.enabled, onChange = onToggle)
    }
}

private val LOG_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())

private fun whenText(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(LOG_TIME)

@Composable
private fun LogLine(line: RuleLogEntity) {
    val p = LocalPaper.current
    val shape = Wobble.Small
    val tone = when (line.result) {
        "ran" -> p.ink
        "failed", "off" -> p.bad
        else -> p.ink2
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface, shape)
            .border(1.dp, p.line, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(whenText(line.atMs), fontSize = 11.sp, color = p.ink2)
            Text(
                when (line.result) { "ran" -> "RAN"; "failed" -> "FAILED"; "off" -> "SWITCHED OFF"; else -> "HELD BACK" },
                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tone,
            )
            Text(line.ruleName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.ink, maxLines = 1)
        }
        Text(line.event, fontSize = 12.sp, color = p.ink2)
        Text(line.detail, fontSize = 12.sp, color = tone)
    }
}

/** The settings each trigger keeps in its own part (everything else typed in the window is left out). */
private val TRIGGER_KEYS = mapOf(
    RuleKinds.HABIT_TICKED to listOf("habitId"),
    RuleKinds.WIFI_ON to listOf("name"),
    RuleKinds.WIFI_OFF to listOf("name"),
    RuleKinds.BT_ON to listOf("device", "deviceName"),
    RuleKinds.BT_OFF to listOf("device", "deviceName"),
    RuleKinds.APP_OPEN to listOf("pkg", "label"),
    RuleKinds.APP_USED to listOf("pkg", "label"),
    RuleKinds.CALL to listOf("type"),
)

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private fun clamp(text: String, lo: Int, hi: Int, default: Int): Int = (text.trim().toIntOrNull() ?: default).coerceIn(lo, hi)

/** The window to make or change a rule. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleEditor(initial: Rule?, vm: RulesViewModel, onClose: () -> Unit) {
    val p = LocalPaper.current
    val places by vm.places.collectAsState()
    val categories by vm.categories.collectAsState()
    val habits by vm.habits.collectAsState()
    val activities by vm.activities.collectAsState()
    val allRules by vm.rules.collectAsState()
    val apps by vm.apps.collectAsState()
    val moneyCategories by vm.moneyCategories.collectAsState()
    val paired = remember { vm.pairedDevices() }

    val t0 = initial?.trigger
    var tp by remember { mutableStateOf(t0?.params ?: emptyMap<String, String>()) }
    fun tset(key: String, value: String) { tp = tp + (key to value) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(t0?.kind ?: RuleKinds.ARRIVE) }
    var whereMode by remember { mutableStateOf(if (t0?.text("placeId").orEmpty().isNotEmpty()) 1 else if (t0?.text("category").orEmpty().isNotEmpty()) 2 else 0) }
    var placeId by remember { mutableStateOf(t0?.text("placeId").orEmpty()) }
    var category by remember { mutableStateOf(t0?.text("category").orEmpty()) }
    var stayMinutes by remember { mutableStateOf((t0?.int("minutes", 30) ?: 30).toString()) }
    var atHour by remember { mutableStateOf(((t0?.int("at", 480) ?: 480) / 60).toString()) }
    var atMinute by remember { mutableStateOf(((t0?.int("at", 480) ?: 480) % 60).toString().padStart(2, '0')) }

    val daysPart = initial?.conditions?.firstOrNull { it.kind == RuleKinds.DAYS }
    val windowPart = initial?.conditions?.firstOrNull { it.kind == RuleKinds.WINDOW }
    var daysOn by remember { mutableStateOf(daysPart != null) }
    var days by remember { mutableStateOf(daysPart?.let { RuleSummary.days(it) } ?: setOf(1, 2, 3, 4, 5)) }
    val chargingPart = initial?.conditions?.firstOrNull { it.kind == RuleKinds.CHARGING }
    val atPart = initial?.conditions?.firstOrNull { it.kind == RuleKinds.AT_PLACE }
    val notDonePart = initial?.conditions?.firstOrNull { it.kind == RuleKinds.HABIT_NOT_DONE }
    var chargingOn by remember { mutableStateOf(chargingPart != null) }
    var chargingState by remember { mutableStateOf(chargingPart?.text("state", "on") ?: "on") }
    var atOn by remember { mutableStateOf(atPart != null) }
    var atMode by remember { mutableStateOf(if (atPart?.text("placeId").orEmpty().isNotEmpty()) 1 else if (atPart?.text("category").orEmpty().isNotEmpty()) 2 else 0) }
    var atPlaceId by remember { mutableStateOf(atPart?.text("placeId").orEmpty()) }
    var atCategory by remember { mutableStateOf(atPart?.text("category").orEmpty()) }
    var atState by remember { mutableStateOf(atPart?.text("state", "on") ?: "on") }
    var notDoneOn by remember { mutableStateOf(notDonePart != null) }
    var notDoneHabit by remember { mutableStateOf(notDonePart?.text("habitId").orEmpty()) }
    var matchAny by remember { mutableStateOf(initial?.conditions?.any { it.kind == RuleKinds.MATCH && it.text("mode") == "any" } == true) }
    var windowOn by remember { mutableStateOf(windowPart != null) }
    var fromText by remember { mutableStateOf(RuleSummary.clock(windowPart?.int("from", 540) ?: 540)) }
    var toText by remember { mutableStateOf(RuleSummary.clock(windowPart?.int("to", 1080) ?: 1080)) }

    val actions = remember { mutableStateListOf<RulePart>().also { it.addAll(initial?.actions ?: emptyList()) } }
    var cooldown by remember { mutableStateOf((initial?.cooldownSec ?: Rule.DEFAULT_COOLDOWN_SEC).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun parseClock(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    fun build(): Rule? {
        val needsPlace = kind == RuleKinds.ARRIVE || kind == RuleKinds.LEAVE || kind == RuleKinds.STAY
        if (needsPlace && whereMode == 1 && placeId.isEmpty()) { error = "Pick the place."; return null }
        if (needsPlace && whereMode == 2 && category.isEmpty()) { error = "Pick the kind of place."; return null }
        var trigger = RulePart(kind)
        if (needsPlace) {
            if (whereMode == 1) trigger = trigger.with("placeId", placeId)
            if (whereMode == 2) trigger = trigger.with("category", category)
        }
        if (kind == RuleKinds.STAY) trigger = trigger.with("minutes", clamp(stayMinutes, 1, 1440, 30).toString())
        if (kind == RuleKinds.TIME) {
            val at = clamp(atHour, 0, 23, 8) * 60 + clamp(atMinute, 0, 59, 0)
            trigger = trigger.with("at", at.toString())
        }
        val keys = TRIGGER_KEYS[kind].orEmpty()
        keys.forEach { k -> tp[k]?.takeIf { it.isNotEmpty() }?.let { trigger = trigger.with(k, it) } }
        if (kind == RuleKinds.SPEED_OVER) trigger = trigger.with("kmh", clamp(tp["kmh"].orEmpty(), 1, 400, 60).toString())
        if (kind == RuleKinds.SPEED_UNDER) trigger = trigger.with("kmh", clamp(tp["kmh"].orEmpty(), 0, 400, 10).toString())
        if (kind == RuleKinds.APP_USED) {
            if (tp["pkg"].orEmpty().isEmpty()) { error = "Pick the app."; return null }
            trigger = trigger.with("minutes", clamp(tp["minutes"].orEmpty(), 1, 1440, 60).toString())
        }
        if (kind == RuleKinds.STEPS) trigger = trigger.with("steps", clamp(tp["steps"].orEmpty(), 1, 100_000, 8000).toString())
        val conditions = mutableListOf<RulePart>()
        if (chargingOn) conditions += RulePart(RuleKinds.CHARGING).with("state", chargingState)
        if (atOn) {
            if (atMode == 1 && atPlaceId.isEmpty()) { error = "Pick the place for the condition."; return null }
            if (atMode == 2 && atCategory.isEmpty()) { error = "Pick the kind of place for the condition."; return null }
            var c = RulePart(RuleKinds.AT_PLACE).with("state", atState)
            if (atMode == 1) c = c.with("placeId", atPlaceId)
            if (atMode == 2) c = c.with("category", atCategory)
            conditions += c
        }
        if (notDoneOn) {
            if (notDoneHabit.isEmpty()) { error = "Pick the habit for the condition."; return null }
            conditions += RulePart(RuleKinds.HABIT_NOT_DONE).with("habitId", notDoneHabit)
        }
        if (daysOn) {
            if (days.isEmpty()) { error = "Pick at least one day, or switch the days off."; return null }
            conditions += RulePart(RuleKinds.DAYS).with("days", days.sorted().joinToString(","))
        }
        if (windowOn) {
            val from = parseClock(fromText)
            val to = parseClock(toText)
            if (from == null || to == null) { error = "Write the times like 09:00."; return null }
            conditions += RulePart(RuleKinds.WINDOW).with("from", from.toString()).with("to", to.toString())
        }
        if (matchAny && conditions.size >= 2) conditions += RulePart(RuleKinds.MATCH).with("mode", "any")
        if (actions.isEmpty()) { error = "Add something to do."; return null }
        actions.forEach { a ->
            when (a.kind) {
                RuleKinds.TICK_HABIT -> if (a.text("habitId").isEmpty()) { error = "Pick the habit to tick."; return null }
                RuleKinds.ENTRY -> if (a.text("title").isBlank()) { error = "Give the Timeline entry a name."; return null }
                RuleKinds.NOTIFY -> if (a.text("title").isBlank()) { error = "Give the notification a title."; return null }
                RuleKinds.NOTE -> if (a.text("habitId").isEmpty() || a.text("text").isBlank()) { error = "Pick the habit and write the note."; return null }
                RuleKinds.RUN_RULE -> if (a.text("ruleId").isEmpty()) { error = "Pick the rule to run."; return null }
                RuleKinds.MONEY -> if (a.text("amount").replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } != true) { error = "Write the amount to log, above 0."; return null }
            }
        }
        val now = System.currentTimeMillis()
        val shown = RuleSummary.trigger(trigger, { id -> places.firstOrNull { it.id == id }?.name ?: "a place" }, { id -> categories.firstOrNull { it.id == id }?.name ?: "a kind" }, { id -> habits.firstOrNull { it.id == id }?.name ?: "a habit" })
        return Rule(
            id = initial?.id ?: "r$now",
            name = name.trim().ifEmpty { shown.replaceFirstChar { it.uppercase() } },
            enabled = initial?.enabled ?: true,
            trigger = trigger,
            conditions = conditions,
            actions = actions.toList(),
            cooldownSec = clamp(cooldown, 0, 86_400, Rule.DEFAULT_COOLDOWN_SEC),
            createdMs = initial?.createdMs ?: now,
            lastRunMs = initial?.lastRunMs ?: 0L,
            runs = initial?.runs ?: 0,
        )
    }

    EditWindow(
        title = if (initial == null) "New rule" else name.ifBlank { "Rule" },
        sub = "When something happens, do something",
        onClose = onClose,
        onSave = { build()?.let { vm.save(it); onClose() } },
    ) {
        PaperField(value = name, onValueChange = { name = it }, label = "Name (optional)", modifier = Modifier.fillMaxWidth(), placeholder = "e.g. Gym done", singleLine = true)

        MonoLabel("When")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RuleKinds.TRIGGERS.forEach { (k, label) -> PaperPill(label, kind == k, { kind = k }) }
        }
        if (kind == RuleKinds.ARRIVE || kind == RuleKinds.LEAVE || kind == RuleKinds.STAY) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Any place I named", whereMode == 0, { whereMode = 0 })
                PaperPill("One place", whereMode == 1, { whereMode = 1 })
                PaperPill("A kind of place", whereMode == 2, { whereMode = 2 })
            }
            if (whereMode == 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    places.filter { !it.archived }.forEach { pl -> PaperPill(pl.name, placeId == pl.id, { placeId = pl.id }) }
                }
                if (places.none { !it.archived }) Text("You have not named any place yet.", fontSize = 12.sp, color = p.ink2)
            }
            if (whereMode == 2) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.filter { !it.archived }.forEach { c -> PaperPill(c.name, category == c.id, { category = c.id }) }
                }
            }
        }
        if (kind == RuleKinds.STAY) {
            PaperField(
                value = stayMinutes, onValueChange = { stayMinutes = it.filter(Char::isDigit).take(4) }, label = "Minutes",
                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text("Checked about every 15 minutes, so it can be a little late.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.TIME) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperField(
                    value = atHour, onValueChange = { atHour = it.filter(Char::isDigit).take(2) }, label = "Hour (0 to 23)",
                    modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                PaperField(
                    value = atMinute, onValueChange = { atMinute = it.filter(Char::isDigit).take(2) }, label = "Minute",
                    modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text("Checked about every 15 minutes, so it can be a little late.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.BUTTON) Text("This rule only runs when you press it: in this window, or from the Buttons at the top of the Rules page.", fontSize = 12.sp, color = p.ink2)
        if (kind == RuleKinds.SPEED_OVER || kind == RuleKinds.SPEED_UNDER) {
            PaperField(
                value = tp["kmh"] ?: (if (kind == RuleKinds.SPEED_OVER) "60" else "10"), onValueChange = { tset("kmh", it.filter(Char::isDigit).take(3)) }, label = "Kilometres per hour",
                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text("Needs GPS readings (Places). It speaks at most once in 10 minutes.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.TRIP_START) Text("Needs trips to be recorded in Places.", fontSize = 12.sp, color = p.ink2)
        if (kind == RuleKinds.HABIT_TICKED) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Any habit", tp["habitId"].orEmpty().isEmpty(), { tset("habitId", "") })
                habits.filter { !it.archived }.forEach { h -> PaperPill(h.name, tp["habitId"] == h.id, { tset("habitId", h.id) }) }
            }
            Text("Once a day for each habit, however many times it is ticked.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.WIFI_ON || kind == RuleKinds.WIFI_OFF) {
            PaperField(value = tp["name"].orEmpty(), onValueChange = { tset("name", it) }, label = "Network name (empty = any)", modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Android only shows the network name when the app may use your location. Looked at about every 15 minutes.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.BT_ON || kind == RuleKinds.BT_OFF) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Any device", tp["device"].orEmpty().isEmpty(), { tp = tp + ("device" to "") + ("deviceName" to "") })
                paired.forEach { (n, addr) -> PaperPill(n.ifEmpty { addr }, tp["device"] == addr, { tp = tp + ("device" to addr) + ("deviceName" to n) }) }
            }
            if (paired.isEmpty()) Text("No paired device can be shown (the Nearby devices permission may be off).", fontSize = 12.sp, color = p.ink2)
            Text("Looked at about every 15 minutes, so a short connection can be missed.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.APP_OPEN || kind == RuleKinds.APP_USED) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (kind == RuleKinds.APP_OPEN) PaperPill("Any app", tp["pkg"].orEmpty().isEmpty(), { tp = tp + ("pkg" to "") + ("label" to "") })
                apps.take(60).forEach { a -> PaperPill(a.label.ifEmpty { a.pkg }, tp["pkg"] == a.pkg, { tp = tp + ("pkg" to a.pkg) + ("label" to a.label) }) }
            }
            if (apps.isEmpty()) Text("No app is known yet: switch on Phone usage and let it sync first.", fontSize = 12.sp, color = p.ink2)
            if (kind == RuleKinds.APP_USED) {
                PaperField(
                    value = tp["minutes"] ?: "60", onValueChange = { tset("minutes", it.filter(Char::isDigit).take(4)) }, label = "Minutes in a day",
                    modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            Text("Comes from Phone usage, which is saved about every 15 minutes or less often, so it can be late.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.CALL) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("" to "Any call", "in" to "I take one", "out" to "I make one", "missed" to "I miss one").forEach { (v, label) ->
                    PaperPill(label, tp["type"].orEmpty() == v, { tset("type", v) })
                }
            }
            Text("Needs Calls to be switched on, and is a little late.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.STEPS) {
            PaperField(
                value = tp["steps"] ?: "8000", onValueChange = { tset("steps", it.filter(Char::isDigit).take(6)) }, label = "Steps in a day",
                modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Text("Once a day. Looked at about every 15 minutes.", fontSize = 12.sp, color = p.ink2)
        }
        if (kind == RuleKinds.WAKE || kind == RuleKinds.SLEEP) {
            Text("From your Sleep nights (estimated from when you stop using the phone, or from Health Connect), so it is found some time after it happened.", fontSize = 12.sp, color = p.ink2)
        }

        MonoLabel("Only if")
        ToggleRow("The phone is charging (or not)", chargingOn, { chargingOn = it }, first = true)
        if (chargingOn) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("Charging", chargingState == "on", { chargingState = "on" })
                PaperPill("Not charging", chargingState == "off", { chargingState = "off" })
            }
        }
        ToggleRow("I am at a place (or not)", atOn, { atOn = it })
        if (atOn) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperPill("At", atState == "on", { atState = "on" })
                PaperPill("Not at", atState == "off", { atState = "off" })
                PaperPill("Any place I named", atMode == 0, { atMode = 0 })
                PaperPill("One place", atMode == 1, { atMode = 1 })
                PaperPill("A kind of place", atMode == 2, { atMode = 2 })
            }
            if (atMode == 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    places.filter { !it.archived }.forEach { pl -> PaperPill(pl.name, atPlaceId == pl.id, { atPlaceId = pl.id }) }
                }
            }
            if (atMode == 2) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.filter { !it.archived }.forEach { c -> PaperPill(c.name, atCategory == c.id, { atCategory = c.id }) }
                }
            }
        }
        ToggleRow("A habit is not done yet today", notDoneOn, { notDoneOn = it })
        if (notDoneOn) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                habits.filter { !it.archived }.forEach { h -> PaperPill(h.name, notDoneHabit == h.id, { notDoneHabit = h.id }) }
            }
        }
        ToggleRow("Only on certain days", daysOn, { daysOn = it })
        if (daysOn) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DAY_LABELS.forEachIndexed { i, label ->
                    val d = i + 1
                    PaperPill(label, d in days, { days = if (d in days) days - d else days + d })
                }
            }
        }
        ToggleRow("Only between two times", windowOn, { windowOn = it })
        if (windowOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperField(value = fromText, onValueChange = { fromText = it.take(5) }, label = "From (09:00)", modifier = Modifier.weight(1f), singleLine = true)
                PaperField(value = toText, onValueChange = { toText = it.take(5) }, label = "To (18:00)", modifier = Modifier.weight(1f), singleLine = true)
            }
            Text("A window can run past midnight, for example 22:00 to 06:00.", fontSize = 12.sp, color = p.ink2)
        }
        ToggleRow("One of these is enough (instead of all)", matchAny, { matchAny = it })
        Text("With this on, the rule runs when any one of the conditions you switched on holds.", fontSize = 12.sp, color = p.ink2)

        MonoLabel("Then")
        actions.forEachIndexed { i, a ->
            val shape = Wobble.Small
            Column(
                modifier = Modifier.fillMaxWidth().background(p.surface, shape).border(1.dp, p.line, shape).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(RuleKinds.actionLabel(a.kind), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = p.ink, modifier = Modifier.weight(1f))
                    PaperButton("Remove", { actions.removeAt(i) }, style = PaperButtonStyle.Ghost, small = true)
                }
                when (a.kind) {
                    RuleKinds.TICK_HABIT -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            habits.filter { !it.archived }.forEach { h -> PaperPill(h.name, a.text("habitId") == h.id, { actions[i] = a.with("habitId", h.id) }) }
                        }
                        if (habits.none { !it.archived }) Text("You have no habits yet.", fontSize = 12.sp, color = p.ink2)
                        Text("Yes/no and counted habits can be ticked. Avoid and checklist habits are done by hand.", fontSize = 12.sp, color = p.ink2)
                    }
                    RuleKinds.ENTRY -> {
                        PaperField(value = a.text("title"), onValueChange = { actions[i] = a.with("title", it) }, label = "Name of the entry", modifier = Modifier.fillMaxWidth(), placeholder = "e.g. At {place}", singleLine = true)
                        Text("Under which activity", fontSize = 12.sp, color = p.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            activities.filter { !it.archived }.forEach { c -> PaperPill(c.name, a.text("category") == c.id, { actions[i] = a.with("category", c.id) }) }
                        }
                        PaperField(value = a.text("note"), onValueChange = { actions[i] = a.with("note", it) }, label = "Note (optional)", modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("It is added at the moment it happened, with no end time.", fontSize = 12.sp, color = p.ink2)
                    }
                    RuleKinds.NOTIFY -> {
                        PaperField(value = a.text("title"), onValueChange = { actions[i] = a.with("title", it) }, label = "Title", modifier = Modifier.fillMaxWidth(), placeholder = "e.g. Welcome to {place}", singleLine = true)
                        PaperField(value = a.text("text"), onValueChange = { actions[i] = a.with("text", it) }, label = "Message (optional)", modifier = Modifier.fillMaxWidth())
                    }
                    RuleKinds.START_FOCUS -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PaperField(
                                value = a.text("minutes", "25"), onValueChange = { actions[i] = a.with("minutes", it.filter(Char::isDigit).take(3)) }, label = "Minutes",
                                modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            PaperField(
                                value = a.text("breakMinutes", "5"), onValueChange = { actions[i] = a.with("breakMinutes", it.filter(Char::isDigit).take(2)) }, label = "Break",
                                modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                        PaperField(value = a.text("label"), onValueChange = { actions[i] = a.with("label", it) }, label = "Label (optional)", modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Text("For which habit (optional)", fontSize = 12.sp, color = p.ink2)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PaperPill("None", a.text("habitId").isEmpty(), { actions[i] = a.with("habitId", "") })
                            habits.filter { !it.archived }.forEach { h -> PaperPill(h.name, a.text("habitId") == h.id, { actions[i] = a.with("habitId", h.id) }) }
                        }
                        Text("Does nothing if a focus timer is already running.", fontSize = 12.sp, color = p.ink2)
                    }
                    RuleKinds.MONEY -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PaperPill("Spending", a.int("kind", KIND_EXPENSE) == KIND_EXPENSE, { actions[i] = a.with("kind", KIND_EXPENSE.toString()).with("categoryId", "") })
                            PaperPill("Income", a.int("kind", KIND_EXPENSE) == KIND_INCOME, { actions[i] = a.with("kind", KIND_INCOME.toString()).with("categoryId", "") })
                        }
                        PaperField(value = a.text("name"), onValueChange = { actions[i] = a.with("name", it) }, label = "What it was", modifier = Modifier.fillMaxWidth(), placeholder = "e.g. Bus fare", singleLine = true)
                        PaperField(
                            value = a.text("amount"), onValueChange = { actions[i] = a.with("amount", it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(10)) }, label = "Amount",
                            modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            moneyCategories.filter { it.kind == a.int("kind", KIND_EXPENSE) }.forEach { c -> PaperPill(c.name, a.text("categoryId") == c.id, { actions[i] = a.with("categoryId", c.id) }) }
                        }
                        Text("Without a category, the first one of that kind is used. It is dated the day it happened.", fontSize = 12.sp, color = p.ink2)
                    }
                    RuleKinds.NOTE -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            habits.filter { !it.archived }.forEach { h -> PaperPill(h.name, a.text("habitId") == h.id, { actions[i] = a.with("habitId", h.id) }) }
                        }
                        PaperField(value = a.text("text"), onValueChange = { actions[i] = a.with("text", it) }, label = "Note", modifier = Modifier.fillMaxWidth(), placeholder = "e.g. Left {place} at {time}")
                    }
                    RuleKinds.RUN_RULE -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            allRules.filter { it.id != initial?.id }.forEach { r -> PaperPill(r.name, a.text("ruleId") == r.id, { actions[i] = a.with("ruleId", r.id) }) }
                        }
                        if (allRules.none { it.id != initial?.id }) Text("You have no other rule yet.", fontSize = 12.sp, color = p.ink2)
                        Text("Runs that rule's actions, whatever its own trigger and conditions say. Rules that run each other stop after 3 steps.", fontSize = 12.sp, color = p.ink2)
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            RuleKinds.ACTIONS.forEach { (k, label) -> PaperPill("+ $label", false, { actions.add(RulePart(k)) }) }
        }
        Text("In names and messages, {place}, {time}, {minutes}, {mode}, {km}, {habit}, {app}, {name}, {steps} and {kmh} are filled in where they apply.", fontSize = 12.sp, color = p.ink2)

        PaperField(
            value = cooldown, onValueChange = { cooldown = it.filter(Char::isDigit).take(5) }, label = "Least seconds between two runs",
            modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        if (initial != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperButton("Run now", { vm.runNow(initial.id) }, style = PaperButtonStyle.Ghost, small = true)
                PaperButton("Delete rule", { vm.delete(initial.id); onClose() }, style = PaperButtonStyle.Danger, small = true)
            }
            Text("Run now does its actions straight away, whatever its trigger and conditions say. Save first if you changed something.", fontSize = 12.sp, color = p.ink2)
        }
        error?.let { Text(it, color = p.bad, fontSize = 13.sp) }
    }
}
