package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val frequencyType: String = "DAILY", // DAILY, WEEKLY, SPECIFIC_DAYS
    val frequencyValue: String? = null, // E.g., "1,3,5" for Mon/Wed/Fri
    val targetCount: Int = 1,
    val goalId: String? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletedDate: String? = null, // yyyy-MM-dd
    val createdAtMillis: Long = System.currentTimeMillis()
)
