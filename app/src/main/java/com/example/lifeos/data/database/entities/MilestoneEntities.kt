package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A checkpoint on the way to a [GoalEntity] (prompt section 15).
 * Goal -> Milestones -> Projects -> Tasks -> Daily Actions.
 */
@Entity(
    tableName = "goal_milestones",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("goalId")]
)
data class GoalMilestoneEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val goalId: String,
    val title: String,
    val deadlineMillis: Long? = null,
    val isCompleted: Boolean = false,
    val position: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * A checkpoint within a [ProjectEntity] (prompt section 16).
 */
@Entity(
    tableName = "project_milestones",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ProjectMilestoneEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val deadlineMillis: Long? = null,
    val isCompleted: Boolean = false,
    val position: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)
