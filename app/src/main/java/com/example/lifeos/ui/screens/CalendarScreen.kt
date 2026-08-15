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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _selectedDayTasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val selectedDayTasks: StateFlow<List<TaskEntity>> = _selectedDayTasks.asStateFlow()

    fun loadTasksForDate(year: Int, month: Int, day: Int) {
        viewModelScope.launch {
            // Convert Jalali to approximate millis range for query
            // For simplicity, we load all tasks and filter client-side
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
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val todayJalali = remember {
        JalaliCalendarUtil.gregorianToJalali(System.currentTimeMillis())
    }
    var selectedDay by remember { mutableIntStateOf(todayJalali.day) }
    val selectedDayTasks by viewModel.selectedDayTasks.collectAsState()

    val daysInMonth = if (todayJalali.month <= 6) 31 else if (todayJalali.month == 12) 29 else 30
    val daysList = (1..daysInMonth).toList()
    val weekDays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    LaunchedEffect(selectedDay) {
        viewModel.loadTasksForDate(todayJalali.year, todayJalali.month, selectedDay)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientMiddle, GradientEnd)
                )
            )
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
                color = TextPrimary,
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
                modifier = Modifier.heightIn(max = 300.dp)
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
                                    isToday -> AccentTeal.copy(alpha = 0.3f)
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
                                else -> TextSecondary
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selected Day Tasks
            Text(
                text = "کارهای روز $selectedDay ${todayJalali.monthName}",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = GlassBorder
            )

            if (selectedDayTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "کار برنامه‌ریزی شده‌ای وجود ندارد",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedDayTasks) { task ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(cornerRadius = 12.dp)
                                .padding(12.dp)
                        ) {
                            Text(text = task.title, color = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}
