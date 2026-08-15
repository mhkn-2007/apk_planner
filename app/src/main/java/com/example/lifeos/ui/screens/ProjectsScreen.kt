package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.ProjectEntity
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
class ProjectsViewModel @Inject constructor(
    private val goalDao: GoalDao,
    private val projectDao: ProjectDao
) : ViewModel() {

    private val _goals = MutableStateFlow<List<GoalEntity>>(emptyList())
    val goals: StateFlow<List<GoalEntity>> = _goals.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectEntity>>(emptyList())
    val projects: StateFlow<List<ProjectEntity>> = _projects.asStateFlow()

    init {
        viewModelScope.launch {
            goalDao.getAllGoals().collect { _goals.value = it }
        }
        viewModelScope.launch {
            projectDao.getAllProjects().collect { _projects.value = it }
        }
    }

    fun addGoal(title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            goalDao.insertGoal(
                GoalEntity(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    description = description.ifBlank { null }
                )
            )
        }
    }

    fun addProject(name: String, description: String, goalId: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            projectDao.insertProject(
                ProjectEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    description = description.ifBlank { null },
                    goalId = goalId
                )
            )
        }
    }
}

@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsState()
    val projects by viewModel.projects.collectAsState()
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var showAddProjectDialog by remember { mutableStateOf(false) }

    // Dynamic gradient background depending on Light/Dark mode (previously
    // hardcoded to always show the dark gradient regardless of theme setting).
    val isLight = MaterialTheme.colorScheme.background == Color(0xFFF5F7FA)
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
                text = "اهداف و پروژه‌ها",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Goals Section
            Text("اهداف بلندمدت", style = MaterialTheme.typography.titleMedium, color = AccentAmber)
            Spacer(modifier = Modifier.height(8.dp))

            if (goals.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().glassCard().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هنوز هدفی تعریف نشده", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(goals) { goal ->
                        Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                            Column {
                                Text(goal.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                                goal.description?.let {
                                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Projects Section
            Text("پروژه‌های فعال", style = MaterialTheme.typography.titleMedium, color = AccentTeal)
            Spacer(modifier = Modifier.height(8.dp))

            if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().glassCard().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("هنوز پروژه‌ای تعریف نشده", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(projects) { project ->
                        Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                            Column {
                                Text(project.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                                project.description?.let {
                                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = "وضعیت: ${project.status}",
                                    color = AccentTeal,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // FABs
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { showAddGoalDialog = true },
                containerColor = AccentAmber,
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "هدف جدید")
            }
            FloatingActionButton(
                onClick = { showAddProjectDialog = true },
                containerColor = AccentBlue,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "پروژه جدید")
            }
        }

        if (showAddGoalDialog) {
            SimpleAddDialog(
                title = "هدف جدید",
                nameLabel = "عنوان هدف",
                onDismiss = { showAddGoalDialog = false },
                onAdd = { name, desc -> viewModel.addGoal(name, desc); showAddGoalDialog = false }
            )
        }
        if (showAddProjectDialog) {
            SimpleAddDialog(
                title = "پروژه جدید",
                nameLabel = "نام پروژه",
                onDismiss = { showAddProjectDialog = false },
                onAdd = { name, desc -> viewModel.addProject(name, desc, null); showAddProjectDialog = false }
            )
        }
    }
}

@Composable
fun SimpleAddDialog(title: String, nameLabel: String, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(nameLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("توضیحات (اختیاری)") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onAdd(name, desc) }, colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
