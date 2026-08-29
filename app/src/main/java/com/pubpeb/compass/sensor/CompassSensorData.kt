package com.pubpeb.compass.sensor

import android.hardware.SensorManager
import androidx.compose.runtime.Immutable

/**
 * How much the magnetometer's current heading can be trusted.
 *
 * Mapped from [SensorManager]'s `SENSOR_STATUS_*` constants, plus [NO_SIGNAL],
 * which the ViewModel synthesises when the sensor flow stops emitting.
 */
enum class CompassAccuracy {
    HIGH,
    MEDIUM,
    LOW,
    UNRELIABLE,
    NO_SIGNAL;

    /** True when the reading is good enough to act on without a caveat. */
    val isTrustworthy: Boolean get() = this == HIGH || this == MEDIUM

    companion object {
        fun fromSensorStatus(status: Int): CompassAccuracy = when (status) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH   -> HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW    -> LOW
            else                                        -> UNRELIABLE
        }
    }
}

/**
 * Immutable snapshot delivered by [SensorRepository] on every sensor update.
 *
 * @param azimuth  Magnetic azimuth in degrees [0, 360). Derived from the
 *                 *remapped* rotation matrix when the device is near-upright,
 *                 so it stays correct in AR postures.
 * @param pitch    Device pitch in degrees, always from the **unremapped**
 *                 matrix so it keeps its intuitive meaning at every tilt.
 * @param roll     Device roll in degrees, likewise unremapped.
 * @param cameraElevationDeg  Elevation of the rear-camera axis above the horizon,
 *                 degrees [-90, +90]. Positive = camera aimed at the sky.
 *                 Derived as `asin(-R[8])` from the unremapped rotation matrix,
 *                 i.e. the world-vertical component of the device -Z axis.
 *                 This exists because `pitch` CANNOT answer the question:
 *                 `getOrientation` returns `asin(-R[7])`, which folds to
 *                 [-90, +90], so camera-up-by-x and camera-down-by-x collapse
 *                 onto the same pitch value. Anything placing a horizon must
 *                 use this field, never `pitch`.
 * @param tiltFromFlatDeg  Angle between the screen normal and vertical, [0, 90].
 *                 0 = lying flat (either face), 90 = held upright. This is the
 *                 raw signal the ViewModel applies AR hysteresis to; the
 *                 repository deliberately makes no mode decision itself.
 * @param accuracy Latest magnetometer accuracy reported by the platform.
 */
@Immutable
data class CompassSensorData(
    val azimuth: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val cameraElevationDeg: Float = 0f,
    val tiltFromFlatDeg: Float = 0f,
    val accuracy: CompassAccuracy = CompassAccuracy.UNRELIABLE,
)
