package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*

private enum class TaskStatusFilter { ALL, ACTIVE, COMPLETED }
private enum class TaskSortOrder { DUE_DATE, PRIORITY, CREATED }

/**
 * Independent "Tasks" section (prompt section 5 lists Tasks as its own
 * top-level nav item, separate from Today). Unlike TodayScreen — which only
 * shows tasks due today — this shows every task, with search, status/priority
 * filtering, and sorting, matching the "search tasks / filter tasks / sort
 * tasks" requirements in section 7.
 *
 * Reuses TodayViewModel rather than duplicating completion/delete/subtask/
 * reminder logic in a second ViewModel: that logic (habit streak sync, alarm
 * scheduling, etc.) needs to stay in exactly one place.
 */
@Composable
fun TasksScreen(
    viewModel: TodayViewModel = hiltViewModel()
) {
    val allTasksState = remember { mutableStateOf<List<TaskEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        viewModel.observeAllTasks().collect { allTasksState.value = it }
    }
    val allTasks = allTasksState.value

    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(TaskStatusFilter.ACTIVE) }
    var sortOrder by remember { mutableStateOf(TaskSortOrder.DUE_DATE) }
    var selectedTaskForEdit by remember { mutableStateOf<TaskEntity?>(null) }

    val filtered = remember(allTasks, query, statusFilter, sortOrder) {
        allTasks
            .filter { task ->
                val matchesQuery = query.isBlank() ||
                    task.title.contains(query, ignoreCase = true) ||
                    (task.description?.contains(query, ignoreCase = true) == true)
                val matchesStatus = when (statusFilter) {
                    TaskStatusFilter.ALL -> true
                    TaskStatusFilter.ACTIVE -> !task.isCompleted
                    TaskStatusFilter.COMPLETED -> task.isCompleted
                }
                matchesQuery && matchesStatus
            }
            .let { list ->
                when (sortOrder) {
                    TaskSortOrder.DUE_DATE -> list.sortedWith(compareBy(nullsLast<Long>()) { it.dueDateMillis })
                    TaskSortOrder.PRIORITY -> list.sortedByDescending { it.priority }
                    TaskSortOrder.CREATED -> list.sortedByDescending { it.createdAtMillis }
                }
            }
    }

    val isDark = LocalIsDarkTheme.current
    val bgGradient = if (isDark) {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "کارها",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "همه‌ی کارها، صرف‌نظر از تاریخ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("جستجوی کار...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = statusFilter == TaskStatusFilter.ACTIVE,
                        onClick = { statusFilter = TaskStatusFilter.ACTIVE },
                        label = { Text("فعال") }
                    )
                    FilterChip(
                        selected = statusFilter == TaskStatusFilter.COMPLETED,
                        onClick = { statusFilter = TaskStatusFilter.COMPLETED },
                        label = { Text("تکمیل‌شده") }
                    )
                    FilterChip(
                        selected = statusFilter == TaskStatusFilter.ALL,
                        onClick = { statusFilter = TaskStatusFilter.ALL },
                        label = { Text("همه") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "مرتب‌سازی:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    FilterChip(
                        selected = sortOrder == TaskSortOrder.DUE_DATE,
                        onClick = { sortOrder = TaskSortOrder.DUE_DATE },
                        label = { Text("تاریخ سررسید") }
                    )
                    FilterChip(
                        selected = sortOrder == TaskSortOrder.PRIORITY,
                        onClick = { sortOrder = TaskSortOrder.PRIORITY },
                        label = { Text("اولویت") }
                    )
                    FilterChip(
                        selected = sortOrder == TaskSortOrder.CREATED,
                        onClick = { sortOrder = TaskSortOrder.CREATED },
                        label = { Text("جدیدترین") }
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp).glassCard().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isNotBlank()) "کاری با این عنوان پیدا نشد" else "کاری در این فیلتر نیست",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { task ->
                        GlassTaskItem(
                            task = task,
                            onToggleComplete = { viewModel.toggleTaskComplete(task) },
                            onDelete = { viewModel.deleteTask(task) },
                            onClick = { selectedTaskForEdit = task }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        if (selectedTaskForEdit != null) {
            EditTaskDialog(
                task = selectedTaskForEdit!!,
                viewModel = viewModel,
                onDismiss = { selectedTaskForEdit = null },
                onUpdate = { title, desc, priority, timeOfDay, hour, min ->
                    viewModel.updateTask(selectedTaskForEdit!!, title, desc, priority, timeOfDay, hour, min)
                    selectedTaskForEdit = null
                }
            )
        }
    }
}
