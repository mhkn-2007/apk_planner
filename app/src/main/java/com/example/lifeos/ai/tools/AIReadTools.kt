package com.example.lifeos.ai.tools

import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controlled read-only access to app data for the AI (prompt section 21).
 *
 * This is the ONLY way the AI is allowed to *read* application state. It
 * never gets a raw DAO/database handle: every method here returns a small,
 * purpose-built slice of data for one specific question the AI might need to
 * answer ("what's on today", "what's overdue", ...), matching the tool names
 * the prompt asked for (get_tasks, get_today_tasks, get_unfinished_tasks,
 * get_goals, get_projects, get_habits, get_routines, ...).
 */
@Singleton
class AIReadTools @Inject constructor(
    private val taskRepository: TaskRepository,
    private val goalDao: GoalDao,
    private val projectDao: ProjectDao,
    private val habitDao: HabitDao,
    private val routineDao: RoutineDao
) {
    private fun startOfDay(offsetDays: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfDay(offsetDays: Int = 0): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    /** get_tasks: all tasks currently in the database. */
    suspend fun getTasks(): List<TaskEntity> = taskRepository.getAllTasks().first()

    /** get_today_tasks: tasks due today. */
    suspend fun getTodayTasks(): List<TaskEntity> =
        taskRepository.getTasksForDateRange(startOfDay(0), endOfDay(0)).first()

    /** Tasks due tomorrow — needed for AI daily planning (prompt section 23). */
    suspend fun getTomorrowTasks(): List<TaskEntity> =
        taskRepository.getTasksForDateRange(startOfDay(1), endOfDay(1)).first()

    /** get_unfinished_tasks: incomplete tasks whose due date has already passed. */
    suspend fun getUnfinishedTasks(): List<TaskEntity> {
        val now = System.currentTimeMillis()
        return taskRepository.getAllTasks().first().filter {
            !it.isCompleted && it.dueDateMillis != null && it.dueDateMillis < now
        }
    }

    /** get_calendar_events: tasks scheduled (have a start/end time) within a range. */
    suspend fun getCalendarEvents(startMillis: Long, endMillis: Long): List<TaskEntity> =
        taskRepository.getTasksForDateRange(startMillis, endMillis).first()
            .filter { it.startTimeMillis != null || it.alarmTimeMillis != null }

    /** get_goals */
    suspend fun getGoals(): List<GoalEntity> = goalDao.getAllGoals().first()

    /** get_projects */
    suspend fun getProjects(): List<ProjectEntity> = projectDao.getAllProjects().first()

    /** get_habits */
    suspend fun getHabits(): List<HabitEntity> = habitDao.getAllHabits().first()

    /** get_routines */
    suspend fun getRoutines(): List<RoutineTemplateEntity> = routineDao.getAllTemplates().first()

    /**
     * get_productivity_history: a lightweight workload summary the AI can use
     * for planning/review, without exposing raw analytics tables that don't
     * exist yet (Analytics is a later phase, prompt section 19).
     */
    suspend fun getProductivityHistory(daysBack: Int = 7): ProductivitySummary {
        val start = startOfDay(-daysBack)
        val end = endOfDay(0)
        val tasks = taskRepository.getTasksForDateRange(start, end).first()
        val completed = tasks.count { it.isCompleted }
        val postponed = tasks.count { !it.isCompleted && (it.dueDateMillis ?: 0L) < startOfDay(0) }
        return ProductivitySummary(
            totalTasks = tasks.size,
            completedTasks = completed,
            postponedTasks = postponed
        )
    }

    data class ProductivitySummary(
        val totalTasks: Int,
        val completedTasks: Int,
        val postponedTasks: Int
    )
}
