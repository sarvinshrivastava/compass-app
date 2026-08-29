package com.pubpeb.compass.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Converts a single location fix into a magnetic declination, and nothing else.
 *
 * Sits beside [SensorRepository] as the app's other hardware boundary: one owns
 * the sensors, this owns the location fix. Pulling it out of the ViewModel keeps
 * a self-contained Play Services interaction — with its own timeout, permission
 * failure mode and cancellation token — from being interleaved with compass
 * state that has nothing to do with it.
 *
 * **Declination is never a precondition.** Every failure path returns null and
 * the compass keeps showing magnetic north; nothing here can block a heading.
 */
class DeclinationRepository(context: Context) {

    private val fusedLocationClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Fetches one fix and derives the local declination in degrees.
     *
     * @return the declination, or null if permission is absent, no fix arrived
     *   within [TIMEOUT_MS], or Play Services failed.
     *
     * Safe to call without the location permission — the resulting
     * [SecurityException] is swallowed, because "no true north" is a normal
     * state for this app rather than an error.
     */
    @SuppressLint("MissingPermission")
    suspend fun fetchDeclination(): Float? {
        val cts = CancellationTokenSource()
        return try {
            // Bounded: getCurrentLocation can hang indefinitely. Left unbounded,
            // the compass sat silently on magnetic north with nothing to
            // distinguish it from an uncalibrated magnetometer — both reach the
            // user as "the heading is wrong".
            val location = withTimeoutOrNull(TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    fusedLocationClient
                        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { cont.resume(it) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                        .addOnCanceledListener { cont.cancel() }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }

            if (location == null) {
                cts.cancel()
                Log.d(TAG, "no fix within timeout, staying on magnetic north")
                return null
            }

            // Only the derived correction leaves this function — never the
            // position it came from.
            GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                System.currentTimeMillis(),
            ).declination
        } catch (_: SecurityException) {
            // Permission absent or revoked — magnetic north remains correct.
            null
        } catch (e: Exception) {
            Log.w(TAG, "declination lookup failed", e)
            null
        } finally {
            cts.cancel()
        }
    }

    private companion object {
        const val TAG = "DeclinationRepo"

        /** Longest we wait for a fix before giving up on true north. */
        const val TIMEOUT_MS = 10_000L
    }
}
