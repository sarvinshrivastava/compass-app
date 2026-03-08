package com.pubpeb.mycompass.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Broadcast receiver registered in AndroidManifest.xml for APPWIDGET_UPDATE.
 * Glance handles all widget lifecycle events internally via this receiver.
 */
class CompassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompassWidget()
}
