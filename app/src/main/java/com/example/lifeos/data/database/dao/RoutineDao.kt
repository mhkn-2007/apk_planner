package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.RoutineInstanceEntity
import com.example.lifeos.data.database.entities.RoutineInstanceTaskEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.RoutineTemplateTaskEntity
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

    // Template tasks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateTask(task: RoutineTemplateTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateTasks(tasks: List<RoutineTemplateTaskEntity>)

    @Update
    suspend fun updateTemplateTask(task: RoutineTemplateTaskEntity)

    @Delete
    suspend fun deleteTemplateTask(task: RoutineTemplateTaskEntity)

    /**
     * Clears every task under a template so [RoutinesViewModel.updateTemplate]
     * can replace the whole ordered list in one edit (prompt section 12:
     * "Edit routines") rather than diffing individual rows.
     */
    @Query("DELETE FROM routine_template_tasks WHERE templateId = :templateId")
    suspend fun deleteAllTemplateTasks(templateId: String)

    @Query("SELECT * FROM routine_template_tasks WHERE templateId = :templateId ORDER BY position ASC")
    fun getTemplateTasks(templateId: String): Flow<List<RoutineTemplateTaskEntity>>

    @Query("SELECT * FROM routine_template_tasks WHERE templateId = :templateId ORDER BY position ASC")
    suspend fun getTemplateTasksOnce(templateId: String): List<RoutineTemplateTaskEntity>

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

    @Query("SELECT * FROM routine_instances WHERE id = :instanceId")
    suspend fun getInstanceById(instanceId: String): RoutineInstanceEntity?

    // Instance tasks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstanceTask(task: RoutineInstanceTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstanceTasks(tasks: List<RoutineInstanceTaskEntity>)

    @Update
    suspend fun updateInstanceTask(task: RoutineInstanceTaskEntity)

    @Delete
    suspend fun deleteInstanceTask(task: RoutineInstanceTaskEntity)

    @Query("SELECT * FROM routine_instance_tasks WHERE instanceId = :instanceId ORDER BY position ASC")
    fun getInstanceTasks(instanceId: String): Flow<List<RoutineInstanceTaskEntity>>

    @Query("SELECT COUNT(*) FROM routine_instance_tasks WHERE instanceId = :instanceId")
    suspend fun countInstanceTasks(instanceId: String): Int

    @Query("SELECT COUNT(*) FROM routine_instance_tasks WHERE instanceId = :instanceId AND isCompleted = 1")
    suspend fun countCompletedInstanceTasks(instanceId: String): Int
}
