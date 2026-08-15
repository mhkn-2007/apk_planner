package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("SELECT * FROM habits ORDER BY createdAtMillis DESC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET currentStreak = currentStreak + 1, longestStreak = MAX(longestStreak, currentStreak + 1) WHERE id = :habitId")
    suspend fun incrementStreak(habitId: String)

    @Query("UPDATE habits SET currentStreak = 0 WHERE id = :habitId")
    suspend fun resetStreak(habitId: String)
}
