package com.example.lifeos.ai.tools

import com.example.lifeos.data.database.dao.FocusSessionDao
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.dao.HabitLogDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.planner.DeterministicPlannerEngine
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
    private val routineDao: RoutineDao,
    private val focusSessionDao: FocusSessionDao,
    private val habitLogDao: HabitLogDao,
    private val plannerEngine: DeterministicPlannerEngine
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

    // ---------------------------------------------------------------
    // Daily / Weekly planning support (prompt sections 23, 24)
    // ---------------------------------------------------------------

    /**
     * Everything the AI needs to build tomorrow's plan in one call: unfinished
     * (overdue) tasks, tasks already scheduled for tomorrow, and a
     * workload/conflict analysis from [DeterministicPlannerEngine] (prompt
     * section 32: the AI may use this engine, but the engine itself has no
     * dependency back on the AI).
     */
    suspend fun getDailyPlanningContext(availableTimeMinutes: Int = 8 * 60): DailyPlanningContext {
        val unfinished = getUnfinishedTasks()
        val tomorrow = getTomorrowTasks()
        val candidateTasks = (unfinished + tomorrow).distinctBy { it.id }

        val sorted = plannerEngine.sortTasksByPriority(candidateTasks)
        val totalWorkload = plannerEngine.calculateTotalWorkload(candidateTasks)
        val conflicts = plannerEngine.detectConflicts(candidateTasks)
        val (fits, postponeCandidates) = plannerEngine.suggestPostponements(candidateTasks, availableTimeMinutes)

        return DailyPlanningContext(
            unfinishedTasks = unfinished,
            tomorrowTasks = tomorrow,
            prioritizedTasks = sorted,
            totalWorkloadMinutes = totalWorkload,
            availableTimeMinutes = availableTimeMinutes,
            conflictingPairs = conflicts,
            fitsInAvailableTime = fits,
            suggestedPostponements = postponeCandidates
        )
    }

    data class DailyPlanningContext(
        val unfinishedTasks: List<TaskEntity>,
        val tomorrowTasks: List<TaskEntity>,
        val prioritizedTasks: List<TaskEntity>,
        val totalWorkloadMinutes: Int,
        val availableTimeMinutes: Int,
        val conflictingPairs: List<Pair<TaskEntity, TaskEntity>>,
        val fitsInAvailableTime: List<TaskEntity>,
        val suggestedPostponements: List<TaskEntity>
    )

    /**
     * The equivalent of [getDailyPlanningContext] but across the next 7 days,
     * for "برنامه این هفته‌ام رو بچین" (prompt section 24).
     */
    suspend fun getWeeklyPlanningContext(availableTimeMinutesPerDay: Int = 8 * 60): WeeklyPlanningContext {
        val weekTasks = taskRepository.getTasksForDateRange(startOfDay(0), endOfDay(6)).first()
        val unfinished = getUnfinishedTasks()
        val allCandidates = (weekTasks + unfinished).distinctBy { it.id }

        val sorted = plannerEngine.sortTasksByPriority(allCandidates)
        val totalWorkload = plannerEngine.calculateTotalWorkload(allCandidates)
        val availableTimeMinutesForWeek = availableTimeMinutesPerDay * 7
        val (fits, postponeCandidates) = plannerEngine.suggestPostponements(allCandidates, availableTimeMinutesForWeek)

        return WeeklyPlanningContext(
            weekTasks = weekTasks,
            unfinishedTasks = unfinished,
            prioritizedTasks = sorted,
            totalWorkloadMinutes = totalWorkload,
            availableTimeMinutesForWeek = availableTimeMinutesForWeek,
            fitsInAvailableTime = fits,
            suggestedPostponements = postponeCandidates
        )
    }

    data class WeeklyPlanningContext(
        val weekTasks: List<TaskEntity>,
        val unfinishedTasks: List<TaskEntity>,
        val prioritizedTasks: List<TaskEntity>,
        val totalWorkloadMinutes: Int,
        val availableTimeMinutesForWeek: Int,
        val fitsInAvailableTime: List<TaskEntity>,
        val suggestedPostponements: List<TaskEntity>
    )

    // ---------------------------------------------------------------
    // Daily review (prompt section 30)
    // ---------------------------------------------------------------

    /**
     * get_daily_review: a same-day (or arbitrary offsetDays) summary of
     * completed vs. postponed tasks, focus sessions, and habit completion —
     * everything the AI needs to "help the user review their day" without
     * inventing numbers (prompt section 30 explicitly lists these four
     * categories: completed tasks, postponed tasks, focus sessions, habits).
     */
    suspend fun getDailyReview(offsetDays: Int = 0): DailyReview {
        val dayTasks = taskRepository.getTasksForDateRange(startOfDay(offsetDays), endOfDay(offsetDays)).first()
        val completedTasks = dayTasks.filter { it.isCompleted }
        val postponedTasks = dayTasks.filter { !it.isCompleted }

        val focusSeconds = focusSessionDao.getTotalFocusSecondsInRange(startOfDay(offsetDays), endOfDay(offsetDays))
        val completedFocusSessions = focusSessionDao.getCompletedWorkSessionCountInRange(
            startOfDay(offsetDays), endOfDay(offsetDays)
        )

        val habits = habitDao.getAllHabits().first()
        val dateKey = dateKeyForOffset(offsetDays)
        val completedHabitCount = habits.count { habitLogDao.hasLogForDate(it.id, dateKey) }

        return DailyReview(
            completedTasks = completedTasks,
            postponedTasks = postponedTasks,
            focusSecondsSpent = focusSeconds,
            completedFocusSessions = completedFocusSessions,
            totalHabits = habits.size,
            completedHabitCount = completedHabitCount
        )
    }

    private fun dateKeyForOffset(offsetDays: Int): String {
        // habit_logs.dateKey stores a JALALI "yyyy-MM-dd" key (see
        // HabitsScreen.toggleHabitStreak), not a Gregorian one — this must
        // match exactly or getDailyReview() would silently undercount
        // completed habits by comparing against the wrong calendar.
        val millis = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }.timeInMillis
        val jalali = com.example.lifeos.util.JalaliCalendarUtil.gregorianToJalali(millis)
        return String.format("%04d-%02d-%02d", jalali.year, jalali.month, jalali.day)
    }

    data class DailyReview(
        val completedTasks: List<TaskEntity>,
        val postponedTasks: List<TaskEntity>,
        val focusSecondsSpent: Int,
        val completedFocusSessions: Int,
        val totalHabits: Int,
        val completedHabitCount: Int
    )
}
