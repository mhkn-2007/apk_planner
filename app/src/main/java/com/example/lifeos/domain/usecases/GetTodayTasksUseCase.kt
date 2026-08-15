package com.example.lifeos.domain.usecases

import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject

class GetTodayTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    operator fun invoke(): Flow<List<TaskEntity>> {
        // Use the actual start-of-day / end-of-day boundaries for "today" in the
        // device's local calendar, instead of a rolling [-24h, +24h] window.
        // A rolling window meant tasks due later today could be excluded (if it's
        // past a certain hour) and yesterday's/tomorrow's tasks could incorrectly
        // show up, depending on what time of day the user opened the app.
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return taskRepository.getTasksForDateRange(startOfDay, endOfDay)
    }
}
