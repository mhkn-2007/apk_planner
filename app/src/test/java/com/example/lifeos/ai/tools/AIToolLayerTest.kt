package com.example.lifeos.ai.tools

import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.MilestoneDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AIToolLayer] — the sole path through which the AI is
 * allowed to mutate application data (prompt section 22/35). Section 57
 * explicitly lists "AI action parsing" and "AI tool authorization" as
 * required test coverage; previously this class, despite being the most
 * safety-sensitive piece of the AI system, had none.
 *
 * These tests focus on what actually matters for safety: input validation
 * (a blank title must never silently create a garbage task), priority
 * clamping (the AI must never be able to push priority outside 0-4, per the
 * prompt's "do not blindly mark everything as critical" requirement,
 * section 8/29), not-found handling, and — most importantly — the
 * confirmation-threshold gating that prevents the AI from silently deleting
 * or moving a large batch of tasks without the user's explicit approval.
 */
class AIToolLayerTest {

    private lateinit var taskRepository: TaskRepository
    private lateinit var goalDao: GoalDao
    private lateinit var projectDao: ProjectDao
    private lateinit var routineDao: RoutineDao
    private lateinit var milestoneDao: MilestoneDao
    private lateinit var toolLayer: AIToolLayer

    @Before
    fun setUp() {
        taskRepository = mockk(relaxed = true)
        goalDao = mockk(relaxed = true)
        projectDao = mockk(relaxed = true)
        routineDao = mockk(relaxed = true)
        milestoneDao = mockk(relaxed = true)
        toolLayer = AIToolLayer(taskRepository, goalDao, projectDao, routineDao, milestoneDao)
    }

    private fun fakeTask(id: String, title: String = "task", priority: Int = 0) =
        TaskEntity(id = id, title = title, priority = priority)

    // --- createTask: validation & priority clamping ---

    @Test
    fun createTask_rejectsBlankTitle() = runTest {
        val result = toolLayer.createTask(title = "   ")

        assertTrue(result is AIToolLayer.ToolResult.Failure)
        coVerify(exactly = 0) { taskRepository.insertTask(any()) }
    }

    @Test
    fun createTask_clampsPriorityAboveMaximum() = runTest {
        val slot = mutableListOf<TaskEntity>()
        coEvery { taskRepository.insertTask(capture(slot)) } returns Unit

        toolLayer.createTask(title = "study", priority = 99)

        assertEquals(4, slot.first().priority)
    }

    @Test
    fun createTask_clampsPriorityBelowMinimum() = runTest {
        val slot = mutableListOf<TaskEntity>()
        coEvery { taskRepository.insertTask(capture(slot)) } returns Unit

        toolLayer.createTask(title = "study", priority = -5)

        assertEquals(0, slot.first().priority)
    }

    @Test
    fun createTask_trimsWhitespaceFromTitle() = runTest {
        val slot = mutableListOf<TaskEntity>()
        coEvery { taskRepository.insertTask(capture(slot)) } returns Unit

        toolLayer.createTask(title = "  study math  ")

        assertEquals("study math", slot.first().title)
    }

    @Test
    fun createTask_succeedsAndInsertsOnValidInput() = runTest {
        val result = toolLayer.createTask(title = "study")

        assertTrue(result is AIToolLayer.ToolResult.Success)
        coVerify(exactly = 1) { taskRepository.insertTask(any()) }
    }

    // --- updateTask: not-found & priority clamping ---

    @Test
    fun updateTask_returnsFailureWhenTaskNotFound() = runTest {
        coEvery { taskRepository.getTaskById("missing") } returns null

        val result = toolLayer.updateTask(taskId = "missing", title = "new title")

        assertTrue(result is AIToolLayer.ToolResult.Failure)
        coVerify(exactly = 0) { taskRepository.updateTask(any()) }
    }

    @Test
    fun updateTask_clampsPriorityWhenProvided() = runTest {
        coEvery { taskRepository.getTaskById("t1") } returns fakeTask("t1")
        val slot = mutableListOf<TaskEntity>()
        coEvery { taskRepository.updateTask(capture(slot)) } returns Unit

        toolLayer.updateTask(taskId = "t1", priority = 10)

        assertEquals(4, slot.first().priority)
    }

    @Test
    fun updateTask_blankTitleLeavesOriginalTitleUnchanged() = runTest {
        coEvery { taskRepository.getTaskById("t1") } returns fakeTask("t1", title = "original")
        val slot = mutableListOf<TaskEntity>()
        coEvery { taskRepository.updateTask(capture(slot)) } returns Unit

        toolLayer.updateTask(taskId = "t1", title = "   ")

        assertEquals("original", slot.first().title)
    }

    // --- completeTask / deleteTask: not-found handling ---

    @Test
    fun completeTask_returnsFailureWhenTaskNotFound() = runTest {
        coEvery { taskRepository.getTaskById("missing") } returns null

        val result = toolLayer.completeTask("missing")

        assertTrue(result is AIToolLayer.ToolResult.Failure)
    }

    @Test
    fun deleteTask_returnsFailureWhenTaskNotFound() = runTest {
        coEvery { taskRepository.getTaskById("missing") } returns null

        val result = toolLayer.deleteTask("missing")

        assertTrue(result is AIToolLayer.ToolResult.Failure)
        coVerify(exactly = 0) { taskRepository.deleteTask(any()) }
    }

    // --- createRoutine / createGoal / createProject: validation ---

    @Test
    fun createRoutine_rejectsBlankName() = runTest {
        val result = toolLayer.createRoutine(name = "  ", taskTitles = listOf("wake up"))

        assertTrue(result is AIToolLayer.ToolResult.Failure)
    }

    @Test
    fun createRoutine_rejectsEmptyTaskList() = runTest {
        val result = toolLayer.createRoutine(name = "morning", taskTitles = emptyList())

        assertTrue(result is AIToolLayer.ToolResult.Failure)
    }

    @Test
    fun createGoal_rejectsBlankTitle() = runTest {
        val result = toolLayer.createGoal(title = "")

        assertTrue(result is AIToolLayer.ToolResult.Failure)
    }

    @Test
    fun createProject_rejectsBlankName() = runTest {
        val result = toolLayer.createProject(name = "   ")

        assertTrue(result is AIToolLayer.ToolResult.Failure)
    }

    // --- breakDownGoal: partial-data tolerance ---

    @Test
    fun breakDownGoal_rejectsBlankGoalTitle() = runTest {
        val result = toolLayer.breakDownGoal(goalTitle = "")

        assertTrue(result is AIToolLayer.ToolResult.Failure)
    }

    @Test
    fun breakDownGoal_succeedsWithOnlyAGoalTitle() = runTest {
        // Milestones and tasks are optional — a bare goal+project must still
        // be a valid result the user can build on manually.
        val result = toolLayer.breakDownGoal(goalTitle = "Prepare for exam")

        assertTrue(result is AIToolLayer.ToolResult.Success)
        coVerify(exactly = 1) { goalDao.insertGoal(any()) }
        coVerify(exactly = 1) { projectDao.insertProject(any()) }
    }

    @Test
    fun breakDownGoal_skipsBlankTaskTitlesWithoutFailing() = runTest {
        val result = toolLayer.breakDownGoal(
            goalTitle = "Prepare for exam",
            tasks = listOf(
                AIToolLayer.BreakdownTask(title = "Study chapter 1"),
                AIToolLayer.BreakdownTask(title = "   "), // should be silently skipped, not fail the whole call
                AIToolLayer.BreakdownTask(title = "Study chapter 2")
            )
        )

        assertTrue(result is AIToolLayer.ToolResult.Success)
        coVerify(exactly = 2) { taskRepository.insertTask(any()) }
    }

    // --- Confirmation-threshold gating (prompt section 35) ---
    // This is the most safety-critical behavior in the class: the AI must
    // never be able to silently delete or move a large batch of tasks
    // without the user explicitly approving a preview first.

    @Test
    fun deleteLowPriorityTasks_appliesImmediatelyWhenAtOrBelowThreshold() = runTest {
        val ids = (1..AIToolLayer.CONFIRMATION_THRESHOLD).map { "t$it" }
        ids.forEach { id -> coEvery { taskRepository.getTaskById(id) } returns fakeTask(id, priority = 0) }

        val result = toolLayer.deleteLowPriorityTasks(ids)

        assertTrue("expected immediate Success, got $result", result is AIToolLayer.ToolResult.Success)
        coVerify(exactly = ids.size) { taskRepository.deleteTask(any()) }
    }

    @Test
    fun deleteLowPriorityTasks_requiresConfirmationAboveThreshold() = runTest {
        val ids = (1..AIToolLayer.CONFIRMATION_THRESHOLD + 1).map { "t$it" }
        ids.forEach { id -> coEvery { taskRepository.getTaskById(id) } returns fakeTask(id, priority = 0) }

        val result = toolLayer.deleteLowPriorityTasks(ids)

        assertTrue(
            "expected RequiresConfirmation above threshold, got $result",
            result is AIToolLayer.ToolResult.RequiresConfirmation
        )
        // Critically: nothing should have been deleted yet — deletion only
        // happens after the user approves via confirmDeleteTasks.
        coVerify(exactly = 0) { taskRepository.deleteTask(any()) }
    }

    @Test
    fun deleteLowPriorityTasks_ignoresHighPriorityTasksEvenWhenIdIsPassed() = runTest {
        coEvery { taskRepository.getTaskById("high") } returns fakeTask("high", priority = 4)

        val result = toolLayer.deleteLowPriorityTasks(listOf("high"))

        assertTrue(result is AIToolLayer.ToolResult.Failure)
        coVerify(exactly = 0) { taskRepository.deleteTask(any()) }
    }

    @Test
    fun confirmDeleteTasks_actuallyDeletesAfterExplicitApproval() = runTest {
        val ids = listOf("t1", "t2")
        ids.forEach { id -> coEvery { taskRepository.getTaskById(id) } returns fakeTask(id) }

        val result = toolLayer.confirmDeleteTasks(ids)

        assertTrue(result is AIToolLayer.ToolResult.Success)
        coVerify(exactly = 2) { taskRepository.deleteTask(any()) }
    }

    @Test
    fun rescheduleUnfinishedTasks_appliesImmediatelyAtOrBelowThreshold() = runTest {
        val ids = (1..AIToolLayer.CONFIRMATION_THRESHOLD).map { "t$it" }
        ids.forEach { id -> coEvery { taskRepository.getTaskById(id) } returns fakeTask(id) }

        val result = toolLayer.rescheduleUnfinishedTasks(ids, newDueDateMillis = 12345L)

        assertTrue(result is AIToolLayer.ToolResult.Success)
        coVerify(exactly = ids.size) { taskRepository.updateTask(any()) }
    }

    @Test
    fun rescheduleUnfinishedTasks_requiresConfirmationAboveThreshold() = runTest {
        val ids = (1..AIToolLayer.CONFIRMATION_THRESHOLD + 1).map { "t$it" }
        ids.forEach { id -> coEvery { taskRepository.getTaskById(id) } returns fakeTask(id) }

        val result = toolLayer.rescheduleUnfinishedTasks(ids, newDueDateMillis = 12345L)

        assertTrue(result is AIToolLayer.ToolResult.RequiresConfirmation)
        coVerify(exactly = 0) { taskRepository.updateTask(any()) }
    }

    @Test
    fun rescheduleUnfinishedTasks_rejectsEmptyIdList() = runTest {
        val result = toolLayer.rescheduleUnfinishedTasks(emptyList(), newDueDateMillis = 12345L)

        assertTrue(result is AIToolLayer.ToolResult.Failure)
    }

    @Test
    fun previewDeleteTasks_neverDeletesAnything() = runTest {
        coEvery { taskRepository.getTaskById("t1") } returns fakeTask("t1")

        val result = toolLayer.previewDeleteTasks(listOf("t1"))

        assertTrue(result is AIToolLayer.ToolResult.RequiresConfirmation)
        coVerify(exactly = 0) { taskRepository.deleteTask(any()) }
    }
}
