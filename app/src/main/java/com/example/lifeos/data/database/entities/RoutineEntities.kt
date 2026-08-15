package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "routine_templates")
data class RoutineTemplateEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val defaultStartTimeMillis: Long? = null, // Time of day for reminder
    val isRecurring: Boolean = false,
    val recurrencePattern: String? = null
)

@Entity(tableName = "routine_instances")
data class RoutineInstanceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val templateId: String,
    val dateMillis: Long, // the day this routine is instantiated for
    val isCompleted: Boolean = false
)
