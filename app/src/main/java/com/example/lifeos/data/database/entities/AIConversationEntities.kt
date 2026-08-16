package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A single AI chat conversation (prompt section 43: AIConversation entity).
 * LifeOS keeps exactly one ongoing conversation at a time for now — this
 * entity exists so that history survives closing the app (prompt section 60:
 * "store relevant AI conversation history locally"), not to support multiple
 * simultaneous chat threads yet.
 */
@Entity(tableName = "ai_conversations")
data class AIConversationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val startedAtMillis: Long = System.currentTimeMillis(),
    val lastMessageAtMillis: Long = System.currentTimeMillis()
)

/**
 * A single message within an [AIConversationEntity] (prompt section 43:
 * AIMessage entity). Stores only what's needed to resume the conversation
 * and show it in the UI — no extra sensitive data is retained beyond the
 * message text itself, per prompt section 60 ("do not store unnecessary
 * sensitive information").
 */
@Entity(
    tableName = "ai_messages",
    foreignKeys = [
        ForeignKey(
            entity = AIConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class AIMessageEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: String, // "user" or "model" (matches Gemini's role naming)
    val text: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val position: Int = 0 // preserves ordering even if createdAtMillis ties
)
