package com.example.lifeos.ai.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A mock provider so the app runs and tests without needing an API key initially.
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
