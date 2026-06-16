package com.gpstools.camera.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.gpstools.camera.R

/**
 * Live compass bearing for the viewfinder + stamp (P2-US-013). Subscribes to the
 * device's rotation-vector sensor while composed and emits the azimuth (degrees from
 * magnetic/true north, 0 = N, clockwise). Emits null when the device has no such
 * sensor (e.g. some emulators) so callers degrade gracefully — same null-as-absent
 * contract as the weather/map fetches. No permission required.
 */
@Composable
fun rememberCompassBearing(): State<Float?> {
    val context = LocalContext.current
    val bearing = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            private val rotationMatrix = FloatArray(9)
            private val orientation = FloatArray(3)
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                // orientation[0] is the azimuth in radians (-π..π); normalise to 0..360.
                val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                bearing.value = degrees
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }
    return bearing
}

/** Maps a compass [bearingDegrees] (0..360, 0 = N, clockwise) to one of 8 cardinal points. */
fun bearingToCardinalRes(bearingDegrees: Float): Int {
    val sectors = intArrayOf(
        R.string.compass_n, R.string.compass_ne, R.string.compass_e, R.string.compass_se,
        R.string.compass_s, R.string.compass_sw, R.string.compass_w, R.string.compass_nw,
    )
    val index = (((bearingDegrees + 22.5f) % 360f + 360f) % 360f / 45f).toInt() % 8
    return sectors[index]
}

/**
 * Builds the combined "Altitude 342m · Facing NE" line shown on the viewfinder HUD and
 * burned onto the stamp (P2-US-013). Includes only the pieces that are available
 * ([altitudeMeters] from the fix, [bearingDegrees] from the compass) and returns null
 * when neither is — so the line is simply omitted.
 */
fun formatAltitudeFacing(context: Context, altitudeMeters: Double?, bearingDegrees: Float?): String? {
    val parts = mutableListOf<String>()
    if (altitudeMeters != null) {
        parts += context.getString(R.string.stamp_altitude, altitudeMeters.toInt())
    }
    if (bearingDegrees != null) {
        parts += context.getString(
            R.string.compass_facing,
            context.getString(bearingToCardinalRes(bearingDegrees)),
        )
    }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
