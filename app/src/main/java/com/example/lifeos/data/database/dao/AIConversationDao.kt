package com.example.lifeos.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lifeos.data.database.entities.AIConversationEntity
import com.example.lifeos.data.database.entities.AIMessageEntity

@Dao
interface AIConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: AIConversationEntity)

    @Update
    suspend fun updateConversation(conversation: AIConversationEntity)

    @Query("SELECT * FROM ai_conversations ORDER BY lastMessageAtMillis DESC LIMIT 1")
    suspend fun getMostRecentConversation(): AIConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AIMessageEntity)

    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY position ASC")
    suspend fun getMessagesForConversation(conversationId: String): List<AIMessageEntity>

    @Query("SELECT COUNT(*) FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun countMessagesForConversation(conversationId: String): Int

    @Query("DELETE FROM ai_conversations")
    suspend fun clearAllConversations()
}
