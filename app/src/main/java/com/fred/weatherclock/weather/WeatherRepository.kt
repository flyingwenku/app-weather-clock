package com.fred.weatherclock.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 天气数据获取。
 *
 * 数据源策略（两者共一个入口，按是否配置 Key 自动选择）：
 * 1. **配置了高德 Web 服务 Key → 高德**
 *    为什么必须用国内源：Open-Meteo 收录的中文地名常缺行政前缀，
 *    搜「宝安区」会匹配到**重庆市的宝安**（乡镇级），定位直接跑偏。
 *    高德的中文地址解析有省市区分级，且返回的 adcode 可直接查天气。
 * 2. **未配置 Key → Open-Meteo**
 *    境外源，中文区县可能匹配错误，但无需注册即可跑通，作为兜底保留。
 *
 * 换数据源（例如和风）只需改本文件。
 */
object WeatherRepository {

    data class Place(
        val name: String,
        /** 高德行政区划代码；Open-Meteo 结果为空串 */
        val adcode: String,
        val latitude: Double,
        val longitude: Double
    )

    private const val TIMEOUT_MS = 10000

    /**
     * 内置默认 Key：换手机不用再重新配置。用户在设置里填的 Key 优先级更高。
     *
     * ⚠️ 风险：Key 明文打包在 apk 内，反编译即可提取。
     * 自用阶段可接受（高德对个人 Web 服务有免费额度），
     * 但若将来上架，必须在高德控制台给这个 Key 设置每日配额上限，防止被盗刷。
     */
    const val BUILTIN_KEY = "42ccd6af04087854bc5116f4da48e3da"

    /** 搜索城市。有 Key 走高德，无 Key 走 Open-Meteo。 */
    fun searchCity(cityName: String, apiKey: String): Place? =
        if (apiKey.isBlank()) searchByOpenMeteo(cityName) else searchByAmap(cityName, apiKey)

    /**
     * 取当前天气。有 adcode + Key 走高德；
     * 高德失败（Key 失效 / 超额）或未配置 adcode 时，用经纬度退回 Open-Meteo，
     * 保证至少还有温度可用，不会出现"天气一直空白"。
     */
    fun fetch(place: Place, apiKey: String): WeatherInfo? {
        if (place.adcode.isNotEmpty() && apiKey.isNotBlank()) {
            fetchByAmap(place.adcode, apiKey)?.let { return it }
        }
        return fetchByOpenMeteo(place.latitude, place.longitude)
    }

    // ==================== 高德 ====================

    private fun searchByAmap(cityName: String, apiKey: String): Place? {
        // 关键词里带"市"时（如"深圳市宝安区"），用 city 参数限定范围，避免匹配到外省同名地
        val hint = cityHint(cityName)
        var url = "https://restapi.amap.com/v3/geocode/geo?address=" +
                URLEncoder.encode(cityName, "UTF-8") +
                "&key=" + URLEncoder.encode(apiKey, "UTF-8")
        if (hint.isNotEmpty()) {
            url += "&city=" + URLEncoder.encode(hint, "UTF-8")
        }

        val body = httpGet(url) ?: return null
        return try {
            val json = JSONObject(body)
            if (json.optString("status") != "1") return null
            val list = json.optJSONArray("geocodes") ?: return null
            if (list.length() == 0) return null

            // 优先取区县/城市级结果，跳过乡镇/村庄级（防"宝安"匹配到重庆的乡镇）
            var target = list.getJSONObject(0)
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val level = item.optString("level", "")
                if (level.contains("区县") || level.contains("城市") || level.contains("省会")) {
                    target = item
                    break
                }
            }

            val district = target.optString("district", "")
            val city = target.optString("city", "")
            val name = when {
                district.isNotEmpty() && city.isNotEmpty() -> "$city$district"
                district.isNotEmpty() -> district
                city.isNotEmpty() -> city
                else -> target.optString("formatted_address", cityName)
            }

            val parts = target.optString("location", "").split(",")
            Place(
                name = name,
                adcode = target.optString("adcode", ""),
                latitude = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
                longitude = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchByAmap(adcode: String, apiKey: String): WeatherInfo? {
        val url = "https://restapi.amap.com/v3/weather/weatherInfo?city=" +
                URLEncoder.encode(adcode, "UTF-8") +
                "&key=" + URLEncoder.encode(apiKey, "UTF-8") +
                "&extensions=base"
        val body = httpGet(url) ?: return null
        return try {
            val json = JSONObject(body)
            if (json.optString("status") != "1") return null
            val lives = json.optJSONArray("lives") ?: return null
            if (lives.length() == 0) return null

            val live = lives.getJSONObject(0)
            val temp = live.optString("temperature").toDoubleOrNull() ?: return null
            WeatherInfo(
                temperature = temp,
                humidity = live.optString("humidity").toIntOrNull(),
                text = live.optString("weather", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 从"深圳市宝安区"这类全称里提取城市限定词"深圳市" */
    private fun cityHint(name: String): String {
        val idx = name.indexOf('市')
        return if (idx > 0) name.substring(0, idx + 1) else ""
    }

    // ============ Open-Meteo（未配置 Key 时的兜底） ============

    private fun searchByOpenMeteo(cityName: String): Place? {
        for (keyword in searchCandidates(cityName)) {
            val place = queryOpenMeteo(keyword)
            if (place != null) return place
        }
        return null
    }

    private fun queryOpenMeteo(keyword: String): Place? {
        val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                URLEncoder.encode(keyword, "UTF-8") + "&count=1&language=zh"
        val body = httpGet(url) ?: return null
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            val name = first.optString("name", keyword)
            val admin2 = first.optString("admin2", "")
            val display = if (admin2.isNotEmpty() && admin2 != name) "$admin2$name" else name
            Place(display, "", first.getDouble("latitude"), first.getDouble("longitude"))
        } catch (e: Exception) {
            null
        }
    }

    /** 逐级剥离行政后缀，生成候选搜索词（"深圳市南山区" → "深圳市南山" → "南山"） */
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

        val idx = maxOf(s.lastIndexOf('市'), s.lastIndexOf('省'))
        if (idx >= 0 && idx < s.length - 1) add(s.substring(idx + 1))

        return candidates
    }

    private fun fetchByOpenMeteo(latitude: Double, longitude: Double): WeatherInfo? {
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
                text = wmoText(current.optInt("weather_code", 3))
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 公共 ====================

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
