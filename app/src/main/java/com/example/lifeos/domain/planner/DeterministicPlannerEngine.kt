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
     * Detects overlapping scheduled tasks (prompt section 34: "Detect...
     * Overlapping tasks... Overlapping time blocks").
     *
     * Compares every scheduled task against every other, not just adjacent
     * pairs in start-time order: a task spanning a wide window can conflict
     * with a later task that doesn't conflict with the tasks in between (a
     * containing block. e.g. 09:00-11:00 vs a task at 10:15-10:45, with an
     * unrelated 09:30-10:00 task also in the list). Comparing only
     * neighbours after sorting misses that pair. With N scheduled tasks this
     * is O(N^2), acceptable for a single day's task list.
     */
    fun detectConflicts(tasks: List<TaskEntity>): List<Pair<TaskEntity, TaskEntity>> {
        val conflicts = mutableListOf<Pair<TaskEntity, TaskEntity>>()
        val scheduledTasks = tasks.filter { it.startTimeMillis != null && it.endTimeMillis != null }
            .sortedBy { it.startTimeMillis }

        for (i in scheduledTasks.indices) {
            for (j in i + 1 until scheduledTasks.size) {
                val current = scheduledTasks[i]
                val other = scheduledTasks[j]
                // Sorted by start time, so other.startTimeMillis >= current.startTimeMillis
                // always holds here; once other starts at/after current ends,
                // nothing later in the sorted list can overlap current either
                // (their starts only increase), so it's safe to stop scanning
                // forward for this `current`.
                if (other.startTimeMillis!! >= current.endTimeMillis!!) break
                conflicts.add(Pair(current, other))
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
