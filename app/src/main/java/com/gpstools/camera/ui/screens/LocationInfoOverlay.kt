package com.gpstools.camera.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpstools.camera.R
import com.gpstools.camera.location.GpsFix
import com.gpstools.camera.location.LocationUiState
import com.gpstools.camera.location.fetchCurrentLocation
import com.gpstools.camera.location.reverseGeocode
import java.util.Locale

/**
 * Acquires the current location once and reverse-geocodes it (US-005). Emits
 * Locating -> Available(coords, address=null) -> Available(coords, address) so the
 * UI can show "Locating…", then coordinates immediately, then the resolved address.
 * Falls back to Unavailable when no fix can be obtained. The caller is responsible
 * for only composing this once location permission is granted.
 */
@Composable
fun rememberCurrentLocation(): State<LocationUiState> {
    val context = LocalContext.current
    return produceState<LocationUiState>(initialValue = LocationUiState.Locating) {
        val location = fetchCurrentLocation(context)
        if (location == null) {
            value = LocationUiState.Unavailable
            return@produceState
        }
        val fix = GpsFix(location.latitude, location.longitude, location.accuracy)
        value = LocationUiState.Available(fix, address = null, geocoding = true)
        val address = reverseGeocode(context, fix.latitude, fix.longitude)
        value = LocationUiState.Available(fix, address, geocoding = false)
    }
}

/** Semi-transparent panel showing the live location read over the camera preview. */
@Composable
fun LocationInfoOverlay(state: LocationUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is LocationUiState.Locating -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.location_locating),
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }

            is LocationUiState.Unavailable -> {
                Icon(
                    imageVector = Icons.Filled.LocationOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.location_unavailable),
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }

            is LocationUiState.Available -> {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Address line: placeholder while geocoding, the result once
                    // resolved, and omitted entirely if geocoding produced nothing.
                    val addressLine = when {
                        state.geocoding -> stringResource(R.string.location_geocoding)
                        else -> state.address
                    }
                    if (addressLine != null) {
                        Text(
                            text = addressLine,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        text = formatCoordinates(state.fix),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = stringResource(
                            R.string.location_accuracy,
                            state.fix.accuracyMeters.toInt(),
                        ),
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

/** "12.971599, 77.594566" — 6 decimals (~0.1 m), locale-independent. */
private fun formatCoordinates(fix: GpsFix): String =
    String.format(Locale.US, "%.6f, %.6f", fix.latitude, fix.longitude)
