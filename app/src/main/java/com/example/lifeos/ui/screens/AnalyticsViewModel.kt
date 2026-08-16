package com.example.lifeos.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.data.database.dao.FocusSessionDao
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.dao.HabitLogDao
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.domain.repositories.TaskRepository
import com.example.lifeos.util.JalaliCalendarUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

enum class AnalyticsPeriod { WEEK, MONTH }

/**
 * A single habit's consistency over the selected period (prompt section 17:
 * "weekly statistics", "monthly statistics", "completion percentage" — shown
 * here inside Analytics rather than duplicated on the Habits screen, per
 * section 19's "habit consistency" metric).
 */
data class HabitConsistency(
    val habit: HabitEntity,
    val completedDays: Int,
    val totalDays: Int
) {
    val percentage: Int
        get() = if (totalDays == 0) 0 else (completedDays * 100) / totalDays
}

data class GoalProgressSummary(
    val goal: GoalEntity
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val period: AnalyticsPeriod = AnalyticsPeriod.WEEK,
    val tasksCompleted: Int = 0,
    val tasksScheduled: Int = 0,
    val tasksPostponed: Int = 0,
    val completionPercentage: Int = 0,
    val focusMinutes: Int = 0,
    val completedFocusSessions: Int = 0,
    val habitConsistency: List<HabitConsistency> = emptyList(),
    val goalProgress: List<GoalProgressSummary> = emptyList(),
    val routineInstancesTotal: Int = 0,
    val routineInstancesCompleted: Int = 0,
    // "You complete most important tasks between 9 AM and 12 PM" style
    // insight (section 19). Null if there isn't enough data yet.
    val mostProductiveHourRange: String? = null
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val focusSessionDao: FocusSessionDao,
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val goalDao: GoalDao,
    private val routineDao: RoutineDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        load(AnalyticsPeriod.WEEK)
    }

    fun setPeriod(period: AnalyticsPeriod) {
        load(period)
    }

    private fun load(period: AnalyticsPeriod) {
        _uiState.value = _uiState.value.copy(isLoading = true, period = period)
        viewModelScope.launch {
            val (startMillis, endMillis) = rangeFor(period)

            val tasksCompleted = taskRepository.countCompletedInRange(startMillis, endMillis)
            val tasksScheduled = taskRepository.countScheduledInRange(startMillis, endMillis)
            val tasksPostponed = taskRepository.countPostponedInRange(startMillis, endMillis)
            val completionPercentage = if (tasksScheduled == 0) 0 else (tasksCompleted * 100) / tasksScheduled

            val focusSeconds = focusSessionDao.getTotalFocusSecondsInRange(startMillis, endMillis)
            val completedSessions = focusSessionDao.getCompletedWorkSessionCountInRange(startMillis, endMillis)

            val habits = habitDao.getAllHabits().first()
            val dateKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val startKey = dateKeyFormat.format(java.util.Date(startMillis))
            val endKey = dateKeyFormat.format(java.util.Date(endMillis))
            val totalDaysInPeriod = daysBetween(startMillis, endMillis)
            val habitConsistency = habits.map { habit ->
                val completedDays = habitLogDao.countLogsInRange(habit.id, startKey, endKey)
                HabitConsistency(habit = habit, completedDays = completedDays, totalDays = totalDaysInPeriod)
            }

            val goals = goalDao.getAllGoals().first()
            val goalProgress = goals.map { GoalProgressSummary(goal = it) }

            val instances = routineDao.getInstancesForDateRange(startMillis, endMillis).first()
            val routineTotal = instances.size
            val routineCompleted = instances.count { it.isCompleted }

            val completionTimestamps = taskRepository.getCompletionTimestampsInRange(startMillis, endMillis)
            val mostProductiveHourRange = computeMostProductiveHourRange(completionTimestamps.filterNotNull())

            _uiState.value = AnalyticsUiState(
                isLoading = false,
                period = period,
                tasksCompleted = tasksCompleted,
                tasksScheduled = tasksScheduled,
                tasksPostponed = tasksPostponed,
                completionPercentage = completionPercentage,
                focusMinutes = focusSeconds / 60,
                completedFocusSessions = completedSessions,
                habitConsistency = habitConsistency,
                goalProgress = goalProgress,
                routineInstancesTotal = routineTotal,
                routineInstancesCompleted = routineCompleted,
                mostProductiveHourRange = mostProductiveHourRange
            )
        }
    }

    /**
     * Buckets completion timestamps by hour-of-day and reports the 3-hour
     * window with the most completions, mirroring the prompt's example
     * insight: "You complete most important tasks between 9 AM and 12 PM."
     * Requires at least a handful of data points to avoid a misleadingly
     * confident claim from 1-2 completions.
     */
    private fun computeMostProductiveHourRange(timestamps: List<Long>): String? {
        if (timestamps.size < 5) return null
        val hourCounts = IntArray(24)
        val cal = Calendar.getInstance()
        for (ts in timestamps) {
            cal.timeInMillis = ts
            hourCounts[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        var bestStartHour = 0
        var bestSum = -1
        for (startHour in 0..21) {
            val sum = hourCounts[startHour] + hourCounts[startHour + 1] + hourCounts[startHour + 2]
            if (sum > bestSum) {
                bestSum = sum
                bestStartHour = startHour
            }
        }
        if (bestSum <= 0) return null
        return "%02d:00 تا %02d:00".format(bestStartHour, bestStartHour + 3)
    }

    private fun daysBetween(startMillis: Long, endMillis: Long): Int {
        val days = ((endMillis - startMillis) / (24 * 60 * 60 * 1000L)).toInt() + 1
        return days.coerceAtLeast(1)
    }

    /**
     * Computes [start, end] millis for the selected period using the Jalali
     * calendar (the app is Persian-first, prompt section 4), anchored on
     * today. Week starts on Saturday (شنبه), matching JalaliCalendarUtil's
     * existing week-start convention used elsewhere in the app.
     */
    private fun rangeFor(period: AnalyticsPeriod): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val today = JalaliCalendarUtil.gregorianToJalali(now)

        return when (period) {
            AnalyticsPeriod.WEEK -> {
                // Calendar.DAY_OF_WEEK: SATURDAY=7 in java.util.Calendar terms.
                val cal = Calendar.getInstance().apply { timeInMillis = now }
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // SUN=1..SAT=7
                // Days since Saturday (Persian week start).
                val daysSinceSaturday = (dayOfWeek - Calendar.SATURDAY + 7) % 7
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, -daysSinceSaturday)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = startCal.timeInMillis
                    add(Calendar.DAY_OF_YEAR, 6)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                startCal.timeInMillis to endCal.timeInMillis
            }
            AnalyticsPeriod.MONTH -> {
                val startMillis = JalaliCalendarUtil.jalaliToGregorian(today.year, today.month, 1)
                val daysInMonth = JalaliCalendarUtil.daysInJalaliMonth(today.year, today.month)
                val endMillis = JalaliCalendarUtil.jalaliToGregorian(today.year, today.month, daysInMonth)
                val endCal = Calendar.getInstance().apply {
                    timeInMillis = endMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val startCal = Calendar.getInstance().apply {
                    timeInMillis = startMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startCal.timeInMillis to endCal.timeInMillis
            }
        }
    }
}
