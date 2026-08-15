package com.example.lifeos.domain.usecases

import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTodayTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository
) {
    operator fun invoke(): Flow<List<TaskEntity>> {
        // Simple mock of finding "today" for now. 
        // In reality, this requires kotlinx-datetime or Calendar to find start/end of the current day in Jalali/Gregorian
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        // This is a naive implementation; replace with proper Jalali day boundaries
        return taskRepository.getTasksForDateRange(now - oneDayMillis, now + oneDayMillis)
    }
}
