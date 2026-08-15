package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE taskId = :taskId ORDER BY triggerTimeMillis ASC")
    fun getRemindersForTask(taskId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE taskId = :taskId ORDER BY triggerTimeMillis ASC")
    suspend fun getRemindersForTaskOnce(taskId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE isEnabled = 1")
    suspend fun getAllEnabledReminders(): List<ReminderEntity>

    @Query("UPDATE reminders SET isEnabled = :enabled WHERE id = :reminderId")
    suspend fun setEnabled(reminderId: String, enabled: Boolean)

    @Query("DELETE FROM reminders WHERE taskId = :taskId")
    suspend fun deleteAllForTask(taskId: String)
}
