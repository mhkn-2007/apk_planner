package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.SubtaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtask(subtask: SubtaskEntity)

    @Update
    suspend fun updateSubtask(subtask: SubtaskEntity)

    @Delete
    suspend fun deleteSubtask(subtask: SubtaskEntity)

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    fun getSubtasksForTask(taskId: String): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position ASC")
    suspend fun getSubtasksForTaskOnce(taskId: String): List<SubtaskEntity>

    @Query("SELECT COUNT(*) FROM subtasks WHERE taskId = :taskId")
    suspend fun countSubtasksForTask(taskId: String): Int

    @Query("SELECT COUNT(*) FROM subtasks WHERE taskId = :taskId AND isCompleted = 1")
    suspend fun countCompletedSubtasksForTask(taskId: String): Int

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteAllForTask(taskId: String)
}
