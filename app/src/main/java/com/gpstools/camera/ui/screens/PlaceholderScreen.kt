package com.gpstools.camera.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gpstools.camera.R
import com.gpstools.camera.ui.navigation.Destination

/**
 * Distinct placeholder shown for each top-level destination until the real
 * screen lands in a later story.
 */
@Composable
fun PlaceholderScreen(
    icon: ImageVector,
    title: String,
    body: String,
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
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun CameraScreen(modifier: Modifier = Modifier) = PlaceholderScreen(
    icon = Destination.Camera.icon,
    title = stringResource(Destination.Camera.labelRes),
    body = stringResource(R.string.camera_placeholder),
    modifier = modifier,
)

@Composable
fun GalleryScreen(modifier: Modifier = Modifier) = PlaceholderScreen(
    icon = Destination.Gallery.icon,
    title = stringResource(Destination.Gallery.labelRes),
    body = stringResource(R.string.gallery_placeholder),
    modifier = modifier,
)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) = PlaceholderScreen(
    icon = Destination.Settings.icon,
    title = stringResource(Destination.Settings.labelRes),
    body = stringResource(R.string.settings_placeholder),
    modifier = modifier,
)
