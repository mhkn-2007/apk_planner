package com.example.lifeos.ai.tools

import com.example.lifeos.data.database.dao.AIActionDao
import com.example.lifeos.data.database.entities.AIActionEntity
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.HabitEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.TaskEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the boundary the model actually talks to (prompt section 21/22):
 * [AIToolCatalog.dispatch] taking a raw function name + JSON args (exactly
 * the shape Gemini's function-calling API sends) and routing it to the
 * correct [AIReadTools]/[AIToolLayer] method with correctly-parsed
 * arguments. [AIToolLayerTest] already covers validation *inside*
 * AIToolLayer; this suite instead covers the dispatch/parsing/authorization
 * layer in front of it — the part the prompt calls out separately in
 * section 57 ("AI action parsing", "AI tool authorization") and which had
 * no test coverage of its own before.
 */
class AIToolCatalogTest {

    private lateinit var readTools: AIReadTools
    private lateinit var actionTools: AIToolLayer
    private lateinit var aiActionDao: AIActionDao
    private lateinit var catalog: AIToolCatalog

    @Before
    fun setUp() {
        readTools = mockk(relaxed = true)
        actionTools = mockk(relaxed = true)
        aiActionDao = mockk(relaxed = true)
        catalog = AIToolCatalog(readTools, actionTools, aiActionDao)
    }

    private fun args(json: String) = JSONObject(json)

    // --- Unknown tool name: must fail cleanly, never throw ---

    @Test
    fun dispatch_unknownToolName_returnsErrorStatusWithoutThrowing() = runTest {
        val result = catalog.dispatch("delete_entire_database", JSONObject())

        assertEquals("error", result.responseJson.getString("status"))
        assertFalse(result.requiresConfirmation)
    }

    // --- Read tools: correct routing, and reads are never audit-logged as actions ---

    @Test
    fun dispatch_getTodayTasks_callsReadToolsAndReturnsOkStatus() = runTest {
        coEvery { readTools.getTodayTasks() } returns listOf(TaskEntity(title = "خرید نان"))

        val result = catalog.dispatch("get_today_tasks", JSONObject())

        assertEquals("ok", result.responseJson.getString("status"))
        assertTrue(result.responseJson.getString("data").contains("خرید نان"))
        coVerify(exactly = 1) { readTools.getTodayTasks() }
    }

    @Test
    fun dispatch_readTool_neverWritesAnAIActionAuditRow() = runTest {
        // Only *mutating* tools should show up in the ai_actions audit log
        // (per AIToolCatalog's own doc comment: "Every call through here
        // is... an action tool"). A read like get_goals must not pollute
        // that log.
        catalog.dispatch("get_goals", JSONObject())

        coVerify(exactly = 0) { aiActionDao.insertAction(any()) }
    }

    @Test
    fun dispatch_getProductivityHistory_parsesDaysBackArgument() = runTest {
        coEvery { readTools.getProductivityHistory(14) } returns
            AIReadTools.ProductivitySummary(totalTasks = 10, completedTasks = 7, postponedTasks = 3)

        catalog.dispatch("get_productivity_history", args("""{"daysBack": 14}"""))

        coVerify(exactly = 1) { readTools.getProductivityHistory(14) }
    }

    @Test
    fun dispatch_getProductivityHistory_missingArgument_fallsBackToDefault() = runTest {
        coEvery { readTools.getProductivityHistory(7) } returns
            AIReadTools.ProductivitySummary(totalTasks = 0, completedTasks = 0, postponedTasks = 0)

        // The model is allowed to omit optional arguments entirely -- this
        // must not throw a JSONException, it must fall back to the
        // documented default of 7 days.
        catalog.dispatch("get_productivity_history", JSONObject())

        coVerify(exactly = 1) { readTools.getProductivityHistory(7) }
    }

    // --- Action tools: argument parsing correctness ---

    @Test
    fun dispatch_createTask_parsesAllFieldsIncludingOptionalOnes() = runTest {
        coEvery { actionTools.createTask(any(), any(), any(), any()) } returns
            AIToolLayer.ToolResult.Success("ساخته شد")

        catalog.dispatch(
            "create_task",
            args("""{"title":"مطالعه ریاضی","dueDateMillis":123456,"priority":3,"estimatedDurationMinutes":45}""")
        )

        coVerify(exactly = 1) {
            actionTools.createTask(
                title = "مطالعه ریاضی",
                dueDateMillis = 123456L,
                estimatedDurationMinutes = 45,
                priority = 3
            )
        }
    }

    @Test
    fun dispatch_createTask_missingOptionalFields_passesNulls() = runTest {
        coEvery { actionTools.createTask(any(), any(), any(), any()) } returns
            AIToolLayer.ToolResult.Success("ساخته شد")

        // The model is allowed to call create_task with only the required
        // "title" field -- the optional fields must come through as null,
        // not throw and not silently default to some garbage sentinel.
        catalog.dispatch("create_task", args("""{"title":"یادداشت سریع"}"""))

        coVerify(exactly = 1) {
            actionTools.createTask(
                title = "یادداشت سریع",
                dueDateMillis = null,
                estimatedDurationMinutes = null,
                priority = 0
            )
        }
    }

    @Test
    fun dispatch_createTask_missingRequiredTitle_throwsRatherThanSilentlyCreatingBlankTask() = runTest {
        // "title" is a required field in the tool declaration -- if the
        // model somehow omits it, this must fail loudly (JSONException)
        // rather than let a blank/garbage task slip through to the
        // repository. (AIToolLayer.createTask also separately rejects
        // blank titles, but that's a second line of defense -- this test
        // is about the dispatch layer's own contract.)
        try {
            catalog.dispatch("create_task", JSONObject())
            org.junit.Assert.fail("expected a JSONException for missing required 'title'")
        } catch (e: org.json.JSONException) {
            // expected
        }
        coVerify(exactly = 0) { actionTools.createTask(any(), any(), any(), any()) }
    }

    @Test
    fun dispatch_breakDownGoal_parsesArrayArgumentsAndMapsTaskTitlesToBreakdownTasks() = runTest {
        val slot = mutableListOf<List<AIToolLayer.BreakdownTask>>()
        coEvery {
            actionTools.breakDownGoal(any(), any(), any(), any(), capture(slot))
        } returns AIToolLayer.ToolResult.Success("انجام شد")

        catalog.dispatch(
            "break_down_goal",
            args(
                """{"goalTitle":"آماده شدن برای آزمون","milestoneTitles":["مرور فصل ۱","مرور فصل ۲"],"taskTitles":["خواندن فصل ۱","حل تمرین"]}"""
            )
        )

        coVerify(exactly = 1) {
            actionTools.breakDownGoal(
                goalTitle = "آماده شدن برای آزمون",
                goalDescription = null,
                projectName = null,
                milestoneTitles = listOf("مرور فصل ۱", "مرور فصل ۲"),
                tasks = any()
            )
        }
        assertEquals(listOf("خواندن فصل ۱", "حل تمرین"), slot.first().map { it.title })
    }

    @Test
    fun dispatch_breakDownGoal_noArraysProvided_passesEmptyListsNotNull() = runTest {
        coEvery { actionTools.breakDownGoal(any(), any(), any(), any(), any()) } returns
            AIToolLayer.ToolResult.Success("انجام شد")

        catalog.dispatch("break_down_goal", args("""{"goalTitle":"فقط یک هدف کلی"}"""))

        coVerify(exactly = 1) {
            actionTools.breakDownGoal(
                goalTitle = "فقط یک هدف کلی",
                goalDescription = null,
                projectName = null,
                milestoneTitles = emptyList(),
                tasks = emptyList()
            )
        }
    }

    // --- Confirmation-kind routing (this exact bug existed before and was fixed) ---

    @Test
    fun dispatch_deleteLowPriorityTasks_requiresConfirmation_tagsItAsDeleteKind() = runTest {
        coEvery { actionTools.deleteLowPriorityTasks(any()) } returns
            AIToolLayer.ToolResult.RequiresConfirmation("۱۰ کار کم‌اهمیت حذف شوند؟", listOf("t1", "t2"))

        val result = catalog.dispatch("delete_low_priority_tasks", args("""{"taskIds":["t1","t2"]}"""))

        assertTrue(result.requiresConfirmation)
        assertEquals(AIToolCatalog.PendingConfirmation.Kind.DELETE_TASKS, result.pendingConfirmation?.kind)
    }

    @Test
    fun dispatch_rescheduleUnfinishedTasks_requiresConfirmation_tagsItAsMoveKindNotDelete() = runTest {
        // Regression test for a real bug that existed in this codebase:
        // every RequiresConfirmation result was hardcoded to DELETE_TASKS
        // regardless of which operation produced it, so confirming a
        // reschedule would have actually run a delete instead.
        coEvery { actionTools.rescheduleUnfinishedTasks(any(), any()) } returns
            AIToolLayer.ToolResult.RequiresConfirmation("۱۰ کار جابجا شوند؟", listOf("t1", "t2"))

        val result = catalog.dispatch(
            "reschedule_unfinished_tasks",
            args("""{"taskIds":["t1","t2"],"newDueDateMillis":999999}""")
        )

        assertTrue(result.requiresConfirmation)
        assertEquals(AIToolCatalog.PendingConfirmation.Kind.MOVE_TASKS, result.pendingConfirmation?.kind)
        assertEquals(999999L, result.pendingConfirmation?.newDueDateMillis)
    }

    @Test
    fun applyConfirmation_deleteKind_callsConfirmDeleteTasksNotConfirmMoveTasks() = runTest {
        val pending = AIToolCatalog.PendingConfirmation(
            description = "حذف شود؟",
            kind = AIToolCatalog.PendingConfirmation.Kind.DELETE_TASKS,
            taskIds = listOf("t1")
        )
        coEvery { actionTools.confirmDeleteTasks(listOf("t1")) } returns AIToolLayer.ToolResult.Success("حذف شد")

        catalog.applyConfirmation(pending)

        coVerify(exactly = 1) { actionTools.confirmDeleteTasks(listOf("t1")) }
        coVerify(exactly = 0) { actionTools.confirmMoveTasks(any(), any()) }
    }

    @Test
    fun applyConfirmation_moveKind_callsConfirmMoveTasksWithCorrectDueDate() = runTest {
        val pending = AIToolCatalog.PendingConfirmation(
            description = "جابجا شود؟",
            kind = AIToolCatalog.PendingConfirmation.Kind.MOVE_TASKS,
            taskIds = listOf("t1", "t2"),
            newDueDateMillis = 555555L
        )
        coEvery { actionTools.confirmMoveTasks(listOf("t1", "t2"), 555555L) } returns AIToolLayer.ToolResult.Success("جابجا شد")

        catalog.applyConfirmation(pending)

        coVerify(exactly = 1) { actionTools.confirmMoveTasks(listOf("t1", "t2"), 555555L) }
        coVerify(exactly = 0) { actionTools.confirmDeleteTasks(any()) }
    }

    // --- Audit logging (prompt section 44: AIAction entity) ---

    @Test
    fun dispatch_successfulAction_writesAuditRowMarkedSuccessful() = runTest {
        coEvery { actionTools.completeTask("t1") } returns AIToolLayer.ToolResult.Success("تکمیل شد")
        val slot = mutableListOf<AIActionEntity>()
        coEvery { aiActionDao.insertAction(capture(slot)) } returns Unit

        catalog.dispatch("complete_task", args("""{"taskId":"t1"}"""))

        assertEquals(1, slot.size)
        assertTrue(slot.first().wasSuccessful)
        assertFalse(slot.first().requiredConfirmation)
        assertEquals("complete_task", slot.first().toolName)
    }

    @Test
    fun dispatch_failedAction_writesAuditRowMarkedNotSuccessful() = runTest {
        coEvery { actionTools.completeTask("missing") } returns AIToolLayer.ToolResult.Failure("پیدا نشد")
        val slot = mutableListOf<AIActionEntity>()
        coEvery { aiActionDao.insertAction(capture(slot)) } returns Unit

        catalog.dispatch("complete_task", args("""{"taskId":"missing"}"""))

        assertEquals(1, slot.size)
        assertFalse(slot.first().wasSuccessful)
    }

    @Test
    fun dispatch_actionRequiringConfirmation_writesAuditRowFlaggedAsRequiredConfirmation() = runTest {
        coEvery { actionTools.deleteLowPriorityTasks(any()) } returns
            AIToolLayer.ToolResult.RequiresConfirmation("مطمئنی؟", listOf("t1"))
        val slot = mutableListOf<AIActionEntity>()
        coEvery { aiActionDao.insertAction(capture(slot)) } returns Unit

        catalog.dispatch("delete_low_priority_tasks", args("""{"taskIds":["t1"]}"""))

        assertEquals(1, slot.size)
        assertTrue(slot.first().requiredConfirmation)
        // A confirmation *request* is not itself a completed mutation.
        assertFalse(slot.first().wasSuccessful)
    }

    @Test
    fun applyConfirmation_alsoWritesItsOwnAuditRow() = runTest {
        val pending = AIToolCatalog.PendingConfirmation(
            description = "حذف شود؟",
            kind = AIToolCatalog.PendingConfirmation.Kind.DELETE_TASKS,
            taskIds = listOf("t1")
        )
        coEvery { actionTools.confirmDeleteTasks(listOf("t1")) } returns AIToolLayer.ToolResult.Success("حذف شد")
        val slot = mutableListOf<AIActionEntity>()
        coEvery { aiActionDao.insertAction(capture(slot)) } returns Unit

        catalog.applyConfirmation(pending)

        assertEquals(1, slot.size)
        assertTrue(slot.first().toolName.startsWith("confirm_"))
        assertTrue(slot.first().wasSuccessful)
    }

    // --- Function declaration schema sanity ---

    @Test
    fun buildFunctionDeclarations_everyDeclaredToolHasNonBlankNameAndDescription() {
        val declarations = catalog.buildFunctionDeclarations()

        assertTrue("expected at least one declared tool", declarations.length() > 0)
        for (i in 0 until declarations.length()) {
            val tool = declarations.getJSONObject(i)
            assertTrue(tool.getString("name").isNotBlank())
            assertTrue(tool.getString("description").isNotBlank())
        }
    }

    @Test
    fun buildFunctionDeclarations_requiredFieldsReferenceActualDeclaredProperties() {
        // Catches a copy-paste-prone class of bug: a "required" array
        // listing a parameter name that was never actually added to
        // "properties", which Gemini's API would likely reject outright.
        val declarations = catalog.buildFunctionDeclarations()

        for (i in 0 until declarations.length()) {
            val tool = declarations.getJSONObject(i)
            val params = tool.getJSONObject("parameters")
            val properties = params.getJSONObject("properties")
            val required = params.optJSONArray("required") ?: JSONArray()
            for (j in 0 until required.length()) {
                val requiredName = required.getString(j)
                assertTrue(
                    "tool '${tool.getString("name")}' requires '$requiredName' but never declares it as a property",
                    properties.has(requiredName)
                )
            }
        }
    }

    @Test
    fun buildFunctionDeclarations_containsAllPlanningAndReviewTools() {
        // A direct check that the newer sections-23/24/26/27/30 tools are
        // actually declared to the model, not just implemented in dispatch()
        // and silently unreachable because nothing tells Gemini they exist.
        val declarations = catalog.buildFunctionDeclarations()
        val names = (0 until declarations.length())
            .map { declarations.getJSONObject(it).getString("name") }
            .toSet()

        assertTrue(names.contains("get_daily_planning_context"))
        assertTrue(names.contains("get_weekly_planning_context"))
        assertTrue(names.contains("get_daily_review"))
        assertTrue(names.contains("break_down_goal"))
        assertTrue(names.contains("reschedule_unfinished_tasks"))
    }
}
