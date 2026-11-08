package com.lifetracker.app.data.gymmane

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.Feature
import com.lifetracker.app.data.Features
import com.lifetracker.app.data.FoodLibrary
import com.lifetracker.app.data.FoodRef
import com.lifetracker.app.data.ImportedItemEntity
import com.lifetracker.app.data.SourceScanner
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GymManResult(
    val fileName: String,
    val meals: Int,
    val water: Int,
    val supplements: Int,
    val fasts: Int,
    val weights: Int,
) {
    fun describe(): String =
        "Imported from $fileName: $meals meals, water on $water days, $supplements supplements, $fasts fasts, $weights weigh-ins."
}

/**
 * Keeps Kairos and GymMane's backup folder in step.
 *
 * Coming in: the newest GymMane backup is read (nothing in it is changed) and what is new is added once.
 * If you delete something here, it does not come back; changes made in GymMane after the first import are not copied.
 *
 * Going out (only if switched on): a new zip, `gymmane-kairos-….zip`, is written next to GymMane's own.
 * It is a copy of GymMane's newest backup with the food, water, supplements, fasts and weights
 * that only exist here added. Nothing in the copy is removed or changed, and GymMane's own files are never touched.
 * To use it, restore it inside GymMane.
 */
object GymManSync {
    private const val MEAL = "gymmane-meal"
    private const val SUPP = "gymmane-supp"
    private const val TAKEN = "gymmane-taken"
    private const val FAST = "gymmane-fast"
    private const val WEIGHT = "gymmane-weight"
    private const val KEEP_COPIES = 3

    suspend fun importIfNew(context: Context, force: Boolean = false): GymManResult? = withContext(Dispatchers.IO) {
        if (!Features.isOn(context, Feature.FOOD)) return@withContext null
        val sources = DataSources(context)
        val folder = sources.folder(DataSources.Slot.GymMane) ?: throw IOException("Choose the GymMane folder first.")
        val file = SourceScanner.newestGymMane(context, folder)
            ?: throw IOException("No GymMane backup found in that folder.")
        val key = "${file.name}|${file.modified}"
        if (!force && sources.gymSeenKey == key) return@withContext null

        val text = readJson(context, file.uri, file.name)
        val library = FoodLibrary.get(context)
        val data = try {
            GymManCodec.parse(text) { ref -> FoodRef.isLibrary(ref) && library.byRef(ref) != null }
        } catch (e: org.json.JSONException) {
            throw IOException("Could not read ${file.name}.")
        }
        val result = apply(AppDatabase.get(context), data, file.name)
        sources.gymSeenKey = key
        sources.lastGymImportText = LocalDateTime.now().toString()
        result
    }

    private suspend fun apply(db: AppDatabase, data: GymManData, name: String): GymManResult {
        val food = db.foodDao()
        val extras = db.foodExtrasDao()
        val marks = db.importDao()
        var meals = 0
        var water = 0
        var supps = 0
        var fasts = 0
        var weights = 0
        db.withTransaction {
            val knownMeals = marks.sourceIds(MEAL).toHashSet()
            val newMeals = data.meals.filter { it.id !in knownMeals }
            food.upsertMeals(newMeals)
            marks.insertAll(newMeals.map { ImportedItemEntity(MEAL, it.id, 0) })
            meals = newMeals.size

            // Water: a day keeps whichever is more, so what you drank here is never lowered.
            val have = food.allWater().associate { it.date to it.ml }
            for ((day, ml) in data.water) {
                if (ml > (have[day] ?: 0)) {
                    food.setWater(com.lifetracker.app.data.WaterEntity(day, ml))
                    water++
                }
            }

            val knownSupps = marks.sourceIds(SUPP).toHashSet()
            val newSupps = data.supplements.filter { it.id !in knownSupps }
            extras.upsertSupplements(newSupps)
            marks.insertAll(newSupps.map { ImportedItemEntity(SUPP, it.id, 0) })
            supps = newSupps.size

            // Ticks only for supplements that are still here (one you deleted stays deleted).
            val present = extras.allSupplements().map { it.id }.toHashSet()
            val knownTaken = marks.sourceIds(TAKEN).toHashSet()
            val newTaken = data.taken.filter { it.supplementId in present && "${it.supplementId}|${it.date}" !in knownTaken }
            extras.upsertTaken(newTaken)
            marks.insertAll(newTaken.map { ImportedItemEntity(TAKEN, "${it.supplementId}|${it.date}", 0) })

            val knownFasts = marks.sourceIds(FAST).toHashSet()
            val newFasts = data.fasts.filter { it.id !in knownFasts }
            extras.upsertFasts(newFasts)
            marks.insertAll(newFasts.map { ImportedItemEntity(FAST, it.id, 0) })
            fasts = newFasts.size

            val weighed = extras.allWeights().map { it.date }.toHashSet()
            val knownWeights = marks.sourceIds(WEIGHT).toHashSet()
            val newWeights = data.weights.filter { it.date !in knownWeights && it.date !in weighed }
            extras.upsertWeights(newWeights)
            marks.insertAll(data.weights.filter { it.date !in knownWeights }.map { ImportedItemEntity(WEIGHT, it.date, 0) })
            weights = newWeights.size
        }
        return GymManResult(name, meals, water, supps, fasts, weights)
    }

    /**
     * Writes the copy with Kairos' data added, if that is switched on and something changed since the last time.
     * Returns the new file's name, or null when there was nothing to do.
     */
    suspend fun sendIfChanged(context: Context, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!Features.isOn(context, Feature.FOOD)) return@withContext null
        val sources = DataSources(context)
        if (!force && !sources.gymSend) return@withContext null
        val folder = sources.folder(DataSources.Slot.GymMane) ?: throw IOException("Choose the GymMane folder first.")
        val base = SourceScanner.newestGymMane(context, folder)
            ?: throw IOException("No GymMane backup found in that folder to build on.")

        val out = outgoing(context)
        if (!GymManCodec.hasAnything(out)) return@withContext null
        val hash = listOf(out.meals, out.water.toList(), out.supplements, out.taken, out.fasts, out.weights).hashCode()
        if (!force && sources.gymSentHash == hash) return@withContext null

        val dir = DocumentFile.fromTreeUri(context, folder)?.takeIf { it.exists() && it.canWrite() }
            ?: throw IOException("Cannot write to the GymMane folder.")
        val name = "gymmane-kairos-" + LocalDateTime.now().format(STAMP) + ".zip"
        val target = dir.createFile("application/zip", name) ?: throw IOException("Could not create $name.")
        var wroteJson = false
        try {
            val output = context.contentResolver.openOutputStream(target.uri) ?: throw IOException("Could not write $name.")
            wroteJson = copyMerged(context, base.uri, output, out)
            if (!wroteJson) throw IOException("${base.name} does not look like a GymMane backup.")
        } catch (e: Exception) {
            runCatching { target.delete() }
            throw if (e is IOException) e else IOException(e.message ?: "Could not write $name.", e)
        }
        sources.gymSentHash = hash
        sources.lastGymSendText = LocalDateTime.now().toString()
        // Keep only the newest few copies.
        runCatching { SourceScanner.kairosCopies(context, folder).drop(KEEP_COPIES).forEach { DocumentFile.fromSingleUri(context, it.uri)?.delete() } }
        name
    }

    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")


    /** Everything in Kairos that GymMane can hold, without what came from GymMane in the first place. */
    suspend fun outgoing(context: Context): GymManOutgoing {
        val db = AppDatabase.get(context)
        val extras = db.foodExtrasDao()
        return GymManOutgoing(
            meals = db.foodDao().allMeals().filter { it.source != GymManCodec.SOURCE },
            water = db.foodDao().allWater().filter { it.ml > 0 }.associate { it.date to it.ml },
            supplements = extras.allSupplements().filter { !it.archived },
            taken = extras.allTaken(),
            fasts = extras.allFasts(),
            weights = extras.allWeights(),
        )
    }

    /** Copies the zip at [base] into [output] with the Kairos data merged into its gymmane.json. True if that file was found. */
    private fun copyMerged(context: Context, base: android.net.Uri, output: java.io.OutputStream, out: GymManOutgoing): Boolean {
        var wroteJson = false
        val input = context.contentResolver.openInputStream(base) ?: throw IOException("Could not open the GymMane backup.")
        ZipInputStream(input.buffered()).use { zin ->
            ZipOutputStream(output.buffered()).use { zout ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    zout.putNextEntry(ZipEntry(e.name))
                    if (!e.isDirectory && e.name.substringAfterLast('/') == GymManCodec.JSON_ENTRY && '/' !in e.name) {
                        zout.write(GymManCodec.merge(zin.readBytes().toString(Charsets.UTF_8), out).toByteArray(Charsets.UTF_8))
                        wroteJson = true
                    } else if (!e.isDirectory) {
                        zin.copyTo(zout)
                    }
                    zout.closeEntry()
                }
            }
        }
        return wroteJson
    }

    /**
     * The Export tab's file for GymMane, written to [target]. With a [base] (a GymMane backup) it is a copy of that with
     * Kairos' data added. Without one it holds only Kairos' data, and restoring it in GymMane replaces everything there.
     * Returns a line saying what was written.
     */
    suspend fun exportTo(context: Context, target: android.net.Uri, base: android.net.Uri?): String = withContext(Dispatchers.IO) {
        val out = outgoing(context)
        if (!GymManCodec.hasAnything(out)) throw IOException("There is no food, water, supplement, fast or weight data to export yet.")
        val output = context.contentResolver.openOutputStream(target) ?: throw IOException("Could not write the file.")
        if (base != null) {
            if (!copyMerged(context, base, output, out)) throw IOException("That file does not look like a GymMane backup.")
            "Saved your GymMane backup with Kairos' data added."
        } else {
            ZipOutputStream(output.buffered()).use { zout ->
                zout.putNextEntry(ZipEntry(GymManCodec.JSON_ENTRY))
                zout.write(GymManCodec.standalone(out).toByteArray(Charsets.UTF_8))
                zout.closeEntry()
            }
            "Saved a file with only Kairos' data."
        }
    }

    fun exportFileName(): String = "gymmane-kairos-" + LocalDateTime.now().format(STAMP) + ".zip"

    private fun readJson(context: Context, uri: android.net.Uri, name: String): String {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Could not open $name.")
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && e.name == GymManCodec.JSON_ENTRY) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        throw IOException("$name does not look like a GymMane backup.")
    }
}
