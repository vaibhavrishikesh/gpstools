package com.gpstools.camera.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.gpstools.camera.location.encodePlusCode
import com.gpstools.camera.location.fetchCurrentLocation
import com.gpstools.camera.location.reverseGeocode
import com.gpstools.camera.ui.theme.accuracyColor
import java.util.Locale

/** Secondary text grey (#9AA0A6) from the v2 spec — coords / muted labels. */
private val TextSecondary = Color(0xFF9AA0A6)

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

/**
 * Semi-transparent panel showing the live location read over the camera preview.
 *
 * When [onEditClick] is supplied it renders an "Edit" affordance (icon + text) on
 * the card itself (US-003) that opens the optional stamp-details bottom sheet —
 * replacing the old free-floating edit button. The affordance is always available
 * regardless of location state so the user can set project/site/note/logo even
 * before a fix arrives; it never blocks capture.
 */
@Composable
fun LocationInfoOverlay(
    state: LocationUiState,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
) {
    // Low-GPS guidance (US-008): when the fix accuracy is poor (> 20 m) the card
    // turns the "Poor" accuracy colour and shows a "move to open sky" hint, so the
    // user knows to relocate for a better fix.
    val available = state as? LocationUiState.Available
    val cardColor = if (available != null && available.fix.accuracyMeters > 20f) {
        accuracyColor(available.fix.accuracyMeters).copy(alpha = 0.9f)
    } else {
        Color.Black.copy(alpha = 0.9f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.weight(1f),
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 1. Address — bold 16sp white. Placeholder while geocoding,
                    //    the result once resolved, omitted if geocoding found nothing.
                    val addressLine = when {
                        state.geocoding -> stringResource(R.string.location_geocoding)
                        else -> state.address
                    }
                    if (addressLine != null) {
                        Text(
                            text = addressLine,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    // 2. Plus code — 14sp 90% white. Computed locally from the fix,
                    //    so it always shows even when reverse-geocoding is down.
                    Text(
                        text = encodePlusCode(state.fix.latitude, state.fix.longitude),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                    )
                    // 3. Coords (12sp grey) + colour-coded accuracy chip.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = formatCoordinates(state.fix),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        AccuracyChip(state.fix.accuracyMeters)
                    }
                    // 4. Low-GPS hint (US-008) — only when accuracy is poor (> 20 m):
                    //    nudge the user to relocate for a better fix.
                    if (state.fix.accuracyMeters > 20f) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.gps_low_accuracy_hint),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
        }

        if (onEditClick != null) {
            EditAffordance(onClick = onEditClick)
        }
    }
}

/**
 * Compact "Edit" affordance (pencil icon + label) sitting on the GPS card (US-003).
 * Tapping it opens the optional stamp-details bottom sheet.
 */
@Composable
private fun EditAffordance(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.custom_fields_edit),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Colour-coded accuracy chip (US-004): green "Good" <10m / amber "Avg" 10–20m /
 * red "Poor" >20m, rendered as "±Nm · Label" on a filled pill.
 */
@Composable
private fun AccuracyChip(accuracyMeters: Float) {
    val labelRes = when {
        accuracyMeters < 10f -> R.string.accuracy_good
        accuracyMeters <= 20f -> R.string.accuracy_avg
        else -> R.string.accuracy_poor
    }
    Text(
        text = stringResource(
            R.string.accuracy_chip,
            accuracyMeters.toInt(),
            stringResource(labelRes),
        ),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accuracyColor(accuracyMeters))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** "12.971599, 77.594566" — 6 decimals (~0.1 m), locale-independent. */
private fun formatCoordinates(fix: GpsFix): String =
    String.format(Locale.US, "%.6f, %.6f", fix.latitude, fix.longitude)
