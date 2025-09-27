package com.example.magicwand

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class GeneratorActivity : AppCompatActivity() {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null

    // Easier to trigger: threshold 10f, faster sampling
    private val shakeListener = ShakeListener(
        onShake = {
            lifecycleScope.launch {
                try {
                    val resp = ApiModule.tmdb.getPopularMovies()
                    val movie = resp.results.randomOrNull() ?: return@launch

                    val i = Intent(this@GeneratorActivity, ResultsActivity::class.java)
                    i.putExtra(ResultsActivity.EXTRA_MOVIE, movie)
                    startActivity(i)
                } catch (e: Exception) {
                    Log.e("TMDB", "Error fetching movies", e)
                }
            }
        },
        threshold = 10f,
        cooldownMs = 800L
    )

    // Ask for POST_NOTIFICATIONS on Android 13+
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: we’ll just skip if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Notification channel
        NotificationHelper.ensureChannel(this)

        // Request permission if needed
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        Log.d("TMDB", "Key length = ${BuildConfig.TMDB_ACCESS_TOKEN.length}")
    }

    override fun onResume() {
        super.onResume()
        accelSensor?.let {
            // More responsive than SENSOR_DELAY_UI
            sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run {
            Toast.makeText(this, "No accelerometer on this device.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(shakeListener)
        super.onPause()
    }

    /** Simple shake detector */
    private class ShakeListener(
        private val onShake: () -> Unit,
        private val threshold: Float = 12f,
        private val cooldownMs: Long = 1000L
    ) : SensorEventListener {

        private var lastMag = 9.81f
        private var lastTs = 0L

        override fun onSensorChanged(event: SensorEvent) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]
            val mag = sqrt(ax * ax + ay * ay + az * az)
            val delta = kotlin.math.abs(mag - lastMag)
            lastMag = mag

            val now = System.currentTimeMillis()
            if (delta > threshold && now - lastTs > cooldownMs) {
                lastTs = now
                onShake()   // <-- calls lambda
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}
