package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lifeos.data.database.entities.AIActionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AIActionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: AIActionEntity)

    @Query("SELECT * FROM ai_actions ORDER BY createdAtMillis DESC LIMIT :limit")
    fun getRecentActions(limit: Int = 50): Flow<List<AIActionEntity>>
}
