package com.example.lifeos.ai.tools

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
    private val actionTools: AIToolLayer
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

    /** Function declarations sent to Gemini on every turn (read + action tools, sections 21-22). */
    fun buildFunctionDeclarations(): JSONArray = JSONArray().apply {
        // --- Read tools ---
        put(obj("get_tasks", "همه‌ی کارهای موجود در برنامه را برمی‌گرداند.", JSONObject()))
        put(obj("get_today_tasks", "کارهای امروز را برمی‌گرداند.", JSONObject()))
        put(obj("get_unfinished_tasks", "کارهای ناتمامی که موعدشان گذشته را برمی‌گرداند.", JSONObject()))
        put(obj("get_goals", "همه‌ی اهداف بلندمدت را برمی‌گرداند.", JSONObject()))
        put(obj("get_projects", "همه‌ی پروژه‌ها را برمی‌گرداند.", JSONObject()))
        put(obj("get_habits", "همه‌ی عادت‌ها را برمی‌گرداند.", JSONObject()))
        put(obj("get_routines", "همه‌ی روتین‌های ذخیره‌شده را برمی‌گرداند.", JSONObject()))
        put(obj("get_productivity_history", "خلاصه‌ی وضعیت بهره‌وری چند روز اخیر را برمی‌گرداند.",
            JSONObject().put("daysBack", prop("INTEGER", "تعداد روزهای گذشته برای بررسی، پیش‌فرض ۷"))))

        // --- Action tools ---
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
                put("taskTitles", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING")).put("description", "لیست عناوین کارهای روتین"))
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
        // High-impact bulk actions — dispatch() routes these to a
        // confirmation step instead of applying immediately.
        put(obj(
            "delete_low_priority_tasks", "چند کار کم‌اهمیت را حذف می‌کند (نیازمند تأیید کاربر برای تعداد زیاد).",
            JSONObject().put("taskIds", JSONObject().put("type", "ARRAY").put("items", JSONObject().put("type", "STRING")).put("description", "شناسه‌ی کارهایی که باید حذف شوند")),
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
            "create_reminder" -> fromToolResult(name, actionTools.createReminder(
                taskId = args.getString("taskId"),
                triggerTimeMillis = args.getLong("triggerTimeMillis"),
                message = args.optStringOrNull("message")
            ))
            "create_routine" -> {
                val titles = args.optJSONArray("taskTitles")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
                fromToolResult(name, actionTools.createRoutine(args.getString("name"), titles))
            }
            "create_goal" -> fromToolResult(name, actionTools.createGoal(args.getString("title"), args.optStringOrNull("description")))
            "create_project" -> fromToolResult(name, actionTools.createProject(args.getString("name"), args.optStringOrNull("goalId")))
            "delete_low_priority_tasks" -> {
                val ids = args.optJSONArray("taskIds")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
                when (val result = actionTools.deleteLowPriorityTasks(ids)) {
                    is AIToolLayer.ToolResult.RequiresConfirmation -> DispatchResult(
                        functionName = name,
                        responseJson = JSONObject().put("status", "awaiting_confirmation").put("description", result.description),
                        requiresConfirmation = true,
                        pendingConfirmation = PendingConfirmation(
                            description = result.description,
                            kind = PendingConfirmation.Kind.DELETE_TASKS,
                            taskIds = result.affectedTaskIds
                        )
                    )
                    else -> fromToolResult(name, result)
                }
            }
            else -> DispatchResult(name, JSONObject().put("status", "error").put("message", "ابزار ناشناخته: $name"))
        }
    }

    suspend fun applyConfirmation(confirmation: PendingConfirmation): AIToolLayer.ToolResult = when (confirmation.kind) {
        PendingConfirmation.Kind.DELETE_TASKS -> actionTools.confirmDeleteTasks(confirmation.taskIds)
        PendingConfirmation.Kind.MOVE_TASKS -> actionTools.confirmMoveTasks(confirmation.taskIds, confirmation.newDueDateMillis ?: System.currentTimeMillis())
    }

    private fun ok(name: String, data: Any): DispatchResult =
        DispatchResult(name, JSONObject().put("status", "ok").put("data", data.toString()))

    private fun fromToolResult(name: String, result: AIToolLayer.ToolResult): DispatchResult = when (result) {
        is AIToolLayer.ToolResult.Success -> DispatchResult(name, JSONObject().put("status", "ok").put("message", result.message))
        is AIToolLayer.ToolResult.Failure -> DispatchResult(name, JSONObject().put("status", "error").put("message", result.reason))
        is AIToolLayer.ToolResult.RequiresConfirmation -> DispatchResult(
            functionName = name,
            responseJson = JSONObject().put("status", "awaiting_confirmation").put("description", result.description),
            requiresConfirmation = true,
            pendingConfirmation = PendingConfirmation(result.description, PendingConfirmation.Kind.DELETE_TASKS, result.affectedTaskIds)
        )
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
