package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * An audit-log row for a single AI-driven mutation (prompt section 44:
 * AIAction entity). Every call through [com.example.lifeos.ai.tools.AIToolLayer]
 * that actually changes data — as opposed to a read-only `get_*` tool —
 * gets one row here, independent of the AIConversation/AIMessage chat
 * transcript. This gives a plain "what did the AI actually do to my data"
 * history, and is the natural place a future "undo last AI action" or
 * per-action audit UI (prompt section 51: protect against unauthorized
 * tool execution / data leakage) would read from.
 */
@Entity(tableName = "ai_actions")
data class AIActionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    /** Human-readable summary, e.g. "تسک «خرید نان» ایجاد شد". */
    val summary: String,
    val wasSuccessful: Boolean,
    val requiredConfirmation: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
