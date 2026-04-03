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
        val gravity         = FloatArray(3)
        val geomagnetic     = FloatArray(3)
        val rotationMatrix  = FloatArray(9)
        val remappedMatrix  = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val orientation     = FloatArray(3)

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

                // ── Coordinate-system remap for portrait/upright mode ─────────────────
                // When the device is held upright (AR mode, looking at a scene ahead),
                // gravity aligns with the device Y-axis instead of Z-axis. The default
                // rotation matrix then maps world-Z to device-Y, causing getOrientation()
                // to produce a wrong azimuth. Remapping with AXIS_X / AXIS_Z reorients
                // the matrix so it describes the device in its natural upright frame.
                //
                // Upright detection: if the magnitude of the filtered gravity vector's
                // Y-component exceeds Z-component, the phone is in a portrait-upright
                // posture and the remap is needed.
                //
                // Threshold: |gravity.y| > |gravity.z|  (no hard degree threshold —
                // continuously determined from the actual sensor gravity projection).
                val matrixToUse = if (Math.abs(gravity[1]) > Math.abs(gravity[2])) {
                    // Phone is closer to upright than flat — remap coordinate system.
                    // AXIS_X keeps the device X-axis (horizontal, right).
                    // AXIS_Z maps world Z (gravity) to the new Y-axis, letting
                    // getOrientation() read azimuth correctly for a vertical phone.
                    val remapSuccess = SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remappedMatrix,
                    )
                    if (remapSuccess) remappedMatrix else rotationMatrix
                } else {
                    // Phone is flat — standard mapping works as-is.
                    rotationMatrix
                }

                SensorManager.getOrientation(matrixToUse, orientation)

                // orientation[0] = azimuth  (radians, -π … π)
                // orientation[1] = pitch    (radians)
                // orientation[2] = roll     (radians)
                val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    .let { if (it < 0) it + 360f else it }
                val pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                val rollDeg  = Math.toDegrees(orientation[2].toDouble()).toFloat()

                // isUpright is derived from the RAW gravity vector, not from the
                // remapped orientation matrix. After remapCoordinateSystem(AXIS_X, AXIS_Z)
                // the pitch value in orientation[1] no longer represents the intuitive
                // "how vertical is the phone" angle, so we bypass it entirely and use
                // the gravity projection directly: |gravity.y| > |gravity.z| means the
                // device Y-axis (pointing toward the top of the phone) is more aligned
                // with gravity than the Z-axis (pointing out of the screen), i.e. the
                // phone is closer to upright than flat.
                val isUpright = Math.abs(gravity[1]) > Math.abs(gravity[2])

                trySend(CompassSensorData(azimuthDeg, pitchDeg, rollDeg, isUpright))
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
