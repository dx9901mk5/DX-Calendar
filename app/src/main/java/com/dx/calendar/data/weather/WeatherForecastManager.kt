package com.dx.calendar.data.weather

import android.content.Context
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

data class DailyWeatherInfo(
    val date: LocalDate,
    val weatherCode: Int,
    val icon: String,
    val label: String,
    val tempMaxC: Int,
    val tempMinC: Int
)

/**
 * 預設 7 天天氣預報引擎（自今日起連續 7 天，免 API Key，Open-Meteo 全球直連＋本機 3 小時快取）。
 * 依系統時區自動對應預設城市座標，無須強迫使用者彈出定位權限即可即時在月曆上呈現天氣圖示。
 */
object WeatherForecastManager {

    private const val PREF_NAME = "dx_weather_forecast_cache_v1"
    private const val KEY_JSON = "cached_json"
    private const val KEY_FETCHED_AT = "fetched_at_ms"
    private const val KEY_ZONE_ID = "zone_id"
    private const val CACHE_TTL_MS = 3L * 60L * 60L * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun loadCachedForecast(context: Context, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, DailyWeatherInfo> {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyMap()
        return parseForecastJson(raw, LocalDate.now(zone))
    }

    suspend fun fetchSevenDayForecast(
        context: Context,
        zone: ZoneId = ZoneId.systemDefault(),
        forceRefresh: Boolean = false
    ): Map<LocalDate, DailyWeatherInfo> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val cachedJson = prefs.getString(KEY_JSON, null)
        val cachedAt = prefs.getLong(KEY_FETCHED_AT, 0L)
        val cachedZone = prefs.getString(KEY_ZONE_ID, null)

        if (!forceRefresh && cachedJson != null && cachedZone == zone.id && (now - cachedAt) < CACHE_TTL_MS) {
            val parsed = parseForecastJson(cachedJson, today)
            if (parsed.isNotEmpty()) return@withContext parsed
        }

        val (lat, lon) = resolveCoordinatesForZone(zone.id)
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&forecast_days=7&timezone=auto"

        val fresh = runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "DX-Calendar/1.9")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string()
            }
        }.getOrNull()

        if (!fresh.isNullOrBlank()) {
            val parsed = parseForecastJson(fresh, today)
            if (parsed.isNotEmpty()) {
                prefs.edit()
                    .putString(KEY_JSON, fresh)
                    .putLong(KEY_FETCHED_AT, now)
                    .putString(KEY_ZONE_ID, zone.id)
                    .apply()
                return@withContext parsed
            }
        }

        // 網路暫時不可用時退回本機快取
        if (!cachedJson.isNullOrBlank()) {
            return@withContext parseForecastJson(cachedJson, today)
        }
        emptyMap()
    }

    internal fun parseForecastJson(rawJson: String, today: LocalDate): Map<LocalDate, DailyWeatherInfo> {
        return runCatching {
            val root = JsonParser.parseString(rawJson).asJsonObject
            val daily = root.getAsJsonObject("daily") ?: return emptyMap()
            val times = daily.getAsJsonArray("time") ?: return emptyMap()
            val codes = daily.getAsJsonArray("weather_code") ?: return emptyMap()
            val maxs = daily.getAsJsonArray("temperature_2m_max") ?: return emptyMap()
            val mins = daily.getAsJsonArray("temperature_2m_min") ?: return emptyMap()

            val endDay = today.plusDays(6)
            val result = LinkedHashMap<LocalDate, DailyWeatherInfo>(7)
            val count = minOf(times.size(), codes.size(), maxs.size(), mins.size())
            for (i in 0 until count) {
                val date = runCatching { LocalDate.parse(times[i].asString) }.getOrNull() ?: continue
                if (date.isBefore(today) || date.isAfter(endDay)) continue
                val code = codes[i].asInt
                val (icon, label) = mapWmoWeatherCode(code)
                result[date] = DailyWeatherInfo(
                    date = date,
                    weatherCode = code,
                    icon = icon,
                    label = label,
                    tempMaxC = Math.round(maxs[i].asDouble).toInt(),
                    tempMinC = Math.round(mins[i].asDouble).toInt()
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    internal fun mapWmoWeatherCode(code: Int): Pair<String, String> = when (code) {
        0 -> "☀️" to "晴朗"
        1 -> "🌤️" to "大致晴朗"
        2 -> "⛅" to "多雲時晴"
        3 -> "☁️" to "陰天"
        45, 48 -> "☁️" to "有霧"
        51, 53, 55, 56, 57 -> "🌦️" to "毛毛雨"
        61, 63, 65, 66, 67 -> "🌧️" to "降雨"
        71, 73, 75, 77, 85, 86 -> "❄️" to "降雪"
        80, 81, 82 -> "🌦️" to "陣雨"
        95, 96, 99 -> "⛈️" to "雷陣雨"
        else -> "🌤️" to "多雲"
    }

    private fun resolveCoordinatesForZone(zoneId: String): Pair<Double, Double> = when (zoneId) {
        "Asia/Taipei" -> 25.0330 to 121.5654
        "Asia/Hong_Kong", "Asia/Macau" -> 22.3193 to 114.1694
        "Asia/Shanghai", "Asia/Chongqing" -> 31.2304 to 121.4737
        "Asia/Tokyo" -> 35.6762 to 139.6503
        "Asia/Seoul" -> 37.5665 to 126.9780
        "Asia/Singapore" -> 1.3521 to 103.8198
        "America/Los_Angeles" -> 34.0522 to -118.2437
        "America/New_York" -> 40.7128 to -74.0060
        "Europe/London" -> 51.5074 to -0.1278
        "Australia/Sydney" -> -33.8688 to 151.2093
        else -> 25.0330 to 121.5654
    }
}
