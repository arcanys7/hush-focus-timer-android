package com.arcanys.hush.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcanys.hush.data.SessionEntity
import com.arcanys.hush.timer.TimerState
import com.arcanys.hush.timer.TimerViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(timerViewModel: TimerViewModel) {
    val selectedBlock = timerViewModel.selectedBlock.collectAsState().value
    val blocks = timerViewModel.blocks.collectAsState().value
    val timerText = timerViewModel.remainingTimeMmSs.collectAsState().value
    val selectedMinutes = timerViewModel.selectedDurationMinutes.collectAsState().value
    val timerState = timerViewModel.currentState.collectAsState().value
    val sessionTimeSpentMmSs = timerViewModel.sessionTimeSpentMmSs.collectAsState().value
    val sessionNotes = timerViewModel.sessionNotes.collectAsState().value
    val sessions = timerViewModel.sessions.collectAsState().value
    val remainingMillis = timerViewModel.remainingTimeMillis.collectAsState().value
    val stopwatchText = timerViewModel.stopwatchTimeMmSs.collectAsState().value
    val isStopwatchRunning = timerViewModel.isStopwatchRunning.collectAsState().value

    val isIdle = timerState == TimerState.IDLE
    val isArming = timerState == TimerState.ARMING
    val isPaused = timerState == TimerState.PAUSED
    val isFocusing = timerState == TimerState.FOCUS
    val isStopwatch = timerState == TimerState.STOPWATCH

    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(timerState) {
        if (timerState == TimerState.FOCUS || timerState == TimerState.BREAK) {
            controlsVisible = true
            delay(15000L)
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }

    val rawBackgroundColor = when (timerState) {
        TimerState.FOCUS -> Color(0xFF000000)
        TimerState.BREAK -> Color(0xFF121212)
        TimerState.STOPWATCH -> Color(0xFF000000)
        else -> MaterialTheme.colorScheme.background
    }

    val backgroundColor by animateColorAsState(
        targetValue = rawBackgroundColor,
        animationSpec = tween(durationMillis = 350),
        label = "timer-background"
    )

    val screenAlpha by animateFloatAsState(
        targetValue = if (isArming) 0.88f else 1f,
        animationSpec = tween(durationMillis = 350),
        label = "screen-alpha"
    )

    // Animation for timer size
    val timerScale by animateFloatAsState(
        // Keep time text size consistent before/after start.
        targetValue = if (isIdle || isFocusing || isPaused || isArming || isStopwatch) 1.5f else 1f,
        animationSpec = tween(durationMillis = 500),
        label = "timer-scale"
    )

    // Countdown color logic
    val secondsLeft = (remainingMillis / 1000).toInt()
    val targetCountdownColor = when {
        secondsLeft > 10 -> Color(0xFFFFFFFF)
        secondsLeft == 10 -> Color(0xFFFFE4E1)
        secondsLeft == 9 -> Color(0xFFFFA07A)
        secondsLeft == 8 -> Color(0xFFFF6F61)
        secondsLeft == 7 -> Color(0xFFFF4D4D)
        secondsLeft == 6 -> Color(0xFFFF0000)
        secondsLeft == 5 -> Color(0xFFDC143C)
        secondsLeft == 4 -> Color(0xFFB22222)
        secondsLeft == 3 -> Color(0xFF8B0000)
        secondsLeft == 2 -> Color(0xFF660000)
        secondsLeft == 1 -> Color(0xFF3B0000)
        else -> Color(0xFF1A0000) // 0
    }

    val timerTextColor by animateColorAsState(
        targetValue = if (isStopwatch) Color.White else targetCountdownColor,
        animationSpec = tween(durationMillis = 500),
        label = "timer-text-color"
    )

    var showBlocksSheet by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var isCreatingBlock by rememberSaveable { mutableStateOf(false) }
    var showEmojiPicker by rememberSaveable { mutableStateOf(false) }
    
    var newBlockName by rememberSaveable { mutableStateOf("") }
    var newBlockEmoji by rememberSaveable { mutableStateOf("📁") }
    var selectedNewColorArgb by rememberSaveable { mutableIntStateOf(0xFF00C853.toInt()) }

    val colorOptions = remember {
        listOf(
            0xFF00C853.toInt(), 0xFF1E88E5.toInt(), 0xFFFFB300.toInt(),
            0xFFD81B60.toInt(), 0xFF7E57C2.toInt(), 0xFF26A69A.toInt(),
        )
    }

    val commonEmojis = remember {
        listOf("📁", "📚", "💻", "🧪", "🎨", "🧘", "🏃", "🍎", "🎮", "🎹", "✍️", "⚙️", "🔥", "💡", "🎯")
    }

    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(timerState) {
        if (timerState != TimerState.IDLE && timerState != TimerState.COMPLETED && showBlocksSheet) {
            showBlocksSheet = false
            isCreatingBlock = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                state = rememberDraggableState { delta ->
                    if (isIdle && delta < -20) {
                        timerViewModel.switchToStopwatch()
                    } else if (isStopwatch && delta > 20) {
                        timerViewModel.switchToTimer()
                    }
                },
                orientation = Orientation.Horizontal
            )
            .clickable(
                enabled = isFocusing || isPaused || isStopwatch,
                onClick = {
                    if (isFocusing) timerViewModel.pauseTimer()
                    else if (isPaused) timerViewModel.resumeTimer()
                    else if (isStopwatch) {
                        timerViewModel.toggleStopwatch()
                    }
                },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Scaffold(containerColor = backgroundColor) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .alpha(screenAlpha)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showHistorySheet = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "History")
                    }

                    // Spacer to push Reset/Add buttons to the right
                    Spacer(modifier = Modifier.weight(1f))

                    val showControls = !isIdle && timerState != TimerState.COMPLETED && timerState != TimerState.ARMING
                    if (showControls) {
                        AnimatedVisibility(
                            visible = controlsVisible,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row {
                                if (isPaused) {
                                    IconButton(onClick = { timerViewModel.resumeTimer() }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else if (isFocusing) {
                                    IconButton(onClick = { timerViewModel.pauseTimer() }) {
                                        Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else if (isStopwatch) {
                                    IconButton(onClick = { timerViewModel.toggleStopwatch() }) {
                                        Icon(
                                            if (isStopwatchRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isStopwatchRunning) "Pause" else "Start",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                if (!isStopwatch) {
                                    IconButton(onClick = { timerViewModel.addTime(1) }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add 1 min", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { timerViewModel.resetTimer() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                                    }
                                } else {
                                    IconButton(onClick = { timerViewModel.resetStopwatch() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp)) // Placeholder for balance
                    }
                }

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().scale(timerScale),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AnimatedContent(
                            targetState = timerState,
                            transitionSpec = {
                                if (targetState == TimerState.STOPWATCH && initialState != TimerState.STOPWATCH) {
                                    // Timer -> stopwatch: smaller slide for a smoother handoff.
                                    (slideInVertically(tween(350)) { it / 3 } + fadeIn(tween(250)))
                                        .togetherWith(slideOutVertically(tween(250)) { -it / 6 } + fadeOut(tween(250)))
                                } else if (initialState == TimerState.STOPWATCH && targetState != TimerState.STOPWATCH) {
                                    // Stopwatch -> timer
                                    (slideInVertically(tween(250)) { -it / 6 } + fadeIn(tween(250)))
                                        .togetherWith(slideOutVertically(tween(350)) { it / 3 } + fadeOut(tween(250)))
                                } else if (targetState == TimerState.FOCUS && initialState == TimerState.PAUSED) {
                                    (slideInVertically(tween(400)) { -it } + fadeIn(tween(400)))
                                        .togetherWith(slideOutVertically(tween(400)) { it } + fadeOut(tween(400)))
                                } else if (targetState == TimerState.PAUSED && initialState == TimerState.FOCUS) {
                                    (slideInVertically(tween(400)) { it } + fadeIn(tween(400)))
                                        .togetherWith(slideOutVertically(tween(400)) { -it } + fadeOut(tween(400)))
                                } else {
                                    fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                                }
                            },
                            label = "timer-state-content"
                        ) { state ->
                            Box(contentAlignment = Alignment.Center) {
                                when (state) {
                                    TimerState.ARMING -> {
                                        Text(
                                            text = "Starting...",
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                    }

                                    TimerState.FOCUS, TimerState.PAUSED, TimerState.BREAK -> {
                                        Box(contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (state == TimerState.BREAK) {
                                                    Text(text = "Break", style = MaterialTheme.typography.headlineMedium)
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                }
                                                Text(
                                                    text = timerText,
                                                    style = MaterialTheme.typography.displayLarge.copy(
                                                        fontSize = 80.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = timerTextColor
                                                )
                                                if (state == TimerState.PAUSED) {
                                                    Text(text = "PAUSED", style = MaterialTheme.typography.titleSmall, color = Color.Red)
                                                }
                                            }
                                        }
                                    }

                                    TimerState.STOPWATCH -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = stopwatchText,
                                                style = MaterialTheme.typography.displayLarge.copy(
                                                    fontSize = 80.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color.White
                                            )
                                        }
                                    }

                                    TimerState.COMPLETED -> {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth().scale(1f / timerScale) // Reset scale for save UI
                                        ) {
                                            Text(
                                                text = "Session Complete",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Time spent: $sessionTimeSpentMmSs",
                                                style = MaterialTheme.typography.bodyLarge
                                            )

                                            Spacer(modifier = Modifier.height(20.dp))

                                            Text(text = "Save this session to:", style = MaterialTheme.typography.titleMedium)
                                            Spacer(modifier = Modifier.height(10.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .padding(horizontal = 6.dp)
                                                    .clickable {
                                                        showBlocksSheet = true
                                                        isCreatingBlock = false
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .background(Color(selectedBlock.colorArgb), CircleShape)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "${selectedBlock.emoji} ${selectedBlock.name}",
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            OutlinedTextField(
                                                value = sessionNotes,
                                                onValueChange = { timerViewModel.setSessionNotes(it) },
                                                label = { Text(text = "Notes (optional)") },
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Button(
                                                onClick = { timerViewModel.saveSession() },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(text = "Save Session")
                                            }
                                        }
                                    }

                                    else -> {
                                        Text(
                                            text = timerText,
                                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp),
                                            color = timerTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isIdle || isStopwatch,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
                    exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 2 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isIdle) {
                            Slider(
                                value = selectedMinutes.toFloat(),
                                onValueChange = { timerViewModel.setSelectedDurationMinutes(it.roundToInt()) },
                                valueRange = 1f..120f,
                                steps = 119,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.Black,
                                    activeTrackColor = Color.LightGray,
                                    inactiveTrackColor = Color.LightGray.copy(alpha = 0.5f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (isIdle) "Flip phone to start" else "Flip phone to start stopwatch",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // History Bottom Sheet
        if (showHistorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showHistorySheet = false },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(modifier = Modifier.fillMaxHeight(0.8f).padding(16.dp)) {
                    Text("Session History", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn {
                        items(sessions) { session ->
                            HistorySessionItem(session)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        // Folders Bottom Sheet
        if (showBlocksSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBlocksSheet = false
                    isCreatingBlock = false
                    showEmojiPicker = false
                },
                sheetState = sheetState,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (isCreatingBlock) "New folder" else "Select folder",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isCreatingBlock) {
                        blocks.forEach { block ->
                            val selected = block.name == selectedBlock.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                                    .clickable {
                                        timerViewModel.selectBlock(block)
                                        if (timerState != TimerState.COMPLETED) showBlocksSheet = false
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(16.dp).background(Color(block.colorArgb), CircleShape))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "${block.emoji} ${block.name}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        TextButton(onClick = { isCreatingBlock = true }) {
                            Text(text = "+ Create new folder")
                        }
                    } else {
                        OutlinedTextField(
                            value = newBlockName,
                            onValueChange = { newBlockName = it },
                            label = { Text("Folder Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text("Select Emoji", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .clickable { showEmojiPicker = !showEmojiPicker },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(newBlockEmoji, fontSize = 32.sp)
                        }

                        if (showEmojiPicker) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(5),
                                modifier = Modifier.height(150.dp)
                            ) {
                                items(commonEmojis) { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(40.dp)
                                            .clickable {
                                                newBlockEmoji = emoji
                                                showEmojiPicker = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(emoji, fontSize = 24.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Folder Color", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            colorOptions.forEach { colorArgb ->
                                val selected = selectedNewColorArgb == colorArgb
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(colorArgb), CircleShape)
                                        .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .clickable { selectedNewColorArgb = colorArgb }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row {
                            TextButton(onClick = { isCreatingBlock = false }, modifier = Modifier.weight(1f)) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    timerViewModel.createBlock(newBlockName, newBlockEmoji, selectedNewColorArgb)
                                    isCreatingBlock = false
                                },
                                modifier = Modifier.weight(1f),
                                enabled = newBlockName.isNotBlank()
                            ) {
                                Text("Create")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistorySessionItem(session: SessionEntity) {
    val date = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.timestamp))
    Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(session.blockName, fontWeight = FontWeight.Bold)
            Text("${session.duration} min", style = MaterialTheme.typography.bodySmall)
        }
        Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!session.notes.isNullOrBlank()) {
            Text(session.notes, style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        }
    }
}
