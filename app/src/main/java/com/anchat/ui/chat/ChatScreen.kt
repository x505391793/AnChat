package com.anchat.ui.chat

import android.util.Log
import android.widget.Toast
import com.anchat.ui.theme.avatarColor
import com.anchat.ui.theme.avatarInitial
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.anchat.AnChatApplication
import com.anchat.ui.main.LocalApp
import com.anchat.ui.main.LocalIsDark
import com.anchat.ui.main.Screen
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(navController: NavHostController, convId: Long = -1L, characterId: Long = -1L) {
    val app: AnChatApplication = LocalApp.current
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(
        key = if (convId >= 0) "chat-$convId" else "chat-new-$characterId",
        factory = ChatViewModel.Factory(app, convId, characterId)
    )
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    // 输入框文本用本地 State 作为唯一真相源，避免 IME 组合态（中文拼音）因
    // StateFlow 收集延迟而丢失，导致中文无法上屏。
    var inputText by rememberSaveable { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val thinkingEnabled by viewModel.thinkingEnabled.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val conversationId by viewModel.conversationIdFlow.collectAsStateWithLifecycle()

    // 头像：基于对话级角色名（可被对话内编辑覆盖），回退标题；用于配色与首字（备注不影响）
    val assistantName = profile?.charName?.takeIf { it.isNotBlank() }
        ?: title.takeIf { it.isNotBlank() }
        ?: "AI"

    // 顶部名称：备注优先，其次角色名，其次标题；AI 生成中显示「对方正在输入中……」
    val displayName = profile?.charRemark?.takeIf { it.isNotBlank() }
        ?: profile?.charName?.takeIf { it.isNotBlank() }
        ?: title.takeIf { it.isNotBlank() }
        ?: "新对话"
    val topBarTitle = if (isLoading) "对方正在输入中……" else displayName

    // 跟随用户主题开关（而非系统），使气泡配色随设置切换
    val dark = LocalIsDark.current

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show error via Toast (more reliable than Snackbar)
    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, error!!, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    IconButton(onClick = {
                        Log.d("ChatScreen", "点击了返回")
                        // 优先 pop（从列表/通讯录进来）；若栈空则显式回到聊天列表
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.WeChat.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    ChatMessageItem(
                        msg = msg,
                        dark = dark,
                        assistantName = assistantName,
                        showReasoning = thinkingEnabled,
                        onAvatarClick = { isUser ->
                            val id = conversationId
                            if (id != null) {
                                navController.navigate(
                                    "conversation/$id?part=${if (isUser) "self" else "ai"}"
                                )
                            }
                        }
                    )
                }
            }

            // 输入区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发送消息") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.send(inputText)
                            inputText = ""
                        }
                    }),
                    enabled = !isLoading
                )
                IconButton(
                    onClick = {
                        Log.d("ChatScreen", "点击了发送按钮")
                        if (inputText.isNotBlank()) {
                            viewModel.send(inputText)
                            inputText = ""
                        }
                    },
                    enabled = !isLoading && inputText.isNotBlank()
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    msg: ChatMessage,
    dark: Boolean,
    assistantName: String,
    showReasoning: Boolean = true,
    onAvatarClick: ((isUser: Boolean) -> Unit)? = null
) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    val isAssistant = msg.role == "assistant"

    // 系统提示（如「请先填写 API Key」）：微信式居中灰底胶囊
    if (isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = msg.content,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 思考过程（可折叠），仅 AI 回复且开启思考模式时展示
        if (showReasoning && isAssistant && !msg.reasoningContent.isNullOrBlank()) {
            var expanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { expanded = !expanded }
                    .padding(6.dp)
            ) {
                Text(
                    text = if (expanded) msg.reasoningContent else "💭 思考过程（点击展开）",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 1
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        // 气泡 + 头像：用户在右（绿底）、AI 在左（白底）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!isUser) {
                    ChatAvatar(
                        assistantName,
                    isUser = false,
                    onClick = onAvatarClick?.let { { it(false) } }
                )
                Spacer(Modifier.width(8.dp))
            }
            MessageBubble(
                text = msg.content.ifBlank { "…" },
                isUser = isUser,
                dark = dark
            )
            if (isUser) {
                Spacer(Modifier.width(8.dp))
                ChatAvatar(
                    "我",
                    isUser = true,
                    onClick = onAvatarClick?.let { { it(true) } }
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, isUser: Boolean, dark: Boolean) {
    // 微信配色：自己发出 = 浅绿 #95EC69（暗色深绿），对方 = 白（暗色深灰）
    val bubbleColor = if (isUser)
        (if (dark) Color(0xFF3B5323) else Color(0xFF95EC69))
    else
        (if (dark) Color(0xFF2C2C2C) else Color.White)
    val textColor = if (dark) Color(0xFFE5E5E5) else Color(0xFF181818)

    Box(
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .wrapContentSize(if (isUser) Alignment.TopEnd else Alignment.TopStart)
    ) {
        // 指向头像的小三角
        Box(
            modifier = Modifier
                .size(12.dp)
                .align(if (isUser) Alignment.TopEnd else Alignment.TopStart)
                .offset(x = if (isUser) 6.dp else (-6).dp, y = 10.dp)
                .rotate(45f)
                .background(bubbleColor)
        )
        // 气泡本体
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ChatAvatar(name: String, isUser: Boolean, onClick: (() -> Unit)? = null) {
    // 用户侧固定灰底「我」；AI 侧按原名统一配色（与列表/名片一致，备注不影响）
    val color = if (isUser) Color(0xFFB2B2B2) else avatarColor(name)
    val label = if (isUser) "我" else avatarInitial(name)
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall
        )
    }
}
