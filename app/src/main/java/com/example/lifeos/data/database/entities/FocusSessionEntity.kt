package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single Focus/Pomodoro session (prompt section 18: Focus Mode,
 * section 43/44: FocusSession entity, "Task -> many FocusSessions").
 *
 * A session can optionally be started directly from a [TaskEntity]
 * ("Start Focus" on a task), in which case [taskId] links it back so the
 * task's total focus time and session history can be shown. Sessions
 * started from the standalone Focus screen (no task selected) have a
 * null [taskId].
 *
 * [type] distinguishes a work interval from a short/long break so
 * history and statistics (section 19: Analytics -> focus time) can
 * separate actual focused work from rest periods.
 */
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("taskId"), Index("startTimeMillis")]
)
data class FocusSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    // "WORK", "SHORT_BREAK", "LONG_BREAK"
    val type: String = "WORK",
    val plannedDurationMinutes: Int,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val endTimeMillis: Long? = null,
    // Actual focused time, which can be less than plannedDurationMinutes*60
    // if the session was stopped early.
    val actualDurationSeconds: Int = 0,
    // True only if the session ran to completion (timer reached zero),
    // as opposed to being cancelled/interrupted early. Used to keep
    // streaks/statistics meaningful (interrupted sessions still count
    // toward actual focus time, but not toward "completed sessions").
    val wasCompleted: Boolean = false
)
