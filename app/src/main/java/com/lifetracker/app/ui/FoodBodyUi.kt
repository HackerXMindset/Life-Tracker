package com.lifetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifetracker.app.data.Activity
import com.lifetracker.app.data.BodyMath
import com.lifetracker.app.data.BodyProfile
import com.lifetracker.app.data.FoodSettings
import com.lifetracker.app.data.GoalKind
import com.lifetracker.app.data.Sex
import com.lifetracker.app.ui.theme.LocalPaper
import com.lifetracker.app.ui.theme.PaperButton
import com.lifetracker.app.ui.theme.PaperButtonStyle
import com.lifetracker.app.ui.theme.PaperCard
import com.lifetracker.app.ui.theme.PaperField
import com.lifetracker.app.ui.theme.PaperPill
import com.lifetracker.app.ui.theme.PaperSegmented
import com.lifetracker.app.ui.theme.PaperToggle
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/*
 * Body and goals, in the way of OpenNutriTracker: how active you are, what you want (lose, keep or gain weight), your BMI, the
 * calories that fit that, a target weight and your weight history. Everything is an estimate for an adult.
 */

@Composable
fun BodyWindow(x: FoodExtrasViewModel, vm: FoodViewModel, onClose: () -> Unit) {
    val p = LocalPaper.current
    val context = LocalContext.current
    val today = LocalDate.now()
    val weights by x.weights.collectAsState()
    val goal by vm.goal.collectAsState()
    val saved = remember { FoodSettings.bodyProfile(context) }
    val latest = weights.lastOrNull()

    var sex by remember { mutableStateOf(saved.sex) }
    var activity by remember { mutableStateOf(saved.activity) }
    var kind by remember { mutableStateOf(saved.goal) }
    var pace by remember { mutableStateOf(saved.paceKg) }
    var ageText by remember { mutableStateOf(BodyMath.age(saved.birthYear, today)?.toString() ?: "") }
    var heightText by remember { mutableStateOf(if (saved.heightCm > 0) foodNum(saved.heightCm) else "") }
    var weightText by remember { mutableStateOf(latest?.let { foodNum(it.kg) } ?: "") }
    var targetText by remember { mutableStateOf(FoodSettings.weightGoalKg(context).let { if (it > 0) foodNum(it) else "" }) }
    var note by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(FoodSettings.autoGoal(context)) }

    val age = foodParse(ageText)?.toInt()?.takeIf { it in 10..110 }
    val heightCm = foodParse(heightText)?.takeIf { it in 100.0..250.0 } ?: 0.0
    val kg = foodParse(weightText)?.takeIf { it > 0 } ?: latest?.kg ?: 0.0
    val target = foodParse(targetText)?.takeIf { it > 0 } ?: 0.0
    val profile = BodyProfile(sex, if (age != null) today.year - age else 0, heightCm, activity, kind, pace, saved.since)
    val plan = BodyMath.plan(profile, kg, today)
    val bmi = BodyMath.bmi(kg, heightCm)

    fun save() {
        val changedGoal = kind != saved.goal || target != FoodSettings.weightGoalKg(context)
        FoodSettings.setBodyProfile(context, profile.copy(since = if (changedGoal || saved.since.isBlank()) today.toString() else saved.since))
        FoodSettings.setWeightGoal(context, if (kind == GoalKind.MAINTAIN) 0.0 else target)
        if (kg > 0 && (latest == null || Math.abs(latest.kg - kg) > 1e-9)) x.setWeight(today, kg)
        FoodSettings.setAutoGoal(context, auto)
        // With "keep my goal up to date" on, the daily goal is set from this plan right now and follows it from then on.
        if (auto && plan != null) {
            val (protein, carbs, fat) = BodyMath.macros(plan.target, kg, goal)
            vm.setGoalFor(today, plan.target, protein, carbs, fat)
        }
    }

    EditWindow(
        title = "Body and goals",
        sub = "Your BMI, the calories that fit your goal, and your weight over time",
        onClose = onClose,
        onSave = { save(); onClose() },
    ) {
        MonoLabel("About you")
        PaperSegmented(Sex.entries.map { it.label }, sex?.ordinal ?: -1, { sex = Sex.entries[it] })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PaperField(ageText, { ageText = it.filter(Char::isDigit).take(3) }, "Age", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(heightText, { heightText = it }, "Height cm", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
            PaperField(weightText, { weightText = it }, "Weight kg", Modifier.weight(1f), singleLine = true, keyboardOptions = DecimalKeys)
        }

        if (bmi != null) BmiCard(bmi, heightCm)

        MonoLabel("How active are you")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Activity.entries.forEach { a -> ChoiceRow(a.label, a.hint, a == activity) { activity = a } }
        }

        MonoLabel("Your goal")
        PaperSegmented(GoalKind.entries.map { it.label }, kind.ordinal, { kind = GoalKind.entries[it] })
        if (kind != GoalKind.MAINTAIN) {
            Text("How fast", fontSize = 12.sp, color = p.ink2)
            ChipFlow {
                BodyMath.PACES.forEach { v -> PaperPill(foodNum(v) + " kg a week", Math.abs(pace - v) < 1e-9, { pace = v }) }
            }
            PaperField(targetText, { targetText = it }, "Target weight (kg)", singleLine = true, keyboardOptions = DecimalKeys)
        }

        ResultCard(plan, kind, auto, { auto = it }) {
            if (plan != null) {
                val (protein, carbs, fat) = BodyMath.macros(plan.target, kg, goal)
                vm.setGoalFor(today, plan.target, protein, carbs, fat)
                save()
                note = "Your daily goal from today is " + kc(plan.target) + " kcal."
            }
        }
        note?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.accent) }

        if (kind != GoalKind.MAINTAIN && target > 0 && latest != null) {
            ProgressCard(kind, BodyMath.startWeight(weights, saved.since) ?: latest.kg, kg, target, pace, today)
        }

        MonoLabel("Weight history")
        WeightHistory(x, weights, if (kind == GoalKind.MAINTAIN) 0.0 else target)
    }
}

@Composable
private fun BmiCard(bmi: Double, heightCm: Double) {
    val p = LocalPaper.current
    val range = BodyMath.healthyKg(heightCm)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, shadow = false) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.US, "%.1f", bmi), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            Text("  BMI", fontSize = 13.sp, color = p.ink2, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(BodyMath.bmiLabel(bmi), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (bmi in 18.5..24.99) p.accent else p.ink)
        Spacer(Modifier.height(10.dp))
        // A strip from 15 to 40, with a mark where you are.
        Box(Modifier.fillMaxWidth().height(10.dp).background(p.surface2, RoundedCornerShape(5.dp))) {
            val from = ((18.5 - 15) / 25).toFloat()
            val to = ((24.9 - 15) / 25).toFloat()
            Row(Modifier.fillMaxWidth().height(10.dp)) {
                Spacer(Modifier.weight(from))
                Box(Modifier.weight(to - from).height(10.dp).background(p.accent.copy(alpha = 0.35f)))
                Spacer(Modifier.weight(1f - to))
            }
            val at = ((bmi - 15) / 25).coerceIn(0.0, 1.0).toFloat()
            Row(Modifier.fillMaxWidth().height(10.dp)) {
                Spacer(Modifier.weight(at.coerceAtLeast(0.001f)))
                Box(Modifier.size(width = 4.dp, height = 10.dp).background(p.ink, RoundedCornerShape(2.dp)))
                Spacer(Modifier.weight((1f - at).coerceAtLeast(0.001f)))
            }
        }
        if (range != null) {
            Text(
                "Healthy range for your height: " + kc(range.start) + " to " + kc(range.endInclusive) + " kg. BMI is a rough guide: it does not see muscle or build.",
                fontSize = 12.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ChoiceRow(title: String, hint: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp, shadow = false, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(20.dp).border(1.5.dp, p.pen, CircleShape).padding(4.dp)) {
                if (on) Box(Modifier.fillMaxWidth().height(12.dp).background(p.ink, CircleShape))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = p.ink)
                Text(hint, fontSize = 12.sp, color = p.ink2)
            }
        }
    }
}

@Composable
private fun ResultCard(plan: BodyMath.Plan?, kind: GoalKind, auto: Boolean, onAuto: (Boolean) -> Unit, onUse: () -> Unit) {
    val p = LocalPaper.current
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, shadow = false) {
        if (plan == null) {
            Text("Your calories", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
            Spacer(Modifier.height(4.dp))
            Text("Add your sex, age, height and weight above and the calories that fit your goal appear here.", fontSize = 13.sp, color = p.ink2)
        } else {
            Text("To " + kind.label.lowercase().let { if (kind == GoalKind.MAINTAIN) "keep your weight" else it }, fontSize = 12.sp, color = p.ink2)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(kc(plan.target), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = p.ink)
                Text(" kcal a day", fontSize = 13.sp, color = p.ink2, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text("Keeping your weight: " + kc(plan.maintenance) + " kcal. At rest you burn about " + kc(plan.bmr) + ".", fontSize = 12.5.sp, color = p.ink2)
            if (plan.floored) Text("Raised to the lowest amount that is safe to eat. Go slower to stay above it.", fontSize = 12.5.sp, color = p.bad, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Keep my daily goal up to date", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text("Saving sets it, and a new weigh-in updates it. A goal you type by hand turns this off.", fontSize = 12.sp, color = p.ink2)
                }
                PaperToggle(auto, onAuto)
            }
            if (!auto) {
                Spacer(Modifier.height(8.dp))
                PaperButton("Use as my daily goal once", onUse, small = true)
            }
            Text("An estimate for adults, not medical advice. Your body is the best check: watch your weight over a few weeks and adjust.", fontSize = 11.5.sp, color = p.ink2, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ProgressCard(kind: GoalKind, start: Double, now: Double, target: Double, pace: Double, today: LocalDate) {
    val p = LocalPaper.current
    val share = BodyMath.progress(start, now, target)
    val weeks = BodyMath.weeksToTarget(now, target, kind, pace)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, shadow = false) {
        Kicker("Towards " + foodNum(target) + " kg")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Start " + foodNum(start), fontSize = 12.5.sp, color = p.ink2)
            Text("Now " + foodNum(now), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = p.ink)
            Text("Target " + foodNum(target), fontSize = 12.5.sp, color = p.ink2)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).background(p.surface2, RoundedCornerShape(5.dp))) {
            if (share > 0.005) Box(Modifier.fillMaxWidth(share.toFloat()).height(10.dp).background(p.accent, RoundedCornerShape(5.dp)))
        }
        Spacer(Modifier.height(8.dp))
        val left = Math.abs(now - target)
        Text(
            when {
                weeks != null -> foodNum(Math.round(left * 10) / 10.0) + " kg to go. At " + foodNum(pace) + " kg a week that is about " + Math.ceil(weeks).toInt() + " weeks, around " +
                    BodyMath.eta(today, weeks).let { it.dayOfMonth.toString() + " " + it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + it.year } + "."
                share >= 1.0 -> "You have reached your target."
                else -> "Your target points the other way from your goal. Check the goal and the target weight."
            },
            fontSize = 13.sp, color = p.ink,
        )
    }
}

@Composable
private fun WeightHistory(x: FoodExtrasViewModel, weights: List<com.lifetracker.app.data.WeightEntity>, target: Double) {
    val p = LocalPaper.current
    if (weights.isEmpty()) {
        Text("No weigh-ins yet. Type your weight above and save, or weigh in from the Body weight card.", fontSize = 13.sp, color = p.ink2)
        return
    }
    val recent = weights.takeLast(30)
    PaperCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, shadow = false) {
        if (recent.size >= 2) {
            ValueLineChart(
                values = recent.map { it.kg },
                labels = recent.mapIndexed { i, w -> if (i % Math.ceil(recent.size / 5.0).toInt() == 0) LocalDate.parse(w.date).let { "${it.dayOfMonth}/${it.monthValue}" } else "" },
                color = WATER_COLOR, goal = if (target > 0) target else null, fromZero = false, height = 140.dp,
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            weights.takeLast(8).reversed().forEach { w ->
                val d = LocalDate.parse(w.date)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + d.dayOfMonth + " " + d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        fontSize = 13.sp, color = p.ink, modifier = Modifier.weight(1f),
                    )
                    Text(foodNum(w.kg) + " kg", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.ink)
                    Text(
                        "Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.bad,
                        modifier = Modifier.clickable(role = Role.Button) { x.deleteWeight(w.date) }.padding(start = 14.dp, top = 6.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}
