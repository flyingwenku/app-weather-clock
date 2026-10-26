package com.fred.weatherclock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.fred.weatherclock.weather.WeatherPrefs
import com.fred.weatherclock.weather.WeatherRepository
import java.util.concurrent.Executors

/**
 * 设置：天气数据源 Key + 城市（支持区县）+ 数字颜色（红/蓝）。
 * 不用定位权限——老手机定位慢、权限弹窗烦，直接让用户搜城市更省事。
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: WeatherPrefs
    private val executor = Executors.newSingleThreadExecutor()
    private var pendingPlace: WeatherRepository.Place? = null

    private lateinit var etKey: EditText
    private lateinit var etCity: EditText
    private lateinit var tvSearchResult: TextView
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = WeatherPrefs(this)

        etKey = findViewById(R.id.etKey)
        etCity = findViewById(R.id.etCity)
        tvSearchResult = findViewById(R.id.tvSearchResult)
        btnSave = findViewById(R.id.btnSave)
        val btnSaveKey = findViewById<Button>(R.id.btnSaveKey)
        val btnSearch = findViewById<Button>(R.id.btnSearch)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val rgColor = findViewById<RadioGroup>(R.id.rgColor)

        etKey.setText(prefs.apiKey)
        etCity.setText(prefs.cityName)
        etCity.setSelection(etCity.text.length)
        rgColor.check(if (prefs.colorScheme == 1) R.id.rbBlue else R.id.rbRed)

        btnSaveKey.setOnClickListener {
            prefs.apiKey = etKey.text.toString().trim()
            Toast.makeText(this, R.string.settings_key_saved, Toast.LENGTH_SHORT).show()
        }

        btnSearch.setOnClickListener { searchCity() }

        btnSave.setOnClickListener {
            val place = pendingPlace
            if (place == null) {
                Toast.makeText(this, R.string.settings_search_none, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.cityName = place.name
            prefs.adcode = place.adcode
            prefs.latitude = place.latitude
            prefs.longitude = place.longitude
            finish()
        }

        btnBack.setOnClickListener { finish() }

        rgColor.setOnCheckedChangeListener { _, checkedId ->
            prefs.colorScheme = if (checkedId == R.id.rbBlue) 1 else 0
        }
    }

    private fun searchCity() {
        val keyword = etCity.text.toString().trim()
        if (keyword.isEmpty()) return

        pendingPlace = null
        btnSave.isEnabled = false
        tvSearchResult.text = getString(R.string.weather_updating)

        executor.execute {
            val place = WeatherRepository.searchCity(keyword, prefs.effectiveKey)
            runOnUiThread {
                if (place == null) {
                    tvSearchResult.text = getString(R.string.settings_search_fail)
                } else {
                    pendingPlace = place
                    tvSearchResult.text = getString(R.string.settings_search_found, place.name)
                    btnSave.isEnabled = true
                }
            }
        }
    }
}
