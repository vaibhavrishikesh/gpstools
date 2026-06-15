package com.gpstools.camera.ads

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * An adaptive AdMob banner (US-015). Renders nothing when ads are disabled
 * ([Ads.adsEnabled] == false), so it disappears the moment the remove-ads IAP
 * (US-016) flips the flag. Every SDK call is wrapped in [runCatching] so a failed
 * load can never crash or block the host screen.
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!Ads.adsEnabled) return
    val widthDp = LocalConfiguration.current.screenWidthDp

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            FrameLayout(ctx).also { container ->
                runCatching {
                    val adView = AdView(ctx).apply {
                        adUnitId = Ads.bannerUnitId
                        setAdSize(
                            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp),
                        )
                    }
                    container.addView(adView)
                    adView.loadAd(AdRequest.Builder().build())
                }
            }
        },
    )
}
