package com.gpstools.camera.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpstools.camera.R
import com.gpstools.camera.ui.navigation.Destination
import com.gpstools.camera.ui.theme.BrandGold
import com.gpstools.camera.ui.theme.BrandNavyDeep
import com.gpstools.camera.ui.theme.BrandNavyLight

/** One row in the navigation drawer. */
private data class DrawerEntry(
    val labelRes: Int,
    val icon: ImageVector,
    val route: String,
)

/**
 * The app's slide-in navigation drawer (redesign v3). Replaces the old bottom
 * navigation bar: the app is camera-first, and everything else (Gallery, Map,
 * Templates, Settings, Go Pro) is reached from here via the top-left ☰ button.
 *
 * [onNavigate] receives the destination route; the host closes the drawer.
 */
@Composable
fun AppDrawer(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        DrawerEntry(R.string.tab_camera, Icons.Filled.PhotoCamera, Destination.Camera.route),
        DrawerEntry(R.string.tab_gallery, Icons.Filled.PhotoLibrary, Destination.Gallery.route),
        DrawerEntry(R.string.tab_templates, Icons.Filled.Description, Destination.Templates.route),
        DrawerEntry(R.string.tab_map, Icons.Filled.Map, Destination.Map.route),
        DrawerEntry(R.string.tab_settings, Icons.Filled.Settings, Destination.Settings.route),
        DrawerEntry(R.string.drawer_go_pro, Icons.Filled.WorkspacePremium, Destination.Settings.route),
    )
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = BrandNavyDeep,
    ) {
        // Brand header.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 36.dp, end = 24.dp, bottom = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = BrandGold,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
        }
        HorizontalDivider(color = BrandNavyLight)
        Spacer(Modifier.height(8.dp))

        entries.forEach { entry ->
            NavigationDrawerItem(
                icon = { Icon(entry.icon, contentDescription = null) },
                label = { Text(stringResource(entry.labelRes)) },
                selected = false,
                onClick = { onNavigate(entry.route) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedIconColor = BrandGold,
                    unselectedTextColor = Color.White,
                ),
            )
        }
    }
}
