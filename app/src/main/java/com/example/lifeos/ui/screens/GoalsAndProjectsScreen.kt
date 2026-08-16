package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.MilestoneDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.GoalMilestoneEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.ProjectMilestoneEntity
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val goalDao: GoalDao,
    private val projectDao: ProjectDao,
    private val milestoneDao: MilestoneDao
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

    fun getMilestonesForGoal(goalId: String): StateFlow<List<GoalMilestoneEntity>> {
        return milestoneDao.getMilestonesForGoal(goalId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun addGoalMilestone(goalId: String, title: String, position: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            milestoneDao.insertGoalMilestone(
                GoalMilestoneEntity(goalId = goalId, title = title, position = position)
            )
        }
    }

    fun toggleGoalMilestone(milestone: GoalMilestoneEntity) {
        viewModelScope.launch {
            milestoneDao.updateGoalMilestone(milestone.copy(isCompleted = !milestone.isCompleted))
        }
    }

    fun deleteGoalMilestone(milestone: GoalMilestoneEntity) {
        viewModelScope.launch { milestoneDao.deleteGoalMilestone(milestone) }
    }

    fun getMilestonesForProject(projectId: String): StateFlow<List<ProjectMilestoneEntity>> {
        return milestoneDao.getMilestonesForProject(projectId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun addProjectMilestone(projectId: String, title: String, position: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            milestoneDao.insertProjectMilestone(
                ProjectMilestoneEntity(projectId = projectId, title = title, position = position)
            )
        }
    }

    fun toggleProjectMilestone(milestone: ProjectMilestoneEntity) {
        viewModelScope.launch {
            milestoneDao.updateProjectMilestone(milestone.copy(isCompleted = !milestone.isCompleted))
        }
    }

    fun deleteProjectMilestone(milestone: ProjectMilestoneEntity) {
        viewModelScope.launch { milestoneDao.deleteProjectMilestone(milestone) }
    }
}

@Composable
fun GoalsScreen(viewModel: ProjectsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsState()
    var showAddGoalDialog by remember { mutableStateOf(false) }

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
                text = "اهداف",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "چیزهایی که می‌خواهید در بلندمدت به آن‌ها برسید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                    modifier = Modifier.weight(1f)
                ) {
                    items(goals) { goal ->
                        GoalCard(goal = goal, viewModel = viewModel)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddGoalDialog = true },
            containerColor = AccentAmber,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "هدف جدید")
        }

        if (showAddGoalDialog) {
            SimpleAddDialog(
                title = "هدف جدید",
                nameLabel = "عنوان هدف",
                onDismiss = { showAddGoalDialog = false },
                onAdd = { name, desc -> viewModel.addGoal(name, desc); showAddGoalDialog = false }
            )
        }
    }
}

@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel = hiltViewModel()) {
    val projects by viewModel.projects.collectAsState()
    var showAddProjectDialog by remember { mutableStateOf(false) }

    // Dynamic gradient background depending on Light/Dark mode (previously
    // hardcoded to always show the dark gradient regardless of theme setting,
    // and even after that fix, relying on a fragile exact-color comparison
    // against the light background constant). We now read the same
    // isDarkMode flag the rest of the app uses via LifeOSTheme, so this stays
    // correct even if the light/dark palette constants change later.
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
                text = "پروژه‌ها",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "کارهایی که در حال پیش‌بردن آن‌ها هستید",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                        ProjectCard(project = project, viewModel = viewModel)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddProjectDialog = true },
            containerColor = AccentBlue,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "پروژه جدید")
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
fun GoalCard(goal: GoalEntity, viewModel: ProjectsViewModel) {
    val milestones by viewModel.getMilestonesForGoal(goal.id).collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var newMilestoneTitle by remember { mutableStateOf("") }
    val completed = milestones.count { it.isCompleted }

    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(goal.title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    goal.description?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                    if (milestones.isNotEmpty()) {
                        Text(
                            "مایلستون‌ها: $completed/${milestones.size}",
                            color = AccentAmber,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                milestones.forEach { milestone ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = milestone.isCompleted,
                            onCheckedChange = { viewModel.toggleGoalMilestone(milestone) }
                        )
                        Text(
                            milestone.title,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (milestone.isCompleted) TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { viewModel.deleteGoalMilestone(milestone) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = AccentRed)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newMilestoneTitle,
                        onValueChange = { newMilestoneTitle = it },
                        label = { Text("مایلستون جدید") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newMilestoneTitle.isNotBlank()) {
                            viewModel.addGoalMilestone(goal.id, newMilestoneTitle, milestones.size)
                            newMilestoneTitle = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "افزودن", tint = AccentAmber)
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectCard(project: ProjectEntity, viewModel: ProjectsViewModel) {
    val milestones by viewModel.getMilestonesForProject(project.id).collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var newMilestoneTitle by remember { mutableStateOf("") }
    val completed = milestones.count { it.isCompleted }

    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(project.name, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
                    project.description?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        text = "وضعیت: ${project.status}",
                        color = AccentTeal,
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (milestones.isNotEmpty()) {
                        Text(
                            "مایلستون‌ها: $completed/${milestones.size}",
                            color = AccentTeal,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                milestones.forEach { milestone ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = milestone.isCompleted,
                            onCheckedChange = { viewModel.toggleProjectMilestone(milestone) }
                        )
                        Text(
                            milestone.title,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (milestone.isCompleted) TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { viewModel.deleteProjectMilestone(milestone) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = AccentRed)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newMilestoneTitle,
                        onValueChange = { newMilestoneTitle = it },
                        label = { Text("مایلستون جدید") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newMilestoneTitle.isNotBlank()) {
                            viewModel.addProjectMilestone(project.id, newMilestoneTitle, milestones.size)
                            newMilestoneTitle = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "افزودن", tint = AccentTeal)
                    }
                }
            }
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
