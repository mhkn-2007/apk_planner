package com.example.lifeos.di

import android.content.Context
import androidx.room.Room
import com.example.lifeos.data.database.LifeOSDatabase
import com.example.lifeos.data.database.TaskRepositoryImpl
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.dao.MilestoneDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.dao.ReminderDao
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.dao.SubtaskDao
import com.example.lifeos.data.database.dao.TaskDao
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
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideTaskDao(database: LifeOSDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideGoalDao(database: LifeOSDatabase): GoalDao {
        return database.goalDao()
    }

    @Provides
    fun provideProjectDao(database: LifeOSDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    fun provideHabitDao(database: LifeOSDatabase): HabitDao {
        return database.habitDao()
    }

    @Provides
    fun provideSubtaskDao(database: LifeOSDatabase): SubtaskDao {
        return database.subtaskDao()
    }

    @Provides
    fun provideReminderDao(database: LifeOSDatabase): ReminderDao {
        return database.reminderDao()
    }

    @Provides
    fun provideRoutineDao(database: LifeOSDatabase): RoutineDao {
        return database.routineDao()
    }

    @Provides
    fun provideMilestoneDao(database: LifeOSDatabase): MilestoneDao {
        return database.milestoneDao()
    }

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskDao: TaskDao,
        subtaskDao: SubtaskDao,
        reminderDao: ReminderDao
    ): TaskRepository {
        return TaskRepositoryImpl(taskDao, subtaskDao, reminderDao)
    }
}
