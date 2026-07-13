package com.anchat.ui.chat

import android.util.Log
import android.widget.Toast
import com.anchat.ui.theme.CharacterAvatar
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val isTyping by viewModel.isTyping.collectAsStateWithLifecycle()
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
    val topBarTitle = if (isTyping) "对方正在输入中……" else displayName

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

    // 进入对话时直接定位到底部（最新消息），不带动画，避免长对话"从头滑到尾"；
    // 仅后续新增消息（用户发送 / AI 回复）才做平滑滚动。
    var initialScrolled by remember { mutableStateOf(false) }
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (!initialScrolled) {
            // 首次进入：无动画瞬时跳到底部
            listState.scrollToItem(messages.lastIndex)
            initialScrolled = true
        } else {
            // 后续新消息：平滑滚到底部
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(topBarTitle, style = MaterialTheme.typography.titleMedium) },
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
                        charAvatar = profile?.charAvatar,
                        userAvatar = profile?.userAvatar,
                        showReasoning = thinkingEnabled,
                        onDelete = { viewModel.deleteMessage(it) },
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
    charAvatar: String? = null,
    userAvatar: String? = null,
    showReasoning: Boolean = true,
    onDelete: (Long) -> Unit = {},
    onAvatarClick: ((isUser: Boolean) -> Unit)? = null
) {
    val isBehavior = msg.behaviorType != null
    val isSystem = msg.role == "system"
    val isUser = msg.role == "user" && !isBehavior
    val isAssistant = msg.role == "assistant" || msg.behaviorType == "speech"

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

    // 真实对话行为：emotion 走居中胶囊；leave 先发 content 气泡再显示离开状态
    if (msg.behaviorType == "emotion") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "😊  对方发表情包",
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
    if (msg.behaviorType == "leave") {
        // leave 类型不入气泡（content 可能是「离开」等占位），只居中显示离开状态行
        val awayText = "对方离开了"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = awayText,
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
                    avatarPath = charAvatar,
                    onClick = onAvatarClick?.let { { it(false) } }
                )
                Spacer(Modifier.width(8.dp))
            }
            MessageBubble(
                text = msg.content.ifBlank { "…" },
                isUser = isUser,
                dark = dark,
                id = msg.id,
                onDelete = onDelete
            )
            if (isUser) {
                Spacer(Modifier.width(8.dp))
                ChatAvatar(
                    "我",
                    isUser = true,
                    avatarPath = userAvatar,
                    onClick = onAvatarClick?.let { { it(true) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    text: String,
    isUser: Boolean,
    dark: Boolean,
    id: Long = -1L,
    onDelete: (Long) -> Unit = {}
) {    var showMenu by remember { mutableStateOf(false) }

    // 微信配色：自己发出 = 浅绿 #95EC69（暗色深绿），对方 = 白（暗色深灰）
    val bubbleColor = if (isUser)
        (if (dark) Color(0xFF3B5323) else Color(0xFF95EC69))
    else
        (if (dark) Color(0xFF2C2C2C) else Color.White)
    val textColor = if (dark) Color(0xFFE5E5E5) else Color(0xFF181818)

    // 显示端剥离 markdown 语法（API 不塞任何额外提示词，纯客户端处理）
    val displayText = stripMarkdown(text)

    Box(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .wrapContentWidth(if (isUser) Alignment.End else Alignment.Start)
            .combinedClickable(
                onLongClick = { showMenu = true },
                onClick = {}
            )
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
            // 文字区域优先处理长按与拖拽，系统浮动菜单复制选中的文字。
            // 气泡留白区域长按则打开操作菜单，“复制文本”复制整条消息。
            SelectionContainer {
                Text(
                    text = displayText,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    onDelete(id)
                    showMenu = false
                }
            )
        }
    }
}

/**
 * 纯客户端剥离 markdown 语法字符，让气泡显示为干净纯文字。
 * 不改动 API 请求体（不消耗任何额外 token）。
 */
private fun stripMarkdown(src: String): String {
    val lines = src.lineSequence().map { line ->
        var l = line
        // 代码围栏 ``` 整行移除（保留代码内容无意义，直接去掉标记行）
        l = l.replace(Regex("^```.*$"), "")
        // 标题 #
        l = l.replace(Regex("^#{1,6}\\s+"), "")
        // 引用 >
        l = l.replace(Regex("^>\\s?"), "")
        // 无序列表 - * +
        l = l.replace(Regex("^\\s*[-*+]\\s+"), "")
        // 有序列表 1.
        l = l.replace(Regex("^\\s*\\d+\\.\\s+"), "")
        l
    }.joinToString("\n")
    return lines
        // 粗体 ** 整段移除标记
        .replace(Regex("\\*\\*"), "")
        // 斜体 *（非双星）
        .replace(Regex("(?<!\\*)\\*(?!\\*)"), "")
        // 行内代码 `
        .replace("`", "")
        // 链接 [text](url) -> text
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        // 删除线 ~~
        .replace("~~", "")
}

@Composable
private fun ChatAvatar(
    name: String,
    isUser: Boolean,
    avatarPath: String? = null,
    onClick: (() -> Unit)? = null
) {
    // 优先显示上传的本地头像图，无图回退首字母（与列表/名片一致）
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        CharacterAvatar(
            name = name,
            avatarPath = avatarPath,
            size = 36.dp,
            corner = 6.dp
        )
    }
}
