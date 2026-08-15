package com.example.lifeos.di

import com.example.lifeos.ai.provider.AIProvider
import com.example.lifeos.ai.provider.MockAIProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {

    @Binds
    abstract fun bindAIProvider(
        mockAIProvider: MockAIProvider
    ): AIProvider
}
