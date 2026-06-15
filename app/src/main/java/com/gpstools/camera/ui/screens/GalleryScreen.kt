package com.gpstools.camera.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gpstools.camera.R
import com.gpstools.camera.ads.BannerAd
import com.gpstools.camera.media.CapturedPhoto
import com.gpstools.camera.media.deleteCapturedPhoto
import com.gpstools.camera.media.queryCapturedPhotos
import com.gpstools.camera.media.sharePhoto
import com.gpstools.camera.ui.navigation.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Gallery tab: a grid of the photos captured by this app (newest first). Tapping a
 * tile opens a full-screen viewer with the file metadata plus share and delete
 * actions. The list reloads whenever the tab is entered (the composable re-enters
 * composition) and after a delete.
 *
 * The viewer is an in-place overlay rather than a Dialog: an edge-to-edge activity
 * doesn't reliably dispatch window insets to dialog windows, so a dialog's bottom
 * content gets clipped under the system bars. The overlay lives inside the Scaffold
 * content area, which already accounts for the bars.
 */
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // null = still loading; emptyList = loaded but no captures yet.
    var photos by remember { mutableStateOf<List<CapturedPhoto>?>(null) }
    var selected by remember { mutableStateOf<CapturedPhoto?>(null) }

    fun refresh() {
        scope.launch {
            photos = withContext(Dispatchers.IO) { queryCapturedPhotos(context) }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val current = photos
            Box(Modifier.weight(1f)) {
                when {
                    current == null -> LoadingState()
                    current.isEmpty() -> EmptyGalleryState()
                    else -> PhotoGrid(photos = current, onClick = { selected = it })
                }
            }
            // Non-intrusive banner ad on the free tier (US-015); hides itself when
            // ads are disabled. Sits below the grid so it never overlaps photos.
            BannerAd()
        }

        selected?.let { photo ->
            PhotoViewer(
                photo = photo,
                onDismiss = { selected = null },
                onShare = {
                    sharePhoto(context, photo, context.getString(R.string.gallery_share_chooser))
                },
                onDelete = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            deleteCapturedPhoto(context, photo.uri)
                        }
                        if (ok) {
                            selected = null
                            refresh()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyGalleryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Destination.Gallery.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = stringResource(R.string.gallery_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.gallery_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PhotoGrid(
    photos: List<CapturedPhoto>,
    onClick: (CapturedPhoto) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(photos, key = { it.uri }) { photo ->
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onClick(photo) },
            )
        }
    }
}

@Composable
private fun PhotoViewer(
    photo: CapturedPhoto,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Top bar: back, share, delete.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.gallery_back),
                    tint = Color.White,
                )
            }
            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.gallery_share),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.gallery_delete),
                        tint = Color.White,
                    )
                }
            }
        }

        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        PhotoMetadata(
            photo = photo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.gallery_delete_confirm_title)) },
            text = { Text(stringResource(R.string.gallery_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.gallery_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.gallery_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun PhotoMetadata(photo: CapturedPhoto, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val date = remember(photo.dateAddedMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(photo.dateAddedMillis))
    }
    val size = remember(photo.sizeBytes) { Formatter.formatShortFileSize(context, photo.sizeBytes) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = photo.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        Text(
            text = stringResource(R.string.gallery_meta_date, date),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
        if (photo.width > 0 && photo.height > 0) {
            Text(
                text = stringResource(R.string.gallery_meta_dimensions, photo.width, photo.height),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
        Text(
            text = stringResource(R.string.gallery_meta_size, size),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}
