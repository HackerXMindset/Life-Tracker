package com.lifetracker.app.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.lifetracker.app.QuickPhotoActivity
import com.lifetracker.app.R

/** How the home-screen button, the long-press shortcut and the quick-settings tile all start the same thing: the camera. */
object QuickPhoto {
    /** Extra that opens the check page for a photo that was taken without internet. */
    const val EXTRA_QUEUED = "queued"

    fun intent(context: Context): Intent =
        Intent(context, QuickPhotoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    fun pending(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 7, intent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
}

/** The "Snap a meal" button for the home screen. A tap opens the camera at once; the check page comes after the photo is read. */
class CameraWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_camera)
            views.setOnClickPendingIntent(R.id.widget_root, QuickPhoto.pending(context))
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

/** The same button as a tile in the quick-settings panel (pull the notification shade down twice, then edit tiles). */
class CameraTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(QuickPhoto.pending(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(QuickPhoto.intent(this))
        }
    }
}
