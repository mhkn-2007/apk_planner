package com.example.lifeos.ai.tools

import com.example.lifeos.data.database.dao.AIActionDao
import com.example.lifeos.data.database.entities.AIActionEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Declares the AI's available tools in the schema Gemini's function-calling
 * API expects, and dispatches a model-requested call into [AIReadTools] /
 * [AIToolLayer]. This is the boundary between "the model decided to call
 * get_today_tasks" and the actual controlled tool layer (prompt section
 * 21/22) — the model never sees more than these declared names/parameters.
 */
@Singleton
class AIToolCatalog @Inject constructor(
    private val readTools: AIReadTools,
    private val actionTools: AIToolLayer,
    private val aiActionDao: AIActionDao
) {
    /** One result of dispatching a model function-call. */
    data class DispatchResult(
        val functionName: String,
        val responseJson: JSONObject,
        val requiresConfirmation: Boolean = false,
        val pendingConfirmation: PendingConfirmation? = null
    )

    /** A high-impact action awaiting explicit user approval (prompt section 35). */
    data class PendingConfirmation(
        val description: String,
        val kind: Kind,
        val taskIds: List<String>,
        val newDueDateMillis: Long? = null
    ) {
        enum class Kind { DELETE_TASKS, MOVE_TASKS }
    }

    private fun obj(name: String, description: String, properties: JSONObject, required: List<String> = emptyList()): JSONObject =
        JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "OBJECT")
                put("properties", properties)
                if (required.isNotEmpty()) put("required", JSONArray(required))
            })
        }

    private fun prop(type: String, description: String): JSONObject =
        JSONObject().put("type", type).put("description", description)

    private fun arrayProp(itemType: String, description: String): JSONObject =
        JSONObject()
            .put("type", "ARRAY")
            .put("items", JSONObject().put("type", itemType))
            .put("description", description)

    /** Function declarations sent to Gemini on every turn (read + action tools, sections 21-22). */
    fun buildFunctionDeclarations(): JSONArray = JSONArray().apply {
        // --- Read tools (section 21) ---
        put(obj("get_tasks", "همه‌ی کارهای موجود در برنامه را برمی‌گرداند.", JSONObject()))
        put(obj("get_today_tasks", "کارهای امروز را برمی‌گرداند.", JSONObject()))
        put(obj("get_unfinished_tasks", "کارهای ناتمامی که موعدشان گذشته را برمی‌گرداند.", JSONObject()))
        put(obj("get_goals", "همه‌ی اهداف بلندمدت را برمی‌گرداند.", JSONObject()))
        put(obj("get_projects", "همه‌ی پروژه‌ها را برمی‌گرداند.", JSONObject()))
        put(obj("get_habits", "همه‌ی عادت‌ها را برمی‌گرداند.", JSONObject()))
        put(obj("get_routines", "همه‌ی روتین‌های ذخیره‌شده را برمی‌گرداند.", JSONObject()))
        put(obj("get_productivity_history", "خلاصه‌ی وضعیت بهره‌وری چند روز اخیر را برمی‌گرداند.",
            JSONObject().put("daysBack", prop("INTEGER", "تعداد روزهای گذشته برای بررسی، پیش‌فرض ۷"))))

        // --- Daily / weekly planning context (sections 23, 24) ---
        put(obj(
            "get_daily_planning_context",
            "برای برنامه‌ریزی فردا: کارهای ناتمام، کارهای فردا، حجم کاری کل، تداخل‌های زمانی، و پیشنهاد کارهایی که باید عقب بیفتند را برمی‌گرداند. قبل از پیشنهاد برنامه‌ی فردا حتماً این ابزار را صدا بزن.",
            JSONObject().put("availableTimeMinutes", prop("INTEGER", "زمان در دسترس فردا به دقیقه، پیش‌فرض ۴۸۰ (۸ ساعت)"))
        ))
        put(obj(
            "get_weekly_planning_context",
            "برای برنامه‌ریزی هفته: کارهای هفته‌ی پیش‌رو، کارهای ناتمام، حجم کاری کل، و پیشنهاد کارهایی که باید عقب بیفتند را برمی‌گرداند. قبل از پیشنهاد برنامه‌ی هفتگی حتماً این ابزار را صدا بزن.",
            JSONObject().put("availableTimeMinutesPerDay", prop("INTEGER", "زمان در دسترس روزانه به دقیقه، پیش‌فرض ۴۸۰"))
        ))

        // --- Daily review (section 30) ---
        put(obj(
            "get_daily_review",
            "برای مرور روز: کارهای تکمیل‌شده و عقب‌افتاده، مجموع زمان فوکوس، و تعداد عادت‌های انجام‌شده در یک روز مشخص را برمی‌گرداند.",
            JSONObject().put("offsetDays", prop("INTEGER", "افست روز نسبت به امروز؛ ۰ برای امروز، منفی‌یک برای دیروز، پیش‌فرض ۰"))
        ))

        // --- Action tools (section 22) ---
        put(obj(
            "create_task", "یک کار جدید ایجاد می‌کند.",
            JSONObject().apply {
                put("title", prop("STRING", "عنوان کار"))
                put("dueDateMillis", prop("NUMBER", "زمان سررسید به میلی‌ثانیه (epoch)، اختیاری"))
                put("priority", prop("INTEGER", "اولویت: ۰ هیچ، ۱ کم، ۲ متوسط، ۳ زیاد، ۴ بحرانی"))
                put("estimatedDurationMinutes", prop("INTEGER", "مدت زمان تخمینی به دقیقه، اختیاری"))
            },
            required = listOf("title")
        ))
        put(obj(
            "complete_task", "یک کار را تکمیل‌شده علامت می‌زند.",
            JSONObject().put("taskId", prop("STRING", "شناسه‌ی کار")),
            required = listOf("taskId")
        ))
        put(obj(
            "reschedule_task", "موعد یک کار را تغییر می‌دهد.",
            JSONObject().apply {
                put("taskId", prop("STRING", "شناسه‌ی کار"))
                put("newDueDateMillis", prop("NUMBER", "موعد جدید به میلی‌ثانیه"))
            },
            required = listOf("taskId", "newDueDateMillis")
        ))
        put(obj(
            "reschedule_unfinished_tasks",
            "چند کار ناتمام را یکجا به تاریخ جدید منتقل می‌کند (برای «امروز نتونستم کارهام رو انجام بدم، برای فردا برنامه‌ریزی کن»). اگر تعداد زیاد باشد، پاسخ نیازمند تأیید کاربر خواهد بود.",
            JSONObject().apply {
                put("taskIds", arrayProp("STRING", "شناسه‌ی کارهایی که باید منتقل شوند"))
                put("newDueDateMillis", prop("NUMBER", "موعد جدید به میلی‌ثانیه"))
            },
            required = listOf("taskIds", "newDueDateMillis")
        ))
        put(obj(
            "create_reminder", "یک یادآوری برای یک کار اضافه می‌کند.",
            JSONObject().apply {
                put("taskId", prop("STRING", "شناسه‌ی کار"))
                put("triggerTimeMillis", prop("NUMBER", "زمان یادآوری به میلی‌ثانیه"))
                put("message", prop("STRING", "متن یادآوری، اختیاری"))
            },
            required = listOf("taskId", "triggerTimeMillis")
        ))
        put(obj(
            "create_routine", "یک روتین جدید با چند کار می‌سازد.",
            JSONObject().apply {
                put("name", prop("STRING", "نام روتین"))
                put("taskTitles", arrayProp("STRING", "لیست عناوین کارهای روتین"))
            },
            required = listOf("name", "taskTitles")
        ))
        put(obj(
            "create_goal", "یک هدف بلندمدت جدید می‌سازد.",
            JSONObject().apply {
                put("title", prop("STRING", "عنوان هدف"))
                put("description", prop("STRING", "توضیحات، اختیاری"))
            },
            required = listOf("title")
        ))
        put(obj(
            "create_project", "یک پروژه جدید می‌سازد.",
            JSONObject().apply {
                put("name", prop("STRING", "نام پروژه"))
                put("goalId", prop("STRING", "شناسه‌ی هدف مرتبط، اختیاری"))
            },
            required = listOf("name")
        ))
        put(obj(
            "break_down_goal",
            "یک قصد یا هدف کاربر (مثلاً «می‌خوام برای آزمون آماده بشم») را به یک هدف، یک پروژه، چند نقطه‌عطف، و چند کار مشخص تبدیل می‌کند. اگر اطلاعات کافی برای ساختن کارهای دقیق نداری، همین ابزار را فقط با milestoneTitles یا حتی بدون tasks صدا بزن و بعد از کاربر جزئیات بیشتر بپرس.",
            JSONObject().apply {
                put("goalTitle", prop("STRING", "عنوان هدف بلندمدت"))
                put("goalDescription", prop("STRING", "توضیح هدف، اختیاری"))
                put("projectName", prop("STRING", "نام پروژه، اختیاری (پیش‌فرض همان عنوان هدف)"))
                put("milestoneTitles", arrayProp("STRING", "عناوین نقاط عطف پروژه، اختیاری"))
                put("taskTitles", arrayProp("STRING", "عناوین کارهایی که باید ساخته شوند، اختیاری"))
            },
            required = listOf("goalTitle")
        ))
        // High-impact bulk actions — dispatch() routes these to a
        // confirmation step instead of applying immediately.
        put(obj(
            "delete_low_priority_tasks", "چند کار کم‌اهمیت را حذف می‌کند (نیازمند تأیید کاربر برای تعداد زیاد).",
            JSONObject().put("taskIds", arrayProp("STRING", "شناسه‌ی کارهایی که باید حذف شوند")),
            required = listOf("taskIds")
        ))
    }

    /**
     * Executes one model-requested function call. Returns a JSON object
     * meant to be sent back to the model as the function's result, plus a
     * [PendingConfirmation] if the action needs the user's explicit
     * approval before it actually applies.
     */
    suspend fun dispatch(name: String, args: JSONObject): DispatchResult {
        return when (name) {
            "get_tasks" -> ok(name, readTools.getTasks().map { it.title })
            "get_today_tasks" -> ok(name, readTools.getTodayTasks().map { it.title })
            "get_unfinished_tasks" -> ok(name, readTools.getUnfinishedTasks().map { it.title })
            "get_goals" -> ok(name, readTools.getGoals().map { it.title })
            "get_projects" -> ok(name, readTools.getProjects().map { it.name })
            "get_habits" -> ok(name, readTools.getHabits().map { it.name })
            "get_routines" -> ok(name, readTools.getRoutines().map { it.name })
            "get_productivity_history" -> {
                val summary = readTools.getProductivityHistory(args.optInt("daysBack", 7))
                ok(name, mapOf(
                    "totalTasks" to summary.totalTasks,
                    "completedTasks" to summary.completedTasks,
                    "postponedTasks" to summary.postponedTasks
                ))
            }
            "get_daily_planning_context" -> {
                val ctx = readTools.getDailyPlanningContext(args.optInt("availableTimeMinutes", 8 * 60))
                ok(name, mapOf(
                    "unfinishedTasks" to ctx.unfinishedTasks.map { it.title },
                    "tomorrowTasks" to ctx.tomorrowTasks.map { it.title },
                    "prioritizedOrder" to ctx.prioritizedTasks.map { it.title },
                    "totalWorkloadMinutes" to ctx.totalWorkloadMinutes,
                    "availableTimeMinutes" to ctx.availableTimeMinutes,
                    "conflicts" to ctx.conflictingPairs.map { "${it.first.title} <-> ${it.second.title}" },
                    "fitsInAvailableTime" to ctx.fitsInAvailableTime.map { it.title },
                    "suggestedPostponements" to ctx.suggestedPostponements.map { it.title }
                ))
            }
            "get_weekly_planning_context" -> {
                val ctx = readTools.getWeeklyPlanningContext(args.optInt("availableTimeMinutesPerDay", 8 * 60))
                ok(name, mapOf(
                    "weekTasks" to ctx.weekTasks.map { it.title },
                    "unfinishedTasks" to ctx.unfinishedTasks.map { it.title },
                    "prioritizedOrder" to ctx.prioritizedTasks.map { it.title },
                    "totalWorkloadMinutes" to ctx.totalWorkloadMinutes,
                    "availableTimeMinutesForWeek" to ctx.availableTimeMinutesForWeek,
                    "fitsInAvailableTime" to ctx.fitsInAvailableTime.map { it.title },
                    "suggestedPostponements" to ctx.suggestedPostponements.map { it.title }
                ))
            }
            "get_daily_review" -> {
                val review = readTools.getDailyReview(args.optInt("offsetDays", 0))
                ok(name, mapOf(
                    "completedTasks" to review.completedTasks.map { it.title },
                    "postponedTasks" to review.postponedTasks.map { it.title },
                    "focusMinutesSpent" to review.focusSecondsSpent / 60,
                    "completedFocusSessions" to review.completedFocusSessions,
                    "totalHabits" to review.totalHabits,
                    "completedHabitCount" to review.completedHabitCount
                ))
            }
            "create_task" -> fromToolResult(name, actionTools.createTask(
                title = args.getString("title"),
                dueDateMillis = args.optLongOrNull("dueDateMillis"),
                estimatedDurationMinutes = args.optIntOrNull("estimatedDurationMinutes"),
                priority = args.optInt("priority", 0)
            ))
            "complete_task" -> fromToolResult(name, actionTools.completeTask(args.getString("taskId")))
            "reschedule_task" -> fromToolResult(name, actionTools.rescheduleTask(
                taskId = args.getString("taskId"),
                newDueDateMillis = args.getLong("newDueDateMillis")
            ))
            "reschedule_unfinished_tasks" -> {
                val ids = args.stringList("taskIds")
                val newDue = args.getLong("newDueDateMillis")
                fromToolResult(name, actionTools.rescheduleUnfinishedTasks(ids, newDue), kind = PendingConfirmation.Kind.MOVE_TASKS, newDueDateMillis = newDue)
            }
            "create_reminder" -> fromToolResult(name, actionTools.createReminder(
                taskId = args.getString("taskId"),
                triggerTimeMillis = args.getLong("triggerTimeMillis"),
                message = args.optStringOrNull("message")
            ))
            "create_routine" -> {
                val titles = args.stringList("taskTitles")
                fromToolResult(name, actionTools.createRoutine(args.getString("name"), titles))
            }
            "create_goal" -> fromToolResult(name, actionTools.createGoal(args.getString("title"), args.optStringOrNull("description")))
            "create_project" -> fromToolResult(name, actionTools.createProject(args.getString("name"), args.optStringOrNull("goalId")))
            "break_down_goal" -> {
                val milestoneTitles = args.stringList("milestoneTitles")
                val taskTitles = args.stringList("taskTitles")
                fromToolResult(name, actionTools.breakDownGoal(
                    goalTitle = args.getString("goalTitle"),
                    goalDescription = args.optStringOrNull("goalDescription"),
                    projectName = args.optStringOrNull("projectName"),
                    milestoneTitles = milestoneTitles,
                    tasks = taskTitles.map { AIToolLayer.BreakdownTask(title = it) }
                ))
            }
            "delete_low_priority_tasks" -> {
                val ids = args.stringList("taskIds")
                fromToolResult(name, actionTools.deleteLowPriorityTasks(ids), kind = PendingConfirmation.Kind.DELETE_TASKS)
            }
            else -> DispatchResult(name, JSONObject().put("status", "error").put("message", "ابزار ناشناخته: $name"))
        }
    }

    suspend fun applyConfirmation(confirmation: PendingConfirmation): AIToolLayer.ToolResult {
        val result = when (confirmation.kind) {
            PendingConfirmation.Kind.DELETE_TASKS -> actionTools.confirmDeleteTasks(confirmation.taskIds)
            PendingConfirmation.Kind.MOVE_TASKS -> actionTools.confirmMoveTasks(confirmation.taskIds, confirmation.newDueDateMillis ?: System.currentTimeMillis())
        }
        logAction(toolName = "confirm_${confirmation.kind.name.lowercase()}", result = result)
        return result
    }

    private fun ok(name: String, data: Any): DispatchResult =
        DispatchResult(name, JSONObject().put("status", "ok").put("data", data.toString()))

    /**
     * [kind]/[newDueDateMillis] are only used if [result] turns out to be
     * [AIToolLayer.ToolResult.RequiresConfirmation] — they tell
     * [applyConfirmation] which follow-up action to run once the user
     * approves. Defaults to DELETE_TASKS for backward compatibility with
     * callers that only ever produce delete-confirmations.
     *
     * Every call through here is, by construction, an action tool (not a
     * `get_*` read) — so this is also the single chokepoint where we write
     * an [AIActionEntity] audit row (prompt section 44), regardless of
     * which of the dozen action methods on [AIToolLayer] produced [result].
     */
    private suspend fun fromToolResult(
        name: String,
        result: AIToolLayer.ToolResult,
        kind: PendingConfirmation.Kind = PendingConfirmation.Kind.DELETE_TASKS,
        newDueDateMillis: Long? = null
    ): DispatchResult {
        logAction(name, result)
        return when (result) {
            is AIToolLayer.ToolResult.Success -> DispatchResult(name, JSONObject().put("status", "ok").put("message", result.message))
            is AIToolLayer.ToolResult.Failure -> DispatchResult(name, JSONObject().put("status", "error").put("message", result.reason))
            is AIToolLayer.ToolResult.RequiresConfirmation -> DispatchResult(
                functionName = name,
                responseJson = JSONObject().put("status", "awaiting_confirmation").put("description", result.description),
                requiresConfirmation = true,
                pendingConfirmation = PendingConfirmation(result.description, kind, result.affectedTaskIds, newDueDateMillis)
            )
        }
    }

    private suspend fun logAction(toolName: String, result: AIToolLayer.ToolResult) {
        val summary = when (result) {
            is AIToolLayer.ToolResult.Success -> result.message
            is AIToolLayer.ToolResult.Failure -> result.reason
            is AIToolLayer.ToolResult.RequiresConfirmation -> result.description
        }
        aiActionDao.insertAction(
            AIActionEntity(
                toolName = toolName,
                summary = summary,
                wasSuccessful = result is AIToolLayer.ToolResult.Success,
                requiredConfirmation = result is AIToolLayer.ToolResult.RequiresConfirmation
            )
        )
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
private fun JSONObject.stringList(key: String): List<String> =
    optJSONArray(key)?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
