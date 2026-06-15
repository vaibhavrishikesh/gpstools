package com.gpstools.camera

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gpstools.camera.ads.Ads
import com.gpstools.camera.billing.BillingManager
import com.gpstools.camera.billing.Premium
import com.gpstools.camera.locale.wrapWithStoredLocale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gpstools.camera.ui.navigation.Destination
import com.gpstools.camera.ui.navigation.START_DESTINATION
import com.gpstools.camera.ui.screens.CameraScreen
import com.gpstools.camera.ui.screens.GalleryScreen
import com.gpstools.camera.ui.screens.MapScreen
import com.gpstools.camera.ui.screens.SettingsScreen
import com.gpstools.camera.ui.theme.GpstoolsTheme

class MainActivity : ComponentActivity() {
    // Connects to Play Billing on launch to RESTORE the one-time entitlement
    // (US-016) — queryOwnedPurchases runs as soon as the connection is ready, so
    // a reinstalled app re-grants premium without any user action.
    private var billingManager: BillingManager? = null

    override fun attachBaseContext(newBase: Context) {
        // Apply the in-app language (US-013) before the activity inflates so all
        // resources resolve in the chosen locale; recreate() re-runs this.
        super.attachBaseContext(newBase.wrapWithStoredLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialise AdMob + load the persisted ads-enabled flag (US-015). Guarded
        // internally so it can never crash the app.
        Ads.initialize(this)
        // Load the persisted premium entitlement (US-016) and kick off the Play
        // Billing restore so reinstalls re-grant ownership automatically.
        Premium.load(this)
        billingManager = BillingManager(this).also { it.start() }
        enableEdgeToEdge()
        setContent {
            GpstoolsTheme {
                GpsToolsApp()
            }
        }
    }

    override fun onDestroy() {
        billingManager?.release()
        billingManager = null
        super.onDestroy()
    }
}

@Composable
fun GpsToolsApp() {
    val navController = rememberNavController()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { BottomNavBar(navController) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = START_DESTINATION.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Camera.route) { CameraScreen() }
            composable(Destination.Gallery.route) { GalleryScreen() }
            composable(Destination.Map.route) { MapScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    NavigationBar {
        Destination.entries.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == destination.route
            } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        // Avoid building up a back stack of top-level tabs.
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
