# What's new in Life Tracker

Every release page shows the sections below that were added since the last release, newest first. Older sections stay here as the history.

## 📜 Habits in the paper style (part 3 of 7)

The Habits tab, the habit page and all its pop-ups now have the paper look. The layout is exactly the one you already have; only the skin changed.

### ✨ New features

- **Paper top of the page** — big "Habits" title, round paper buttons, a dark **New** button, and a small strip saying the real Streak state (Shared with Streak plus the last send, or From Streak) with a **Write now** button.
- **Paper habit cards** — wobbly letter tiles, a tally-stroke strength bar, paper day tiles and tick buttons, in Week, Month and Year views.
- **Paper pop-ups** — the habit page (numbers with dashed dividers), the long-press day menu, the editor, the note box, All notes and the relapse question use the paper dialogs, pills, labelled boxes and buttons.

### ⚠️ Good to know

- Your own Habits layout was kept on purpose; the demo's Habits layout was not copied.
- Nothing about ticking, notes, relapses or the Streak sending changed.

## 📜 Timeline in the paper style (part 2 of 7, the pie and the pop-ups)

The Timeline is finished: the Pie, the Log button, the Log sheet and the pop-ups all have the paper look.

### ✨ New features

- **New Pie** — like your demo: what you did is the inner pie and where you were is the outer ring, sized by how much of the recorded time each took. Tap a slice or a row to see its share and what it overlapped (for example "at Home 2h 10m"); the rest fades. Under it are the Tasks list, the Places list and **How you got around**.
- **Paper Log button** — ink-black with hand-drawn corners and a pencil shadow.
- **Paper Log sheet and pop-ups** — a lighter sheet with a hand-drawn top edge, pill choices for the activity, labelled boxes, paper buttons. The same goes for the edit, hidden, delete and "How did you travel?" pop-ups.

### ⚠️ Good to know

- The old Pie had two rings ("you logged" and "collected for you") laid out by the clock. The new one follows your demo instead; the 24-hour strip is the clock view, and the solid and dashed lines in the list still show what you logged versus what the phone collected.
- Pop-ups on the other tabs keep their old look until their own parts.

## 📜 Timeline in the paper style (part 2 of 7, the list)

The "In order" list now looks like your demo, and the whole app now really uses Space Mono.

### ✨ New features

- **Place blocks** — a slim coloured bar with a pencil shadow beside a wobbly paper body holding the place name, the times and the length of the stay. A stay that is still going ends in a dashed orange line with the time and "now".
- **Rows** — each thing inside a stay has its time, a coloured line (solid = you logged it, dashed = the phone collected it) and a small paper card. The taller the stretch, the taller its row.
- **No data** — pencil-hatched dashed boxes with a **+ Fill in** button.
- **Trips** — a dashed line under the place bars, a paper card, and the "How did you go?" choices as paper pills. A trip with no known way of travel is orange.
- **To-dos, notes, empty day** — the same paper look, with highlighted headings.

### 🛠 Fixed

- Several screens forced the phone's own monospace font, so Space Mono did not show there. They all use Space Mono now.

### ⚠️ Good to know

- Pop-ups (Log sheet, edit boxes, the Pie) still have the old look until P2d.

## 📜 Timeline in the paper style (part 2 of 7, first half)

The top of the Timeline now looks like your demo.

### ✨ New features

- **New top** — a big "Today" (or Yesterday, Tomorrow, or the weekday), the full date under it, and on today a sun arc with "5h 36m left today". The "Saved on this phone" badge is gone from this screen.
- **Paper week strip** — day tiles with an orange dot on days that have entries, a pen ring on today, and the chosen day inked black.
- **Steps, Phone, Studied** — one card with dashed dividers.
- **The whole day** — a card with two lines: **Where** (places and trips) and **What** (what you logged and what the phone collected), an hour axis, an orange "now" mark and pencil hatching over the part of today still to come. Tap a stretch to see its name, times and length under the line.
- **Activity pills** — a row you can slide sideways showing the time spent on each kind of thing ("Sleep 6h 30m").

### ⚠️ Good to know

- The "In order" list, the Pie and the Log sheet still have the old layouts; they follow in the next steps (P2c and P2d).
- The line's readout shows the stretch but has no Edit button yet; tap the row in the list to edit it, as before.

## 🔤 Space Mono, the demo's font

The whole app now uses the same typewriter-style font as your paper design.

### ✨ New features

- **Space Mono everywhere** — regular and bold, on every screen, instead of the phone's own monospace font.

## 📜 A new look: the paper (part 1 of 7)

The app starts taking on the look of your paper design: cream paper, ink-black buttons, a floating menu bar and a dark version.

### ✨ New features

- **Paper everywhere** — every screen now sits on cream paper with a faint grain, fibres, two fold lines and darker edges. Dark mode has its own dark paper.
- **Floating menu bar** — the bottom menu is now a paper strip with a pen outline and a pencil shadow. The tab you are on is inked black.
- **Light, Dark or System** — Settings, then **Look**. Your choice is remembered, and the whole app changes at once.
- **New building blocks** — wobbly hand-drawn cards, buttons, pills, switches, headings with an orange highlighter swipe, and progress bars made of tally strokes. Screens start using them in the next parts.

### ⚠️ Good to know

- This is part 1 of 7. The screens keep their old layouts for now, so the app looks half new: new colours, paper and menu bar everywhere, and new layouts one screen at a time (Timeline next).

## 🔁 Habit changes reach Streak by themselves

Turn on one switch and your Habits changes go to Streak without pressing anything.

### ✨ New features

- **Send automatically** — Settings, then Habits, then **Send your changes to Streak**. When it is on, a change you make is sent 15 seconds later, and anything not yet sent goes out each time the app opens. Ticking several days in a row is sent as one file.
- **Last send** — the screen shows what the last automatic send did, or why it could not send.

### ⚠️ Good to know

- The switch starts off. Choose the Streak folder again first (it needs permission to write), then turn it on.
- Streak takes changes in when it checks the folder, so they show up in Streak a little later, not instantly.
- A day you un-tick here stays ticked in Streak.

## 📤 Send your habit changes to Streak

You can now send what you changed in Habits to Streak, by pressing a button.

### ✨ New features

- **Send to Streak now** — Settings, then Habits, then **Send your changes to Streak**. The app writes a Streak backup file in Streak's sync folder holding only the habits, days and notes you changed here. Streak takes it in the next time it checks that folder.
- **Choose the folder again** — the Streak folder was chosen as read only before. Choose the same folder once more so Android allows writing to it. The screen shows whether writing is allowed.

### 🛠 Improvements

- **Safe writing** — the file is written whole and then renamed, so Streak never reads half a file. The app keeps its own three newest files there and never touches Streak's files.
- **No loop** — the app ignores its own file when importing from Streak.

### ⚠️ Good to know

- Sending is by button for now. Sending automatically comes next.
- Streak only adds days, so a day you un-tick here stays ticked in Streak.
- This has only been tested with sample data. It has not been tried with the real Streak app on a phone yet. Tell me if Streak does not show your changes.

## 🕓 The app now remembers what you changed in Habits

Groundwork again: you will not see a change on screen yet.

### 🛠 Improvements

- **A list of what changed here** — every new habit, edit, tick, amount, missed-day reason, archive and note now records when it was changed. The next step uses this list to send only your changes to Streak. Things that come in from Streak are not on the list, so they are never sent back.

### ⚠️ Good to know

- Nothing is sent to Streak yet, and no folder is written to.

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
