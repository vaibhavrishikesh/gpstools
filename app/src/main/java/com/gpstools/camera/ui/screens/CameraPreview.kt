package com.gpstools.camera.ui.screens

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
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

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shutter button — capture is wired up in US-006.
            FilledIconButton(
                onClick = { /* capture lands in US-006 */ },
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
