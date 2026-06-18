package com.gpstools.camera.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.gpstools.camera.R

/**
 * App destinations. The app lands on [Home] (dashboard); the camera and everything
 * else are reached from the home tiles or the navigation drawer ([AppDrawer]).
 */
enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    Camera("camera", R.string.tab_camera, Icons.Filled.PhotoCamera),
    Gallery("gallery", R.string.tab_gallery, Icons.Filled.PhotoLibrary),
    Templates("templates", R.string.tab_templates, Icons.Filled.Description),
    Map("map", R.string.tab_map, Icons.Filled.Map),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

val START_DESTINATION = Destination.Home
