package com.lifetracker.app.data.ont

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lifetracker.app.data.AppDatabase
import com.lifetracker.app.data.DataSources
import com.lifetracker.app.data.EntryEntity
import com.lifetracker.app.data.FoodGoalEntity
import com.lifetracker.app.data.ImportedItemEntity
import com.lifetracker.app.data.SourceScanner
import java.io.IOException
import java.time.LocalDateTime
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OntResult(
    val fileName: String,
    val mealsAdded: Int,
    val goalDays: Int,
    val activitiesAdded: Int,
) {
    fun describe(): String =
        "Imported from $fileName: $mealsAdded new meals, goals for $goalDays days, " +
            "$activitiesAdded activities added to the Timeline."
}

/**
 * Brings the newest OpenNutriTracker export into the app.
 *
 * - The newest file (by modified time; the export has no date in its name) wins.
 * - It is only imported if it is a different file from the last one imported,
 *   unless you tap Import now.
 * - Each meal and activity is brought in once. If you delete one later,
 *   importing again does not bring it back. Daily goals are refreshed.
 * - Nothing in OpenNutriTracker's folder is changed.
 */
object OntImporter {
    private const val SOURCE_MEAL = "ont-meal"
    private const val SOURCE_ACTIVITY = "ont-activity"

    suspend fun importIfNew(context: Context, force: Boolean = false): OntResult? =
        withContext(Dispatchers.IO) {
            val sources = DataSources(context)
            val folder = sources.folder(DataSources.Slot.OpenNutriTracker)
                ?: throw IOException("Choose the OpenNutriTracker folder first.")
            val file = SourceScanner.newestOpenNutriTracker(context, folder)
                ?: throw IOException("No OpenNutriTracker export found in that folder.")

            val key = "${file.name}|${file.modified}"
            if (!force && sources.ontSeenKey == key) return@withContext null

            val data = read(context, file.uri, file.name)
            if (data.isEmpty) return@withContext null

            val result = apply(AppDatabase.get(context), data, file.name)
            sources.ontSeenKey = key
            sources.lastOntImportText = LocalDateTime.now().toString()
            result
        }

    private fun read(context: Context, uri: Uri, name: String): OntData {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Could not open $name.")
        val files = HashMap<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val base = entry.name.substringAfterLast('/')
                if (base in setOf(OntParser.INTAKE_FILE, OntParser.TRACKED_DAY_FILE, OntParser.ACTIVITY_FILE)) {
                    files[base] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        if (files.isEmpty()) throw IOException("$name does not look like an OpenNutriTracker export.")
        return try {
            OntParser.parse(files[OntParser.INTAKE_FILE], files[OntParser.TRACKED_DAY_FILE], files[OntParser.ACTIVITY_FILE])
        } catch (e: IllegalArgumentException) {
            throw IOException(e.message ?: "Could not read $name.")
        }
    }

    private suspend fun apply(db: AppDatabase, data: OntData, fileName: String): OntResult {
        val food = db.foodDao()
        val importDao = db.importDao()
        val entryDao = db.entryDao()
        var mealsAdded = 0
        var activitiesAdded = 0

        db.withTransaction {
            food.upsertGoals(data.goals.map { FoodGoalEntity(it.date.toString(), it.kcal, it.carbs, it.fat, it.protein) })

            val knownMeals = importDao.sourceIds(SOURCE_MEAL).toHashSet()
            val newMeals = data.intakes.filter { it.id !in knownMeals }
            food.upsertMeals(newMeals.map(OntConverter::meal))
            importDao.insertAll(newMeals.map { ImportedItemEntity(SOURCE_MEAL, it.id, 0) })
            mealsAdded = newMeals.size

            val knownActivities = importDao.sourceIds(SOURCE_ACTIVITY).toHashSet()
            val marks = ArrayList<ImportedItemEntity>()
            for (activity in data.activities) {
                if (activity.id in knownActivities) continue
                val spec = OntConverter.activity(activity)
                val id = entryDao.insert(
                    EntryEntity(
                        date = spec.date.toString(),
                        startMinute = spec.startMinute,
                        endMinute = spec.endMinute,
                        category = spec.category,
                        title = spec.title,
                        note = spec.note,
                    ),
                )
                marks += ImportedItemEntity(SOURCE_ACTIVITY, activity.id, id)
                activitiesAdded++
            }
            importDao.insertAll(marks)
        }
        return OntResult(fileName, mealsAdded, data.goals.size, activitiesAdded)
    }
}
