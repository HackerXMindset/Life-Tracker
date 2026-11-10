package com.lifetracker.app.data

import android.content.Context

/*
 * The food list that comes with the app: 561 common foods and 27 ready dishes, each with a photo, taken from GymMane
 * (https://github.com/InlitX/GymMane, GPL-3.0). Based on GymMane by InlitX.
 *
 * Everything is per 100 g, as in the rest of the app. Sodium is in milligrams, everything else in grams (kcal for energy).
 * The list is read once from the app's assets and kept in memory; it is never stored in the database. Foods you make
 * yourself live in the `foods` table (see [FoodEntity]).
 */

/** How one portion of a food is counted: "1 banana", "2 slices", "1 tbsp". */
enum class PortionKind(val key: String, val one: String, val many: String) {
    Unit("unit", "piece", "pieces"),
    Slice("slice", "slice", "slices"),
    Scoop("scoop", "scoop", "scoops"),
    Tbsp("tbsp", "tbsp", "tbsp"),
    Tsp("tsp", "tsp", "tsp"),
    Cup("cup", "cup", "cups"),
    Glass("glass", "glass", "glasses"),
    Katori("katori", "katori", "katoris"),
    Bowl("bowl", "bowl", "bowls"),
    /** A food you saved from Quick add: one "serving" of whatever you typed. */
    Serving("serving", "serving", "servings");

    companion object {
        fun of(key: String): PortionKind? = entries.firstOrNull { it.key == key }
    }
}

/** The groups the food list is sorted into. */
enum class FoodCat(val key: String, val label: String) {
    Fruit("fruit", "Fruit"),
    Veg("veg", "Vegetables"),
    Meat("meat", "Meat"),
    Fish("fish", "Fish"),
    Egg("egg", "Eggs"),
    Dairy("dairy", "Dairy"),
    Grain("grain", "Grains"),
    Bakery("bakery", "Bakery"),
    Legume("legume", "Legumes"),
    Nut("nut", "Nuts and seeds"),
    Oil("oil", "Oils and fats"),
    Pantry("pantry", "Pantry"),
    Drink("drink", "Drinks"),
    Sauce("sauce", "Sauces"),
    Spice("spice", "Spices"),
    Sweet("sweet", "Sweets"),
    Snack("snack", "Snacks"),
    Indian("indian", "Indian dishes");

    companion object {
        fun of(key: String): FoodCat? = entries.firstOrNull { it.key == key }
    }
}

/** What 100 g of a food holds. [amino] is the nine essential amino acids in g per 100 g, or empty when not known. */
data class Per100(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val animalProtein: Double = 0.0,
    val sat: Double = 0.0,
    val mono: Double = 0.0,
    val poly: Double = 0.0,
    val amino: List<Double> = emptyList(),
)

/** One food of the built-in list. [id] is the file name of its photo without ".webp" and is stored in meals as "a:" + id. */
data class LibraryFood(
    val id: String,
    val name: String,
    val per100: Per100,
    /** A liquid is counted in ml (1 ml = 1 g here). */
    val liquid: Boolean,
    val portion: PortionKind?,
    val portionGrams: Double,
    val cat: FoodCat?,
    /** The numbers are an estimate, not a measurement. */
    val approx: Boolean,
    /** Cooked weight per gram of raw (rice 2.7), or 0 when the food is not cooked. */
    val cookedYield: Double,
) {
    val ref: String get() = FoodRef.LIBRARY + id
    val photoAsset: String get() = "food/photos/$id.webp"
    val canCook: Boolean get() = cookedYield > 0
}

/** One line of a ready dish. [cooked] says [grams] is the cooked weight. */
data class DishItem(val foodId: String, val grams: Double, val cooked: Boolean)

data class ReadyDish(val id: String, val name: String, val items: List<DishItem>) {
    val ref: String get() = FoodRef.DISH + id
}

/** How a logged meal points back to the food it came from. */
object FoodRef {
    const val LIBRARY = "a:"
    const val MINE = "m:"
    const val DISH = "d:"
    const val SAVED = "s:"

    fun isLibrary(ref: String) = ref.startsWith(LIBRARY)
    fun isMine(ref: String) = ref.startsWith(MINE)
}

/** The whole built-in list, with search. Build it with [FoodLibrary.parse] (tests) or [FoodLibrary.get] (the app). */
class FoodLibraryData(
    val foods: List<LibraryFood>,
    val dishes: List<ReadyDish>,
    /** Vitamins and minerals per 100 g that come with the app: food id, then nutrient id, then amount. Empty for a food with none. */
    val micro: Map<String, Map<String, Double>> = emptyMap(),
    /** Other names for the same food: each word points to every word of its group ("sooji" to rava, semolina, suji), itself included. */
    val aliases: Map<String, Set<String>> = emptyMap(),
) {
    private val byId = foods.associateBy { it.id }
    private val folded = foods.map { FoodLibrary.fold(it.name) }

    fun food(id: String): LibraryFood? = byId[id]

    /** The vitamins and minerals per 100 g that come with the app for a food (before anything you typed). */
    fun microOf(id: String): Map<String, Double> = micro[id] ?: emptyMap()

    /** The food a stored reference ("a:banana") points to. */
    fun byRef(ref: String): LibraryFood? = if (FoodRef.isLibrary(ref)) byId[ref.removePrefix(FoodRef.LIBRARY)] else null

    fun inCat(cat: FoodCat): List<LibraryFood> = foods.filter { it.cat == cat }

    /**
     * Foods whose name has every word of [query] (or another name for that word, so "chapati" finds roti and "sooji" finds rava).
     * Exact name first, then names that start with it, then the rest by length; a food found only through another name comes last.
     * Within the same kind of match, the foods in [boost] (food references such as your starred and recent ones) come first.
     */
    fun search(query: String, cat: FoodCat? = null, limit: Int = 60, boost: Set<String> = emptySet()): List<LibraryFood> {
        val q = FoodLibrary.fold(query).trim()
        if (q.isEmpty()) return if (cat == null) emptyList() else inCat(cat).take(limit)
        val words = q.split(' ').filter { it.isNotEmpty() }
        val scored = ArrayList<Triple<Int, Int, LibraryFood>>()
        for (i in foods.indices) {
            val f = foods[i]
            if (cat != null && f.cat != cat) continue
            val name = folded[i]
            val direct = words.all { name.contains(it) }
            if (!direct && !words.all { w -> (aliases[w] ?: setOf(w)).any { name.contains(it) } }) continue
            val rank = when {
                !direct -> 4
                name == q -> 0
                name.startsWith(q) -> 1
                name.split(' ', '-').any { it.startsWith(words.first()) } -> 2
                else -> 3
            }
            scored += Triple(rank * 2 + (if (f.ref in boost) 0 else 1), name.length, f)
        }
        return scored.sortedWith(compareBy({ it.first }, { it.second })).take(limit).map { it.third }
    }

    fun searchDishes(query: String): List<ReadyDish> {
        val words = FoodLibrary.fold(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return dishes
        return dishes.filter { d -> val n = FoodLibrary.fold(d.name); words.all { n.contains(it) } }
    }
}

object FoodLibrary {
    private const val FOODS_FILE = "food/foods.tsv"
    private const val DISHES_FILE = "food/dishes.tsv"
    private const val MICRO_FILE = "food/micro.tsv"
    private const val ALIASES_FILE = "food/aliases.tsv"
    private val EXTRA_FILES = listOf("food/foods-india.tsv", "food/foods-ifct.tsv")

    @Volatile
    private var cached: FoodLibraryData? = null

    /** The built-in list, read from the app's assets the first time. */
    fun get(context: Context): FoodLibraryData = cached ?: synchronized(this) {
        cached ?: run {
            val assets = context.applicationContext.assets
            val foods = join(
                assets.open(FOODS_FILE).bufferedReader().use { it.readText() },
                *EXTRA_FILES.map { f -> runCatching { assets.open(f).bufferedReader().use { it.readText() } }.getOrDefault("") }.toTypedArray(),
            )
            val dishes = assets.open(DISHES_FILE).bufferedReader().use { it.readText() }
            val micro = runCatching { assets.open(MICRO_FILE).bufferedReader().use { it.readText() } }.getOrDefault("")
            val aliases = runCatching { assets.open(ALIASES_FILE).bufferedReader().use { it.readText() } }.getOrDefault("")
            parse(foods, dishes, micro, aliases).also { cached = it }
        }
    }

    /** The main list with the rows of another list (same columns, its own header line) added below it. */
    fun join(base: String, vararg extras: String): String {
        val rows = extras.flatMap { it.lines().drop(1).filter { line -> line.isNotBlank() } }
        return if (rows.isEmpty()) base else base.trimEnd('\n') + "\n" + rows.joinToString("\n") + "\n"
    }

    /** Lower case, without accents, so "Café" is found by "cafe". */
    fun fold(text: String): String {
        val from = "áàâäãåąéèêëęíìîïıóòôöõőøúùûüűñńçćčłśşšźżžğýÿ"
        val to = "aaaaaaaeeeeeiiiiiooooooouuuuunnccclssszzzgyy"
        val out = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            val i = from.indexOf(ch)
            out.append(if (i < 0) ch else to[i])
        }
        return out.toString()
    }

    /** Reads the two files. A line that cannot be read is skipped. */
    fun parse(foodsTsv: String, dishesTsv: String, microTsv: String = "", aliasesTxt: String = ""): FoodLibraryData {
        val foods = ArrayList<LibraryFood>()
        val lines = foodsTsv.lines().filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            val head = lines.first().split('\t')
            fun col(key: String) = head.indexOf(key)
            for (line in lines.drop(1)) {
                val c = line.split('\t')
                if (c.size < head.size - 1) continue
                fun cell(key: String): String = col(key).let { if (it < 0 || it >= c.size) "" else c[it] }
                fun num(key: String): Double = cell(key).toDoubleOrNull() ?: 0.0
                val id = cell("id")
                val name = cell("en")
                if (id.isEmpty() || name.isEmpty()) continue
                val amino = cell("aa").split(',').mapNotNull { it.toDoubleOrNull() }.takeIf { it.size == 9 } ?: emptyList()
                foods += LibraryFood(
                    id = id,
                    name = name,
                    per100 = Per100(
                        kcal = num("k"), protein = num("p"), carbs = num("c"), fat = num("f"), fiber = num("fi"),
                        sugar = num("su"), sodiumMg = num("na"), animalProtein = num("an"), sat = num("sa"),
                        mono = num("mu"), poly = num("po"), amino = amino,
                    ),
                    liquid = cell("ml").isNotEmpty(),
                    portion = PortionKind.of(cell("kind")),
                    portionGrams = num("g"),
                    cat = FoodCat.of(cell("cat")),
                    approx = cell("ax").isNotEmpty(),
                    cookedYield = num("cy"),
                )
            }
        }
        val dishes = ArrayList<ReadyDish>()
        for (line in dishesTsv.lines().drop(1)) {
            val c = line.split('\t')
            if (c.size < 3 || c[0].isEmpty()) continue
            val items = c[2].split(' ').mapNotNull { part ->
                val bits = part.split(':')
                if (bits.size != 2 || bits[0].isEmpty()) return@mapNotNull null
                val grams = bits[1].removeSuffix("c").toDoubleOrNull() ?: return@mapNotNull null
                DishItem(bits[0], grams, bits[1].endsWith("c"))
            }
            if (items.isNotEmpty()) dishes += ReadyDish(c[0], c[1], items)
        }
        return FoodLibraryData(foods, dishes, parseMicro(microTsv), parseAliases(aliasesTxt))
    }

    /** One line per food, the words that mean it separated by spaces. A word on two lines gets the words of both. */
    fun parseAliases(text: String): Map<String, Set<String>> {
        val out = HashMap<String, MutableSet<String>>()
        for (line in text.lines()) {
            val group = line.split(' ', '\t').map { fold(it).trim() }.filter { it.isNotEmpty() }.toSet()
            if (group.size < 2) continue
            for (w in group) out.getOrPut(w) { HashSet() }.addAll(group)
        }
        return out
    }

    /** The first column is the food id, the other columns are headed by a nutrient id and hold amounts per 100 g (empty = not known). */
    fun parseMicro(tsv: String): Map<String, Map<String, Double>> {
        val lines = tsv.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyMap()
        val head = lines.first().split('\t')
        val out = LinkedHashMap<String, Map<String, Double>>()
        for (line in lines.drop(1)) {
            val c = line.split('\t')
            if (c.isEmpty() || c[0].isEmpty()) continue
            val values = LinkedHashMap<String, Double>()
            for (i in 1 until minOf(head.size, c.size)) {
                val v = c[i].toDoubleOrNull() ?: continue
                if (v > 0) values[head[i]] = v
            }
            if (values.isNotEmpty()) out[c[0]] = values
        }
        return out
    }
}
