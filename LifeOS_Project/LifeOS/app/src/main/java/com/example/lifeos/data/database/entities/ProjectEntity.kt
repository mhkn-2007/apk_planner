package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "projects",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("goalId")]
)
data class ProjectEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val deadlineMillis: Long? = null,
    val status: String = "PENDING", // PENDING, IN_PROGRESS, COMPLETED
    val priority: Int = 0,
    val goalId: String? = null, // Linked goal
    val progressPercentage: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis()
)
