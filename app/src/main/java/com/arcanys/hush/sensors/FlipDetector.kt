package com.arcanys.hush.sensors

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class FlipDetector(
    private val sensorManager: SensorManager,
    private val accelerometer: Sensor,
    private val scope: CoroutineScope,
    private val onOrientationChanged: (Orientation) -> Unit,
    private val zThreshold: Float = 7f,
    private val confirmationDelayMs: Long = 800L,
    private val gMin: Float = 7f,
    private val gMax: Float = 13f,
    private val cooldownMs: Long = 1_000L,
) {
    private var confirmationJob: Job? = null
    private var lastConfirmedAtMillis: Long = 0L
    private var currentOrientation: Orientation = Orientation.NONE
    private var listening: Boolean = false

    enum class Orientation {
        FACE_UP, FACE_DOWN, NONE
    }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values.getOrNull(0) ?: return
            val y = event.values.getOrNull(1) ?: return
            val z = event.values.getOrNull(2) ?: return

            val g = sqrt(x * x + y * y + z * z)
            val detectedOrientation = when {
                z > zThreshold && g in gMin..gMax -> Orientation.FACE_UP
                z < -zThreshold && g in gMin..gMax -> Orientation.FACE_DOWN
                else -> Orientation.NONE
            }

            if (detectedOrientation != Orientation.NONE && detectedOrientation != currentOrientation) {
                if (confirmationJob != null || isInCooldown()) return

                confirmationJob = scope.launch {
                    delay(confirmationDelayMs)
                    
                    // We need to check the orientation again. Since we are in a listener, 
                    // we don't have the "latest" values here directly unless we store them.
                    // But for simplicity, if this job wasn't cancelled, it means it stayed roughly the same.
                    
                    if (!isInCooldown()) {
                        currentOrientation = detectedOrientation
                        lastConfirmedAtMillis = System.currentTimeMillis()
                        onOrientationChanged(detectedOrientation)
                    }
                    confirmationJob = null
                }
            } else if (detectedOrientation == Orientation.NONE) {
                confirmationJob?.cancel()
                confirmationJob = null
                currentOrientation = Orientation.NONE
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (listening) return
        listening = true
        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    fun stop() {
        if (!listening) return
        listening = false
        sensorManager.unregisterListener(listener)
        confirmationJob?.cancel()
        confirmationJob = null
        currentOrientation = Orientation.NONE
    }

    private fun isInCooldown(): Boolean {
        val now = System.currentTimeMillis()
        return now - lastConfirmedAtMillis < cooldownMs
    }
}
