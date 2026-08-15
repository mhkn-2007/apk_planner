package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.RoutineInstanceEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    // Templates
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: RoutineTemplateEntity)

    @Update
    suspend fun updateTemplate(template: RoutineTemplateEntity)

    @Delete
    suspend fun deleteTemplate(template: RoutineTemplateEntity)

    @Query("SELECT * FROM routine_templates ORDER BY name ASC")
    fun getAllTemplates(): Flow<List<RoutineTemplateEntity>>

    @Query("SELECT * FROM routine_templates WHERE id = :templateId")
    suspend fun getTemplateById(templateId: String): RoutineTemplateEntity?

    // Instances
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstance(instance: RoutineInstanceEntity)

    @Update
    suspend fun updateInstance(instance: RoutineInstanceEntity)

    @Delete
    suspend fun deleteInstance(instance: RoutineInstanceEntity)

    @Query("SELECT * FROM routine_instances WHERE dateMillis >= :startOfDay AND dateMillis <= :endOfDay")
    fun getInstancesForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<RoutineInstanceEntity>>

    @Query("SELECT * FROM routine_instances WHERE templateId = :templateId ORDER BY dateMillis DESC")
    fun getInstancesForTemplate(templateId: String): Flow<List<RoutineInstanceEntity>>
}
