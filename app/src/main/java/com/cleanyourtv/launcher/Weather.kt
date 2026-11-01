package com.cleanyourtv.launcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class WeatherState(
    val tempC: Int,
    val label: String,
    val sunrise: String,
    val sunset: String,
)

/** Current weather from Open-Meteo (free, no API key). Returns null if the request fails. */
suspend fun fetchWeather(): WeatherState? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${Config.LATITUDE}&longitude=${Config.LONGITUDE}" +
                "&current=temperature_2m,weather_code&daily=sunrise,sunset" +
                "&timezone=auto&forecast_days=1"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        WeatherState(
            tempC = Math.round(current.getDouble("temperature_2m")).toInt(),
            label = weatherLabel(current.getInt("weather_code")),
            sunrise = to12h(daily.getJSONArray("sunrise").getString(0)),
            sunset = to12h(daily.getJSONArray("sunset").getString(0)),
        )
    }.getOrNull()
}

private fun to12h(isoLocal: String): String {
    val hm = isoLocal.substringAfter('T')
    return runCatching {
        val parsed = SimpleDateFormat("HH:mm", Locale.US).parse(hm)
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(parsed!!)
    }.getOrDefault(hm)
}

private fun weatherLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    in 95..99 -> "Thunderstorm"
    else -> "Weather"
}
