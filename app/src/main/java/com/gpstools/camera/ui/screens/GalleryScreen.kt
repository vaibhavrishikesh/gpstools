package com.gpstools.camera.ui.screens

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.mutableStateListOf
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
import com.gpstools.camera.billing.Entitlements
import com.gpstools.camera.media.CapturedPhoto
import com.gpstools.camera.media.CustomFieldsStore
import com.gpstools.camera.media.FREE_REPORT_MAX_PHOTOS
import com.gpstools.camera.media.deleteCapturedPhoto
import com.gpstools.camera.media.generatePhotoReport
import com.gpstools.camera.media.openReport
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
 * A selection mode (entered via "Select" or a long-press) lets the user pick photos
 * and "Export PDF report" (US-017): free users get up to [FREE_REPORT_MAX_PHOTOS]
 * watermarked photos, premium users get an unlimited watermark-free report.
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
    var viewing by remember { mutableStateOf<CapturedPhoto?>(null) }

    var selectionMode by remember { mutableStateOf(false) }
    val selectedUris = remember { mutableStateListOf<Uri>() }
    var generating by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            photos = withContext(Dispatchers.IO) { queryCapturedPhotos(context) }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    fun exitSelection() {
        selectionMode = false
        selectedUris.clear()
    }

    fun toggle(photo: CapturedPhoto) {
        if (selectedUris.contains(photo.uri)) selectedUris.remove(photo.uri)
        else selectedUris.add(photo.uri)
    }

    fun exportSelected() {
        val current = photos ?: return
        val chosen = current.filter { it.uri in selectedUris }
        if (chosen.isEmpty()) {
            Toast.makeText(context, R.string.report_none_selected, Toast.LENGTH_SHORT).show()
            return
        }
        generating = true
        scope.launch {
            val projectName = withContext(Dispatchers.IO) {
                CustomFieldsStore(context).load().projectName
            }
            val result = withContext(Dispatchers.IO) {
                // Unlimited / watermark-free reports + batch export are unlocked by
                // EITHER the one-time Premium IAP (US-016) or the Pro subscription
                // (US-018) — see Entitlements.hasUnlimitedReports.
                generatePhotoReport(context, chosen, projectName, Entitlements.hasUnlimitedReports)
            }
            generating = false
            if (result == null) {
                Toast.makeText(context, R.string.report_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            val msg = if (result.limited) {
                context.getString(R.string.report_free_limit, FREE_REPORT_MAX_PHOTOS)
            } else {
                context.getString(R.string.report_saved)
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            val opened = openReport(context, result.uri, context.getString(R.string.report_open_chooser))
            if (!opened) {
                Toast.makeText(context, R.string.report_no_viewer, Toast.LENGTH_SHORT).show()
            }
            exitSelection()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val current = photos
            if (!current.isNullOrEmpty()) {
                GalleryActionBar(
                    selectionMode = selectionMode,
                    selectedCount = selectedUris.size,
                    onStartSelection = { selectionMode = true },
                    onCancelSelection = { exitSelection() },
                    onExport = { exportSelected() },
                )
            }
            Box(Modifier.weight(1f)) {
                when {
                    current == null -> LoadingState()
                    current.isEmpty() -> EmptyGalleryState()
                    else -> PhotoGrid(
                        photos = current,
                        selectionMode = selectionMode,
                        selectedUris = selectedUris,
                        onClick = { photo ->
                            if (selectionMode) toggle(photo) else viewing = photo
                        },
                        onLongClick = { photo ->
                            selectionMode = true
                            if (!selectedUris.contains(photo.uri)) selectedUris.add(photo.uri)
                        },
                    )
                }
            }
            // Non-intrusive banner ad on the free tier (US-015); hides itself when
            // ads are disabled. Sits below the grid so it never overlaps photos.
            BannerAd()
        }

        viewing?.let { photo ->
            PhotoViewer(
                photo = photo,
                onDismiss = { viewing = null },
                onShare = {
                    sharePhoto(context, photo, context.getString(R.string.gallery_share_chooser))
                },
                onDelete = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            deleteCapturedPhoto(context, photo.uri)
                        }
                        if (ok) {
                            viewing = null
                            refresh()
                        }
                    }
                },
            )
        }

        if (generating) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text(
                        text = stringResource(R.string.report_generating),
                        color = Color.White,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryActionBar(
    selectionMode: Boolean,
    selectedCount: Int,
    onStartSelection: () -> Unit,
    onCancelSelection: () -> Unit,
    onExport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            IconButton(onClick = onCancelSelection) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.report_cancel))
            }
            Text(
                text = stringResource(R.string.report_selected_count, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            TextButton(onClick = onExport) {
                Icon(
                    Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
                Text(stringResource(R.string.report_export))
            }
        } else {
            Box(Modifier.weight(1f))
            TextButton(onClick = onStartSelection) {
                Text(stringResource(R.string.report_select))
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    photos: List<CapturedPhoto>,
    selectionMode: Boolean,
    selectedUris: List<Uri>,
    onClick: (CapturedPhoto) -> Unit,
    onLongClick: (CapturedPhoto) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 112.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(photos, key = { it.uri }) { photo ->
            val selected = selectedUris.contains(photo.uri)
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .combinedClickable(
                        onClick = { onClick(photo) },
                        onLongClick = { onLongClick(photo) },
                    ),
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (selectionMode) {
                    if (selected) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f)),
                        )
                    }
                    Icon(
                        imageVector = if (selected) Icons.Filled.CheckCircle
                        else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp),
                    )
                }
            }
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
        // US-004: the dimensions line was redundant clutter in the photo overlay —
        // the stamp already carries the meaningful metadata. Keep date + size only.
        Text(
            text = stringResource(R.string.gallery_meta_size, size),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
        )
    }
}
