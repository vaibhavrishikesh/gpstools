package com.gpstools.camera.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "LocationProvider"

/** How long to wait for a high-accuracy fix before falling back to last-known. */
const val LOCATION_TIMEOUT_MS = 12_000L

/**
 * A single GPS fix. [accuracyMeters] is the 68% confidence radius from the OS.
 * [altitudeMeters] is the WGS84 altitude in metres when the fix reports one (P2-US-012),
 * else null (e.g. a 2D fix or a last-known location with no altitude).
 */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val altitudeMeters: Double? = null,
)

/**
 * UI state for the location read (US-005). Coordinates render as soon as a fix is
 * available; [Available.geocoding] is true while the reverse-geocode is still in
 * flight (so the UI shows "Finding address…") and false once it resolves — at
 * which point [Available.address] holds the result, or null if geocoding failed.
 */
sealed interface LocationUiState {
    data object Locating : LocationUiState
    data class Available(
        val fix: GpsFix,
        val address: String?,
        val geocoding: Boolean,
        /** Current weather for the fix (US-009); null while loading or if unavailable. */
        val weather: Weather? = null,
    ) : LocationUiState
    data object Unavailable : LocationUiState
}

/**
 * Gets the current location with high accuracy and a timeout, falling back to the
 * last-known location if the fresh request doesn't complete in time. Returns null
 * when no location can be obtained. Caller must hold a location permission.
 */
@SuppressLint("MissingPermission")
suspend fun fetchCurrentLocation(
    context: Context,
    timeoutMs: Long = LOCATION_TIMEOUT_MS,
): Location? {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cancellation = CancellationTokenSource()
    return try {
        val fresh = withTimeoutOrNull(timeoutMs) {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
        }
        fresh ?: client.lastLocation.await()
    } catch (e: Exception) {
        Log.e(TAG, "Failed to obtain location", e)
        null
    } finally {
        cancellation.cancel()
    }
}

/**
 * Reverse-geocodes [latitude]/[longitude] to a human-readable address. Uses the
 * async Geocoder API on Android 13+ and the (deprecated) blocking API off the main
 * thread on older versions. Returns null if geocoding is unavailable or fails.
 */
suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    val geocoder = Geocoder(context, Locale.getDefault())
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                // Must supply BOTH callbacks: if only onGeocode is registered and
                // the backend errors (common on emulators), onError fires and the
                // continuation would hang forever at "Finding address…".
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull()?.let(::formatAddress))
                        }

                        override fun onError(errorMessage: String?) {
                            Log.w(TAG, "Geocoder error: $errorMessage")
                            if (cont.isActive) cont.resume(null)
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()?.let(::formatAddress)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Reverse geocode failed", e)
        null
    }
}

/** Builds a concise address line, preferring the OS-formatted line. */
private fun formatAddress(address: Address): String {
    val line = address.getAddressLine(0)
    if (!line.isNullOrBlank()) return line
    return listOfNotNull(
        address.subLocality,
        address.locality,
        address.adminArea,
        address.countryName,
    ).distinct().joinToString(", ")
}

/**
 * Encodes [latitude]/[longitude] as a standard 10-digit Open Location Code
 * ("plus code", e.g. "7JWVQX2C+9F"). Self-contained — no dependency. ~14 m
 * precision, which is plenty for the GPS card's secondary line (US-004).
 */
fun encodePlusCode(latitude: Double, longitude: Double): String {
    val alphabet = "23456789CFGHJMPQRVWX"
    val lat = latitude.coerceIn(-90.0, 89.999999)
    // Normalize longitude into [-180, 180).
    val lng = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    var latLow = -90.0
    var latHigh = 90.0
    var lngLow = -180.0
    var lngHigh = 180.0
    val sb = StringBuilder()
    repeat(5) {
        val latStep = (latHigh - latLow) / 20.0
        val latDigit = ((lat - latLow) / latStep).toInt().coerceIn(0, 19)
        latLow += latDigit * latStep
        latHigh = latLow + latStep
        sb.append(alphabet[latDigit])

        val lngStep = (lngHigh - lngLow) / 20.0
        val lngDigit = ((lng - lngLow) / lngStep).toInt().coerceIn(0, 19)
        lngLow += lngDigit * lngStep
        lngHigh = lngLow + lngStep
        sb.append(alphabet[lngDigit])
    }
    sb.insert(8, '+')
    return sb.toString()
}

/** Awaits a Play Services [Task], resolving to null on failure instead of throwing. */
private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (!cont.isActive) return@addOnCompleteListener
        cont.resume(if (task.isSuccessful) task.result else null)
    }
}
