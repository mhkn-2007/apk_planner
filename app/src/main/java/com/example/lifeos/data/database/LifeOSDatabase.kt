package com.example.lifeos.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.lifeos.data.database.dao.TaskDao
import com.example.lifeos.data.database.entities.ReminderEntity
import com.example.lifeos.data.database.entities.SubtaskEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.RoutineInstanceEntity

@Database(
    entities = [
        TaskEntity::class,
        SubtaskEntity::class,
        ReminderEntity::class,
        GoalEntity::class,
        ProjectEntity::class,
        HabitEntity::class,
        RoutineTemplateEntity::class,
        RoutineInstanceEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LifeOSDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
