package com.pubpeb.compass.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.pubpeb.compass.Constants
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.sqrt

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
        val gravity           = FloatArray(3)
        val geomagnetic       = FloatArray(3)
        val rotationMatrix    = FloatArray(9)
        val remappedMatrix    = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val orientation       = FloatArray(3)
        val flatOrientation   = FloatArray(3)

        // Magnetometer accuracy, updated by onAccuracyChanged. Starts UNRELIABLE so
        // the UI never claims confidence the platform has not yet granted.
        var accuracy = CompassAccuracy.UNRELIABLE

        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, newAccuracy: Int) {
                // Only the magnetometer's accuracy tells us anything about heading
                // trust; the accelerometer's is effectively always high.
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    accuracy = CompassAccuracy.fromSensorStatus(newAccuracy)
                }
            }

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    // One loop per axis rather than three near-identical lines:
                    // the reader no longer has to diff the statements to confirm
                    // the same formula really is applied to each axis.
                    Sensor.TYPE_ACCELEROMETER -> lowPass(gravity, event.values)
                    Sensor.TYPE_MAGNETIC_FIELD -> lowPass(geomagnetic, event.values)
                    else -> return
                }

                val success = SensorManager.getRotationMatrix(
                    rotationMatrix,
                    inclinationMatrix,
                    gravity,
                    geomagnetic,
                )
                if (!success) return

                // ── Pitch / roll come from the UNREMAPPED matrix, always ──────────
                // The remap below is correct for azimuth but redefines what
                // orientation[1] and orientation[2] mean. Reading tilt from the
                // remapped matrix made the level indicator jump ~90° the instant the
                // device crossed the remap threshold. Deriving it here, before any
                // remap, keeps pitch and roll continuous through every posture.
                SensorManager.getOrientation(rotationMatrix, flatOrientation)
                val pitchDeg = Math.toDegrees(flatOrientation[1].toDouble()).toFloat()
                val rollDeg  = Math.toDegrees(flatOrientation[2].toDouble()).toFloat()

                // ── Coordinate-system remap for portrait/upright mode ─────────────
                // When the device is held upright (looking at a scene ahead), gravity
                // aligns with the device Y-axis instead of Z-axis. The default rotation
                // matrix then maps world-Z to device-Y, causing getOrientation() to
                // produce a wrong azimuth. Remapping with AXIS_X / AXIS_Z reorients the
                // matrix so it describes the device in its natural upright frame.
                val matrixToUse = if (abs(gravity[1]) > abs(gravity[2])) {
                    val remapSuccess = SensorManager.remapCoordinateSystem(
                        rotationMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        remappedMatrix,
                    )
                    if (remapSuccess) remappedMatrix else rotationMatrix
                } else {
                    rotationMatrix
                }

                SensorManager.getOrientation(matrixToUse, orientation)

                val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    .let { if (it < 0) it + 360f else it }

                // ── Camera elevation, unambiguously signed ───────────────────────
                // pitch cannot answer "is the camera above or below the horizon":
                // getOrientation gives asin(-R[7]), which folds to [-90, +90], so
                // camera-up-by-x and camera-down-by-x collapse onto one value.
                // The rotation matrix maps device -> world, so the world-vertical
                // component of the device -Z axis (the rear-camera axis) is
                // -R[8]; its arcsine is a true signed elevation. Sanity check:
                // lying flat face-up, device +Z is world-up, R[8] = 1, elevation
                // = -90° — the rear camera is aimed straight at the ground.
                val cameraElevationDeg = Math.toDegrees(
                    asin((-rotationMatrix[8]).coerceIn(-1f, 1f).toDouble())
                ).toFloat()

                trySend(
                    CompassSensorData(
                        azimuth            = azimuthDeg,
                        pitch              = pitchDeg,
                        roll               = rollDeg,
                        cameraElevationDeg = cameraElevationDeg,
                        tiltFromFlatDeg    = tiltFromFlat(gravity),
                        accuracy           = accuracy,
                    )
                )
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

    companion object {

        /**
         * Applies the low-pass filter in place, one axis at a time.
         *
         * Smooths jitter without lagging the reading; the same alpha is used for
         * both sensors so their noise profiles stay comparable.
         */
        private fun lowPass(target: FloatArray, raw: FloatArray) {
            for (i in 0..2) {
                target[i] = Constants.Sensor.LPF_ALPHA * raw[i] +
                    (1 - Constants.Sensor.LPF_ALPHA) * target[i]
            }
        }

        /**
         * Angle between the screen normal and vertical, in degrees [0, 90].
         *
         * `acos(|g.z| / |g|)` — 0° when the device lies flat on either face, 90° when
         * it is held upright. Taking the absolute Z component folds face-up and
         * face-down onto the same value, which is what "how upright is this phone"
         * should mean. Unlike the old `|g.y| > |g.z|` test this is a continuous
         * magnitude rather than a boolean at a fixed 45°, so the ViewModel can apply
         * a proper hysteresis band to it.
         */
        internal fun tiltFromFlat(gravity: FloatArray): Float {
            val magnitude = sqrt(
                gravity[0] * gravity[0] +
                gravity[1] * gravity[1] +
                gravity[2] * gravity[2]
            )
            if (magnitude < Constants.Sensor.MIN_GRAVITY_MAGNITUDE) return 0f
            val ratio = (abs(gravity[2]) / magnitude).coerceIn(0f, 1f)
            return Math.toDegrees(acos(ratio).toDouble()).toFloat()
        }
    }
}
