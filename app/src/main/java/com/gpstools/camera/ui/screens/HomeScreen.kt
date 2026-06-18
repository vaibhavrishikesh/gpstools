package com.gpstools.camera.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpstools.camera.R
import com.gpstools.camera.ads.BannerAd
import com.gpstools.camera.ui.navigation.Destination
import com.gpstools.camera.ui.theme.BrandGold
import com.gpstools.camera.ui.theme.BrandNavy
import com.gpstools.camera.ui.theme.BrandNavyDeep
import com.gpstools.camera.ui.theme.BrandNavyLight
import com.gpstools.camera.ui.theme.BrandNavySurface

private data class HomeTile(val labelRes: Int, val icon: ImageVector, val route: String)

/**
 * Home dashboard — the app's landing screen. A navy+gold screen with a ☰ menu, the
 * quick-access tiles (Camera / Gallery / Map / Reports) and an ad banner pinned at the
 * bottom. [onOpenDrawer] opens the navigation drawer; [onTileClick] navigates to a route.
 */
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    onTileClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        HomeTile(R.string.tab_camera, Icons.Filled.PhotoCamera, Destination.Camera.route),
        HomeTile(R.string.tab_gallery, Icons.Filled.PhotoLibrary, Destination.Gallery.route),
        HomeTile(R.string.tab_map, Icons.Filled.Map, Destination.Map.route),
        HomeTile(R.string.tab_reports, Icons.Filled.PictureAsPdf, Destination.Gallery.route),
    )
    // Gentle continuous "breathing" pulse on the hero logo — subtle life on the home
    // screen without being distracting.
    val pulse by rememberInfiniteTransition(label = "logo").animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B3A66), BrandNavy, BrandNavyDeep),
                ),
            )
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar: hamburger opens the drawer.
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.drawer_open), tint = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        // Animated hero logo — fills the top and gives the screen some motion.
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(132.dp).scale(pulse),
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.app_name), color = BrandGold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.home_subtitle), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            tiles.chunked(2).forEach { rowTiles ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowTiles.forEach { tile ->
                        HomeTileCard(tile = tile, onClick = { onTileClick(tile.route) }, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        // Pushes the banner to the bottom; content stays in the upper-middle.
        Spacer(Modifier.weight(1f))
        // Ad banner pinned at the bottom (only takes space once an ad fills — real ads
        // serve once the app is published; debug builds show test ads).
        BannerAd(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun HomeTileCard(tile: HomeTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BrandNavySurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.size(64.dp).background(BrandNavyLight, CircleShape), contentAlignment = Alignment.Center) {
                Icon(tile.icon, contentDescription = null, tint = BrandGold, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(tile.labelRes), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
