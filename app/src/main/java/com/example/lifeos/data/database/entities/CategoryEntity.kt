package com.example.lifeos.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A user-defined category for organizing tasks/goals (prompt section 44:
 * Data Model -> Category). [TaskEntity.categoryId] and
 * [GoalEntity.categoryId] already reference this table's id; this entity
 * fills in the table itself, which was previously missing (leaving those
 * foreign-key-shaped fields as unusable dead columns).
 *
 * [colorHex] lets the UI render a small colored dot/chip per category
 * (e.g. task list filter chips) without hardcoding a palette — the user
 * picks the color when creating the category.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#4A90D9",
    val createdAtMillis: Long = System.currentTimeMillis()
)
