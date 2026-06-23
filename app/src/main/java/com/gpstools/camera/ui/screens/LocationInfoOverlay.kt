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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpstools.camera.R
import com.gpstools.camera.location.GpsFix
import com.gpstools.camera.location.LocationUiState
import com.gpstools.camera.location.encodePlusCode
import com.gpstools.camera.location.fetchCurrentLocation
import com.gpstools.camera.location.fetchWeather
import com.gpstools.camera.location.reverseGeocode
import com.gpstools.camera.media.StampTemplate
import com.gpstools.camera.ui.theme.accuracyColor
import java.util.Locale

/** Secondary text grey (#9AA0A6) from the v2 spec — coords / muted labels. */
private val TextSecondary = Color(0xFF9AA0A6)

/** Deep navy used (translucent) as the GPS card background. */
private val CardNavy = Color(0xFF0E1B30)

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
        val fix = GpsFix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
        )
        value = LocationUiState.Available(fix, address = null, geocoding = true)
        val address = reverseGeocode(context, fix.latitude, fix.longitude)
        value = LocationUiState.Available(fix, address, geocoding = false)
        // Weather (US-009) is best-effort: fetch after the address resolves and fold it
        // into the state when it lands. A null result (offline) just leaves it off.
        val weather = fetchWeather(fix.latitude, fix.longitude)
        if (weather != null) {
            value = LocationUiState.Available(fix, address, geocoding = false, weather = weather)
        }
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
    template: StampTemplate,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
) {
    // Low-GPS guidance (US-008): when the fix accuracy is poor (> 20 m) the card
    // turns the "Poor" accuracy colour and shows a "move to open sky" hint, so the
    // user knows to relocate for a better fix.
    // The card is a translucent dark-navy so the viewfinder reads through it; white
    // text stays legible thanks to the text shadows. Poor-accuracy tint is a touch
    // more opaque so the warning colour still registers.
    val available = state as? LocationUiState.Available
    val cardColor = if (available != null && available.fix.accuracyMeters > 20f) {
        accuracyColor(available.fix.accuracyMeters).copy(alpha = 0.62f)
    } else {
        CardNavy.copy(alpha = 0.55f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor),
    ) {
        // Template header band — mirrors the burned stamp's style so the on-screen
        // preview updates the moment the user changes the stamp style.
        TemplateHeaderBar(template)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 2. Plus code — computed locally from the fix, so it always shows
                    //    even when reverse-geocoding is down.
                    Text(
                        text = encodePlusCode(state.fix.latitude, state.fix.longitude),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                    )
                    // 2b. Weather (US-009) — temp + condition, once it has loaded.
                    state.weather?.let { weather ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WbSunny,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = weather.describe(LocalContext.current),
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 13.sp,
                            )
                        }
                    }
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
}

/** Map of template → (header label, accent colour) shown on the live preview card. */
private fun templateHeader(template: StampTemplate): Pair<String, Color> = when (template) {
    StampTemplate.CLASSIC -> "CLASSIC" to Color(0xFFF2A93B)
    StampTemplate.MODERN -> "MODERN" to Color(0xFFF2A93B)
    StampTemplate.REPORTING -> "WORK REPORT" to Color(0xFF2EA043)
    StampTemplate.ADVANCE -> "ADVANCE" to Color(0xFFFFC107)
    StampTemplate.CUSTOM -> "CUSTOM" to Color(0xFF2176D2)
}

/** Thin accent header carrying the active template's name (mirrors the burned stamp). */
@Composable
private fun TemplateHeaderBar(template: StampTemplate) {
    // Preview-only template indicator — shown for ALL templates. The strip is
    // intentionally NOT burned onto the saved photo (see PhotoStamp.drawReport).
    val (label, color) = templateHeader(template)
    // Dark text on the light gold/amber accents, white on the darker green/blue.
    val onColor = if (template == StampTemplate.REPORTING || template == StampTemplate.CUSTOM) {
        Color.White
    } else {
        Color(0xFF1A1A1A)
    }
    Text(
        text = label,
        color = onColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
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
