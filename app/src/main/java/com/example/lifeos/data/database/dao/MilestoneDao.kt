package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.GoalMilestoneEntity
import com.example.lifeos.data.database.entities.ProjectMilestoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {
    // Goal milestones
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalMilestone(milestone: GoalMilestoneEntity)

    @Update
    suspend fun updateGoalMilestone(milestone: GoalMilestoneEntity)

    @Delete
    suspend fun deleteGoalMilestone(milestone: GoalMilestoneEntity)

    @Query("SELECT * FROM goal_milestones WHERE goalId = :goalId ORDER BY position ASC")
    fun getMilestonesForGoal(goalId: String): Flow<List<GoalMilestoneEntity>>

    @Query("SELECT COUNT(*) FROM goal_milestones WHERE goalId = :goalId")
    suspend fun countMilestonesForGoal(goalId: String): Int

    @Query("SELECT COUNT(*) FROM goal_milestones WHERE goalId = :goalId AND isCompleted = 1")
    suspend fun countCompletedMilestonesForGoal(goalId: String): Int

    // Project milestones
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectMilestone(milestone: ProjectMilestoneEntity)

    @Update
    suspend fun updateProjectMilestone(milestone: ProjectMilestoneEntity)

    @Delete
    suspend fun deleteProjectMilestone(milestone: ProjectMilestoneEntity)

    @Query("SELECT * FROM project_milestones WHERE projectId = :projectId ORDER BY position ASC")
    fun getMilestonesForProject(projectId: String): Flow<List<ProjectMilestoneEntity>>

    @Query("SELECT COUNT(*) FROM project_milestones WHERE projectId = :projectId")
    suspend fun countMilestonesForProject(projectId: String): Int

    @Query("SELECT COUNT(*) FROM project_milestones WHERE projectId = :projectId AND isCompleted = 1")
    suspend fun countCompletedMilestonesForProject(projectId: String): Int
}
