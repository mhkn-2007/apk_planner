package com.example.lifeos.domain.repositories

import com.example.lifeos.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun insertTask(task: TaskEntity)
    suspend fun updateTask(task: TaskEntity)
    suspend fun deleteTask(task: TaskEntity)
    suspend fun getTaskById(taskId: String): TaskEntity?
    fun getAllTasks(): Flow<List<TaskEntity>>
    fun getTasksForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>
}
