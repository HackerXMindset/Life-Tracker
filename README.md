# Life Tracker

**One private app for your whole day: what you did, what you ate, which habits you kept, where your money went, and what it all adds up to.**

Life Tracker is an Android app built for a single person: its owner. It replaces a pile of separate apps with one place, and it keeps every byte of your data on your own phone. There is no account, no server and no subscription.

---

## Contents

1. [The idea in one minute](#the-idea-in-one-minute)
2. [Where the project stands today](#where-the-project-stands-today)
3. [The roadmap](#the-roadmap)
4. [How the app is organised](#how-the-app-is-organised)
5. [The file architecture](#the-file-architecture)
6. [How your data is stored and protected](#how-your-data-is-stored-and-protected)
7. [Installing on your phone](#installing-on-your-phone)
8. [How a new version gets built](#how-a-new-version-gets-built)
9. [Rules the project lives by](#rules-the-project-lives-by)

---

## The idea in one minute

Most life-tracking apps do one thing well and ignore the rest. This project joins four of them into one and adds the part that none of them have: statistics you can shape yourself.

| What you want | The app it learns from | What this app does |
| --- | --- | --- |
| A timeline of your day, logged by hand, with your own activities such as hours studied | LifeXP | **Timeline** tab |
| Meals, calories, macros and water | OpenNutriTracker | **Food** tab, with automatic import |
| Habits, streaks and totals | Streak | **Habits** tab, with automatic import |
| Income and spending | (new) | **Money** tab |
| Deep, customisable statistics on everything | Simkl | **Stats** tab |

It also remembers the big moments, such as moving to a new city or starting a new exam preparation, and shows what you did and how much you studied around them, by month and by year.

---

## Where the project stands today

| Part | Status |
| --- | --- |
| App skeleton with five tabs | Done |
| Automatic APK builds in the cloud | Done |
| Timeline with a real on-phone database | Done |
| Data sources and backups | Done |
| Habits, with Streak import (also focus sessions, to-dos, notes) | Done |
| Food, with OpenNutriTracker import, water and meal logging | Built, waiting to be tried on a phone |
| Money, Stats | Placeholder screens |

**What works right now (Steps 2 to 5).** On the Timeline tab you can:

- scroll through the last 60 days, with a dot under every day that has entries;
- tap **Log** to record an activity with a category, a name, a start and end time, and an optional note, or log a single moment with no end time;
- see the day's study time, total tracked time and entry count, plus a study goal bar (8 hours, fixed for now);
- tap an entry to delete it.

Everything is saved in a database on the phone and is still there after the app is closed.

The gear icon at the top of the Timeline opens **Data sources**, where you can:

- choose a **backup folder** on the phone, tap **Back up now**, and **Restore** from any backup or safety copy (a safety copy of your current data is always saved first);
- choose the **Streak** and **OpenNutriTracker** folders, and see the newest file the app found in each;
- tap **Import now** under Streak or OpenNutriTracker to bring in the newest export straight away.

The app also makes one backup automatically the first time you open it each day (keeping the newest 5), and then imports anything new from Streak and OpenNutriTracker.

**From Streak you get:**

- **Habits tab.** Every habit with its current streak, best streak and totals, and a 14-day row you can tap to tick a day (counted habits add one step per tap, "relapse" habits toggle a relapse, press and hold clears a day).
- **Focus sessions** become ordinary entries on your Timeline, at the time you focused, split at midnight when needed. Each is brought in once; if you delete one, it stays deleted.
- **To-dos** that have a date appear on that day of the Timeline, at their time and with their estimated length if set, including future days. Tap one to tick it off. To-dos with no date are imported but not shown yet.
- **Notes** appear on the day they were written for.

**From OpenNutriTracker you get:**

- **Food tab.** Pick a day (‹ Previous / Next ›) and see calories against that day's goal, carbs, fat and protein against theirs, and every meal grouped as breakfast, lunch, dinner and snack. **Add meal** logs a new one, with an "Eat again" row of things you have eaten before. Tap a meal to delete it.
- **Water** is tracked in the app (+250 ml, +500 ml, Undo) against a 3000 ml target. OpenNutriTracker does not export water, so this starts at zero.
- **Meals on the Timeline.** Each meal also shows on its day of the Timeline, at the time you ate it.
- **Activities** (walking, and custom ones such as a "stool" log) become Timeline entries, in Health or Exercise. Each is brought in once; if you delete one, it stays deleted.
- **Amounts are grams.** OpenNutriTracker counts every amount as grams even when it says "serving", and its daily totals match this app's to the calorie.

Nothing in Streak or OpenNutriTracker is ever changed. Importing twice never doubles anything: for each habit day the higher count is kept, and a to-do you ticked stays ticked. Streak's weekly, monthly and "every X days" schedules are stored but not applied yet, so every habit's streak is counted as a daily one.

*Honest note:* the builds compile and install, but Step 2 has had only a cloud build so far. It has not yet been tested by hand on a real phone.

---

## The roadmap

Each step is small enough to build, install and try before the next begins. The order below is the plan, and it can be changed at any time.

```
 DONE         Step 1  Skeleton
 DONE         Step 2  Timeline and database
 DONE         Step 3  Data sources and backups
 DONE         Step 4  Habits (Streak import)
 DONE         Step 5  Food (OpenNutriTracker import)
 NEXT  ──►    Step 6  Your own activities and goals
              Step 7  Money
              Step 8  Life events, month and year views
              Step 9  Stats
              Step 10 Polish
```

### Done

**Step 1: Skeleton.** Five tabs, a light and dark theme, an app icon, and the cloud build that turns every change into an installable APK.

**Step 2: Timeline and database.** The Room database, the **Log** button, the day strip, daily summary, study goal bar and deleting entries.

**Step 3: Data sources and backups.** A single *Data sources* screen where you pick three folders, once each:

- the **Streak folder** (`Internal shared storage/Streak`);
- the **OpenNutriTracker folder** (`Internal shared storage/OpenNutritracker`);
- a **backup folder** on the phone, such as `Documents/LifeTracker`.

Android remembers each choice, so you never pick them again. This step also adds **Back up now** and **Restore**, plus a daily automatic backup when the app is opened, with the 5 most recent copies kept. Backups stay on the device. Nothing is uploaded anywhere. (Built; the Streak and OpenNutriTracker folders are only checked in this step, and the imports follow in Steps 4 and 5.)

**Step 4: Habits.** On opening, the app finds the newest Streak backup in the Streak folder and imports it: habits and their history, categories, focus sessions (as Timeline entries), to-dos and notes. The Habits tab shows streaks, totals and a 14-day row for each habit, and handles all three of your habit kinds: yes or no, the "Relap" kind, and counted habits. This was the first change to the database layout, done with a proper migration so nothing you had logged was touched.

**Step 5: Food.** The same automatic import for OpenNutriTracker: meals, daily calorie and macro goals, and activities. The Food tab shows calories and macros against your goals, water, and lets you log meals directly. Meals also appear on the Timeline. This was the second database change (version 2 to 3), again with a proper migration.

### Next

**Step 6: Your own activities and goals.** Today the categories are fixed. This step lets you create your own (for example one per subject you study), choose their colours, and set your own daily goals instead of the fixed 8-hour study goal.

**Step 7: Money.** Income by source, spending by category and the monthly net. There is no import for this, so entries are made by hand.

**Step 8: Life events, month and year views.** Mark moments that changed your life, then look back by month or year to see what you did and how much you studied around each one.

**Step 9: Stats.** Choose any metric and view it by day, week, month or year, with a heatmap and short written insights. This comes late on purpose: it needs the other steps' data to exist first.

**Step 10: Polish.** Reminders, search, smoother screens, and anything learned from using the app every day.

---

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
├── settings.gradle.kts                Names the project and its one module
├── build.gradle.kts                   Versions of the build plugins
├── gradle.properties                  Build settings
├── .gitignore                         Files kept out of the repository
│
├── .github/workflows/
│   └── build.yml                      The cloud build: every push becomes an APK
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
    │   └── .../data/ont/OntImportTest.kt OpenNutriTracker export parsing
    │
    └── src/main/
        ├── AndroidManifest.xml        The app's identity card for Android
        │
        ├── java/com/lifetracker/app/
        │   ├── MainActivity.kt        Entry point: starts the theme and the app
        │   │
        │   ├── data/                  ── The memory layer ──
        │   │   ├── Database.kt        Entries table, the database itself and its upgrade steps
        │   │   ├── Tables.kt          Habits, completions, to-dos, notes and import records
        │   │   ├── HabitStats.kt      Streak arithmetic
        │   │   ├── FoodTables.kt      Meals, daily food goals and water
        │   │   ├── FoodStats.kt       Calorie and macro arithmetic
        │   │   ├── Backup.kt          The backup file format (JSON) and how it is read back
        │   │   ├── BackupManager.kt   Writing, listing and restoring backups, daily auto backup
        │   │   ├── DataSources.kt     The three chosen folders, remembered between runs
        │   │   ├── SourceScanner.kt   Finds the newest Streak and OpenNutriTracker file
        │   │   ├── streak/
        │   │   │   ├── StreakModels.kt   What a Streak backup contains
        │   │   │   ├── StreakParser.kt   Reads Streak's backup format
        │   │   │   ├── FocusConverter.kt Turns focus sessions into Timeline entries
        │   │   │   └── StreakImporter.kt Finds, reads and merges the newest backup
        │   │   ├── ont/
        │   │   │   ├── OntParser.kt      Reads an OpenNutriTracker export
        │   │   │   ├── OntConverter.kt   Meals and activities into this app's form
        │   │   │   └── OntImporter.kt    Finds, reads and merges the newest export
        │   │   └── (money tables arrive in Step 7)
        │   │
        │   └── ui/                    ── The screen layer ──
        │       ├── LifeTrackerApp.kt  The frame: bottom tab bar and tab switching
        │       ├── TimelineScreen.kt  Timeline: day strip, summary, goal bar, entries
        │       ├── TimelineViewModel.kt  Selected day, adding and deleting entries
        │       ├── LogSheet.kt        The "Log something" pop-up
        │       ├── DataSourcesScreen.kt     Folders, Back up now and Restore
        │       ├── DataSourcesViewModel.kt  What that screen shows and does
        │       ├── Model.kt           Activity categories and their colours
        │       ├── Format.kt          Turns minutes into "2h 05m" and "8:15 AM"
        │       ├── Components.kt      Shared pieces such as the screen header
        │       ├── theme/
        │       │   └── Theme.kt       Light and dark colours
        │       ├── HabitsScreen.kt    Habits tab: cards, streaks, 14-day rows
        │       ├── HabitsViewModel.kt Works out each habit's streaks and ticking
        │       ├── FoodScreen.kt      Food tab: calories, macros, water, meals
        │       ├── FoodViewModel.kt   Selected day, adding meals and water
        │       ├── AddMealSheet.kt    The "Add meal" pop-up with Eat again chips
        │       ├── MoneyScreen.kt                 (planned, Step 7)
        │       ├── EventsScreen.kt                (planned, Step 8)
        │       └── StatsScreen.kt                 (planned, Step 9)
        │
        └── res/                       ── Look and feel ──
            ├── drawable/              Icons: five tab icons, add, settings, the launcher icon
            ├── mipmap-anydpi-v26/     The adaptive app icon
            ├── values/                Colours, text, light theme
            └── values-night/          Dark theme
```

---

## How your data is stored and protected

- **On the phone only.** The database is a private SQLite file that no other app can read.
- **Imports read, never change.** The app reads your Streak and OpenNutriTracker backups and never writes to those folders or alters those files.
- **Imports are repeatable.** Each import overwrites matching records instead of adding copies, so running the same one twice is harmless.
- **Backups are plain files.** A backup is an ordinary JSON file you can open, copy or keep, and it still exists if the app is uninstalled. Each file is written under a temporary name and renamed only when complete, so a crash never leaves a broken backup.
- **Restore is cautious.** The file is checked before anything is touched, and your current data is saved as a safety copy first.
- **What backups do not cover.** A backup on the phone cannot help if the phone itself is lost. Copy the backup folder somewhere else now and then if that matters to you.

---

## Installing on your phone

1. Open this repository's **Releases** page on GitHub in your phone browser.
2. Open the newest release and download `life-tracker.apk`.
3. Tap the downloaded file. Android will ask once to allow installs from your browser or Files app. Allow it.
4. Google Play Protect may warn that the app is from outside the Play Store. That is expected for your own app.

A new version installs over the old one and keeps your data.

---

## How a new version gets built

No computer is needed. GitHub builds the app in the cloud.

1. A change is pushed to this repository.
2. GitHub Actions runs `build.yml`, which runs the tests and then builds a signed release APK (a few minutes). If a test fails, no APK is published.
3. The APK is published as a new release named `build-N`, where `N` is the run number.
4. You download it from the Releases page and install it.

The run number also becomes the app's internal version code, so every build counts as newer than the last.

**About the signing key.** `keystore/lifetracker.p12` is committed on purpose. It exists only so that updates install over older versions. The password is in `app/build.gradle.kts`. **Keep this repository private.** If it is ever made public, create a new key, then uninstall and reinstall the app once, after making a backup.

---

## Rules the project lives by

1. **Your data stays on your phone.** No accounts, no servers, no tracking.
2. **No data is ever lost by an update.** Every change to the database structure ships with a proper migration. Wiping and rebuilding the database is never allowed.
3. **Small steps you can try.** Each roadmap step ends with something you can install and use.
4. **Plain and quiet.** No notifications, ads or nagging unless you ask for them.
5. **Built when you say so.** Work on a step begins only when the owner asks for it.
