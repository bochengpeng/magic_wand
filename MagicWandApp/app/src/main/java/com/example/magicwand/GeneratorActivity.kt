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
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class GeneratorActivity : AppCompatActivity() {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null

    // Debounce/guard
    private var isLaunching: Boolean = false
    private var lastLaunchAt: Long = 0L
    private val minLaunchGapMs: Long = 1500L

    private var isMovieMode: Boolean = true

    private lateinit var shakeListener: ShakeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val genreDropdown = findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.genreDropdown)
        val genres = resources.getStringArray(R.array.genres_display)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, genres)
        genreDropdown.setAdapter(adapter)

        val chipGroup = findViewById<ChipGroup>(R.id.toggleGroup)
        val movie = findViewById<Chip>(R.id.chipMovie)
        isMovieMode = movie.isChecked

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            isMovieMode = checkedIds.contains(R.id.chipMovie)
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        NotificationHelper.ensureChannel(this)

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

        // Allow launching again after returning from ResultsActivity
        isLaunching = false

        // Create the listener here (no self-reference in a property initializer)
        shakeListener = ShakeListener(
            onShake = {
                if (isMovieMode) {
                    handleShake()
                    Haptics.shakePulse(this@GeneratorActivity)
                }
            },
            threshold = 10f,
            cooldownMs = 800L
        )

        accelSensor?.let {
            sensorManager.registerListener(shakeListener, it, SensorManager.SENSOR_DELAY_GAME)
        } ?: run {
            Toast.makeText(this, "No accelerometer on this device.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(shakeListener)
        super.onPause()
    }

    // ---- shake handling ----
    private fun handleShake() {
        val now = System.currentTimeMillis()
        if (isLaunching || now - lastLaunchAt < minLaunchGapMs) return
        isLaunching = true
        lastLaunchAt = now

        lifecycleScope.launch {
            try {
                val randomPage = (1..100).random()
                val resp = ApiModule.tmdb.getPopularMovies(randomPage)
                val movie = resp.results.randomOrNull() ?: run {
                    Toast.makeText(this@GeneratorActivity, "No movie found. Try again.", Toast.LENGTH_SHORT).show()
                    isLaunching = false
                    return@launch
                }

                startActivity(
                    Intent(this@GeneratorActivity, ResultsActivity::class.java)
                        .putExtra(ResultsActivity.EXTRA_MOVIE, movie)
                )
            } catch (e: Exception) {
                Log.e("TMDB", "Error fetching movies", e)
                Toast.makeText(this@GeneratorActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                isLaunching = false
            }
        }
    }

    // Ask for POST_NOTIFICATIONS on Android 13+
    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op */ }

    /** Simple shake detector */
    private class ShakeListener(
        private val onShake: () -> Unit,
        private val threshold: Float = 11f,
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
                onShake()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
}
