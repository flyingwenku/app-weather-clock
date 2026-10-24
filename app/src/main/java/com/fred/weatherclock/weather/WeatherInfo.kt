package com.fred.weatherclock.weather

import com.fred.weatherclock.R

data class WeatherInfo(
    val temperature: Double,
    val humidity: Int?,
    val code: Int,
    val fetchedAt: Long = System.currentTimeMillis()
)

/** WMO weather_code → 图标 */
fun WeatherInfo.iconRes(): Int = when (code) {
    0, 1 -> R.drawable.ic_sun
    2, 3 -> R.drawable.ic_cloud
    45, 48 -> R.drawable.ic_fog
    in 51..67, 80, 81, 82 -> R.drawable.ic_rain
    in 71..77, 85, 86 -> R.drawable.ic_snow
    95, 96, 99 -> R.drawable.ic_thunder
    else -> R.drawable.ic_cloud
}

/** WMO weather_code → 中文描述（只保留最简词汇，符合"极简"定位） */
fun WeatherInfo.description(): String = when (code) {
    0 -> "晴"
    1 -> "大部晴朗"
    2 -> "局部多云"
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
