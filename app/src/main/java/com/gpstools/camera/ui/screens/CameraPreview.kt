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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gpstools.camera.R
import com.gpstools.camera.ads.InterstitialAdManager
import com.gpstools.camera.billing.Premium
import com.gpstools.camera.locale.findActivity
import com.gpstools.camera.location.LocationUiState
import com.gpstools.camera.location.formatAltitudeFacing
import com.gpstools.camera.location.rememberCompassBearing
import com.gpstools.camera.media.CustomFields
import com.gpstools.camera.media.CustomFieldsStore
import com.gpstools.camera.media.StampData
import com.gpstools.camera.media.StampTemplate
import com.gpstools.camera.media.StampTemplateStore
import com.gpstools.camera.media.capturePhoto
import com.gpstools.camera.media.label
import com.gpstools.camera.media.openLastPhoto
import com.gpstools.camera.settings.AppSettingsStore
import com.gpstools.camera.settings.StampPosition
import com.gpstools.camera.ui.theme.BrandGold
import com.gpstools.camera.ui.theme.BrandNavy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    // Flash mode (P2-US-006): cycles off → on → auto. Applied to the shared
    // ImageCapture use case; survives recomposition / config changes.
    var flashMode by rememberSaveable { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }

    // Free-tier interstitial ad shown after every Nth capture (US-015). Preloaded
    // up front; never blocks the capture itself.
    val interstitialAds = remember { InterstitialAdManager(context) }
    LaunchedEffect(Unit) { interstitialAds.preload() }

    // Selected stamp template (US-009), persisted across captures + launches.
    val templateStore = remember { StampTemplateStore(context) }
    var template by rememberSaveable { mutableStateOf(templateStore.load()) }

    // User's custom stamp fields (US-010): project/site name, note, logo. Persisted
    // across captures + launches via SharedPreferences + an app-storage logo file.
    val customFieldsStore = remember { CustomFieldsStore(context) }
    var customFields by remember { mutableStateOf(customFieldsStore.load()) }
    var showCustomFieldsSheet by remember { mutableStateOf(false) }

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

    // WYSIWYG stamp position (P2-US-011): the live GPS card sits at the SAME edge the
    // stamp will be burned at, so the preview matches the photo. Read once on entry
    // (the camera tab re-composes when navigated back to, so a setting change applies).
    val stampPosition = remember { AppSettingsStore.loadStampPosition(context) }
    val stampAtTop = stampPosition == StampPosition.TOP

    // Framing grid (P2-US-012): the 3×3 rule-of-thirds overlay is opt-in from Settings.
    // Read once on entry (the camera tab re-composes when navigated back to, so a
    // setting change applies) — same pattern as the stamp position above.
    val showGrid = remember { AppSettingsStore.loadShowGrid(context) }

    // Live altitude for the viewfinder HUD (P2-US-012), from the current fix when known.
    val altitudeMeters = (locationState as? LocationUiState.Available)?.fix?.altitudeMeters

    // Live compass bearing (P2-US-013) from the rotation-vector sensor; null when the
    // device has no such sensor. Shown as a cardinal "Facing NE" on the HUD + stamp.
    val compassBearing by rememberCompassBearing()

    // Pre-resolved so it can be applied via semantics on the non-composable shutter.
    val shutterContentDescription = stringResource(R.string.camera_shutter)

    // Pro gestures (P2-US-018): the self-timer countdown + the swipe-left quick-look
    // both need a coroutine scope. [countdown] is null when idle, or the visible
    // 3-2-1 number while the long-press self-timer runs.
    val scope = rememberCoroutineScope()
    var countdown by remember { mutableStateOf<Int?>(null) }
    val noPhotosMessage = stringResource(R.string.camera_no_photos)

    // Capture feedback: a brief full-screen white flash on shutter-press (the
    // universal "photo taken" cue) plus a progress ring on the shutter while the
    // save runs. Without this the static white shutter gave no sign anything
    // happened — users tapped repeatedly thinking it hadn't fired.
    val flashAlpha = remember { Animatable(0f) }

    // The actual capture, shared by a normal shutter tap and the self-timer (P2-US-018).
    // P2-US-002 (one-tap capture): a tap fires this IMMEDIATELY; no "Stamp details" modal
    // ever blocks the shutter. The stamp auto-fills location/address/date-time, and the
    // last-used project/site name + note (set once via the optional Edit affordance) apply
    // silently below.
    fun captureNow() {
        if (isCapturing || countdown != null) return
        isCapturing = true
        // Fire the shutter flash immediately on tap so the press is acknowledged
        // even before the (slower) save completes.
        scope.launch {
            flashAlpha.snapTo(0.8f)
            flashAlpha.animateTo(0f, animationSpec = tween(durationMillis = 320))
        }
        // Snapshot the current location read at shutter-press time so it
        // gets burned into the photo's stamp (US-007). Location fields are
        // null until a fix arrives; the date/time is always stamped.
        val available = locationState as? LocationUiState.Available
        // Re-read the persisted custom fields at shutter time (cheap
        // SharedPreferences load) so the LAST-USED project/site name + note
        // always apply silently — even if they changed since composition.
        val latestFields = customFieldsStore.load()
        val stamp = StampData(
            timestamp = Date(),
            latitude = available?.fix?.latitude,
            longitude = available?.fix?.longitude,
            accuracyMeters = available?.fix?.accuracyMeters,
            address = available?.address,
            // Weather (US-009): snapshot the loaded current weather, if any.
            weather = available?.weather?.describe(context),
            // Altitude + compass facing (P2-US-013): snapshot the current
            // fix altitude + compass bearing into one pre-formatted line.
            altitudeFacing = formatAltitudeFacing(
                context,
                available?.fix?.altitudeMeters,
                compassBearing,
            ),
            // Raw altitude for machine-readable EXIF GPS (P2-US-014).
            altitudeMeters = available?.fix?.altitudeMeters,
            projectName = latestFields.projectName.ifBlank { null },
            note = latestFields.note.ifBlank { null },
            // Custom watermark (P2-US-017), drawn bottom-right.
            watermark = latestFields.watermark.ifBlank { null },
            // Snapshot the user's formatting prefs (US-014) so they're
            // burned into this capture's stamp.
            coordinateFormat = AppSettingsStore.loadCoordinateFormat(context),
            timeFormat = AppSettingsStore.loadTimeFormat(context),
            // Which location fields render (P2-US-010), snapshot at shutter.
            layoutPreset = AppSettingsStore.loadLayoutPreset(context),
            // Which edge the stamp anchors to (P2-US-011), snapshot at shutter.
            stampPosition = AppSettingsStore.loadStampPosition(context),
            // Whether the date/time line is burned in (P2-US-017).
            showDateTime = AppSettingsStore.loadShowDateTime(context),
        )
        val logoFile = customFieldsStore.logoFileOrNull()
        // P2-US-005: premium templates (Field Report) are unlocked for now,
        // so the selected template always renders as-is.
        capturePhoto(context, imageCapture, stamp, template, logoFile) { uri ->
            isCapturing = false
            val msg = if (uri != null) {
                context.getString(R.string.capture_saved)
            } else {
                context.getString(R.string.capture_failed)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            // Photo is already saved — only now (post-save) maybe show an
            // interstitial, so an ad can never block or delay a capture.
            if (uri != null) {
                interstitialAds.onCaptureCompleted(context.findActivity())
            }
        }
    }

    // Long-press the shutter (P2-US-018) → a 3-second self-timer that counts 3-2-1
    // on screen, then fires the capture. Ignored if a capture/timer is already running.
    fun startSelfTimer() {
        if (isCapturing || countdown != null) return
        scope.launch {
            for (n in 3 downTo 1) {
                countdown = n
                delay(1000L)
            }
            countdown = null
            captureNow()
        }
    }

    // Swipe left on the viewfinder (P2-US-018) → quick-look the most recent capture in a
    // full-screen system viewer. Queries off the main thread; toasts when none exist yet.
    fun openLastPhotoQuickLook() {
        scope.launch {
            val opened = withContext(Dispatchers.IO) { openLastPhoto(context) }
            if (!opened) {
                Toast.makeText(context, noPhotosMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                // Swipe left → open the last captured photo (P2-US-018). A horizontal
                // drag that ends more than ~80px to the left triggers the quick-look;
                // vertical/short drags are ignored so they don't interfere with controls.
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = { if (totalDrag < -80f) openLastPhotoQuickLook() },
                    ) { _, dragAmount -> totalDrag += dragAmount }
                },
        )

        // 3×3 rule-of-thirds framing grid (P2-US-012) — 30% white lines, opt-in.
        if (showGrid) {
            RuleOfThirdsGrid(modifier = Modifier.fillMaxSize())
        }

        // WYSIWYG (P2-US-011): when the stamp position is TOP the GPS card sits at the
        // top; when BOTTOM it sits inside the bottom controls column (above the mode
        // chips) — matching the edge the stamp is burned at.
        if (stampAtTop) {
            LocationInfoOverlay(
                state = locationState,
                // US-003: the "Edit" affordance now lives ON the GPS card and opens the
                // optional stamp-details bottom sheet (never blocks capture).
                onEditClick = { showCustomFieldsSheet = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Bottom-left viewfinder HUD (P2-US-012): live date/time + altitude, 12sp
            // white with a black shadow. Left-aligned at the top of the bottom controls
            // stack so it reads as a lower-left HUD without overlapping the controls.
            ViewfinderInfoOverlay(
                altitudeMeters = altitudeMeters,
                compassBearing = compassBearing,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 16.dp, bottom = 12.dp),
            )

            if (!stampAtTop) {
                LocationInfoOverlay(
                    state = locationState,
                    onEditClick = { showCustomFieldsSheet = true },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 20.dp),
                )
            }

            // Mode selector (P2-US-005) — centered icon chips, selection persists
            // across captures. Premium templates (Field Report) show a gold "PRO"
            // badge instead of a lock but stay selectable/usable for now.
            TemplatePickerRow(
                selected = template,
                isPremium = Premium.isPremium,
                onSelect = {
                    template = it
                    templateStore.save(it)
                },
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // Camera controls row (P2-US-006): flash toggle | shutter | flip.
            // The shutter stays centered; the 48dp side buttons sit at the edges
            // (a placeholder keeps the shutter centered when there's only one lens).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Flash toggle — cycles off → on → auto; applied to ImageCapture.
                SideControlButton(
                    onClick = { flashMode = nextFlashMode(flashMode) },
                    icon = flashIcon(flashMode),
                    contentDescription = stringResource(
                        R.string.camera_flash,
                        stringResource(flashLabelRes(flashMode)),
                    ),
                )

                // Shutter — 72dp white fill, 4dp grey ring, 8dp elevation (US-006).
                // Tap = immediate capture (P2-US-002 one-tap); long-press = a 3-second
                // self-timer with an on-screen countdown (P2-US-018). The capture body
                // lives in [captureNow] so both the tap and the timer share it.
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(4.dp, Color.LightGray),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(72.dp)
                        .semantics {
                            contentDescription = shutterContentDescription
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { captureNow() },
                                onLongPress = { startSelfTimer() },
                            )
                        },
                    content = {
                        // While the photo is being saved, show a navy progress ring
                        // inside the shutter so the tap is clearly acknowledged.
                        if (isCapturing) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = BrandNavy,
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    },
                )

                // Flip lens — only when both lenses exist, else a spacer to keep
                // the shutter visually centered.
                if (hasFront && hasBack) {
                    SideControlButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        icon = Icons.Filled.Cameraswitch,
                        contentDescription = stringResource(R.string.camera_flip),
                    )
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // Self-timer countdown (P2-US-018): a large 3-2-1 number centered over the
        // preview while the long-press timer runs, then it captures.
        countdown?.let { n ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = n.toString(),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 8f),
                    ),
                )
            }
        }

        // Full-screen white shutter flash (capture feedback). Sits above the
        // preview/controls but below the bottom sheet; only drawn while fading.
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(flashAlpha.value)
                    .background(Color.White),
            )
        }

        if (showCustomFieldsSheet) {
            CustomFieldsSheet(
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
                onSave = { projectName, note, watermark ->
                    customFieldsStore.saveFields(projectName, note, watermark)
                    customFields = customFieldsStore.load()
                    showCustomFieldsSheet = false
                },
                onDismiss = { showCustomFieldsSheet = false },
            )
        }
    }
}

/**
 * A 48dp circular side control (flash / flip) for the camera controls row
 * (P2-US-006): translucent grey 20% background, white icon.
 */
@Composable
private fun SideControlButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White.copy(alpha = 0.2f),
            contentColor = Color.White,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Next flash mode in the off → on → auto cycle (P2-US-006). */
private fun nextFlashMode(mode: Int): Int = when (mode) {
    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
    else -> ImageCapture.FLASH_MODE_OFF
}

/** Icon for the current flash mode. */
private fun flashIcon(mode: Int): ImageVector = when (mode) {
    ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
    ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
    else -> Icons.Filled.FlashOff
}

/** String resource describing the current flash mode (for content description). */
private fun flashLabelRes(mode: Int): Int = when (mode) {
    ImageCapture.FLASH_MODE_ON -> R.string.camera_flash_on
    ImageCapture.FLASH_MODE_AUTO -> R.string.camera_flash_auto
    else -> R.string.camera_flash_off
}

/**
 * 3×3 rule-of-thirds framing grid (P2-US-012): two evenly-spaced vertical + horizontal
 * lines at 30% white over the live preview. Toggled from Settings; opt-in (off by default).
 */
@Composable
private fun RuleOfThirdsGrid(modifier: Modifier = Modifier) {
    val lineColor = Color.White.copy(alpha = 0.3f)
    Canvas(modifier = modifier) {
        val stroke = 1.dp.toPx()
        val w = size.width
        val h = size.height
        // Vertical thirds.
        drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), stroke)
        drawLine(lineColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), stroke)
        // Horizontal thirds.
        drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), stroke)
        drawLine(lineColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), stroke)
    }
}

/**
 * Bottom-left viewfinder HUD (P2-US-012 / P2-US-013): a live ticking date/time line
 * plus an "Altitude 342m · Facing NE" line combining the fix's altitude (when known)
 * with the live compass [compassBearing] mapped to a cardinal point. 12sp white with a
 * black drop shadow so it stays legible over any preview. The clock follows the user's
 * 24/12-hour [TimeFormat] preference for consistency with the burned stamp.
 */
@Composable
private fun ViewfinderInfoOverlay(
    altitudeMeters: Double?,
    compassBearing: Float?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timeFormat = remember { AppSettingsStore.loadTimeFormat(context) }
    val formatter = remember(timeFormat) {
        SimpleDateFormat(timeFormat.datePattern, Locale.getDefault())
    }
    // Tick once a second so the displayed time stays current.
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000L)
        }
    }
    val hudStyle = TextStyle(
        color = Color.White,
        fontSize = 12.sp,
        shadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 3f),
    )
    val altitudeFacing = formatAltitudeFacing(context, altitudeMeters, compassBearing)
    Column(modifier = modifier) {
        Text(text = formatter.format(now), style = hudStyle)
        if (altitudeFacing != null) {
            Text(text = altitudeFacing, style = hudStyle)
        }
    }
}

/**
 * Bottom sheet for editing the optional custom stamp fields (US-003 / US-010):
 * project/site name, a free-text note, and an optional logo picked from the
 * gallery. Replaces the old blocking AlertDialog — it slides up from the bottom,
 * is dismissible, and NEVER blocks capture (the shutter stays live behind it).
 * The project name and note are committed on Save; logo add/remove apply
 * immediately (so the "Logo added" state reflects the current [fields]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFieldsSheet(
    fields: CustomFields,
    onPickLogo: () -> Unit,
    onRemoveLogo: () -> Unit,
    onSave: (projectName: String, note: String, watermark: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var projectName by rememberSaveable(fields.projectName) { mutableStateOf(fields.projectName) }
    var note by rememberSaveable(fields.note) { mutableStateOf(fields.note) }
    var watermark by rememberSaveable(fields.watermark) { mutableStateOf(fields.watermark) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.custom_fields_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
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
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = watermark,
                onValueChange = { watermark = it },
                label = { Text(stringResource(R.string.custom_field_watermark)) },
                singleLine = true,
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
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.custom_fields_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(projectName, note, watermark) }) {
                    Text(stringResource(R.string.custom_fields_save))
                }
            }
        }
    }
}

/** Mode-chip leading icon for each stamp template (P2-US-005): 📷 / 🎯 / 📋. */
private fun StampTemplate.icon(): ImageVector = when (this) {
    StampTemplate.CLASSIC -> Icons.Filled.PhotoCamera
    StampTemplate.MINIMAL -> Icons.Filled.CenterFocusStrong
    StampTemplate.FIELD_REPORT -> Icons.Filled.Description
}

/**
 * Centered row of mode chips for choosing the stamp template (P2-US-005). Each chip
 * has an icon + label; the [selected] one is filled navy with white text/icon while
 * the rest are transparent with grey [#9AA0A6] text. Tapping a chip calls [onSelect]
 * so the caller can apply + persist it.
 *
 * Premium templates (Field Report) show a small gold "PRO" badge while [isPremium] is
 * false — replacing the old lock — but stay fully selectable/usable (unlocked for now).
 */
@Composable
private fun TemplatePickerRow(
    selected: StampTemplate,
    isPremium: Boolean,
    onSelect: (StampTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StampTemplate.entries.forEach { t ->
            val isSelected = t == selected
            val showProBadge = t.premium && !isPremium
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(t) },
                label = { Text(t.label(context)) },
                leadingIcon = {
                    Icon(
                        imageVector = t.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = if (showProBadge) {
                    { ProBadge() }
                } else {
                    null
                },
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Transparent,
                    labelColor = TextSecondary,
                    iconColor = TextSecondary,
                    selectedContainerColor = BrandNavy,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                ),
            )
        }
    }
}

/** Small gold "PRO" badge shown on premium mode chips (P2-US-005). */
@Composable
private fun ProBadge() {
    Text(
        text = stringResource(R.string.pro_badge),
        color = BrandNavy,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(BrandGold, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** Secondary text grey (#9AA0A6) for unselected mode chips — matches the v2 spec. */
private val TextSecondary = Color(0xFF9AA0A6)

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
