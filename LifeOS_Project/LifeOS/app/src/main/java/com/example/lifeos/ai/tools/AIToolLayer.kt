package com.example.lifeos.ai.tools

import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only way the AI is allowed to interact with the system. 
 * Includes safety checks and validations.
 */
@Singleton
class AIToolLayer @Inject constructor(
    private val taskRepository: TaskRepository
) {

    suspend fun createTask(title: String, dueDateMillis: Long?, priority: Int, estimatedDurationMinutes: Int?): Boolean {
        if (title.isBlank()) return false
        
        val task = TaskEntity(
            title = title,
            dueDateMillis = dueDateMillis,
            priority = priority,
            estimatedDurationMinutes = estimatedDurationMinutes
        )
        
        taskRepository.insertTask(task)
        return true
    }

    suspend fun rescheduleTask(taskId: String, newDueDateMillis: Long): Boolean {
        val task = taskRepository.getTaskById(taskId) ?: return false
        val updatedTask = task.copy(dueDateMillis = newDueDateMillis)
        taskRepository.updateTask(updatedTask)
        return true
    }

    // Protection: AI cannot delete massive amounts of tasks without specific limits
    suspend fun deleteLowPriorityTasks(taskIds: List<String>): Boolean {
        if (taskIds.size > 5) {
            // Refuse large deletions for safety
            return false
        }
        
        for (id in taskIds) {
            val task = taskRepository.getTaskById(id)
            if (task != null && task.priority < 3) {
                taskRepository.deleteTask(task)
            }
        }
        return true
    }
}
