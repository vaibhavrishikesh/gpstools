package com.gpstools.camera.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.gpstools.camera.R
import com.gpstools.camera.media.CapturedPhoto
import com.gpstools.camera.media.GeotagStore
import com.gpstools.camera.media.queryCapturedPhotos
import com.gpstools.camera.ui.navigation.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.DateFormat
import java.util.Date

/** A captured photo with the coordinates it was taken at (joined from [GeotagStore]). */
private data class GeotaggedPhoto(
    val photo: CapturedPhoto,
    val latitude: Double,
    val longitude: Double,
)

// Centre of India — the default view before any pins are framed.
private val INDIA_CENTER = GeoPoint(22.5937, 78.9629)
private const val INDIA_ZOOM = 4.0

/**
 * Map tab (US-012): plots this app's geotagged captures as pins on an OpenStreetMap
 * map (osmdroid — no API key, consistent with the static-map stamp in US-008).
 * Tapping a pin previews that photo. When nothing is geotagged yet, the map still
 * renders (centred on India) under an empty-state card.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = still loading; emptyList = loaded but nothing geotagged yet.
    var pins by remember { mutableStateOf<List<GeotaggedPhoto>?>(null) }
    var selected by remember { mutableStateOf<GeotaggedPhoto?>(null) }

    LaunchedEffect(Unit) {
        pins = withContext(Dispatchers.IO) { loadGeotaggedPhotos(context) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = pins) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> {
                OsmMap(pins = current, onPinClick = { selected = it })
                if (current.isEmpty()) EmptyMapOverlay()
            }
        }

        selected?.let { pin ->
            MapPhotoPreview(pin = pin, onDismiss = { selected = null })
        }
    }
}

/** Joins the captured photos with their recorded coordinates, pruning stale geotags. */
private fun loadGeotaggedPhotos(context: Context): List<GeotaggedPhoto> {
    val photosByName = queryCapturedPhotos(context).associateBy { it.displayName }
    GeotagStore.retainOnly(context, photosByName.keys)
    return GeotagStore.loadAll(context).mapNotNull { (name, tag) ->
        val photo = photosByName[name] ?: return@mapNotNull null
        GeotaggedPhoto(photo, tag.latitude, tag.longitude)
    }
}

@Composable
private fun OsmMap(
    pins: List<GeotaggedPhoto>,
    onPinClick: (GeotaggedPhoto) -> Unit,
) {
    val context = LocalContext.current
    val onPinClickState = rememberUpdatedState(onPinClick)

    // osmdroid must be configured (user-agent is required by the tile policy) before
    // a MapView is inflated. Keep its cache inside app storage so no storage
    // permission is needed on any API level.
    val mapView = remember {
        Configuration.getInstance().apply {
            // OSM's tile-usage policy blocks generic/default user agents; send a
            // descriptive, app-identifying value so real devices aren't 403'd.
            userAgentValue = "GpsCameraLocation/1.0 (${context.packageName})"
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(INDIA_ZOOM)
            controller.setCenter(INDIA_CENTER)
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        // setCenter/setZoom in the builder above run before the MapView is laid out,
        // so osmdroid ignores them and opens on the world (0,0) view. Re-apply once
        // the view has a size so it actually opens on India.
        mapView.post {
            mapView.controller.setZoom(INDIA_ZOOM)
            mapView.controller.setCenter(INDIA_CENTER)
        }
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Frame the pins once, the first time a non-empty set is laid out, so we don't
    // fight the user's panning/zooming on later recompositions.
    var framed by remember { mutableStateOf(false) }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            map.overlays.clear()
            pins.forEach { pin ->
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(pin.latitude, pin.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = pin.photo.displayName
                        setOnMarkerClickListener { _, _ ->
                            onPinClickState.value(pin)
                            true
                        }
                    },
                )
            }
            map.invalidate()

            if (pins.isNotEmpty() && !framed) {
                framed = true
                map.post {
                    if (pins.size == 1) {
                        map.controller.setZoom(16.0)
                        map.controller.setCenter(GeoPoint(pins[0].latitude, pins[0].longitude))
                    } else {
                        val box = BoundingBox.fromGeoPoints(
                            pins.map { GeoPoint(it.latitude, it.longitude) },
                        )
                        map.zoomToBoundingBox(box.increaseByScale(1.5f), false, 80)
                    }
                }
            }
        },
    )
}

@Composable
private fun EmptyMapOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Destination.Map.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = stringResource(R.string.map_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.map_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Full-screen preview of the photo behind a tapped pin. Mirrors the gallery viewer
 * (an in-place overlay, not a Dialog, so the edge-to-edge insets are respected).
 */
@Composable
private fun MapPhotoPreview(pin: GeotaggedPhoto, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.map_preview_close),
                    tint = Color.White,
                )
            }
        }

        AsyncImage(
            model = pin.photo.uri,
            contentDescription = pin.photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        val date = remember(pin.photo.dateAddedMillis) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(pin.photo.dateAddedMillis))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = pin.photo.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.map_meta_coords, pin.latitude, pin.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.gallery_meta_date, date),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }
}
