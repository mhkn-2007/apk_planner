package com.example.lifeos.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.dao.FocusSessionDao
import com.example.lifeos.data.database.dao.TaskDao
import com.example.lifeos.data.database.entities.FocusSessionEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import com.example.lifeos.util.AlarmScheduler
import com.example.lifeos.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Focus Mode (prompt section 18): Pomodoro-style work/break timer with
 * session history, optionally linked to a task so completed focus time
 * shows up against that task ("Start Focus" from a task, section 66/step 8).
 *
 * The countdown itself runs as a coroutine tick while this screen/ViewModel
 * is alive (consistent with how the rest of the app keeps live state in the
 * ViewModel, e.g. RoutinesViewModel). To make sure the user is still
 * notified when the session ends even if they've left the app or turned
 * the screen off, an exact alarm mirroring the same deadline is scheduled
 * through [AlarmScheduler] (the same mechanism already used for task
 * reminders) and cancelled if the session is stopped early or the app is
 * in the foreground to observe completion itself.
 */
enum class FocusSessionType { WORK, SHORT_BREAK, LONG_BREAK }

data class FocusPreset(
    val workMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val sessionsBeforeLongBreak: Int = 4
)

val defaultFocusPreset = FocusPreset(workMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 15)

@HiltViewModel
class FocusViewModel @Inject constructor(
    private val focusSessionDao: FocusSessionDao,
    private val taskDao: TaskDao,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val recentSessions: StateFlow<List<FocusSessionEntity>> =
        focusSessionDao.getAllSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incompleteTasks: StateFlow<List<TaskEntity>> =
        taskDao.getAllTasks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var preset by mutableStateOf(defaultFocusPreset)
        private set

    var sessionType by mutableStateOf(FocusSessionType.WORK)
        private set

    var selectedTask by mutableStateOf<TaskEntity?>(null)
        private set

    var isRunning by mutableStateOf(false)
        private set

    var totalSeconds by mutableStateOf(defaultFocusPreset.workMinutes * 60)
        private set

    var remainingSeconds by mutableStateOf(defaultFocusPreset.workMinutes * 60)
        private set

    private var completedWorkSessionsInCycle = 0
    private var currentSessionId: String? = null
    private var currentSessionStartMillis: Long = 0L
    private var tickJob: Job? = null

    fun updatePreset(newPreset: FocusPreset) {
        if (isRunning) return
        preset = newPreset
        setDuration(minutesFor(sessionType))
    }

    fun selectTask(task: TaskEntity?) {
        if (isRunning) return
        selectedTask = task
    }

    fun selectSessionType(type: FocusSessionType) {
        if (isRunning) return
        sessionType = type
        setDuration(minutesFor(type))
    }

    private fun minutesFor(type: FocusSessionType): Int = when (type) {
        FocusSessionType.WORK -> preset.workMinutes
        FocusSessionType.SHORT_BREAK -> preset.shortBreakMinutes
        FocusSessionType.LONG_BREAK -> preset.longBreakMinutes
    }

    private fun setDuration(minutes: Int) {
        totalSeconds = minutes * 60
        remainingSeconds = totalSeconds
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        val sessionId = java.util.UUID.randomUUID().toString()
        currentSessionId = sessionId
        currentSessionStartMillis = System.currentTimeMillis()
        val deadline = currentSessionStartMillis + remainingSeconds * 1000L

        val label = when (sessionType) {
            FocusSessionType.WORK -> selectedTask?.let { "زمان فوکوس روی «${it.title}» تموم شد" }
                ?: "جلسه‌ی فوکوس تموم شد"
            FocusSessionType.SHORT_BREAK -> "استراحت کوتاه تموم شد، وقت برگشت به کاره"
            FocusSessionType.LONG_BREAK -> "استراحت بلند تموم شد"
        }
        NotificationHelper.createNotificationChannel(appContext)
        alarmScheduler.scheduleFocusSessionAlarm(appContext, sessionId, label, deadline)

        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds -= 1
            }
            if (isRunning) {
                finishSession(completed = true)
            }
        }
    }

    /** Stops the running session early; still records the elapsed focus time. */
    fun stop() {
        if (!isRunning) return
        finishSession(completed = false)
    }

    private fun finishSession(completed: Boolean) {
        val sessionId = currentSessionId ?: return
        tickJob?.cancel()
        tickJob = null
        isRunning = false
        alarmScheduler.cancelFocusSessionAlarm(appContext, sessionId)

        val elapsedSeconds = (totalSeconds - remainingSeconds).coerceIn(0, totalSeconds)
        val typeLabel = when (sessionType) {
            FocusSessionType.WORK -> "WORK"
            FocusSessionType.SHORT_BREAK -> "SHORT_BREAK"
            FocusSessionType.LONG_BREAK -> "LONG_BREAK"
        }

        viewModelScope.launch {
            focusSessionDao.insertSession(
                FocusSessionEntity(
                    id = sessionId,
                    taskId = if (sessionType == FocusSessionType.WORK) selectedTask?.id else null,
                    type = typeLabel,
                    plannedDurationMinutes = totalSeconds / 60,
                    startTimeMillis = currentSessionStartMillis,
                    endTimeMillis = System.currentTimeMillis(),
                    actualDurationSeconds = elapsedSeconds,
                    wasCompleted = completed
                )
            )
        }

        if (completed && sessionType == FocusSessionType.WORK) {
            completedWorkSessionsInCycle += 1
            val nextType = if (completedWorkSessionsInCycle % preset.sessionsBeforeLongBreak == 0) {
                FocusSessionType.LONG_BREAK
            } else {
                FocusSessionType.SHORT_BREAK
            }
            sessionType = nextType
            setDuration(minutesFor(nextType))
        } else if (completed) {
            sessionType = FocusSessionType.WORK
            setDuration(minutesFor(FocusSessionType.WORK))
        } else {
            // Stopped early: reset the same session type's timer rather than
            // silently advancing the Pomodoro cycle.
            setDuration(minutesFor(sessionType))
        }
        currentSessionId = null
    }

    fun totalFocusSecondsForTask(taskId: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            onResult(focusSessionDao.getTotalFocusSecondsForTask(taskId))
        }
    }
}

@Composable
fun FocusScreen(
    viewModel: FocusViewModel = hiltViewModel(),
    preselectTaskId: String? = null
) {
    val recentSessions by viewModel.recentSessions.collectAsState()
    val incompleteTasks by viewModel.incompleteTasks.collectAsState()
    var showTaskPicker by remember { mutableStateOf(false) }
    var showDurationDialog by remember { mutableStateOf(false) }

    // Coming from "Start Focus" on a task (prompt section 18/66): once that
    // task has loaded, select it and switch to the WORK session type so the
    // timer is ready to go without extra taps. Guarded by the id so this
    // doesn't keep re-firing/overriding a manual selection on recomposition.
    LaunchedEffect(preselectTaskId, incompleteTasks) {
        if (preselectTaskId != null && viewModel.selectedTask?.id != preselectTaskId) {
            incompleteTasks.firstOrNull { it.id == preselectTaskId }?.let { task ->
                viewModel.selectSessionType(FocusSessionType.WORK)
                viewModel.selectTask(task)
            }
        }
    }

    val isLight = !LocalIsDarkTheme.current
    val bgGradient = if (isLight) {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    }

    Box(modifier = Modifier.fillMaxSize().background(bgGradient)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "فوکوس",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                FocusTimerCard(
                    viewModel = viewModel,
                    onPickTask = { showTaskPicker = true },
                    onEditDuration = { showDurationDialog = true }
                )
            }

            item {
                Text(
                    "تاریخچه‌ی جلسات",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (recentSessions.isEmpty()) {
                item {
                    Text(
                        "هنوز جلسه‌ای ثبت نشده. یک تایمر رو شروع کن.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(recentSessions.take(30)) { session ->
                    FocusSessionRow(session = session, taskTitle = incompleteTasks.firstOrNull { it.id == session.taskId }?.title)
                }
            }
        }

        if (showTaskPicker) {
            TaskPickerDialog(
                tasks = incompleteTasks.filter { !it.isCompleted },
                onDismiss = { showTaskPicker = false },
                onPick = {
                    viewModel.selectTask(it)
                    showTaskPicker = false
                },
                onClear = {
                    viewModel.selectTask(null)
                    showTaskPicker = false
                }
            )
        }

        if (showDurationDialog) {
            EditDurationDialog(
                preset = viewModel.preset,
                onDismiss = { showDurationDialog = false },
                onSave = { newPreset ->
                    viewModel.updatePreset(newPreset)
                    showDurationDialog = false
                }
            )
        }
    }
}

@Composable
private fun FocusTimerCard(
    viewModel: FocusViewModel,
    onPickTask: () -> Unit,
    onEditDuration: () -> Unit
) {
    val minutes = viewModel.remainingSeconds / 60
    val seconds = viewModel.remainingSeconds % 60
    val progress = if (viewModel.totalSeconds > 0) {
        1f - (viewModel.remainingSeconds.toFloat() / viewModel.totalSeconds.toFloat())
    } else 0f

    val accentColor = when (viewModel.sessionType) {
        FocusSessionType.WORK -> AccentBlue
        FocusSessionType.SHORT_BREAK -> AccentTeal
        FocusSessionType.LONG_BREAK -> AccentGreen
    }

    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Session type selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionTypeChip("فوکوس", Icons.Default.Bolt, viewModel.sessionType == FocusSessionType.WORK, viewModel.isRunning) {
                    viewModel.selectSessionType(FocusSessionType.WORK)
                }
                SessionTypeChip("استراحت کوتاه", Icons.Default.Coffee, viewModel.sessionType == FocusSessionType.SHORT_BREAK, viewModel.isRunning) {
                    viewModel.selectSessionType(FocusSessionType.SHORT_BREAK)
                }
                SessionTypeChip("استراحت بلند", Icons.Default.Coffee, viewModel.sessionType == FocusSessionType.LONG_BREAK, viewModel.isRunning) {
                    viewModel.selectSessionType(FocusSessionType.LONG_BREAK)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(220.dp)) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = accentColor,
                    trackColor = accentColor.copy(alpha = 0.15f),
                    strokeWidth = 10.dp
                )
                Text(
                    text = "%02d:%02d".format(minutes, seconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (!viewModel.isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onEditDuration) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تنظیم مدت‌زمان‌ها", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (viewModel.sessionType == FocusSessionType.WORK) {
                TextButton(onClick = onPickTask, enabled = !viewModel.isRunning) {
                    Text(
                        viewModel.selectedTask?.let { "روی: ${it.title}" } ?: "بدون تسک مشخص (لمس کن برای انتخاب)",
                        color = accentColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!viewModel.isRunning) {
                Button(
                    onClick = { viewModel.start() },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier.height(52.dp).fillMaxWidth(0.6f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("شروع")
                }
            } else {
                Button(
                    onClick = { viewModel.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                    modifier = Modifier.height(52.dp).fillMaxWidth(0.6f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("توقف")
                }
            }
        }
    }
}

@Composable
private fun SessionTypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    disabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { if (!disabled) onClick() },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentBlue.copy(alpha = 0.25f)
        )
    )
}

@Composable
private fun FocusSessionRow(session: FocusSessionEntity, taskTitle: String?) {
    val minutes = session.actualDurationSeconds / 60
    val seconds = session.actualDurationSeconds % 60
    val typeLabel = when (session.type) {
        "WORK" -> taskTitle ?: "جلسه‌ی فوکوس"
        "SHORT_BREAK" -> "استراحت کوتاه"
        "LONG_BREAK" -> "استراحت بلند"
        else -> session.type
    }
    val icon = if (session.wasCompleted) Icons.Default.Check else Icons.Default.Close
    val iconColor = if (session.wasCompleted) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    Row(
        modifier = Modifier.fillMaxWidth().glassCard().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(typeLabel, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "%d:%02d".format(minutes, seconds),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun TaskPickerDialog(
    tasks: List<TaskEntity>,
    onDismiss: () -> Unit,
    onPick: (TaskEntity) -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تسک برای فوکوس", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                TextButton(onClick = onClear) { Text("بدون تسک", color = AccentRed) }
                HorizontalDivider()
                LazyColumn {
                    items(tasks) { task ->
                        TextButton(onClick = { onPick(task) }, modifier = Modifier.fillMaxWidth()) {
                            Text(task.title, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (tasks.isEmpty()) {
                    Text(
                        "تسک تکمیل‌نشده‌ای موجود نیست",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}

/**
 * Lets the user customize the Pomodoro preset (prompt section 18: "Custom
 * focus duration, Break duration, Long break"). Values are plain minute
 * counts typed as text since a stepper for 1-180 minutes would need many
 * taps; input is sanitized to digits only and clamped to a sane range so a
 * blank/garbled field can't produce a zero-or-negative timer.
 */
@Composable
private fun EditDurationDialog(
    preset: FocusPreset,
    onDismiss: () -> Unit,
    onSave: (FocusPreset) -> Unit
) {
    var workText by remember { mutableStateOf(preset.workMinutes.toString()) }
    var shortBreakText by remember { mutableStateOf(preset.shortBreakMinutes.toString()) }
    var longBreakText by remember { mutableStateOf(preset.longBreakMinutes.toString()) }

    fun parsed(text: String, fallback: Int): Int =
        text.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 180) ?: fallback

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مدت‌زمان‌های فوکوس", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                OutlinedTextField(
                    value = workText,
                    onValueChange = { workText = it.filter { c -> c.isDigit() } },
                    label = { Text("فوکوس (دقیقه)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = shortBreakText,
                    onValueChange = { shortBreakText = it.filter { c -> c.isDigit() } },
                    label = { Text("استراحت کوتاه (دقیقه)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = longBreakText,
                    onValueChange = { longBreakText = it.filter { c -> c.isDigit() } },
                    label = { Text("استراحت بلند (دقیقه)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        FocusPreset(
                            workMinutes = parsed(workText, preset.workMinutes),
                            shortBreakMinutes = parsed(shortBreakText, preset.shortBreakMinutes),
                            longBreakMinutes = parsed(longBreakText, preset.longBreakMinutes),
                            sessionsBeforeLongBreak = preset.sessionsBeforeLongBreak
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
