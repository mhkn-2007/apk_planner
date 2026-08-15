package com.example.lifeos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifeos.ai.provider.GeminiProvider
import com.example.lifeos.ai.tools.AIToolCatalog
import com.example.lifeos.ai.tools.AIToolLayer
import com.example.lifeos.ui.components.glassCard
import com.example.lifeos.ui.theme.*
import com.example.lifeos.util.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean
)

/**
 * A high-impact action the AI proposed and is waiting on the user to
 * approve or cancel before it actually applies (prompt section 35).
 */
data class PendingAction(
    val description: String,
    val confirmation: AIToolCatalog.PendingConfirmation
)

@HiltViewModel
class AIChatViewModel @Inject constructor(
    private val geminiProvider: GeminiProvider,
    private val toolCatalog: AIToolCatalog,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _pendingAction = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pendingAction.asStateFlow()

    // Gemini's "contents" history for this conversation: (role, text) pairs.
    // Kept small/relevant rather than growing forever (prompt section 60:
    // don't send more than needed).
    private val history = mutableListOf<Pair<String, String>>()

    init {
        _messages.value = listOf(
            ChatMessage(
                text = "سلام! من دستیار هوشمند LifeOS هستم. می‌تونم کارها، اهداف، پروژه‌ها و روتین‌های شما رو مدیریت کنم. اگر هنوز کلید API را در تنظیمات وارد نکرده‌اید، این بخش در حالت آفلاین باقی می‌ماند و بقیه‌ی برنامه بدون تغییر کار می‌کند.",
                isUser = false
            )
        )
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(text = text, isUser = true)
        _messages.value = _messages.value + userMsg
        history.add("user" to text)

        _isTyping.value = true

        viewModelScope.launch {
            try {
                val apiKey = preferencesManager.apiKey.first()
                runConversationTurn(apiKey)
            } catch (e: GeminiProvider.AIProviderException) {
                // AI failures must never break the rest of the app (prompt
                // section 39) — show a clear message and let the user keep
                // using LifeOS manually.
                appendAssistantMessage(e.message ?: "خطایی در ارتباط با هوش مصنوعی رخ داد.")
            } catch (e: Exception) {
                appendAssistantMessage("خطای غیرمنتظره‌ای رخ داد. لطفاً دوباره تلاش کنید یا از بخش‌های دیگر برنامه به‌صورت دستی استفاده کنید.")
            } finally {
                _isTyping.value = false
            }
        }
    }

    private suspend fun runConversationTurn(apiKey: String) {
        val today = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(java.util.Date())
        val systemInstruction = """
            شما دستیار برنامه‌ریزی شخصی LifeOS هستید. فقط از طریق ابزارهای اعلام‌شده به داده‌های کاربر
            دسترسی دارید و هرگز نباید داده یا اقدامی را جعل کنید. زمان فعلی دستگاه: $today.
            برای عملیات‌های پرتاثیر (حذف/جابجایی چند کار)، ابزار مربوطه ممکن است نیاز به تأیید کاربر را
            گزارش کند؛ در این حالت فقط توضیح بده که منتظر تأیید کاربر هستید و از ابزار دیگری برای دور زدن
            آن استفاده نکن. پاسخ‌ها را به فارسی و کوتاه بده.
        """.trimIndent()

        var turn = geminiProvider.sendTurn(
            apiKey = apiKey,
            systemInstruction = systemInstruction,
            history = history,
            functionDeclarations = toolCatalog.buildFunctionDeclarations()
        )

        // Function-calling loop: execute any tool calls, feed results back,
        // repeat until the model responds with plain text (bounded to avoid
        // an infinite loop on a misbehaving response).
        var rounds = 0
        while (turn.functionCalls.isNotEmpty() && rounds < 5) {
            rounds++
            for (call in turn.functionCalls) {
                val dispatch = toolCatalog.dispatch(call.name, call.args)
                if (dispatch.requiresConfirmation && dispatch.pendingConfirmation != null) {
                    _pendingAction.value = PendingAction(
                        description = dispatch.pendingConfirmation.description,
                        confirmation = dispatch.pendingConfirmation
                    )
                }
                history.add("model" to "[${call.name} -> ${dispatch.responseJson}]")
            }
            turn = geminiProvider.sendTurn(
                apiKey = apiKey,
                systemInstruction = systemInstruction,
                history = history,
                functionDeclarations = toolCatalog.buildFunctionDeclarations()
            )
        }

        val finalText = turn.text.ifBlank { "انجام شد." }
        history.add("model" to finalText)
        appendAssistantMessage(finalText)
    }

    /** User tapped "Apply" on a pending high-impact action preview. */
    fun confirmPendingAction() {
        val pending = _pendingAction.value ?: return
        viewModelScope.launch {
            val result = toolCatalog.applyConfirmation(pending.confirmation)
            _pendingAction.value = null
            val message = when (result) {
                is AIToolLayer.ToolResult.Success -> result.message
                is AIToolLayer.ToolResult.Failure -> result.reason
                is AIToolLayer.ToolResult.RequiresConfirmation -> result.description
            }
            appendAssistantMessage(message)
        }
    }

    /** User tapped "Cancel" on a pending high-impact action preview. */
    fun cancelPendingAction() {
        _pendingAction.value = null
        appendAssistantMessage("لغو شد.")
    }

    private fun appendAssistantMessage(text: String) {
        _messages.value = _messages.value + ChatMessage(text = text, isUser = false)
    }
}

@Composable
fun AIChatScreen(viewModel: AIChatViewModel = hiltViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isTyping by viewModel.isTyping.collectAsState()
    val pendingAction by viewModel.pendingAction.collectAsState()
    var inputText by remember { mutableStateOf("") }

    val isDark = LocalIsDarkTheme.current
    val bgGradient = if (isDark) {
        Brush.verticalGradient(colors = listOf(GradientStart, GradientMiddle, GradientEnd))
    } else {
        Brush.verticalGradient(colors = listOf(LightGradientStart, LightGradientMiddle, LightGradientEnd))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Column {
                    Text(
                        text = "دستیار هوشمند",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "من آماده کمک به شما هستم",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    GlassChatBubble(message)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (isTyping) {
                    item {
                        Text(
                            text = "دستیار در حال تایپ است...",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentTeal,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Action preview / confirmation card (prompt section 35: show a
            // preview before applying major changes — "Review Changes" /
            // "Apply Plan" / "Cancel").
            pendingAction?.let { action ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .glassCard(cornerRadius = 16.dp)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "تأیید تغییرات",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            action.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.confirmPendingAction() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                            ) { Text("اعمال کن") }
                            OutlinedButton(onClick = { viewModel.cancelPendingAction() }) {
                                Text("انصراف")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Input Row with high-contrast text styling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .glassCard(cornerRadius = 28.dp)
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "پیام خود را بنویسید...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.scrim.copy(alpha = if (isDark) 0.35f else 0.06f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.scrim.copy(alpha = if (isDark) 0.2f else 0.03f),
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                            cursorColor = AccentBlue
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ارسال",
                            tint = AccentBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GlassChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val isDark = LocalIsDarkTheme.current
    val bubbleSurfaceColor = if (isDark) Color.White else Color.Black

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (message.isUser)
                        Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.3f), AccentBlue.copy(alpha = 0.15f)))
                    else
                        Brush.linearGradient(
                            listOf(
                                bubbleSurfaceColor.copy(alpha = if (isDark) 0.12f else 0.06f),
                                bubbleSurfaceColor.copy(alpha = if (isDark) 0.06f else 0.03f)
                            )
                        )
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.text,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
