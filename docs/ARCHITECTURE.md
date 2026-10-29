# How the app is built

> [!NOTE]
> Moved word for word from the old README. Files marked (planned) did not exist when this was written.

## How the app is organised

The app has three layers. Each file belongs to exactly one of them, which makes the code easy to find and safe to change.

```
   What you see            What decides                What remembers
  ┌──────────────┐       ┌────────────────┐        ┌──────────────────┐
  │   Screens    │ ◄───► │   ViewModels   │ ◄────► │     Database     │
  │ (ui/*.kt)    │       │ (*ViewModel.kt)│        │ (data/*.kt)      │
  └──────────────┘       └────────────────┘        └──────────────────┘
   Timeline, Log sheet    Which day is selected,    Entries saved in
   Food, Habits, ...      adding, deleting          SQLite on the phone
```

- **Screens** only draw things and report taps.
- **ViewModels** hold the current state and talk to the database.
- **The database** is the only place that stores anything.

---

## The file architecture

Files marked **(planned)** do not exist yet. They show where each upcoming step will live.

```
life-tracker/
│
├── README.md                          This document
├── CHANGELOG.md                       What's new, in plain words; shown on each release page
├── settings.gradle.kts                Names the project and its one module
├── build.gradle.kts                   Versions of the build plugins
├── gradle.properties                  Build settings
├── .gitignore                         Files kept out of the repository
│
├── .github/workflows/
│   └── build.yml                      The cloud build: every push becomes an APK, and a release page
├── .github/scripts/
│   └── release-notes.sh               Writes the release page text from CHANGELOG.md
│
├── keystore/
│   └── lifetracker.p12                Fixed signing key, so updates keep your data
│
└── app/
    ├── build.gradle.kts               App settings, version number, libraries
    ├── proguard-rules.pro             Code-shrinking rules for release builds
    │
    ├── src/test/                      Tests that run in the cloud build
    │   ├── .../data/BackupCodecTest.kt   A backup reads back exactly as written
    │   ├── .../data/HabitStatsTest.kt    Streak and clean-day arithmetic
    │   ├── .../data/streak/StreakImportTest.kt  Streak backup parsing and focus-session splitting
    │   ├── .../data/FoodStatsTest.kt     Calorie, macro and goal arithmetic
    │   ├── .../data/ActivityStatsTest.kt Activity time and goal arithmetic
    │   ├── .../data/MoneyStatsTest.kt    Money totals, formatting and monthly-item rules
    │   ├── .../data/CallStatsTest.kt     Call wording, totals and people
    │   ├── .../data/UsageSessionsTest.kt App events into sessions, and a day's blocks and totals
    │   └── .../data/ont/OntImportTest.kt OpenNutriTracker export parsing
    │
    └── src/main/
        ├── AndroidManifest.xml        The app's identity card for Android
        │
        ├── java/com/lifetracker/app/
        │   ├── MainActivity.kt        Entry point: starts the theme and the app
        │   ├── LifeTrackerApplication.kt  Listens for plug and unplug while the app is running
        │   ├── PermissionsRationaleActivity.kt  What Health Connect shows when it asks why steps and sleep are wanted
        │   │
        │   ├── data/                  ── The memory layer ──
        │   │   ├── Database.kt        Entries table, the database itself and its upgrade steps
        │   │   ├── Tables.kt          Habits, completions, to-dos, notes and import records
        │   │   ├── HabitStats.kt      Streak arithmetic
        │   │   ├── HabitRules.kt      Streak's schedules, streaks, goals, day colours (Habits H1)
        │   │   ├── HabitCalendar.kt   Week, month and year grids, Today ring maths (H2)
        │   │   ├── HabitPauses.kt     Pause (holiday), rest days and pausing single days, kept in the habit's record (H9)
        │   │   ├── HabitOrder.kt      Moving a habit up or down, the Today filter (H11)
        │   │   ├── FocusTimer.kt      The focus timer's rules: countdown, stopwatch, Pomodoro, pause, +1 min, midnight splitting (H13a)
        │   │   ├── FocusSessions.kt   The sessions table, and what a finished session does for its habit (H13b)
        │   │   ├── FocusEngine.kt     The running session, its banner (foreground service), end alarm and buttons (H13c)
        │   │   ├── HabitReminders.kt  A reminder, kept in the habit's record, and when it rings next (H12a)
        │   │   ├── HabitReminderService.kt  The alarms, the notification with Done and Snooze, setting them again after a restart (H12b, H12c)
        │   │   ├── HabitSteps.kt      Checklist habits: steps, which are ticked, how many are needed (H8)
        │   │   ├── HabitTicks.kt      Check times (marks) of a day: add, undo, change a time, last check (H7)
        │   │   ├── HabitsSettings.kt  Which calendar, card size, category
        │   │   ├── HabitEditor.kt     Makes and changes habits in Streak's record format (H3a)
        │   │   ├── StreakLink.kt      Opens the Streak app on a habit (H4)
        │   │   ├── HabitSyncState.kt  Remembers which habits and notes changed here, this app's device name, the automatic-send switch (H5b, H5d)
        │   │   ├── StreakSendWorker.kt  Sends the changes to Streak's folder in the background (H5d)
        │   │   ├── HabitNotes.kt      Notes on habits: sorting, filtering, new and edited notes (H3c)
        │   │   ├── ActivityTypes.kt   Your activities (names, colours, goals) and the built-in ones
        │   │   ├── ActivityStats.kt   Time per activity and goal progress
        │   │   ├── UsageTables.kt     App sessions and what you told the app about each app
        │   │   ├── UsageSessions.kt   Turns Android's app events into sessions, and sessions into a day
        │   │   ├── DaySegments.kt     Flattens a day into non-overlapping 24-hour stretches
        │   │   ├── UsageCollector.kt  Reads Android's usage history (needs Usage access)
        │   │   ├── UsageSyncWorker.kt Copies that history every few hours in the background
        │   │   ├── UsageSettings.kt   Timeline and sync settings for phone usage
        │   │   ├── CallTables.kt      Your phone calls
        │   │   ├── CallStats.kt       Call wording, totals and people
        │   │   ├── CallsCollector.kt  Copies the call log (needs Call logs permission)
        │   │   ├── CallsSettings.kt   Timeline setting for calls
        │   │   ├── ChargeTables.kt    Charging sessions
        │   │   ├── ChargeStats.kt     Charging arithmetic, session rules and the learned guess
        │   │   ├── ChargeTracker.kt   Reads the battery and updates sessions
        │   │   ├── ChargeNotifier.kt  The plug-in banner and its buttons, plug/unplug receiver
        │   │   ├── ChargeWorker.kt    15 minute check that runs only while charging
        │   │   ├── ChargeSettings.kt  Charging settings
        │   │   ├── StepSleepTables.kt Steps per day and sleep per night
        │   │   ├── StepSleepStats.kt  Step and sleep rules, and the sleep estimate
        │   │   ├── HealthConnectSync.kt  Copies steps and sleep from Health Connect
        │   │   ├── StepCounterSync.kt Fallback: the phone's own step counter
        │   │   ├── HealthSync.kt      Runs all of the above and the sleep estimate
        │   │   ├── HealthSettings.kt  Steps and sleep settings
        │   │   ├── PlaceTables.kt     Places and visits
        │   │   ├── PlaceStats.kt      Place rules: distance, visits, stops, review groups, totals
        │   │   ├── PlaceEngine.kt     Turns position readings into stays, visits and stops
        │   │   ├── PlaceWatchService.kt  The position readings (foreground service, stops by itself)
        │   │   ├── TripTables.kt      Trips and their position readings
        │   │   ├── TripRules.kt       Trip distance and speed, and the guess at how you travelled
        │   │   ├── RawTables.kt       Every position reading
        │   │   ├── FixCleaning.kt     Removes bad readings, finds stays with hindsight, reading statistics
        │   │   ├── MotionSensor.kt    Still or moving from the accelerometer (pure classifier + Android listener)
        │   │   ├── StayConfidence.kt  How sure the app is of a visit (0 to 100), from its readings
        │   │   ├── Gpx.kt             Writes and reads GPX tracks and CSV of the readings
        │   │   ├── PlaceCleanup.kt    Tidies visits and trips from the stored readings; the 15 minute check's rule
        │   │   ├── KeepAlive.kt       Opens the phone's battery screens for the app
        │   │   ├── AppLog.kt          The app's own diary: writes one line per event to a daily text file and keeps the recent ones for the Live logs page
        │   │   ├── LogFormat.kt       Pure part of the diary: line to text and back, filters, tags, which old days to remove (tested)
        │   │   ├── PlaceLookup.kt     Suggests a name for a stop from OpenStreetMap
        │   │   ├── PlacesTracker.kt   Hands places to Android and feeds Android's messages to the engine
        │   │   ├── PlacesReceivers.kt What Android calls: geofence, still/moving, restart
        │   │   ├── PlacesWorker.kt    6 hourly refresh of the geofences
        │   │   ├── PlacesSettings.kt  Places settings and the stop in progress
│   │   ├── DateSpan.kt        Whole-day ranges and the quick choices for them
        │   │   ├── FoodTables.kt      Meals, daily food goals and water
        │   │   ├── FoodStats.kt       Calorie and macro arithmetic
        │   │   ├── Backup.kt          The backup file format (JSON) and how it is read back
        │   │   ├── BackupManager.kt   Writing, listing and restoring backups, daily auto backup
        │   │   ├── DataSources.kt     The three chosen folders, remembered between runs
        │   │   ├── SourceScanner.kt   Finds the newest Streak and OpenNutriTracker file
        │   │   ├── streak/
        │   │   │   ├── StreakModels.kt   What a Streak backup contains
        │   │   │   ├── StreakParser.kt   Reads Streak's backup format
        │   │   │   ├── StreakBackupWriter.kt  Builds a Streak-format backup file from your changes (H5a)
        │   │   │   ├── StreakFolderWriter.kt  Writes that file into Streak's folder, keeps the newest three (H5c)
        │   │   │   ├── FocusConverter.kt Turns focus sessions into Timeline entries
        │   │   │   └── StreakImporter.kt Finds, reads and merges the newest backup
        │   │   ├── ont/
        │   │   │   ├── OntParser.kt      Reads an OpenNutriTracker export
        │   │   │   ├── OntConverter.kt   Meals and activities into this app's form
        │   │   │   └── OntImporter.kt    Finds, reads and merges the newest export
        │   │   ├── MoneyTables.kt     Money categories, saved items (monthly, often) and entries
        │   │   ├── MoneyStats.kt      Money totals, amount formatting, and the monthly-item planner
        │   │   ├── MoneyPoster.kt     Adds the monthly items that have come due
        │   │   └── MoneySettings.kt   The currency symbol
        │   │
        │   └── ui/                    ── The screen layer ──
        │       ├── LifeTrackerApp.kt  The frame: bottom tab bar and tab switching
        │       ├── TimelineScreen.kt  Timeline: day strip, the three day numbers, entries in order
        │       ├── TimelineViewModel.kt  Selected day, adding and deleting entries
        │       ├── LogSheet.kt        The "Log something" pop-up
        │       ├── DataSourcesScreen.kt     Folders, Back up now and Restore
        │       ├── DataSourcesViewModel.kt  What that screen shows and does
        │       ├── Model.kt           Colour palette and goal wording for activities
        │       ├── CallsScreen.kt          Calls: allow access, totals, people, Timeline option
        │       ├── CallsViewModel.kt       What that screen shows and does
        │       ├── ChargingScreen.kt       Charging: sessions, banner, settings
        │       ├── ChargingViewModel.kt    What that screen shows and does
        │       ├── StepsSleepScreen.kt     Steps and sleep: Health Connect, days, nights, fixes
        │       ├── StepsSleepViewModel.kt  What that screen shows and does
        │       ├── PlacesScreen.kt         Places: permissions, review list, your places, visits
        │       ├── PlacesViewModel.kt      What that screen shows and does
        │       ├── Journeys.kt             The stay and journey cards of the Places list
        │       ├── RangePicker.kt          Quick choices plus From and To dates, shared by Calls and Phone usage
        │       ├── PhoneUsageScreen.kt     Phone usage: allow access, totals per app, links, Timeline options
        │       ├── PhoneUsageViewModel.kt  What that screen shows and does
        │       ├── ActivitiesScreen.kt     Activities settings: list and colours
        │       ├── ActivitiesViewModel.kt  Saving and hiding activities
        │       ├── ActivityEditor.kt       The pop-up for making or editing an activity
        │       ├── Format.kt          Turns minutes into "2h 05m" and "8:15 AM"
        │       ├── Components.kt      Shared pieces such as the screen header
        │       ├── theme/
        │       │   └── Theme.kt       Light and dark colours
        │       ├── HabitsScreen.kt    Habits tab: cards, streaks, 14-day rows
        │       ├── FocusPage.kt       The Focus page: set-up and the running timer (H13d)
        │       ├── HabitReminderEditor.kt  The Reminders part of the habit form (H12d)
        │       ├── HabitPages.kt      Full-screen page frame (Back, title, pinned Save/Cancel) and the Reorder page (H11)
        │       ├── HabitsViewModel.kt Works out each habit's streaks and ticking
        │       ├── HabitsSettingsScreen.kt  Settings: own data or import from Streak (H4)
        │       ├── FoodScreen.kt      Food tab: calories, macros, water, meals
        │       ├── FoodViewModel.kt   Selected day, adding meals and water
        │       ├── AddMealSheet.kt    The "Add meal" pop-up with Eat again chips
        │       ├── MoneyScreen.kt     Money tab: totals, quick log, categories, entries by day
        │       ├── MoneyViewModel.kt  Selected month, adding, logging and saving money items
        │       ├── MoneyDialogs.kt    The add flow (Expense or Income, then the kind) and its forms
        │       ├── DayTimeline.kt     The Timeline's place blocks, travel rows, gaps and item cards
        │       ├── DayOverview.kt     The Timeline | Pie switch, the 24-hour bar and the pie
        │       ├── AppEditor.kt       The pop-up that files an app under a category
        │       ├── StatsScreen.kt     Stats tab: one card per source for the day, opening the full figures
        │       ├── SettingsScreen.kt  Settings tab: every permission and switch, plus Activities and Backup
        │       ├── LogsScreen.kt      Settings, last line: Live logs (day picker, filters, search, copy and share)
        │       └── EventsScreen.kt                (planned, Step 14)
        │
        └── res/                       ── Look and feel ──
            ├── drawable/              Icons: six tab icons, add, settings, the three day-number icons, the launcher icon
            ├── mipmap-anydpi-v26/     The adaptive app icon
            ├── values/                Colours, text, light theme
            └── values-night/          Dark theme
```
