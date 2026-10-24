# What's new in Life Tracker

Every release page shows the sections below that were added since the last release, newest first. Older sections stay here as the history.

## 🧭 Places and trips remake: pause zones (C5)

### ✨ New

- **Pause zone in the place editor** (Add a place, Name this spot and Edit place). Turn on **Pause recording here** and GPS all day stops its dense readings a moment after you arrive; the stay still shows as a visit with its arrival and leaving time. Options: **Pause on these Wi-Fi names** (with an **Add the Wi-Fi I am on** button), **Pause when motionless**, a **Heartbeat** reading every N minutes while paused, and the **delay** before pausing (automatic, or your own seconds). A sentence under the options reads the whole zone back, and a bad value is refused with its reason. A reading at the place's centre is added when you leave, so the next trip starts cleanly.
- The place list says "pause zone" on a place that has it on.

### 🔧 Changed

- The place editor scrolls, because it is longer now.

## 🧭 Places and trips remake: tracking profiles (C4)

### ✨ New

- **Tracking profiles** (Settings > Tracking profiles). A profile is a rule: while something is true, GPS all day records with the profile's pace and accuracy instead of yours. Conditions: charging, car mode, a chosen Bluetooth device connected, any Wi-Fi, a named Wi-Fi, faster than a speed, slower than a speed, still. Each profile sets seconds between readings, the least a reading must have moved, the accuracy level, a priority (the highest wins), and how long the condition must hold before it switches on and stay ended before it switches off. The list shows which profile is in force now, and every rule is read back as a sentence.
- **Hide, never delete.** A hidden profile is kept and can be shown again.
- **Bluetooth devices permission** (Settings > Permissions) lets the app see your paired devices and which are connected, so a profile can switch on when your car or headphones connect. Only names and addresses are read.

### 🔧 Changed

- Profiles only work when Recording is set to GPS all day. Car mode is Android's own car mode; for a car screen with Android Auto use the Bluetooth condition.

## 🧭 Places and trips remake: GPS all day (C3)

### ✨ New

- **Recording: Gentle or GPS all day** (Settings > Places and trips > Recording). Gentle is what the app did before and stays the default. **GPS all day** reads your position all day at the pace you set (seconds between readings, the least a reading must have moved to be kept, the worst accuracy accepted, High / Balanced / Low power) and keeps every good reading, so the path is exact. A notification stays and shows what it is doing, with a **Pause / Resume** button.
- **Pause zones work in the engine.** At a place with Pause here on (the switch itself arrives with the place editor in a later stage), the readings stop after you have been there a moment, and stop altogether while you are on one of its Wi-Fi names or have not moved. A heartbeat reading can be taken every few minutes, and a reading at the place's centre is added when you leave, so the next trip starts cleanly.
- **The diary says why a reading was refused** (too inaccurate, a jump, too close to the last one, out of order). Every kept reading now also keeps its direction, height, battery, step counter and where it came from.

### 🔧 Changed

- Visits and trips still appear on the Timeline as before; in GPS all day the kept readings are given to the same detector. Android's arrived/left messages no longer start the old readings service while GPS all day is on, and the 15-minute check starts the new one if Android stopped it.
- Two new normal permissions (see the Wi-Fi and its name). Nothing else is asked.

## 🧭 Places and trips remake: the new data (C1)

### 🔧 Changed

- **Groundwork only, nothing looks different yet.** Database 27 and backup format 27 prepare the Colota-style Places and trips: every position reading can now also keep its direction, height, battery, step counter, where it came from and which profile was active; there are new places for merges and splits of trips, notes on a single reading and tracking profiles; and every place has room for pause-zone settings (all off).
- **Nothing is removed or moved.** Old trip readings are copied into the one readings table (the old trip table stays as it is). Older backups still restore, and restoring never switches off a pause setting you made later.

## ⏱️ Habits: focus mode

### ✨ New

- **A focus timer for a habit**, as in Streak. Press **Focus** on the Habits tab or on a habit's page. Pick the habit (or none), then **Countdown**, **Stopwatch** or **Pomodoro** (a break after each round; every 4th break is a long one if you set it in Settings > Habits > Focus), the minutes, and what you are working on (your recent labels are buttons).
- **It keeps going with the screen off.** A banner shows the running time with **Pause, +1 min, End** (and **Continue / Skip break** between rounds), and the end rings on the exact second with the phone's alarm sound, even in battery saving. A finished Pomodoro round is saved and the break starts by itself; after a break it waits for **Continue**. A countdown is saved the moment it ends.
- **Sessions are kept** (never deleted), put one block on the Timeline, and go to Streak in its own backup shape (Streak's sessions come in too). A finished round **ticks a yes/no habit** for the day; a habit that **counts minutes** gets the minutes added.
- **Saved timers.** Each habit remembers its own minutes and break (kept in the habit's record, so it travels to Streak), and the recent labels per habit.
- **"Only a focus session can complete it"** (a switch in New habit / Edit habit, like Streak's): the card cannot be ticked by hand (taking a tick back still works).
- A **Focus** card in Settings > Habits: long break, minutes for a session with no habit, and "Count only whole Pomodoro rounds".

### 🔧 Changed

- **Database 26** (a new table for sessions). Backups are format 26 and older backups still restore.
- Not copied from Streak: the music player, backgrounds, clock styles, the to-do list inside a session and the focus statistics pages.

## 🔔 Habits: reminders

### ✨ New

- **Reminders for each habit.** In **New habit / Edit habit**, **Add reminder**: a time, the weekdays it rings on, **every N days**, or **repeat every N hours until a time**, your own message, and how long **Snooze** waits (5, 10 or 15 minutes). A habit can have several. They are kept in the habit's own record, so they travel to and from Streak.
- **The banner** shows the habit's name and your message, with **Done** (a yes/no habit is ticked, a counted or tracking-only habit gets one more check) and **Snooze**. Avoid habits and checklists have no Done button; tapping the banner opens the app.
- **Quiet when done.** A reminder does not ring for a habit already done today, and ticking a habit takes its banner away. It also stays quiet on days the habit is not due and while it is paused. Settings > Habits has **Habit reminders** (all on or off) and **Stay quiet when already done**.
- **Exact alarms.** A new card in **Settings > Permissions** switches on "alarms and reminders" so reminders ring at the minute you chose (Android 14 and newer turn it off for new apps). Without it they still ring, but may be a few minutes late. Reminders need the Notifications permission too.

### 🔧 Changed

- No database change. Alarms are set again after a restart, an update, or a change of clock or time zone.

## 📄 Habits: full-screen pages, reorder and the Today filter

### ✨ New

- **Habit pages are full screens.** Tapping a habit's card (or pressing and holding it) opens its page with a Back button, and **New habit** and **Edit habit** open as full pages too, with **Save** and **Cancel** pinned at the bottom. The phone's Back button closes them. The small questions (the day window, "Delete this habit?", "Log a relapse?") stay as pop-ups.
- **Reorder.** A **Reorder** button on the Habits tab opens a list with **Up** and **Down** buttons. The order is written to each habit's own `order`, so it travels to and from Streak.
- **Today filter.** A **Filter** button with four switches, kept between visits: **Only habits due today**, **Hide completed**, **Hide tracking-only habits** and **Completed last**. Cards are only hidden or moved, never deleted, a note says how many are hidden, and the Today ring still counts every habit that is due.

### 🔧 Changed

- No database change.

## 📅 Habits: schedule extras and difficulty

### ✨ New

- **"Every X" schedules got two switches.** **Count from the last time I did it**: the next due day is X days after the last day it was done. And, for months, **Same weekday each month** (the second Tuesday, the last Friday). A fixed day each month is **Every 1 month**, counting from a day: it is due on that day number, or on the last day of a shorter month.
- **The schedule is said in words** under the form and on the habit page: "On the 14th of every month", "On the second Wednesday of every month", "Every 3 days, counted from the last time done".
- **Difficulty.** Mark a habit Easy, Medium or Hard. Like Streak, it only weighs habits against each other: switch on **Settings, Habits, Weigh the Today ring by difficulty** (off by default) and the ring fills by weight (easy 1, medium 2, hard 3; not set counts as medium). "3 of 5 completed" still counts habits, and a habit's own strength is not changed.

### 🔧 Changed

- No database change. The switches and difficulty live in the habit's own record, so they travel to and from Streak.

## ⏸️ Habits: pause and rest days

### ✨ New

- **Pause a habit.** Press and hold a habit's card and use **Pause** (a holiday or an illness). While paused, days count as neither done nor missed and never break a streak, and the card says "Paused". **Resume** ends the pause yesterday (a pause that began today just disappears), as Streak does.
- **Rest days.** On the same page, pick the weekdays that never count (for example Sunday).
- **Pause one day.** Press and hold a single day for **Pause this day** or **Resume this day**. Resuming a day in the middle of a pause splits it, so only that day comes back.
- Pauses and rest days are kept in the habit's own record in Streak's own shape, so they travel with the habit to and from Streak. Ticks on a paused day are kept and still count.

### 🔧 Changed

- No database change.

## ☑️ Habits: checklists

### ✨ New

- **Checklist habits.** A Yes/no habit can now be made of **steps**. In the habit form, **Steps** lets you add, rename and remove steps. The day is done when every step is ticked, or, with **Any of the steps is enough**, when the number you choose is ticked.
- **Tick steps one by one.** Press and hold a day to tick each step on its own (today's ticks note the time). Tapping the habit's card ticks every step, or clears the day if it was already done. The card shows **2/4 steps**, and the calendar fills as steps are ticked.
- **Steps travel to and from Streak.** The steps live in the habit's own record, and each day keeps which steps were ticked, in Streak's own format. Where both sides have a day, the bigger count wins and brings its steps.

### 🔧 Changed

- Database version 25 and backup format 25: one new column on a habit's day for the ticked steps. Every day you already had stays as it was. A backup made before this version restores with no step lists (a day keeps its amount).
- A removed step stops counting but stays stored in the days it was ticked on, as in Streak. A checklist cannot also be "just tracking".

## ⏱️ Habits: check times, several checks a day, tracking only

### ✨ New

- **Every check keeps its time.** Ticking a habit today notes the time. Press and hold a day to see the time of each check and **change** any of them with the clock. A day ticked in the past has no time until you set one.
- **Several checks in a day.** A counted habit adds one step each tap and keeps each time. An Avoid habit can have more than one relapse a day: tapping the card when you already relapsed opens the day window, where you can **log another**, **take back the last one** or add one **at a time**.
- **Add or replace an amount.** On a counted habit the day window has **Add** (on top of what is there), **Add at a time…**, and **Replace the day's total**. Zero clears the day, as before.
- **A counted habit can have no goal.** Enter 0 for the goal: any amount counts the day and nothing is "short".
- **Tracking only.** A new switch, **Just tracking (no streak)**, for Yes/no and Counted habits. Such a habit has no streak, no strength and no missed days, and is left out of the Today ring. Its card shows **Last Today · 14:05**, **Week** and **Month** totals, and every tap adds one more check. It uses the same switch Streak has, so it travels both ways.
- **Times travel to and from Streak** (Streak calls them marks). Where both sides have a day, the bigger amount still wins and brings its times.

### 🔧 Changed

- Database version 24 and backup format 24: one new column on a habit's day for its times. Every day you already had stays as it was. A backup made before this version restores with no check times.
- Tapping the card of an Avoid habit that already has a relapse today now opens the day window instead of clearing the day.

## 🗑️ Habits: delete and restore, and Streak 2.4 sync

### ✨ New

- **Delete a habit here, and bring it back.** The habit page has **Delete** (it asks first) and **Clear all progress** (also asks first). Nothing is erased: a deleted habit is hidden together with its notes and its days, and **Settings > Habits > Deleted habits and notes** lists it with a **Restore** button. Notes have **Delete note** in the note box and the same list.
- **Clear all progress** hides every day up to today for that habit (the days stay stored), like Streak's own button. Only later days count towards streaks and strength.
- **Deletions and cleared progress now travel to Streak.** Every file this app writes for Streak carries what was deleted and what was cleared, which fixes "delete does not reach Streak". A restored habit or note is sent as changed again, which Streak reads as bringing it back.
- **Streak's deletions and cleared progress now arrive here.** The newest file from **each** of Streak's devices is read (before, only the newest file overall). A habit or note Streak deleted is hidden here too (unless you changed it here after that), one Streak changed after you deleted it comes back, and a habit Streak cleared is cleared here.

### 🔧 Changed

- Database 23 and backups 23 (a "deleted at" time on habits and notes and a "cleared at" time on habits; older backups still restore). Nothing already saved is changed.

## 🛟 A safety net for places, and the diary for the whole app

### ✨ New

- **Check once every 15 minutes** (Settings > Places and trips, under Always on; on by default). When the continuous readings are not running, the app now asks the phone for one position every 15 minutes (up to 20 seconds). That position goes through the same rules as any other, so a stay is saved even if Android never sent its "arrived" or "left" message, and a move is noticed (the stay ends and the continuous readings start). A position older than 5 minutes is not used. This is for days like 21 October, when nothing was read between 9:17 and 10:22 and the app believed you never left Home.
- **Save day** and **Save all** on the Live logs page. Android's own "save as" screen opens, you pick Downloads, Drive or any folder, and the file is plain text with the date and time on every line.
- **The diary now covers the whole app**, not only places: phone usage sync, calls, steps and sleep, charging (plugged in, unplugged), money entries added from monthly items, backups made and restored, sending changes to Streak, and the 15-minute check. Background work that used to fail silently now writes why.

### 🔧 Changed

- Nothing already saved is changed. Visits and trips you have are left as they are; edit a wrong one from the Timeline.

## 🪵 Live logs: a diary of what the app does

### ✨ New

- **Settings > Live logs** (the small quiet line at the very bottom of Settings). The app now writes a diary of what it does in the background, one line per event with the time to the second, and the page shows it live: newest at the bottom, a day picker, filters (All, Warnings, Errors), a row of parts (Places, Service, Fixes, Trips, Geofence, Activity, Watchdog, App...), a search box, and Copy and Share.
- **What is in it.** Android's "arrived near / left a place" and "went still / started moving" messages, the motion sensor, every position reading (and why one was thrown away), stays and journeys starting and ending, journeys that were **dropped** with how far and how long they were, the readings service starting, stopping or being refused by Android, the 15-minute check, restarts and crashes. When the app starts it also writes why Android ended the last run, whether battery saving applies to the app, and its standby group.
- The diary stays on the phone, one text file per day. Days older than 30 are removed from the diary only. **Clear logs** (with a question first) empties it. No tracked data, habit, entry or backup is touched.

### 🔧 Changed

- Nothing about how places, trips or the Timeline are tracked changed in this build; it only adds the diary, so the next missed journey can be explained from real lines.

## 🧹 The hour ruler is gone

### 🔧 Changed

- **No more hour labels or hour lines on the Timeline.** The 12:00, 1:00, 2:00 ... column down the left and the lines across the lanes are removed, and the day is no longer drawn to scale, so a long sleep or a long stay no longer leaves hours of empty space. Rows stack one under the other; the thick coloured lines stay, each running from its own row down to the row where it ended. Every row still shows its real time.
- **Last phone-maker names removed.** The home screen is found by asking Android which app it is, instead of a list of names.

## 🔐 Permissions in one place

### ✨ New

- **Settings > Permissions.** One page with a card for every permission: usage access, location, location all the time, physical activity, calls and contacts, notifications, unrestricted battery, Health Connect and your backup, Streak and OpenNutriTracker folders. Each card says **Allowed** or **Not allowed**, **why** the app needs it, **how** to switch it on, and has the button that opens the right Android screen. The page checks again each time you come back from Android's settings.
- **Allow restricted settings guide.** Under Usage access, a fold-out explains the extra step Android asks for: on Android 17 open the app's page in Android Settings and use the three dots at the top right; on earlier versions it is at the very bottom of that page.
- The Settings row says how many permissions are still off.

### 🔧 Changed

- Phone and usage, Calls, Charging, Steps and sleep and Places and trips no longer carry their own permission cards; each keeps one small line pointing to Settings > Permissions. Nothing is switched off and no data is touched.

## 📍 Place categories with colours

### ✨ New

- **Place categories.** Home, Study, Gym, Work, Food, Shop, Friends and family and Other now each have a colour and an icon, and you can add your own (Friend's house, Library...). Settings > **Place categories** lists them and edits them with the same colour and icon pickers.
- **The place line takes its category's colour.** On the Timeline a stay's thick line and its icon use the category's colour; a category you pick for one particular stay still wins. The place editor lets you pick any of the categories.

### 🔧 Changed

- **Database 21, backups 21.** One new table. Every place keeps its category (the new table uses the old ids), nothing is removed, and older backups still restore.

## 🎨 Colours and icons the Streak way

### ✨ New

- **Streak's colour picker.** Making or changing an activity (and a habit) now shows Streak's grid of swatches, eight in a row, and a **Custom** tab with a hue ring around a square for how strong and how bright, plus the colour's code.
- **Streak's icon picker.** An **Icons | Emojis** switch, category chips (Fitness, Study, Home, Mind, Food, Work, Social, Finance, Nature, Tech, Art) and a grid of tiles. All 293 of Streak's picture names and its emoji lists are there. The pictures come from the free Material Design Icons set, because Streak's own icon font could not be downloaded here.
- **Icons for activities and apps.** Every activity and every app can wear an icon. Pick it where you set the activity, or where you set the app's category (that is now a full page too). Icons show on the Timeline rows, in the Activities list and on the Apps page.
- **Habit icons from Streak show up.** The habits you imported from Streak used to show a first letter; their named pictures are drawn now, and the habit editor uses the same two pickers.

### 🔧 Changed

- **The activity editor is a full page** with a preview, the name, the colour picker and the icon picker.
- **Database 19, backups 19.** One new text column (icon) on activities and apps, empty for everything that already exists. Nothing is removed; older backups still restore.

## 🪟 Timeline: rows open in place, holding a row edits it

### ✨ New

- **Hold a row to edit it.** Tap a row on the Timeline to open it in place (as before); press and hold to open a full-screen edit page (back arrow, title, **Save** at the top). A phone period lists every app inside it with its time and how many times it was opened. Changing the time and category, setting the app's category for every day, hiding it and "Back to as recorded" are all on that page. Changing something you logged uses the same page, with **Delete** at the bottom.
- **Activities sit inside their place.** Each stay at a place is a soft box with its name, time and length on top and the things that happened there inside. The place line is now a **thick rounded bar with the place name written sideways**.
- **Stretches with no data show up together.** Back-to-back No data, Offline or Phone off stretches are one row ("No data · 55m · 12:00–1:01"); open it to see each stretch and fill any of them in.
- **A small bolt when the phone was charging.** Rows that overlap a charge show a quiet bolt at the right; open the row to see for how long.
- **Unknown ways of travel show suggestions at once** (Walked, Cycled, Bus, Drove, Auto, Metro, Train) under the journey.
- **Icons on every kind of row by default.** Calls get a phone, charging a bolt, sleep a moon, meals a fork and knife, apps a phone, unless you picked another.
- **20 more built-in activities, each with an icon** (Work, Chores, Cooking, Reading, Meditation, Friends, Family, Shopping, Creative, Movies and shows, Gaming, Music, Outdoors, Self-care, Errands, Coding, Writing, Rest, Travel, Money), and icons for the original eight. An activity you already have with the same name is left alone.

### 🔧 Changed

- **Database 20.** Only adds activities and icons. Nothing is renamed or removed.

## 🧵 Thicker lines, no phone-maker text

### ✨ New

- **Thicker Timeline lines.** The lines for things, places and journeys are thicker and the dots for moments bigger, so they are easier to see and tap near.

### 🔧 Changed

- **Plain Android wording for battery help.** The "Keep it running" card on Places and the help rows on Places and Charging no longer name one phone maker, and the Autostart and Battery saver buttons (which only opened that maker's screens) are gone. "Allow unrestricted" and "Open this app's settings" stay.

## 🧹 Timeline tidy-up

### 🔧 Fixed

- **Numbers are no longer cut off.** A row's title now has the whole width, and the length comes first in the line under it ("24m · 12:42–1:07 pm · Screen and leisure"). A journey shows its way of travel as the title and "2m · 120 m · 6:04–6:06 pm · Home to Maths" under it. Offline, Phone off and No data put the length before the times.
- **To-dos tick off in one tap again.**
- **The note under the list** now says how rows work now (tap to open, Edit inside).

## 🧹 A clean Timeline

### 🔧 Fixed

- **The hour labels no longer drift away from the rows.** Before, a busy day pushed the cards down while the lines stayed put, so a 2:04 am row sat beside the 9:00 label. Now the rows, their lines and the hour labels all share one map. Where nothing is crowded it is exactly 1 hour = 60 dp; where many short things happen close together the hours sit a little further apart to make room, and the labels move with them.
- **One time per row.** The separate time column is gone; each row says "10:21–10:51 pm" once, under its title.

### ✨ New

- **Small rows that open in place.** Every thing, place and journey is one tidy row (title, time and category, length). Tap it to open it inside the timeline: every app with its time and how many times it was opened, the category, and an Edit button. Tap again to close. Rows with nothing more to show still open their edit pop-up straight away.
- **Soft grey Offline, Phone off and No data** — one small grey line, no outline and no button. Opening one shows what it is and the Fill in button.
- **Unknown journeys** show a small orange row; open it to answer "How did you go?".

### 🔧 Unchanged

- Same data, same edits, same Fill in. Nothing saved is touched; this is only how a day is drawn.

## 🍽️ Food in the paper look

### ✨ New

- **Month calendar** on the Food tab — pick any day from a paper month (‹ month ›), with a small square mark on days that have food logged. Today is ringed, the chosen day is inked, and a "Today" pill brings you back. The day-by-day ‹ › buttons are still there.
- **The day card** — calories eaten in big type with "left" or "over" against your goal, a bar split by meal (breakfast, lunch, dinner, snack in their own colours), and carbs, fat and protein in tally strokes. Water is its own card with the same +250 / +500 / Undo buttons.
- **Meals in paper cards** — one card per meal type with its coloured edge, its kcal total and a small + to add straight into that meal. Add meal, the Add sheet and the delete question all use the paper sheets and buttons.

### 🔧 Unchanged

- Same meals, water, goals and OpenNutriTracker import. Nothing saved is touched.

## ⚙️ Timeline and apps settings

### ✨ New

- **Apps and categories** (Settings > Timeline) — every app the phone has seen, with a "Counts as ..." pill. Tap a row to pick its category, or to hide it; hidden apps are listed below so you can bring them back. A category applies to every day, past and future.
- **Timeline settings** (Settings > Timeline) — four switches, all on to begin with: *Show gaps*, *Group repeated opens*, *Ask how I travelled* and *Remember my category changes* (a category you give one app period on the Timeline also applies to that app on every day). They only change how a day is drawn or what is asked; nothing saved is changed or removed, and switching one back on brings everything back.
- **Walking** (Settings > Places and trips) — enter your height so your step length starts right (height × 0.415), see the step length in use, and start learning again if it looks wrong.

## 🚶 Walking distance you can trust

### ✨ New

- **Steps are noted during a trip** — while a trip is being recorded, each position reading also notes the phone's step counter. A walk's steps are the counter at its end minus at its start.
- **Steps beat GPS drift** — for a walk or run, when the GPS distance is more than a third away from steps times your step length, the steps are trusted. GPS drift nearly always adds distance, which is why walks looked too long.
- **A walk cannot be a car** — the distance is checked against the speed of the way you travelled: at most 8 km/h on foot, 20 km/h running, 35 km/h cycling. 823 m in 2 minutes is no longer shown as a walk's distance.
- **Your step length is learned** — it starts at 0.70 m (or your height × 0.415, once there is a place to enter your height) and moves slowly towards what your own long walks show, where the GPS and the steps agree.
- **Changing how you travelled** — works the distance out again at once, and so does the daily tidy-up. Nothing is deleted: only the stored distance and speeds change, and the readings stay.
- **Backups** — each trip reading keeps its step counter (backup version 18).

### Not yet

- The box for your height comes with the Places settings page (T-H). Moving a trip's start and end to the last and first reading at a place also waits for that page.

## ✦ Offline, Phone off, No data

### ✨ New

- **Offline** — when your screen stayed off (for example at night, or while the phone sat on a desk), that stretch is now called Offline instead of No data.
- **Phone off** — when the phone was switched off, that stretch says Phone off, and runs until the phone started again.
- **No data** — now means only: the screen was on but the app cannot tell what you were using. It is the only one that is hatched; Offline and Phone off are quiet outlines. Each still has "+ Fill in".
- **How it works** — the app also saves when the screen turned on and off. The first sync after this update builds again the days Android still remembers (about a week); older days stay as they are and read as No data. Everything is kept in backups (backup version 17), and restoring only adds.

## ✦ The Timeline is drawn to scale

### ✨ New

- **A real time line** — every hour is 60 dp tall (one minute is one dp, with no smallest or largest size), with the hours written on the left. A stay at a place, a journey, a phone period, a call, a sleep and so on are lines that start and end exactly when they really did, so a 13-minute app period is no longer as tall as 4 hours of sleep.
- **Side by side** — things that happened at the same time sit in their own lanes next to each other.
- **Cards beside the lines** — each card sits next to its line at its start time. When two cards would touch, the later one moves down a little; the times on every card are always the real ones. A note or a logged moment with no length is a dot.
- **Gaps at their real length** — a stretch with no data is hatched at its true length.

### 🔧 Fixes

- **"No da ta"** — the length and "+ Fill in" on a gap now sit on their own line, so the words are no longer squeezed.

## ✦ Name your app combinations

### ✨ New

- **Name a combination** — open an app period on the Timeline and choose "Always count this combination as...". Pick a name, the apps and the category. Whenever those apps are used together, on any day, past or future, the whole stretch counts as that category. Physics Wallah (PW) and ChatGPT together can be Study; Claude and Brave can be Building.
- **A short interruption does not break it** — a few minutes in another app (less than a fifth of the period) between two stretches of the combination no longer splits it. The combination's apps must make up at least 80% of the period.
- **Suggestions** — Phone usage settings now has a Combinations section. It suggests a name when the same apps are used together at least 3 times over 2 or more days, or when a built-in example fits (study, building, travel, shopping, banking, reading, writing, chatting). Nothing is named until you confirm, and "Not now" hides a suggestion.
- **Backups** — your combinations are saved in backups (backup version 16). Restoring only adds them.

### 🔧 Fixes

- Nothing saved is changed: combinations are applied when a day is drawn, and removing one only removes the rule.

## ✦ Periods of use on the Timeline

### ✨ New

- **One row for a stretch of phone use** — a period ends after 5 minutes without using the phone, or when you start an app from a different category. Apps used together (for example Telegram and Brave) now show as one row with a line per app: minutes and how many times it was opened.
- **Its category** — apps that share a category give the period that category (Anki and Notes are both Study, so it counts as Study). Several apps without a category count as Screen and leisure. One app with no category just shows the app, as before.
- **Past days follow the rules** — periods are worked out each time a day is drawn from the saved app use, so nothing is ever lost or rewritten.

### 🔧 Fixes

- **Goals and Stats** — the minutes of a period count towards the period's activity, and the totals still count only time an app was really on screen.

## 🐞 Phone usage now counts the way Digital Wellbeing does

### 🔧 Fixes

- **Missing time** — moving between screens of one app (for example inside Brave or WhatsApp) no longer ends the stretch early. Android sometimes reports the old screen as "stopped" after the new one opened; that report is now ignored unless it is about the screen on top. This was the main reason your totals were about an hour below Digital Wellbeing.
- **Screen lock** — locking the phone now ends the stretch, and a phone that shut down or died ends it at its last event.
- **The Phone pill and the pie** — they added up the time from the first to the last use of an app, so they could show more than the Phone number. They now count only the time an app was really on screen.
- **Check line** — Phone usage shows this app's total next to Android's own total for the same days, to compare with Digital Wellbeing.
- **Days already saved** — the first sync after this update builds again the days Android still remembers (about a week) with the corrected rules. Older days are left exactly as they are, and every backup keeps the old numbers.

## 🐞 Fill in now stays inside the place

### 🔧 Fixes

- **Fill in** — an entry you add for a gap inside a place now stays inside that place, and the gap disappears. Before, the entry only knew whole minutes, so it could start a few seconds before the stay did and was drawn above the place with the gap still showing. An item now belongs to the stay it overlaps most.

## ✦ Continuous Timeline lines

- Removed dashed styles from event rails and card outlines, travel connectors, ongoing-stay markers, the day-progress arc, and breakdown-list separators.
- Manually logged and automatically collected events now use the same continuous line style; their colours still identify their categories.

## 📜 Export and GPX import moved

### ✨ New features

- **Backups and imports** now holds **Export** (Readings as GPX, Readings as CSV, Trips as GPX) and the **GPX files** import, like the demo. They were on the Places and trips page; they work exactly as before and nothing was removed.

## 📜 Settings like your demo (part 7: Appearance, Backups and imports)

### ✨ New features

- **Backups and imports** — the tilted note "Everything stays on this phone", Backups with folder / newest backup lines and **Back up now** / **Restore**, and Streak and OpenNutriTracker as cards with their folder, newest file, last import and **Import now**.
- **Appearance** — the theme is a strip under a "Theme" heading. Your home-screen name, icon and saved designs are unchanged, now with paper buttons (the three buttons on a saved design wrap onto a second line on narrow screens).

## 📜 Settings like your demo (part 6: Habits)

### ✨ New features

- **Habits settings** — "Track habits with" is now a two-way strip (Streak / This app). Switching to Streak still asks before importing, exactly as before. Below it: a short explaining card, the Streak details as key / value lines, the "Send automatically" switch, **Send to Streak now** and **Change folder** as rows, and the tilted note about un-ticked days. Nothing about how habits are stored or sent has changed.

## 📜 Settings like your demo (part 5: Activities)

### ✨ New features

- **Activities** — a list with the colour square and a pencil on each row, a full-width dark **New activity** button, and hidden ones under "Hidden from Log" (dimmed, with a crossed-out eye).
- **Editing an activity** now opens as a bottom sheet like the demo: name, colours, and a "Hide from Log" switch (saved when you tap Save). Old entries keep their colour; nothing you logged is removed.

## ✦ Kairos shelf-of-days launcher icon

- Replaced the placeholder clock launcher icon with a Kairos icon based on the supplied shelf-of-days SVG: parchment background, dark book spines, and the orange accent.
- Recreated the artwork with native Android vector paths so the launcher resource builds without unsupported SVG filters or `strokeDashArray` features. Fine grain texture from the SVG is omitted.

## ✦ Kairos and independent home-screen shortcuts

- Changed the installed app label to **Kairos**.
- Each saved app design can create or update its own home-screen shortcut, using its own name and icon without replacing other saved designs.
- Reworked Calls settings into full-width cards so headings, options, and controls have room to lay out cleanly.

## 📜 Custom home-screen name and icon

### ✨ New features

- **Separate settings sections** — Appearance and Backups and imports have their own entries instead of being grouped together.
- **Custom home-screen identity** — inside Settings → Appearance, choose a name and pick an image through the system image picker. Fit the whole image or fill/crop a square, position/zoom it, reset it, and create or update a matching home-screen shortcut. Save multiple name-and-icon designs locally and apply or delete them later.
- Accepts common Android-decodable image formats, including raster photos and SVG. SVGs are rendered into a square viewport so the full drawing is fitted rather than clipped; the cropped icon is stored privately on the phone.

### ⚠️ Good to know

- Android does not let an app replace its own installed app-drawer label and icon with arbitrary user-selected values at runtime. This feature creates a separate home-screen shortcut; the original Life Tracker app entry remains unchanged.
- Launcher support and label truncation vary by device. The launcher may ask for confirmation before adding a shortcut.


## 📜 Settings like your demo (part 4: Calls and Charging)

### ✨ New features

- **Calls** — the "Show calls on the Timeline" card, "Only show calls longer than" as a connected strip (shorter ones stay in Stats) and the tilted orange note about missed calls; "Older calls" stays below.
- **Charging** — one card with Record charging, Banner when I plug in and Show charging on the Timeline (each with its hint), then "How it works" and the battery help as open-and-close rows, like the demo.

## 📜 Settings like your demo (part 3: Places and trips)

### ✨ New features

- **Places and trips in the demo's order** — Watch my places (with "Needs location all the time, set once"), the permissions, Keep it running, How closely to watch (connected 1 / 2 / 5 min strip, Use GPS and Always on in one card), Record my trips (switch plus a 15 s / 30 s / 1 min strip), On the Timeline (one card with both switches), then your places as rows with "radius m circle · kind" and Add a place.
- Back button and big title like the demo.

### ⚠️ Good to know

- The demo's "Cleaner readings" options (stop length, trip start distance, motion sensor, ignore GPS jumps) are not built; this app has no such options.
- Readings, Export and import, Status and How it works stay as before, below your places.

## 📜 Settings like your demo (part 2: Phone and usage, Sleep and steps)

### ✨ New features

- **Phone and usage** — as in the demo: a card with the "Show app use on the Timeline" switch and its hint, "Leave out stretches shorter than" as a connected strip (1, 2, 5, 10 min), hidden apps as rows, and the tilted orange note about when the usage was last copied.
- **Sleep and steps** — the "Health Connect is connected" line, an "Open Health Connect" row, and one card with the step counter, "Sleep from phone use" and "Show sleep on the Timeline" switches, each with its hint.
- Both pages now start with the demo's Back button and big title with a quiet line.

### ⚠️ Good to know

- The "Workouts" switch in the demo is not built (this app does not read workouts).
- The Stats halves of these pages keep their old top for now.

## 📜 Settings like your demo (part 1: icons, shared pieces, the Settings tab)

Your demo's Settings layout, step one. The Settings tab now matches it, and the pieces the other Settings pages will use are in place.

### ✨ New features

- **The demo's line icons** — drawn from the demo's own icon paths.
- **Settings tab like the demo** — four groups: Timeline (Activities), Collected from your phone (Phone and usage, Sleep and steps, Places and trips, Calls, Charging), Trackers (Habits) and This app (Appearance and backups). Each row has an icon, a name, a small line and a chevron. Under the title: "Everything that is not data".
- **Switches are ink-black when on** — as in the demo (they were green).
- **New shared pieces** — page top with a Back button, switch cards with dashed dividers, key / value lines and the tilted orange note. The other Settings pages move to them in the next parts.

### ⚠️ Good to know

- The state chips (Allowed, Watching...) are gone from the tab, as in the demo; when something is not allowed or switched off, the small line says so ("Not allowed yet · ...").
- The Look choice stays at the bottom of the tab until "Appearance and backups" is redone (last part).

## 📜 Settings in the paper style (part 7g, Habits and Backup and import)

Settings is now finished in the paper style.

### ✨ New features

- **Habits settings** — the two choices ("This app's own data" and "Import from Streak") are paper cards with a hand-drawn round tick and an "In use" label on the chosen one; the "Send automatically" box is a paper card.
- **Backup and import** — Backups, Streak and OpenNutriTracker are paper cards; the restore pop-ups use the paper look.

### ⚠️ Good to know

- Only the look changed. Backups, restores and imports work as before, and importing twice never doubles anything.

## 📜 Settings in the paper style (part 7f, Activities and apps)

### ✨ New features

- **Activities** — each activity is a paper card with its colour dot; the New / Edit activity pop-up, the "Counts as" pop-up for an app, the pills and the Save button use the paper look.

### ⚠️ Good to know

- Only the look changed. Hiding an activity or an app still removes nothing you logged.

## 📜 Settings in the paper style (part 7e, Calls and Charging)

### ✨ New features

- **Calls and Charging** — the "Allow call log access" box and the charging info boxes ("Allow notifications", how it works) are paper cards. Their switches, minute choices and buttons use the paper look from part 7b.

### ⚠️ Good to know

- Only the look changed. The lists of calls and charging sessions are numbers shown under Stats; they change with Stats later.

## 📜 Settings in the paper style (part 7d, Places and trips)

### ✨ New features

- **Places and trips** — your places, the spots waiting to be named, the readings box and the export/import box are paper cards. The switches, choices (how closely to watch, how often to record) and buttons use the paper look from part 7b.

### ⚠️ Good to know

- Only the look changed. Nothing is deleted or recorded differently.

## 📜 Settings in the paper style (part 7c, Phone usage, Steps and sleep)

### ✨ New features

- **Phone usage** — each app is a paper card with a tally-stroke bar for its time (coloured by the activity it counts as); the "Allow usage access" box is a paper card.
- **Steps and sleep** — the Health Connect boxes and each night in the sleep list are paper cards (tap a night to fix it, as before).

### ⚠️ Good to know

- Only the look changed. Switches, choices and buttons do what they did.

## 📜 Settings in the paper style (part 7b, shared parts)

Every Settings page now uses the same paper buttons, switches, pills, pop-ups and text boxes, so they all start to look alike before each page is tidied.

### ✨ New features

- **Paper buttons** — ink-black for the main action, outlined for the rest, with hand-drawn corners.
- **Paper switches and pills** — the pen-drawn on/off switch and the pick-one pills (for example "1 min / 2 min / 5 min").
- **Paper pop-ups and text boxes** — on a sheet of paper with a pen outline.
- **New page header** — a big title with a quiet line under it, and orange-swipe section headings, on Phone usage, Steps and sleep, Places and trips, Calls, Charging, Activities, Habits and Backup and import.

### ⚠️ Good to know

- Nothing about what the buttons and switches do has changed.
- The Stats pages that share these pages, and the Food and Money titles, get the new header too. The rest of those tabs keeps its old look until its own part.
- Cards and lists inside the Settings pages are still the old look; P7c to P7g do those one page at a time.

## 📜 Settings in the paper style (part 7a, the Settings tab)

Settings comes first in the paper redesign, at your request. This step is the Settings tab itself; the pages it opens follow.

### ✨ New features

- **Paper Settings tab** — a big "Settings" title, orange-swipe headings, and one paper card per row with its name, a quiet line, a chevron and the real state as a small chip (green for Allowed, Watching, Recording; red for Not allowed yet or Switched off).
- The Look choice (System / Light / Dark) stays on this tab as a paper switch.

### ⚠️ Good to know

- The pages the rows open keep their old look until P7b to P7g. Nothing about what they do has changed.

## 📜 Habits in the paper style (part 3 of 7)

The Habits tab, the habit page and all its pop-ups now have the paper look. The layout is exactly the one you already have; only the skin changed.

### ✨ New features

- **Paper top of the page** — big "Habits" title, round paper buttons, a dark **New** button, and a small strip saying the real Streak state (Shared with Streak plus the last send, or From Streak) with a **Write now** button.
- **Paper habit cards** — wobbly letter tiles, a tally-stroke strength bar, paper day tiles and tick buttons, in Week, Month and Year views.
- **Paper pop-ups** — the habit page (numbers with dashed dividers), the long-press day menu, the editor, the note box, All notes and the relapse question use the paper dialogs, pills, labelled boxes and buttons.

### ⚠️ Good to know

- Your own Habits layout was kept on purpose; the demo's Habits layout was not copied.
- Nothing about ticking, notes, relapses or the Streak sending changed.

## 🧹 Timeline: the whole-day strip is gone

### 🛠 Changed

- **"The whole day" card removed** — the 24-hour Where / What strip, its hour axis and its readout are taken out of the Timeline, as you asked. The activity pills and the "In order" list stay.
- Section hints (like "tap a row to edit it") no longer squeeze onto two lines.

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
