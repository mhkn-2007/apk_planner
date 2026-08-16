package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.dao.CalendarEventDao
import com.example.lifeos.data.database.entities.CalendarEventEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.planner.DeterministicPlannerEngine
import com.example.lifeos.domain.repositories.TaskRepository
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import com.example.lifeos.util.JalaliCalendarUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val plannerEngine: DeterministicPlannerEngine,
    private val calendarEventDao: CalendarEventDao
) : ViewModel() {

    private val _selectedDayTasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val selectedDayTasks: StateFlow<List<TaskEntity>> = _selectedDayTasks.asStateFlow()

    private val _selectedDayEvents = MutableStateFlow<List<CalendarEventEntity>>(emptyList())
    /**
     * Manually-created calendar events for the selected day (prompt section
     * 13: "View ... time blocks" / section 44: CalendarEvent) — distinct
     * from tasks: an event is a fixed appointment (e.g. a meeting) rather
     * than actionable work, so it isn't completed/prioritized like a task.
     * Previously this table existed with no reader/writer anywhere in the
     * app; this is its first real consumer, LifeOS-local only (no
     * provider/sourceId set) until an actual external-calendar sync exists.
     */
    val selectedDayEvents: StateFlow<List<CalendarEventEntity>> = _selectedDayEvents.asStateFlow()

    /**
     * Time-blocked tasks for the selected day (prompt section 14) — those
     * with both a start and end time — sorted chronologically for the
     * timeline view. Untimed tasks stay in [selectedDayTasks]'s plain list.
     */
    val timeBlockedTasks: StateFlow<List<TaskEntity>> = MutableStateFlow<List<TaskEntity>>(emptyList())
        .also { flow ->
            viewModelScope.launch {
                selectedDayTasks.collect { tasks ->
                    flow.value = tasks
                        .filter { it.startTimeMillis != null && it.endTimeMillis != null }
                        .sortedBy { it.startTimeMillis }
                }
            }
        }.asStateFlow()

    /** Pairs of overlapping time blocks for the selected day (section 14: "must detect overlapping blocks"). */
    val conflicts: StateFlow<List<Pair<TaskEntity, TaskEntity>>> = MutableStateFlow<List<Pair<TaskEntity, TaskEntity>>>(emptyList())
        .also { flow ->
            viewModelScope.launch {
                timeBlockedTasks.collect { blocked ->
                    flow.value = plannerEngine.detectConflicts(blocked)
                }
            }
        }.asStateFlow()

    fun loadTasksForDate(year: Int, month: Int, day: Int) {
        viewModelScope.launch {
            taskRepository.getAllTasks().collect { allTasks ->
                val filtered = allTasks.filter { task ->
                    if (task.dueDateMillis != null) {
                        val taskJalali = JalaliCalendarUtil.gregorianToJalali(task.dueDateMillis)
                        taskJalali.year == year && taskJalali.month == month && taskJalali.day == day
                    } else false
                }
                _selectedDayTasks.value = filtered
            }
        }
        val dayStart = JalaliCalendarUtil.jalaliToGregorian(year, month, day)
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
        viewModelScope.launch {
            calendarEventDao.getEventsInRange(dayStart, dayEnd).collect { events ->
                _selectedDayEvents.value = events.sortedBy { it.startTimeMillis }
            }
        }
    }

    fun addEvent(title: String, startMillis: Long, endMillis: Long, location: String?) {
        if (title.isBlank() || endMillis <= startMillis) return
        viewModelScope.launch {
            calendarEventDao.insertEvent(
                CalendarEventEntity(
                    title = title,
                    startTimeMillis = startMillis,
                    endTimeMillis = endMillis,
                    location = location?.ifBlank { null }
                )
            )
        }
    }

    fun deleteEvent(event: CalendarEventEntity) {
        viewModelScope.launch { calendarEventDao.deleteEvent(event) }
    }

    fun addTaskForDate(title: String, description: String, priority: Int, year: Int, month: Int, day: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            // Use the real Jalali -> Gregorian conversion (accurate across leap
            // years and variable month lengths) instead of a *365/*30 day
            // delta approximation, which used to drift the due date by several
            // days depending on how far the target date was from today.
            val dueDateMillis = JalaliCalendarUtil.jalaliToGregorian(year, month, day)

            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description.ifBlank { null },
                priority = priority,
                dueDateMillis = dueDateMillis
            )
            taskRepository.insertTask(task)
            loadTasksForDate(year, month, day)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val todayJalali = remember {
        JalaliCalendarUtil.gregorianToJalali(System.currentTimeMillis())
    }

    // The month/year currently being *viewed* can now differ from today's
    // month/year — previously the calendar was permanently stuck showing only
    // the current month with no way to navigate to other months.
    var viewedYear by remember { mutableIntStateOf(todayJalali.year) }
    var viewedMonth by remember { mutableIntStateOf(todayJalali.month) }
    var selectedDay by remember { mutableIntStateOf(todayJalali.day) }

    val selectedDayTasks by viewModel.selectedDayTasks.collectAsState()
    val timeBlockedTasks by viewModel.timeBlockedTasks.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val selectedDayEvents by viewModel.selectedDayEvents.collectAsState()
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    // Day list vs. Timeline/time-blocking view (prompt section 13: "Timeline
    // / schedule view", section 14: time blocking with visible overlaps).
    var showTimeline by remember { mutableStateOf(false) }

    // Correctly accounts for Esfand having 29 or 30 days depending on whether
    // the Jalali year is a leap year.
    val daysInMonth = JalaliCalendarUtil.daysInJalaliMonth(viewedYear, viewedMonth)
    val daysList = (1..daysInMonth).toList()
    val weekDays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val viewedMonthName = remember(viewedYear, viewedMonth) {
        JalaliCalendarUtil.JalaliDate(viewedYear, viewedMonth, 1).monthName
    }

    fun goToPreviousMonth() {
        if (viewedMonth == 1) {
            viewedMonth = 12
            viewedYear -= 1
        } else {
            viewedMonth -= 1
        }
        selectedDay = 1
    }

    fun goToNextMonth() {
        if (viewedMonth == 12) {
            viewedMonth = 1
            viewedYear += 1
        } else {
            viewedMonth += 1
        }
        selectedDay = 1
    }

    LaunchedEffect(selectedDay, viewedMonth, viewedYear) {
        viewModel.loadTasksForDate(viewedYear, viewedMonth, selectedDay)
    }

    // Dynamic gradient backgrounds depending on Light/Dark mode
    val isLight = !LocalIsDarkTheme.current
    val bgGradient = if (isLight) {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Month Header with navigation
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { goToNextMonth() }) {
                    // RTL layout: "next" visually reads as the left-pointing arrow
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "ماه بعد",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "$viewedMonthName $viewedYear",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { goToPreviousMonth() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "ماه قبل",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Weekday Headers
            Box(modifier = Modifier.fillMaxWidth().glassCard(cornerRadius = 16.dp).padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    weekDays.forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentTeal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 240.dp)
            ) {
                items(daysList) { day ->
                    val isToday = day == todayJalali.day &&
                        viewedMonth == todayJalali.month &&
                        viewedYear == todayJalali.year
                    val isSelected = day == selectedDay

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> AccentBlue
                                    isToday -> AccentTeal.copy(alpha = 0.25f)
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { selectedDay = day },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            color = when {
                                isSelected -> Color.White
                                isToday -> AccentTeal
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected Day Header & Add Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "کارهای روز $selectedDay $viewedMonthName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // List / Timeline toggle (prompt section 13-14).
                    IconButton(onClick = { showTimeline = !showTimeline }) {
                        Icon(
                            if (showTimeline) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.Schedule,
                            contentDescription = if (showTimeline) "نمای لیست" else "نمای تایم‌لاین",
                            tint = AccentTeal
                        )
                    }
                    IconButton(
                        onClick = { showAddEventDialog = true },
                        modifier = Modifier.background(AccentTeal.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = "رویداد جدید برای این روز", tint = AccentTeal)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { showAddTaskDialog = true },
                        modifier = Modifier.background(AccentBlue.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "کار جدید برای این روز", tint = AccentBlue)
                    }
                }
            }

            if (selectedDayEvents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedDayEvents.forEach { event ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(cornerRadius = 10.dp)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Event, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.title, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    formatTimeRange(event.startTimeMillis, event.endTimeMillis) +
                                        (event.location?.let { " • $it" } ?: ""),
                                    color = AccentTeal,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            IconButton(onClick = { viewModel.deleteEvent(event) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "حذف رویداد", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )

            if (showTimeline) {
                DayTimelineView(
                    tasks = timeBlockedTasks,
                    conflicts = conflicts,
                    modifier = Modifier.weight(1f)
                )
            } else if (selectedDayTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "کار برنامه‌ریزی شده‌ای برای این روز وجود ندارد",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(selectedDayTasks) { task ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(cornerRadius = 12.dp)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(text = task.title, color = MaterialTheme.colorScheme.onBackground)
                                if (task.startTimeMillis != null && task.endTimeMillis != null) {
                                    Text(
                                        text = formatTimeRange(task.startTimeMillis!!, task.endTimeMillis!!),
                                        color = AccentTeal,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddTaskDialog) {
            SimpleAddTaskForDateDialog(
                onDismiss = { showAddTaskDialog = false },
                onAdd = { title, desc, priority ->
                    viewModel.addTaskForDate(title, desc, priority, viewedYear, viewedMonth, selectedDay)
                    showAddTaskDialog = false
                }
            )
        }

        if (showAddEventDialog) {
            AddEventDialog(
                onDismiss = { showAddEventDialog = false },
                onAdd = { title, startHour, startMinute, endHour, endMinute, location ->
                    val dayBase = JalaliCalendarUtil.jalaliToGregorian(viewedYear, viewedMonth, selectedDay)
                    val startMillis = Calendar.getInstance().apply {
                        timeInMillis = dayBase
                        set(Calendar.HOUR_OF_DAY, startHour)
                        set(Calendar.MINUTE, startMinute)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis
                    val endMillis = Calendar.getInstance().apply {
                        timeInMillis = dayBase
                        set(Calendar.HOUR_OF_DAY, endHour)
                        set(Calendar.MINUTE, endMinute)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis
                    viewModel.addEvent(title, startMillis, endMillis, location)
                    showAddEventDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleAddTaskForDateDialog(onDismiss: () -> Unit, onAdd: (String, String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کار جدید برای این تاریخ", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان کار") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("توضیحات (اختیاری)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                Text("اولویت:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val priorities = listOf("عادی" to 0, "کم" to 1, "متوسط" to 2, "بالا" to 3, "بحرانی" to 4)
                    priorities.forEach { (label, value) ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(title, desc, priority) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEventDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, location: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var startHour by remember { mutableStateOf<Int?>(null) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf<Int?>(null) }
    var endMinute by remember { mutableStateOf(0) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رویداد جدید", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان رویداد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("مکان (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val current = Calendar.getInstance()
                            android.app.TimePickerDialog(context, { _, h, m -> startHour = h; startMinute = m },
                                current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal.copy(alpha = 0.2f))
                    ) {
                        Text(
                            startHour?.let { String.format("شروع %02d:%02d", it, startMinute) } ?: "ساعت شروع",
                            color = AccentTeal
                        )
                    }
                    Button(
                        onClick = {
                            val current = Calendar.getInstance()
                            android.app.TimePickerDialog(context, { _, h, m -> endHour = h; endMinute = m },
                                current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentTeal.copy(alpha = 0.2f))
                    ) {
                        Text(
                            endHour?.let { String.format("پایان %02d:%02d", it, endMinute) } ?: "ساعت پایان",
                            color = AccentTeal
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sh = startHour; val eh = endHour
                    if (sh != null && eh != null) {
                        onAdd(title, sh, startMinute, eh, endMinute, location)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                enabled = title.isNotBlank() && startHour != null && endHour != null
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

private fun formatTimeRange(startMillis: Long, endMillis: Long): String {
    val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
    val endCal = Calendar.getInstance().apply { timeInMillis = endMillis }
    return String.format(
        "%02d:%02d–%02d:%02d",
        startCal.get(Calendar.HOUR_OF_DAY), startCal.get(Calendar.MINUTE),
        endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE)
    )
}

/**
 * Time-blocking timeline (prompt section 13: "Timeline / schedule view",
 * section 14: "Time blocks must be visually clear. The system must detect
 * overlapping blocks."). Renders every time-blocked task for the day as a
 * proportionally-sized, proportionally-positioned block along a fixed
 * 06:00-24:00 hour axis, with conflicting blocks (from
 * [CalendarViewModel.conflicts], backed by
 * [com.example.lifeos.domain.planner.DeterministicPlannerEngine.detectConflicts])
 * outlined in red so overlaps are immediately visible rather than only
 * discoverable by reading times.
 */
@Composable
private fun DayTimelineView(
    tasks: List<TaskEntity>,
    conflicts: List<Pair<TaskEntity, TaskEntity>>,
    modifier: Modifier = Modifier
) {
    val startHour = 6
    val endHour = 24
    val hourHeight = 64.dp
    val totalHeight = hourHeight * (endHour - startHour)
    val conflictingIds = remember(conflicts) {
        conflicts.flatMap { (a, b) -> listOf(a.id, b.id) }.toSet()
    }

    if (tasks.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "هیچ کار زمان‌بندی‌شده‌ای (با ساعت شروع و مدت‌زمان) برای این روز نیست",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            // Hour grid lines + labels.
            for (hour in startHour..endHour) {
                val y = hourHeight * (hour - startHour)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = y),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = String.format("%02d:00", hour % 24),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(44.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                    )
                }
            }

            // Task blocks, positioned/sized by their start/end time.
            tasks.forEach { task ->
                val startCal = Calendar.getInstance().apply { timeInMillis = task.startTimeMillis!! }
                val endCal = Calendar.getInstance().apply { timeInMillis = task.endTimeMillis!! }
                val startFractionalHour = (startCal.get(Calendar.HOUR_OF_DAY) - startHour) +
                    startCal.get(Calendar.MINUTE) / 60f
                val durationHours = ((task.endTimeMillis!! - task.startTimeMillis!!).coerceAtLeast(60_000L)) / 3_600_000f

                val hasConflict = task.id in conflictingIds
                val priorityColor = when (task.priority) {
                    1 -> PriorityLow
                    2 -> PriorityMedium
                    3 -> PriorityHigh
                    4 -> PriorityCritical
                    else -> AccentBlue
                }

                Box(
                    modifier = Modifier
                        .padding(start = 52.dp, end = 8.dp)
                        .offset(y = hourHeight * startFractionalHour)
                        .fillMaxWidth()
                        .height((hourHeight * durationHours).coerceAtLeast(28.dp))
                        .background(
                            priorityColor.copy(alpha = if (hasConflict) 0.35f else 0.22f),
                            RoundedCornerShape(10.dp)
                        )
                        .then(
                            if (hasConflict) {
                                Modifier.border(1.5.dp, AccentRed, RoundedCornerShape(10.dp))
                            } else Modifier
                        )
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = task.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = formatTimeRange(task.startTimeMillis!!, task.endTimeMillis!!) +
                                if (hasConflict) " • تداخل زمانی" else "",
                            color = if (hasConflict) AccentRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
