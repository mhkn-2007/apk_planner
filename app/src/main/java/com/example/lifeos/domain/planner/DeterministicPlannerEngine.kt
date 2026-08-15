package com.example.lifeos.domain.planner

import com.example.lifeos.data.database.entities.TaskEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicPlannerEngine @Inject constructor() {

    /**
     * Sorts tasks based on priority (0: None, 4: Critical) and deadline.
     */
    fun sortTasksByPriority(tasks: List<TaskEntity>): List<TaskEntity> {
        return tasks.sortedWith(
            compareByDescending<TaskEntity> { it.priority }
                .thenBy { it.dueDateMillis ?: Long.MAX_VALUE }
        )
    }

    /**
     * Calculates total estimated workload for a given day in minutes.
     */
    fun calculateTotalWorkload(tasks: List<TaskEntity>): Int {
        return tasks.sumOf { it.estimatedDurationMinutes ?: 30 } // Default 30 min if unknown
    }

    /**
     * Detects overlapping scheduled tasks. 
     * Returns a list of tasks that conflict with each other.
     */
    fun detectConflicts(tasks: List<TaskEntity>): List<Pair<TaskEntity, TaskEntity>> {
        val conflicts = mutableListOf<Pair<TaskEntity, TaskEntity>>()
        val scheduledTasks = tasks.filter { it.startTimeMillis != null && it.endTimeMillis != null }
            .sortedBy { it.startTimeMillis }

        for (i in 0 until scheduledTasks.size - 1) {
            val current = scheduledTasks[i]
            val next = scheduledTasks[i + 1]
            
            // If the next task starts before the current one ends, it's a conflict
            if (next.startTimeMillis!! < current.endTimeMillis!!) {
                conflicts.add(Pair(current, next))
            }
        }
        return conflicts
    }

    /**
     * Identifies lower-priority tasks that should be postponed if the daily workload 
     * exceeds the user's available time.
     */
    fun suggestPostponements(
        tasks: List<TaskEntity>,
        availableTimeMinutes: Int
    ): Pair<List<TaskEntity>, List<TaskEntity>> {
        val sortedTasks = sortTasksByPriority(tasks)
        
        var currentWorkload = 0
        val approvedTasks = mutableListOf<TaskEntity>()
        val postponedTasks = mutableListOf<TaskEntity>()

        for (task in sortedTasks) {
            val duration = task.estimatedDurationMinutes ?: 30
            if (currentWorkload + duration <= availableTimeMinutes) {
                approvedTasks.add(task)
                currentWorkload += duration
            } else {
                postponedTasks.add(task)
            }
        }

        return Pair(approvedTasks, postponedTasks)
    }
}
