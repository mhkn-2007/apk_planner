package com.example.lifeos.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import com.example.lifeos.domain.usecases.GetTodayTasksUseCase
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import com.example.lifeos.util.AlarmScheduler
import com.example.lifeos.util.JalaliCalendarUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID
import java.util.Calendar

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val getTodayTasksUseCase: GetTodayTasksUseCase,
    private val taskRepository: TaskRepository,
    private val habitDao: HabitDao,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks: StateFlow<List<TaskEntity>> = _tasks.asStateFlow()

    val habits: StateFlow<List<HabitEntity>> = habitDao.getAllHabits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            getTodayTasksUseCase().collect { list ->
                _tasks.value = list
            }
        }
    }

    fun addTask(title: String, description: String, priority: Int, timeOfDay: String? = null, customHour: Int? = null, customMinute: Int? = null) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var alarmTime: Long? = null

            if (timeOfDay != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = now
                when (timeOfDay) {
                    "MORNING" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 9)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                    }
                    "AFTERNOON" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 15)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                    }
                    "NIGHT" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 21)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                    }
                    "CUSTOM" -> {
                        if (customHour != null && customMinute != null) {
                            cal.set(Calendar.HOUR_OF_DAY, customHour)
                            cal.set(Calendar.MINUTE, customMinute)
                            cal.set(Calendar.SECOND, 0)
                        }
                    }
                }
                alarmTime = cal.timeInMillis
                if (alarmTime < now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    alarmTime = cal.timeInMillis
                }
            }

            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description.ifBlank { null },
                priority = priority,
                dueDateMillis = System.currentTimeMillis(),
                timeOfDay = timeOfDay,
                alarmTimeMillis = alarmTime
            )
            taskRepository.insertTask(task)

            alarmTime?.let {
                alarmScheduler.scheduleAlarm(context, task.id, task.title, it)
            }
        }
    }

    fun updateTask(task: TaskEntity, newTitle: String, newDesc: String, newPriority: Int, timeOfDay: String? = null, customHour: Int? = null, customMinute: Int? = null) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var alarmTime: Long? = null

            if (timeOfDay != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = now
                when (timeOfDay) {
                    "MORNING" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 9)
                        cal.set(Calendar.MINUTE, 0)
                    }
                    "AFTERNOON" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 15)
                        cal.set(Calendar.MINUTE, 0)
                    }
                    "NIGHT" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 21)
                        cal.set(Calendar.MINUTE, 0)
                    }
                    "CUSTOM" -> {
                        if (customHour != null && customMinute != null) {
                            cal.set(Calendar.HOUR_OF_DAY, customHour)
                            cal.set(Calendar.MINUTE, customMinute)
                        }
                    }
                }
                alarmTime = cal.timeInMillis
                if (alarmTime < now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    alarmTime = cal.timeInMillis
                }
            }

            val updated = task.copy(
                title = newTitle,
                description = newDesc.ifBlank { null },
                priority = newPriority,
                timeOfDay = timeOfDay,
                alarmTimeMillis = alarmTime
            )
            taskRepository.updateTask(updated)

            // Reschedule or cancel alarm
            alarmScheduler.cancelAlarm(context, task.id)
            alarmTime?.let {
                alarmScheduler.scheduleAlarm(context, task.id, updated.title, it)
            }
        }
    }

    fun toggleTaskComplete(task: TaskEntity) {
        viewModelScope.launch {
            val newCompletedState = !task.isCompleted
            taskRepository.updateTask(task.copy(isCompleted = newCompletedState))

            val habitId = task.habitId
            if (habitId != null) {
                val habit = habitDao.getHabitById(habitId) ?: return@launch
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())

                if (newCompletedState) {
                    // Only count once per day, mirroring the manual habit
                    // check-in logic in HabitsScreen.
                    if (habit.lastCompletedDate != todayStr) {
                        val newStreak = habit.currentStreak + 1
                        habitDao.updateHabit(
                            habit.copy(
                                currentStreak = newStreak,
                                longestStreak = maxOf(habit.longestStreak, newStreak),
                                lastCompletedDate = todayStr
                            )
                        )
                    }
                } else {
                    // Un-completing the task should undo today's check-in.
                    if (habit.lastCompletedDate == todayStr) {
                        habitDao.updateHabit(
                            habit.copy(
                                currentStreak = maxOf(0, habit.currentStreak - 1),
                                lastCompletedDate = null
                            )
                        )
                    }
                }
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
            alarmScheduler.cancelAlarm(context, task.id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    onAddTaskClick: () -> Unit = {}
) {
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showHabitsDialog by remember { mutableStateOf(false) }
    var selectedHabitForConfig by remember { mutableStateOf<HabitEntity?>(null) }
    var selectedTaskForEdit by remember { mutableStateOf<TaskEntity?>(null) }

    val todayJalali = remember { JalaliCalendarUtil.gregorianToJalali(System.currentTimeMillis()) }

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
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "امروز",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = todayJalali.format(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${tasks.size} کار برنامه‌ریزی شده",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentTeal
                        )
                    }

                    // Add from habits button
                    Button(
                        onClick = { showHabitsDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.glassCard(cornerRadius = 12.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = AccentGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("عادت‌ها", color = AccentGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🎉",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "هیچ کاری برنامه‌ریزی نشده",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "روی دکمه + بزنید یا یک عادت انتخاب کنید",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(tasks) { task ->
                        GlassTaskItem(
                            task = task,
                            onToggleComplete = { viewModel.toggleTaskComplete(task) },
                            onDelete = { viewModel.deleteTask(task) },
                            onClick = { selectedTaskForEdit = task }
                        )
                    }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = AccentBlue,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "اضافه کردن کار")
        }

        if (showAddDialog) {
            AddTaskDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { title, desc, priority, timeOfDay, hour, min ->
                    viewModel.addTask(title, desc, priority, timeOfDay, hour, min)
                    showAddDialog = false
                }
            )
        }

        if (showHabitsDialog) {
            SelectHabitDialog(
                habits = habits,
                onDismiss = { showHabitsDialog = false },
                onSelect = { habit ->
                    selectedHabitForConfig = habit
                    showHabitsDialog = false
                }
            )
        }

        if (selectedHabitForConfig != null) {
            ConfigureHabitDialog(
                habitName = selectedHabitForConfig!!.name,
                onDismiss = { selectedHabitForConfig = null },
                onAdd = { title, desc, priority, timeOfDay, hour, min ->
                    viewModel.addTask(title, desc, priority, timeOfDay, hour, min)
                    selectedHabitForConfig = null
                }
            )
        }

        if (selectedTaskForEdit != null) {
            EditTaskDialog(
                task = selectedTaskForEdit!!,
                onDismiss = { selectedTaskForEdit = null },
                onUpdate = { title, desc, priority, timeOfDay, hour, min ->
                    viewModel.updateTask(selectedTaskForEdit!!, title, desc, priority, timeOfDay, hour, min)
                    selectedTaskForEdit = null
                }
            )
        }
    }
}

@Composable
fun SelectHabitDialog(
    habits: List<HabitEntity>,
    onDismiss: () -> Unit,
    onSelect: (HabitEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب از عادت‌ها", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            if (habits.isEmpty()) {
                Text("هنوز هیچ عادتی تعریف نکرده‌اید. ابتدا در بخش عادت‌ها یک عادت بسازید.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                ) {
                    items(habits) { habit ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(habit) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ListItem(
                                headlineContent = { Text(habit.name, color = MaterialTheme.colorScheme.onBackground) },
                                supportingContent = { habit.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureHabitDialog(
    habitName: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String?, Int?, Int?) -> Unit
) {
    var desc by remember { mutableStateOf("عادت روزانه") }
    var priority by remember { mutableIntStateOf(2) }
    var timeOfDay by remember { mutableStateOf<String?>(null) }
    var customHour by remember { mutableStateOf<Int?>(null) }
    var customMinute by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیم عادت در برنامه روزانه", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                Text("افزودن عادت «$habitName» به لیست کارهای امروز", style = MaterialTheme.typography.titleMedium, color = AccentGreen)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("توضیح دلخواه برای امروز") },
                    modifier = Modifier.fillMaxWidth()
                )
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
                Spacer(modifier = Modifier.height(12.dp))
                Text("زمان انجام:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val times = listOf("صبح" to "MORNING", "عصر" to "AFTERNOON", "شب" to "NIGHT")
                    times.forEach { (label, value) ->
                        FilterChip(
                            selected = timeOfDay == value,
                            onClick = { 
                                timeOfDay = if (timeOfDay == value) null else value
                                customHour = null
                                customMinute = null
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val current = Calendar.getInstance()
                        TimePickerDialog(context, { _, h, m ->
                            customHour = h
                            customMinute = m
                            timeOfDay = "CUSTOM"
                        }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f))
                ) {
                    val timeText = if (timeOfDay == "CUSTOM" && customHour != null) {
                        String.format("ساعت %02d:%02d", customHour, customMinute)
                    } else {
                        "تنظیم ساعت دلخواه آلارم"
                    }
                    Text(timeText, color = AccentBlue)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(habitName, desc, priority, timeOfDay, customHour, customMinute) }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                Text("افزودن به امروز")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String?, Int?, Int?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }
    var timeOfDay by remember { mutableStateOf<String?>(null) }
    var customHour by remember { mutableStateOf<Int?>(null) }
    var customMinute by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کار جدید", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان کار") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات (اختیاری)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
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
                Spacer(modifier = Modifier.height(12.dp))
                Text("زمان انجام کار (آلارم):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val times = listOf("صبح (۹:۰۰)" to "MORNING", "عصر (۱۵:۰۰)" to "AFTERNOON", "شب (۲۱:۰۰)" to "NIGHT")
                    times.forEach { (label, value) ->
                        FilterChip(
                            selected = timeOfDay == value,
                            onClick = { 
                                timeOfDay = if (timeOfDay == value) null else value
                                customHour = null
                                customMinute = null
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val current = Calendar.getInstance()
                        TimePickerDialog(context, { _, h, m ->
                            customHour = h
                            customMinute = m
                            timeOfDay = "CUSTOM"
                        }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f))
                ) {
                    val timeText = if (timeOfDay == "CUSTOM" && customHour != null) {
                        String.format("ساعت %02d:%02d", customHour, customMinute)
                    } else {
                        "ساعت دلخواه نوتیفیکیشن"
                    }
                    Text(timeText, color = AccentBlue)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title, description, priority, timeOfDay, customHour, customMinute) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("ذخیره")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onUpdate: (String, String, Int, String?, Int?, Int?) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var priority by remember { mutableIntStateOf(task.priority) }
    var timeOfDay by remember { mutableStateOf(task.timeOfDay) }
    
    // Parse custom hour/min if available from existing alarmTimeMillis
    var customHour by remember {
        mutableStateOf(task.alarmTimeMillis?.let {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it
            cal.get(Calendar.HOUR_OF_DAY)
        })
    }
    var customMinute by remember {
        mutableStateOf(task.alarmTimeMillis?.let {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it
            cal.get(Calendar.MINUTE)
        })
    }
    
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش کار", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان کار") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
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
                Spacer(modifier = Modifier.height(12.dp))
                Text("زمان انجام کار (آلارم):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val times = listOf("صبح" to "MORNING", "عصر" to "AFTERNOON", "شب" to "NIGHT")
                    times.forEach { (label, value) ->
                        FilterChip(
                            selected = timeOfDay == value,
                            onClick = { 
                                timeOfDay = if (timeOfDay == value) null else value
                                customHour = null
                                customMinute = null
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val current = Calendar.getInstance()
                        TimePickerDialog(context, { _, h, m ->
                            customHour = h
                            customMinute = m
                            timeOfDay = "CUSTOM"
                        }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f))
                ) {
                    val timeText = if (timeOfDay == "CUSTOM" && customHour != null) {
                        String.format("ساعت %02d:%02d", customHour, customMinute)
                    } else {
                        "تغییر ساعت نوتیفیکیشن"
                    }
                    Text(timeText, color = AccentBlue)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdate(title, description, priority, timeOfDay, customHour, customMinute) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("بروزرسانی")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun GlassTaskItem(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val priorityColor = when (task.priority) {
        1 -> PriorityLow
        2 -> PriorityMedium
        3 -> PriorityHigh
        4 -> PriorityCritical
        else -> PriorityNone
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Priority indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(priorityColor, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (!task.description.isNullOrEmpty()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
                
                // Show alarm indicator if set
                if (task.alarmTimeMillis != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val timeText = when (task.timeOfDay) {
                            "MORNING" -> "صبح (۹:۰۰)"
                            "AFTERNOON" -> "عصر (۱۵:۰۰)"
                            "NIGHT" -> "شب (۲۱:۰۰)"
                            else -> {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = task.alarmTimeMillis
                                String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                            }
                        }
                        Text(
                            text = "یادآوری: $timeText",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentBlue
                        )
                    }
                }
            }

            IconButton(onClick = onToggleComplete) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "تکمیل",
                    tint = if (task.isCompleted) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف",
                    tint = AccentRed.copy(alpha = 0.7f)
                )
            }
        }
    }
}


