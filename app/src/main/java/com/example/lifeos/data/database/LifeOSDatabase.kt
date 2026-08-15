package com.example.lifeos.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.lifeos.data.database.dao.FocusSessionDao
import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.HabitDao
import com.example.lifeos.data.database.dao.HabitLogDao
import com.example.lifeos.data.database.dao.MilestoneDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.dao.ReminderDao
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.dao.SubtaskDao
import com.example.lifeos.data.database.dao.TaskDao
import com.example.lifeos.data.database.entities.FocusSessionEntity
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.GoalMilestoneEntity
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.HabitLogEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.ProjectMilestoneEntity
import com.example.lifeos.data.database.entities.ReminderEntity
import com.example.lifeos.data.database.entities.RoutineInstanceEntity
import com.example.lifeos.data.database.entities.RoutineInstanceTaskEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.RoutineTemplateTaskEntity
import com.example.lifeos.data.database.entities.SubtaskEntity
import com.example.lifeos.data.database.entities.TaskEntity

@Database(
    entities = [
        TaskEntity::class,
        SubtaskEntity::class,
        ReminderEntity::class,
        GoalEntity::class,
        ProjectEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        RoutineTemplateEntity::class,
        RoutineTemplateTaskEntity::class,
        RoutineInstanceEntity::class,
        RoutineInstanceTaskEntity::class,
        GoalMilestoneEntity::class,
        ProjectMilestoneEntity::class,
        FocusSessionEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class LifeOSDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun goalDao(): GoalDao
    abstract fun projectDao(): ProjectDao
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun routineDao(): RoutineDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun focusSessionDao(): FocusSessionDao
}
