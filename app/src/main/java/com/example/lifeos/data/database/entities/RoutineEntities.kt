package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
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
    val recurrencePattern: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * A reusable task belonging to a [RoutineTemplateEntity]. This is the
 * definition that gets copied into a [RoutineInstanceTaskEntity] whenever the
 * template is added to a specific day (prompt section 12: "Editing a
 * RoutineInstance must NOT automatically modify the original RoutineTemplate").
 */
@Entity(
    tableName = "routine_template_tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoutineTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class RoutineTemplateTaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val templateId: String,
    val title: String,
    val estimatedDurationMinutes: Int? = null,
    val position: Int = 0
)

@Entity(
    tableName = "routine_instances",
    foreignKeys = [
        ForeignKey(
            entity = RoutineTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId")]
)
data class RoutineInstanceEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val templateId: String,
    val dateMillis: Long, // the day this routine is instantiated for
    val isCompleted: Boolean = false
)

/**
 * A copy of a [RoutineTemplateTaskEntity] scoped to one [RoutineInstanceEntity].
 * Users can freely edit/reorder/complete these without ever touching the
 * source template.
 */
@Entity(
    tableName = "routine_instance_tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoutineInstanceEntity::class,
            parentColumns = ["id"],
            childColumns = ["instanceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("instanceId")]
)
data class RoutineInstanceTaskEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val instanceId: String,
    val title: String,
    val estimatedDurationMinutes: Int? = null,
    val position: Int = 0,
    val isCompleted: Boolean = false
)
