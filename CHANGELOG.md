# What's new in Life Tracker

Every release page shows the sections below that were added since the last release, newest first. Older sections stay here as the history.

## 📊 Stats rebuilt

### ✨ New

- **Swipe sideways to other weeks.** The "This week" card on the Food tab now moves to the week before or after when you swipe it, and shows the dates when it is not this week. Opening it shows the stats window.
- **Week, Month, 90 days and All time.** One strip at the top of the stats window. Swipe sideways (or use the arrows) to go to the period before or after. It opens on the one you used last.
- **A calmer, more useful page.** A big average with how far it is above or below your goal, how many days you logged, protein a day, and how many days were near your goal. A chart of calories over the period with your goal as a dashed line (one point a day, a week or a month, depending on the length). Where the calories come from (carbs, protein, fat). Highest and lowest day. Weight change. How many days you did each habit you watch. The foods you ate most, with when you last had them.
- **Days with nothing logged are left out, not counted as zero**, both in the averages and in the chart, and the page says how many there were. A period with no food shows one short note instead of a page of "nothing logged".

### 🔧 Changed

- The old week window is replaced by this one. For the week you still get every day with its meals at the bottom.

## 📅 Week, Month and Timeline

### ✨ New

- **A switch under the Food title: Week, Month, Timeline.** The week strip you know is the Week view and is unchanged. The tab opens on whichever view you used last.
- **Month view.** A calendar like OpenNutriTracker's, Monday first, each day with its calorie ring. Under a day, a small **tick in the habit's colour** shows that you did a habit you picked that day (up to three, then "+n"). Swipe sideways or use the arrows to change month, tap the month name to come back to this month, tap a day to open it. Tap a habit's pill under the grid to hide or show its ticks.
- **Timeline view.** Days newest first, each listing your foods and your habit ticks together in the order they happened, with the time, so you can read what you ate before a headache or a bad stomach. A tick made without a time shows first as "no time". Tap a day to open it.
- The dot under a day in the Week strip now follows the habits you picked, whether or not the Habits card is switched on.

### 🔧 Changed

- The Diary button is gone: the Timeline view replaces it.

## 🔗 Trackers are habits now

### ✨ New

- **Your trackers are your habits.** The Habits card on the Food tab (it was "Custom trackers") now shows habits from the Habits tab, only the ones you pick. Tap one and it is a check of that habit, with the time, so the Habits tab, Streak and Food always agree. Checks made on the Habits tab show up in Food too, and in the Diary.
- **Pick which habits show.** Tap **Pick** or **Change** on the card and switch on the habits you want. **New habit** makes one on the spot (count by tap, no streak) and it also appears in the Habits tab.
- **Your old trackers moved over by themselves**, once. A tracker joined the habit with the same name, or became a new habit, and every tap became a check with its time. Nothing was deleted from the old tables.
- **Water is linked three ways.** Water you add on the Food tab counts on a habit called Water, and checks on that habit count as water here, with the same streak and goal rules as any habit. If you have no such habit, one is made (in ml, one glass a tap). A habit called Water in glasses, cups, litres or oz is used as it is. There is a switch, **Link water to Habits**, in What to show (on).

### 🔧 Changed

- A check made without a time (a past day ticked on the Habits tab) shows without a clock time. Tap it to give it one.
- The note on a tap is gone, since a habit check has none.

## 🔁 Meals you eat again

### ✨ New

- **"You have had this before"** on the check page. When a photo or typed meal reads as a dish you already logged in the last 90 days (same words in its name, so "Dal tadka with 3 rotis" matches your "Dal tadka roti" but not "Dal tadka rice"), a card shows it and offers **Use my numbers**. They are applied to today's weight, so the same dish gives the same answer each time instead of a fresh guess. Tap **Back to the reader's numbers** to undo.
- Nothing new is stored: the meal you checked and logged last time is what gets reused, so correcting it once fixes it for next time.
## 🧈 Oil, ghee and butter, out in the open

### ✨ New

- **A new "Oil, ghee or butter" card on the check page** for a meal read from a photo or from your words. The reader now says how many grams of cooking oil, ghee, butter, cream or dressing it thinks went in, and that guess is shown (with its calories, 9 a gram) instead of being hidden inside the totals. Tap 0, 5, 10, 20 or 30 g, or type your own, and the calories and fat change with it.
- The reader is told that restaurant, delivery and street food usually has more oil than home cooking, so it should not assume a little. In a study of photo calorie apps, missing fat was the biggest single gap.
- It scales with the amount you slide to, and it is left out when you log each food on its own line (those lines keep the reader's numbers).

## ✒️ A paper-style animation while the AI reads

### ✨ New

- **While a photo, a label or your words are being read**, your photo (or an empty paper card, for typed words and barcodes) gets a pen that keeps drawing and rubbing out its outline, a yellow marker that sweeps across, and pencil hatching that flickers in the corners. No words, no spinner.
- **When the answer comes back** an ink stamp lands in the corner and the card dips like pressed paper; the calories and macros count up from 0, and each food on the plate gets a quick marker stroke under its name. **If it fails**, a red pencil scribbles across and the card shakes once; the error text is still shown.
- It plays on the check page and on "Fill from a photo" in the new-food form.
- **Switch it off** in Food > What to show > Photo reading. It is on to start, and Android's own "remove animations" setting also turns it off.

## 📅 Sunday week report

### ✨ New

- **Sunday week report**, a notification with a short summary of the week: days logged, average calories, days the protein goal was met and the weight change. It rings on Sundays at 19:00 (you can change the time) and says nothing if you logged nothing that week. **Off until you switch it on**, under Food > What to show > Reminders. It uses the same alarms as the meal and water reminders, so it comes back after a restart or a clock change.
## 🥘 Say what is in a recipe

### ✨ New

- **Or say what is in it** under the ingredients of a Meal prep batch (and of meal plans and saved meals, which share the same list). Type or speak something like "200 g rice, 150 g chicken" and press **Add them**.
- Each ingredient with a weight (g, kg, ml or l) is **looked up in your foods first**, so it costs no AI call and uses real numbers. Only what is not found, or has no weight ("2 roti"), is sent to the AI, and those rows are flagged as estimates so you can check the grams and numbers. If the AI is not set up or offline, the ones found in your foods are still added and the rest are named.

## 🍲 Recipes as foods

### ✨ New

- **Save as a food** in a Meal prep batch. A batch already has the ingredients, what the pot weighs once cooked and the number of portions; this button makes it a food of your own (numbers per 100 g of the cooked pot, one serving being one portion), including its vitamin numbers. It then turns up in search, My foods and quick picks like any other food.

## 🥦 Vitamin watch

### ✨ New

- **Vitamin watch** card for the Food tab. It is **off until you switch it on** in **What to show**. Pick the vitamins and minerals you care about (**Choose**), and it shows their average over the last 7 days (not counting today) against your daily targets.
- A nutrient is called **Low** when its average is under 70% of the target, and only once at least 4 of those days have foods with vitamin numbers. A low one lists the three foods in the library with the most of it (spices, oils, sauces and pantry items are left out).
- Days where no food had vitamin numbers are left out of the average, and the card says how many of the library's foods have vitamin numbers, so a low result can be missing data rather than a real gap.

## 🔔 Meal and water reminders

### ✨ New

- **Reminders** section at the bottom of **What to show** on the Food tab. Both are **off until you switch them on**.
- **Meal reminders**: a time for breakfast, lunch, snack and dinner (8:30, 13:00, 17:00 and 20:00 to start with), each with its own switch. The reminder is **skipped if anything is already logged for that meal today**.
- **Water reminders**: every hour, 2 hours or 3 hours between a start and an end time (8:00 to 22:00 to start with). A reminder is **skipped when you are on track**, meaning you have drunk at least the share of your goal that the waking window has used up. The banner has a **Drank a glass** button that adds one glass.
- They have their own notification channel, **Meal and water reminders**, so you can mute them in Android settings.

## 🔥 Calories burned from your phone

### ✨ New

- **Count what my phone measured** switch in the Calories burned box (tap Burned on the Food tab). It is **off until you turn it on**. When on, the day uses Health Connect's **active calories** (the calories above resting, which your daily goal already includes). If Health Connect has none for a day, it estimates from your steps and your latest weigh-in (about 0.0004 kcal per step per kg, so 10,000 steps at 70 kg is 280 kcal). A number you type always wins.
- Health Connect calories are a **separate permission**, asked only when you turn the switch on and press **Allow Health Connect to share calories**. Steps and sleep keep working as before.
- The week strip rings use the same burned numbers.

## ↕️ Move the parts of the Food tab

### ✨ New

- **What to show** now has **Up** and **Down** buttons beside each switch, so you can put Water, Supplements, This week or anything else where you want it. The parts are in two piles, the ones above the "Details" heading and the ones under it, and each moves inside its own pile. Water and Protein shake share a row, which sits where the higher of the two is. The order is saved with your other settings and in the backup.

## 📅 The week window and weekly report

### ✨ New

- **Tap "This week"** on the Food tab to open the week: average calories and protein, days on target, days the protein goal was reached, the day closest to and furthest from your calorie goal, the five foods you ate most, and how your weight moved (when you weighed in at least twice).
- Under that, **every day of the week** with its calories, protein and what you ate at each meal. Tap a day to open it. **Earlier week** and **Later week** move through the weeks.

## 🧺 Add food: add several foods at once

### ✨ New

- **Add several** switch under the Photo, Barcode and Label row. While it is on, tapping a quick pick puts it on a **To log** list at the amount you ate last time, and opening any other food asks only for the amount (no meal or time) and has **Put on the list**. Tap a row on the list to change its amount, or **Remove** it.
- **Add N foods to lunch** puts everything on the list into the day in one go, with the same time, and the bar at the top has **Undo** for the whole batch.
- Pressing Close with foods still on the list asks **Leave without adding?**, so nothing is lost by accident.

## 🔎 Add food: search that knows other names

### ✨ New

- **Other names find the same food**: "sooji" finds rava, "chapati" and "phulka" find roti, "lauki" finds bottle gourd, "murgh" finds chicken, and so on for about seventy common foods (the list is in `assets/food/aliases.tsv`). A food that really has the word you typed still comes first.
- **Your foods come first**: among equally good matches, foods you starred or ate recently are listed before the others.

## ⚡ Add food: quick picks and one-tap adding

### ✨ New

- **Quick picks for lunch** (or whichever meal you are adding to) replaces "Eaten before" and "Favourites" on the front of Add food. Starred foods come first, then the foods you eat most at that meal, then the ones you eat most overall. A starred food you have never logged is still offered, at one serving.
- **One tap adds it** with the amount you ate last time, shown under each food. A bar at the top says what was added, with **Undo** and **Change amount** (which takes the meal back and opens the amount sheet). The bar goes away by itself after a few seconds.
- Foods you open from a search or a shelf now **start at the amount you ate last time** instead of 100 g or one serving.

## 🧭 Add food: the same screen every time

### ✨ New

- **One layout, always**: under the search box there is now always a row of **Photo**, **Barcode** and **Label**, then the shelves (All, Ready dishes, My foods, Favourites, categories), then the foods. Before, the ways to add and the shelves swapped places depending on whether you had searched or pressed Browse, and a "Back to the short list" button hid the shelves.
- **When the food is not there**: type what you ate and two buttons appear under the row, **Describe it with AI** (the words go to the check page) and **Just calories** (opens Quick add with the name filled in). The search box has a **Speak** link when it is empty.
- **Browse all foods** now only makes the list longer, and turns into **Show fewer foods**.

### 🔧 Changed

- The long "Say what you ate" form on the front of Add food is gone; the search box does that job.

## 🏷️ Name the dish before it is sent

### ✨ New

- After you take a photo of a meal, Kairos now asks **Do you want to name this dish?** before anything goes to the reader. Type a name and press **Send with this name**, or press **Skip, just read the photo**. The name is sent as a hint to get the food and the amount right, and it becomes the name on the check page (you can still change it there). You can also take the photo again from that step.

## 📶 Photos without internet, a second model, and learning from your fixes

### ✨ New

- **Read it when I am online**: if a meal or label photo cannot be read because there is no internet, the check page offers to keep the photo. When the phone is online again a notification says so; tapping it opens the check page for that photo. Nothing is added without you looking at it, and photos not read within a week are dropped.
- **Second model as a back-up**: when the free allowance of the model you chose is used up (the quick one allows many more a day than the "think harder" one), Kairos tries your other model at once instead of failing.
- **It learns from your fixes**: when you rename what the reader called a meal ("dal" to "rajma"), the last 20 such fixes are sent along in the next prompts as a hint to take care with similar foods. They are kept in their own settings file and are backed up; they contain only food names.

## 📦 Scan a barcode, read a label, say what you ate

### ✨ New

- **Scan barcode** in Add food: Google's scanner screen reads the pack (no camera permission needed), the numbers come from **Open Food Facts**, the free open food database, and you land on the check page. Many Indian products are missing there; then it offers to read the label instead, or you can type the number.
- **Read a label**: photograph the nutrition table on a pack and Gemini reads the printed numbers (per 100 g, converted from per serving if that is all it prints) and the pack's serving size. Only the boxes in your form are read.
- **Say what you ate**: type or speak "2 rotis, 1 katori dal, curd" and Gemini works out the foods and amounts, with the same tick boxes and numbers on the check page.
- The check page has **Also keep it as one of my foods** (on for barcodes and labels): the next time the food is one tap in Add food.

### ⚠️ Good to know

- Barcode scanning needs Google Play services. Typing the digits under the barcode works without it.
- A barcode is looked up on Open Food Facts (it sees only the number); typed or spoken words and photos go to Google's Gemini, as the notice says.

## 🍽️ A calmer Add food, and a smarter check page

### ✨ New

- **Add food opens short**: a **Take a photo of the meal** button, then the foods you ate before and your favourites. The full list, the group pills and your own foods are one tap away under **Browse all foods**, and typing in the search box still searches everything.
- **Take a photo from Add food** goes to the same check page as the widget, and adds to the meal and day you were adding to.
- **The check page lists what is on the plate.** When the photo has several foods (roti, dal, sabzi), each shows with its own weight and calories and a tick box. Untick what you did not eat and the numbers follow. A switch logs each food on its own line instead of one meal.
- **The photo stays with the meal.** A small copy of the photo shows in the food log and in the meal's details. It lives on this phone only (not in backups) and goes when you delete the meal.
- **Katori and bowl** are now portion sizes you can pick for your own foods.

## 🧮 Choose the boxes of a food

### ✨ New

- **Change these boxes** on the New food form: take out a box you never use and add one you do (a vitamin, a mineral or one of your own nutrients, for example Vitamin B9). Calories always stay. A box you take out is only hidden, so foods keep the numbers they already have.
- **The photo reader fills exactly your boxes.** Fill from a photo and the widget ask Gemini only for the numbers in your form, including the vitamins you added (these are rough guesses, and the photo check page keeps them with the meal).
- **Test the key** button in the Gemini key window: it tries both models and says "works", or tells you if the key is wrong, the model name is wrong, or the free allowance is used up.

### 🎨 Changed

- The New food form is calmer: name, drink switch and your number boxes first. Group, portion, cooked weight and the full vitamin list are under **More**.

## 📷 Snap a meal from your home screen

### ✨ New

- **Snap a meal** home-screen widget: long-press the home screen, choose Widgets, find Kairos and drag **Snap a meal** out. One tap opens the camera, Gemini reads the photo, and you land on a **Check this meal** page.
- The **check page** asks "Is this what you ate?" before anything is added. You can rename it, slide the portion to match your plate, fix the calories, protein, carbs or fat, pick breakfast, lunch, dinner or snack (chosen for the time of day), and press **Yes, add this**. If it looks wrong, ask again with a hint such as "2 rotis, 1 katori dal", ask the bigger model, or take another photo.
- The same button is also on the **app icon** (long-press it) and as a **quick-settings tile** (pull the shade down, then edit tiles).
- The key and privacy dialogs of Fill from a photo are now shared with the widget, so the first tap asks for your free Gemini key if it is missing.

## 📷 Fill from a photo: fix

### 🐛 Fixed

- **Fill from a photo** always said "Could not open the photo", from the camera and from the gallery. Photos now open and are sent to Gemini.
- The two buttons are now **Camera** and **Gallery**, so their names are no longer cut off.

## 📷 Fill a food from a photo

### ✨ New

- **Fill from a photo** at the top of **New food**: take or choose a photo of a meal, and Google's Gemini reads it and fills in the name, calories, protein, carbs, fat, fibre, sugar, sodium and fat types, plus how much was on the plate. You check the numbers and press Save. Add a note such as "2 rotis, 1 katori dal" to improve the guess.
- **Think harder** asks a bigger model about the same photo, for a dish the quick one got wrong.
- Needs your own **free Gemini key** (aistudio.google.com/apikey), asked for once under **AI settings**. The key stays on this phone and is left out of backups. The model names can be changed there when Google retires one.
- A one-time notice says that the photo goes to Google, and that on the free plan Google may use it to improve its products.
- Food photos only for now; labels and barcodes are not read.

The method follows the open-source calorie apps Fud AI (MIT) and CalorieMate (MIT): a small JPEG sent with a prompt that asks for JSON only, low thinking for speed, and a form you review before saving.

## 📤 Export for GymMane

### ✨ New

- **Export > "For GymMane"**: saves a file GymMane can restore, with your food, water, supplements, fasts and weights from Kairos.
  - **Save with my GymMane backup**: a copy of your GymMane backup (the newest one in the GymMane folder, or one you choose) with what only exists in Kairos added. Nothing in GymMane is lost or changed.
  - **Save Kairos data only**: a file with just Kairos' data. GymMane replaces everything when it restores a file, so use it only on a GymMane with nothing in it.

## 🧪 Food rebuild, part 10: the Food screen looks like GymMane's

### ✨ New

- **Week strip**: each day is a ring that fills with that day's calories (green near the goal, red well over). Swipe sideways to change week.
- **Big ring** now has **Eaten** and **Burned** on its sides, with small pictures. Tap Burned to type what you burned that day; it is added to the day's calories.
- **Water and Protein tiles side by side**, each with GymMane's bottle or shaker picture that fills as you go. Tap the glass to add a glass, hold it to type an amount; tap the shaker to log your shake, hold it to pick another. The "…" button has the settings.
- **Supplements** card with suggestion chips (Creatine, Vitamin D, Omega-3 and so on), a count, and "Add supplement" at the bottom.
- **Meal prep** and **Meal plans** are headings with a "+ New" button, then a card for each batch or plan.
- **Details**: Animal protein, Fibre, Sugar and Sodium as four ring cards (goals 30 g, 50 g, 2.3 g), then vitamins, protein quality, fat types.
- **This week** and **Body weight** are cards with a line chart and a goal line. Tap Body weight to weigh in or set a goal.

Based on GymMane by InlitX (GPL-3.0); the bottle pictures come from GymMane.

## 🧪 Food rebuild, part 9: the Indian Food Composition Tables (IFCT 2017)

### ✨ New

- **527 foods from the IFCT 2017 tables** (the National Institute of Nutrition's measured values, per 100 g, for raw Indian foods): cereals and millets, dals and pulses, leafy and other vegetables, fruits, roots, spices, nuts and seeds, sugars, mushrooms, milk and paneer, eggs, poultry, meat, fish and shellfish, oils. They carry **real numbers**, not estimates: calories (from the tables' kJ), protein, fat, carbs (available carbs plus fibre), fibre, sugars, sodium, saturated / mono / poly fats, and **vitamins and minerals** (A, C, D, E, K, B1, B2, B3, B5, B6, B7 (biotin), B9, calcium, iron, magnesium, phosphorus, potassium, zinc, copper, manganese, selenium). Hindi names are in brackets, so "gajar", "palak", "chana" and "sev" find them.
- **Biotin (B7)** now has values; the IFCT tables are the first source that does. (B12 and iodine are not in the tables.)
- **69 of them have a photo**, borrowed from the same plain food in the main list (apple, carrot, tomato, banana, spinach and so on). The rest have the category icon.
- **Fibre and vitamins for the hand-estimated dishes** (about 130, gulab jamun to dal tadka): worked out from what goes into each dish using the IFCT values, scaled to the dish's calories. Cooking loses vitamin C and folate, so those two are left empty for dishes. Treat the numbers as rough.
- The 16 raw staples I had estimated by hand (toor dal, atta, bajra, jaggery, paneer and so on) are removed, because the real ones replace them.

### ⚠️ Good to know

- IFCT values are for the food **raw or as sold**; log cooked dal or rice with the cooked dishes (dal tadka, khichdi) instead.
- **Vitamin D is left out for plant foods.** The tables list ergocalciferol (D2) at 2 to 100 µg per 100 g for cereals and vegetables, which would make a plate of roti look like a vitamin D source; USDA shows none. Mushrooms (real D2), milk, eggs, meat and fish keep theirs.
- Where a food has several varieties (mangoes, grapes, bananas), each variety is its own entry.
- Source: Longvah T, Ananthan R, Bhaskarachary K, Venkaiah K. *Indian Food Composition Tables 2017*, National Institute of Nutrition, Hyderabad, via github.com/ifct2017 (AGPL-3.0). If the app is ever shared, the combined work must stay under a licence compatible with that (GPL-3.0 already applies because of GymMane).

## ⏰ Supplement reminders

### ✨ New

- **Remind me** (Food > Supplements): a notification at each supplement's time, on the days it is due, with **Taken** and **Snooze 10 min** buttons. Taken is the same as ticking it in the app. It stays quiet if you already ticked it today, and supplements set to "Any time" do not ring. Off until you switch it on. Alarms are set again after a restart, an app update or a change of clock or time zone. Notifications and "Alarms and reminders" must be allowed in Settings > Permissions (the same ones the habit reminders use).

## 🍛 Food rebuild, part 8: Indian foods, sweets and snacks

### ✨ New

- **151 Indian foods**, mostly **by the piece**: 1 gulab jamun, 1 samosa, 1 roti, 1 idli, 1 laddu, 1 momo. Sweets (30), snacks (about 40), everyday dishes (about 55, curries and rice by the cup), drinks (lassi, chai, thandai, sugarcane juice) and raw staples (dals, atta, besan, rice, millets, ghee, paneer, jaggery). Three new groups on the food list: **Sweets**, **Snacks** and **Indian dishes**.
- Search also finds common spellings, such as "jamoon".

### ⚠️ Good to know

- These numbers are **typical values per 100 g from my own knowledge of standard recipes and the Indian food tables, not copied from a table** (the tables could not be reached from here), so every one is marked **approximate** like the other estimated foods. Homemade and shop-bought versions differ a lot (ghee, sugar, size of a piece), so use "Save as my food" or edit the numbers when you know them. Fibre, sugar, sodium and vitamins are left empty rather than guessed. They have no photos yet.

## 🥦 Food rebuild, part 7: vitamins and minerals for the food list

### ✨ New

- **Vitamin and mineral amounts for about 450 of the 561 foods**, taken from the USDA FoodData Central "SR Legacy" table (per 100 g): A, C, D, E, K, B1, B2, B3, B5, B6, B9 (folate), B12, choline, calcium, iron, magnesium, phosphorus, potassium, zinc, copper, manganese and selenium. Each food is matched to the same food in the same state (raw with raw, cooked with cooked); where only a close variety exists it uses that one. Foods with no real counterpart (dishes, mixes, rare items such as dragon fruit) are left empty rather than guessed, and you can still type values in by hand.
- Not covered: **iodine** and **biotin (B7)**, because the USDA table has no values for them.

## 🔄 Food rebuild, part 6: GymMane sync (N7)

### ✨ New

- **GymMane folder** (Settings → Data sources): choose the folder where GymMane saves its automatic backups. When you open the app, the newest backup is read and what is new is added once: **food log** (per-serving numbers turned into per 100 g, library foods keep their photo and vitamins), **water** (a day keeps the larger amount), **supplements** and what you ticked, **fasts** and **weigh-ins**. Nothing in GymMane's files is changed, and importing twice never doubles anything. If you delete something here, it does not come back.
- **Send what is new here back** (off until you switch it on): writes `gymmane-kairos-….zip` into the same folder, a copy of GymMane's newest backup with your food, water, supplements, fasts and weights from Kairos added. GymMane's own files are never touched, nothing already in the copy is changed, and only the newest 3 copies are kept. Restoring stays manual inside GymMane, so nothing there can be lost by surprise. Things that came from GymMane are never sent back to it.
- "Import now" and "Send now" buttons on the same card.

### ⚠️ Good to know

- Changes you make in GymMane to something already imported (an edited serving, say) are not copied again, and deletions do not travel either way.

## 🩺 Food rebuild, part 5: custom trackers and the Diary timeline (N6)

From OpenNutriTracker issue #1304.

### ✨ New

- **Custom trackers** (off until you switch on "Custom trackers" under the list icon on the Food tab): make a tracker with a name, colour and icon (the same picker as everywhere else), such as headache, reflux, tea or a medicine dose. **One tap logs it with the time**; on another day it asks for the time first. Tap a logged one to change its time, add a note or delete it.
- Each tracker shows its **count today**, the **time of each tap**, and a **weekly bar chart**. Hidden trackers keep their history. Trackers never touch calories.
- **Diary timeline** (the timeline icon at the top of the Food tab): days newest first, each with its calories, every food with its time, and the taps as chips such as "Headache ×2". A filled mark means the day has taps. Tap a day to open it. "Show 30 days earlier" goes further back.
- The week strip shows a second dot on days with taps.
- Backups carry trackers and every tap (backup format 37, database 36).

## 🥗 Food rebuild, part 4: shake, supplements, weight, fasting, meal prep, plans (N5)

### ✨ New

- **Protein shake**: pick your shake once, then one tap logs it.
- **Supplements**: add what you take (dose, time, every day, some weekdays or every few days, from and until) and tick it off each day. Reminders come in a later step.
- **Body weight**: weigh-ins, a goal weight ("2.4 kg to lose"), the pace per week and a small line chart.
- **This week**: calories and protein a day, and how many days were on target (calories within 10% of the goal, at least 90% of the protein).
- **Fasting**: a timer with a ring (12 to 23 hours), your last 7 days and how many fasts in a row reached their goal.
- **Meal prep**: log a whole pot once with its cooked weight, then eat "25% / 50% / 1 portion of what is left"; it keeps track of what is left.
- **Meal plans**: plan a day of meals (or copy the day you are looking at) and log only the empty meals with one tap.
- **Protein quality** and **Types of fat** (off until you switch them on): animal against plant protein, the nine essential amino acids against what an adult needs, saturated, mono- and polyunsaturated fat.
- **What to show** (the list icon at the top of the Food tab) switches each part on or off. Water now has its own goal and glass size (tap its name).
- Backups carry all of it (backup format 36, database 35).

## 🍎 Food rebuild, part 3: the new Food screen (N3)

### ✨ New

- **The Food tab is new**, in the paper look and laid out like GymMane: a week strip, a calorie ring ("left of your goal") with carb, protein and fat bars, a search bar, a shelf of foods you ate before, **Favourites** and **Saved meals** folders, and Breakfast / Lunch / Dinner / Snack tabs with a photo plate for each meal.
- **Add food** is a full window: search, 15 food groups, **Ready dishes**, My foods and Favourites, with photos. Tap a food to set the amount (grams, portions, raw or cooked), see calories, macros, vitamins and what is left of today's goal, then add it.
- **Quick add can also save the food.** Type a name, calories and macros; the "Also save it as one of my foods" switch (on by default) keeps it under My foods as one serving.
- **Your own foods** are made and edited in a full window: group, drink or not, numbers per 100 g, fat types, one portion, cooked weight and **vitamins and minerals**. Built-in foods take typed vitamin numbers too.
- **Meals**: tap an entry to change the amount, meal, time, copy it to another day, keep it as your own food or delete it. A meal's menu saves it as a **saved meal**, copies it to another day or clears it.
- **Goals** (calories, protein, carbs, fat) are set from the day you are on.
- The old "Add a meal" sheet is gone. Open Nutri Tracker import still works.
- Details below the meals show fibre, sugar, sodium and your vitamins and minerals for the day. Water is a tile with +250 / +500.

## 🥦 Food rebuild, part 2: vitamins and minerals (N2)

### ✨ New

- **Vitamins and minerals you control.** Settings → Trackers → **Vitamins and minerals**: 24 starters (vitamins A, C, D, E, K, B1, B2, B3, B5, B6, B7, **B9**, B12, choline; calcium, iron, magnesium, phosphorus, potassium, zinc, copper, manganese, selenium, iodine), each with the daily amount printed on food labels.
- **Add your own** (Omega 3, creatine, anything) with a name, a unit (mg, µg, g, IU or your own), a group and an optional target. **Hide** any of them, or **delete** one you added. A hidden or deleted nutrient never rewrites meals you already logged.
- Every meal remembers the vitamins it had per 100 g; the numbers you type for a food are kept and win over the ones that come with the app (database 34, backup format 35).
- The Food screen does not show them yet; that comes with the new Food screen.

## 🍽️ Food rebuild, part 1: the food list (N1)

### ✨ New

- **561 foods with photos and 27 ready dishes** come with the app, with calories, protein, carbs, fat, fibre, sugar, sodium, the kinds of fat and the essential amino acids per 100 g, and a portion size for each (one banana is 120 g). The list is based on GymMane by InlitX (a line in Settings says so).
- **Your own foods, stars and saved meals** now have places to live (database 33), and every meal you log remembers which food it came from and its extra numbers.
- Backups carry all of it (backup format 34). Older backups still load.
- Nothing on the Food tab looks different yet; the new Food screen comes next.

## 💰 Money: edit everything, Deduct it from, paper look (R6b, R6c)

### ✨ New

- **Tap an entry to change it**: name, amount, date, category, **Deduct it from** and note, in the full-screen edit window. **Delete** is at the bottom and asks once.
- **Deduct it from** (expenses): *My allowance*, *Someone else paid* (with who) or *Not sure*. Each says what it does to "Left". It is in the add and edit windows of single expenses and of saved items, so a monthly rent paid by someone else never takes from what is left.
- **Saved items edit the same way** (monthly or again and again), with start and end date, hide or stop, and delete. Deleting one keeps the entries it already made.
- **Categories edit with a colour and an icon**, can be hidden, and can be deleted when nothing uses them.
- **Sort the entries** by newest, oldest or biggest.

### 🛠️ Changed

- **The Money tab has the paper look**: a month card with In, Out and Left (and *Paid by others* and *To decide* lines when there is something), Earlier and Later buttons, quick-log chips with a category dot, entries as paper cards with the category icon, an **Add** button like the Timeline's, and the add questions in a paper sheet. The two questions (Expense or Income, then Every month, Again and again or Just once) are the same.

## 🥧 Apps in the pie (H2)

### ✨ New

- **The Pie can show the apps too**, not just the category. Each app you used becomes its own slice, in a shade of its category's colour: the app you used most keeps the category's colour and the others get lighter. The apps of a category sit side by side.
- **A switch for it**: "Show apps in the pie" in Settings, Timeline, and a quick button above the pie on the Timeline. It starts off. The Timeline bar and the totals are unchanged.
- The phone does not say when inside a period each app was open, so an app's share of a period is its time on screen; the totals per app are exact.

## 🧱 Choices in one place, and joining two stays (H1)

### ✨ New

- **Join two stays by dragging.** On the Timeline, hold a stay and drag it onto the stay next to it. You are asked once, and if the two have different names you pick which name the joined stay keeps. The walk between them becomes part of the stay. A stay that is still going always keeps its name; an unnamed spot takes the name of the named one.
- **Another way to join**: open a stay (tap it) and use "Join with the stay before" or "Join with the stay after".
- **Nothing is lost**: a bar with Undo shows for a few seconds, and the Changes page lists every join with "Separate the stays again". Your own time and category changes on the stay come back too.

### 🛠️ Changed

- **No more rows that scroll sideways.** Every row of choices (what did you do, habit days and categories, range pickers, money, places, replay, logs, the Timeline totals and more) now wraps and shows everything in one block. When there is a lot, the rest waits behind "More", and "Show less" folds it up again.

## 🗺️ Maps: a size limit, no maps nobody uses, a better picker (F3)

### ✨ New

- **A size limit for offline maps** (250 MB, 500 MB, 1 GB, 2 GB, 5 GB or no limit; 1 GB to start). The Maps page shows how much the maps take, of the limit, with a bar and the free space on the phone.
- **The least used maps are deleted by itself** to stay under the limit (a switch, on to start). Maps of another style than yours and of places you no longer have go first, then the ones you have not been in for longest. Areas you download by hand are never deleted by itself. With the switch off nothing is deleted, and a download that does not fit waits and says why. "No limit" keeps everything.
- **No map for a place you never go to**: a place with no visit for 90 days is not downloaded (a place you just made always is). While the limit is full and deleting is off, no new map is asked for.
- **A better downloader**: Pause and Resume for all downloads, "Try the failed ones again", failed downloads that were only bad luck (a stall) are tried again by themselves every few hours, and the queue goes areas you chose first, then places, then where you went.
- **The Download an area window** can jump to where you are and to your places, has size buttons (a neighbourhood, a town, a city, a region), and tells you when the area would pass your limit.

## 💾 One backup for everything, export and import in order (G4)

### ✨ New

- **One backup file holds everything**: all your data (Timeline, habits, money, food, to-dos, notes, places and deleted places, trips, every GPS reading, phone usage, calls, charging, steps, sleep, focus, rules, goals) **and every setting of the app** (looked up on the phone, so a setting added later is kept too). There are no separate backups. The offline map files are not in it; the map areas are remembered and show as "Tiles deleted" to download again.
- **Save to a file / Restore from a file**: the same one file can be saved anywhere (Drive, USB, another folder) and restored from there, for a new phone.
- **Restore shows what is inside first** (how many habits, readings, settings...), says what will be replaced and what is only added to, and saves a safety copy first (in the backup folder, or inside the app when there is none). After a restore that put settings back, the app restarts to use them.
- **Backup, export and import is three tabs now**: Backup | Export | Import, instead of one long page.
- **Export GPS readings in three steps**: what (everything, a day, a range, with Today / 7 / 30 / 365 days buttons and the number of readings it will save), what kind of file (each one says what it is for, now with **Google Timeline**: the same layout as the Timeline.json from the phone) and where. The automatic export can use any of them too.
- **Import your Google Timeline** has its own card with the steps to get the phone's Timeline.json. Only that file is taken. The place of each visit and the start and end of each activity count as readings too.
## 🧩 Rules and goals on one page (G3)

### ✨ New

- **Rules and Place goals are now one page**, "Rules and goals", with a Rules | Goals switch under the title. It is one row in Settings now; the goal rings on the Timeline and Habits open it on Goals.
- **New rule starts from a choice in plain words**: "Tell me when I get somewhere", "Remind me when I leave somewhere", "Tick a habit when I get somewhere", "Remind me at a time every day", "Do something when I plug in", "A button I tap myself", or start from nothing. Everything can still be changed.
- **The rule window is three short steps** (1 When, 2 Only if, 3 Then) with Back and Next, instead of one long form. The triggers are sorted into six groups (A place, Time, The phone, Moving, My body, A habit) and each one has a line saying what it is for.
- A mistake is shown on the step where it was made. The name, the "wait at least this many seconds" box and the notes about {place} and friends are tucked behind "More options".
- "One of them is enough" only shows once two limits are on, and the long explanation at the top of Rules is shorter (the details moved to "Good to know" at the bottom).

## 🧭 Settings polish (G1, G2)

### ✨ New

- **Permissions**: the ones that are **not allowed come first**, with the allowed ones below under their own heading. Each card now says **what it is used for** (Places, Timeline, Rules...) and **where it is in Android**.
- **"Open its page"** goes to the exact Android page of that permission: the phone's own permission editor on Xiaomi, Redmi and POCO phones, the named permission page elsewhere, the usage access, notification, alarm and battery pages directly, and the app's general settings only as the last choice. When Android has refused a permission before and will not ask again, the card says so.
- **Settings remembers where you were.** Going deep into Settings (for example Places and trips > Places) and pressing Back lands on the same part of the same page, scrolled to the same place, instead of a fresh Settings page. The same goes for every other screen you open on top of a tab.
- **"What are you charging with?" banner is off by default.** Switch it on under Charging.

## 🧭 Places list and drawn areas (F2)

### ✨ New

- **Add a place** is now at the top of the Places page.
- **Search** places by name or kind, and the list is **closest first** (from where the phone last was; by name when there is no position yet). It shows "350 m away" on each place, and goes **10 places a page** with Previous and Next.
- **Delete a place**: a Delete button on each place (and in its Edit window). A bar with Undo shows for a few seconds, and "Deleted places" lists them to bring back or remove for good. A deleted place stops being watched, but your old visits there keep its name on the Timeline.
- **Draw the area of a place on the map**, like cropping a picture: "Draw the area on the map" in the place window starts from your circle as a ring of corners. Drag a corner to move it, tap the map to add one, press and hold a corner to remove it. "Move the map" lets you pan and zoom without changing the shape; "Back to a circle" undoes it.
- **Stays understand the drawn area**: you are at the place while you are inside the shape, and walking about inside it is still one stay (see F1). The place page shows the shape on its map.
- Database version 32 and backup format 32 (a place can have an area and a deleted time). Your data is untouched.

## 🧭 Stays and trips fixes (F1)

### ✨ New

- **Walking about inside a named place is still one stay.** Moving up and down in your house no longer starts a trip. A trip begins only when you leave the place's circle. The stay shows a small line such as "Walked about 15 min".
- **Guess how I travelled** (Settings > Timeline) is **off** now. With it off, a trip shows only the way you set yourself, with a small green dot; every other trip is just "Trip" with its distance and time. Turn it on to see the app's guesses again.
- **Delete a trip**: open a trip on the Timeline, Edit, Delete this trip. A bar with Undo shows for a few seconds, and "Deleted trips" lists them to bring back or remove for good. Deleted trips leave the Timeline, the totals and the goals.
- **Collapse every place and trip**: one button on the Timeline makes each place and trip a small row, without the apps and entries inside.
- Trips from older versions that began and ended at the same place and never left it are shown as a walk inside the stay.
- Database version 31 and backup format 31 (a trip remembers when it was deleted). Your data is untouched.

## 🧭 Timeline header: rewind button, goal rings off by default

### ✨ Changed

- **Replay this day is now a small rewind button** at the top right of the Timeline, beside the "left today" arc, instead of a wide button on the page. It works for any day.
- **The goal rings are off by default.** Switch them on under Settings > Place goals ("Show goal rings on the Timeline and in Habits"). The goals themselves are unchanged.

## 🧭 Places and trips round 2 is finished (E10)

### ✨ In short

Round 2 of Places and trips is done. Since the remake: stays that no longer split in two, better place names with suggestions, a live map that follows you, a replay of your day (with speeds, a zoom that follows your pace, and a GIF with the real map), journeys with a map and a much better guess of how you travelled, **Rules** (when something happens, do something), and **Place goals** with rings. Every part has its own switch under Settings > Features.

### 🔧 Checked

- The train day of 27 and 28 October runs through the rules and the goals in the tests: one stop at Prayagraj Junction (one arrival, one leave), two journeys of 343 km and 60 km, 405 km and 8.5 hours out of the house in all.
- Please look at that day on your phone and tell me what is still wrong.

## 🧭 Places and trips round 2: place goals (E9)

### ✨ New

- **Place goals**: Settings > Place goals. Make a goal from where you go: hours at a place or a kind of place, visits, hours out of the house, distance, or hours travelling (any way, or one way such as car).
- **A day, a week, a month or the last few days**, as a **target to reach** ("10 hours out of the house a week") or a **limit to stay under** ("at most 2 hours travelling a day"). A week runs Monday to Sunday.
- **Rings** for your goals on the Timeline (today) and at the top of Habits. Tap them to open the goals. A ring fills as you get closer, shows "Done" or "Kept", and turns red when a limit is passed.
- One stay or trip counts towards every goal it fits. Hidden and very short stays are left out, a stay across midnight is split between the days, and a stay that is still going counts up to now.
- A **Place goals** switch under Settings > Features. Goals are saved in your backup file. Database version 30; your data is untouched.

## 🧭 Places and trips round 2: more for Rules (E8b)

### ✨ New

- **More things that set a rule off:** Wi-Fi connects or disconnects (any network or one by name), a Bluetooth device connects or disconnects, an app is opened, an app is used for more than some minutes in a day, a call comes or goes (any, taken, made or missed), steps reach a number, you wake up or fall asleep, a trip starts, your speed goes above (or on a trip drops below) a number, or a habit is ticked.
- **More "only if":** only while charging (or not), only while at a place, a kind of place or any named place (or not), only if a habit is not done yet today. A new switch says one of the conditions is enough instead of all of them.
- **More things a rule can do:** start a focus timer, log money, add a note to a habit, or run another rule (stops after 3 steps in a row).
- **Buttons:** a rule set to "I press Run now" appears as a button at the top of the Rules page, for things the phone cannot notice ("I went to the gym"). Rules that tick a habit make a habit done by something else.
- Messages can also use {habit}, {app}, {name}, {steps} and {kmh}.
- Wi-Fi, Bluetooth, apps, calls, steps and sleep are looked at about every 15 minutes, only if you have such a rule, so a very short connection can be missed. Things found by looking back from before a rule was made never set that rule off.
- No database change.

## 🧭 Places and trips round 2: Rules (E8a)

### ✨ New

- **Rules**: tell the app "when this happens, do that". Settings > Rules has a list of your rules, each with its own switch, a **Run now** button, and a line of **what ran and why**.
- **Things that can set a rule off:** arriving at, leaving or staying longer than some minutes at a place (any place you named, one place, or a kind of place), charging starting or stopping, a trip ending, a time of day, or you pressing Run now.
- **Only if:** only on certain days, only between two times (even past midnight).
- **What a rule can do:** tick a habit, add a Timeline entry at the moment it happened, or send you a notification. Messages can use {place}, {time}, {minutes}, {mode} and {km}.
- **It cannot loop.** Each happening is acted on once per rule, a rule waits a minute between runs by default, a rule that runs 10 times in 10 minutes is switched off by itself, and a rule set off by another stops after 3 steps.
- **A Rules switch under Settings > Features**: off means no rule runs. Time-of-day and "stay longer than" rules are checked about every 15 minutes, only if you have such a rule.
- Rules are saved in your backup file. Database version 29; your data is untouched.

## 🧭 Places and trips round 2: the replay no longer seems too fast (E7d)

### 🔧 Fixed

- **The GIF had no map.** It now draws the path on the real map (the style you chose in Maps). If the map cannot be loaded it falls back to plain paper and tells you.
- **The map was too zoomed in and the dot raced across it.** The auto zoom now widens with the replay speed: the picture always shows what the dot passes in about 5 real seconds each side, so the dot takes about 10 seconds to cross the screen at any speed, and the recent trail stays in view. The map is never closer than a few streets and can zoom out to a whole region.
- On a very fast stretch (a flight) the clock slows down a little instead of racing across the widest map.

## 🧭 Places and trips round 2: a much better replay (E7c)

### ✨ New

- **Smart speed**: the replay slows down when you are busy (walking about, turning a lot, or close to a place or something you logged) and speeds up when it is quiet, gliding between the two. **Tune** sets how much slower it gets when busy and how much faster when quiet, and has a Reset. The GIF uses it too.
- **Slower speeds**: real time (1x), 2x, 5x, 10x and 30x, then 1 minute a second and up to a day a second, with a slider that starts at real time. The speed is remembered.
- **A bar under the map** shows the whole stretch: trips in the colour of how you travelled, stays as a thin line, a tick for each card. Tap or drag it to jump anywhere.
- **A readout** under the map: how you were travelling (or the place), your speed in km/h, how far you had gone so far, and the speed with what smart speed is doing.
- **A bright recent trail**: the way you came is faint, the last 15 minutes are bright. Cards fade away after a few seconds instead of staying.

## 🧭 Places and trips round 2: replay for any stretch, auto zoom and GIF (E7b)

### ✨ New

- **The map zooms by itself while following**: close in when walking, further out in a car, further still on a train or plane, gliding between zooms. Pinch or use + and − and your zoom is kept until you tap **Auto zoom** again. Dragging the map away from the dot stops following (tap **Follow** to come back).
- **Replay this stretch** on Stats > Trips: replays whatever the page shows, a day, a week, a month, a year or any dates. Speeds go up to a day for each real second.
- **Make a GIF** from the replay: pick the size (240, 360 or 480 px) and the length (10, 20, 30 or 60 seconds), and either fit the whole stretch into that length or use your own speed. The picture is drawn by the app (path, your places, the travel picture on the dot, the clock and the cards), so it needs no map or connection. You choose where to save it.

## 🧭 Places and trips round 2: day replay fixes (E7a)

### 🔧 Changed

- **The replay map is bigger**: the title, clock and controls are small and compact, so the map takes the rest of the screen.
- **You can zoom**: pinch as usual, or use the new **+** and **−** buttons. Moving the map with your finger stops the camera from following the dot (before, it pulled the map back many times a second); the **Follow** button brings it back.
- **The dot shows how you were travelling**: a walker, a car, a train, an auto-rickshaw and so on, taken from the trip you were on at that moment (your answer, or the guess). A pin shows when you were at a place.

## 🧭 Places and trips round 2: day replay (E7a)

### ✨ New

- **Replay this day** (a button on the Timeline under the week): the day plays back like a film. A clock runs through the day, a dot sits where you were between the two nearest readings, and the path trails behind it on the map with your places as circles. Arriving at or leaving a place, and each activity you logged, pops up as a card when the clock reaches it.
- **Speeds** from 1 minute to 4 hours of the day for each real second, and your own speed on a slider. Play, pause, previous and next card, and a slider to jump to any time.
- **Skip quiet stretches**: sleep, long stays and stretches with no readings go by at least 4 hours a second.
- **Whole day** shows the full path; **Follow the dot** brings the camera back.
- The GIF export comes next (E7b).

## 🧭 Places and trips round 2: journey fixes (E6b)

### ✨ New

- **Dense recording when you move fast**: from about 25 km/h the app asks for a reading every 10 seconds by itself, in Gentle mode as well as GPS all day, so a train or car route is drawn exactly. It stays dense for 3 minutes after the last fast reading (a station stop does not drop it) and it never slows recording down.
- **One journey, small stops**: fast trips with only short stops between them (up to 15 minutes, with nothing you logged there) are one Travel row, with the stations listed as "Stops" and the distance added up along the route. Changing the way of travel changes every leg.

### 🔧 Changed

- A journey that is only your own answer (no readings) now says **no record of the route** and does not open a map; its distance is called a straight line.
- The **Sleep** row, and its slice of the day pie, stops where a vehicle journey begins (and starts again where it ends), so it no longer runs over a train you were awake on.
- A place bar no longer writes its name a second time right under itself.

## 🧭 Places and trips round 2: a much better travel-mode guess (E6a)

### ✨ New

- **The guess now reads the shape of the trip**: how fast while moving, how steady, how often and how long it stood still (a few minutes in the middle is a station), how smoothly the speed changed and how straight the way was. From that it tells **train**, **metro** (shown as Train or metro), **bus**, **car** and **flight** apart instead of calling every vehicle trip "not sure". Your own answers still come first, and the guess still learns from them.
- Android's own "walking" signal is now only believed when the speed agrees (a "walk" at 45 km/h is not a walk).
- **How did you travel?** is a sheet of big pictures (walk, run, cycle, motorbike, auto-rickshaw, car, bus, train or metro, flight, boat, scooter, skateboard, other) above the trip's From, To, Distance and Average speed. It opens from the Timeline row, the trip popup and the trip page.
- **Flight, Boat, Scooter and Skateboard** are new ways to travel. You choose them; the app never guesses them (a boat or a scooter cannot be told from the speed alone).

## 🧭 Places and trips round 2: place names (E5)

### ✨ New

- **Several name suggestions** for each unnamed spot, as buttons on the Location page and as chips in the naming box: the exact shop, station or landmark within 80 m first, the street address last. Plain buildings come after real places.
- **English first**: names come in English where OpenStreetMap has one (Settings > Places and trips > Places > Names); you can choose the name as written on the map instead.
- **A spot you named before** is suggested first, even if you stopped watching that place.
- **Where are you?** notification (off until you switch it on): when you stay at a spot with no name for your own minimum time, it offers the best name as a button, lets you type a name right in the notification, or "Not now". Choose the minimum stay, quiet hours and how long "Not now" keeps it quiet.

## 🧭 Places and trips round 2: settings and switches (E4)

### ✨ New

- **Features** (Settings > This app). One switch each for Food, Habits, Money, Stats, Steps and sleep, Phone usage, Calls, Charging, Places and trips, and Maps. Switched off, a part disappears from the tabs, Settings, the Timeline and Stats, and **nothing new is collected for it** (no position readings, usage, calls, charging, steps, map downloads, imports, reminders or monthly money entries) until you switch it on again. Everything collected before is kept and comes back with the switch.

### 🔧 Changed

- **Places and trips settings** are now a short first page: the master switch and five rows, each one a page of its own with one line on what it is for (Recording, Places, Maps, Trips and data, Advanced).
- With Maps switched off, trips and places are drawn on plain paper and no map pictures are fetched.
- The "how it works" text no longer mentions joining trips.

## 🧭 Places and trips round 2: the live map (E3)

### ✨ New

- **On the move** row at the top of today on the Timeline. While you are moving it shows how long, how far and how fast, refreshes every few seconds (only while the screen is on) and opens the live map.
- **Live map**: your position follows you smoothly like Google Maps, with the accuracy circle, today's path so far, your saved places as circles and your offline map areas. Move the map yourself and following stops; **Centre on me** turns it back on. It uses the readings the app already takes, so it does not ask the phone for a second position.
- **Status card** under the map: recording on, paused or waiting, the last reading and how exact it is, how often readings are taken, and the profile in force, with a **Pause / Resume** button.

## 🧭 Places and trips round 2: the Trips view (E2)

### ✨ New

- **Trips** page (Stats tab > Trips, or Location > See whole trips). Pick a day, week, month, year or your own dates and see whole trips in three tabs: **Map** (every trip in its own colour, tap a path to pick it), **Trips** (a list by day) and **Data** (distance, time on the move, average speed, longest and fastest trip, totals by way of travelling and by day).
- **Tap a trip on the Timeline** and a card opens with only that trip on the map (coloured by speed), its numbers and where it began and ended, with a button to the full trip page. The small pencil on the row still changes how you travelled.
- A **Trips** card on the Stats tab shows the day's trips and opens the page.

### 🔧 Changed

- Hidden trips, and trips a join took away, are left out of the Trips page, the same as on the Timeline. Nothing is changed or deleted by looking.

## 🧭 Places and trips round 2: undo and cleanup (E1)

### ✨ New

- **Changes** (Settings > Places and trips > Changes). Every trip you joined or cut and every trip or stay you hid is listed, newest first, with a button to bring it back. Nothing is ever deleted.
- **Bring back all joined trips**: one button undoes every join, newest first. A join you cut afterwards is left for you to undo, and says so.

### 🔧 Changed

- **Merge is gone** from the trip page. Trips you had already joined can be brought back from the Changes page, and the trip page still shows its own changes with Undo.
- **Two stays in a row at the same named place** (for example a station stop split in two) now show as one stay, as long as no trip lies between them and there is no visible gap. Both are still kept in the data.

## 🧭 Places and trips remake: the finish (C9)

### 🔧 Fixed

- **Trips as GPX** no longer writes stray readings after you split a trip or undo a merge, and leaves out a trip that was joined to another (it is already in that trip's track).
- **Stats** no longer count trips or stays you hid, or that a merge joined away, in the Travel line and the place times.
- **Undoing a split** takes the right trip back when a merge once hid a trip starting at the same moment.
- **Joining a walk to a drive** guesses the way you travelled again for the whole trip (your own answer still stays).
- **Undoing a merge** finds the trip's end place again, and no longer shows a trip you had hidden yourself.
- **Tidy everything from my readings** now builds a trip that crosses from one week into the next.

### 📝 Changed

- The help text in Settings > Places and trips now explains both recording ways, the Location page, trip editing, maps and what goes online.

## 🧭 Places and trips remake: import (C8b)

### ✨ New

- **Import card** (Settings > Data sources). Choose a **GPX, KML, GeoJSON or CSV** file, or a **Google Timeline** export (Records.json, or the export made on the phone). The app finds the type from what is inside.
- **A preview first:** how many readings, on which days, how many are new, already here (same second), too inaccurate, or without a position or time. Only **Import** adds them. Nothing is replaced or deleted, and visits are worked out again afterwards.
- Big files are read in one pass without loading them whole.

### 🔧 Changed

- The old Import GPX button is replaced by the card. The old monthly Google files (Semantic Location History) are not read yet, and the preview says so.

## 🧭 Places and trips remake: export (C8a)

### ✨ New

- **Export card** (Settings > Data sources > Export). Save your position readings as **GPX, KML, GeoJSON or CSV**, for everything, one day or a range of days. Android asks where to save the file.
- **Automatic export.** Pick a folder and Every day or Every week; the app saves the readings since the last export into it. **Export now** runs it at once. The card says what the last run did.

### 🔧 Changed

- The two buttons for Readings as GPX and as CSV are replaced by the card. Trips as GPX stays.

## 🧭 Places and trips remake: offline maps (C7b)

### ✨ New

- **Offline areas** (Settings > Places and trips > Maps). **Download an area** by hand: move the map so the part you want fills the window, see the number of tiles, the size and the free space, then download. The list shows each area with its style, how it was added and its state, with **Download again** and **Delete area** (the tiles are removed; the entry stays).
- **A map for every place:** each place gets the map around it as soon as it is made and again if it changes. You choose how far around.
- **A map where you go:** a reading outside every downloaded area asks for the map around it. With no connection it waits and downloads when one is back.
- A switch for each kind, and **Use mobile data for map downloads** (off means Wi-Fi only).

### 🔧 Changed

- OpenFreeMap allows at most 3,000 tiles or 50 MB in one download. The app follows that for its built-in styles and says so when an area is too big. Your own styles have no limit from the app. The only refusal from the app is not enough free space.

## 🧭 Places and trips remake: the map (C7a)

### ✨ New

- **A real map** on the trip page and the place page. The path is coloured by speed, and tapping it picks a reading. If the map cannot load, the old plain drawing is shown.
- **Pick on map** in the place editor: move the map so the cross is on the spot.
- **Settings > Places and trips > Maps.** Three built-in styles (from OpenFreeMap), and **Add your own style** with a style address or a tile address, and a key if its provider wants one. A style you hide is kept.

### 🔧 Changed

- The map is the one place the app talks to an outside server: asking for a tile tells that server roughly which area you look at. Nothing else is sent.
- Your own map styles are included in the backup.

## 🧭 Places and trips remake: the new screens (C6)

### ✨ New

- **Location page** (Settings > Places and trips > Location, and from the Timeline). A month calendar with a dot on each day you moved, a summary of the day, the day's stays and trips, spots to look at, and hidden trips you can show again.
- **Trip page.** The path, distance, time and speeds. **Merge with previous or next**, **Split here** on a reading you pick, a note on a reading, **Hide** and **Show again**, and **Undo** for a merge or a split.
- **Place page.** Its circle, every stay, its pause zone in words, and **Edit place**.
- **Open trip** and **Open place** buttons on the Timeline.
- With GPS all day, trips the live detector missed are added from the saved readings.

### 🔧 Changed

- Settings > Places and trips is in a new order: Recording, Profiles, Places, then the rest.
- The path is drawn on plain paper for now. The map comes next.
- Nothing is deleted. Hidden trips and stays are kept.

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
