# What's new in Life Tracker

Every release page shows the sections below that were added since the last release, newest first. Older sections stay here as the history.

## 🔌 Getting ready to send your habit edits to Streak

This is groundwork. You will not see a change in the app yet.

### 🛠 Improvements

- **The Streak backup file** — the app can now build a backup file in Streak's own format that holds only the habits, days and notes you changed here, so Streak can take them in through its sync folder. Nothing is written to your phone or to Streak yet. That comes in the next steps.

### ⚠️ Good to know

- Streak's sync only ever adds days. When this is switched on, a day you tick here will appear in Streak, but a day you un-tick here will stay ticked in Streak.
- Streak reads the folder only when it checks it, so changes arrive a little later, not instantly.

## 📝 Notes on habits

Write notes on your habits, and read them all in one place.

### ✨ New features

- **All notes** — the new notes icon at the top of Habits (next to the up-down button) opens every note, newest first, with the habit and the day. If you have notes on more than one habit, a row of chips narrows the list to one habit. Notes you already had from Streak show up here too.
- **Write a note** — on a habit's page there is now a **Notes** list with an **Add note** button. Press and hold a day and choose **Add a note for this day** to write one for that day. On the All notes page, **New note** lets you pick the habit.
- **Change a note** — tap any note to change its text.

### ⚠️ Good to know

- Notes are never deleted. There is no delete button, and a note cannot be saved empty, so one can't be wiped by accident.
- With **Import from Streak**, a note that came from Streak goes back to Streak's text at the next import if you changed it here. Notes you write here are kept. Sending your changes to Streak is a later step.
- A new note gets the day you pressed (today for **Add note** and **New note**). Changing a note's day is not possible yet.

## 🌱 Habits like Streak, and your own habit tracker

The Habits tab now looks and works like Streak's classic view, and you can track habits right here without Streak.

### ✨ New features

- **Streak's classic view** — a Today ring ("3 of 5 completed"), a **Week | Month | Year** switch, category chips, and one card per habit with its streak, a strength bar and a tick button. The calendar under each card is a row of seven days, a month grid, or a 53-week year grid.
- **Small or big cards** — the up-down button at the top right makes every card small (a thin coloured bar instead of the calendar) or big again. The app remembers your choice, the calendar and the category.
- **Add your own habits** — tap **New**. Pick a name, emoji, colour and category, and the type: yes/no, avoid (days without a relapse) or counted (goal, unit, amount per tap, or "any amount counts"). Choose how often: every day, some days of the week, so many days a week or month, or every X days, weeks or months.
- **Habit page** — tap a card (or press and hold it) to see its streak, best streak, total and strength, and to edit or archive it. Archived habits are hidden, never deleted, and can be shown again.
- **Press and hold a day** — mark it done or not done, set an exact amount, say why you missed it (Overslept, Busy, Traveling, Sick, Tired, Forgot, or your own words), or clear the day.
- **Choose where your habits live** — Settings, then Habits: **This app's own data** (the default) or **Import from Streak**.
- **Open in Streak** — with Streak chosen, tapping a habit opens Streak right on that habit.

### 🛠 Improvements

- **Streak's own maths** — streaks, best streaks and "done today" follow Streak's rules: rest days and holidays never break a streak, weekly and monthly goals count weeks and months, "every X days" habits done early are covered, and a week or month that already reached its goal shows its other days as covered, not missed.

### ⚠️ Good to know

- With **Import from Streak**, a habit you change here that also exists in Streak goes back to Streak's version at the next import. Days you tick here are kept. Sending your changes to Streak is a later step.
- If you were already importing from Streak, you stay on **Import from Streak** until you choose otherwise in Settings.
- Streak's named icons are not drawn yet. A habit with an emoji icon shows the emoji; the others show their first letter.

## 🎁 Release pages now say what's new

Every build now has a proper release page like Streak's: a heading, what's new in plain words, and how to download.

### ✨ New features

- **What's new on every release** — the page lists what changed since the last release, written in plain language.
- **Test builds are marked** — a build made from a branch that is not merged yet is shown as a pre-release called "Test build", so the **Latest** label always points at the newest merged version.
- **Exact commit** — every release is tied to the exact change it was built from, with a link to see everything that changed.

## 🧭 Places, trips, calls and editing (Polish 4)

Places and Trips are one list, calls are in Stats, and everything on the Timeline can be edited.

### ✨ New features

- **Places and trips in one view** — where you were, and how you got from one place to the next (for example "Walked, 1.6 km, 15 min"). A journey the phone has no record of is shown as unknown, with the straight-line distance, so you can say how you went.
- **Edit everything on the Timeline** — every item gets an editor for its category and times, and visits also for their name. Your edits are kept as a layer on top; the phone's own records are never changed.
- **Calls in Stats** — a call you talked for 38 minutes now fills 38 minutes of the 24-hour bar. The Timeline shows only calls you talked at least a chosen number of minutes (2 by default).

### 🛠 Improvements

- **Trips start 50 m from a stop** — a trip begins as soon as you are more than 50 m from the stop you were at, for every stop the app detects.
- **Settings are separate from numbers** — permissions and switches live in the Settings tab; the numbers live in Stats. Every Stats screen opens on Today.
- **Tab names fit** — the six bottom tabs keep their names on one line.

## 📜 Earlier steps

Habits and tracking, built one small step at a time: Timeline and database, backups, Streak habits import, Food (OpenNutriTracker import), your own activities and goals, Money, phone usage, calls, charging, steps and sleep, places and trips, GPX and CSV export, and a Stats and Settings tab. The README roadmap lists every step.
