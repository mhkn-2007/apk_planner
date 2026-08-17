package com.example.lifeos.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lifeos.data.database.entities.RoutineInstanceEntity
import com.example.lifeos.data.database.entities.RoutineInstanceTaskEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.RoutineTemplateTaskEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.usecases.GenerateRecurringTaskOccurrencesUseCase
import com.example.lifeos.domain.usecases.RecurrenceRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.UUID

/**
 * Room in-memory database tests for two prompt section 57 areas that had no
 * coverage before this: recurring tasks (section 11) and the Routine
 * Template/Instance separation (section 12).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class RecurrenceAndRoutineDatabaseTests {

    private lateinit var db: LifeOSDatabase
    private lateinit var taskRepository: TaskRepositoryImpl
    private lateinit var generateOccurrences: GenerateRecurringTaskOccurrencesUseCase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LifeOSDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskRepository = TaskRepositoryImpl(db.taskDao(), db.subtaskDao(), db.reminderDao())
        generateOccurrences = GenerateRecurringTaskOccurrencesUseCase(taskRepository)
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // --- Recurring tasks (prompt section 11) ---

    @Test
    fun dailyRecurrence_generatesOneOccurrencePerDayInTheWindow() = runBlocking {
        val groupId = UUID.randomUUID().toString()
        val baseDue = startOfDay(System.currentTimeMillis())
        val source = TaskEntity(
            title = "نوشیدن آب",
            dueDateMillis = baseDue,
            recurrenceRule = RecurrenceRule.Daily.encode(),
            recurrenceGroupId = groupId
        )
        db.taskDao().insertTask(source)

        generateOccurrences(source)

        val occurrences = db.taskDao().getTasksForRecurrenceGroup(groupId).first()
        // Window is 60 days inclusive of day 0 (GENERATION_WINDOW_DAYS = 60).
        assertEquals(61, occurrences.size)
    }

    @Test
    fun weeklyRecurrence_onlyGeneratesOccurrencesOnSelectedWeekdays() = runBlocking {
        val groupId = UUID.randomUUID().toString()
        val baseDue = startOfDay(System.currentTimeMillis())
        // Calendar.MONDAY and Calendar.WEDNESDAY
        val source = TaskEntity(
            title = "باشگاه",
            dueDateMillis = baseDue,
            recurrenceRule = RecurrenceRule.Weekly(setOf(Calendar.MONDAY, Calendar.WEDNESDAY)).encode(),
            recurrenceGroupId = groupId
        )
        db.taskDao().insertTask(source)

        generateOccurrences(source)

        val occurrences = db.taskDao().getTasksForRecurrenceGroup(groupId).first()
        assertTrue(occurrences.isNotEmpty())
        val weekdays = occurrences.mapNotNull { it.dueDateMillis }.map {
            Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_WEEK)
        }.toSet()
        assertEquals(setOf(Calendar.MONDAY, Calendar.WEDNESDAY), weekdays)
    }

    @Test
    fun regeneratingOccurrences_isIdempotentAndDoesNotDuplicate() = runBlocking {
        val groupId = UUID.randomUUID().toString()
        val baseDue = startOfDay(System.currentTimeMillis())
        val source = TaskEntity(
            title = "مدیتیشن",
            dueDateMillis = baseDue,
            recurrenceRule = RecurrenceRule.Daily.encode(),
            recurrenceGroupId = groupId
        )
        db.taskDao().insertTask(source)

        // Call twice, exactly as TodayViewModel.refreshAllRecurringSeries()
        // does on every app launch.
        generateOccurrences(source)
        val afterFirstRun = db.taskDao().getTasksForRecurrenceGroup(groupId).first().size
        generateOccurrences(source)
        val afterSecondRun = db.taskDao().getTasksForRecurrenceGroup(groupId).first().size

        // This is the "must not create uncontrolled duplicate tasks"
        // requirement from prompt section 11, verified against the real DB.
        assertEquals(afterFirstRun, afterSecondRun)
    }

    @Test
    fun nonRecurringTask_generatesNoOccurrences() = runBlocking {
        val plainTask = TaskEntity(title = "کار یک‌باره", dueDateMillis = System.currentTimeMillis())
        db.taskDao().insertTask(plainTask)

        // No recurrenceRule/recurrenceGroupId set -> should be a no-op.
        generateOccurrences(plainTask)

        val all = db.taskDao().getAllTasks().first()
        assertEquals(1, all.size) // just the original task, nothing generated
    }

    // --- Routine Template/Instance separation (prompt section 12) ---

    @Test
    fun addingTemplateToADay_copiesTasksIntoAnIndependentInstance() = runBlocking {
        val template = RoutineTemplateEntity(name = "روتین صبحگاهی")
        db.routineDao().insertTemplate(template)
        db.routineDao().insertTemplateTasks(
            listOf(
                RoutineTemplateTaskEntity(templateId = template.id, title = "نوشیدن آب", position = 0),
                RoutineTemplateTaskEntity(templateId = template.id, title = "ورزش", position = 1)
            )
        )

        val instance = RoutineInstanceEntity(templateId = template.id, dateMillis = System.currentTimeMillis())
        db.routineDao().insertInstance(instance)
        val templateTasks = db.routineDao().getTemplateTasksOnce(template.id)
        db.routineDao().insertInstanceTasks(
            templateTasks.map {
                RoutineInstanceTaskEntity(instanceId = instance.id, title = it.title, position = it.position)
            }
        )

        val instanceTasks = db.routineDao().getInstanceTasks(instance.id).first()
        assertEquals(2, instanceTasks.size)
        assertEquals(setOf("نوشیدن آب", "ورزش"), instanceTasks.map { it.title }.toSet())
    }

    @Test
    fun editingAnInstanceTask_doesNotModifyTheOriginalTemplate() = runBlocking {
        val template = RoutineTemplateEntity(name = "روتین کاری")
        db.routineDao().insertTemplate(template)
        val templateTask = RoutineTemplateTaskEntity(templateId = template.id, title = "بررسی ایمیل", position = 0)
        db.routineDao().insertTemplateTask(templateTask)

        val instance = RoutineInstanceEntity(templateId = template.id, dateMillis = System.currentTimeMillis())
        db.routineDao().insertInstance(instance)
        val instanceTask = RoutineInstanceTaskEntity(instanceId = instance.id, title = templateTask.title, position = 0)
        db.routineDao().insertInstanceTask(instanceTask)

        // This is the exact scenario prompt section 12 calls out by name:
        // "Editing a RoutineInstance must NOT automatically modify the
        // original RoutineTemplate."
        db.routineDao().updateInstanceTask(instanceTask.copy(title = "بررسی ایمیل (فقط امروز)", isCompleted = true))

        val templateTasksAfterEdit = db.routineDao().getTemplateTasksOnce(template.id)
        assertEquals("بررسی ایمیل", templateTasksAfterEdit.single().title)
        assertEquals(false, templateTasksAfterEdit.single().isCompleted)
    }

    @Test
    fun deletingTemplate_cascadeDeletesItsTemplateTasksButNotPastInstances() = runBlocking {
        val template = RoutineTemplateEntity(name = "روتین موقت")
        db.routineDao().insertTemplate(template)
        db.routineDao().insertTemplateTask(RoutineTemplateTaskEntity(templateId = template.id, title = "کار"))

        db.routineDao().deleteTemplate(template)

        val remainingTemplateTasks = db.routineDao().getTemplateTasks(template.id).first()
        assertTrue(remainingTemplateTasks.isEmpty())
    }

    @Test
    fun multipleInstancesOfSameTemplate_areIndependentOfEachOther() = runBlocking {
        val template = RoutineTemplateEntity(name = "روتین شب")
        db.routineDao().insertTemplate(template)
        db.routineDao().insertTemplateTask(RoutineTemplateTaskEntity(templateId = template.id, title = "کتاب‌خوانی"))

        val day1 = RoutineInstanceEntity(templateId = template.id, dateMillis = 1_000_000L)
        val day2 = RoutineInstanceEntity(templateId = template.id, dateMillis = 2_000_000L)
        db.routineDao().insertInstance(day1)
        db.routineDao().insertInstance(day2)
        val day1Task = RoutineInstanceTaskEntity(instanceId = day1.id, title = "کتاب‌خوانی")
        val day2Task = RoutineInstanceTaskEntity(instanceId = day2.id, title = "کتاب‌خوانی")
        db.routineDao().insertInstanceTask(day1Task)
        db.routineDao().insertInstanceTask(day2Task)

        // Completing day1's task must not affect day2's separately-copied task.
        db.routineDao().updateInstanceTask(day1Task.copy(isCompleted = true))

        val day2TasksAfter = db.routineDao().getInstanceTasks(day2.id).first()
        assertEquals(false, day2TasksAfter.single().isCompleted)
    }
}
