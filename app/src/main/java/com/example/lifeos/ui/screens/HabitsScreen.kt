package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import com.example.lifeos.data.database.dao.HabitLogDao
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.HabitLogEntity
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import com.example.lifeos.util.JalaliCalendarUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao
) : ViewModel() {

    private val _habits = MutableStateFlow<List<HabitEntity>>(emptyList())
    val habits: StateFlow<List<HabitEntity>> = _habits.asStateFlow()

    init {
        viewModelScope.launch {
            habitDao.getAllHabits().collect { _habits.value = it }
        }
    }

    fun addHabit(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            habitDao.insertHabit(
                HabitEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = description.ifBlank { null }
                )
            )
        }
    }

    fun toggleHabitStreak(habit: HabitEntity) {
        val todayStr = JalaliCalendarUtil.gregorianToJalali(System.currentTimeMillis()).let {
            String.format("%04d-%02d-%02d", it.year, it.month, it.day)
        }
        
        viewModelScope.launch {
            if (habit.lastCompletedDate == todayStr) {
                // Toggle OFF: Decrement streak, clear date, remove today's log
                val updated = habit.copy(
                    currentStreak = maxOf(0, habit.currentStreak - 1),
                    lastCompletedDate = null
                )
                habitDao.updateHabit(updated)
                habitLogDao.deleteLogForDate(habit.id, todayStr)
            } else {
                // Toggle ON: Increment streak, update date, record today's log.
                // The log is what powers weekly/monthly consistency stats
                // (prompt section 17) independently of the running streak.
                val newStreak = habit.currentStreak + 1
                val updated = habit.copy(
                    currentStreak = newStreak,
                    longestStreak = maxOf(habit.longestStreak, newStreak),
                    lastCompletedDate = todayStr
                )
                habitDao.updateHabit(updated)
                habitLogDao.insertLog(HabitLogEntity(habitId = habit.id, dateKey = todayStr))
            }
        }
    }

    /** Backing data for weekly/monthly consistency views (prompt section 17). */
    fun getLogsForHabit(habitId: String) = habitLogDao.getLogsForHabit(habitId)
}

@Composable
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val habits by viewModel.habits.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

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
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "عادت‌های روزانه",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (habits.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💪", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("هنوز عادتی تعریف نشده", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium)
                        Text("عادت‌های مفید را اضافه کنید تا پیشرفتتان را ببینید", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(habits) { habit ->
                        val todayStr = remember {
                            JalaliCalendarUtil.gregorianToJalali(System.currentTimeMillis()).let {
                                String.format("%04d-%02d-%02d", it.year, it.month, it.day)
                            }
                        }
                        val isCheckedToday = habit.lastCompletedDate == todayStr
                        
                        HabitCard(
                            habit = habit,
                            isCheckedToday = isCheckedToday,
                            onToggle = { viewModel.toggleHabitStreak(habit) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = AccentGreen,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "عادت جدید")
        }

        if (showAddDialog) {
            SimpleAddDialog(
                title = "عادت جدید",
                nameLabel = "نام عادت",
                onDismiss = { showAddDialog = false },
                onAdd = { name, desc -> viewModel.addHabit(name, desc); showAddDialog = false }
            )
        }
    }
}

@Composable
fun HabitCard(
    habit: HabitEntity,
    isCheckedToday: Boolean,
    onToggle: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                habit.description?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Column {
                    Text("رکورد فعلی", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Text(
                        "${habit.currentStreak} روز",
                        style = MaterialTheme.typography.titleLarge,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = onToggle,
                modifier = Modifier.background(
                    if (isCheckedToday) AccentGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f),
                    CircleShape
                )
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "انجام دادم",
                    tint = if (isCheckedToday) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


