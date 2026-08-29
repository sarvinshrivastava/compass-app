package com.pubpeb.compass.sensor

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping that gates the confidence line and `onHeadingBecameStable`.
 * `SENSOR_STATUS_*` are plain int constants, so this runs on the JVM with no
 * framework — unlike most of `SensorManager`, which is not mocked.
 */
class CompassAccuracyTest {

    @Test
    fun `platform statuses map to their own levels`() {
        assertEquals(CompassAccuracy.HIGH,
            CompassAccuracy.fromSensorStatus(SensorManager.SENSOR_STATUS_ACCURACY_HIGH))
        assertEquals(CompassAccuracy.MEDIUM,
            CompassAccuracy.fromSensorStatus(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM))
        assertEquals(CompassAccuracy.LOW,
            CompassAccuracy.fromSensorStatus(SensorManager.SENSOR_STATUS_ACCURACY_LOW))
        assertEquals(CompassAccuracy.UNRELIABLE,
            CompassAccuracy.fromSensorStatus(SensorManager.SENSOR_STATUS_UNRELIABLE))
    }

    /** The fail-safe branch: anything unrecognised must not read as trustworthy. */
    @Test
    fun `unknown statuses fall back to unreliable`() {
        assertEquals(CompassAccuracy.UNRELIABLE, CompassAccuracy.fromSensorStatus(-1))
        assertEquals(CompassAccuracy.UNRELIABLE, CompassAccuracy.fromSensorStatus(99))
    }

    @Test
    fun `only high and medium are trustworthy`() {
        assertTrue(CompassAccuracy.HIGH.isTrustworthy)
        assertTrue(CompassAccuracy.MEDIUM.isTrustworthy)
        assertFalse(CompassAccuracy.LOW.isTrustworthy)
        assertFalse(CompassAccuracy.UNRELIABLE.isTrustworthy)
        assertFalse(CompassAccuracy.NO_SIGNAL.isTrustworthy)
    }
}
