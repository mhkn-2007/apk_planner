package com.example.lifeos.di

import android.content.Context
import androidx.room.Room
import com.example.lifeos.data.database.LifeOSDatabase
import com.example.lifeos.data.database.dao.TaskDao
import com.example.lifeos.data.database.TaskRepositoryImpl
import com.example.lifeos.domain.repositories.TaskRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LifeOSDatabase {
        return Room.databaseBuilder(
            context,
            LifeOSDatabase::class.java,
            "lifeos_db"
        ).build()
    }

    @Provides
    fun provideTaskDao(database: LifeOSDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    @Singleton
    fun provideTaskRepository(taskDao: TaskDao): TaskRepository {
        return TaskRepositoryImpl(taskDao)
    }
}
