package com.pubpeb.mycompass.widget

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

// ── DataStore singleton (one per app process) ─────────────────────────────────
val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "compass_widget_prefs",
)

object WidgetPrefsKeys {
    val AZIMUTH  = floatPreferencesKey("azimuth")
    val CARDINAL = stringPreferencesKey("cardinal")
}

/**
 * WorkManager [CoroutineWorker] that wakes every 15 minutes, takes a single
 * sensor reading, persists azimuth + cardinal direction to DataStore, and then
 * triggers a Glance widget refresh.
 *
 * ### Sensor registration note
 * Sensor callbacks must be delivered on a thread with a live [Looper].
 * WorkManager worker threads do have a looper, but passing `Handler(Looper.getMainLooper())`
 * is the safest cross-API approach and keeps callback delivery off the worker thread
 * so [CompletableDeferred.await] can suspend cleanly on the worker thread.
 */
class CompassWidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val azimuth = readAzimuth() ?: return Result.retry()

        // Persist to DataStore
        applicationContext.widgetDataStore.edit { prefs ->
            prefs[WidgetPrefsKeys.AZIMUTH]  = azimuth
            prefs[WidgetPrefsKeys.CARDINAL] = cardinalFrom(azimuth)
        }

        // `updateAll` is a suspend extension from androidx.glance.appwidget that
        // finds every placed instance of this widget and recomposes it.
        CompassWidget().updateAll(applicationContext)

        return Result.success()
    }

    /**
     * Registers the accelerometer + magnetometer, waits up to 3 s for the
     * first fused reading, then unregisters. Returns null on timeout.
     */
    private suspend fun readAzimuth(): Float? {
        val sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE)
            as SensorManager
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag   = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (accel == null || mag == null) return null

        val deferred   = CompletableDeferred<Float>()
        val gravity    = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotation   = FloatArray(9)
        val inclination = FloatArray(9)
        val orientation = FloatArray(3)
        val alpha = 0.15f
        var hasGravity = false
        var hasMag     = false

        val handler = Handler(Looper.getMainLooper())

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity[0] = alpha * event.values[0] + (1 - alpha) * gravity[0]
                        gravity[1] = alpha * event.values[1] + (1 - alpha) * gravity[1]
                        gravity[2] = alpha * event.values[2] + (1 - alpha) * gravity[2]
                        hasGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic[0] = alpha * event.values[0] + (1 - alpha) * geomagnetic[0]
                        geomagnetic[1] = alpha * event.values[1] + (1 - alpha) * geomagnetic[1]
                        geomagnetic[2] = alpha * event.values[2] + (1 - alpha) * geomagnetic[2]
                        hasMag = true
                    }
                }
                if (!hasGravity || !hasMag) return
                val ok = SensorManager.getRotationMatrix(rotation, inclination, gravity, geomagnetic)
                if (!ok) return
                SensorManager.getOrientation(rotation, orientation)
                val azDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    .let { if (it < 0f) it + 360f else it }
                deferred.complete(azDeg)
            }
        }

        sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL, handler)
        sensorManager.registerListener(listener, mag,   SensorManager.SENSOR_DELAY_NORMAL, handler)

        return try {
            withTimeout(3_000L) { deferred.await() }
        } catch (_: Exception) {
            null
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }

    companion object {
        private const val WORK_NAME = "compass_widget_update"

        /**
         * Enqueues a periodic 15-minute update. Safe to call on every app launch —
         * [ExistingPeriodicWorkPolicy.KEEP] leaves an already-running chain untouched.
         */
        fun enqueuePeriodicWork(context: Context) {
            val request = PeriodicWorkRequestBuilder<CompassWidgetUpdateWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build(),
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

internal fun cardinalFrom(azimuth: Float): String = when {
    azimuth < 22.5f  || azimuth >= 337.5f -> "N"
    azimuth < 67.5f  -> "NE"
    azimuth < 112.5f -> "E"
    azimuth < 157.5f -> "SE"
    azimuth < 202.5f -> "S"
    azimuth < 247.5f -> "SW"
    azimuth < 292.5f -> "W"
    else             -> "NW"
}
