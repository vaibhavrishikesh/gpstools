package com.gpstools.camera.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.gpstools.camera.R
import com.gpstools.camera.ui.navigation.Destination

/**
 * Camera tab. Gates the (still placeholder) camera content behind CAMERA and
 * ACCESS_FINE_LOCATION runtime permissions (US-003). Permissions are requested
 * automatically on first entry; if denied we show a rationale, and if
 * permanently denied we deep-link to the app's settings. No crash on any path.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(modifier: Modifier = Modifier) {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
    )

    // Distinguishes "first visit, never asked" from "asked and denied" so we
    // can tell a recoverable denial (show rationale) apart from a permanent one
    // (shouldShowRationale == false after a request => deep-link to settings).
    var hasRequested by rememberSaveable { mutableStateOf(false) }

    // Auto-request once when the Camera tab is first shown.
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted && !hasRequested) {
            hasRequested = true
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    when {
        permissionsState.allPermissionsGranted -> CameraReadyContent(modifier)
        else -> PermissionGate(
            permissionsState = permissionsState,
            hasRequested = hasRequested,
            onRequest = {
                hasRequested = true
                permissionsState.launchMultiplePermissionRequest()
            },
            modifier = modifier,
        )
    }
}

/** Placeholder for the camera preview that lands in US-004. */
@Composable
private fun CameraReadyContent(modifier: Modifier = Modifier) = PlaceholderScreen(
    icon = Destination.Camera.icon,
    title = stringResource(Destination.Camera.labelRes),
    body = stringResource(R.string.camera_placeholder),
    modifier = modifier,
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionGate(
    permissionsState: MultiplePermissionsState,
    hasRequested: Boolean,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // After a request, if the system no longer offers a rationale and the
    // permissions are still missing, the user picked "Don't ask again".
    val permanentlyDenied = hasRequested && !permissionsState.shouldShowRationale

    if (permanentlyDenied) {
        PermissionMessage(
            icon = Destination.Settings.icon,
            title = stringResource(R.string.permission_title),
            body = stringResource(R.string.permission_denied_body),
            buttonLabel = stringResource(R.string.permission_open_settings),
            onButtonClick = { context.openAppSettings() },
            modifier = modifier,
        )
    } else {
        PermissionMessage(
            icon = Destination.Camera.icon,
            title = stringResource(R.string.permission_title),
            body = stringResource(R.string.permission_rationale),
            buttonLabel = stringResource(R.string.permission_grant),
            onButtonClick = onRequest,
            modifier = modifier,
        )
    }
}

@Composable
private fun PermissionMessage(
    icon: ImageVector,
    title: String,
    body: String,
    buttonLabel: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Button(onClick = onButtonClick) {
            Text(buttonLabel)
        }
    }
}

/** Deep-links to this app's entry in the system Settings. */
private fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
