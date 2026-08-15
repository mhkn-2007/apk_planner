package com.example.lifeos.domain.repositories

import com.example.lifeos.data.database.entities.ReminderEntity
import com.example.lifeos.data.database.entities.SubtaskEntity
import com.example.lifeos.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun insertTask(task: TaskEntity)
    suspend fun updateTask(task: TaskEntity)
    suspend fun deleteTask(task: TaskEntity)
    suspend fun getTaskById(taskId: String): TaskEntity?
    fun getAllTasks(): Flow<List<TaskEntity>>
    fun getTasksForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    // Subtasks
    fun getSubtasksForTask(taskId: String): Flow<List<SubtaskEntity>>
    suspend fun insertSubtask(subtask: SubtaskEntity)
    suspend fun updateSubtask(subtask: SubtaskEntity)
    suspend fun deleteSubtask(subtask: SubtaskEntity)

    // Reminders
    fun getRemindersForTask(taskId: String): Flow<List<ReminderEntity>>
    suspend fun insertReminder(reminder: ReminderEntity)
    suspend fun updateReminder(reminder: ReminderEntity)
    suspend fun deleteReminder(reminder: ReminderEntity)
}
