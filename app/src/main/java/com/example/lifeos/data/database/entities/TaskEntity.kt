package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val dueDateMillis: Long? = null, // Store as epoch millis
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long? = null,
    val estimatedDurationMinutes: Int? = null,
    val priority: Int = 0, // 0: None, 1: Low, 2: Medium, 3: High, 4: Critical
    val categoryId: String? = null,
    val goalId: String? = null,
    val projectId: String? = null,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
