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

/** A single GPS fix. [accuracyMeters] is the 68% confidence radius from the OS. */
data class GpsFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
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

/** Awaits a Play Services [Task], resolving to null on failure instead of throwing. */
private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (!cont.isActive) return@addOnCompleteListener
        cont.resume(if (task.isSuccessful) task.result else null)
    }
}
