package com.example.lifeos.di

import com.example.lifeos.ai.provider.AIProvider
import com.example.lifeos.ai.provider.GeminiProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the concrete [AIProvider] implementation the app uses (prompt
 * section 36: AIProvider abstraction). [GeminiProvider] is the real,
 * network-backed implementation; swapping in a different provider (OpenAI,
 * a local on-device model) later only means changing this binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {

    @Binds
    abstract fun bindAIProvider(
        geminiProvider: GeminiProvider
    ): AIProvider
}
