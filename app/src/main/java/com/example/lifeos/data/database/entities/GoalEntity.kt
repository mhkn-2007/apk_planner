package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val deadlineMillis: Long? = null,
    val priority: Int = 0,
    val categoryId: String? = null,
    val progressPercentage: Int = 0, // 0 to 100
    val createdAtMillis: Long = System.currentTimeMillis()
)
