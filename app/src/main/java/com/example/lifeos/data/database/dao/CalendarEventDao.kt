package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity)

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Delete
    suspend fun deleteEvent(event: CalendarEventEntity)

    @Query("SELECT * FROM calendar_events WHERE startTimeMillis <= :endOfRange AND endTimeMillis >= :startOfRange ORDER BY startTimeMillis ASC")
    fun getEventsInRange(startOfRange: Long, endOfRange: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE provider = :provider AND sourceId = :sourceId LIMIT 1")
    suspend fun findBySource(provider: String, sourceId: String): CalendarEventEntity?
}
