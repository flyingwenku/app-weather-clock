package com.fred.weatherclock.weather

import com.fred.weatherclock.R

data class WeatherInfo(
    val temperature: Double,
    val humidity: Int?,
    /** 中文天气现象，如「晴」「多云」「小雨」。高德直接返回，无需再映射编码 */
    val text: String,
    val fetchedAt: Long = System.currentTimeMillis()
)

/**
 * 中文天气现象 → 图标。
 * 判断顺序由特殊到一般：「雷雨」归雷，「雨夹雪」归雪。
 */
fun WeatherInfo.iconRes(): Int = when {
    text.contains("雷") -> R.drawable.ic_thunder
    text.contains("雪") -> R.drawable.ic_snow
    text.contains("雨") -> R.drawable.ic_rain
    text.contains("雾") || text.contains("霾") || text.contains("尘") || text.contains("沙") ->
        R.drawable.ic_fog

    text.contains("晴") -> R.drawable.ic_sun
    else -> R.drawable.ic_cloud
}

/** WMO weather_code → 中文（仅 Open-Meteo 兜底时用） */
fun wmoText(code: Int): String = when (code) {
    0 -> "晴"
    1 -> "大部晴朗"
    2 -> "多云"
    3 -> "阴"
    45, 48 -> "雾"
    in 51..57 -> "毛毛雨"
    in 61..67 -> "雨"
    71, 73, 75, 77 -> "雪"
    80, 81, 82 -> "阵雨"
    85, 86 -> "阵雪"
    95 -> "雷雨"
    96, 99 -> "雷暴"
    else -> "多云"
}
