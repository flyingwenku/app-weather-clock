package com.fred.weatherclock

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.fred.weatherclock.ui.SevenSegmentView
import com.fred.weatherclock.weather.WeatherInfo
import com.fred.weatherclock.weather.WeatherPrefs
import com.fred.weatherclock.weather.WeatherRepository
import com.fred.weatherclock.weather.description
import com.fred.weatherclock.weather.iconRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 桌面天气时钟主界面（横屏 / 全屏 / 常亮）。
 *
 * 三段信息：左=时间，中=室外（网络），右=室内（电池温度代理 + 传感器）。
 */
class MainActivity : Activity() {

    private lateinit var clockView: SevenSegmentView
    private lateinit var ivOutdoorIcon: ImageView
    private lateinit var tvOutdoorTemp: TextView
    private lateinit var tvOutdoorHumidity: TextView
    private lateinit var tvOutdoorDesc: TextView
    private lateinit var ivIndoorIcon: ImageView
    private lateinit var tvIndoorTemp: TextView
    private lateinit var tvIndoorHumidity: TextView
    private lateinit var tvIndoorSource: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvCity: TextView
    private lateinit var tvUpdate: TextView

    private lateinit var prefs: WeatherPrefs
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var lastWeather: WeatherInfo? = null

    private var sensorManager: SensorManager? = null
    private var humiditySensor: Sensor? = null
    private var ambientTempSensor: Sensor? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("M月d日 EEEE", Locale.CHINA)
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, CLOCK_TICK_MS)
        }
    }

    private val batteryTicker = object : Runnable {
        override fun run() {
            updateIndoorTemp()
            handler.postDelayed(this, BATTERY_INTERVAL_MS)
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_RELATIVE_HUMIDITY -> {
                    val value = event.values.getOrNull(0) ?: return
                    tvIndoorHumidity.text = getString(R.string.unit_humidity, value.toInt())
                }

                Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                    // 极少数机型才有真正的环境温度传感器，有则以它为准
                    val value = event.values.getOrNull(0) ?: return
                    tvIndoorTemp.text = formatTemp(value.toDouble())
                    tvIndoorSource.text = getString(R.string.label_indoor)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 常亮：FLAG_KEEP_SCREEN_ON，无需任何权限（项目一已验证）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_clock)
        bindViews()

        prefs = WeatherPrefs(this)
        applyAccentColor()

        tvCity.text = prefs.cityName
        tvCity.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupSensors()
    }

    override fun onResume() {
        super.onResume()
        updateClock()
        updateDate()
        updateIndoorTemp()
        handler.post(ticker)
        handler.postDelayed(batteryTicker, BATTERY_INTERVAL_MS)
        refreshWeather(force = true)
        registerSensors()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(batteryTicker)
        sensorManager?.unregisterListener(sensorListener)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersive()
    }

    private fun bindViews() {
        clockView = findViewById(R.id.clockView)
        ivOutdoorIcon = findViewById(R.id.ivOutdoorIcon)
        tvOutdoorTemp = findViewById(R.id.tvOutdoorTemp)
        tvOutdoorHumidity = findViewById(R.id.tvOutdoorHumidity)
        tvOutdoorDesc = findViewById(R.id.tvOutdoorDesc)
        ivIndoorIcon = findViewById(R.id.ivIndoorIcon)
        tvIndoorTemp = findViewById(R.id.tvIndoorTemp)
        tvIndoorHumidity = findViewById(R.id.tvIndoorHumidity)
        tvIndoorSource = findViewById(R.id.tvIndoorSource)
        tvDate = findViewById(R.id.tvDate)
        tvCity = findViewById(R.id.tvCity)
        tvUpdate = findViewById(R.id.tvUpdate)
    }

    private fun applyAccentColor() {
        val colorRes = if (prefs.colorScheme == 1) R.color.accent_blue else R.color.accent_red
        val color = ContextCompat.getColor(this, colorRes)
        clockView.setColor(color)
        ivOutdoorIcon.setColorFilter(color)
        ivIndoorIcon.setColorFilter(color)
    }

    private fun applyImmersive() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun updateClock() {
        val now = Date()
        val second = now.time / 1000L
        clockView.text = timeFormat.format(now)
        clockView.colonVisible = second % 2L == 0L
    }

    private fun updateDate() {
        tvDate.text = dateFormat.format(Date())
    }

    /** 室内温度：优先环境温度传感器，否则用电池温度（标注来源，不伪造） */
    private fun updateIndoorTemp() {
        if (ambientTempSensor != null) return
        val temp = readBatteryTempCelsius()
        if (temp == null) {
            tvIndoorTemp.text = getString(R.string.na)
        } else {
            tvIndoorTemp.text = formatTemp(temp)
            tvIndoorSource.text = getString(R.string.label_indoor_battery)
        }
    }

    private fun readBatteryTempCelsius(): Double? {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths == null || tenths == Int.MIN_VALUE) return null
        return tenths / 10.0
    }

    private fun refreshWeather(force: Boolean) {
        val last = lastWeather
        if (!force && last != null &&
            System.currentTimeMillis() - last.fetchedAt < WEATHER_INTERVAL_MS
        ) return

        tvOutdoorDesc.text = getString(R.string.weather_updating)
        executor.execute {
            val info = WeatherRepository.fetch(prefs.latitude, prefs.longitude)
            runOnUiThread {
                if (info != null) {
                    lastWeather = info
                    showWeather(info)
                } else if (lastWeather == null) {
                    tvOutdoorDesc.text = getString(R.string.weather_failed)
                }
            }
        }
    }

    private fun showWeather(info: WeatherInfo) {
        tvOutdoorTemp.text = formatTemp(info.temperature)
        tvOutdoorHumidity.text = if (info.humidity != null) {
            getString(R.string.unit_humidity, info.humidity)
        } else {
            getString(R.string.na)
        }
        tvOutdoorDesc.text = info.description()
        ivOutdoorIcon.setImageResource(info.iconRes())
        ivOutdoorIcon.setColorFilter(
            ContextCompat.getColor(
                this,
                if (prefs.colorScheme == 1) R.color.accent_blue else R.color.accent_red
            )
        )
        tvUpdate.text = getString(R.string.updated_at, clockFormat.format(Date(info.fetchedAt)))
    }

    private fun setupSensors() {
        val manager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = manager
        ambientTempSensor = manager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        humiditySensor = manager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        if (humiditySensor == null) {
            tvIndoorHumidity.text = getString(R.string.na)
        }
    }

    private fun registerSensors() {
        val manager = sensorManager ?: return
        humiditySensor?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        ambientTempSensor?.let { manager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    private fun formatTemp(value: Double): String =
        String.format(Locale.getDefault(), "%.1f°C", value)

    companion object {
        private const val CLOCK_TICK_MS = 1000L
        private const val BATTERY_INTERVAL_MS = 30_000L
        private const val WEATHER_INTERVAL_MS = 15 * 60 * 1000L
    }
}
