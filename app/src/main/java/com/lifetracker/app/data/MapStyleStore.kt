package com.lifetracker.app.data

import android.content.Context
import java.io.File

/** A style ready to use: its id, name, the address the map loads it from and the provider's limit for downloads. */
class ResolvedStyle(val id: String, val name: String, val url: String) {
    val limit: MapStyles.Limit? get() = MapStyles.limitFor(url)
}

/** Finds the style you chose (built-in or your own) and turns a tile address into a style file on the phone. */
object MapStyleStore {
    private fun folder(context: Context): File = File(context.applicationContext.filesDir, "map-styles").also { it.mkdirs() }

    /** The style for [id], or null when it is gone (then the default is used). */
    fun resolve(context: Context, id: String, own: List<MapStyleEntity>): ResolvedStyle? {
        MapStyles.builtInById(id)?.let { return ResolvedStyle(it.id, it.name, it.url) }
        val mine = own.firstOrNull { it.id == id } ?: return null
        val address = MapStyles.resolve(mine.address, mine.key)
        return when (MapStyles.kind(address)) {
            MapStyles.Kind.STYLE -> ResolvedStyle(mine.id, mine.name, address)
            MapStyles.Kind.TILES -> {
                val file = File(folder(context), mine.id + ".json")
                file.writeText(MapStyles.rasterStyleJson(mine.name, address))
                ResolvedStyle(mine.id, mine.name, "file://" + file.absolutePath)
            }
            MapStyles.Kind.INVALID -> null
        }
    }

    /** The style the settings say, falling back to the default when it is gone. */
    suspend fun current(context: Context, db: AppDatabase): ResolvedStyle {
        val id = MapSettings(context).styleId
        val own = db.mapDao().styles()
        return resolve(context, id, own) ?: resolve(context, MapStyles.DEFAULT_ID, own)!!
    }
}
