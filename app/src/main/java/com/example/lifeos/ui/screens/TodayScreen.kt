package com.example.lifeos.ui.screens

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
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import com.example.lifeos.domain.usecases.GetTodayTasksUseCase
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import com.example.lifeos.util.JalaliCalendarUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val getTodayTasksUseCase: GetTodayTasksUseCase,
    private val taskRepository: TaskRepository,
    private val habitDao: HabitDao
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

    fun addTask(title: String, description: String, priority: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description.ifBlank { null },
                priority = priority,
                dueDateMillis = System.currentTimeMillis()
            )
            taskRepository.insertTask(task)
        }
    }

    fun addHabitAsTask(habit: HabitEntity) {
        viewModelScope.launch {
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = habit.name,
                description = habit.description ?: "عادت روزانه",
                priority = 2, // Medium priority by default
                dueDateMillis = System.currentTimeMillis()
            )
            taskRepository.insertTask(task)
        }
    }

    fun toggleTaskComplete(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
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
    val todayJalali = remember { JalaliCalendarUtil.gregorianToJalali(System.currentTimeMillis()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientMiddle, GradientEnd)
                )
            )
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
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = todayJalali.format(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
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
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.glassCard(cornerRadius = 12.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = AccentGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("عادت‌ها", color = AccentGreen)
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
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "روی دکمه + بزنید یا یک عادت انتخاب کنید",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(tasks) { task ->
                        GlassTaskItem(
                            task = task,
                            onToggleComplete = { viewModel.toggleTaskComplete(task) },
                            onDelete = { viewModel.deleteTask(task) }
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
                onAdd = { title, desc, priority ->
                    viewModel.addTask(title, desc, priority)
                    showAddDialog = false
                }
            )
        }

        if (showHabitsDialog) {
            SelectHabitDialog(
                habits = habits,
                onDismiss = { showHabitsDialog = false },
                onSelect = { habit ->
                    viewModel.addHabitAsTask(habit)
                    showHabitsDialog = false
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
        title = { Text("انتخاب از عادت‌ها", color = TextPrimary) },
        containerColor = GlassSecondaryDark,
        text = {
            if (habits.isEmpty()) {
                Text("هنوز هیچ عادتی تعریف نکرده‌اید. ابتدا در بخش عادت‌ها یک عادت بسازید.", color = TextMuted)
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
                            colors = CardDefaults.cardColors(containerColor = GlassSurfaceMedium)
                        ) {
                            ListItem(
                                headlineContent = { Text(habit.name, color = TextPrimary) },
                                supportingContent = { habit.description?.let { Text(it, color = TextMuted) } },
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
fun AddTaskDialog(onDismiss: () -> Unit, onAdd: (String, String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کار جدید", color = TextPrimary) },
        containerColor = GlassSecondaryDark,
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
                Text("اولویت:", color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val priorities = listOf("عادی" to 0, "کم" to 1, "متوسط" to 2, "بالا" to 3, "بحرانی" to 4)
                    priorities.forEach { (label, value) ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(title, description, priority) },
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

@Composable
fun GlassTaskItem(task: TaskEntity, onToggleComplete: () -> Unit, onDelete: () -> Unit) {
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
                    color = if (task.isCompleted) TextMuted else TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (!task.description.isNullOrEmpty()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            IconButton(onClick = onToggleComplete) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "تکمیل",
                    tint = if (task.isCompleted) AccentGreen else TextMuted
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
