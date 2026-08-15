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
}
