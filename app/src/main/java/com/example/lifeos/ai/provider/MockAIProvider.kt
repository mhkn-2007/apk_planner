package com.example.lifeos.ai.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline placeholder provider — not currently bound (see [com.example.lifeos.di.AIModule],
 * which binds [GeminiProvider] instead). Kept as an example of the
 * [AIProvider] abstraction (prompt section 36) and as a safe fallback
 * implementation to bind back in if a network provider should be disabled
 * for a build variant.
 */
@Singleton
class MockAIProvider @Inject constructor() : AIProvider {

    override fun streamChat(message: String, contextData: String): Flow<String> = flow {
        val words = "من در حال حاضر یک دستیار آزمایشی (آفلاین) هستم. پیام شما دریافت شد: $message".split(" ")
        var current = ""
        for (word in words) {
            delay(150) // simulate typing
            current += "$word "
            emit(current)
        }
    }

    override suspend fun sendMessage(message: String, contextData: String): String {
        delay(1000)
        return "من آماده کمک هستم، اما به عنوان دستیار آزمایشی پاسخگوی دستورات پیچیده نیستم."
    }

    override suspend fun parseAction(naturalLanguage: String): String {
        delay(800)
        // Mock parsing result
        return """
            {
                "action": "CREATE_TASK",
                "title": "کار جدید از طریق دستیار",
                "priority": 1
            }
        """.trimIndent()
    }
}
