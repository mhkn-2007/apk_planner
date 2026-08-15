package com.example.lifeos.ai.provider

import kotlinx.coroutines.flow.Flow

/**
 * Core abstraction for the AI engine. 
 * Allows swapping out Gemini, OpenAI, or a Local Model in the future.
 */
interface AIProvider {
    /**
     * Send a message to the AI and get a streaming response.
     */
    fun streamChat(message: String, contextData: String): Flow<String>

    /**
     * Send a single message and get a single response (blocking/suspend).
     */
    suspend fun sendMessage(message: String, contextData: String): String

    /**
     * Parses a natural language string into a structured JSON representing an action
     * (e.g., "Schedule math for tomorrow at 8am" -> { action: "CREATE_TASK", title: "Math", ... })
     */
    suspend fun parseAction(naturalLanguage: String): String
}
