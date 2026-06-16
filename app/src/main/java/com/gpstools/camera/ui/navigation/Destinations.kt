package com.gpstools.camera.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.gpstools.camera.R

/**
 * Top-level destinations shown in the bottom navigation bar.
 * [Home] is the start destination (US-015 dashboard).
 */
enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.tab_home, Icons.Filled.Home),
    Camera("camera", R.string.tab_camera, Icons.Filled.PhotoCamera),
    Gallery("gallery", R.string.tab_gallery, Icons.Filled.PhotoLibrary),
    Map("map", R.string.tab_map, Icons.Filled.Map),
    Settings("settings", R.string.tab_settings, Icons.Filled.Settings),
}

val START_DESTINATION = Destination.Home
