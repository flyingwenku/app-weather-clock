package com.fred.weatherclock.weather

import android.content.Context

/** 城市与配色的持久化（SharedPreferences） */
class WeatherPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    var cityName: String
        get() = prefs.getString(KEY_CITY, DEFAULT_CITY) ?: DEFAULT_CITY
        set(value) {
            prefs.edit().putString(KEY_CITY, value).apply()
        }

    var latitude: Double
        get() = prefs.getFloat(KEY_LAT, DEFAULT_LAT.toFloat()).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_LAT, value.toFloat()).apply()
        }

    var longitude: Double
        get() = prefs.getFloat(KEY_LON, DEFAULT_LON.toFloat()).toDouble()
        set(value) {
            prefs.edit().putFloat(KEY_LON, value.toFloat()).apply()
        }

    /** 0 = 红，1 = 蓝 */
    var colorScheme: Int
        get() = prefs.getInt(KEY_COLOR, 0)
        set(value) {
            prefs.edit().putInt(KEY_COLOR, value).apply()
        }

    companion object {
        private const val PREF_NAME = "weather_clock"
        private const val KEY_CITY = "city_name"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_COLOR = "color"

        const val DEFAULT_CITY = "深圳市南山区"
        const val DEFAULT_LAT = 22.5328
        const val DEFAULT_LON = 113.9285
    }
}
