package com.example.lifeos.data.database

import com.example.lifeos.data.database.dao.TaskDao
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao
) : TaskRepository {
    override suspend fun insertTask(task: TaskEntity) {
        taskDao.insertTask(task)
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
}
