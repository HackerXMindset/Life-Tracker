package com.lifetracker.app.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitEditorTest {
    private val today = LocalDate.of(2026, 10, 14)
    private val now = LocalDateTime.of(2026, 10, 14, 9, 30)

    private fun build(form: HabitForm, existing: HabitEntity? = null, id: String = "h1") =
        HabitEditor.build(form, existing, id, 3, today, now)

    @Test
    fun aNewYesNoHabitGetsStreaksDefaults() {
        val h = build(HabitForm(name = "  Read  ", category = "Study", icon = "📚"))
        assertEquals("Read", h.name)
        assertEquals("📚", h.icon)
        assertEquals(HabitStats.KIND_YES_NO, h.kind)
        assertEquals(1.0, h.target, 0.0)
        assertEquals("2026-10-14", h.startedOn)
        assertEquals(3, h.sortOrder)
        assertFalse(h.archived)
        val o = JSONObject(h.raw)
        assertEquals("h1", o.getString("id"))
        assertEquals("2026-10-14T09:30", o.getString("createdAt"))
        assertEquals(0, o.getInt("interval"))
        assertEquals(25, o.getInt("focusMinutes"))
        assertEquals(3, o.getInt("order"))
        assertEquals(0, o.getJSONArray("reminders").length())
    }

    @Test
    fun anEmptyIconBecomesStreaksDefaultIcon() {
        assertEquals("target", build(HabitForm(name = "x")).icon)
    }

    @Test
    fun aCountedHabitKeepsItsGoalUnitAndStep() {
        val h = build(HabitForm(name = "Water", kind = HabitStats.KIND_COUNTED, target = 8.0, unitLabel = " glasses ", step = 2.0))
        assertEquals(8.0, h.target, 0.0)
        assertEquals("glasses", h.unitLabel)
        assertEquals(2.0, h.step, 0.0)
        val o = JSONObject(h.raw)
        assertEquals(8.0, o.getDouble("numberOfCompletionsPerDay"), 0.0)
        assertEquals(2.0, o.getDouble("incrementAmount"), 0.0)
        assertEquals(2, o.getInt("kind"))
    }

    @Test
    fun theScheduleIsWrittenInStreaksFields() {
        val plan = HabitPlan(
            interval = HabitPlan.EVERY_X_DAYS, every = 2, unit = HabitPlan.UNIT_WEEKS,
            scheduleStart = LocalDate.of(2026, 10, 4),
        )
        val h = build(HabitForm(name = "x", plan = plan))
        val o = JSONObject(h.raw)
        assertEquals(4, o.getInt("interval"))
        assertEquals(2, o.getInt("scheduleEvery"))
        assertEquals(1, o.getInt("scheduleUnit"))
        assertEquals("04-10-2026", o.getString("scheduleStart"))
        assertEquals(plan.copy(), HabitPlan.fromRaw(h.raw).copy(restDays = emptyList(), vacations = emptyList()))
    }

    @Test
    fun weekdaysAreOnlyKeptForASomeDaysHabit() {
        val some = build(HabitForm(name = "x", plan = HabitPlan(interval = HabitPlan.WEEKDAYS, weekdays = listOf(5, 1, 3))))
        assertEquals(listOf(1, 3, 5), HabitPlan.fromRaw(some.raw).weekdays)
        val daily = build(HabitForm(name = "x", plan = HabitPlan(interval = HabitPlan.DAILY, weekdays = listOf(1, 3))))
        assertEquals(emptyList<Int>(), HabitPlan.fromRaw(daily.raw).weekdays)
        assertNull(JSONObject(daily.raw).optString("scheduleStart").takeIf { it.isNotEmpty() })
    }

    @Test
    fun editingKeepsEverythingTheFormDoesNotAsk() {
        val raw = """{"id":"h1","name":"Old","icon":"book","category":"","color":4278190080,"order":7,
            "reminders":[{"hour":8,"minute":0}],"focusMinutes":50,"restDays":[7],
            "vacations":[{"start":"2026-10-01T00:00:00.000"}],"missReasons":{"13-10-2026":"Sick"},
            "scheduleStart":"01-10-2026","futureField":"keep me"}"""
        val old = HabitEntity("h1", "Old", "book", "", 0xFF000000, 0, 0, "", 1.0, 1.0, false, -1, 0, "2026-09-01", false, 7, raw)
        val h = build(HabitForm(name = "New", plan = HabitPlan(interval = HabitPlan.WEEKLY, timesPerPeriod = 3)), old)
        val o = JSONObject(h.raw)
        assertEquals("New", h.name)
        assertEquals("2026-09-01", h.startedOn)
        assertEquals(7, h.sortOrder)
        assertEquals(50, o.getInt("focusMinutes"))
        assertEquals(1, o.getJSONArray("reminders").length())
        assertEquals("Sick", o.getJSONObject("missReasons").getString("13-10-2026"))
        assertEquals("keep me", o.getString("futureField"))
        assertEquals(listOf(7), HabitPlan.fromRaw(h.raw).restDays)
        assertEquals(1, HabitPlan.fromRaw(h.raw).vacations.size)
        assertFalse(o.has("scheduleStart"))
        assertEquals(1, o.getInt("interval"))
        assertEquals(3, o.getInt("targetFrequency"))
    }

    @Test
    fun changingACountedHabitToYesNoDropsTheCountedGoalButAYesNoHabitKeepsItsOwn() {
        val counted = build(HabitForm(name = "Pushups", kind = HabitStats.KIND_COUNTED, target = 20.0, step = 5.0, unitLabel = "reps"))
        val h = build(HabitForm(name = "Pushups", kind = HabitStats.KIND_YES_NO), counted)
        assertEquals(1.0, h.target, 0.0)
        assertEquals(1.0, h.step, 0.0)
        assertEquals("", h.unitLabel)
        assertFalse(h.anyAmount)

        val twice = build(HabitForm(name = "Meds"), null).copy(target = 2.0)
        val edited = build(HabitForm(name = "Meds 2"), twice)
        assertEquals(2.0, edited.target, 0.0)
    }

    @Test
    fun theFormIsReadBackFromAStoredHabit() {
        val form = HabitForm(
            name = "Water", icon = "💧", color = HabitEditor.PALETTE[2], category = "Body",
            kind = HabitStats.KIND_COUNTED, target = 8.0, unitLabel = "glasses", step = 1.0, anyAmount = false,
            plan = HabitPlan(interval = HabitPlan.MONTHLY, timesPerPeriod = 10), description = "Stay hydrated",
        )
        val back = HabitEditor.formFrom(build(form))
        assertEquals(form.copy(plan = back.plan), back)
        assertEquals(HabitPlan.MONTHLY, back.plan.interval)
        assertEquals(10, back.plan.timesPerPeriod)
    }

    @Test
    fun aNamedIconIsKeptInTheFormButTheDefaultIsNot() {
        val named = HabitEntity("h", "x", "book", "", 0, 0, 0, "", 1.0, 1.0, false, -1, 0, "2026-10-01", false, 0, "{}")
        assertEquals("book", HabitEditor.formFrom(named).icon)
        val none = named.copy(icon = "target")
        assertEquals("", HabitEditor.formFrom(none).icon)
    }

    @Test
    fun validationExplainsWhatIsWrong() {
        assertNotNull(HabitEditor.validate(HabitForm(name = " ")))
        assertNull(HabitEditor.validate(HabitForm(name = "x")))
        // A goal of 0 means no goal, which is allowed; a goal below 0 is not.
        assertNull(HabitEditor.validate(HabitForm(name = "x", kind = HabitStats.KIND_COUNTED, target = 0.0)))
        assertNotNull(HabitEditor.validate(HabitForm(name = "x", kind = HabitStats.KIND_COUNTED, target = -1.0)))
        assertNull(HabitEditor.validate(HabitForm(name = "x", kind = HabitStats.KIND_COUNTED, target = 0.0, anyAmount = true)))
        assertNotNull(HabitEditor.validate(HabitForm(name = "x", kind = HabitStats.KIND_COUNTED, step = 0.0)))
        assertNotNull(HabitEditor.validate(HabitForm(name = "x", plan = HabitPlan(interval = HabitPlan.WEEKDAYS))))
        assertNotNull(HabitEditor.validate(HabitForm(name = "x", plan = HabitPlan(interval = HabitPlan.WEEKLY, timesPerPeriod = 8))))
        assertNotNull(HabitEditor.validate(HabitForm(name = "x", plan = HabitPlan(interval = HabitPlan.EVERY_X_DAYS, every = 0))))
        assertNull(HabitEditor.validate(HabitForm(name = "x", plan = HabitPlan(interval = HabitPlan.MONTHLY, timesPerPeriod = 31))))
    }

    @Test
    fun archivingHidesAndShowingBringsItBackWithoutTouchingTheRest() {
        val h = build(HabitForm(name = "x"))
        val hidden = HabitEditor.withArchived(h, true, now)
        assertTrue(hidden.archived)
        assertEquals("2026-10-14T09:30", JSONObject(hidden.raw).getString("archivedAt"))
        val back = HabitEditor.withArchived(hidden, false, now)
        assertFalse(back.archived)
        assertFalse(JSONObject(back.raw).has("archivedAt"))
        assertEquals(h.copy(raw = ""), back.copy(raw = ""))
    }

    @Test
    fun theScheduleIsDescribedInWords() {
        assertEquals("Every day", HabitEditor.describe(HabitPlan()))
        assertEquals("Mon, Wed, Fri", HabitEditor.describe(HabitPlan(interval = HabitPlan.WEEKDAYS, weekdays = listOf(5, 1, 3))))
        assertEquals("3 days a week", HabitEditor.describe(HabitPlan(interval = HabitPlan.WEEKLY, timesPerPeriod = 3)))
        assertEquals("1 day a month", HabitEditor.describe(HabitPlan(interval = HabitPlan.MONTHLY, timesPerPeriod = 1)))
        assertEquals("Every 2 weeks", HabitEditor.describe(HabitPlan(interval = HabitPlan.EVERY_X_DAYS, every = 2, unit = HabitPlan.UNIT_WEEKS)))
        assertEquals("Every month", HabitEditor.describe(HabitPlan(interval = HabitPlan.EVERY_X_DAYS, every = 1, unit = HabitPlan.UNIT_MONTHS)))
    }

    @Test
    fun newIdsAreDifferent() {
        assertTrue(HabitEditor.newId() != HabitEditor.newId())
    }

    private fun plain(raw: String = "{}") =
        HabitEntity("h", "x", "target", "", 0, 0, 0, "", 1.0, 1.0, false, -1, 0, "2026-10-01", false, 0, raw)

    @Test
    fun aMissReasonIsStoredByStreaksDayKeyAndKeepsTheRest() {
        val h = plain("""{"id":"h","name":"x","focusMinutes":50}""")
        val with = HabitEditor.withMissReason(h, LocalDate.of(2026, 10, 13), "  Sick ")
        assertEquals("Sick", JSONObject(with.raw).getJSONObject("missReasons").getString("13-10-2026"))
        assertEquals(50, JSONObject(with.raw).getInt("focusMinutes"))
        assertEquals(mapOf(LocalDate.of(2026, 10, 13) to "Sick"), HabitEditor.missReasons(with.raw))
    }

    @Test
    fun aMissReasonCanBeChangedAndRemovedAndTheLastRemovalDropsTheField() {
        val a = HabitEditor.withMissReason(plain(), LocalDate.of(2026, 10, 12), "Busy")
        val b = HabitEditor.withMissReason(a, LocalDate.of(2026, 10, 13), "Tired")
        val c = HabitEditor.withMissReason(b, LocalDate.of(2026, 10, 12), "Forgot")
        assertEquals("Forgot", HabitEditor.missReasons(c.raw)[LocalDate.of(2026, 10, 12)])
        assertEquals("Tired", HabitEditor.missReasons(c.raw)[LocalDate.of(2026, 10, 13)])
        val d = HabitEditor.withMissReason(HabitEditor.withMissReason(c, LocalDate.of(2026, 10, 12), null), LocalDate.of(2026, 10, 13), " ")
        assertFalse(JSONObject(d.raw).has("missReasons"))
    }

    @Test
    fun reasonOptionsAreThePresetsThenYourOwnWordsOnce() {
        val a = HabitEditor.withMissReason(plain(), LocalDate.of(2026, 10, 12), "Exam day")
        val b = HabitEditor.withMissReason(a, LocalDate.of(2026, 10, 13), "Exam day")
        val c = HabitEditor.withMissReason(b, LocalDate.of(2026, 10, 11), "Sick")
        assertEquals(HabitEditor.MISS_PRESETS + "Exam day", HabitEditor.reasonOptions(c))
    }

    @Test
    fun aDayWithAnUnreadableKeyIsSkipped() {
        val raw = """{"missReasons":{"bad":"x","13-10-2026":"Sick"}}"""
        assertEquals(1, HabitEditor.missReasons(raw).size)
    }
}
