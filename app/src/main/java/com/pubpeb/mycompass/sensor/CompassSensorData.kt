package com.pubpeb.mycompass.sensor

/**
 * Immutable snapshot delivered by [SensorRepository] on every sensor update.
 *
 * @param azimuth  Magnetic azimuth in degrees [0, 360).
 * @param pitch    Device pitch in degrees (positive = top tilted toward user).
 * @param roll     Device roll in degrees (positive = left side raised).
 */
data class CompassSensorData(
    val azimuth: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
)
