package com.lifetracker.app.data

/**
 * Ready-made ideas for app combinations, and the suggestions built from your own use.
 *
 * Nothing here is ever applied by itself. A suggestion only shows up as a card; a combination exists once you name it
 * (see UsageComboEntity). Apps are recognised by their name on your phone (and a few well-known package names), so
 * "Physics Wallah" and "PW" are both found. If an example does not fit your phone you simply do not accept it.
 */
object UsageCombos {
    /** One part of an example: any [count] different apps whose name matches one of the [keywords]. */
    data class Member(val keywords: List<String>, val count: Int = 1) {
        fun matches(pkg: String, label: String): Boolean = keywords.any { keywordMatches(it, pkg, label) }
    }

    /** A built-in combination idea. [required] parts must all be found; [optional] ones are added when present. */
    data class Example(
        val name: String,
        /** The first of these names that is one of your activities is suggested as the category. */
        val activityNames: List<String>,
        val required: List<Member>,
        val optional: List<Member> = emptyList(),
    )

    /** One combination worth naming. [key] is the app set, so a dismissed suggestion stays dismissed. */
    data class Suggestion(
        val key: String,
        val name: String,
        val pkgs: Set<String>,
        val activityId: String,
        val why: String,
        val times: Int,
        val days: Int,
    )

    private val PAYMENT = Member(listOf("google pay", "gpay", "phonepe", "paytm", "bhim", "cred", "amazon pay", "mobikwik"))
    private val BROWSER = Member(listOf("chrome", "brave", "firefox", "edge", "opera", "duckduckgo", "vivaldi"))
    private val AI = Member(listOf("chatgpt", "claude", "gemini", "grammarly", "copilot", "perplexity", "deepseek"))

    val EXAMPLES: List<Example> = listOf(
        Example(
            "Study", listOf("Study"),
            listOf(
                Member(listOf("physics wallah", "pw", "xyz.penpencil.physicswala")),
                Member(listOf("chatgpt", "com.openai.chatgpt")),
            ),
        ),
        Example(
            "Building", listOf("Building", "Coding", "Work"),
            listOf(
                Member(listOf("claude", "com.anthropic.claude")),
                Member(listOf("brave", "com.brave.browser")),
            ),
            optional = listOf(Member(listOf("github", "com.github.android"))),
        ),
        Example(
            "Travel", listOf("Travel"),
            listOf(
                Member(listOf("maps", "com.google.android.apps.maps")),
                Member(listOf("uber", "ola", "rapido", "redbus", "irctc", "ixigo", "makemytrip", "indrive", "namma yatri", "abhibus")),
            ),
        ),
        Example(
            "Shopping", listOf("Shopping"),
            listOf(
                Member(listOf("amazon", "flipkart", "myntra", "meesho", "ajio", "nykaa", "blinkit", "zepto", "bigbasket", "swiggy instamart")),
                PAYMENT,
            ),
        ),
        Example(
            "Banking", listOf("Banking", "Money", "Finance"),
            listOf(
                Member(listOf("bank", "banking", "hdfc", "icici", "kotak", "yono", "axis", "indusind", "canara", "idfc")),
                PAYMENT,
            ),
        ),
        Example(
            "Reading", listOf("Reading"),
            listOf(
                Member(listOf("kindle", "pocket", "medium", "wikipedia", "inshorts", "dailyhunt", "google news", "flipboard", "audible")),
                BROWSER,
            ),
        ),
        Example(
            "Writing", listOf("Writing", "Work"),
            listOf(
                Member(listOf("docs", "notion", "obsidian", "keep", "notes", "word", "onenote", "evernote", "joplin")),
                AI,
            ),
        ),
        Example(
            "Chatting", listOf("Social", "Chatting"),
            listOf(Member(listOf("whatsapp", "telegram", "signal", "messenger", "instagram", "snapchat", "discord", "messages"), count = 2)),
        ),
    )

    /**
     * Whether [keyword] names the app. A keyword of several words must appear in the name; a single word must be a word of the
     * name (so "word" finds "Microsoft Word" but not "Wordle"), or, from five letters up, the start of one ("chatgpt").
     * A keyword with a dot is a package name.
     */
    internal fun keywordMatches(keyword: String, pkg: String, label: String): Boolean {
        val k = keyword.lowercase()
        if ('.' in k) return pkg.lowercase() == k || pkg.lowercase().startsWith("$k.")
        val l = label.lowercase()
        if (' ' in k) return k in l
        return l.split(' ', '-', '_', '+', '/').any { it == k || (k.length >= 5 && it.startsWith(k)) }
    }

    /** Which apps of [apps] (package, name) an [example] uses, or null when it does not fit. Needs every required part, each with its own app. */
    fun matchExample(example: Example, apps: List<Pair<String, String>>): Set<String>? {
        fun assign(index: Int, used: Set<String>): Set<String>? {
            if (index == example.required.size) return used
            val member = example.required[index]
            val free = apps.filter { it.first !in used && member.matches(it.first, it.second) }
            if (free.size < member.count) return null
            // Try each way of choosing which of the matching apps fills this part, so a shared word cannot block a match.
            val choices = if (member.count == 1) free.map { listOf(it) } else listOf(free.take(member.count))
            for (choice in choices) {
                val found = assign(index + 1, used + choice.map { it.first })
                if (found != null) return found
            }
            return null
        }
        val base = assign(0, emptySet()) ?: return null
        val extra = example.optional.flatMap { m -> apps.filter { it.first !in base && m.matches(it.first, it.second) }.map { it.first } }
        return base + extra
    }

    /** The first built-in example that fits these apps, with the apps it uses. */
    fun firstExample(apps: List<Pair<String, String>>): Pair<Example, Set<String>>? {
        for (e in EXAMPLES) matchExample(e, apps)?.let { return e to it }
        return null
    }

    /** The id of the activity an example suggests: one of your activities with a matching name, or "" when you have none. */
    fun activityFor(example: Example, types: List<ActivityTypeEntity>): String {
        for (name in example.activityNames) {
            types.firstOrNull { !it.archived && it.name.equals(name, ignoreCase = true) }?.let { return it.id }
        }
        return ""
    }

    fun keyOf(pkgs: Collection<String>): String = pkgs.toSortedSet().joinToString(",")

    /** The apps of a period that count towards what it was: those with at least a fifth of its time. */
    fun mainApps(block: UsageDays.Block): List<UsageDays.AppUse> =
        block.apps.filter { it.activeMs * 5 >= block.activeMs }

    /** What to suggest for the apps of one period, used to fill in the naming pop-up. */
    fun suggestionFor(block: UsageDays.Block, types: List<ActivityTypeEntity>): Pair<String, String> {
        val main = mainApps(block)
        val found = firstExample(main.map { it.pkg to it.label })
        return if (found != null) found.first.name to activityFor(found.first, types)
        else main.joinToString(" + ") { it.label } to ""
    }

    /**
     * Combinations worth naming, from days already worked out: a built-in example that fits a period, or the same apps together
     * in at least [MIN_TIMES] periods on at least [MIN_DAYS] days. Sets you already cover with a combination, and sets you
     * dismissed, are left out.
     */
    fun suggest(
        days: List<UsageDays.Day>,
        combos: List<UsageComboEntity>,
        types: List<ActivityTypeEntity>,
        dismissed: Set<String>,
    ): List<Suggestion> {
        class Seen(val labels: Map<String, String>) {
            var times = 0
            val dayIndexes = HashSet<Int>()
        }
        val seen = LinkedHashMap<String, Seen>()
        days.forEachIndexed { index, day ->
            for (block in day.blocks) {
                if (block.comboId.isNotEmpty()) continue
                val main = mainApps(block)
                if (main.size < 2 || main.size > MAX_APPS) continue
                val pkgs = main.map { it.pkg }.toSet()
                if (combos.any { c -> c.pkgSet.count { it in pkgs } >= minOf(2, c.pkgSet.size) }) continue
                val key = keyOf(pkgs)
                if (key in dismissed) continue
                val s = seen.getOrPut(key) { Seen(main.associate { it.pkg to it.label }) }
                s.times++
                s.dayIndexes += index
            }
        }
        val out = ArrayList<Suggestion>()
        for ((key, s) in seen) {
            val pkgs = key.split(',').toSet()
            val example = firstExample(s.labels.map { it.key to it.value })
            val byHistory = s.times >= MIN_TIMES && s.dayIndexes.size >= MIN_DAYS
            if (example == null && !byHistory) continue
            val name = example?.first?.name ?: pkgs.joinToString(" + ") { s.labels[it] ?: it }
            val why = if (example != null) "Looks like ${example.first.name}" else "Used together ${s.times} times on ${s.dayIndexes.size} days"
            out += Suggestion(key, name, pkgs, example?.let { activityFor(it.first, types) }.orEmpty(), why, s.times, s.dayIndexes.size)
        }
        return out.sortedWith(compareByDescending<Suggestion> { it.times }.thenBy { it.name })
    }

    const val MIN_TIMES = 3
    const val MIN_DAYS = 2
    const val MAX_APPS = 4

    /** How many recent days suggestions look at. */
    const val LOOKBACK_DAYS = 30
}
