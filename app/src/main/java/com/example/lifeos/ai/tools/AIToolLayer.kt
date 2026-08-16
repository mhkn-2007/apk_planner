package com.example.lifeos.ai.tools

import com.example.lifeos.data.database.dao.GoalDao
import com.example.lifeos.data.database.dao.MilestoneDao
import com.example.lifeos.data.database.dao.ProjectDao
import com.example.lifeos.data.database.dao.RoutineDao
import com.example.lifeos.data.database.entities.GoalEntity
import com.example.lifeos.data.database.entities.GoalMilestoneEntity
import com.example.lifeos.data.database.entities.ProjectEntity
import com.example.lifeos.data.database.entities.ProjectMilestoneEntity
import com.example.lifeos.data.database.entities.ReminderEntity
import com.example.lifeos.data.database.entities.RoutineTemplateEntity
import com.example.lifeos.data.database.entities.RoutineTemplateTaskEntity
import com.example.lifeos.data.database.entities.SubtaskEntity
import com.example.lifeos.data.database.entities.TaskEntity
import com.example.lifeos.domain.repositories.TaskRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ONLY way the AI is allowed to change application state (prompt
 * section 22 / 35). Every mutating call goes:
 *
 *   AI -> AIToolLayer (this class) -> validation -> TaskRepository/DAO -> Room
 *
 * The AI never gets a raw repository or DAO reference — AIProvider
 * implementations only see the tool declarations exposed here.
 *
 * High-impact operations (deleting/moving many tasks) return
 * [ToolResult.RequiresConfirmation] instead of executing immediately; the
 * caller (AIChatViewModel) shows a preview and only calls the corresponding
 * `confirm*` method after the user approves it (prompt section 35: "show a
 * preview before applying major changes").
 */
@Singleton
class AIToolLayer @Inject constructor(
    private val taskRepository: TaskRepository,
    private val goalDao: GoalDao,
    private val projectDao: ProjectDao,
    private val routineDao: RoutineDao,
    private val milestoneDao: MilestoneDao
) {
    companion object {
        /** Above this many affected tasks, an action requires explicit confirmation. */
        const val CONFIRMATION_THRESHOLD = 5
    }

    sealed class ToolResult {
        data class Success(val message: String) : ToolResult()
        data class Failure(val reason: String) : ToolResult()
        data class RequiresConfirmation(val description: String, val affectedTaskIds: List<String>) : ToolResult()
    }

    // ---------------------------------------------------------------
    // Tasks
    // ---------------------------------------------------------------

    suspend fun createTask(
        title: String,
        dueDateMillis: Long? = null,
        startTimeMillis: Long? = null,
        estimatedDurationMinutes: Int? = null,
        priority: Int = 0,
        goalId: String? = null,
        projectId: String? = null
    ): ToolResult {
        if (title.isBlank()) return ToolResult.Failure("عنوان کار نمی‌تواند خالی باشد.")
        val safePriority = priority.coerceIn(0, 4) // never let the AI blindly set out-of-range/always-critical priority
        val task = TaskEntity(
            title = title.trim(),
            dueDateMillis = dueDateMillis,
            startTimeMillis = startTimeMillis,
            estimatedDurationMinutes = estimatedDurationMinutes,
            priority = safePriority,
            goalId = goalId,
            projectId = projectId
        )
        taskRepository.insertTask(task)
        return ToolResult.Success("کار «${task.title}» ایجاد شد.")
    }

    suspend fun updateTask(
        taskId: String,
        title: String? = null,
        priority: Int? = null,
        dueDateMillis: Long? = null
    ): ToolResult {
        val task = taskRepository.getTaskById(taskId) ?: return ToolResult.Failure("کاری با این شناسه پیدا نشد.")
        val updated = task.copy(
            title = title?.takeIf { it.isNotBlank() } ?: task.title,
            priority = priority?.coerceIn(0, 4) ?: task.priority,
            dueDateMillis = dueDateMillis ?: task.dueDateMillis
        )
        taskRepository.updateTask(updated)
        return ToolResult.Success("کار «${updated.title}» به‌روزرسانی شد.")
    }

    suspend fun completeTask(taskId: String): ToolResult {
        val task = taskRepository.getTaskById(taskId) ?: return ToolResult.Failure("کاری با این شناسه پیدا نشد.")
        taskRepository.updateTask(task.copy(isCompleted = true))
        return ToolResult.Success("کار «${task.title}» تکمیل شد.")
    }

    suspend fun deleteTask(taskId: String): ToolResult {
        val task = taskRepository.getTaskById(taskId) ?: return ToolResult.Failure("کاری با این شناسه پیدا نشد.")
        taskRepository.deleteTask(task)
        return ToolResult.Success("کار «${task.title}» حذف شد.")
    }

    suspend fun scheduleTask(taskId: String, startTimeMillis: Long, endTimeMillis: Long?): ToolResult {
        val task = taskRepository.getTaskById(taskId) ?: return ToolResult.Failure("کاری با این شناسه پیدا نشد.")
        taskRepository.updateTask(task.copy(startTimeMillis = startTimeMillis, endTimeMillis = endTimeMillis))
        return ToolResult.Success("کار «${task.title}» زمان‌بندی شد.")
    }

    suspend fun rescheduleTask(taskId: String, newDueDateMillis: Long): ToolResult {
        val task = taskRepository.getTaskById(taskId) ?: return ToolResult.Failure("کاری با این شناسه پیدا نشد.")
        taskRepository.updateTask(task.copy(dueDateMillis = newDueDateMillis))
        return ToolResult.Success("کار «${task.title}» به تاریخ جدید منتقل شد.")
    }

    suspend fun createSubtask(taskId: String, title: String): ToolResult {
        if (title.isBlank()) return ToolResult.Failure("عنوان زیرکار نمی‌تواند خالی باشد.")
        taskRepository.getTaskById(taskId) ?: return ToolResult.Failure("کاری با این شناسه پیدا نشد.")
        taskRepository.insertSubtask(SubtaskEntity(taskId = taskId, title = title.trim()))
        return ToolResult.Success("زیرکار «$title» اضافه شد.")
    }

    // ---------------------------------------------------------------
    // Reminders
    // ---------------------------------------------------------------

    suspend fun createReminder(taskId: String, triggerTimeMillis: Long, message: String? = null): ToolResult {
        taskRepository.getTaskById(taskId) ?: return ToolResult.Failure("کاری با این شناسه پیدا نشد.")
        taskRepository.insertReminder(ReminderEntity(taskId = taskId, triggerTimeMillis = triggerTimeMillis, message = message))
        return ToolResult.Success("یادآوری اضافه شد.")
    }

    suspend fun updateReminder(reminderId: String, triggerTimeMillis: Long? = null, isEnabled: Boolean? = null): ToolResult {
        val reminder = taskRepository.getReminderById(reminderId) ?: return ToolResult.Failure("یادآوری با این شناسه پیدا نشد.")
        val updated = reminder.copy(
            triggerTimeMillis = triggerTimeMillis ?: reminder.triggerTimeMillis,
            isEnabled = isEnabled ?: reminder.isEnabled
        )
        taskRepository.updateReminder(updated)
        return ToolResult.Success("یادآوری به‌روزرسانی شد.")
    }

    suspend fun deleteReminder(reminderId: String): ToolResult {
        val reminder = taskRepository.getReminderById(reminderId) ?: return ToolResult.Failure("یادآوری با این شناسه پیدا نشد.")
        taskRepository.deleteReminder(reminder)
        return ToolResult.Success("یادآوری حذف شد.")
    }

    // ---------------------------------------------------------------
    // Routines
    // ---------------------------------------------------------------

    suspend fun createRoutine(name: String, taskTitles: List<String>): ToolResult {
        if (name.isBlank()) return ToolResult.Failure("نام روتین نمی‌تواند خالی باشد.")
        if (taskTitles.isEmpty()) return ToolResult.Failure("روتین باید حداقل یک کار داشته باشد.")
        val template = RoutineTemplateEntity(name = name.trim())
        routineDao.insertTemplate(template)
        val tasks = taskTitles.mapIndexed { index, t ->
            RoutineTemplateTaskEntity(templateId = template.id, title = t, position = index)
        }
        routineDao.insertTemplateTasks(tasks)
        return ToolResult.Success("روتین «$name» با ${tasks.size} کار ساخته شد.")
    }

    suspend fun updateRoutine(templateId: String, name: String): ToolResult {
        val template = routineDao.getTemplateById(templateId) ?: return ToolResult.Failure("روتینی با این شناسه پیدا نشد.")
        if (name.isBlank()) return ToolResult.Failure("نام روتین نمی‌تواند خالی باشد.")
        routineDao.updateTemplate(template.copy(name = name.trim()))
        return ToolResult.Success("روتین به‌روزرسانی شد.")
    }

    // ---------------------------------------------------------------
    // Goals / Projects
    // ---------------------------------------------------------------

    suspend fun createGoal(title: String, description: String? = null): ToolResult {
        if (title.isBlank()) return ToolResult.Failure("عنوان هدف نمی‌تواند خالی باشد.")
        goalDao.insertGoal(GoalEntity(title = title.trim(), description = description))
        return ToolResult.Success("هدف «$title» ایجاد شد.")
    }

    suspend fun createProject(name: String, goalId: String? = null, description: String? = null): ToolResult {
        if (name.isBlank()) return ToolResult.Failure("نام پروژه نمی‌تواند خالی باشد.")
        projectDao.insertProject(ProjectEntity(name = name.trim(), goalId = goalId, description = description))
        return ToolResult.Success("پروژه «$name» ایجاد شد.")
    }

    // ---------------------------------------------------------------
    // Task breakdown: intention -> Goal -> Project -> Milestones -> Tasks
    // (prompt section 26). This is the single tool that performs the whole
    // chain in one controlled, atomic-ish operation instead of the model
    // having to issue create_goal/create_project/... separately and risk a
    // half-finished structure if one step fails or the model gets confused.
    // ---------------------------------------------------------------

    /** One task to create as part of a goal/project breakdown. */
    data class BreakdownTask(
        val title: String,
        val estimatedDurationMinutes: Int? = null,
        val dueDateMillis: Long? = null,
        val priority: Int = 2
    )

    /**
     * Creates a Goal, an associated Project, a set of Project Milestones, and
     * a set of Tasks linked to that project — turning a stated intention
     * ("می‌خوام برای آزمون آماده بشم") into the structured chain the prompt
     * describes: Goal -> Project -> Milestones -> Tasks -> Schedule.
     *
     * All parts happen even if the model only supplies partial data:
     * milestones and tasks are optional (a bare goal+project is still a
     * valid, useful structure the user can build on manually afterward).
     */
    suspend fun breakDownGoal(
        goalTitle: String,
        goalDescription: String? = null,
        projectName: String? = null,
        milestoneTitles: List<String> = emptyList(),
        tasks: List<BreakdownTask> = emptyList()
    ): ToolResult {
        if (goalTitle.isBlank()) return ToolResult.Failure("عنوان هدف نمی‌تواند خالی باشد.")

        val goal = GoalEntity(title = goalTitle.trim(), description = goalDescription)
        goalDao.insertGoal(goal)

        val project = ProjectEntity(
            name = (projectName ?: goalTitle).trim(),
            goalId = goal.id,
            description = goalDescription
        )
        projectDao.insertProject(project)

        milestoneTitles.forEachIndexed { index, title ->
            if (title.isNotBlank()) {
                milestoneDao.insertProjectMilestone(
                    ProjectMilestoneEntity(projectId = project.id, title = title.trim(), position = index)
                )
            }
        }

        var createdTaskCount = 0
        for (t in tasks) {
            if (t.title.isBlank()) continue
            taskRepository.insertTask(
                TaskEntity(
                    title = t.title.trim(),
                    estimatedDurationMinutes = t.estimatedDurationMinutes,
                    dueDateMillis = t.dueDateMillis,
                    priority = t.priority.coerceIn(0, 4),
                    goalId = goal.id,
                    projectId = project.id
                )
            )
            createdTaskCount++
        }

        return ToolResult.Success(
            "هدف «${goal.title}» با پروژه «${project.name}»، " +
                "${milestoneTitles.count { it.isNotBlank() }} نقطه‌عطف و $createdTaskCount کار ایجاد شد."
        )
    }

    // ---------------------------------------------------------------
    // High-impact / bulk operations — require confirmation (section 35)
    // ---------------------------------------------------------------

    /**
     * Step 1: describe what WOULD happen without doing it. The chat layer
     * shows this to the user as a preview ("Review Changes" / "Apply Plan" /
     * "Cancel").
     */
    suspend fun previewDeleteTasks(taskIds: List<String>): ToolResult {
        val tasks = taskIds.mapNotNull { taskRepository.getTaskById(it) }
        if (tasks.isEmpty()) return ToolResult.Failure("کاری برای حذف پیدا نشد.")
        return ToolResult.RequiresConfirmation(
            description = "حذف ${tasks.size} کار: ${tasks.joinToString("، ") { it.title }}",
            affectedTaskIds = tasks.map { it.id }
        )
    }

    /** Step 2: only called after the user taps "Apply". */
    suspend fun confirmDeleteTasks(taskIds: List<String>): ToolResult {
        var deleted = 0
        for (id in taskIds) {
            val task = taskRepository.getTaskById(id) ?: continue
            taskRepository.deleteTask(task)
            deleted++
        }
        return ToolResult.Success("$deleted کار حذف شد.")
    }

    suspend fun previewMoveTasks(taskIds: List<String>, newDueDateMillis: Long): ToolResult {
        val tasks = taskIds.mapNotNull { taskRepository.getTaskById(it) }
        if (tasks.isEmpty()) return ToolResult.Failure("کاری برای جابجایی پیدا نشد.")
        return ToolResult.RequiresConfirmation(
            description = "انتقال ${tasks.size} کار به تاریخ جدید: ${tasks.joinToString("، ") { it.title }}",
            affectedTaskIds = tasks.map { it.id }
        )
    }

    suspend fun confirmMoveTasks(taskIds: List<String>, newDueDateMillis: Long): ToolResult {
        var moved = 0
        for (id in taskIds) {
            val task = taskRepository.getTaskById(id) ?: continue
            taskRepository.updateTask(task.copy(dueDateMillis = newDueDateMillis))
            moved++
        }
        return ToolResult.Success("$moved کار جابجا شد.")
    }

    /**
     * Convenience entry point for AI-driven rescheduling (prompt section 27:
     * "امروز نتونستم کارهام رو انجام بدم، برای فردا برنامه‌ریزی کن"). Moves a
     * set of unfinished tasks to a new due date. Goes through the same
     * preview/confirm bulk-move mechanism as [previewMoveTasks] so a large
     * batch still requires the user's explicit approval (prompt section 35).
     */
    suspend fun rescheduleUnfinishedTasks(taskIds: List<String>, newDueDateMillis: Long): ToolResult {
        if (taskIds.isEmpty()) return ToolResult.Failure("کاری برای جابجایی مشخص نشده است.")
        if (taskIds.size <= CONFIRMATION_THRESHOLD) {
            return confirmMoveTasks(taskIds, newDueDateMillis)
        }
        return previewMoveTasks(taskIds, newDueDateMillis)
    }

    /**
     * Deletes tasks that are both low-priority (priority < 2, i.e. None/Low)
     * AND in [taskIds]. Small batches (<= [CONFIRMATION_THRESHOLD]) apply
     * immediately; larger ones go through the preview/confirm flow instead
     * of silently applying — a bulk deletion is exactly the kind of
     * high-impact action prompt section 35 asks to gate behind confirmation.
     */
    suspend fun deleteLowPriorityTasks(taskIds: List<String>): ToolResult {
        val eligible = taskIds.mapNotNull { taskRepository.getTaskById(it) }.filter { it.priority < 2 }
        if (eligible.isEmpty()) return ToolResult.Failure("هیچ کار کم‌اهمیتی برای حذف پیدا نشد.")
        if (eligible.size > CONFIRMATION_THRESHOLD) {
            return ToolResult.RequiresConfirmation(
                description = "حذف ${eligible.size} کار کم‌اهمیت: ${eligible.joinToString("، ") { it.title }}",
                affectedTaskIds = eligible.map { it.id }
            )
        }
        for (task in eligible) {
            taskRepository.deleteTask(task)
        }
        return ToolResult.Success("${eligible.size} کار کم‌اهمیت حذف شد.")
    }
}
