package com.example.lifeos.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.lifeos.data.database.entities.ReminderEntity
import com.example.lifeos.data.database.entities.SubtaskEntity
import com.example.lifeos.data.database.entities.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room in-memory database tests covering the prompt section 57 areas that
 * had no coverage at all before this: task CRUD, multiple reminders per
 * task, subtasks, and the foreign-key cascade relationships between them
 * (deleting a task must delete its reminders/subtasks, not orphan them).
 *
 * Runs under Robolectric so Room's real SQLite-backed generated DAO code
 * executes inside a plain JVM `test` source set -- these are genuine
 * database operations, not simulated/mocked ones.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class TaskDatabaseTests {

    private lateinit var db: LifeOSDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, LifeOSDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun task(title: String, dueDateMillis: Long? = null) =
        TaskEntity(title = title, dueDateMillis = dueDateMillis)

    // --- Task CRUD (prompt section 7) ---

    @Test
    fun insertAndRetrieveTask() = runBlocking {
        val t = task("خرید نان")
        db.taskDao().insertTask(t)

        val retrieved = db.taskDao().getTaskById(t.id)

        assertEquals(t.title, retrieved?.title)
        assertEquals(t.id, retrieved?.id)
    }

    @Test
    fun updateTask_changesArePersisted() = runBlocking {
        val t = task("پیش‌نویس اولیه")
        db.taskDao().insertTask(t)

        val updated = t.copy(title = "عنوان نهایی", priority = 3)
        db.taskDao().updateTask(updated)

        val retrieved = db.taskDao().getTaskById(t.id)
        assertEquals("عنوان نهایی", retrieved?.title)
        assertEquals(3, retrieved?.priority)
    }

    @Test
    fun completeTask_setsCompletedFlag() = runBlocking {
        val t = task("تسک تکمیل‌نشده")
        db.taskDao().insertTask(t)

        db.taskDao().updateTask(t.copy(isCompleted = true, completedAtMillis = 1000L))

        val retrieved = db.taskDao().getTaskById(t.id)
        assertTrue(retrieved!!.isCompleted)
        assertEquals(1000L, retrieved.completedAtMillis)
    }

    @Test
    fun deleteTask_removesItFromDatabase() = runBlocking {
        val t = task("تسک موقت")
        db.taskDao().insertTask(t)

        db.taskDao().deleteTask(t)

        assertNull(db.taskDao().getTaskById(t.id))
    }

    @Test
    fun insertTasks_batchInsertsMultipleAtOnce() = runBlocking {
        val tasks = listOf(task("اول"), task("دوم"), task("سوم"))
        db.taskDao().insertTasks(tasks)

        val all = db.taskDao().getAllTasks().first()
        assertEquals(3, all.size)
    }

    // --- Multiple reminders per task (prompt section 10) ---

    @Test
    fun taskCanHaveMultipleIndependentReminders() = runBlocking {
        val t = task("مطالعه ریاضی")
        db.taskDao().insertTask(t)

        db.reminderDao().insertReminder(ReminderEntity(taskId = t.id, triggerTimeMillis = 1000L, message = "آماده شو"))
        db.reminderDao().insertReminder(ReminderEntity(taskId = t.id, triggerTimeMillis = 2000L, message = "شروع کن"))
        db.reminderDao().insertReminder(ReminderEntity(taskId = t.id, triggerTimeMillis = 3000L, message = "تمام کن"))

        val reminders = db.reminderDao().getRemindersForTaskOnce(t.id)
        assertEquals(3, reminders.size)
        // Ordered by trigger time ascending, per the DAO's query.
        assertEquals(listOf(1000L, 2000L, 3000L), reminders.map { it.triggerTimeMillis })
    }

    @Test
    fun disablingOneReminderDoesNotAffectOthersOnSameTask() = runBlocking {
        val t = task("جلسه کاری")
        db.taskDao().insertTask(t)
        val r1 = ReminderEntity(taskId = t.id, triggerTimeMillis = 1000L)
        val r2 = ReminderEntity(taskId = t.id, triggerTimeMillis = 2000L)
        db.reminderDao().insertReminder(r1)
        db.reminderDao().insertReminder(r2)

        db.reminderDao().setEnabled(r1.id, false)

        val reminders = db.reminderDao().getRemindersForTaskOnce(t.id)
        assertEquals(false, reminders.first { it.id == r1.id }.isEnabled)
        assertEquals(true, reminders.first { it.id == r2.id }.isEnabled)
    }

    @Test
    fun deleteReminder_removesOnlyThatReminder() = runBlocking {
        val t = task("چند یادآوری")
        db.taskDao().insertTask(t)
        val r1 = ReminderEntity(taskId = t.id, triggerTimeMillis = 1000L)
        val r2 = ReminderEntity(taskId = t.id, triggerTimeMillis = 2000L)
        db.reminderDao().insertReminder(r1)
        db.reminderDao().insertReminder(r2)

        db.reminderDao().deleteReminder(r1)

        val remaining = db.reminderDao().getRemindersForTaskOnce(t.id)
        assertEquals(1, remaining.size)
        assertEquals(r2.id, remaining[0].id)
    }

    // --- Subtasks (prompt section 9) ---

    @Test
    fun taskCanHaveMultipleSubtasksIndependentlyCompleted() = runBlocking {
        val t = task("آماده‌سازی ارائه")
        db.taskDao().insertTask(t)
        val s1 = SubtaskEntity(taskId = t.id, title = "تحقیق", position = 0)
        val s2 = SubtaskEntity(taskId = t.id, title = "طراحی اسلایدها", position = 1)
        db.subtaskDao().insertSubtask(s1)
        db.subtaskDao().insertSubtask(s2)

        db.subtaskDao().updateSubtask(s1.copy(isCompleted = true))

        val subtasks = db.subtaskDao().getSubtasksForTaskOnce(t.id)
        assertTrue(subtasks.first { it.id == s1.id }.isCompleted)
        assertEquals(false, subtasks.first { it.id == s2.id }.isCompleted)
    }

    // --- Database relationships / foreign key cascade (prompt section 44) ---

    @Test
    fun deletingTask_cascadeDeletesItsReminders() = runBlocking {
        val t = task("تسک با یادآوری")
        db.taskDao().insertTask(t)
        db.reminderDao().insertReminder(ReminderEntity(taskId = t.id, triggerTimeMillis = 1000L))
        db.reminderDao().insertReminder(ReminderEntity(taskId = t.id, triggerTimeMillis = 2000L))
        assertEquals(2, db.reminderDao().getRemindersForTaskOnce(t.id).size)

        db.taskDao().deleteTask(t)

        // The FK is declared with onDelete = CASCADE (see ReminderEntity) --
        // if that annotation were ever removed or a raw DELETE bypassed it,
        // this would start failing, catching a real regression.
        assertEquals(0, db.reminderDao().getRemindersForTaskOnce(t.id).size)
    }

    @Test
    fun deletingTask_cascadeDeletesItsSubtasks() = runBlocking {
        val t = task("تسک با زیرکار")
        db.taskDao().insertTask(t)
        db.subtaskDao().insertSubtask(SubtaskEntity(taskId = t.id, title = "زیرکار ۱"))
        db.subtaskDao().insertSubtask(SubtaskEntity(taskId = t.id, title = "زیرکار ۲"))
        assertEquals(2, db.subtaskDao().getSubtasksForTaskOnce(t.id).size)

        db.taskDao().deleteTask(t)

        assertEquals(0, db.subtaskDao().getSubtasksForTaskOnce(t.id).size)
    }

    @Test
    fun deletingOneTask_doesNotAffectAnotherTasksReminders() = runBlocking {
        val t1 = task("تسک اول")
        val t2 = task("تسک دوم")
        db.taskDao().insertTasks(listOf(t1, t2))
        db.reminderDao().insertReminder(ReminderEntity(taskId = t1.id, triggerTimeMillis = 1000L))
        db.reminderDao().insertReminder(ReminderEntity(taskId = t2.id, triggerTimeMillis = 2000L))

        db.taskDao().deleteTask(t1)

        assertEquals(0, db.reminderDao().getRemindersForTaskOnce(t1.id).size)
        assertEquals(1, db.reminderDao().getRemindersForTaskOnce(t2.id).size)
    }
}
