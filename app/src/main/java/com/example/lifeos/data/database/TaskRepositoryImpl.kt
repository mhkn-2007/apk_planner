package com.example.lifeos.data.database

import com.example.lifeos.data.database.dao.ReminderDao
import com.example.lifeos.data.database.dao.SubtaskDao
import com.example.lifeos.data.database.dao.TaskDao
import com.example.lifeos.data.database.entities.ReminderEntity
import com.example.lifeos.data.database.entities.SubtaskEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val subtaskDao: SubtaskDao,
    private val reminderDao: ReminderDao
) : TaskRepository {
    override suspend fun insertTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    override suspend fun insertTasks(tasks: List<TaskEntity>) {
        taskDao.insertTasks(tasks)
    }

    override suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
    }

    override suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }

    override suspend fun getTaskById(taskId: String): TaskEntity? {
        return taskDao.getTaskById(taskId)
    }

    override fun getAllTasks(): Flow<List<TaskEntity>> {
        return taskDao.getAllTasks()
    }

    override fun getTasksForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>> {
        return taskDao.getTasksForDateRange(startOfDay, endOfDay)
    }

    override fun getArchivedTasks(): Flow<List<TaskEntity>> {
        return taskDao.getArchivedTasks()
    }

    override suspend fun setArchived(taskId: String, archived: Boolean) {
        taskDao.setArchived(taskId, archived)
    }

    override suspend fun getExistingOccurrenceDates(groupId: String, startOfDay: Long, endOfDay: Long): List<Long?> {
        return taskDao.getExistingOccurrenceDates(groupId, startOfDay, endOfDay)
    }

    override fun getTasksForRecurrenceGroup(groupId: String): Flow<List<TaskEntity>> {
        return taskDao.getTasksForRecurrenceGroup(groupId)
    }

    override suspend fun deleteFutureUncompletedOccurrences(groupId: String, fromMillis: Long) {
        taskDao.deleteFutureUncompletedOccurrences(groupId, fromMillis)
    }

    override suspend fun countCompletedInRange(startMillis: Long, endMillis: Long): Int {
        return taskDao.countCompletedInRange(startMillis, endMillis)
    }

    override suspend fun countScheduledInRange(startMillis: Long, endMillis: Long): Int {
        return taskDao.countScheduledInRange(startMillis, endMillis)
    }

    override suspend fun countPostponedInRange(startMillis: Long, endMillis: Long): Int {
        return taskDao.countPostponedInRange(startMillis, endMillis)
    }

    override suspend fun getCompletionTimestampsInRange(startMillis: Long, endMillis: Long): List<Long?> {
        return taskDao.getCompletionTimestampsInRange(startMillis, endMillis)
    }

    override suspend fun countTasksForGoal(goalId: String): Int {
        return taskDao.countTasksForGoal(goalId)
    }

    override suspend fun countCompletedTasksForGoal(goalId: String): Int {
        return taskDao.countCompletedTasksForGoal(goalId)
    }

    override suspend fun countTasksForProject(projectId: String): Int {
        return taskDao.countTasksForProject(projectId)
    }

    override suspend fun countCompletedTasksForProject(projectId: String): Int {
        return taskDao.countCompletedTasksForProject(projectId)
    }

    override fun getSubtasksForTask(taskId: String): Flow<List<SubtaskEntity>> {
        return subtaskDao.getSubtasksForTask(taskId)
    }

    override suspend fun insertSubtask(subtask: SubtaskEntity) {
        subtaskDao.insertSubtask(subtask)
    }

    override suspend fun updateSubtask(subtask: SubtaskEntity) {
        subtaskDao.updateSubtask(subtask)
    }

    override suspend fun deleteSubtask(subtask: SubtaskEntity) {
        subtaskDao.deleteSubtask(subtask)
    }

    override fun getRemindersForTask(taskId: String): Flow<List<ReminderEntity>> {
        return reminderDao.getRemindersForTask(taskId)
    }

    override suspend fun getReminderById(reminderId: String): ReminderEntity? {
        return reminderDao.getReminderById(reminderId)
    }

    override suspend fun insertReminder(reminder: ReminderEntity) {
        reminderDao.insertReminder(reminder)
    }

    override suspend fun updateReminder(reminder: ReminderEntity) {
        reminderDao.updateReminder(reminder)
    }

    override suspend fun deleteReminder(reminder: ReminderEntity) {
        reminderDao.deleteReminder(reminder)
    }
}
