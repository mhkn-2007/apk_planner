package com.example.lifeos.domain.usecases

import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/**
 * Supported recurrence patterns (prompt section 11: daily, weekly on
 * specific weekdays, monthly, or a custom weekday combination — the same
 * "weekly on specific weekdays" mechanism covers both).
 */
sealed class RecurrenceRule {
    object Daily : RecurrenceRule()

    /** [weekdays] uses java.util.Calendar weekday constants (1=Sunday..7=Saturday). */
    data class Weekly(val weekdays: Set<Int>) : RecurrenceRule()

    /** Repeats on the same day-of-month as the first occurrence. */
    object Monthly : RecurrenceRule()

    fun encode(): String = when (this) {
        is Daily -> "DAILY"
        is Weekly -> "WEEKLY:" + weekdays.sorted().joinToString(",")
        is Monthly -> "MONTHLY"
    }

    companion object {
        fun decode(raw: String?): RecurrenceRule? {
            if (raw.isNullOrBlank()) return null
            return when {
                raw == "DAILY" -> Daily
                raw == "MONTHLY" -> Monthly
                raw.startsWith("WEEKLY:") -> {
                    val days = raw.removePrefix("WEEKLY:")
                        .split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .toSet()
                    if (days.isEmpty()) null else Weekly(days)
                }
                else -> null
            }
        }
    }
}

/**
 * Expands a recurring task's rule into concrete [TaskEntity] rows for a
 * rolling window ahead of "today", instead of generating occurrences
 * forever. Skips any date that already has a generated occurrence for the
 * same [TaskEntity.recurrenceGroupId], so re-running this is always safe
 * and never creates duplicates (prompt section 11 requirement).
 */
class GenerateRecurringTaskOccurrencesUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    companion object {
        // How far ahead to materialize occurrences. Long enough that the
        // calendar/today views always have upcoming instances to show,
        // short enough that we're not silently writing hundreds of rows.
        const val GENERATION_WINDOW_DAYS = 60
    }

    suspend operator fun invoke(sourceTask: TaskEntity) {
        val rule = RecurrenceRule.decode(sourceTask.recurrenceRule) ?: return
        val groupId = sourceTask.recurrenceGroupId ?: return
        val baseDue = sourceTask.dueDateMillis ?: return

        val windowStart = startOfDay(baseDue)
        val windowEnd = startOfDay(addDays(baseDue, GENERATION_WINDOW_DAYS.toLong()))

        val existingDates = taskRepository
            .getExistingOccurrenceDates(groupId, windowStart, windowEnd)
            .filterNotNull()
            .map { startOfDay(it) }
            .toSet()

        val occurrenceDates = when (rule) {
            RecurrenceRule.Daily -> generateDaily(baseDue, GENERATION_WINDOW_DAYS)
            is RecurrenceRule.Weekly -> generateWeekly(baseDue, GENERATION_WINDOW_DAYS, rule.weekdays)
            RecurrenceRule.Monthly -> generateMonthly(baseDue, GENERATION_WINDOW_DAYS)
        }

        val newTasks = occurrenceDates
            .filter { startOfDay(it) !in existingDates }
            .map { occurrenceDate ->
                sourceTask.copy(
                    id = UUID.randomUUID().toString(),
                    dueDateMillis = occurrenceDate,
                    alarmTimeMillis = sourceTask.alarmTimeMillis?.let { shiftTimeOfDayOnto(it, occurrenceDate) },
                    isCompleted = false,
                    createdAtMillis = System.currentTimeMillis()
                )
            }

        if (newTasks.isNotEmpty()) {
            taskRepository.insertTasks(newTasks)
        }
    }

    private fun generateDaily(baseDue: Long, windowDays: Int): List<Long> {
        return (0..windowDays).map { offset -> addDays(baseDue, offset.toLong()) }
    }

    private fun generateWeekly(baseDue: Long, windowDays: Int, weekdays: Set<Int>): List<Long> {
        if (weekdays.isEmpty()) return emptyList()
        val results = mutableListOf<Long>()
        for (offset in 0..windowDays) {
            val candidate = addDays(baseDue, offset.toLong())
            val cal = Calendar.getInstance().apply { timeInMillis = candidate }
            if (cal.get(Calendar.DAY_OF_WEEK) in weekdays) {
                results.add(candidate)
            }
        }
        return results
    }

    private fun generateMonthly(baseDue: Long, windowDays: Int): List<Long> {
        val results = mutableListOf<Long>()
        val baseCal = Calendar.getInstance().apply { timeInMillis = baseDue }
        val dayOfMonth = baseCal.get(Calendar.DAY_OF_MONTH)
        val cal = Calendar.getInstance().apply { timeInMillis = baseDue }
        val endMillis = addDays(baseDue, windowDays.toLong())

        while (cal.timeInMillis <= endMillis) {
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            cal.set(Calendar.DAY_OF_MONTH, minOf(dayOfMonth, maxDay))
            if (cal.timeInMillis in baseDue..endMillis) {
                results.add(cal.timeInMillis)
            }
            cal.add(Calendar.MONTH, 1)
        }
        return results
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addDays(millis: Long, days: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.DAY_OF_YEAR, days.toInt())
    }.timeInMillis

    /** Keeps the original alarm's hour/minute but moves it onto [targetDateMillis]'s day. */
    private fun shiftTimeOfDayOnto(originalAlarmMillis: Long, targetDateMillis: Long): Long {
        val timeCal = Calendar.getInstance().apply { timeInMillis = originalAlarmMillis }
        return Calendar.getInstance().apply {
            timeInMillis = targetDateMillis
            set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
