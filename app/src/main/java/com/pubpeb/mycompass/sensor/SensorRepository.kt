package com.pubpeb.mycompass.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps TYPE_ACCELEROMETER + TYPE_MAGNETIC_FIELD into a [Flow] of [CompassSensorData].
 *
 * Uses [SensorManager.getRotationMatrix] + [SensorManager.getOrientation] to derive
 * azimuth, pitch, and roll from fused raw sensor readings.
 *
 * Lifecycle: collect the flow in a coroutine scope tied to the UI; the sensor listener
 * is registered on collection start and unregistered when the flow is cancelled.
 *
 * // TODO Phase 2 – ARCore hook:
 * //   Replace or augment this with ARCore's Frame.getUpdatedTrackables() to get
 * //   camera-based orientation that is independent of sensor hardware quality.
 */
class SensorRepository(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val magnetometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    /** Emits a new [CompassSensorData] every time either raw sensor fires. */
    val sensorData: Flow<CompassSensorData> = callbackFlow {

        // Working buffers — reused across events to minimise allocations.
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        // Low-pass filter alpha: 0 = no smoothing, 1 = frozen.
        // A value of 0.15 smooths jitter while remaining responsive.
        val alpha = 0.15f

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        // Low-pass filter to reduce noise
                        gravity[0] = alpha * event.values[0] + (1 - alpha) * gravity[0]
                        gravity[1] = alpha * event.values[1] + (1 - alpha) * gravity[1]
                        gravity[2] = alpha * event.values[2] + (1 - alpha) * gravity[2]
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic[0] = alpha * event.values[0] + (1 - alpha) * geomagnetic[0]
                        geomagnetic[1] = alpha * event.values[1] + (1 - alpha) * geomagnetic[1]
                        geomagnetic[2] = alpha * event.values[2] + (1 - alpha) * geomagnetic[2]
                    }
                    else -> return
                }

                val success = SensorManager.getRotationMatrix(
                    rotationMatrix,
                    inclinationMatrix,
                    gravity,
                    geomagnetic,
                )
                if (!success) return

                SensorManager.getOrientation(rotationMatrix, orientation)

                // orientation[0] = azimuth  (radians, -π … π)
                // orientation[1] = pitch    (radians)
                // orientation[2] = roll     (radians)
                val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    .let { if (it < 0) it + 360f else it }
                val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()

                trySend(CompassSensorData(azimuthDeg, pitchDeg, rollDeg))
            }
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    /** True if both required sensors are present on this device. */
    val isAvailable: Boolean
        get() = accelerometer != null && magnetometer != null
}
