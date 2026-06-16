package com.gpstools.camera.ui.theme

import androidx.compose.ui.graphics.Color

// --- Brand palette (Phase 2): navy + gold ---
val BrandNavy = Color(0xFF15294D)       // primary brand navy
val BrandNavyDeep = Color(0xFF0F1626)   // dark background
val BrandNavySurface = Color(0xFF131C2E) // dark surface
val BrandNavyLight = Color(0xFF2A4470)  // container / lighter navy
val BrandGold = Color(0xFFF2A93B)       // gold accent
val BrandGoldDark = Color(0xFFC98A24)   // darker gold (light-theme tertiary)
val BrandGoldSoft = Color(0xFFFFD699)   // soft gold (dark-theme tertiary)

// --- GPS accuracy colour tokens (shared across screens) ---
val AccuracyGood = Color(0xFF34A853)    // < 10 m
val AccuracyAvg = Color(0xFFFB8C00)     // 10–20 m
val AccuracyPoor = Color(0xFFE53935)    // > 20 m

/**
 * Maps a GPS horizontal accuracy (metres) to its brand-defined colour token.
 * < 10 m = Good (green), 10–20 m = Avg (amber), > 20 m = Poor (red).
 * A null accuracy is treated as Poor (no fix / unknown).
 */
fun accuracyColor(accuracyMeters: Float?): Color = when {
    accuracyMeters == null -> AccuracyPoor
    accuracyMeters < 10f -> AccuracyGood
    accuracyMeters <= 20f -> AccuracyAvg
    else -> AccuracyPoor
}
