package com.pubpeb.compass.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest-registered receiver for APPWIDGET_UPDATE. Glance drives the entire
 * widget lifecycle through it.
 *
 * There is deliberately nothing else here. [CompassWidget] renders no live
 * data, so there is no reading to refresh, no custom broadcast to answer, and
 * no background job to enqueue on placement.
 */
class CompassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompassWidget()
}
