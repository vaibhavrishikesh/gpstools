package com.gpstools.camera

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gpstools.camera.ads.Ads
import com.gpstools.camera.billing.BillingManager
import com.gpstools.camera.billing.Premium
import com.gpstools.camera.billing.Subscription
import com.gpstools.camera.locale.wrapWithStoredLocale
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.gpstools.camera.ads.BannerAd
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gpstools.camera.ui.navigation.Destination
import com.gpstools.camera.ui.screens.AppDrawer
import com.gpstools.camera.ui.screens.CameraScreen
import com.gpstools.camera.ui.screens.GalleryScreen
import com.gpstools.camera.ui.screens.MapScreen
import com.gpstools.camera.ui.screens.SettingsScreen
import com.gpstools.camera.ui.screens.TemplatesScreen
import com.gpstools.camera.ui.theme.BrandGold
import com.gpstools.camera.ui.theme.BrandNavy
import com.gpstools.camera.ui.theme.GpstoolsTheme
import kotlinx.coroutines.launch

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
        // Load the persisted entitlements — the one-time premium IAP (US-016) and
        // the Pro subscription (US-018) — then kick off the Play Billing restore so
        // reinstalls re-grant ownership of both automatically.
        Premium.load(this)
        Subscription.load(this)
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

/**
 * Camera-first app shell (redesign v3). The app opens straight into the camera;
 * Gallery / Templates / Map / Settings are reached from the slide-in [AppDrawer]
 * (top-left ☰ on the camera, swipe-from-edge anywhere). There is no bottom nav.
 */
@Composable
fun GpsToolsApp() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigateTopLevel(route)
                },
            )
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Destination.Camera.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(Destination.Camera.route) {
                CameraScreen(onOpenDrawer = openDrawer)
            }
            composable(Destination.Gallery.route) {
                SecondaryScreen(R.string.tab_gallery, onBack = { navController.popBackStack() }) {
                    GalleryScreen()
                }
            }
            composable(Destination.Templates.route) {
                SecondaryScreen(R.string.tab_templates, onBack = { navController.popBackStack() }) {
                    TemplatesScreen()
                }
            }
            composable(Destination.Map.route) {
                SecondaryScreen(R.string.tab_map, onBack = { navController.popBackStack() }) {
                    MapScreen()
                }
            }
            composable(Destination.Settings.route) {
                SecondaryScreen(R.string.tab_settings, onBack = { navController.popBackStack() }) {
                    SettingsScreen()
                }
            }
        }
    }
}

/**
 * Chrome for a non-camera destination: a navy top bar with a back arrow (returns to
 * the camera) and an ad banner pinned to the bottom (US-015; auto-hidden for Pro).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondaryScreen(
    @StringRes titleRes: Int,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandNavy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = BrandGold,
                ),
            )
        },
        bottomBar = { BannerAd() },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}

/**
 * Navigate to a drawer destination, keeping Camera as the single root so the back
 * stack stays flat (Camera → current) and back always returns to the camera.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Destination.Camera.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
