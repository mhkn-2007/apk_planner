package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One completion record for a [HabitEntity] on a specific (Jalali) date
 * (prompt section 44: Habit -> many HabitLogs).
 *
 * [HabitEntity.currentStreak]/[HabitEntity.lastCompletedDate] only track the
 * running streak, which is enough to render a checkbox but not enough to
 * answer "how consistent was I this week/month" (prompt section 17: daily
 * completion, weekly/monthly statistics, progress charts). This table is the
 * actual per-day history those views need.
 */
@Entity(
    tableName = "habit_logs",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("habitId"), Index(value = ["habitId", "dateKey"], unique = true)]
)
data class HabitLogEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val habitId: String,
    // Jalali date key formatted "yyyy-MM-dd", same format as
    // HabitEntity.lastCompletedDate, so a day can only be logged once.
    val dateKey: String,
    val completedAtMillis: Long = System.currentTimeMillis()
)
