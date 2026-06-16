package com.gpstools.camera.location

import android.content.Context
import android.util.Log
import com.gpstools.camera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private const val TAG = "WeatherProvider"

/** Short timeout so a slow/offline weather call never holds up the UI for long. */
private const val WEATHER_TIMEOUT_MS = 8_000L
private const val USER_AGENT = "gpstools/1.0 (Android; com.gpstools.camera)"

/**
 * Current weather for a coordinate (US-009): temperature in °C and the WMO weather
 * code from Open-Meteo, mapped to a coarse human-readable condition.
 */
data class Weather(
    val temperatureC: Double,
    val weatherCode: Int,
) {
    /** String resource for the condition, grouping WMO codes into a few buckets. */
    val conditionRes: Int
        get() = when (weatherCode) {
            0 -> R.string.weather_clear
            1, 2, 3 -> R.string.weather_cloudy
            45, 48 -> R.string.weather_fog
            51, 53, 55, 56, 57 -> R.string.weather_drizzle
            61, 63, 65, 66, 67 -> R.string.weather_rain
            71, 73, 75, 77, 85, 86 -> R.string.weather_snow
            80, 81, 82 -> R.string.weather_showers
            95, 96, 99 -> R.string.weather_thunderstorm
            else -> R.string.weather_unknown
        }

    /** "28°C · Clear" — the line rendered on the overlay and burned onto the stamp. */
    fun describe(context: Context): String =
        context.getString(R.string.weather_stamp, temperatureC.roundToInt(), context.getString(conditionRes))
}

/**
 * Fetches current weather for [latitude]/[longitude] from Open-Meteo's free, no-key
 * forecast API. Runs on [Dispatchers.IO] with a short timeout and returns null on any
 * failure (offline, HTTP error, parse error) so callers degrade gracefully to "no
 * weather" — same contract as the static-map fetch.
 */
suspend fun fetchWeather(latitude: Double, longitude: Double): Weather? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(WEATHER_TIMEOUT_MS) {
        try {
            // Double.toString is locale-independent (always '.'), safe to interpolate.
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$latitude&longitude=$longitude&current_weather=true",
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = WEATHER_TIMEOUT_MS.toInt()
                readTimeout = WEATHER_TIMEOUT_MS.toInt()
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Weather returned HTTP ${conn.responseCode}")
                    return@withTimeoutOrNull null
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val current = JSONObject(body).optJSONObject("current_weather")
                    ?: return@withTimeoutOrNull null
                Weather(
                    temperatureC = current.getDouble("temperature"),
                    weatherCode = current.optInt("weathercode", -1),
                )
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed", e)
            null
        }
    }
}
