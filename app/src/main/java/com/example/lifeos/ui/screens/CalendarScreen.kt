package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.entities.TaskEntity
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
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _selectedDayTasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val selectedDayTasks: StateFlow<List<TaskEntity>> = _selectedDayTasks.asStateFlow()

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
    }

    fun addTaskForDate(title: String, description: String, priority: Int, year: Int, month: Int, day: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            // Find Gregorian equivalent timestamp
            // Naive conversion for standard timezone
            val jalali = JalaliCalendarUtil.JalaliDate(year, month, day)
            val gregorianCal = Calendar.getInstance()
            
            // To approximate, we calculate delta in days from today
            val todayJalali = JalaliCalendarUtil.gregorianToJalali(System.currentTimeMillis())
            val deltaDays = (year - todayJalali.year) * 365 + (month - todayJalali.month) * 30 + (day - todayJalali.day)
            
            gregorianCal.add(Calendar.DAY_OF_YEAR, deltaDays)
            
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description.ifBlank { null },
                priority = priority,
                dueDateMillis = gregorianCal.timeInMillis
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
    var selectedDay by remember { mutableIntStateOf(todayJalali.day) }
    val selectedDayTasks by viewModel.selectedDayTasks.collectAsState()
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val daysInMonth = if (todayJalali.month <= 6) 31 else if (todayJalali.month == 12) 29 else 30
    val daysList = (1..daysInMonth).toList()
    val weekDays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    LaunchedEffect(selectedDay) {
        viewModel.loadTasksForDate(todayJalali.year, todayJalali.month, selectedDay)
    }

    // Dynamic gradient backgrounds depending on Light/Dark mode
    val isLight = MaterialTheme.colorScheme.background.toColorInt() == 0xFFF5F7FA.toInt()
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
            // Month Header
            Text(
                text = "${todayJalali.monthName} ${todayJalali.year}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                    val isToday = day == todayJalali.day
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

            // Selected Day Header & Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "کارهای روز $selectedDay ${todayJalali.monthName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                
                IconButton(
                    onClick = { showAddTaskDialog = true },
                    modifier = Modifier.background(AccentBlue.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "کار جدید برای این روز", tint = AccentBlue)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            )

            if (selectedDayTasks.isEmpty()) {
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
                            Text(text = task.title, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }

        if (showAddTaskDialog) {
            SimpleAddTaskForDateDialog(
                onDismiss = { showAddTaskDialog = false },
                onAdd = { title, desc, priority ->
                    viewModel.addTaskForDate(title, desc, priority, todayJalali.year, todayJalali.month, selectedDay)
                    showAddTaskDialog = false
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

private fun Color.toColorInt(): Int {
    return ((value shr 32) and 0xffffffff).toInt()
}
