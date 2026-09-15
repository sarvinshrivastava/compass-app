package com.pubpeb.compass.sensor

import com.pubpeb.compass.Constants

/** Which instrument the level screen should be showing. */
enum class LevelPosture {
    /** Device lying on a surface — two axes matter, so: bullseye. */
    FLAT,

    /** Device held up against something — one axis matters, so: single-axis bar. */
    UPRIGHT,
}

/**
 * Chooses the level's instrument from the device's tilt, with hysteresis.
 *
 * Same shape and the same reason as [ArTriggerGate], minus the dwell: a bare
 * threshold anywhere in this app oscillates on sensor noise for any hand held
 * near it, and swapping the whole instrument under the user mid-reading is a
 * worse outcome than swapping a camera view. Entry needs
 * [Constants.Level.UPRIGHT_ENTER_DEG]; the posture then survives back down to
 * [Constants.Level.UPRIGHT_EXIT_DEG].
 *
 * No dwell, unlike the AR gate. AR entry replaces the screen and opens a
 * camera, so it should be reluctant; this only changes which of two drawings of
 * the same sensor data is on screen, and a delay there reads as lag rather than
 * as deliberation.
 *
 * Pure and framework-free, so it unit-tests without Android — see
 * `LevelPostureGateTest`.
 */
class LevelPostureGate(
    private val enterDeg: Float = Constants.Level.UPRIGHT_ENTER_DEG,
    private val exitDeg: Float = Constants.Level.UPRIGHT_EXIT_DEG,
) {
    var posture: LevelPosture = LevelPosture.FLAT
        private set

    /**
     * Feeds one tilt reading in and returns the resulting posture.
     *
     * @param tiltDeg angle from flat, [0, 90] — see [SensorRepository.tiltFromFlat].
     */
    fun update(tiltDeg: Float): LevelPosture {
        posture = when (posture) {
            LevelPosture.FLAT ->
                if (tiltDeg > enterDeg) LevelPosture.UPRIGHT else LevelPosture.FLAT

            LevelPosture.UPRIGHT ->
                if (tiltDeg < exitDeg) LevelPosture.FLAT else LevelPosture.UPRIGHT
        }
        return posture
    }
}
