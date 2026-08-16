package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
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

private enum class TaskStatusFilter { ALL, ACTIVE, COMPLETED, ARCHIVED }
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

    val archivedTasksState = remember { mutableStateOf<List<TaskEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        viewModel.observeArchivedTasks().collect { archivedTasksState.value = it }
    }
    val archivedTasks = archivedTasksState.value
    val categories by viewModel.categories.collectAsState()

    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(TaskStatusFilter.ACTIVE) }
    var sortOrder by remember { mutableStateOf(TaskSortOrder.DUE_DATE) }
    var selectedTaskForEdit by remember { mutableStateOf<TaskEntity?>(null) }
    // null = no category filter applied (prompt section 44: Category).
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var showCategoryManager by remember { mutableStateOf(false) }

    val filtered = remember(allTasks, archivedTasks, query, statusFilter, sortOrder, selectedCategoryId) {
        val source = if (statusFilter == TaskStatusFilter.ARCHIVED) archivedTasks else allTasks
        source
            .filter { task ->
                val matchesQuery = query.isBlank() ||
                    task.title.contains(query, ignoreCase = true) ||
                    (task.description?.contains(query, ignoreCase = true) == true)
                val matchesStatus = when (statusFilter) {
                    TaskStatusFilter.ALL -> true
                    TaskStatusFilter.ACTIVE -> !task.isCompleted
                    TaskStatusFilter.COMPLETED -> task.isCompleted
                    TaskStatusFilter.ARCHIVED -> true
                }
                val matchesCategory = selectedCategoryId == null || task.categoryId == selectedCategoryId
                matchesQuery && matchesStatus && matchesCategory
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
                    FilterChip(
                        selected = statusFilter == TaskStatusFilter.ARCHIVED,
                        onClick = { statusFilter = TaskStatusFilter.ARCHIVED },
                        label = { Text("آرشیو") }
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

                if (categories.isNotEmpty() || selectedCategoryId != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("همه‌ی دسته‌ها") }
                        )
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategoryId == category.id,
                                onClick = { selectedCategoryId = if (selectedCategoryId == category.id) null else category.id },
                                label = { Text(category.name) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = { showCategoryManager = true }) {
                    Icon(Icons.Default.Sell, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مدیریت دسته‌بندی‌ها", style = MaterialTheme.typography.bodySmall)
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
                            onClick = { selectedTaskForEdit = task },
                            onArchive = {
                                if (statusFilter == TaskStatusFilter.ARCHIVED) {
                                    viewModel.unarchiveTask(task)
                                } else {
                                    viewModel.archiveTask(task)
                                }
                            },
                            onDuplicate = { viewModel.duplicateTask(task) },
                            isArchived = statusFilter == TaskStatusFilter.ARCHIVED
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
                onUpdate = { title, desc, priority, timeOfDay, hour, min, durationMinutes ->
                    viewModel.updateTask(selectedTaskForEdit!!, title, desc, priority, timeOfDay, hour, min, durationMinutes)
                    selectedTaskForEdit = null
                }
            )
        }

        if (showCategoryManager) {
            CategoryManagerDialog(
                categories = categories,
                onDismiss = { showCategoryManager = false },
                onCreate = { name, color -> viewModel.createCategory(name, color) },
                onDelete = { category ->
                    if (selectedCategoryId == category.id) selectedCategoryId = null
                    viewModel.deleteCategory(category)
                }
            )
        }
    }
}

/**
 * Create/delete UI for categories (prompt section 44/50). Kept minimal and
 * scoped to the Tasks screen — categories are an organizational aid, not a
 * separate top-level concept the prompt calls for its own nav item, so a
 * dedicated screen would be over-building this.
 */
@Composable
private fun CategoryManagerDialog(
    categories: List<com.example.lifeos.data.database.entities.CategoryEntity>,
    onDismiss: () -> Unit,
    onCreate: (name: String, colorHex: String) -> Unit,
    onDelete: (com.example.lifeos.data.database.entities.CategoryEntity) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    val palette = listOf("#4A90D9", "#50C878", "#E67E22", "#E74C3C", "#9B59B6", "#1ABC9C")
    var selectedColor by remember { mutableStateOf(palette.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دسته‌بندی‌ها", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column {
                categories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(category.colorHex)),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(category.name, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                        IconButton(onClick = { onDelete(category) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف دسته", tint = AccentRed)
                        }
                    }
                }
                if (categories.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }
                Text("دسته‌ی جدید:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("نام دسته") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                                .then(
                                    if (selectedColor == hex) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, androidx.compose.foundation.shape.CircleShape)
                                    } else Modifier
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreate(newName, selectedColor)
                            newName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    enabled = newName.isNotBlank()
                ) {
                    Text("افزودن دسته")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}
