package com.example.lifeos.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.planner.DeterministicPlannerEngine
import com.example.lifeos.domain.repositories.TaskRepository
import com.example.lifeos.domain.usecases.GenerateRecurringTaskOccurrencesUseCase
import com.example.lifeos.domain.usecases.GetTodayTasksUseCase
import com.example.lifeos.domain.usecases.RecurrenceRule
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import com.example.lifeos.util.AlarmScheduler
import com.example.lifeos.util.JalaliCalendarUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID
import java.util.Calendar

/**
 * Read-only summary of what [DeterministicPlannerEngine] found for today's
 * task list (prompt sections 32-34: workload calculation + conflict
 * detection, surfaced in the UI rather than only existing as unused
 * domain logic).
 */
data class PlanningInsight(
    val totalWorkloadMinutes: Int = 0,
    val availableMinutes: Int = DEFAULT_AVAILABLE_MINUTES,
    val conflicts: List<Pair<TaskEntity, TaskEntity>> = emptyList(),
    val isOverloaded: Boolean = false
) {
    companion object {
        // Prompt section 33 example uses a 5-hour available window; we default
        // to a reasonable single-day working window when no user preference
        // for available time exists yet.
        const val DEFAULT_AVAILABLE_MINUTES = 5 * 60
    }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val getTodayTasksUseCase: GetTodayTasksUseCase,
    private val taskRepository: TaskRepository,
    private val habitDao: HabitDao,
    private val habitLogDao: com.example.lifeos.data.database.dao.HabitLogDao,
    private val alarmScheduler: AlarmScheduler,
    private val plannerEngine: DeterministicPlannerEngine,
    private val generateRecurringOccurrences: GenerateRecurringTaskOccurrencesUseCase,
    private val goalDao: GoalDao,
    private val projectDao: ProjectDao,
    private val categoryDao: com.example.lifeos.data.database.dao.CategoryDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks: StateFlow<List<TaskEntity>> = _tasks.asStateFlow()

    val habits: StateFlow<List<HabitEntity>> = habitDao.getAllHabits()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Exposed so EditTaskDialog can offer a Goal/Project picker (prompt
    // section 15-16: connecting daily tasks to long-term goals/projects).
    val goals: StateFlow<List<GoalEntity>> = goalDao.getAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = projectDao.getAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Categories (prompt section 44: Category). Exposed the same way as
    // goals/projects above so EditTaskDialog/TasksScreen can offer a picker
    // and filter chips without each needing their own DAO wiring.
    val categories: StateFlow<List<com.example.lifeos.data.database.entities.CategoryEntity>> =
        categoryDao.getAllCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Per-taskId caches — see the comment on
    // ProjectsViewModel.getMilestonesForGoal for why these exist: without
    // memoization, EditTaskDialog calling getSubtasksForTask/
    // getRemindersForTask directly in its @Composable body got a fresh,
    // momentarily-empty StateFlow on every recomposition.
    private val subtaskFlows = mutableMapOf<String, StateFlow<List<com.example.lifeos.data.database.entities.SubtaskEntity>>>()
    private val reminderFlows = mutableMapOf<String, StateFlow<List<com.example.lifeos.data.database.entities.ReminderEntity>>>()

    fun createCategory(name: String, colorHex: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryDao.insertCategory(
                com.example.lifeos.data.database.entities.CategoryEntity(name = name.trim(), colorHex = colorHex)
            )
        }
    }

    /** Deletes a category and clears it off any task that referenced it (never deletes the tasks themselves). */
    fun deleteCategory(category: com.example.lifeos.data.database.entities.CategoryEntity) {
        viewModelScope.launch {
            categoryDao.clearCategoryFromTasks(category.id)
            categoryDao.deleteCategory(category)
        }
    }

    fun linkTaskToCategory(task: TaskEntity, categoryId: String?) {
        viewModelScope.launch { taskRepository.updateTask(task.copy(categoryId = categoryId)) }
    }

    fun linkTaskToGoal(task: TaskEntity, goalId: String?) {
        viewModelScope.launch { taskRepository.updateTask(task.copy(goalId = goalId)) }
    }

    fun linkTaskToProject(task: TaskEntity, projectId: String?) {
        viewModelScope.launch { taskRepository.updateTask(task.copy(projectId = projectId)) }
    }

    /**
     * All tasks regardless of due date, for the standalone Tasks screen
     * (prompt section 5/7). Kept separate from [tasks], which stays
     * today-only for the Today screen.
     */
    fun observeAllTasks(): Flow<List<TaskEntity>> = taskRepository.getAllTasks()

    /** Archived tasks (prompt section 7) for the Tasks screen's "Archived" filter. */
    fun observeArchivedTasks(): Flow<List<TaskEntity>> = taskRepository.getArchivedTasks()

    private val _planningInsight = MutableStateFlow(PlanningInsight())
    val planningInsight: StateFlow<PlanningInsight> = _planningInsight.asStateFlow()

    init {
        viewModelScope.launch {
            getTodayTasksUseCase().collect { list ->
                _tasks.value = list
                recomputePlanningInsight(list)
            }
        }
        refreshAllRecurringSeries()
    }

    private fun recomputePlanningInsight(tasks: List<TaskEntity>) {
        val activeTasks = tasks.filter { !it.isCompleted }
        val workload = plannerEngine.calculateTotalWorkload(activeTasks)
        val conflicts = plannerEngine.detectConflicts(activeTasks)
        _planningInsight.value = PlanningInsight(
            totalWorkloadMinutes = workload,
            availableMinutes = PlanningInsight.DEFAULT_AVAILABLE_MINUTES,
            conflicts = conflicts,
            isOverloaded = workload > PlanningInsight.DEFAULT_AVAILABLE_MINUTES
        )
    }

    fun addTask(
        title: String,
        description: String,
        priority: Int,
        timeOfDay: String? = null,
        customHour: Int? = null,
        customMinute: Int? = null,
        recurrenceRule: RecurrenceRule? = null,
        estimatedDurationMinutes: Int? = null,
        isAlarmRing: Boolean = true
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var alarmTime: Long? = null

            if (timeOfDay != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = now
                when (timeOfDay) {
                    "MORNING" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 9)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                    }
                    "AFTERNOON" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 15)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                    }
                    "NIGHT" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 21)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                    }
                    "CUSTOM" -> {
                        if (customHour != null && customMinute != null) {
                            cal.set(Calendar.HOUR_OF_DAY, customHour)
                            cal.set(Calendar.MINUTE, customMinute)
                            cal.set(Calendar.SECOND, 0)
                        }
                    }
                }
                alarmTime = cal.timeInMillis
                if (alarmTime < now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    alarmTime = cal.timeInMillis
                }
            }

            // Time blocking (prompt section 14): a task with both a
            // scheduled time and an estimated duration becomes a concrete
            // time block (start -> start+duration), not just a reminder
            // instant. This is what CalendarScreen's timeline view and
            // DeterministicPlannerEngine.detectConflicts actually key off.
            val validDuration = estimatedDurationMinutes?.takeIf { it > 0 }
            val computedEndTime = if (alarmTime != null && validDuration != null) {
                alarmTime + validDuration * 60_000L
            } else null

            val recurrenceGroupId = if (recurrenceRule != null) UUID.randomUUID().toString() else null
            val task = TaskEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description.ifBlank { null },
                priority = priority,
                dueDateMillis = System.currentTimeMillis(),
                timeOfDay = timeOfDay,
                alarmTimeMillis = alarmTime,
                startTimeMillis = if (alarmTime != null && validDuration != null) alarmTime else null,
                endTimeMillis = computedEndTime,
                estimatedDurationMinutes = validDuration,
                isAlarmRing = isAlarmRing,
                recurrenceRule = recurrenceRule?.encode(),
                recurrenceGroupId = recurrenceGroupId
            )
            taskRepository.insertTask(task)

            alarmTime?.let {
                alarmScheduler.scheduleAlarm(context, task.id, task.title, it, isAlarmRing)
            }

            // Materialize the rest of this series' occurrences right away so
            // Today/Calendar show them without waiting for a background pass.
            if (recurrenceRule != null) {
                generateRecurringOccurrences(task)
            }
        }
    }

    /**
     * Regenerates upcoming occurrences for every existing recurring series.
     * Safe to call on every app start: occurrence generation is idempotent
     * (see [GenerateRecurringTaskOccurrencesUseCase]), so this only fills in
     * newly-entered days of the rolling window rather than duplicating rows.
     */
    private fun refreshAllRecurringSeries() {
        viewModelScope.launch {
            val recurringSources = taskRepository.getAllTasks().first()
            recurringSources
                .filter { it.recurrenceRule != null && it.recurrenceGroupId != null }
                .groupBy { it.recurrenceGroupId }
                .values
                .mapNotNull { group -> group.minByOrNull { it.dueDateMillis ?: Long.MAX_VALUE } }
                .forEach { earliestInGroup -> generateRecurringOccurrences(earliestInGroup) }
        }
    }

    fun updateTask(task: TaskEntity, newTitle: String, newDesc: String, newPriority: Int, timeOfDay: String? = null, customHour: Int? = null, customMinute: Int? = null, estimatedDurationMinutes: Int? = null, isAlarmRing: Boolean = task.isAlarmRing) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var alarmTime: Long? = null

            if (timeOfDay != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = now
                when (timeOfDay) {
                    "MORNING" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 9)
                        cal.set(Calendar.MINUTE, 0)
                    }
                    "AFTERNOON" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 15)
                        cal.set(Calendar.MINUTE, 0)
                    }
                    "NIGHT" -> {
                        cal.set(Calendar.HOUR_OF_DAY, 21)
                        cal.set(Calendar.MINUTE, 0)
                    }
                    "CUSTOM" -> {
                        if (customHour != null && customMinute != null) {
                            cal.set(Calendar.HOUR_OF_DAY, customHour)
                            cal.set(Calendar.MINUTE, customMinute)
                        }
                    }
                }
                alarmTime = cal.timeInMillis
                if (alarmTime < now) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    alarmTime = cal.timeInMillis
                }
            }

            // Same time-blocking derivation as addTask (prompt section 14):
            // an explicit scheduled time + duration together define the
            // block's start/end, not just an alarm instant.
            val validDuration = estimatedDurationMinutes?.takeIf { it > 0 }
            val computedEndTime = if (alarmTime != null && validDuration != null) {
                alarmTime + validDuration * 60_000L
            } else null

            val updated = task.copy(
                title = newTitle,
                description = newDesc.ifBlank { null },
                priority = newPriority,
                timeOfDay = timeOfDay,
                alarmTimeMillis = alarmTime,
                startTimeMillis = if (alarmTime != null && validDuration != null) alarmTime else null,
                endTimeMillis = computedEndTime,
                estimatedDurationMinutes = validDuration ?: task.estimatedDurationMinutes,
                isAlarmRing = isAlarmRing
            )
            taskRepository.updateTask(updated)

            // Reschedule or cancel alarm
            alarmScheduler.cancelAlarm(context, task.id)
            alarmTime?.let {
                alarmScheduler.scheduleAlarm(context, task.id, updated.title, it, isAlarmRing)
            }
        }
    }

    fun toggleTaskComplete(task: TaskEntity) {
        viewModelScope.launch {
            val newCompletedState = !task.isCompleted
            taskRepository.updateTask(
                task.copy(
                    isCompleted = newCompletedState,
                    completedAtMillis = if (newCompletedState) System.currentTimeMillis() else task.completedAtMillis
                )
            )

            val habitId = task.habitId
            if (habitId != null) {
                val habit = habitDao.getHabitById(habitId) ?: return@launch
                val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())

                if (newCompletedState) {
                    // Only count once per day, mirroring the manual habit
                    // check-in logic in HabitsScreen.
                    if (habit.lastCompletedDate != todayStr) {
                        val newStreak = habit.currentStreak + 1
                        habitDao.updateHabit(
                            habit.copy(
                                currentStreak = newStreak,
                                longestStreak = maxOf(habit.longestStreak, newStreak),
                                lastCompletedDate = todayStr
                            )
                        )
                        // Keep the per-day history (used for weekly/monthly
                        // stats, prompt section 17) in sync with completions
                        // that come from a habit-linked task, not just the
                        // manual check-in on the Habits screen.
                        habitLogDao.insertLog(
                            com.example.lifeos.data.database.entities.HabitLogEntity(
                                habitId = habitId,
                                dateKey = todayStr
                            )
                        )
                    }
                } else {
                    // Un-completing the task should undo today's check-in.
                    if (habit.lastCompletedDate == todayStr) {
                        habitDao.updateHabit(
                            habit.copy(
                                currentStreak = maxOf(0, habit.currentStreak - 1),
                                lastCompletedDate = null
                            )
                        )
                        habitLogDao.deleteLogForDate(habitId, todayStr)
                    }
                }
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.deleteTask(task)
            alarmScheduler.cancelAlarm(context, task.id)
        }
    }

    /**
     * Archives a task (prompt section 7: "Archive tasks") — hides it from
     * every normal list without deleting it, so its reminders/subtasks and
     * any analytics history stay intact. The task's own alarm is cancelled
     * since an archived task shouldn't keep notifying the user.
     */
    fun archiveTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.setArchived(task.id, true)
            alarmScheduler.cancelAlarm(context, task.id)
        }
    }

    fun unarchiveTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.setArchived(task.id, false)
        }
    }

    /**
     * Duplicates a task (prompt section 7: "Duplicate tasks"). The copy
     * gets a new id/creation time and starts uncompleted and unarchived —
     * everything else (title, description, timing, priority, links to a
     * goal/project, etc.) carries over. Subtasks/reminders are NOT copied:
     * the prompt only asks for duplicating the task itself, and silently
     * cloning reminders would double-schedule alarms the user didn't ask
     * for.
     */
    fun duplicateTask(task: TaskEntity) {
        viewModelScope.launch {
            // taskRepository.duplicateTask also copies subtasks and
            // reminders (prompt sections 9-10) — a duplicate that silently
            // dropped them wouldn't be a real copy of the task.
            val copy = taskRepository.duplicateTask(task)
            copy.alarmTimeMillis?.let {
                alarmScheduler.scheduleAlarm(context, copy.id, copy.title, it, copy.isAlarmRing)
            }
            taskRepository.getRemindersForTask(copy.id).first()
                .filter { it.isEnabled }
                .forEach { reminder ->
                    alarmScheduler.scheduleReminderAlarm(
                        context = context,
                        reminderId = reminder.id,
                        taskId = copy.id,
                        title = copy.title,
                        message = reminder.message,
                        triggerAtMillis = reminder.triggerTimeMillis,
                        isAlarmRing = reminder.isAlarmRing
                    )
                }
        }
    }

    // --- Subtasks ---

    fun getSubtasksForTask(taskId: String): StateFlow<List<com.example.lifeos.data.database.entities.SubtaskEntity>> {
        return subtaskFlows.getOrPut(taskId) {
            taskRepository.getSubtasksForTask(taskId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    fun addSubtask(taskId: String, title: String, position: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            taskRepository.insertSubtask(
                com.example.lifeos.data.database.entities.SubtaskEntity(
                    taskId = taskId,
                    title = title,
                    position = position
                )
            )
        }
    }

    fun toggleSubtaskComplete(subtask: com.example.lifeos.data.database.entities.SubtaskEntity) {
        viewModelScope.launch {
            taskRepository.updateSubtask(subtask.copy(isCompleted = !subtask.isCompleted))
        }
    }

    fun deleteSubtask(subtask: com.example.lifeos.data.database.entities.SubtaskEntity) {
        viewModelScope.launch {
            taskRepository.deleteSubtask(subtask)
        }
    }

    // --- Reminders ---

    fun getRemindersForTask(taskId: String): StateFlow<List<com.example.lifeos.data.database.entities.ReminderEntity>> {
        return reminderFlows.getOrPut(taskId) {
            taskRepository.getRemindersForTask(taskId)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }
    }

    fun addReminder(task: TaskEntity, triggerTimeMillis: Long, message: String?, isAlarmRing: Boolean = true) {
        viewModelScope.launch {
            val reminder = com.example.lifeos.data.database.entities.ReminderEntity(
                taskId = task.id,
                triggerTimeMillis = triggerTimeMillis,
                message = message,
                isAlarmRing = isAlarmRing
            )
            taskRepository.insertReminder(reminder)
            alarmScheduler.scheduleReminderAlarm(
                context = context,
                reminderId = reminder.id,
                taskId = task.id,
                title = task.title,
                message = message,
                triggerAtMillis = triggerTimeMillis,
                isAlarmRing = isAlarmRing
            )
        }
    }

    fun setReminderEnabled(reminder: com.example.lifeos.data.database.entities.ReminderEntity, task: TaskEntity, enabled: Boolean) {
        viewModelScope.launch {
            taskRepository.updateReminder(reminder.copy(isEnabled = enabled))
            if (enabled) {
                alarmScheduler.scheduleReminderAlarm(
                    context = context,
                    reminderId = reminder.id,
                    taskId = task.id,
                    title = task.title,
                    message = reminder.message,
                    triggerAtMillis = reminder.triggerTimeMillis,
                    isAlarmRing = reminder.isAlarmRing
                )
            } else {
                alarmScheduler.cancelReminderAlarm(context, reminder.id)
            }
        }
    }

    fun deleteReminder(reminder: com.example.lifeos.data.database.entities.ReminderEntity) {
        viewModelScope.launch {
            taskRepository.deleteReminder(reminder)
            alarmScheduler.cancelReminderAlarm(context, reminder.id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: TodayViewModel = hiltViewModel(),
    onAddTaskClick: () -> Unit = {},
    onStartFocus: (TaskEntity) -> Unit = {}
) {
    val tasks by viewModel.tasks.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val planningInsight by viewModel.planningInsight.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showHabitsDialog by remember { mutableStateOf(false) }
    var selectedHabitForConfig by remember { mutableStateOf<HabitEntity?>(null) }
    var selectedTaskForEdit by remember { mutableStateOf<TaskEntity?>(null) }

    val todayJalali = remember { JalaliCalendarUtil.gregorianToJalali(System.currentTimeMillis()) }

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
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = todayJalali.format(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.glassCard(cornerRadius = 12.dp)
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = AccentGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("عادت‌ها", color = AccentGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (planningInsight.isOverloaded || planningInsight.conflicts.isNotEmpty()) {
                PlanningInsightCard(
                    insight = planningInsight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp)
                )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "روی دکمه + بزنید یا یک عادت انتخاب کنید",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(tasks) { task ->
                        GlassTaskItem(
                            task = task,
                            onToggleComplete = { viewModel.toggleTaskComplete(task) },
                            onDelete = { viewModel.deleteTask(task) },
                            onClick = { selectedTaskForEdit = task },
                            onStartFocus = { onStartFocus(task) },
                            onArchive = { viewModel.archiveTask(task) },
                            onDuplicate = { viewModel.duplicateTask(task) }
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
                onAdd = { title, desc, priority, timeOfDay, hour, min, recurrenceRule, durationMinutes, isAlarmRing ->
                    viewModel.addTask(title, desc, priority, timeOfDay, hour, min, recurrenceRule, durationMinutes, isAlarmRing)
                    showAddDialog = false
                }
            )
        }

        if (showHabitsDialog) {
            SelectHabitDialog(
                habits = habits,
                onDismiss = { showHabitsDialog = false },
                onSelect = { habit ->
                    selectedHabitForConfig = habit
                    showHabitsDialog = false
                }
            )
        }

        if (selectedHabitForConfig != null) {
            ConfigureHabitDialog(
                habitName = selectedHabitForConfig!!.name,
                onDismiss = { selectedHabitForConfig = null },
                onAdd = { title, desc, priority, timeOfDay, hour, min ->
                    viewModel.addTask(title, desc, priority, timeOfDay, hour, min)
                    selectedHabitForConfig = null
                }
            )
        }

        if (selectedTaskForEdit != null) {
            EditTaskDialog(
                task = selectedTaskForEdit!!,
                viewModel = viewModel,
                onDismiss = { selectedTaskForEdit = null },
                onUpdate = { title, desc, priority, timeOfDay, hour, min, durationMinutes, isAlarmRing ->
                    viewModel.updateTask(selectedTaskForEdit!!, title, desc, priority, timeOfDay, hour, min, durationMinutes, isAlarmRing)
                    selectedTaskForEdit = null
                }
            )
        }
    }
}

/**
 * Surfaces [DeterministicPlannerEngine] output (prompt sections 33-34):
 * warns when today's estimated workload exceeds the available window, and
 * lists any time-overlapping tasks so the user can resolve them manually.
 */
@Composable
fun PlanningInsightCard(insight: PlanningInsight, modifier: Modifier = Modifier) {
    Box(modifier = modifier.glassCard().padding(16.dp)) {
        Column {
            if (insight.isOverloaded) {
                val workloadHours = insight.totalWorkloadMinutes / 60f
                val availableHours = insight.availableMinutes / 60f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "حجم کار امروز (%.1f ساعت) از زمان در دسترس (%.1f ساعت) بیشتره".format(workloadHours, availableHours),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (insight.conflicts.isNotEmpty()) {
                if (insight.isOverloaded) Spacer(modifier = Modifier.height(8.dp))
                insight.conflicts.forEach { (first, second) ->
                    Text(
                        text = "تداخل زمانی: «${first.title}» و «${second.title}»",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentRed
                    )
                }
            }
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
        title = { Text("انتخاب از عادت‌ها", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            if (habits.isEmpty()) {
                Text("هنوز هیچ عادتی تعریف نکرده‌اید. ابتدا در بخش عادت‌ها یک عادت بسازید.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ListItem(
                                headlineContent = { Text(habit.name, color = MaterialTheme.colorScheme.onBackground) },
                                supportingContent = { habit.description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
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
fun ConfigureHabitDialog(
    habitName: String,
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String?, Int?, Int?) -> Unit
) {
    var desc by remember { mutableStateOf("عادت روزانه") }
    var priority by remember { mutableIntStateOf(2) }
    var timeOfDay by remember { mutableStateOf<String?>(null) }
    var customHour by remember { mutableStateOf<Int?>(null) }
    var customMinute by remember { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیم عادت در برنامه روزانه", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("افزودن عادت «$habitName» به لیست کارهای امروز", style = MaterialTheme.typography.titleMedium, color = AccentGreen)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("توضیح دلخواه برای امروز") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("اولویت:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val priorities = listOf("عادی" to 0, "کم" to 1, "متوسط" to 2, "بالا" to 3, "بحرانی" to 4)
                    priorities.forEach { (label, value) ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("زمان انجام:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val times = listOf("صبح" to "MORNING", "عصر" to "AFTERNOON", "شب" to "NIGHT")
                    times.forEach { (label, value) ->
                        FilterChip(
                            selected = timeOfDay == value,
                            onClick = { 
                                timeOfDay = if (timeOfDay == value) null else value
                                customHour = null
                                customMinute = null
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val current = Calendar.getInstance()
                        TimePickerDialog(context, { _, h, m ->
                            customHour = h
                            customMinute = m
                            timeOfDay = "CUSTOM"
                        }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f))
                ) {
                    val timeText = if (timeOfDay == "CUSTOM" && customHour != null) {
                        String.format("ساعت %02d:%02d", customHour, customMinute)
                    } else {
                        "تنظیم ساعت دلخواه آلارم"
                    }
                    Text(timeText, color = AccentBlue)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(habitName, desc, priority, timeOfDay, customHour, customMinute) }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                Text("افزودن به امروز")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String?, Int?, Int?, RecurrenceRule?, Int?, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(0) }
    var timeOfDay by remember { mutableStateOf<String?>(null) }
    var customHour by remember { mutableStateOf<Int?>(null) }
    var customMinute by remember { mutableStateOf<Int?>(null) }
    var durationText by remember { mutableStateOf("") }
    var isAlarmRing by remember { mutableStateOf(true) }
    var recurrenceOption by remember { mutableStateOf("NONE") } // NONE, DAILY, WEEKLY, MONTHLY
    val selectedWeekdays = remember { mutableStateListOf<Int>() }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کار جدید", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                Text("اولویت:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val priorities = listOf("عادی" to 0, "کم" to 1, "متوسط" to 2, "بالا" to 3, "بحرانی" to 4)
                    priorities.forEach { (label, value) ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("زمان انجام کار (آلارم):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val times = listOf("صبح (۹:۰۰)" to "MORNING", "عصر (۱۵:۰۰)" to "AFTERNOON", "شب (۲۱:۰۰)" to "NIGHT")
                    times.forEach { (label, value) ->
                        FilterChip(
                            selected = timeOfDay == value,
                            onClick = { 
                                timeOfDay = if (timeOfDay == value) null else value
                                customHour = null
                                customMinute = null
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val current = Calendar.getInstance()
                        TimePickerDialog(context, { _, h, m ->
                            customHour = h
                            customMinute = m
                            timeOfDay = "CUSTOM"
                        }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f))
                ) {
                    val timeText = if (timeOfDay == "CUSTOM" && customHour != null) {
                        String.format("ساعت %02d:%02d", customHour, customMinute)
                    } else {
                        "ساعت دلخواه نوتیفیکیشن"
                    }
                    Text(timeText, color = AccentBlue)
                }

                // Time blocking (prompt section 14): only meaningful once a
                // specific time is set above — a duration with no start
                // time isn't a time block, just a task with a duration
                // estimate the planner already knows how to use.
                if (timeOfDay != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                        label = { Text("مدت زمان (دقیقه) — برای نمایش در تایم‌لاین") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("زنگ هشدار (آلارم)", color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "به‌جای نوتیفیکیشن ساده، صفحه با صدا و ویبره زنگ می‌زند",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = isAlarmRing, onCheckedChange = { isAlarmRing = it })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("تکرار:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf("بدون تکرار" to "NONE", "روزانه" to "DAILY", "هفتگی" to "WEEKLY", "ماهانه" to "MONTHLY")
                    options.forEach { (label, value) ->
                        FilterChip(
                            selected = recurrenceOption == value,
                            onClick = { recurrenceOption = value },
                            label = { Text(label) }
                        )
                    }
                }
                if (recurrenceOption == "WEEKLY") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("روزهای هفته:", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Calendar.SUNDAY=1 .. Calendar.SATURDAY=7
                        val weekdayLabels = listOf("ش" to 7, "ی" to 1, "د" to 2, "س" to 3, "چ" to 4, "پ" to 5, "ج" to 6)
                        weekdayLabels.forEach { (label, calDay) ->
                            FilterChip(
                                selected = calDay in selectedWeekdays,
                                onClick = {
                                    if (calDay in selectedWeekdays) selectedWeekdays.remove(calDay) else selectedWeekdays.add(calDay)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rule = when (recurrenceOption) {
                        "DAILY" -> RecurrenceRule.Daily
                        "WEEKLY" -> if (selectedWeekdays.isNotEmpty()) RecurrenceRule.Weekly(selectedWeekdays.toSet()) else null
                        "MONTHLY" -> RecurrenceRule.Monthly
                        else -> null
                    }
                    onAdd(title, description, priority, timeOfDay, customHour, customMinute, rule, durationText.toIntOrNull(), isAlarmRing)
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskEntity,
    viewModel: TodayViewModel,
    onDismiss: () -> Unit,
    onUpdate: (String, String, Int, String?, Int?, Int?, Int?, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description ?: "") }
    var priority by remember { mutableIntStateOf(task.priority) }
    var timeOfDay by remember { mutableStateOf(task.timeOfDay) }
    var durationText by remember { mutableStateOf(task.estimatedDurationMinutes?.toString() ?: "") }
    var isAlarmRing by remember { mutableStateOf(task.isAlarmRing) }

    // Parse custom hour/min if available from existing alarmTimeMillis
    var customHour by remember {
        mutableStateOf(task.alarmTimeMillis?.let {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it
            cal.get(Calendar.HOUR_OF_DAY)
        })
    }
    var customMinute by remember {
        mutableStateOf(task.alarmTimeMillis?.let {
            val cal = Calendar.getInstance()
            cal.timeInMillis = it
            cal.get(Calendar.MINUTE)
        })
    }

    val context = LocalContext.current
    val subtasks by viewModel.getSubtasksForTask(task.id).collectAsState()
    val reminders by viewModel.getRemindersForTask(task.id).collectAsState()
    val goals by viewModel.goals.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var newSubtaskTitle by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش کار", color = MaterialTheme.colorScheme.onBackground) },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
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
                    label = { Text("توضیحات") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("اولویت:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val priorities = listOf("عادی" to 0, "کم" to 1, "متوسط" to 2, "بالا" to 3, "بحرانی" to 4)
                    priorities.forEach { (label, value) ->
                        FilterChip(
                            selected = priority == value,
                            onClick = { priority = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("زمان انجام کار (آلارم):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val times = listOf("صبح" to "MORNING", "عصر" to "AFTERNOON", "شب" to "NIGHT")
                    times.forEach { (label, value) ->
                        FilterChip(
                            selected = timeOfDay == value,
                            onClick = { 
                                timeOfDay = if (timeOfDay == value) null else value
                                customHour = null
                                customMinute = null
                            },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val current = Calendar.getInstance()
                        TimePickerDialog(context, { _, h, m ->
                            customHour = h
                            customMinute = m
                            timeOfDay = "CUSTOM"
                        }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f))
                ) {
                    val timeText = if (timeOfDay == "CUSTOM" && customHour != null) {
                        String.format("ساعت %02d:%02d", customHour, customMinute)
                    } else {
                        "تغییر ساعت نوتیفیکیشن"
                    }
                    Text(timeText, color = AccentBlue)
                }

                // Time blocking (prompt section 14) — same rule as
                // AddTaskDialog: only shown once a specific time exists.
                if (timeOfDay != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                        label = { Text("مدت زمان (دقیقه) — برای نمایش در تایم‌لاین") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("زنگ هشدار (آلارم)", color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "به‌جای نوتیفیکیشن ساده، صفحه با صدا و ویبره زنگ می‌زند",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = isAlarmRing, onCheckedChange = { isAlarmRing = it })
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // --- Subtasks section (prompt section 9) ---
                Text("زیروظیفه‌ها:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                subtasks.forEach { subtask ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = subtask.isCompleted,
                            onCheckedChange = { viewModel.toggleSubtaskComplete(subtask) }
                        )
                        Text(
                            text = subtask.title,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null
                        )
                        IconButton(onClick = { viewModel.deleteSubtask(subtask) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف زیروظیفه", tint = AccentRed)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newSubtaskTitle,
                        onValueChange = { newSubtaskTitle = it },
                        label = { Text("زیروظیفه جدید") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newSubtaskTitle.isNotBlank()) {
                            viewModel.addSubtask(task.id, newSubtaskTitle, subtasks.size)
                            newSubtaskTitle = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "افزودن زیروظیفه", tint = AccentBlue)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // --- Reminders section (prompt section 10: multiple independent reminders) ---
                Text("یادآوری‌ها:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                reminders.forEach { reminder ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val cal = Calendar.getInstance().apply { timeInMillis = reminder.triggerTimeMillis }
                        val timeLabel = String.format(
                            "%02d:%02d",
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE)
                        )
                        Text(
                            text = reminder.message?.let { "$timeLabel — $it" } ?: timeLabel,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Switch(
                            checked = reminder.isEnabled,
                            onCheckedChange = { viewModel.setReminderEnabled(reminder, task, it) }
                        )
                        IconButton(onClick = { viewModel.deleteReminder(reminder) }) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف یادآوری", tint = AccentRed)
                        }
                    }
                }

                // "Notify before due time" quick options: the user asked for
                // a way to say "notify me X before I need to do this task"
                // rather than only picking an absolute clock time. Requires
                // the task to already have a scheduled time (alarmTimeMillis)
                // since "before" is undefined otherwise.
                if (task.alarmTimeMillis != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "اعلان قبل از موعد کار:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val beforeOptions = listOf(
                            "۵ دقیقه قبل" to 5,
                            "۱۵ دقیقه قبل" to 15,
                            "۳۰ دقیقه قبل" to 30,
                            "۱ ساعت قبل" to 60,
                            "۱ روز قبل" to (24 * 60)
                        )
                        beforeOptions.forEach { (label, minutesBefore) ->
                            val alreadyAdded = reminders.any {
                                it.triggerTimeMillis == task.alarmTimeMillis!! - minutesBefore * 60_000L
                            }
                            AssistChip(
                                onClick = {
                                    if (!alreadyAdded) {
                                        viewModel.addReminder(
                                            task,
                                            task.alarmTimeMillis!! - minutesBefore * 60_000L,
                                            "یادآوری «${task.title}»"
                                        )
                                    }
                                },
                                enabled = !alreadyAdded,
                                label = { Text(label) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        val current = Calendar.getInstance()
                        TimePickerDialog(context, { _, h, m ->
                            val cal = Calendar.getInstance()
                            cal.set(Calendar.HOUR_OF_DAY, h)
                            cal.set(Calendar.MINUTE, m)
                            cal.set(Calendar.SECOND, 0)
                            if (cal.timeInMillis < System.currentTimeMillis()) {
                                cal.add(Calendar.DAY_OF_YEAR, 1)
                            }
                            viewModel.addReminder(task, cal.timeInMillis, null)
                        }, current.get(Calendar.HOUR_OF_DAY), current.get(Calendar.MINUTE), true).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue.copy(alpha = 0.2f))
                ) {
                    Text("افزودن یادآوری", color = AccentBlue)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // --- Goal/Project link (prompt sections 15-16: connect daily
                // tasks to long-term goals/projects) ---
                Text("مرتبط با هدف:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = task.goalId == null,
                        onClick = { viewModel.linkTaskToGoal(task, null) },
                        label = { Text("هیچکدام") }
                    )
                    goals.forEach { goal ->
                        FilterChip(
                            selected = task.goalId == goal.id,
                            onClick = { viewModel.linkTaskToGoal(task, goal.id) },
                            label = { Text(goal.title) }
                        )
                    }
                }
                if (goals.isEmpty()) {
                    Text("هنوز هدفی تعریف نشده", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("مرتبط با پروژه:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = task.projectId == null,
                        onClick = { viewModel.linkTaskToProject(task, null) },
                        label = { Text("هیچکدام") }
                    )
                    projects.forEach { project ->
                        FilterChip(
                            selected = task.projectId == project.id,
                            onClick = { viewModel.linkTaskToProject(task, project.id) },
                            label = { Text(project.name) }
                        )
                    }
                }
                if (projects.isEmpty()) {
                    Text("هنوز پروژه‌ای تعریف نشده", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("دسته‌بندی:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = task.categoryId == null,
                        onClick = { viewModel.linkTaskToCategory(task, null) },
                        label = { Text("هیچکدام") }
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = task.categoryId == category.id,
                            onClick = { viewModel.linkTaskToCategory(task, category.id) },
                            label = { Text(category.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(android.graphics.Color.parseColor(category.colorHex)).copy(alpha = 0.35f)
                            )
                        )
                    }
                }
                if (categories.isEmpty()) {
                    Text("هنوز دسته‌ای تعریف نشده — از صفحه‌ی «کارها» می‌توانید دسته بسازید", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdate(title, description, priority, timeOfDay, customHour, customMinute, durationText.toIntOrNull(), isAlarmRing) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Text("بروزرسانی")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}

@Composable
fun GlassTaskItem(
    task: TaskEntity,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onStartFocus: () -> Unit = {},
    onArchive: () -> Unit = {},
    onDuplicate: () -> Unit = {},
    isArchived: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
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
            .clickable { onClick() }
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
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (!task.description.isNullOrEmpty()) {
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
                
                // Show alarm indicator if set
                if (task.alarmTimeMillis != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val timeText = when (task.timeOfDay) {
                            "MORNING" -> "صبح (۹:۰۰)"
                            "AFTERNOON" -> "عصر (۱۵:۰۰)"
                            "NIGHT" -> "شب (۲۱:۰۰)"
                            else -> {
                                val cal = Calendar.getInstance()
                                cal.timeInMillis = task.alarmTimeMillis
                                String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                            }
                        }
                        Text(
                            text = "یادآوری: $timeText",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentBlue
                        )
                    }
                }
            }

            if (!task.isCompleted) {
                IconButton(onClick = onStartFocus) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "شروع فوکوس",
                        tint = AccentBlue.copy(alpha = 0.8f)
                    )
                }
            }
            IconButton(onClick = onToggleComplete) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "تکمیل",
                    tint = if (task.isCompleted) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف",
                    tint = AccentRed.copy(alpha = 0.7f)
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "گزینه‌های بیشتر",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    // Prompt section 7: "Duplicate tasks" / "Archive tasks".
                    DropdownMenuItem(
                        text = { Text("کپی کردن") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { showMenu = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isArchived) "بازگردانی از آرشیو" else "آرشیو") },
                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                        onClick = { showMenu = false; onArchive() }
                    )
                }
            }
        }
    }
}


