package com.arcanys.hush

import android.os.Bundle
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arcanys.hush.ui.MainScreen
import com.arcanys.hush.ui.theme.HushTheme
import com.arcanys.hush.sensors.FlipDetector
import com.arcanys.hush.timer.TimerViewModel
import com.arcanys.hush.timer.TimerState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val timerViewModel: TimerViewModel by viewModels {
        TimerViewModel.Factory(application, (application as HushApplication).repository)
    }

    private var flipDetector: FlipDetector? = null
    private var insetsController: WindowInsetsControllerCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        insetsController = WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        applyImmersiveMode(enabled = true)

        val sensorManager = getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            flipDetector = FlipDetector(
                sensorManager = sensorManager,
                accelerometer = accelerometer,
                scope = lifecycleScope,
                onOrientationChanged = { orientation ->
                    when (orientation) {
                        FlipDetector.Orientation.FACE_DOWN -> {
                            // In timer mode: flip starts focus from IDLE.
                            // In stopwatch mode: flip toggles start/pause.
                            val state = timerViewModel.currentState.value
                            if (state == TimerState.IDLE || state == TimerState.STOPWATCH) {
                                timerViewModel.onValidFlipDetected()
                            }
                        }
                        else -> {}
                    }
                }
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastState: TimerState? = null
                timerViewModel.currentState.collect { state ->
                    val enteredFocus = state == TimerState.FOCUS && lastState != TimerState.FOCUS
                    val enteredBreak = state == TimerState.BREAK && lastState != TimerState.BREAK
                    val enteredCompleted = state == TimerState.COMPLETED && lastState != TimerState.COMPLETED

                    if (enteredFocus) {
                        vibrateFocusStarted()
                        setDoNotDisturbEnabled(enabled = true)
                    }
                    if (enteredBreak || enteredCompleted) {
                        setDoNotDisturbEnabled(enabled = false)
                    }
                    
                    if (enteredCompleted) {
                        vibrateFocusStarted() // Vibrate on completion
                    }

                    lastState = state
                }
            }
        }

        setContent {
            HushTheme {
                MainScreen(timerViewModel = timerViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode(enabled = true)
        flipDetector?.start()
    }

    override fun onPause() {
        flipDetector?.stop()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode(enabled = true)
    }

    private fun applyImmersiveMode(enabled: Boolean) {
        val controller = insetsController ?: return
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun vibrateFocusStarted() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(VibratorManager::class.java)
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator == null || !vibrator.hasVibrator()) return
        val effect = VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(effect)
    }

    private fun setDoNotDisturbEnabled(enabled: Boolean) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_NOTIFICATION_POLICY
        ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            if (enabled) {
                runCatching {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
            return
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        runCatching {
            notificationManager?.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }
    }
}
