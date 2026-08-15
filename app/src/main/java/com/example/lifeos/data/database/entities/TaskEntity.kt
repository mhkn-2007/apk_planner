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
    val habitId: String? = null, // links this task to a habit, if it was created from one
    val isCompleted: Boolean = false,
    val timeOfDay: String? = null, // MORNING, AFTERNOON, NIGHT, CUSTOM
    val alarmTimeMillis: Long? = null, // specific scheduled alarm time
    val createdAtMillis: Long = System.currentTimeMillis(),
    // --- Recurrence (prompt section 11) ---
    // Encoded rule, e.g. "DAILY", "WEEKLY:2,4" (Calendar.DAY_OF_WEEK values,
    // comma separated), "MONTHLY". Null means this task does not recur.
    val recurrenceRule: String? = null,
    // The id of the original task that defines the recurrence rule. Every
    // generated occurrence (including the first) shares this id, so the
    // series can be found/edited/deleted as a group without ever silently
    // multiplying rows beyond the generation window (spec: "must not create
    // uncontrolled duplicate tasks").
    val recurrenceGroupId: String? = null
)
