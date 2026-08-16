package com.example.lifeos.domain.planner

import com.example.lifeos.data.database.entities.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DeterministicPlannerEngine] — prompt section 41 requires a
 * deterministic scheduling engine independent of AI, and section 57
 * explicitly lists "Scheduling" and "Conflict detection" as required test
 * coverage. Previously this class (which backs the workload/conflict
 * warnings on both Today and Calendar) had zero tests.
 */
class DeterministicPlannerEngineTest {

    private val engine = DeterministicPlannerEngine()

    private fun task(
        title: String,
        priority: Int = 0,
        dueDateMillis: Long? = null,
        startTimeMillis: Long? = null,
        endTimeMillis: Long? = null,
        estimatedDurationMinutes: Int? = null
    ) = TaskEntity(
        title = title,
        priority = priority,
        dueDateMillis = dueDateMillis,
        startTimeMillis = startTimeMillis,
        endTimeMillis = endTimeMillis,
        estimatedDurationMinutes = estimatedDurationMinutes
    )

    // --- sortTasksByPriority ---

    @Test
    fun sortTasksByPriority_ordersHighestPriorityFirst() {
        val low = task("low", priority = 1)
        val critical = task("critical", priority = 4)
        val medium = task("medium", priority = 2)

        val sorted = engine.sortTasksByPriority(listOf(low, critical, medium))

        assertEquals(listOf(critical, medium, low), sorted)
    }

    @Test
    fun sortTasksByPriority_breaksTiesByEarlierDeadline() {
        val laterDeadline = task("later", priority = 3, dueDateMillis = 2000L)
        val earlierDeadline = task("earlier", priority = 3, dueDateMillis = 1000L)

        val sorted = engine.sortTasksByPriority(listOf(laterDeadline, earlierDeadline))

        assertEquals(listOf(earlierDeadline, laterDeadline), sorted)
    }

    @Test
    fun sortTasksByPriority_taskWithNoDeadlineSortsLastAmongEqualPriority() {
        val withDeadline = task("has deadline", priority = 2, dueDateMillis = 1000L)
        val noDeadline = task("no deadline", priority = 2, dueDateMillis = null)

        val sorted = engine.sortTasksByPriority(listOf(noDeadline, withDeadline))

        assertEquals(listOf(withDeadline, noDeadline), sorted)
    }

    // --- calculateTotalWorkload ---

    @Test
    fun calculateTotalWorkload_sumsEstimatedDurations() {
        val tasks = listOf(
            task("a", estimatedDurationMinutes = 30),
            task("b", estimatedDurationMinutes = 45),
            task("c", estimatedDurationMinutes = 15)
        )

        assertEquals(90, engine.calculateTotalWorkload(tasks))
    }

    @Test
    fun calculateTotalWorkload_defaultsMissingDurationToThirtyMinutes() {
        val tasks = listOf(task("no duration", estimatedDurationMinutes = null))

        assertEquals(30, engine.calculateTotalWorkload(tasks))
    }

    @Test
    fun calculateTotalWorkload_emptyListIsZero() {
        assertEquals(0, engine.calculateTotalWorkload(emptyList()))
    }

    // --- detectConflicts ---

    @Test
    fun detectConflicts_flagsDirectlyOverlappingTasks() {
        // 09:00-10:00 and 09:30-10:30 overlap.
        val a = task("a", startTimeMillis = 9_000L, endTimeMillis = 10_000L)
        val b = task("b", startTimeMillis = 9_500L, endTimeMillis = 10_500L)

        val conflicts = engine.detectConflicts(listOf(a, b))

        assertEquals(1, conflicts.size)
        assertEquals(a, conflicts[0].first)
        assertEquals(b, conflicts[0].second)
    }

    @Test
    fun detectConflicts_backToBackTasksDoNotConflict() {
        // 09:00-10:00 followed immediately by 10:00-11:00: touching, not overlapping.
        val a = task("a", startTimeMillis = 9_000L, endTimeMillis = 10_000L)
        val b = task("b", startTimeMillis = 10_000L, endTimeMillis = 11_000L)

        assertTrue(engine.detectConflicts(listOf(a, b)).isEmpty())
    }

    @Test
    fun detectConflicts_ignoresUnscheduledTasks() {
        // Tasks without both a start and end time aren't part of conflict detection.
        val unscheduled = task("no time", startTimeMillis = null, endTimeMillis = null)
        val partiallyScheduled = task("only start", startTimeMillis = 9_000L, endTimeMillis = null)

        assertTrue(engine.detectConflicts(listOf(unscheduled, partiallyScheduled)).isEmpty())
    }

    /**
     * Regression test: a task spanning a wide window (A) can conflict with a
     * later task (C) that doesn't conflict with the task in between them (B)
     * in start-time order. A naive "only compare adjacent pairs after
     * sorting" implementation misses the A-C conflict entirely, since A and
     * C are never adjacent in the sorted list (B sits between them).
     */
    @Test
    fun detectConflicts_findsConflictBetweenNonAdjacentTasksInSortedOrder() {
        // A: 09:00-11:00 (wide window)
        // B: 09:30-10:00 (nested inside A, sorted between A and C)
        // C: 10:15-10:45 (also nested inside A, but does NOT overlap B)
        val a = task("a-wide", startTimeMillis = 9_00_00L, endTimeMillis = 11_00_00L)
        val b = task("b-nested", startTimeMillis = 9_30_00L, endTimeMillis = 10_00_00L)
        val c = task("c-nested", startTimeMillis = 10_15_00L, endTimeMillis = 10_45_00L)

        val conflicts = engine.detectConflicts(listOf(a, b, c))

        val conflictPairs = conflicts.map { setOf(it.first.title, it.second.title) }
        assertTrue("A and B should conflict", setOf("a-wide", "b-nested") in conflictPairs)
        assertTrue("A and C should conflict (this is the case adjacent-only comparison misses)", setOf("a-wide", "c-nested") in conflictPairs)
        assertTrue("B and C should NOT conflict", setOf("b-nested", "c-nested") !in conflictPairs)
        assertEquals(2, conflicts.size)
    }

    @Test
    fun detectConflicts_threeMutuallyOverlappingTasksProduceThreePairs() {
        val a = task("a", startTimeMillis = 0L, endTimeMillis = 300L)
        val b = task("b", startTimeMillis = 100L, endTimeMillis = 400L)
        val c = task("c", startTimeMillis = 200L, endTimeMillis = 500L)

        val conflicts = engine.detectConflicts(listOf(c, a, b)) // unsorted input on purpose

        assertEquals(3, conflicts.size) // a-b, a-c, b-c
    }

    // --- suggestPostponements ---

    @Test
    fun suggestPostponements_fitsHighestPriorityTasksFirstWithinAvailableTime() {
        val critical = task("critical", priority = 4, estimatedDurationMinutes = 60)
        val low = task("low", priority = 1, estimatedDurationMinutes = 60)

        val (approved, postponed) = engine.suggestPostponements(listOf(low, critical), availableTimeMinutes = 60)

        assertEquals(listOf(critical), approved)
        assertEquals(listOf(low), postponed)
    }

    @Test
    fun suggestPostponements_allTasksFitWhenWithinBudget() {
        val a = task("a", estimatedDurationMinutes = 30)
        val b = task("b", estimatedDurationMinutes = 30)

        val (approved, postponed) = engine.suggestPostponements(listOf(a, b), availableTimeMinutes = 120)

        assertEquals(2, approved.size)
        assertTrue(postponed.isEmpty())
    }

    @Test
    fun suggestPostponements_noTasksFitWhenBudgetIsZero() {
        val a = task("a", estimatedDurationMinutes = 30)

        val (approved, postponed) = engine.suggestPostponements(listOf(a), availableTimeMinutes = 0)

        assertTrue(approved.isEmpty())
        assertEquals(listOf(a), postponed)
    }

    @Test
    fun suggestPostponements_exactFitIsApproved() {
        val a = task("a", estimatedDurationMinutes = 60)

        val (approved, postponed) = engine.suggestPostponements(listOf(a), availableTimeMinutes = 60)

        assertEquals(listOf(a), approved)
        assertTrue(postponed.isEmpty())
    }
}
