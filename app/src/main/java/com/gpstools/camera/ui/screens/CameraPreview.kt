package com.gpstools.camera.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gpstools.camera.R
import com.gpstools.camera.location.LocationUiState
import com.gpstools.camera.media.CustomFields
import com.gpstools.camera.media.CustomFieldsStore
import com.gpstools.camera.media.StampData
import com.gpstools.camera.media.StampTemplate
import com.gpstools.camera.media.StampTemplateStore
import com.gpstools.camera.media.capturePhoto
import com.gpstools.camera.media.label
import com.gpstools.camera.settings.AppSettingsStore
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "CameraPreview"

/**
 * Live CameraX preview (US-004). Fills the screen, supports a front/back lens
 * toggle, and rebinds the use cases whenever the lens changes or the screen
 * leaves/re-enters composition. The shutter button is present but capture is
 * wired up in US-006. Lifecycle is handled by binding to the current
 * LifecycleOwner so pause/resume releases and reacquires the camera cleanly.
 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Survives recomposition and config changes so the chosen lens sticks.
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    // Which physical lenses this device actually has — only offer the toggle
    // when both exist, and never bind a lens that's missing.
    var hasFront by remember { mutableStateOf(false) }
    var hasBack by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Full-resolution still-capture use case (US-006), bound alongside the preview.
    // Prioritise quality over latency since these are proof photos.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    // Prevents firing a second capture before the first one finishes.
    var isCapturing by remember { mutableStateOf(false) }

    // Selected stamp template (US-009), persisted across captures + launches.
    val templateStore = remember { StampTemplateStore(context) }
    var template by rememberSaveable { mutableStateOf(templateStore.load()) }

    // User's custom stamp fields (US-010): project/site name, note, logo. Persisted
    // across captures + launches via SharedPreferences + an app-storage logo file.
    val customFieldsStore = remember { CustomFieldsStore(context) }
    var customFields by remember { mutableStateOf(customFieldsStore.load()) }
    var showCustomFieldsDialog by remember { mutableStateOf(false) }

    // Logo picker — copies the chosen image into app storage so it persists.
    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null && customFieldsStore.saveLogo(uri)) {
            customFields = customFieldsStore.load()
        }
    }

    // (Re)bind whenever the lens facing changes. The camera provider keys its
    // use cases to the lifecycle, so we unbind everything first to avoid
    // "use case already bound" errors when toggling.
    LaunchedEffect(lensFacing) {
        try {
            val cameraProvider = context.awaitCameraProvider()
            hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
            hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

            // If the requested lens isn't present (e.g. a tablet with no front
            // camera), fall back to whichever one exists rather than blanking.
            val effectiveLens = when {
                lensFacing == CameraSelector.LENS_FACING_FRONT && !hasFront -> CameraSelector.LENS_FACING_BACK
                lensFacing == CameraSelector.LENS_FACING_BACK && !hasBack -> CameraSelector.LENS_FACING_FRONT
                else -> lensFacing
            }
            if (effectiveLens != lensFacing) {
                lensFacing = effectiveLens // triggers a re-run with the valid lens
                return@LaunchedEffect
            }

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val selector = CameraSelector.Builder()
                .requireLensFacing(effectiveLens)
                .build()
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera preview", e)
        }
    }

    // Release the camera when this composable leaves the tree (e.g. tab switch).
    DisposableEffect(Unit) {
        onDispose {
            ContextCompat.getMainExecutor(context).execute {
                try {
                    ProcessCameraProvider.getInstance(context).get().unbindAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unbind camera on dispose", e)
                }
            }
        }
    }

    // Acquire location + reverse-geocoded address (US-005); shown as an overlay
    // and later burned into the captured photo's stamp (US-007).
    val locationState by rememberCurrentLocation()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        LocationInfoOverlay(
            state = locationState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Template picker (US-009) — selection persists across captures.
            TemplatePickerRow(
                selected = template,
                onSelect = {
                    template = it
                    templateStore.save(it)
                },
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // Shutter button — captures a full-res photo to MediaStore (US-006).
            FilledIconButton(
                onClick = {
                    if (isCapturing) return@FilledIconButton
                    isCapturing = true
                    // Snapshot the current location read at shutter-press time so it
                    // gets burned into the photo's stamp (US-007). Location fields are
                    // null until a fix arrives; the date/time is always stamped.
                    val available = locationState as? LocationUiState.Available
                    val stamp = StampData(
                        timestamp = Date(),
                        latitude = available?.fix?.latitude,
                        longitude = available?.fix?.longitude,
                        accuracyMeters = available?.fix?.accuracyMeters,
                        address = available?.address,
                        projectName = customFields.projectName.ifBlank { null },
                        note = customFields.note.ifBlank { null },
                        // Snapshot the user's formatting prefs (US-014) so they're
                        // burned into this capture's stamp.
                        coordinateFormat = AppSettingsStore.loadCoordinateFormat(context),
                        timeFormat = AppSettingsStore.loadTimeFormat(context),
                    )
                    val logoFile = customFieldsStore.logoFileOrNull()
                    capturePhoto(context, imageCapture, stamp, template, logoFile) { uri ->
                        isCapturing = false
                        val msg = if (uri != null) {
                            context.getString(R.string.capture_saved)
                        } else {
                            context.getString(R.string.capture_failed)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isCapturing,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Lens,
                    contentDescription = stringResource(R.string.camera_shutter),
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // Edit custom stamp fields (US-010) — top-right, clear of the location
        // overlay and the bottom controls.
        IconButton(
            onClick = { showCustomFieldsDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 12.dp, top = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.custom_fields_open),
                tint = Color.White,
            )
        }

        // Front/back toggle, sitting to the right of the shutter. Only shown
        // when the device actually has both lenses.
        if (hasFront && hasBack) {
            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 32.dp, bottom = 48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Cameraswitch,
                    contentDescription = stringResource(R.string.camera_flip),
                    tint = Color.White,
                )
            }
        }

        if (showCustomFieldsDialog) {
            CustomFieldsDialog(
                fields = customFields,
                onPickLogo = {
                    logoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveLogo = {
                    customFieldsStore.clearLogo()
                    customFields = customFieldsStore.load()
                },
                onSave = { projectName, note ->
                    customFieldsStore.saveFields(projectName, note)
                    customFields = customFieldsStore.load()
                    showCustomFieldsDialog = false
                },
                onDismiss = { showCustomFieldsDialog = false },
            )
        }
    }
}

/**
 * Dialog for editing the custom stamp fields (US-010): project/site name, a
 * free-text note, and an optional logo picked from the gallery. The project name
 * and note are committed on Save; logo add/remove apply immediately (so the
 * "Logo added" state reflects the current [fields]).
 */
@Composable
private fun CustomFieldsDialog(
    fields: CustomFields,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onSave: (projectName: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var projectName by rememberSaveable(fields.projectName) { mutableStateOf(fields.projectName) }
    var note by rememberSaveable(fields.note) { mutableStateOf(fields.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_fields_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text(stringResource(R.string.custom_field_project)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.custom_field_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.custom_field_logo),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onPickLogo) {
                        Text(
                            if (fields.hasLogo) {
                                stringResource(R.string.custom_field_change_logo)
                            } else {
                                stringResource(R.string.custom_field_add_logo)
                            },
                        )
                    }
                    if (fields.hasLogo) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = onRemoveLogo) {
                            Text(stringResource(R.string.custom_field_remove_logo))
                        }
                    }
                }
                if (fields.hasLogo) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.custom_field_logo_added),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(projectName, note) }) {
                Text(stringResource(R.string.custom_fields_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.custom_fields_cancel))
            }
        },
    )
}

/**
 * Horizontally-scrollable row of FilterChips for choosing the stamp template
 * (US-009). The current [selected] template is highlighted; tapping a chip calls
 * [onSelect] so the caller can apply + persist it.
 */
@Composable
private fun TemplatePickerRow(
    selected: StampTemplate,
    onSelect: (StampTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StampTemplate.entries.forEachIndexed { index, t ->
            if (index > 0) Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = t == selected,
                onClick = { onSelect(t) },
                label = { Text(t.label(context)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    labelColor = Color.White,
                    selectedContainerColor = Color.White,
                    selectedLabelColor = Color.Black,
                ),
            )
        }
    }
}

/** Awaits the ListenableFuture from ProcessCameraProvider without pulling in guava-coroutines. */
private suspend fun android.content.Context.awaitCameraProvider(): ProcessCameraProvider {
    val future = ProcessCameraProvider.getInstance(this)
    return suspendCoroutine { cont ->
        future.addListener(
            { cont.resume(future.get()) },
            ContextCompat.getMainExecutor(this),
        )
    }
}
