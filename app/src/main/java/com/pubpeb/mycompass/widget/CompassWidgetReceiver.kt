package com.pubpeb.mycompass.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Broadcast receiver registered in AndroidManifest.xml for APPWIDGET_UPDATE.
 * Glance handles all widget lifecycle events internally via this receiver.
 *
 * ### Widget first-load fix
 * [onUpdate] fires the instant a widget instance is placed on the home screen —
 * even if the app has never been opened. We kick off a one-shot WorkManager job
 * here so sensor data is fetched and written to DataStore immediately, without
 * waiting for the 15-minute periodic window or for the user to open the app.
 *
 * ### Tap-to-refresh
 * Tapping the widget sends [ACTION_REFRESH] back to this receiver (via Glance's
 * [actionSendBroadcast]). The receiver enqueues a fresh one-shot sensor read —
 * the widget updates in-place within ~3–5 seconds, no app launch required.
 *
 * WorkManager is safe to call from a BroadcastReceiver: it enqueues the request
 * and returns immediately; the actual sensor work runs in a background thread.
 */
class CompassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompassWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Immediately fetch a sensor reading so DataStore has data before Glance
        // renders the widget for the first time.
        CompassWidgetUpdateWorker.enqueueOneShot(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            // ACTION_APPWIDGET_ENABLED fires on first placement on some launchers
            // before the first onUpdate call — belt-and-braces measure.
            AppWidgetManager.ACTION_APPWIDGET_ENABLED -> {
                CompassWidgetUpdateWorker.enqueueOneShot(context)
            }
            // Tap-to-refresh: widget tap fires this action → fresh sensor read →
            // DataStore written → updateAll() → widget shows new heading in ~3–5 s.
            ACTION_REFRESH -> {
                CompassWidgetUpdateWorker.enqueueOneShot(context)
            }
        }
    }

    companion object {
        /** Custom broadcast action sent by the widget tap to trigger a fresh reading. */
        const val ACTION_REFRESH = "com.pubpeb.mycompass.widget.ACTION_REFRESH"
    }
}
