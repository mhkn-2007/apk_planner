package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity)

    @Update
    suspend fun updateSession(session: FocusSessionEntity)

    @Delete
    suspend fun deleteSession(session: FocusSessionEntity)

    @Query("SELECT * FROM focus_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions ORDER BY startTimeMillis DESC")
    fun getAllSessions(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE taskId = :taskId ORDER BY startTimeMillis DESC")
    fun getSessionsForTask(taskId: String): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions WHERE startTimeMillis BETWEEN :startMillis AND :endMillis ORDER BY startTimeMillis DESC")
    fun getSessionsInRange(startMillis: Long, endMillis: Long): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM(actualDurationSeconds), 0) FROM focus_sessions WHERE type = 'WORK' AND taskId = :taskId")
    suspend fun getTotalFocusSecondsForTask(taskId: String): Int

    @Query("SELECT COALESCE(SUM(actualDurationSeconds), 0) FROM focus_sessions WHERE type = 'WORK' AND startTimeMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getTotalFocusSecondsInRange(startMillis: Long, endMillis: Long): Int

    @Query("SELECT COUNT(*) FROM focus_sessions WHERE type = 'WORK' AND wasCompleted = 1 AND startTimeMillis BETWEEN :startMillis AND :endMillis")
    suspend fun getCompletedWorkSessionCountInRange(startMillis: Long, endMillis: Long): Int
}
