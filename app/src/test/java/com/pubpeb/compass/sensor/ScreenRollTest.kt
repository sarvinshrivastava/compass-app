package com.pubpeb.compass.sensor

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `screenRoll` is the upright level's entire reading, and it is derived
 * straight from gravity rather than from `getOrientation`'s roll — precisely
 * because that Euler angle degenerates at the ±90° pitch this posture sits at.
 *
 * These tests build the gravity vector for a known rotation and check the
 * function recovers it, including at the pitch where the platform's own roll
 * stops meaning anything.
 */
class ScreenRollTest {

    private val g = 9.81f
    private val tolerance = 0.01f

    /**
     * Accelerometer reading for a device held upright and rolled [deg]
     * clockwise.
     *
     * Built from the **real** Android convention, which is the trap this
     * function fell into once already: a stationary accelerometer reports
     * +9.81 along the axis pointing *up*, so plumb portrait is `(0, +9.81, 0)`
     * and not `(0, -9.81, 0)`. Getting that backwards puts every reading half a
     * turn out, and `foldToQuarterTurn` then hides it perfectly — so these
     * vectors must stay in the true convention, and the assertions below must
     * stay on the *unfolded* output.
     */
    private fun uprightGravity(deg: Float): FloatArray {
        val rad = Math.toRadians(deg.toDouble())
        return floatArrayOf(
            // NEGATIVE x for a clockwise roll. Rotating the device clockwise
            // swings device +X below world-horizontal, so world-up acquires a
            // negative x component in device coordinates.
            //
            // This helper previously had `+g*sin`, which is the vector for a
            // COUNTER-clockwise roll. Built that way it agreed with a formula
            // that had the same sign error, and the pair passed together — a
            // circular check that proves only self-consistency. Derive from
            // "the accelerometer reads +G along the axis pointing up", never
            // from whatever the implementation happens to do.
            (-g * sin(rad)).toFloat(),
            (g * cos(rad)).toFloat(),
            0f,
        )
    }

    @Test
    fun `flat face-up is the documented reading`() {
        // Sanity-pins the convention the rest of the file depends on.
        val faceUp = floatArrayOf(0f, 0f, g)
        assertEquals(0f, faceUp[0], tolerance)
        assertEquals(g, faceUp[2], tolerance)
    }

    @Test
    fun `plumb portrait reads zero`() {
        assertEquals(0f, SensorRepository.screenRoll(uprightGravity(0f)), tolerance)
    }

    @Test
    fun `clockwise roll is positive`() {
        assertEquals(10f, SensorRepository.screenRoll(uprightGravity(10f)), tolerance)
        assertEquals(45f, SensorRepository.screenRoll(uprightGravity(45f)), tolerance)
    }

    @Test
    fun `counter-clockwise roll is negative`() {
        assertEquals(-10f, SensorRepository.screenRoll(uprightGravity(-10f)), tolerance)
    }

    @Test
    fun `quarter turns read as quarter turns`() {
        assertEquals(90f, SensorRepository.screenRoll(uprightGravity(90f)), tolerance)
        assertEquals(-90f, SensorRepository.screenRoll(uprightGravity(-90f)), tolerance)
    }

    @Test
    fun `upside down reads plus or minus 180`() {
        val result = SensorRepository.screenRoll(uprightGravity(180f))
        assertEquals(180f, kotlin.math.abs(result), tolerance)
    }

    /**
     * The whole point. A device face-up has gravity almost entirely on Z, so
     * the X and Y components this reads are near zero and the angle is noise —
     * which is why [Constants.Level.UPRIGHT_ENTER_DEG] gates its use. The test
     * pins the documented behaviour rather than pretending the value is
     * meaningful here.
     */
    @Test
    fun `flat on a table returns a value but it is not meaningful`() {
        val flat = floatArrayOf(0.02f, -0.03f, g)
        val result = SensorRepository.screenRoll(flat)
        // Finite and in range — it must not blow up or return NaN, because the
        // filter downstream keeps running through the flat posture.
        assert(result.isFinite())
        assert(result in -180f..180f)
    }

    @Test
    fun `a tiny roll survives the tolerance the plumb readout needs`() {
        // The upright level calls level at 0.4°, so the derivation must resolve
        // well below that to be worth showing.
        assertEquals(0.2f, SensorRepository.screenRoll(uprightGravity(0.2f)), 0.005f)
    }
}
