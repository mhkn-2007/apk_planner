package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.lifeos.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY dueDateMillis ASC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE dueDateMillis >= :startOfDay AND dueDateMillis <= :endOfDay")
    fun getTasksForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND dueDateMillis < :currentTimeMillis")
    suspend fun getOverdueTasks(currentTimeMillis: Long): List<TaskEntity>

    /**
     * Existing occurrences already generated for a recurring series
     * (identified by [TaskEntity.recurrenceGroupId]) within a date range.
     * Used to avoid regenerating/duplicating occurrences that already exist.
     */
    @Query("SELECT dueDateMillis FROM tasks WHERE recurrenceGroupId = :groupId AND dueDateMillis >= :startOfDay AND dueDateMillis <= :endOfDay")
    suspend fun getExistingOccurrenceDates(groupId: String, startOfDay: Long, endOfDay: Long): List<Long?>

    @Query("SELECT * FROM tasks WHERE recurrenceGroupId = :groupId ORDER BY dueDateMillis ASC")
    fun getTasksForRecurrenceGroup(groupId: String): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks WHERE recurrenceGroupId = :groupId AND isCompleted = 0 AND dueDateMillis > :fromMillis")
    suspend fun deleteFutureUncompletedOccurrences(groupId: String, fromMillis: Long)

    // --- Analytics (prompt section 19) ---

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1 AND completedAtMillis BETWEEN :startMillis AND :endMillis")
    suspend fun countCompletedInRange(startMillis: Long, endMillis: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE dueDateMillis BETWEEN :startMillis AND :endMillis")
    suspend fun countScheduledInRange(startMillis: Long, endMillis: Long): Int

    /**
     * Tasks originally due within the range but either still not completed,
     * or completed on a later day than they were due — i.e. postponed.
     * Used for the "you often postpone tasks" style insight (section 19).
     */
    @Query(
        "SELECT COUNT(*) FROM tasks WHERE dueDateMillis BETWEEN :startMillis AND :endMillis " +
        "AND (isCompleted = 0 OR completedAtMillis > :endMillis)"
    )
    suspend fun countPostponedInRange(startMillis: Long, endMillis: Long): Int

    @Query("SELECT completedAtMillis FROM tasks WHERE isCompleted = 1 AND completedAtMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getCompletionTimestampsInRange(startMillis: Long, endMillis: Long): List<Long?>

    // --- Goal/Project progress (sections 15-16: "Progress" field on both) ---

    @Query("SELECT COUNT(*) FROM tasks WHERE goalId = :goalId")
    suspend fun countTasksForGoal(goalId: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE goalId = :goalId AND isCompleted = 1")
    suspend fun countCompletedTasksForGoal(goalId: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId")
    suspend fun countTasksForProject(projectId: String): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE projectId = :projectId AND isCompleted = 1")
    suspend fun countCompletedTasksForProject(projectId: String): Int
}
