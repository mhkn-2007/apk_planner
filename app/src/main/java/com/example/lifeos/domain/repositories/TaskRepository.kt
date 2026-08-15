package com.example.lifeos.domain.repositories

import com.example.lifeos.data.database.entities.ReminderEntity
import com.example.lifeos.data.database.entities.SubtaskEntity
import com.example.lifeos.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    suspend fun insertTask(task: TaskEntity)
    suspend fun insertTasks(tasks: List<TaskEntity>)
    suspend fun updateTask(task: TaskEntity)
    suspend fun deleteTask(task: TaskEntity)
    suspend fun getTaskById(taskId: String): TaskEntity?
    fun getAllTasks(): Flow<List<TaskEntity>>
    fun getTasksForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    // Recurrence (prompt section 11)
    suspend fun getExistingOccurrenceDates(groupId: String, startOfDay: Long, endOfDay: Long): List<Long?>
    fun getTasksForRecurrenceGroup(groupId: String): Flow<List<TaskEntity>>
    suspend fun deleteFutureUncompletedOccurrences(groupId: String, fromMillis: Long)

    // Subtasks
    fun getSubtasksForTask(taskId: String): Flow<List<SubtaskEntity>>
    suspend fun insertSubtask(subtask: SubtaskEntity)
    suspend fun updateSubtask(subtask: SubtaskEntity)
    suspend fun deleteSubtask(subtask: SubtaskEntity)

    // Reminders
    fun getRemindersForTask(taskId: String): Flow<List<ReminderEntity>>
    suspend fun getReminderById(reminderId: String): ReminderEntity?
    suspend fun insertReminder(reminder: ReminderEntity)
    suspend fun updateReminder(reminder: ReminderEntity)
    suspend fun deleteReminder(reminder: ReminderEntity)
}
