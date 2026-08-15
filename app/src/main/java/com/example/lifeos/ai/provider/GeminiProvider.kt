package com.example.lifeos.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single function/tool call the model asked for, to be executed through
 * [com.example.lifeos.ai.tools.AIToolLayer] — never directly against the
 * database (prompt section 21/22: AI never gets raw DB access).
 */
data class AIFunctionCall(
    val name: String,
    val args: JSONObject
)

/** Structured result of one AI turn: free text plus any tool calls the model requested. */
data class AITurnResult(
    val text: String,
    val functionCalls: List<AIFunctionCall> = emptyList()
)

/**
 * Real AI provider backed by Google's Gemini API (prompt section 36:
 * AIProvider abstraction, with GeminiProvider as one concrete
 * implementation). Talks to the REST API directly over OkHttp so the app
 * doesn't need a server-side component — the user supplies their own API key
 * in Settings (prompt sections 37-39: AI is optional, and for now the key is
 * user-supplied rather than embedded in the APK or proxied through a
 * backend LifeOS doesn't have yet).
 *
 * Every network/parsing failure is caught and turned into a
 * [AIProviderException] rather than propagating a raw exception, so a failed
 * AI call can never crash the app (prompt section 39: AI failures must never
 * cause the core application to fail).
 */
@Singleton
class GeminiProvider @Inject constructor() : AIProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val MODEL = "gemini-2.0-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    /** Thrown for any AI failure — missing key, network, rate limit, malformed response, etc. */
    class AIProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Sends one turn to Gemini with the given system context and the app's
     * function declarations, returning free text plus any requested tool
     * calls. This is the entry point AIChatViewModel actually uses; the
     * simpler [sendMessage]/[streamChat]/[parseAction] below satisfy the
     * shared [AIProvider] interface for callers that don't need tool use.
     */
    suspend fun sendTurn(
        apiKey: String,
        systemInstruction: String,
        history: List<Pair<String, String>>, // (role: "user"|"model", text)
        functionDeclarations: JSONArray
    ): AITurnResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw AIProviderException("کلید API تنظیم نشده است.")
        }

        val requestBody = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
            })
            put("contents", JSONArray().apply {
                history.forEach { (role, text) ->
                    put(JSONObject().apply {
                        put("role", role)
                        put("parts", JSONArray().put(JSONObject().put("text", text)))
                    })
                }
            })
            if (functionDeclarations.length() > 0) {
                put("tools", JSONArray().put(JSONObject().apply {
                    put("function_declarations", functionDeclarations)
                }))
            }
        }

        val request = Request.Builder()
            .url("$BASE_URL/$MODEL:generateContent?key=$apiKey")
            .post(requestBody.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = parseErrorMessage(bodyStr) ?: "کد خطا: ${response.code}"
                    throw AIProviderException("درخواست به سرویس هوش مصنوعی ناموفق بود ($reason).")
                }
                parseTurnResult(bodyStr)
            }
        } catch (e: AIProviderException) {
            throw e
        } catch (e: IOException) {
            throw AIProviderException("اتصال به اینترنت برقرار نشد. لطفاً اتصال خود را بررسی کنید.", e)
        } catch (e: Exception) {
            throw AIProviderException("پاسخ سرویس هوش مصنوعی قابل پردازش نبود.", e)
        }
    }

    private fun parseErrorMessage(bodyStr: String): String? = try {
        JSONObject(bodyStr).optJSONObject("error")?.optString("message")
    } catch (e: Exception) {
        null
    }

    private fun parseTurnResult(bodyStr: String): AITurnResult {
        val root = JSONObject(bodyStr)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            // e.g. blocked by safety filters
            val blockReason = root.optJSONObject("promptFeedback")?.optString("blockReason")
            return AITurnResult(
                text = if (blockReason != null) "این درخواست توسط فیلترهای ایمنی رد شد." else "پاسخی دریافت نشد."
            )
        }
        val content = candidates.getJSONObject(0).optJSONObject("content")
        val parts = content?.optJSONArray("parts") ?: JSONArray()

        val textBuilder = StringBuilder()
        val calls = mutableListOf<AIFunctionCall>()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("text")) {
                textBuilder.append(part.getString("text"))
            }
            part.optJSONObject("functionCall")?.let { fc ->
                calls.add(AIFunctionCall(name = fc.getString("name"), args = fc.optJSONObject("args") ?: JSONObject()))
            }
        }
        return AITurnResult(text = textBuilder.toString(), functionCalls = calls)
    }

    // ---------------------------------------------------------------
    // AIProvider interface (kept for provider-abstraction purposes, prompt
    // section 36). The chat screen talks to GeminiProvider directly via
    // [sendTurn] because it needs structured function-calls, which the
    // simple string-based AIProvider methods can't express — implementing
    // fake streaming here would just be another form of "Fake AI" (section
    // 62), so these delegate to the same real call instead of pretending.
    // ---------------------------------------------------------------

    override fun streamChat(message: String, contextData: String): Flow<String> = flow {
        emit(sendMessage(message, contextData))
    }

    override suspend fun sendMessage(message: String, contextData: String): String {
        val result = sendTurn(
            apiKey = lastKnownApiKey ?: throw AIProviderException("کلید API تنظیم نشده است."),
            systemInstruction = contextData,
            history = listOf("user" to message),
            functionDeclarations = JSONArray()
        )
        return result.text
    }

    override suspend fun parseAction(naturalLanguage: String): String {
        throw UnsupportedOperationException(
            "GeminiProvider uses sendTurn() with function declarations instead of a separate parseAction step."
        )
    }

    /**
     * [sendMessage]/[streamChat] exist only to satisfy the shared
     * [AIProvider] interface for callers that don't need tool use; real
     * chat traffic goes through [sendTurn] with an explicit API key. Set
     * before calling those interface methods.
     */
    var lastKnownApiKey: String? = null
}
