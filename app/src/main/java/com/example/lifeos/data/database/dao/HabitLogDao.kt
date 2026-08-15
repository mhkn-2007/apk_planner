package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lifeos.data.database.entities.HabitLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HabitLogEntity)

    @Delete
    suspend fun deleteLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND dateKey = :dateKey")
    suspend fun deleteLogForDate(habitId: String, dateKey: String)

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY dateKey DESC")
    fun getLogsForHabit(habitId: String): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND dateKey BETWEEN :startDateKey AND :endDateKey ORDER BY dateKey ASC")
    fun getLogsForHabitInRange(habitId: String, startDateKey: String, endDateKey: String): Flow<List<HabitLogEntity>>

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :habitId AND dateKey BETWEEN :startDateKey AND :endDateKey")
    suspend fun countLogsInRange(habitId: String, startDateKey: String, endDateKey: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM habit_logs WHERE habitId = :habitId AND dateKey = :dateKey)")
    suspend fun hasLogForDate(habitId: String, dateKey: String): Boolean
}
