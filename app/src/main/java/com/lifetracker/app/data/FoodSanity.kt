package com.lifetracker.app.data

/**
 * Checks that catch a number which cannot be right: a typo in the unit ("20 kg" for 20 g), a decimal point in the wrong place, or
 * calories that do not match the protein, carbs and fat beside them. They only say what looks wrong; nothing is changed or blocked.
 */
object FoodSanity {
    /** Calories in a gram of each, as printed on packs (Atwater). */
    private const val KCAL_PROTEIN = 4.0
    private const val KCAL_CARBS = 4.0
    private const val KCAL_FAT = 9.0

    /** A pure fat has about 900 kcal in 100 g, so nothing can have more than this. */
    const val MAX_KCAL_100 = 910.0
    const val BIG_AMOUNT_GRAMS = 3000.0
    const val BIG_ENTRY_KCAL = 3500.0

    fun macroKcal(protein: Double, carbs: Double, fat: Double): Double = KCAL_PROTEIN * protein + KCAL_CARBS * carbs + KCAL_FAT * fat

    private fun n(v: Double): String = Math.round(v).toString()

    /**
     * Calories that are more than a fifth away from what protein, carbs and fat add up to (and more than 30 kcal away, so small
     * foods are left alone). Nothing is said when the macros are blank, or when the calories are. Fibre and alcohol move the
     * sum a little, which is why the allowance is wide.
     */
    fun macroMismatch(kcal: Double, protein: Double, carbs: Double, fat: Double): String? {
        if (kcal <= 0 || protein < 0 || carbs < 0 || fat < 0) return null
        val from = macroKcal(protein, carbs, fat)
        if (from <= 0) return null
        val gap = Math.abs(kcal - from)
        if (gap <= 30 || gap <= 0.2 * maxOf(kcal, from)) return null
        return if (kcal > from) {
            "The calories (${n(kcal)}) are more than protein, carbs and fat explain (about ${n(from)}). Something may be missing, or the calories are too high."
        } else {
            "The calories (${n(kcal)}) are less than protein, carbs and fat add up to (about ${n(from)}). One of them is probably off."
        }
    }

    /** Numbers per 100 g that cannot exist. [kcal100] and the macros are for 100 g (or 100 ml). */
    fun impossible100(kcal100: Double, protein100: Double, carbs100: Double, fat100: Double): List<String> {
        val out = ArrayList<String>()
        if (kcal100 > MAX_KCAL_100) out.add("${n(kcal100)} kcal in 100 g is not possible: pure oil is about 900. Check the number, or whether it is for the whole pack.")
        val grams = protein100 + carbs100 + fat100
        if (grams > 105) out.add("Protein, carbs and fat add up to ${n(grams)} g in 100 g, which is more than there is. Check the numbers.")
        return out
    }

    /** An amount or an entry that is much bigger than anyone eats at once. [grams] is the weight (or ml) logged, [kcal] its calories. */
    fun bigAmount(grams: Double, kcal: Double): List<String> {
        val out = ArrayList<String>()
        if (grams >= BIG_AMOUNT_GRAMS) out.add("${n(grams)} g is a very large amount for one entry. Check the unit: kg, g and ml are easy to mix up.")
        if (kcal >= BIG_ENTRY_KCAL) out.add("${n(kcal)} kcal is more than most people eat in a whole day. Check the amount.")
        return out
    }

    /** Everything worth a second look for one logged amount: numbers are for the whole [grams]. At most three lines. */
    fun check(grams: Double, kcal: Double, protein: Double, carbs: Double, fat: Double): List<String> {
        val out = ArrayList<String>()
        out.addAll(bigAmount(grams, kcal))
        if (grams > 0) {
            val k = 100.0 / grams
            out.addAll(impossible100(kcal * k, protein * k, carbs * k, fat * k))
        }
        macroMismatch(kcal, protein, carbs, fat)?.let { out.add(it) }
        return out.take(3)
    }

    /** The same check for a food typed in per 100 g. */
    fun checkPer100(kcal100: Double, protein100: Double, carbs100: Double, fat100: Double): List<String> {
        val out = ArrayList<String>()
        out.addAll(impossible100(kcal100, protein100, carbs100, fat100))
        macroMismatch(kcal100, protein100, carbs100, fat100)?.let { out.add(it) }
        return out.take(3)
    }
}
