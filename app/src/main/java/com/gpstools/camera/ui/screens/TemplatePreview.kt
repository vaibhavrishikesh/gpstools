package com.gpstools.camera.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.gpstools.camera.media.StampData
import com.gpstools.camera.media.StampTemplate
import com.gpstools.camera.media.drawStamp
import java.util.Date

/**
 * Renders a real, to-scale preview of how [template] stamps a photo, using fixed
 * sample data over a neutral gradient "photo" with a placeholder map. Lets users see
 * the actual difference between templates (header band, accent bar, map slot, fields)
 * instead of just a name. Cached per (template, size) via [remember].
 */
@Composable
fun rememberTemplatePreview(
    template: StampTemplate,
    widthPx: Int = 560,
    heightPx: Int = 360,
): ImageBitmap {
    val context = LocalContext.current
    return remember(template, widthPx, heightPx) {
        renderTemplatePreview(context, template, widthPx, heightPx).asImageBitmap()
    }
}

private fun renderTemplatePreview(
    context: Context,
    template: StampTemplate,
    widthPx: Int,
    heightPx: Int,
): Bitmap {
    val bg = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bg)
    // Neutral "photo" backdrop so the stamp reads against something realistic.
    val sky = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, heightPx.toFloat(),
            intArrayOf(Color.rgb(0x8E, 0xA9, 0xC4), Color.rgb(0x6E, 0x8B, 0x74)),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), sky)

    val map = if (template.usesMap) fakeMap((widthPx * 0.22f).toInt().coerceAtLeast(24)) else null
    return drawStamp(bg, sampleStamp(context), template, map, null)
}

/** A placeholder map thumbnail (gridded grey square + a red centre pin). */
private fun fakeMap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.rgb(0xE2, 0xE6, 0xDA))
    val grid = Paint().apply { color = Color.rgb(0xC2, 0xCA, 0xB8); strokeWidth = size / 64f }
    val step = size / 4f
    var p = step
    while (p < size) {
        c.drawLine(p, 0f, p, size.toFloat(), grid)
        c.drawLine(0f, p, size.toFloat(), p, grid)
        p += step
    }
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xD3, 0x2F, 0x2F) }
    c.drawCircle(size / 2f, size / 2f, size / 9f, ring)
    c.drawCircle(size / 2f, size / 2f, size / 14f, dot)
    return bmp
}

/** Fixed, representative stamp data so every preview shows the same fields. */
private fun sampleStamp(context: Context): StampData = StampData(
    timestamp = Date(1_718_524_800_000L), // fixed so previews are deterministic
    latitude = 28.612894,
    longitude = 77.229446,
    accuracyMeters = 5f,
    address = "Connaught Place, New Delhi 110001, India",
    weather = "28°C · Clear",
    altitudeFacing = "Altitude 216m · Facing NE",
    showDateTime = true,
)
