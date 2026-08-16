package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A calendar event, distinct from [TaskEntity] (prompt sections 13, 44).
 *
 * The prompt is explicit that external calendar sync (Google Calendar,
 * Outlook, etc.) is "not required for the first release" but that the
 * architecture should allow it later. This entity is that scaffold: a
 * source-agnostic event row an external sync adapter could write into
 * without needing a schema change, and [sourceId]/[provider] let it be
 * matched back to the remote event it came from. LifeOS-native scheduled
 * items continue to live on [TaskEntity] as before — this table has no
 * consumer yet and is intentionally not wired into any screen until an
 * actual sync integration exists to populate it.
 */
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val isAllDay: Boolean = false,
    val location: String? = null,
    /** e.g. "google", "outlook". Null means a LifeOS-local event. */
    val provider: String? = null,
    /** The remote event's own id, for de-duplication on re-sync. */
    val sourceId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)
