# Life Tracker

<img alt="Downloads" src="https://img.shields.io/github/downloads/HackerXMindset/Life-Tracker/total?style=flat&logo=github&logoColor=white&label=Downloads&color=7C3AED" />

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
| Food, with OpenNutriTracker import, water and meal logging | Done |
| Your own activities, colours and daily goals | Done |
| Money: income and spending, monthly items, quick-log items, categories | Done |
| Phone usage: app sessions on the Timeline, per-app totals, app-to-activity links | Done |
| Calls from the call log, on the Timeline | Built, waiting to be tried on a phone |
| Charging sessions, a banner to say what you plugged into, phone use while charging | Built, waiting to be tried on a phone |
| Steps (Health Connect or the phone step counter) and sleep (estimated from phone use, editable) | Built, waiting to be tried on a phone |
| Stats | Placeholder screen |

**What works right now (Steps 2 to 6).** On the Timeline tab you can:

- scroll through the last 60 days, with a dot under every day that has entries;
- tap **Log** to record an activity (pick one of your own, or tap **+ New** to make one on the spot), a name, a start and end time, and an optional note, or log a single moment with no end time;
- see the day's tracked time, entry count and how many goals you met, plus a bar for every activity that has a daily goal (Study starts with 8 hours, and you can change it);
- tap an entry to delete it.

Everything is saved in a database on the phone and is still there after the app is closed.

The list icon at the top of the Timeline opens **Activities**, where you can add your own activities, rename them, pick a colour from a palette, and set a daily goal for each: *at least* (such as 8 hours of study) or *at most* (such as 2 hours of screen time, whose bar turns red if you go over). *Hide from Log* removes an activity from the Log pop-up without touching anything already logged.

The gear icon beside it opens **Data sources**, where you can:

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
 DONE         Step 6  Your own activities and goals
 DONE         Step 7  Money
 DONE         Step 8  Phone usage (app sessions on the Timeline)
 DONE         Step 9  Calls
 DONE         Step 9b Any dates on Calls and Phone usage
 DONE         Step 10 Charging
 DONE         Step 11 Steps and sleep (Health Connect, phone step counter)
 DONE         Step 12 Places (named places, unknown stops to review)
 DONE         Step 13 Trips (distance, speed, how you travelled)
 DONE         Step 13b Keeping it running and keeping every reading
 DONE         Step 13c Cleaner readings (3 minute stays, motion sensor, jump filter)
 DONE         Step 13d How sure each visit is, name hints
 DONE         Step 13f GPX and CSV export, GPX import
 DONE         Polish 1 Timeline top numbers, Stats tab, Settings tab
 DONE         Polish 2a Merged app blocks, edit apps and entries
 DONE         Polish 2b Place blocks, travel rows, no-data gaps
 DONE         Polish 2c Timeline | Pie switch, 24-hour bar and pie
 DONE         Polish 3 Every Stats screen opens on Today
 DONE         Polish 4a Places and Trips become one list (Stats)
 DONE         Polish 4b A "Places and trips" page in Settings
 DONE         Polish 4c Calls: Stats and Settings, minimum call length, calls in the 24-hour bar
 DONE         Polish 4d Edit everything on the Timeline (a "your edits" layer)
 DONE         Polish 4e Trips start 50 m from a stop
 DONE         Polish 4f Tab labels that fit
 DONE         Habits H1 Habit rules (schedules, streaks), tested
 DONE         Habits H2 Streak's classic view, Week / Month / Year, expand button
 DONE         Habits H3a Add, edit and archive a habit here (+ New, tap a card)
 DONE         Habits H3b Long-press a day: set an amount, miss reason, clear
 DONE         Habits H4 Settings: Streak or this app's own data; tap a habit to open it in Streak
 DONE         Habits H3c Notes on habits (notes icon, notes on a habit)
 DONE         Habits H5 Your edits reach Streak (through Streak's backup folder)
 DONE         Paper P1 New look: colours, font, paper, menu bar, buttons, cards
 DONE         Paper P2a New look, Timeline: top of the page
 DONE         Paper P2b New look, Timeline: the whole-day strip
 DONE         Paper P2c New look, Timeline: the In order list
 DONE         Paper P2d New look, Timeline: Pie, Log button and Log sheet
 DONE         Paper P3a New look, Habits: top of the page
 DONE         Paper P3b New look, Habits: the habit cards
 DONE         Paper P3c New look, Habits: habit page and pop-ups
 DONE         Paper P7a to P7g New look, Settings: tab, shared parts, every page (paper skin, own layouts)
 DONE         Paper P7h-a Settings 1:1 with your demo: icons, shared pieces, the Settings tab
 DONE         Paper P7h-b Settings 1:1: Phone usage, Steps and sleep
 DONE         Paper P7h-c Settings 1:1: Places and trips
 DONE         Paper P7h-d Settings 1:1: Calls, Charging
 DONE         Paper P7h-e Settings 1:1: Activities
 DONE         Paper P7h-f Settings 1:1: Habits
  DONE         Paper P7h-g Settings 1:1: Appearance, Backups and imports
  DONE         Timeline T-A Fill in lands inside the place (bug fix)
  DONE         Timeline T-B Correct phone usage numbers (one source, Digital Wellbeing check)
  DONE         Timeline T-C Periods of use: apps used together count as one activity
  DONE         Timeline T-D Name and suggest combinations (with built-in examples)
  DONE         Timeline T-E Timeline drawn to scale (1 hour = 60 dp, calendar style)
  DONE         Timeline T-F Offline / Phone off / No data
  DONE         Timeline T-G Walking distance from readings and the step counter
  DONE         Timeline T-H Apps and categories page, Timeline settings page, Cleaner readings (Walking)
  DONE         Timeline T-I Clean Timeline: one time map, small rows that open in place, soft grey Offline
  DONE         R1 Thicker Timeline lines; remove every phone-maker mention
  DONE         R2 Full-screen edit window for everything on the Timeline (like editing a habit in Streak); the open-in-place panel goes
  DONE         R3 Colours and icons like Streak's (swatches + custom colour ring; icons + emoji) for apps and activities
  DONE         R2b Timeline corrections: tap expands again, long-press edits, grouped No data, Home box with its activities, thick place line, travel suggestions, charging bolt, more activities with icons
  NEXT ──►    R4 Place categories with colours you can add (the location lines take the colour)
              R5 Settings > Permissions: every permission in one place, why it is needed, how to switch it on
              R6 Money rework: "Deduct it from", separate expense and income categories, edit anything, paper look (includes Paper P5)
  DONE         Paper P4 New look: Food
              Paper P5 New look: Money (built inside R6)
              Paper P6 New look: Stats
              Step 14 Life events, month and year views
              Step 15 Stats
              Step 16 Polish
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

**Step 6: Your own activities and goals.** The eight fixed categories became a list you control. Each activity has a name, a colour and an optional daily goal (a minimum to reach or a limit to stay under), and the single fixed study goal became one bar per goal. Your old entries kept their colours because the built-in activities kept their old keys. This was the third database change (version 3 to 4), with a proper migration, and backups moved to format 4.

**Step 7: Money.** A Money tab for what comes in and goes out, entered by hand. Adding starts with two questions: *Expense or Income?* and then *what kind?*

- **Every month**: rent, a subscription, an EMI, a salary. You give it a name, amount, category, the day of the month it is charged, a start date and an optional end date. The app adds it for you on that day each month (on the last day if the month is shorter), and never twice.
- **Again and again**: a lassi, chai, a bus ticket. Save it once and it becomes a button; tap it to log it for today, or press and hold to change the amount for that one entry.
- **Just once**: a book, a repair, a gift.

You make your own spending and income categories (a starter set is there), see each month's in, out and left, a bar per category, and every entry by day. The currency is rupees by default and can be changed. This was the fourth database change (version 4 to 5), with a proper migration, and backups moved to format 5.

**Step 8: Phone usage.** The app reads Android's own record of which app was on screen and for how long (the source Digital Wellbeing uses), once you switch on *Usage access*. It copies that record into its own database on the phone, because Android only keeps about a week, and keeps copying it every few hours in the background. On the Timeline, each stretch of app use shows as a block with the app's name and exact time, with a short summary of the day's top apps. A *Phone usage* screen shows totals per app (today or the last 7 days) and lets you link an app to one of your activities (for example Anki to Study, YouTube to Screen and leisure), so its time counts towards that activity's daily goal, including screen-time limits. You can hide apps, leave short stretches off the Timeline, or turn app use off the Timeline entirely. This was the fifth database change (version 5 to 6), and backups moved to format 6. Backups now grow with your phone use, because every session is kept.

**Step 9: Calls.** The app copies the phone's call log (who, when, how long, and whether it was incoming, outgoing, missed or declined) into its own database, once you allow *Call logs*. Contacts access is optional and only turns numbers into names. Calls show on the Timeline with their time, and a card shows the day's count and talk time. A *Calls* screen totals any days you choose (quick choices from Today to All time, or any From and To dates, or a single day), lists the people you talked to most, and shows your latest calls. Only normal phone calls are in the call log, so WhatsApp and Telegram calls do not appear. The first copy reads the whole call log, and *Copy older calls* repeats that on demand. The *Phone usage* screen has the same day picker; it can only show days the app has already copied, because Android keeps about a week of detail. Phone usage and calls are a history that only grows: syncing adds new rows and restoring a backup adds to what is already there, so nothing copied from the phone is ever deleted by the app. Backups include calls, with numbers and names as plain text, so keep the backup folder private. This was the sixth database change (version 6 to 7), and backups moved to format 7.

### Next

The next steps were planned together after asking for more automatic tracking. They are ordered so that nothing needs an always-on notification or drains the battery: each one uses Android's own low-power services.

**Step 10: Charging.** The app records every time the phone is on a charger: start and end, battery level at both ends, the plug type (charger, USB, wireless), and the average speed (watts into the battery, from Android's battery readings). When a session starts, a banner asks "What are you charging with?" with Wall / Power bank / Laptop buttons; you can also set it later on the *Charging* screen. After at least 3 similar tagged sessions (same plug type, about the same speed, 70% the same answer) the app suggests the answer itself and shows it as "(guess)". The screen also shows how long you used the phone while it charged, taken from the Phone usage sessions. Android only tells apps about plug-in and unplug while they are running, and cannot tell a power bank from a wall charger, so the app does not guess that alone. Nothing runs while the phone is not charging: Android runs a small job about every 15 minutes only while charging, which opens a session and notes level and speed, and the plug and unplug events give exact times whenever the app happens to be running. A session's end can therefore be up to 15 minutes early. Battery *No restrictions* and pop-up notifications must be allowed or the banner may not appear. Charging history is like phone usage and calls: it only grows, and restoring a backup adds to it. This was the seventh database change (version 7 to 8), and backups moved to format 8.

**Step 11: Steps and sleep.** Daily steps are copied from Health Connect, which on Android 14 and newer counts the phone's own steps once any app is allowed to read them (so earlier days stay empty unless another app saved them there). If Health Connect is missing or has nothing for today, the phone's own step counter can be switched on instead; it adds the steps since the last reading whenever the app syncs. A day's steps never go down. Sleep is read from Health Connect if an app writes it, or worked out from when you stop using the phone at night: the longest stretch of 3 to 14 hours with no phone use that began between 6 pm and 10 am, ignoring quick checks under 5 minutes between 12:30 and 6 am. It is a guess (reading in bed looks like sleep), so you can edit any night, add a missing one, or mark it "not sleep"; a night you edited is never overwritten. Sleep shows on the Timeline at the time you woke up and counts towards your Sleep goal on days you did not log Sleep yourself. The charging record is not used for the estimate: plugging in at bedtime was not reliable enough. Steps and sleep only ever grow, restoring a backup adds to them, and they are in backups. This was the eighth database change (version 8 to 9), and backups moved to format 9.

**Step 12: Places.** Name places such as Home, Library or Gym, each a circle you size from 30 to 150 m; that circle is the real size used to decide you are there. Android's geofencing (a ring of at least 150 m around each place) and its still/moving messages only wake the app. Then the app reads your position: every minute (or 2 or 5, your choice, with or without GPS) while you are somewhere it does not know, until you leave; at a named place two readings are enough and it rests until you move. Android only allows readings that often from a foreground service, so a small notification shows while it runs, and it stops by itself. Staying in one spot (within about 60 m) for 5 minutes is a stay: if its centre is inside a place's circle it is a visit to that place, otherwise it waits in a review list (grouped by spot, with a Map button and a name suggested from OpenStreetMap) where you can name it, which also turns every other stop there into a visit, or say "Not a place", which only hides it. A stay starts when the phone went still and ends when it started moving, a stay that resumes within 20 minutes joins the earlier visit, and if Android never says you left, the visit ends when you are next seen elsewhere. Names are suggested over the internet from OpenStreetMap (the named place within 60 m, else the street), only for stops you have not named, once per spot, when the review list is open; that is the only use of the internet. Places are never deleted, only "not watched". You can add a place from where you are, or paste the numbers Google Maps copies. Geofences are set up again after a restart, every 6 hours and whenever you open the app, and the Places screen shows when Android last reported something and when the last reading arrived, so you can tell if the phone's battery settings are blocking it (set battery to No restrictions for this app). Visits show on the Timeline and are in backups, and restoring only adds to them. This was the ninth database change (version 9 to 10), and backups moved to format 10.

**Step 13: Trips.** A trip is the way between two stays. It starts when you leave a place or stop you had been at for at least two position readings, and ends when you stay somewhere for 3 minutes (the end is when you got there, not 3 minutes later; it was 5 minutes before Step 13c). While it runs, the app reads your position every 30 seconds (15 s or 1 min if you choose; Places must be on) and keeps every reading, so the route is never lost. Distance adds up the readings, skipping ones that moved less than their own error and any jump faster than 250 km/h; you also get the usual and top speed. Something that never got 200 m away or lasted under a minute was GPS drift and is dropped. The app guesses how you travelled: first from what you told it about trips between the same two places, then from Android's walking, running and cycling signals and the speed; for anything in a vehicle it looks at your own corrected trips with a similar speed, and otherwise says "vehicle" until you pick Auto-rickshaw, Motorbike, Car, Bus, Train or metro, Cycle, Walk, Run or Other (tap a trip). What you pick is never overwritten, and the trips it had only guessed are looked at again using it. Trips show on the Timeline, on a Trips screen with totals per way of travelling, and in backups (restoring only adds). This was the tenth database change (version 10 to 11), and backups moved to format 11. Importing your Google Timeline history is not built; it would need the file you export from Google.

**Step 13b: Keeping it running, and keeping every reading.** Phones stop background apps, which is the main way places and trips get missed, so the Places screen now has a "Keep it running" card: it asks for unrestricted battery, opens the app's settings page (it used to try two phone-maker screens first, removed in R1) (each falls back to the app's settings page if the phone does not have that screen), and shows whether battery is unrestricted. If you swipe the app away while the readings are running, an alarm starts them again a few seconds later. A check every 15 minutes starts the readings again if Android stopped them while they should be running, and the Places status shows how many times that was needed. **Always on** mode keeps the readings going all the time (every 1, 2 or 5 minutes while standing still using Wi-Fi and mobile network, and GPS at the trip pace while moving), with a notification all the time; it is off by default because it costs battery. Every position reading accurate to 150 m or better is now stored for good (database version 11 to 12, backups format 12, compact one day at a time; restoring only adds missing readings), and the Places screen shows how many readings arrived in each of the last 24 hours and the longest silence. A tidy-up looks at the stored readings again with hindsight: it drops bad readings (spikes, impossible speeds), finds stays that wander only a little, joins the same place either side of a silence but leaves a hole when the silence ends somewhere else, and weights a stay's centre by how accurate each reading was. It only ever adds a visit or moves a visit's start earlier or end later (never into a neighbour, never a visit in progress, never over what you decided), and it works trip distance and speeds out again without the bad readings (the way you travelled is not touched). It runs by itself every 6 hours and when you open the Places screen, for the last three days, and "Tidy everything from my readings" does the whole history. Nothing is deleted. Not built: Google Timeline import (left out on purpose) and reading other apps' positions (passive mode).

**Step 13c: Cleaner readings.** A stay is now **3 minutes** (it was 5). The old rule that joined a return to the same spot within 20 minutes into one visit is gone: if you went 200 m or more away (the same distance that makes a trip real, less each reading's own error), or stopped somewhere else in between, coming back is a new visit and the trip between shows in Trips. Only wobble that never got that far is still one visit. The tidy-up uses the same rule, so it never glues two real visits together. Every position reading now also keeps the **speed the phone measured** (database version 12 to 13, backups format 13; older backups still load), and a jump faster than 20 m/s that is also more than 5 times that speed is thrown out as a glitch, live (it cannot end a stay or start a trip, and the Places status counts them) and again by the tidy-up; the reading itself is still stored. While the location service runs it also listens to the phone's **motion sensor** (accelerometer, batched to save battery, plus Android's significant-motion alert): shaking for a few seconds is moving, a minute of calm is still, counted from the last shake. It adds to Android's own still/moving messages: moving always counts, but "still" is ignored while a trip is in progress (a smooth ride feels still) or when the phone is already known to be still. The Places status shows what the sensor last said.

**Step 13d: How sure each visit is, and name hints.** Every finished visit gets a **confidence** from 0 to 100, worked out from the position readings taken during it: how long you stayed (full marks at 30 minutes), how tightly the readings sit together (nine in ten within the 60 m stay radius), how many readings there are, how accurate they were, how much of the visit has readings instead of silence (gaps over 10 minutes count against it) and whether the spot is a place you named (an unnamed spot just leaves that part out). It is stored on the visit (database version 13 to 14, backups format 14; older backups still load) and worked out again by every tidy-up, so it improves when the readings do. Visits scoring under 50 show an **unsure** tag on the Timeline and in the Places visit list; a visit that could not be judged (no readings) shows nothing. Name hints: every 6 hours, together with the tidy-up, the app looks up a name on OpenStreetMap for up to 6 unnamed spots that have none yet (one lookup a second; only the position of that spot is sent, once), so "Looks like: ..." is already there when you open the review list, and a **Use "name"** button saves it as a place (kind Other, 50 m circle) in one tap. Nothing is named without your tap, and names in OpenStreetMap are patchy in India, so many hints will be street addresses.

**Step 13f: GPX and CSV export, GPX import.** The Export and GPX import buttons are on the Backups and imports page (they were on the Places screen until P7h-h). **Readings GPX** and **Readings CSV** save every stored reading (CSV: time in milliseconds and as a date, latitude, longitude, accuracy, speed), **Trips GPX** saves each trip as its own track named with its day, how you travelled and distance, and **Import GPX** adds the positions in a GPX file from any app (GPSLogger, Google Earth, OsmAnd...) to the stored readings. A written file has a new track segment after a silence of 30 minutes and keeps each point's accuracy and speed in an extensions block, so reading it back gives exactly what was written. A point from another app needs a position and a time; its accuracy comes from `acc`, else `hdop` times 5 m, else a guess from the source (network 50 m, cell 150 m, anything else 20 m). Importing only ever adds readings (the same second is skipped, readings worse than 150 m are left out) and then works the visits out again from all the readings; it does not build trips (the way between stays) from the imported positions. Files with an XML DOCTYPE are refused.

**Polish 1: a plainer Timeline, a Stats tab and a Settings tab.** The top of the Timeline is now one card with three numbers for the day: steps, time on the phone and time studied (what you logged as Study plus apps you linked to Study). The Tracked / Entries / Goals met row, the goal and limit bars, the Activities and settings buttons and the five cards (phone, steps and sleep, places, trips, calls) are gone from it, so the Timeline is only the day's entries in order. Daily goals are no longer shown or edited anywhere; goals already stored are kept untouched. The **Stats** tab now holds the numbers: one card per source for the chosen day, each opening the full figures over any range of days. The new **Settings** tab holds every permission and switch (Phone usage, Steps and sleep, Places, Trips, Calls, Charging), each row saying whether it is on, plus Activities and Backup and import. Each detail screen has a numbers half (from Stats) and a settings half (from Settings); every control that existed before still exists. Tapping a place, trip, charge or sleep row on the Timeline opens the numbers half. Tapping a bottom tab now also closes any detail screen that was open.

**Polish 2a and 2b: the Timeline as place blocks.** Uses of the same app within 10 minutes of each other are one block ("Telegram · 23m · opened 6 times"). Tap an app block to choose which category it counts as (kept for the app, so it applies to every day); tap something you logged to edit or delete it. Everything you did at a place (phone use, meals, sleep, calls, charging, your own entries) sits inside one tall block for that place, with the place's name running up its side. The way between two places is a travel row: the mode and distance when the phone recorded the trip (tap to change it), or "Unknown way of travel" with quick choices (Walked, Cycled, Bus, Drove, Auto, Metro, Train) when it can't tell, or when there is no trip record at all. Ten minutes or more with nothing known becomes a "No data" card with a Fill in button that opens the Log pop-up with those times. Gaps are only drawn between the first and last thing of the day, and on today never after now. Tapping a meal, call, charge or sleep row opens the screen where it is edited. The arranging is in `DayLayout.kt` (tested); the drawing is in `DayTimeline.kt`.

**Polish 2c: Timeline | Pie.** A switch under the day numbers flips the day between the Timeline and a Pie. Both show the same 24-hour bar first: one bar, the same length every day, with marks at 6, 12 and 18 hours and a marker for now on today. Each stretch is coloured by what is known; where you logged something and the phone also collected something, what you logged wins, and nothing is counted twice. The Pie has two rings: the outer one is what you logged, the inner one is what was collected for you (sleep, meals, phone, travel), with the total in the middle, a legend, and a "Where the time went" list per category. The arranging is in `DaySegments.kt` (tested); the drawing is in `DayOverview.kt`.

**Polish 3: every Stats screen opens on Today.** Phone usage, Steps and sleep, Trips, Places, Charging and Calls start on Today each time they are opened, whatever range you looked at last time. You can still pick another range once inside.

**Polish 4 (all of 4a to 4f are built; the rest is planned, in this build order, one small step at a time).**

- **4a Places and Trips become one list (Stats). Built.** One view of where you went and how you got there: a place (name, arrival to leaving, time spent), then a journey row ("Walked, 1.6 km, 15 min"), then the next place. The separate Trips screen is gone (its data stays). A journey with no trip record shows the straight-line distance and asks how you went; your answer is saved as a new trip record (nothing is changed). The same quick choices now work on the Timeline's unknown-journey rows. The logic is in `PlaceTrail.kt` (tested); the cards are in `Journeys.kt`. It opens on Today. When you moved but there is no record in between, the journey row shows the straight-line distance and "Unknown way of travel" with the quick choices (Walked, Cycled, Bus, Drove, Auto, Metro, Train).
- **4b A "Places and trips" page in Settings. Built.** Moves out of the Stats view: Watch my places, Record my trips and how closely to follow one, how closely to watch, GPS, Always on, "Stop the phone putting the app to sleep" (unrestricted battery), the list of your places with Add a place, and the Timeline switches. The Status card and the readings card go to the bottom of that page under "Status". Switch labels that run off the screen are fixed.
- **4c Calls. Built.** Calls move to Stats (totals, people, latest calls, Sync now, opens on Today). The access prompt, Copy older calls and the Timeline switch go to a Calls page in Settings, with a new **minimum talk time for the Timeline** you choose (1, 2, 5 or 10 minutes; starts at 2; the check is `CallStats.shownOnTimeline`, tested). Missed calls and calls nobody answered never show on the Timeline (they stay in the Stats list). Calls become their own segment in the 24-hour bar and the Pie, so a 38-minute call takes 38 minutes' worth of the line.
- **4d Edit everything on the Timeline. Built.** Every kind of item gets an editor for its category and its times (visits also their name; meals, calls, charges and sleep no longer send you to another screen). The phone's own records (visits, calls, app usage, trips) are never changed: your changes are saved in a separate "your edits" table (database version 14 to 15, backups move to format 15; the rules are in `TimelineEdits.kt`, tested, and the pop-up is `EditItemDialog.kt`). Tap a visit block, a call, a charge, sleep, a meal, an app block or a trip to change when it started and ended and which category it counts as, or to hide it; "Hidden by you" at the end of the day brings it back. Not yet editable: the unknown-journey rows (nothing recorded to edit; answer the way of travel instead) and the gaps and the Timeline shows your version on top. Hiding an item is an edit, never a delete, and removing an edit brings the original back.
- **4e Trips start 50 m from a stop. Built.** A trip begins as soon as you are more than 50 m from the stop you were at, for every stop the app detects, named or not. The old rule that a trip must get 200 m away is dropped. To stop GPS drift making fake trips, a reading only counts as outside when it is more than 50 m away plus its own error (up to 50 m more), and a trip only counts when it really got 50 m away once that error is taken off. This is the same error-aware rule as before with smaller numbers, not a new two-readings-in-a-row rule. Going 50 m away and coming back is now a new visit, not a wobble (the old distance for that was 200 m), so a short walk next door shows as a trip. Old days can be rebuilt with "Tidy everything from my readings"; nothing is deleted. This needs a real walk on the phone to check, so it is its own small step.
- **4f Tab labels that fit. Built.** With six tabs the labels "Timeline" and "Settings" wrapped onto two lines; they will fit on one.

Not part of Polish 4, and not built: **Step 13e** (fixing trips and visits by hand: split and join), which you have not asked for yet.

**Habits rework (H1 to H5, all built, one small step each).** The Habits tab was a plain list of cards. It becomes Streak's classic view, and the app gets its own habit tracker that works like Streak's. Only the core Streak features come first: the classic view, Week / Month / Year, notes, categories, counted habits and miss reasons. Focus timers, to-dos, reminders, widgets, cover images and the book and water special types are not part of this. The app already stores habits in its own tables (from the Streak import), so no new database version is expected unless a step says so.

- **H1 Habit rules. Built.** Streak's schedules (every day, some days of the week, a number of times a week or month, every X days) and the streak, goal and "done today" maths as tested code with no screens (`HabitRules.kt`). Days off a schedule never break a streak.
- **H2 Streak's classic view. Built.** The Today ring at the top ("3 of 5 completed"), the **Week | Month | Year** switch, category chips, and one card per habit: coloured tile, name, streak, category, a strength bar with a percent, a tick button on the right, and the calendar under it (a row of seven days, a month grid with day numbers, or a 53-week year grid that opens on the newest weeks). Tapping a day ticks it, pressing and holding clears it. The up-down button at the top right makes every card small (the calendar becomes a thin coloured bar) or big again. It remembers the mode, the card size and the category you picked. Day colours follow Streak: paused days blue, days covered by a weekly or monthly target faded, partial counted days lighter. Habit icons: Streak's named icons are not drawn yet; an emoji icon shows as is, otherwise the habit's first letter. The notes icon and **+ New** come with H3 (they have nothing to open yet), and Focus is not part of this rework.
- **H3 Add, edit and note habits here (three small steps).** Everything a habit has is kept in Streak's own record format (the `raw` copy every habit already carries), so a habit made here can be sent to Streak in H5 without conversion. A habit imported from Streak keeps the Streak fields this app does not edit.
  - **H3a Add, edit and archive. Built.** A **+ New** button next to the up-down button and a habit page when you tap a card (streak, best, total, strength, schedule, and buttons **Edit** and **Archive**). The form has name, icon (emoji), colour, category (pick one or type a new one), type (yes or no, avoid, counted), and for counted habits the goal per day, the unit, the step and "any amount counts"; and the schedule (every day, some days of the week, so many times a week, so many times a month, every X days, weeks or months, with a start day). Archive hides a habit and **Show again** brings it back; nothing is deleted. A habit imported from Streak that you edit here would be put back to Streak's version by the next import until H4 adds the "use this app's data" switch, so H4 follows straight after H3.
  - **H3b Long-press a day. Built.** Long-press (it used to clear the day) opens a small menu for that day: mark done or not done, set an exact amount (counted habits), say why you missed it (Overslept, Busy, Traveling, Sick, Tired, Forgot, or your own words), and **Clear this day** (the only way a day record is removed).
  - **H3c Notes. Built.** Notes use the same `notes` table the Streak import already fills, so imported notes show up too.
    - **The notes icon** (top right, next to the up-down button) opens **All notes**: newest first, each with the habit name, the day and the text. Notes with no day go last. Tap a note to change its text. A filter row narrows the list to one habit.
    - **Write a note:** the habit page has a **Notes** list for that habit and an **Add note** button (today's date). Long-pressing a day now also has **Add a note for this day**.
    - **Never deleted:** there is no delete button. Saving an empty text is refused, so a note cannot be wiped by accident. Notes on archived habits stay and still show.
    - **Streak source:** while you use Import from Streak, a note you change that came from Streak is put back to Streak's text by the next import (the same limit as habit edits, removed in H5). Notes you write here are new and are kept.
    - `HabitNotes.kt` holds the rules (sorting, filtering, new and edited notes) and is unit tested.
- **H4 Settings and tap to open. Built.** A **Habits** row in Settings (under "Your data") with the choice **This app's own data** (the default) or **Import from Streak**.
  - **This app's own data:** the app no longer imports Streak's habits, their days, categories or habit notes, so nothing you edit here is put back. Habits you already have stay as they are. Streak's to-dos and focus sessions are still imported as before. You do not need Streak installed.
  - **Who starts on which:** a new install starts on your own data. If you had already imported from Streak before this choice existed and have not chosen, you stay on Streak so your habits keep coming in.
  - **Import from Streak:** switching asks first, then imports Streak's newest backup straight away, and later backups as before. For now, a habit you changed here that also exists in Streak goes back to Streak's version at each import; days you ticked here are kept. H5 removes this limit.
  - **Tap a card:** with Streak chosen, tapping a habit opens Streak on that exact habit (Streak accepts a habit ID; if Streak is not installed or cannot be opened, a short message says so). With your own data chosen, tapping opens this app's habit page. In both cases pressing and holding the card opens this app's habit page (edit, archive). Ticking and long-pressing days work the same as before.
- **H5 Your edits reach Streak (four small steps).** Streak can read other devices' backups from a shared folder ("Keep devices in sync"). This app writes its own Streak-format backup file there, under its own device name, and Streak merges it like a second phone. I read Streak's sync code (`folder_sync.dart`, `backup_service.dart`) to see exactly what it accepts. What it means for you:
  - **Streak reads the file only when it checks the folder** (when Streak opens and on its own sync timer), so changes arrive in Streak a little later, not instantly.
  - **What reaches Streak:** new habits, changes to a habit (name, colour, schedule, archive), new ticks and bigger amounts, and new notes. A habit changed in both places: the newer change wins, and the days from both are combined.
  - **What does not reach Streak:** clearing a day or lowering an amount here. Streak's merge only ever adds days and keeps the bigger amount, so a day you un-tick here stays ticked in Streak. Reasons for a missed day: if Streak already has a reason for that day, Streak's stays.
  - **Folder:** Streak keeps its sync folder as a normal folder path. This app needs to be allowed to write to the same folder, so you pick it again once in Settings with write permission (today it is read only).
  - **H5a Writer. Built.** `StreakBackupWriter.kt` turns changed habits, their days, notes and categories into a Streak backup file (`streak_backup_<date>_<time>_<device>.json`), with a "last changed" stamp for each item. It only includes what you changed, so Streak's other habits are never overwritten. Tested with sample data; nothing is written to your phone yet.
  - **H5b Remember what changed. Built.** Every new habit, edit, tick, amount, miss reason, archive and note written here notes "this changed at this time" (`HabitSyncState.kt`, kept in the app's own settings, no database change), so H5a knows what to send. The note only ever moves forward in time. Clearing a day also notes the habit as changed (harmless: Streak keeps its own copy of that day anyway). Things that come in from a Streak import are not noted, so Streak's own habits are never sent back. The app's own device name (eight letters and digits, like Streak's) is made once, the first time it is needed. Nothing is sent anywhere yet.
  - **H5c Write into the folder. Built.** `StreakFolderWriter.kt`. In **Settings, Habits** there is now a **Send your changes to Streak** part: the folder name, whether writing is allowed, when it was last sent, **Choose the folder again** (the same Streak folder; this time Android is asked for write permission, which is needed once because the folder was chosen read only before) and **Send to Streak now**. The file is written whole under a name Streak ignores (`....json.part`) and then renamed to `streak_backup_<date>_<time>_<device>.json`, and its size is checked. The app keeps its own newest three files and removes its older ones; Streak's files are never touched. Sending the same content twice is skipped ("Already sent"). The app's own importer now ignores files with this app's device name, so it can never read its own file back as Streak's. You send by pressing the button for now; the automatic sending is H5d.
  - **H5d Send automatically. Built.** In **Settings, Habits**, under **Send your changes to Streak**, a **Send automatically** switch (off until you turn it on). When it is on, every change here asks for a send 15 seconds later (`StreakSendWorker.kt`, a background job that survives the app closing); a newer change restarts that wait, so ticking several days in a row writes one file. The app also sends when it opens, after it has read Streak's newest backup. If a send fails it tries again up to three times, then shows "Not sent" with the reason. The screen shows the result of the last automatic send. Turning the switch on sends straight away. Sending the same content twice is skipped. Streak's owner is adding "save the backup after every change" in Streak, so changes made in Streak come back here faster; nothing more is needed on this side.

**Paper redesign (P1 to P7, one part at a time).** The whole app takes on the look of your paper demo (`Life-Tracker-paper.html`): cream paper, ink-black buttons, green only for "good", orange highlighter swipes under headings, hand-drawn wobbly card borders with a hard pencil shadow, tally strokes for progress bars, dashed lines for what the phone collected on its own, a floating menu bar, and a dark version. This is looks only: the app keeps exactly the features it has, and the demo's made-up numbers are replaced by your real data. Features that only exist in the demo (Money's "Deduct it from" choice and imported shopping orders, the built-in food tracker) are not part of it. Habits keeps everything built so far (editing Streak habits, sending changes to Streak) in the demo's style. I cannot run the app here, so after each part you send a screenshot and I fix what differs from the demo.
- **The paper is a wallpaper tile, not drawn live.** The demo makes its paper from code every frame. On a phone that would drain the battery and stutter while scrolling, so the same texture is rendered once as a small picture (`paper_tooth.webp`, the grain and loose fibres; `paper_stain.webp`, the age blotches) and the app repeats it, like wallpaper. It looks the same and costs almost nothing.
- **P1 Foundation. Built.**
  - **Colours:** the demo's light and dark colours (`PaperColors`), mapped onto the app's Material colours so every existing screen already picks them up. Primary buttons are ink-black, like the demo.
  - **Font:** Space Mono (regular and bold). The app looks for it in `app/src/main/assets/fonts/` (`SpaceMono-Regular.ttf`, `SpaceMono-Bold.ttf`) and falls back to the phone's own monospace font if they are ever missing. Both files are now in the app.
  - **Paper:** `PaperBackground` draws the cream sheet, the two fold lines, the blotches and the dark edge shading, then lays the grain over everything (multiplied, like the demo). Dark mode has no blotches or folds, as in the demo.
  - **Shapes and parts:** wobbly corners (`WobbleShape`), `PaperCard`, `PaperButton` (dark, ghost, danger), `PaperPill`, `PaperSegmented`, `PaperToggle`, `PaperIconButton`, `HighlightedHeading` (the orange swipe), `TallyBar` (progress in tally strokes).
  - **Menu bar:** the bottom bar becomes the floating paper pill with the six tabs.
  - **Theme switch:** Settings, **Look**: Light, Dark or System (`ThemeMode.kt`, tested).
  - **Not in P1:** the screens themselves keep their old layouts until their own part, so the app looks half new in between: new colours, font, paper and menu bar everywhere, new layouts one screen at a time.
- **P2 Timeline (four small steps).** Same data and same taps as now; only the look changes. Nothing the Timeline can do today is removed.
  - **P2a Top of the page. Built.** A big title (Today, Yesterday, Tomorrow, or the weekday) with the full date under it; on today, the sun arc with "5h 36m left today"; the week strip as paper tiles (today has a pen ring, a day with entries has an orange dot, the chosen day is inked black); the Timeline / Pie switch; and the Steps / Phone / Studied card with dashed dividers. The old "Saved on this phone" badge goes (the demo has none; Settings still says it).
  - **P2b The whole day. Built, then removed at your request.** The 24-hour Where / What strip card was built and then taken out again after you saw it on your phone. What stays from P2b: the row of activity pills with the time spent on each, and the highlighted section headings.
  - **P2c The In order list. Built.** Place blocks (coloured side bar with the place name, wobbly body), rows with the coloured line (solid = you logged it, dashed = the phone collected it), "No data" gaps with Fill in, trips with the "How did you go?" chips, to-dos and notes. All taps stay: edit, tick a to-do, fill a gap, pick how you travelled.
  - **P2d Pie, Log button, Log sheet. Built.** The Log button is the paper one (ink-black, wobbly, pencil shadow). The Log sheet and the Timeline's pop-ups (edit a stretch, hidden items, delete, how did you travel) use new shared paper parts that every later screen will reuse: `PaperSheet` (a bottom sheet), `PaperField` (a labelled text box), `PaperDialog` (a pop-up with paper buttons) and `PaperFab`. The Pie is now drawn like the demo: **what you did is the inner pie, where you were is the outer ring**, sized by share of the recorded time (not by clock position; the 24-hour strip above is the clock view). Tap a slice or a row to see it named with its share and what it overlapped ("at Home 2h", "doing Study 1h"); the rest fades. Under it: the Tasks list, the Places list, and "How you got around" from your trips. The old two-ring "you logged / collected for you" pie is gone; the solid and dashed lines in the list still tell those apart. Pure sums are in `DayPieMath.kt` and tested.
- **P3 Habits (three small steps).** Same habits, same ticking, same long-press, same Streak behaviour; only the look changes.
  - **P3 rule (your words: "don't copy the habit page, we have better"):** the Habits layout stays exactly as it is in this app (Streak-classic cards, week row, month and year views, strips). Only the paper skin is applied: colours, font, wobbly outlines, pencil shadows, paper buttons and pills. The demo's own Habits layout is not copied.
  - **P3a Top of the page. Built.** The big "Habits" title with round paper buttons (All notes, collapse or expand the cards) and a dark **New** button. Under it the Streak strip: a small orange chip and one line saying the real state, with a button. When your habits are this app's own, it says **Shared with Streak** and shows when the last send happened (or why nothing was sent), and the button is **Write now** (the same as "Send to Streak now" in Settings). When your habits come from Streak, it says **From Streak** instead. Then the Today card with the progress ring (green), the Week / Month / Year switch, the category pills, the "No habits yet" card, and the Archived habits link and rows.
  - **P3b The habit cards. Built.** A paper card per habit: a letter tile in the habit's colour, the name, the flame and streak, the amount and category, a strength bar of tally strokes with its percent, and the round tick / plus / ban button. The days under it become paper tiles: done = the habit's colour with white text, part-done lighter, clean days soft, a relapse red, paused blue, empty days worn paper, today ringed in pen. Same for Week, Month, Year and the small cards' thin strips.
  - **P3c Habit page and pop-ups. Built.** The habit page (numbers with dashed dividers, notes, Archive / Edit / Close), the long-press day menu, the editor, the All notes page, the note box and the relapse question, all on the paper sheets and dialogs from P2.
- **P4 Food (three small steps). Built.** Same meals, same water, same goals, same "Eat again"; only the look changes. The demo's source chooser, "Import from OpenNutriTracker" bar, barcode button, "Link from Streaks" habits and edit-a-meal sheet are NOT built: this app has none of them (it reads your OpenNutriTracker backup automatically, and tapping a meal still offers Delete only).
  - **P4a Top of the page.** The big title (Today, Yesterday, Tomorrow, or the weekday) with the full date under it, then a paper month calendar: ‹ month › arrows, weekday letters, a tile per day (today ringed in pen, the chosen day inked black, days after today faded, a small square mark on days with food logged). Tap a day to open it. A "Today" pill appears when you are on another day, and the small day-by-day ‹ › buttons stay next to the day's heading, so nothing the old Previous / Next buttons did is lost. The month grid is worked out in `FoodCalendar.kt` and tested.
  - **P4b The day card and water.** One paper card: the kcal eaten in big type, "left" in green or "over" in red against the goal, a coloured bar split by meal (breakfast amber, lunch green, dinner indigo, snack pink; each piece sized by its calories against the goal, or against what you ate if that is more), then Carbs, Fat and Protein with tally-stroke bars. Water is its own paper card with a tally bar, the same +250 ml / +500 ml / Undo buttons in paper style. The bar pieces are worked out in `FoodStats.fuelShares` and tested.
  - **P4c Meals, Add sheet, delete.** "What I ate" heading with the item count, one paper card per meal type with its coloured side bar, the kcal total and rows showing name, grams and C / F / P. The Add meal button is the paper one; the sheet uses `PaperSheet`, `PaperField`, pills for Eat again and the meal type; tapping a meal asks "Delete this meal?" in a paper pop-up. Add and delete work exactly as before.
- **P5 Money. Planned.** The month card, sorting, the entry sheets.
- **P6 Stats. Planned.** The charts, the 20-week heatmap, and every tab.
- **P7 Settings (done before Food, Money and Stats, your order). Planned, nothing built yet.** Same rule as Habits: only what exists in this app gets the paper skin; its layout and every switch, button and permission stay. The demo's Settings parts that this app does not have are NOT built (listed below).
  - **What stays out (demo only):** a Timeline settings page (gaps, grouping repeated opens, remember my category changes), the Food page (choose OpenNutriTracker or a built-in tracker), the Money page (shopping apps, "where did it come from"), the hidden-apps list as its own row, and the demo's invented numbers.
  - **P7a The Settings tab. Built.** Paper title "Settings" and the paper rows: each row is a paper card with its name, one quiet line, the real state as a small chip (green only for "good": Allowed, Watching, Recording; red for "Not allowed yet" or "Switched off") and a chevron. The groups keep this app's order: What the phone records, Your data. The Look choice (System / Light / Dark) stays where it is as a paper switch.
  - **P7b Shared parts. Built.** How: `PaperCompat.kt` holds paper versions of Button, OutlinedButton, TextButton, FilterChip, Switch, AlertDialog and OutlinedTextField with the same names and options as the Material ones; each Settings page simply stops importing the Material ones, so every button, switch, pill, pop-up and text box on those pages changes look while doing exactly what it did. Also new: the page header (big title with a quiet line), section headings with the orange swipe, the "needs switching on" note and the switch rows. One restyle of the pieces every settings page is built from, so all pages change together: the page header with its Back button, section headings (orange swipe), switch rows (paper switch, label wraps, dashed dividers inside a card), the "needs switching on" note, choice rows as paper pills or a paper switch, buttons, pop-ups. The Stats pages share these, so their headers and switches also change (the rest of Stats waits for P6).
  - **P7c Phone usage, Steps and sleep. Built.** The settings halves: usage access, what shows on the Timeline, the linked-apps list, Health Connect, the step counter, how sleep is worked out.
  - **P7d Places and trips. Built.** The biggest page: permissions, your places and Add a place (sheet), how closely to watch, keeping the app awake, status, GPX and CSV files.
  - **P7e Calls and Charging. Built.** Call log access, the shortest call to show (1, 2, 5, 10 minutes), copy older calls; charging record, the banner, notifications.
  - **P7f Activities and apps. Built.** The activities list, the activity editor and the app editor as paper sheets with labelled boxes and pills; hide / show stays.
  - **P7g Habits, Backup and import. Built.** The Habits choice (own data or Streak), Send to Streak, the folder pickers, backups and restore (its confirm pop-ups), importing Streak and OpenNutriTracker.
  - **How each part goes:** README first, then code, then CHANGELOG, then the build check, then one PR. After each part you send screenshots and I fix what differs. Nothing is built until you say go.
- **P7h Settings 1:1 with your demo (your answer: "Yes, match the demo's layout"). Built (P7h-a to P7h-g).** P7a to P7g gave Settings the paper skin but kept this app's own layouts, so it is not 1:1. This second pass moves the layout to the demo (`Life-Tracker-paper-6.html`, the same file as before) for everything this app already has. Rules: only features that exist; behaviour never changes; nothing is deleted.
  - **What changes everywhere (P7h-a):** the demo's own line icons (drawn as vector icons from the demo's paths); the page top with a round-cornered "Back" and a big title with a quiet line under it; list rows with an icon, name, a small line and a chevron; switches inside one card with dashed dividers and a hint under each name; the switch is ink-black when on (not green); grey-green "lock" notes become the demo's orange-tinted tilted note; key / value lines with dashed dividers.
  - **P7h-a The Settings tab:** four groups as in the demo: Timeline (Activities only; the demo's Timeline page and "Apps and categories" page do not exist here), Collected from your phone (Phone and usage, Sleep and steps, Places and trips, Calls, Charging), Trackers (Habits; Food and Money have no settings here, so they are left out), This app (Appearance and backups). Subtitle "Everything that is not data".
  - **P7h-b Phone usage, Steps and sleep. Built.** the switch card, "Leave out stretches shorter than" as a connected strip (1, 2, 5, 10 minutes as now), the Health Connect row, notes in the demo's note style.
  - **P7h-c Places and trips. Built.** the demo's order: Watch my places, Status (keep it running), Record my trips as a strip, "Cleaner readings" (only the options this app has), On the Timeline switches, Your places with Add a place.
  - **P7h-d Calls, Charging. Built.** switch cards and strips; Charging's "How it works" as the demo's open-and-close sections.
  - **P7h-e Activities. Built.** a list with the colour square and a pencil, a full-width dark **New activity** button, the editor as a bottom sheet (name, colours, and for an existing one a "Hide from Log" switch that is saved with Save), hidden ones under "Hidden from Log" (dimmed, with a crossed-out eye). Nothing logged is touched; hiding only removes it from the Log pop-up.
  - **P7h-f Habits. Built.** "Track habits with" as a two-way strip (Streak / This app; choosing Streak still asks first, as now), a short card that explains the choice, the Streak details as key / value lines (app installed, backup folder, last sent, writing allowed), the "Send automatically" switch, and the rows Send to Streak now and Change folder, then the tilted note. Not built: the demo's "Ask me again next time" (this app has no first-time question) and its read-only Streak switch.
  - **P7h-g Appearance, Backups and imports. Built.** You already split these into two pages, so they stay two pages, now in the demo's look. Appearance: the theme as a strip under a "Theme" heading, your home-screen name and icon tools and saved designs kept as they are (with paper buttons). Backups and imports: the tilted note "Everything stays on this phone", Backups with key / value lines and Back up now / Restore, then Streak and OpenNutriTracker as cards with folder, newest file, last import and Import now. Export (Readings GPX, Readings CSV, Trips GPX) and the GPX import now live here too, moved from Places and trips; they work exactly as before (the demo's list of found GPX files and day ranges are not built).

### Timeline rework (your answers: Option A, 1 hour = 60 dp, 5 minutes, suggest names, "also change past days")

Why: phone usage showed less than Digital Wellbeing (Wed 14 Oct 6h 20m vs 7h 24m; Thu 15 Oct 4h 48m vs 6h 07m), the Phone pill under the numbers showed more (8h 57m on Wed), Fill in put the new entry above the place, lines were not drawn to scale, "No data" was shown when the phone was simply not used, and short walks showed too much distance. Rules for all parts: nothing logged or collected is ever deleted; naming rules are applied when the day is drawn, so they also fix past days; no caps or minimums on the line length.

- **T-A Fill in lands inside the place.** An entry belongs to a stay when most of it overlaps the stay (a few minutes of tolerance at each edge), not only when it starts after the stay began. Entries made with Fill in are never left outside the place whose gap they filled, and the gap disappears. A test covers entries starting seconds before a stay, straddling two stays, and one inside a trip.
- **T-B Correct phone usage.** Digital Wellbeing cannot be read by other apps; the best match is reading Android's usage events carefully. Fixes: ignore "stopped" events (they arrive late and cut sessions short when you move between screens of one app); follow one screen at a time and close it only when that same screen is paused; also close on screen off, screen lock and shut down; end a session left open by a phone restart at its last event. The Phone number, the pill, the Timeline and the Phone usage page all use the same time (the pill used first-to-last use before, so it was too big). A small "check" shows our total next to Android's own daily total so you can compare with Digital Wellbeing. **Built.** The pie and the pills now count the stretches of real use inside each block, not the whole span. The first sync after this update builds again the days Android still remembers (about a week) with the new rules; days it no longer remembers are left exactly as they are, and every backup keeps the old numbers.
- **T-C Periods of use.** A period is a run of phone use. It ends after 5 minutes of no use, or when an app from a different category starts (unless the apps are a combination you named). Its category: a named combination if one matches (built in T-D; the 20% rule for small interruptions comes with it); else the shared category if all apps have the same one; else Screen and leisure. Every app's time inside the period counts towards the period's category, so ChatGPT during a named Study combination counts as Study. While a period is still running or unknown, the Timeline shows just the apps and their own categories, and updates when a rule matches. Each row shows the activity, the total, and one line per app with "opened N times" and minutes. Pure logic with unit tests (midnight, A-B-A-B switching, order of apps, ignored apps, exactly 5 minutes).
- **T-D Name and suggest combinations.** Steps: (1) a small table of the combinations you named (name, apps, category), database version 16 with a migration, and backup version 16 that keeps them (restoring only adds). (2) The rule: a combination applies when at least two of its apps (all, if it has fewer) were used and they make up at least 80% of the period; an app of another category used for less than 20% of the period in between does not split it. (3) A pop-up from the Timeline row ("Always count this combination as..." / "Change this combination") to name it, choose its apps and its category; it applies to every day, past and future, because periods are worked out when a day is drawn. (4) Suggestions on the Phone usage settings page: the same apps together at least 3 times over 2 or more days, or a built-in example (matched by app name): Study = Physics Wallah (PW) + ChatGPT; Building = Claude + Brave (+ GitHub); Travel = maps + a ride or train app; Shopping = a shopping app + a payment app; Banking = a bank app + a payment app; Reading = a reading app + a browser; Writing = a notes or docs app + an AI app; Chatting = two chat apps. A suggestion uses the activity with the same name if you have one. "Not now" hides a suggestion; nothing is ever named without you confirming. (5) Unit tests for matching, the 20% rule, suggestions and the backup round trip.
- **T-E Timeline to scale.** Calendar style. Steps: (1) the day is one ruler: an hour line every 60 dp (1 minute = 1 dp, no minimum, no maximum), hours labelled on the left. (2) The coloured lines are exact: a place bar, the line for a journey and a bar for every phone period, call, sleep and so on start and end at their real times; things with no length (a note, a logged moment) are a dot. (3) Things that overlap in time sit side by side in their own lanes (lanes are worked out by a tested function). (4) Cards sit beside the lines at their start time; when two cards would touch, the later one moves down just enough, and the time shown on each card is always the real one. (5) Gaps are hatched at their true length; their card shows the length and "+ Fill in" on a separate line, which also fixes the squeezed "No da ta" text. (6) The day runs from the hour before the first thing to the hour after the last (or now). Tests for the lane function.
- **T-F Offline / Phone off / No data.** The app also records when the screen turns on and off. Screen stayed off = **Offline**; phone shut down = **Phone off**; **No data** only when the screen was on and the app cannot tell what you used. The gap row gets a layout where the text is no longer squeezed (done in T-E). Steps: (1) a small table of these off periods (kind, start, end), database version 17 with a migration, and backup version 17 that keeps them (restoring only adds). (2) The sync reads the screen-on event as well, builds the off periods from the events (a shut down ends the screen-off period and starts a Phone off period until the phone starts again; a phone that died ends at its last event), and builds again the days Android still remembers. An off period still going is extended by the next sync. (3) A stretch with nothing known is cut at the off periods: the part inside one is Offline or Phone off, the rest stays No data. Pieces under 10 minutes are not shown, as before. (4) Offline and Phone off are drawn as quiet outlines, No data stays hatched; each card has its own title and note. (5) Unit tests for building the off periods, cutting a gap, and the backup round trip.
- **T-G Walking distance.** Steps: (1) while a trip is being recorded, each position reading also notes the phone's step counter (database version 18, a new column on the trip readings with -1 for "not known", backup version 18 keeps it). (2) The steps of a trip are the counter at its last reading minus the counter at its first; a restart of the phone (counter going back) means "not known". (3) A trip you travelled on foot gets its distance from steps times your step length when the GPS distance disagrees by more than a third (GPS drift usually adds distance), otherwise the GPS distance stays. (4) Speed check against the way you travelled: a walk cannot be faster than 8 km/h, a run 20 km/h, a cycle 35 km/h, so the distance is cut to what that speed allows (823 m in 2 minutes cannot be a walk). (5) Your step length starts at your height times 0.415 (0.70 m until you give your height) and is learned from your own long walks where the GPS and the steps agree. (6) Changing the way you travelled works the distance out again at once, and so does the daily tidy-up. (7) Unit tests for all of it. Not in this step: moving the start and end of a trip to the last and first reading at a place (needs the readings before the trip, comes with the Places settings page), and the height box (T-H).
- **T-I Clean Timeline (your screenshots of 17 and 18 October showed it broken).** What was wrong: every line was drawn to exact size (1 minute = 1 dp) but every card was 110 to 370 dp tall, so cards were pushed down and the hour ruler fell hours behind them (a 2:04 am card beside the 9:00 label). Each card also repeated a second time column next to the ruler, so the page showed two different times. Your decision: short rows with a minimum size, tap to expand in the timeline, and a clean look. Steps:
  - **(1) One time map (`TimeWarp.kt`, tested).** Rows keep their real order and start time; each row is at least as tall as its minimum, and where that is more than its real time the page makes room by stretching that stretch of time. Lines, rows and hour labels all use the same map, so they can never disagree. Where nothing is crowded the scale is exactly 1 hour = 60 dp. Where it is crowded (many short things in one hour) the hour labels sit further apart than 60 dp; that is the only change. The tests use a busy day of about 70 items and check that every line starts where its row starts, nothing overlaps, and the hour labels never go backwards.
  - **(2) One row per thing.** Minimum sizes: 44 dp for a thing, place or journey, 26 dp for Offline, Phone off and No data. A row is one line of title and one line of "start to end · category", with the length on the right. No separate time column; the time is written once. Text is ellipsised, never wrapped, and the line spacing is tightened.
  - **(3) Tap to expand in place.** Tapping a row opens it inside the timeline: every app with its time and how many times it was opened, the category, and an Edit button. The row, its line and everything below move down together and the ruler follows. Tap again to close. A row with nothing more to show (a note, an empty to-do) opens its edit pop-up directly, as before. Unknown journeys open to show "How did you go?" with the buttons; known journeys still open the same pop-up.
  - **(4) Offline, Phone off and No data** are soft grey, one small line, no outline and no button. Opening one shows what it is and the Fill in button.
  - **(5) Calmer drawing.** Hour ticks only across the lines (no line cutting through rows), softer outlines, the "In order" hint says "tap a row to open it".
  - **(6) Tests:** `TimeWarpTest` (see 1). Nothing saved is changed; this is only how a day is drawn.
  - **(7) Tidy-up after your screenshots of 19 October.** Rows were cutting off the numbers that matter ("Walk (guess) · 2m · 12…", "No data · 12:00-12:28 am · …"). Now the title has the full width and the length comes first in the second line ("24m · 12:42-1:07 pm · Screen and leisure"); a journey's title is only its way of travel, with length and distance first in the second line; Offline, Phone off and No data say the length before the times. A to-do still ticks off in one tap. The footer text under the list now describes opening a row.

**Round 3: your list of 19 October (plan, one step at a time).** You asked for seven changes and went to sleep, so these are the steps I work through, in this order, each one README first, then code, CHANGELOG, CI and a pull request. Streak's own code (github.com/InlitX/streak) was read for how it does colours and icons.
- **R1 Thicker lines, no phone-maker help text.** (1) The lines on the Timeline that stand for time and place get thicker: things and places 6 dp to 9 dp, journeys 2.5 dp to 4 dp, moments a bigger dot, with the lanes a little wider. (2) Every mention of one phone maker goes: the Places "Keep it running" text becomes plain Android wording, the **Autostart** and **Battery saver** buttons (they opened one maker's own screens) are removed and **Allow unrestricted** (Android's own question) and **Open this app's settings** stay, the "If visits never appear" and "If the banner never appears" notes lose the brand names, `KeepAlive.kt` loses those screens, and this README and the CHANGELOG are reworded. The few phone-maker launcher names in the list of screens that must not count as app use (`UsageCollector.kt`) stay: they are not shown anywhere and removing them would make the home screen count as phone use.
- **R2 The edit window.** Today, editing anything on the Timeline opens a small pop-up. It becomes a full-screen page like Streak's habit editor: a back arrow, the title, **Save** at the top, one scrolling body, and **Delete** or **Back to as recorded** at the bottom. (1) A shared `EditWindow` page. (2) A phone period shows every app in it (time and how many times opened) and lets you change its start, end and category, set the app's category for every day, or hide it. (3) A place stay, a journey, a log entry, a meal, a call, a charging session and a sleep night use the same page with their own fields. (4) The open-in-place panel I built in T-I is removed (you did not like it): tapping a row opens this window; a to-do still ticks off in one tap. (5) Pure parts (what the fields start as, what Save changes) are tested.
- **R3 Colours and icons the Streak way.** (1) A colour picker like Streak's: a grid of 8-wide swatches plus a **Custom** tab with a colour ring. (2) An icon picker like Streak's: an **Icons | Emojis** switch, category chips (Fitness, Study, Home, Mind, Food, Work, Social, Finance, Nature, Tech), and tiles. Streak keeps an icon as a name (like "dumbbell") or an emoji; the same names are used here. Streak draws them from the Lucide icon font; that download was blocked while I was building this (the package sites were not reachable), so the picture for each name is taken from the free Material Design Icons set (Pictogrammers Free License, which allows use in apps) that was already on the build machine, and every one of Streak's 293 names has a picture. The drawings are stored as small text in the app (`res/raw/icon_paths.txt`), so nothing is downloaded when you use it. If you want Streak's exact Lucide pictures later, only that one file changes. This also draws the habit icons that came from Streak, which until now showed as a first letter. (3) Database 19: an app and an activity can each have an icon. (4) The pickers appear when you set an app's category or make or change an activity; icons show on the Timeline rows, the Activities list and the Apps page. (5) Tested: name to icon lookup, emoji detection.
- **R4 Place categories with colours.** (1) Database 21: a list of place categories, each with a name, a colour and an icon; Home, Study and Other are already there and you can add your own (Gym, Friend's house, ...). Places keep their current category by name, so nothing is lost. (2) Settings > Places and trips > **Place categories** lists them and edits them with the R3 pickers. (3) The Place editor picks from them. (4) On the Timeline a place's line takes its category's colour (a category you chose for one stay still wins). (5) Tested.
- **R5 Permissions.** (1) Settings gets a **Permissions** page. Every permission card from Phone usage, Steps and sleep, Places, Calls and Charging moves there: usage access, location (all the time), physical activity, call log, notifications, unrestricted battery, Health Connect and the Streak and OpenNutriTracker folders. Each card says whether it is allowed, **why the app needs it**, **how to switch it on**, and has the button that opens the right Android screen. (2) The pages keep one small line ("Needs usage access, see Settings > Permissions"). (3) **Usage access** has an extra step for "Allow restricted settings": on Android 17 open the app's page in Android Settings, tap the three dots at the top right and choose Allow restricted settings; on earlier Android versions it is at the very bottom of that page, below everything else (this is how you described it; tell me if your phone shows it differently). (4) Backups, behaviour and all switches are unchanged.
- **R6 Money.** (1) Database 22: each entry says where it was deducted from: **My allowance**, **Someone else paid** (with who) or **Not sure** (waits under "Where did it come from?" until you decide). Someone else's payment is a logged expense that does not touch your allowance. (2) Expense categories and income categories are separate lists you can add to, rename, recolour and give an icon (R3 pickers). (3) Every entry, category and allowance can be edited or deleted, in the R2 window style. (4) The Money tab looks like your paper demo (this is Paper P5): the month card, sorting, quick-add chips, entry sheets. (5) Tested: what counts against the allowance, monthly totals with the three sources.
- **R2b Timeline corrections (your 19 Oct afternoon message).** You liked the version where a row opens in place, so: (1) **Tap a row** opens it in place again (the R2 change that removed this is undone); **long-press a row** opens the full-screen edit page from R2. (2) **Back-to-back No data, Offline or Phone off stretches show as one row** ("No data · 55m · 12:00-1:01"); opening it lists each stretch with its own Fill in button. (3) **Activities sit inside their place** like your HTML: a soft box around each stay with its name and time on top and the things that happened there inside, and the place line becomes a **thick rounded bar with the place name written sideways** (like the demo). (4) **An unknown way of travel shows its suggestions at once** (Walked, Cycled, Bus, Drove, Auto, Metro, Train) under the journey, as in the demo, without opening it. (5) **A small quiet bolt at the right of a row** when the phone was charging at some point during it; opening the row says for how long. (6) **Every kind of row gets an icon by default** (calls get a phone, charging a bolt, sleep a moon, meals a fork and knife, apps a phone) unless you picked one. (7) **More built-in activities, each with an icon** (database 20): Work, Chores, Cooking, Reading, Meditation, Friends, Family, Shopping, Creative, Movies and shows, Gaming, Music, Outdoors, Self-care, Errands, Coding, Writing, Rest, Travel and Money are added next to the eight you have, and the eight get icons. An activity you already have with the same name is left alone and nothing is renamed or removed. (8) Tested: merging of stretches, the charging overlap, and the list of built-in activities.
- **Assumptions I made while you slept (change any in the morning):** (a) "the timeline window you set before" is the open-in-place panel from T-I (you left one open in your last screenshot). (b) Nothing about one phone maker remains in what you see; the launcher names stay as written above. (c) The Android 17 / earlier-version wording for restricted settings follows your message. (d) Icons for apps and activities are Streak's names and emoji, not the demo's little drawings. (e) Place categories are a new list, and the old Home / Study / Other places keep their category by name.
- **T-H Settings pages.** Steps: (1) **Apps and categories** page (Settings > Timeline): every app the phone has seen, with a "Counts as ..." pill; tap it to pick the category or hide the app; hidden apps are listed below so they can be brought back. (2) **Timeline** settings page with four switches, all on by default: *Show gaps* (off hides the "No data / Offline / Phone off" stretches, nothing else), *Group repeated opens* (off shows every stretch of app use as its own row instead of one period), *Ask how I travelled* (off hides the quick "Walked / Cycled / Bus..." buttons under journeys with an unknown way of travel), *Remember my category changes* (on: when you give a single-app period a category on the Timeline, that app counts as it on every day too). The switches are read each time the Timeline is shown. (3) In Places and trips a **Walking** group: your height (step length starts at height x 0.415), the step length the app has learned, and a button to start learning again. (4) Tests for the Show-gaps switch; the rest is screens. Nothing here changes saved data.
  - **Not built (demo only):** Food and Money settings, GPS-jump and motion-sensor switches if this app has no such option, CSV choices and day ranges for export.
  - **How each part goes:** README first (this section), then code, CHANGELOG, build check, one PR; you send screenshots and I fix differences.

**Step 14: Life events, month and year views.** Mark moments that changed your life, then look back by month or year to see what you did and how much you studied around each one.

**Step 15: Stats.** Choose any metric and view it by day, week, month or year, with a heatmap and short written insights. This comes late on purpose: it needs the other steps' data to exist first.

**Step 16: Polish.** Reminders, search, smoother screens, and anything learned from using the app every day.

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
        │       └── EventsScreen.kt                (planned, Step 14)
        │
        └── res/                       ── Look and feel ──
            ├── drawable/              Icons: six tab icons, add, settings, the three day-number icons, the launcher icon
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
2. Open the release marked **Latest** (or a **Test build** at the top, if you were told to try one) and download `life-tracker.apk`.
3. Tap the downloaded file. Android will ask once to allow installs from your browser or Files app. Allow it.
4. Google Play Protect may warn that the app is from outside the Play Store. That is expected for your own app.

A new version installs over the old one and keeps your data.

---

## How a new version gets built

No computer is needed. GitHub builds the app in the cloud.

1. A change is pushed to this repository.
2. GitHub Actions runs `build.yml`, which runs the tests and then builds a signed release APK (a few minutes). If a test fails, no APK is published.
3. The APK is published as a release named `build-N`, where `N` is the run number. A build from `main` (a merged change) is called **Life Tracker build N** and carries the **Latest** label. A build from any other branch is a **pre-release** called **Test build N (branch)**, so the Latest label always points at the newest merged version.
4. You download it from the Releases page and install it.

The run number also becomes the app's internal version code, so every build counts as newer than the last.

**The release page, like Streak's.** Each release has a big heading ("Life Tracker build N"), then **what's new** in plain words, then **Download**. The "what's new" text comes from `CHANGELOG.md`: every section with a `## ` heading that was added since the last full release is shown, newest first, with `### ✨ New features`, `### 🛠 Improvements` and `### 🐞 Fixed` inside. So each change adds one section at the top of `CHANGELOG.md`, and the release shows it. The page is written by `.github/scripts/release-notes.sh`. If the last release cannot be worked out, only the newest section is shown.

**About the signing key.** `keystore/lifetracker.p12` is committed on purpose. It exists only so that updates install over older versions. The password is in `app/build.gradle.kts`. **Keep this repository private.** If it is ever made public, create a new key, then uninstall and reinstall the app once, after making a backup.

---

## Rules the project lives by

1. **Your data stays on your phone.** No accounts, no servers, no tracking.
2. **No data is ever lost by an update.** Every change to the database structure ships with a proper migration. Wiping and rebuilding the database is never allowed.
3. **Small steps you can try.** Each roadmap step ends with something you can install and use.
4. **Plain and quiet.** No notifications, ads or nagging unless you ask for them.
5. **Built when you say so.** Work on a step begins only when the owner asks for it.
