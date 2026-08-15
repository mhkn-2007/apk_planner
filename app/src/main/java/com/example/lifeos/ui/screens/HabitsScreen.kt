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
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habitDao: HabitDao
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

    fun incrementStreak(habitId: String) {
        viewModelScope.launch {
            habitDao.incrementStreak(habitId)
        }
    }

    fun resetStreak(habitId: String) {
        viewModelScope.launch {
            habitDao.resetStreak(habitId)
        }
    }
}

@Composable
fun HabitsScreen(viewModel: HabitsViewModel = hiltViewModel()) {
    val habits by viewModel.habits.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(GradientStart, GradientMiddle, GradientEnd)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "عادت‌های روزانه",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
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
                        Text("هنوز عادتی تعریف نشده", color = TextMuted, style = MaterialTheme.typography.titleMedium)
                        Text("عادت‌های مفید را اضافه کنید تا پیشرفتتان را ببینید", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(habits) { habit ->
                        HabitCard(
                            habit = habit,
                            onCheck = { viewModel.incrementStreak(habit.id) },
                            onReset = { viewModel.resetStreak(habit.id) }
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
fun HabitCard(habit: HabitEntity, onCheck: () -> Unit, onReset: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                habit.description?.let {
                    Text(it, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("رکورد فعلی", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            "${habit.currentStreak} روز",
                            style = MaterialTheme.typography.titleLarge,
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("بهترین رکورد", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            "${habit.longestStreak} روز",
                            style = MaterialTheme.typography.titleLarge,
                            color = AccentAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            IconButton(onClick = onCheck) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "انجام دادم",
                    tint = AccentGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
