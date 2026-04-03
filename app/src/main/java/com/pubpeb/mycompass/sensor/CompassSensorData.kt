package com.pubpeb.mycompass.sensor

/**
 * Immutable snapshot delivered by [SensorRepository] on every sensor update.
 *
 * @param azimuth   Magnetic azimuth in degrees [0, 360).
 * @param pitch     Device pitch in degrees (positive = top tilted toward user).
 * @param roll      Device roll in degrees (positive = left side raised).
 * @param isUpright True when the phone is held roughly upright (portrait, looking at a scene).
 *                  Computed from raw gravity: |gravity.y| > |gravity.z|.
 *                  This is the canonical upright flag — it is NOT derived from the remapped
 *                  orientation matrix, so it remains reliable after remapCoordinateSystem().
 */
data class CompassSensorData(
    val azimuth: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val isUpright: Boolean = false,
)
