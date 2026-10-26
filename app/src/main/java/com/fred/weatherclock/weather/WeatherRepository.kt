package com.fred.weatherclock.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 天气数据获取 —— Open-Meteo（**免 API Key**）。
 *
 * 选它的理由：不需要注册账号、不需要保管密钥，先把链路跑通。
 * 换国内数据源（和风 / 高德）时只需改这个文件。
 */
object WeatherRepository {

    data class Place(val name: String, val latitude: Double, val longitude: Double)

    private const val TIMEOUT_MS = 10000

    /**
     * 城市名 → 经纬度。无结果或失败返回 null。
     *
     * 中文地名带行政后缀时 Open-Meteo 常匹配不到（"南山区" 实际要搜 "南山"），
     * 因此逐级降级重试：深圳市南山区 → 深圳市南山 → 南山。
     */
    fun searchCity(cityName: String): Place? {
        for (keyword in searchCandidates(cityName)) {
            val place = query(keyword)
            if (place != null) return place
        }
        return null
    }

    private fun query(keyword: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                URLEncoder.encode(keyword, "UTF-8") + "&count=1&language=zh"
        val body = httpGet(url) ?: return null
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            val name = first.optString("name", keyword)
            // 区县查询（如"南山"）时拼上地级市，显示为"深圳市南山"
            val admin2 = first.optString("admin2", "")
            val display = if (admin2.isNotEmpty() && admin2 != name) "$admin2$name" else name
            Place(display, first.getDouble("latitude"), first.getDouble("longitude"))
        } catch (e: Exception) {
            null
        }
    }

    /** 逐级剥离行政后缀，生成候选搜索词 */
    private fun searchCandidates(name: String): List<String> {
        val candidates = ArrayList<String>()

        fun add(value: String) {
            val v = value.trim()
            if (v.isNotEmpty() && !candidates.contains(v)) candidates.add(v)
        }

        add(name)

        var s = name.trim()
        while (s.length > 2 && (s.endsWith("区") || s.endsWith("市") ||
                    s.endsWith("县") || s.endsWith("省") || s.endsWith("镇"))
        ) {
            s = s.dropLast(1)
            add(s)
        }

        // 再去掉地级市/省份前缀：深圳市南山 → 南山
        val idx = maxOf(s.lastIndexOf('市'), s.lastIndexOf('省'))
        if (idx >= 0 && idx < s.length - 1) add(s.substring(idx + 1))

        return candidates
    }

    /** 当前天气。失败返回 null（调用方据此显示 "--"）。 */
    fun fetch(latitude: Double, longitude: Double): WeatherInfo? {
        val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m,weather_code&timezone=auto"
        val body = httpGet(url) ?: return null
        return try {
            val current = JSONObject(body).getJSONObject("current")
            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (temp.isNaN()) return null
            val humidity = if (current.has("relative_humidity_2m")) {
                current.optInt("relative_humidity_2m", -1).takeIf { it >= 0 }
            } else null
            WeatherInfo(
                temperature = temp,
                humidity = humidity,
                code = current.optInt("weather_code", 3)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "app-weather-clock/1.0")
            val code = conn.responseCode
            if (code < 200 || code > 299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
