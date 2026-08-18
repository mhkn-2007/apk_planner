package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("taskId")]
)
data class ReminderEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val taskId: String,
    val triggerTimeMillis: Long,
    val title: String? = null,
    val message: String? = null,
    val isEnabled: Boolean = true,
    // Same meaning as TaskEntity.isAlarmRing: when true (default), this
    // reminder fires as a full-screen ringing alarm instead of a plain
    // notification.
    val isAlarmRing: Boolean = true
)
