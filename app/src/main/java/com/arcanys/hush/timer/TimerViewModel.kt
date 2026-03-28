package com.arcanys.hush.timer

import android.media.Ringtone
import android.media.RingtoneManager
import android.media.AudioAttributes
import android.net.Uri
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arcanys.hush.data.SessionEntity
import com.arcanys.hush.data.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import android.os.SystemClock
import java.util.concurrent.TimeUnit

class TimerViewModel(
    application: Application,
    private val repository: SessionRepository
) : AndroidViewModel(application) {

    private val _currentState = MutableStateFlow(TimerState.IDLE)
    val currentState: StateFlow<TimerState> = _currentState.asStateFlow()

    private val _selectedDurationMinutes = MutableStateFlow(25)
    val selectedDurationMinutes: StateFlow<Int> = _selectedDurationMinutes.asStateFlow()

    private val _remainingTimeMillis = MutableStateFlow(25 * 60_000L)
    val remainingTimeMillis: StateFlow<Long> = _remainingTimeMillis.asStateFlow()

    private val _remainingTimeMmSs = MutableStateFlow(formatMmSs(25 * 60_000L))
    val remainingTimeMmSs: StateFlow<String> = _remainingTimeMmSs.asStateFlow()

    private val _stopwatchTimeMillis = MutableStateFlow(0L)
    val stopwatchTimeMillis: StateFlow<Long> = _stopwatchTimeMillis.asStateFlow()

    private val _stopwatchTimeMmSs = MutableStateFlow(formatMmSs(0L))
    val stopwatchTimeMmSs: StateFlow<String> = _stopwatchTimeMmSs.asStateFlow()

    private val _isStopwatchRunning = MutableStateFlow(false)
    val isStopwatchRunning: StateFlow<Boolean> = _isStopwatchRunning.asStateFlow()

    private val _stopwatchAccumulatedMillis = MutableStateFlow(0L)
    private val _stopwatchStartedAtElapsedMillis = MutableStateFlow(0L)

    private val defaultBlocks = listOf(
        Block(name = "Science", colorArgb = 0xFF1E88E5.toInt(), emoji = "🧪"),
        Block(name = "Reading", colorArgb = 0xFFFFB300.toInt(), emoji = "📚"),
        Block(name = "Coding", colorArgb = 0xFF7E57C2.toInt(), emoji = "💻"),
    )

    private val _blocks = MutableStateFlow(defaultBlocks)
    val blocks: StateFlow<List<Block>> = _blocks.asStateFlow()

    private val _selectedBlock = MutableStateFlow(defaultBlocks.first())
    val selectedBlock: StateFlow<Block> = _selectedBlock.asStateFlow()

    private val _selectedBlockName = MutableStateFlow(_selectedBlock.value.name)
    val selectedBlockName: StateFlow<String> = _selectedBlockName.asStateFlow()

    private val _lastValidFlipAtMillis = MutableStateFlow(0L)
    val lastValidFlipAtMillis: StateFlow<Long> = _lastValidFlipAtMillis.asStateFlow()

    private var timerJob: Job? = null
    private var armingJob: Job? = null
    private var stopwatchJob: Job? = null

    private val _sessionDurationMinutes = MutableStateFlow(25)
    val sessionDurationMinutes: StateFlow<Int> = _sessionDurationMinutes.asStateFlow()

    private val _sessionTimeSpentMmSs = MutableStateFlow(formatMmSs(25 * 60_000L))
    val sessionTimeSpentMmSs: StateFlow<String> = _sessionTimeSpentMmSs.asStateFlow()

    private val _sessionTimestampMillis = MutableStateFlow(0L)
    val sessionTimestampMillis: StateFlow<Long> = _sessionTimestampMillis.asStateFlow()

    private val _sessionNotes = MutableStateFlow("")
    val sessionNotes: StateFlow<String> = _sessionNotes.asStateFlow()

    private var ringtone: Ringtone? = null

    val sessions: StateFlow<List<SessionEntity>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedDurationMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(1, 120) 
        _selectedDurationMinutes.value = clamped
        _remainingTimeMillis.value = clamped * 60_000L
        _remainingTimeMmSs.value = formatMmSs(_remainingTimeMillis.value)
    }

    fun setSelectedBlockName(name: String) {
        val matched = _blocks.value.firstOrNull { it.name == name }
        if (matched != null) {
            selectBlock(matched)
        } else {
            _selectedBlockName.value = name
        }
    }

    fun selectBlock(block: Block) {
        _selectedBlock.value = block
        _selectedBlockName.value = block.name
    }

    fun createBlock(name: String, emoji: String, colorArgb: Int) {
        val cleanName = name.trim()
        val cleanEmoji = emoji.trim().take(4)
        if (cleanName.isEmpty() || cleanEmoji.isEmpty()) return

        val existingIndex = _blocks.value.indexOfFirst { it.name == cleanName }
        val updatedBlocks = if (existingIndex >= 0) {
            _blocks.value.toMutableList().apply {
                this[existingIndex] = Block(cleanName, colorArgb, cleanEmoji)
            }
        } else {
            _blocks.value + Block(cleanName, colorArgb, cleanEmoji)
        }

        _blocks.value = updatedBlocks
        selectBlock(updatedBlocks.first { it.name == cleanName })
    }

    fun setCurrentState(state: TimerState) {
        _currentState.value = state
        if (state == TimerState.IDLE) {
            stopRingtone()
        }
    }

    fun setSessionNotes(notes: String) {
        _sessionNotes.value = notes
    }

    fun onValidFlipDetected() {
        val state = _currentState.value
        if (state == TimerState.IDLE) {
            // Start timer
            _lastValidFlipAtMillis.value = System.currentTimeMillis()
            armingJob?.cancel()
            armingJob = viewModelScope.launch {
                _currentState.value = TimerState.ARMING
                delay(1_000L)
                if (_currentState.value != TimerState.ARMING) return@launch
                startTimer()
            }
        } else if (state == TimerState.STOPWATCH) {
            // Flip starts the stopwatch, but should not pause it.
            if (!_isStopwatchRunning.value) startStopwatch()
        }
    }

    fun startTimer() {
        stopRingtone()
        timerJob?.cancel()
        _currentState.value = TimerState.FOCUS
        
        val focusMinutes = _selectedDurationMinutes.value
        _sessionDurationMinutes.value = focusMinutes
        _remainingTimeMillis.value = focusMinutes * 60_000L
        _remainingTimeMmSs.value = formatMmSs(_remainingTimeMillis.value)
        _sessionNotes.value = ""

        timerJob = viewModelScope.launch {
            runCountdownLoop()
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
        _currentState.value = TimerState.PAUSED
    }

    fun resumeTimer() {
        if (_currentState.value != TimerState.PAUSED) return
        _currentState.value = TimerState.FOCUS 
        timerJob = viewModelScope.launch {
            runCountdownLoop()
        }
    }

    fun addTime(minutes: Int) {
        val addedMillis = minutes * 60_000L
        _remainingTimeMillis.value += addedMillis
        _remainingTimeMmSs.value = formatMmSs(_remainingTimeMillis.value)
    }

    fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        armingJob?.cancel()
        armingJob = null
    }

    fun resetTimer() {
        stopTimer()
        stopRingtone()
        _currentState.value = TimerState.IDLE
        setSelectedDurationMinutes(_selectedDurationMinutes.value)
    }

    fun switchToStopwatch() {
        if (_currentState.value == TimerState.IDLE) {
            _currentState.value = TimerState.STOPWATCH
            resetStopwatch()
        }
    }

    fun switchToTimer() {
        if (_currentState.value == TimerState.STOPWATCH) {
            stopStopwatch()
            _currentState.value = TimerState.IDLE
        }
    }

    fun startStopwatch() {
        if (_isStopwatchRunning.value) return
        stopwatchJob?.cancel()
        _isStopwatchRunning.value = true
        _stopwatchStartedAtElapsedMillis.value = SystemClock.elapsedRealtime()
        stopwatchJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = currentStopwatchElapsedMillis()
                _stopwatchTimeMillis.value = elapsed
                _stopwatchTimeMmSs.value = formatMmSsMs(elapsed)

                // Update frequently enough to make milliseconds visible.
                delay(50L)
            }
        }
    }

    fun stopStopwatch() {
        if (!_isStopwatchRunning.value) {
            stopwatchJob?.cancel()
            stopwatchJob = null
            return
        }

        val now = SystemClock.elapsedRealtime()
        val startedAt = _stopwatchStartedAtElapsedMillis.value
        if (startedAt > 0L) {
            _stopwatchAccumulatedMillis.value += (now - startedAt).coerceAtLeast(0L)
        }
        _stopwatchStartedAtElapsedMillis.value = 0L
        _isStopwatchRunning.value = false

        stopwatchJob?.cancel()
        stopwatchJob = null

        val elapsed = currentStopwatchElapsedMillis()
        _stopwatchTimeMillis.value = elapsed
        _stopwatchTimeMmSs.value = formatMmSsMs(elapsed)
    }

    fun resetStopwatch() {
        stopStopwatch()
        _stopwatchAccumulatedMillis.value = 0L
        _stopwatchStartedAtElapsedMillis.value = 0L
        _isStopwatchRunning.value = false
        _stopwatchTimeMillis.value = 0L
        _stopwatchTimeMmSs.value = formatMmSsMs(0L)
    }

    fun toggleStopwatch() {
        if (_currentState.value != TimerState.STOPWATCH) return
        if (_isStopwatchRunning.value) stopStopwatch() else startStopwatch()
    }

    private fun currentStopwatchElapsedMillis(): Long {
        val base = _stopwatchAccumulatedMillis.value
        val startedAt = _stopwatchStartedAtElapsedMillis.value
        if (!_isStopwatchRunning.value || startedAt <= 0L) return base.coerceAtLeast(0L)
        val now = SystemClock.elapsedRealtime()
        return (base + (now - startedAt)).coerceAtLeast(0L)
    }

    private suspend fun runCountdownLoop() {
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            delay(1_000L)

            val next = (_remainingTimeMillis.value - 1_000L).coerceAtLeast(0L)
            _remainingTimeMillis.value = next
            _remainingTimeMmSs.value = formatMmSs(next)

            if (next <= 0L) {
                onTimerFinished()
                return
            }
        }
    }

    private fun onTimerFinished() {
        _sessionTimestampMillis.value = System.currentTimeMillis()
        _sessionTimeSpentMmSs.value = formatMmSs(_sessionDurationMinutes.value * 60_000L)
        _currentState.value = TimerState.COMPLETED
        vibrate(500L)
        playRingtone()
        stopTimer()
    }

    fun vibrate(durationMillis: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getApplication<Application>().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(effect)
        }
    }

    private fun playRingtone() {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            ringtone = RingtoneManager.getRingtone(getApplication(), alarmUri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRingtone() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        ringtone = null
    }

    private fun formatMmSs(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis).toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    private fun formatMmSsMs(millis: Long): String {
        val safeMillis = millis.coerceAtLeast(0L)
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(safeMillis).toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val ms = (safeMillis % 1000L).toInt()
        // Format: 01:23.456
        return "%02d:%02d.%03d".format(minutes, seconds, ms)
    }

    fun saveSession() {
        if (_currentState.value != TimerState.COMPLETED) return

        val now = System.currentTimeMillis()
        val timestampMillis = if (_sessionTimestampMillis.value != 0L) _sessionTimestampMillis.value else now

        val sessionEntity = SessionEntity(
            blockName = _selectedBlockName.value,
            duration = _sessionDurationMinutes.value,
            timestamp = timestampMillis,
            notes = _sessionNotes.value.takeIf { it.isNotBlank() }
        )

        viewModelScope.launch {
            repository.insert(sessionEntity)
        }

        _sessionNotes.value = ""
        _sessionTimestampMillis.value = 0L

        stopTimer()
        stopRingtone()
        setCurrentState(TimerState.IDLE)
        setSelectedDurationMinutes(_selectedDurationMinutes.value)
    }

    class Factory(
        private val application: Application,
        private val repository: SessionRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TimerViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
