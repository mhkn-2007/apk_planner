package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.entities.RoutineInstanceEntity
import com.example.lifeos.data.database.entities.RoutineInstanceTaskEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.RoutineTemplateTaskEntity
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
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Routines feature (prompt section 12).
 *
 * Keeps RoutineTemplate (reusable definition) and RoutineInstance (a
 * per-day, freely-editable copy) strictly separate: adding a template to a
 * day snapshots its tasks into RoutineInstanceTask rows, so later edits to
 * an instance never mutate the template it came from.
 */
@HiltViewModel
class RoutinesViewModel @Inject constructor(
    private val routineDao: RoutineDao
) : ViewModel() {

    val templates: StateFlow<List<RoutineTemplateEntity>> =
        routineDao.getAllTemplates()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun startEndOfDay(dateMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis - 1
        return start to end
    }

    fun getTemplateTasks(templateId: String): StateFlow<List<RoutineTemplateTaskEntity>> {
        return routineDao.getTemplateTasks(templateId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getTodaysInstances(): StateFlow<List<RoutineInstanceEntity>> {
        val (start, end) = startEndOfDay(System.currentTimeMillis())
        return routineDao.getInstancesForDateRange(start, end)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getInstanceTasks(instanceId: String): StateFlow<List<RoutineInstanceTaskEntity>> {
        return routineDao.getInstanceTasks(instanceId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    /** Creates a new reusable routine template together with its ordered tasks. */
    fun createTemplate(name: String, description: String, taskTitles: List<String>) {
        if (name.isBlank() || taskTitles.isEmpty()) return
        viewModelScope.launch {
            val template = RoutineTemplateEntity(
                name = name,
                description = description.ifBlank { null }
            )
            routineDao.insertTemplate(template)
            val tasks = taskTitles.mapIndexed { index, title ->
                RoutineTemplateTaskEntity(
                    templateId = template.id,
                    title = title,
                    position = index
                )
            }
            routineDao.insertTemplateTasks(tasks)
        }
    }

    fun deleteTemplate(template: RoutineTemplateEntity) {
        viewModelScope.launch { routineDao.deleteTemplate(template) }
    }

    /**
     * Adds a template to today's plan: creates a RoutineInstance and copies
     * every RoutineTemplateTask into a fresh, independent RoutineInstanceTask.
     */
    fun addTemplateToToday(template: RoutineTemplateEntity) {
        viewModelScope.launch {
            val instance = RoutineInstanceEntity(
                templateId = template.id,
                dateMillis = System.currentTimeMillis()
            )
            routineDao.insertInstance(instance)
            val templateTasks = routineDao.getTemplateTasksOnce(template.id)
            val instanceTasks = templateTasks.map {
                RoutineInstanceTaskEntity(
                    instanceId = instance.id,
                    title = it.title,
                    estimatedDurationMinutes = it.estimatedDurationMinutes,
                    position = it.position
                )
            }
            routineDao.insertInstanceTasks(instanceTasks)
        }
    }

    fun toggleInstanceTaskComplete(task: RoutineInstanceTaskEntity) {
        viewModelScope.launch {
            routineDao.updateInstanceTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteInstance(instance: RoutineInstanceEntity) {
        viewModelScope.launch { routineDao.deleteInstance(instance) }
    }
}

@Composable
fun RoutinesScreen(viewModel: RoutinesViewModel = hiltViewModel()) {
    val templates by viewModel.templates.collectAsState()
    val todaysInstances by viewModel.getTodaysInstances().collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    val isLight = !LocalIsDarkTheme.current
    val bgGradient = if (isLight) {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    }

    Box(modifier = Modifier.fillMaxSize().background(bgGradient)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "روتین‌ها",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (todaysInstances.isNotEmpty()) {
                Text(
                    "امروز",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    todaysInstances.forEach { instance ->
                        RoutineInstanceCard(instance = instance, viewModel = viewModel)
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))
            }

            Text(
                "روتین‌های ذخیره‌شده",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (templates.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔁", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("هنوز روتینی نساخته‌اید", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium)
                        Text("مثل روتین صبحگاهی یا شب", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(templates) { template ->
                        RoutineTemplateCard(
                            template = template,
                            viewModel = viewModel,
                            onAddToToday = { viewModel.addTemplateToToday(template) },
                            onDelete = { viewModel.deleteTemplate(template) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = AccentBlue,
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = "روتین جدید")
        }

        if (showCreateDialog) {
            CreateRoutineDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, desc, tasks ->
                    viewModel.createTemplate(name, desc, tasks)
                    showCreateDialog = false
                }
            )
        }
    }
}

@Composable
fun RoutineTemplateCard(
    template: RoutineTemplateEntity,
    viewModel: RoutinesViewModel,
    onAddToToday: () -> Unit,
    onDelete: () -> Unit
) {
    val tasks by viewModel.getTemplateTasks(template.id).collectAsState()

    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    template.description?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف روتین", tint = AccentRed)
                }
            }
            if (tasks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    tasks.joinToString(" • ") { it.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAddToToday,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f))
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("افزودن به امروز", color = AccentBlue)
            }
        }
    }
}

@Composable
fun RoutineInstanceCard(
    instance: RoutineInstanceEntity,
    viewModel: RoutinesViewModel
) {
    val tasks by viewModel.getInstanceTasks(instance.id).collectAsState()
    val completed = tasks.count { it.isCompleted }

    Box(modifier = Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "پیشرفت: $completed/${tasks.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.deleteInstance(instance) }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = AccentRed)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            tasks.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = task.isCompleted,
                        onCheckedChange = { viewModel.toggleInstanceTaskComplete(task) }
                    )
                    Text(
                        task.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
                    )
                }
            }
        }
    }
}

@Composable
fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, taskTitles: List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var newTaskTitle by remember { mutableStateOf("") }
    val taskTitles = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("روتین جدید", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام روتین (مثلا روتین صبحگاهی)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("توضیحات (اختیاری)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("کارهای روتین:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                taskTitles.forEachIndexed { index, taskTitle ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${index + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
                        Text(taskTitle, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                        IconButton(onClick = { taskTitles.removeAt(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف", tint = AccentRed)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        label = { Text("کار جدید") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            taskTitles.add(newTaskTitle.trim())
                            newTaskTitle = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "افزودن", tint = AccentBlue)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, description, taskTitles.toList()) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                enabled = name.isNotBlank() && taskTitles.isNotEmpty()
            ) {
                Text("ذخیره روتین")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
