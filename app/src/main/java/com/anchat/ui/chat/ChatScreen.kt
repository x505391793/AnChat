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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.anchat.AnChatApplication
import com.anchat.ui.main.LocalApp
import com.anchat.ui.main.LocalIsDark
import com.anchat.ui.main.Screen
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.flow.filter

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

    // 在 composable 作用域先取出，供下方 pointerInput（非 composable lambda）使用
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
    // 记录「尾部消息 id」：分页上滑是头部前插（尾部不变），只有新消息到达（尾部变化）
    // 才应自动滚到底部；否则上滑加载更早历史时会被错误拽回底部。
    var prevTailId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(messages.size, messages.lastOrNull()?.behaviorId) {
        if (messages.isEmpty()) return@LaunchedEffect
        val tailId = messages.lastOrNull()?.behaviorId
        if (!initialScrolled) {
            // 首次进入：无动画瞬时跳到底部
            listState.scrollToItem(messages.lastIndex)
            initialScrolled = true
            prevTailId = tailId
        } else if (tailId != prevTailId) {
            // 仅最新一条（尾部）变化 = 新消息到达：平滑滚到底部
            listState.animateScrollToItem(messages.lastIndex)
            prevTailId = tailId
        }
    }

    // 上滑到顶部增量加载更早历史（分页）：避免进入长对话一次性全量加载导致卡顿。
    // 用 snapshotFlow(Unit) 常驻监听，prepend 后用 scrollToItem 锚定位置，避免画面跳动。
    val isLoadingOlder = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .filter { it <= 2 }
            .collect {
                if (isLoadingOlder.value || !viewModel.canLoadOlder) return@collect
                isLoadingOlder.value = true
                try {
                    val anchorIndex = listState.firstVisibleItemIndex
                    val anchorOffset = listState.firstVisibleItemScrollOffset
                    val added = viewModel.loadOlder()
                    if (added > 0) listState.scrollToItem(anchorIndex + added, anchorOffset)
                } finally {
                    isLoadingOlder.value = false
                }
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
            // 列表项回调上提稳定化：避免父重组时 items lambda 重建 lambda 实例，
            // 导致全表可见项因参数引用变化而无效重组。
            val onDelete: (String) -> Unit = remember(viewModel) {
                { viewModel.deleteMessage(it) }
            }
            val onSaveEdit: (String, String) -> Unit = remember(viewModel) {
                { id, newText -> viewModel.updateMessage(id, newText) }
            }
            val onRegenerate: (String) -> Unit = remember(viewModel) {
                { viewModel.regenerateMessage(it) }
            }
            val onAvatarClick: (Boolean) -> Unit = remember(navController, conversationId) {
                { isUser ->
                    val id = conversationId
                    if (id != null) {
                        navController.navigate("conversation/$id?part=${if (isUser) "self" else "ai"}")
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .pointerInput(Unit) {
                        // 点按消息区 → 清空输入框焦点 + 收起键盘。
                        // 仅响应 tap，不挡长按选区，也不影响 LazyColumn 滚动。
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        })
                    },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    messages,
                    key = { it.behaviorId ?: "sys_${it.role}_${it.content.hashCode()}" },
                    contentType = { it.role }
                ) { msg ->
                    ChatMessageItem(
                        msg = msg,
                        dark = dark,
                        assistantName = assistantName,
                        charAvatar = profile?.charAvatar,
                        userAvatar = profile?.userAvatar,
                        showReasoning = thinkingEnabled,
                        onDelete = onDelete,
                        onSaveEdit = onSaveEdit,
                        onRegenerate = onRegenerate,
                        onAvatarClick = onAvatarClick
                    )
                }
            }

            // 输入区活跃状态（聚焦或键盘弹出）→ 上报 VM 驱动自动触发计时
            var isInputFocused by remember { mutableStateOf(false) }
            val density = LocalDensity.current
            val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
            val isInputActive = isInputFocused || isKeyboardVisible
            LaunchedEffect(isInputActive) {
                viewModel.setInputActive(isInputActive)
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
                    modifier = Modifier.weight(1f).onFocusChanged { isInputFocused = it.isFocused },
                    placeholder = { Text("发送消息") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.send(inputText)
                            inputText = ""
                            // 输入完毕（发送后）主动取消输入框聚焦、收起键盘
                            focusManager.clearFocus()
                            keyboardController?.hide()
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
                            // 输入完毕（发送后）主动取消输入框聚焦、收起键盘
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    },
                    enabled = !isLoading && inputText.isNotBlank()
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "发送")
                }
                // 排队模式：触发按钮（测试模式：弹气泡不发送 API）
                val isQueueMode by viewModel.isQueueMode.collectAsStateWithLifecycle()
                if (isQueueMode) {
                    IconButton(
                        onClick = { viewModel.fireTrigger() },
                        enabled = !isLoading
                    ) {
                        Text("▶", style = MaterialTheme.typography.titleMedium)
                    }
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
    onDelete: (String) -> Unit = {},
    onSaveEdit: (String, String) -> Unit = { _, _ -> },
    onRegenerate: (String) -> Unit = {},
    onAvatarClick: ((isUser: Boolean) -> Unit)? = null
) {
    val isSystem = msg.role == "system"
    val isUser = msg.role == "user"
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
                behaviorId = msg.behaviorId,
                canRegenerate = isAssistant && msg.behaviorId != null,
                onDelete = onDelete,
                onSaveEdit = onSaveEdit,
                onRegenerate = onRegenerate
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
/**
 * 接管系统浮动文本工具栏：界面上的菜单已改成气泡底部的图标行，
 * 这里只做两件事——抑制长按弹出的系统菜单，并把选区的复制/全选回调交给图标行使用。
 */
private data class BubbleToolbarState(
    val onCopy: (() -> Unit)?,
    val onPaste: (() -> Unit)?,
    val onCut: (() -> Unit)?,
    val onSelectAll: (() -> Unit)?
)

private class BubbleTextToolbar : TextToolbar {
    var data by mutableStateOf<BubbleToolbarState?>(null)
        private set

    override val status: TextToolbarStatus
        get() = if (data != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        data = BubbleToolbarState(
            onCopy = onCopyRequested,
            onPaste = onPasteRequested,
            onCut = onCutRequested,
            onSelectAll = onSelectAllRequested
        )
    }

    override fun hide() {
        data = null
    }
}

@Composable
private fun MessageBubble(
    text: String,
    isUser: Boolean,
    dark: Boolean,
    behaviorId: String? = null,
    canRegenerate: Boolean = false,
    onDelete: (String) -> Unit = {},
    onSaveEdit: (String, String) -> Unit = { _, _ -> },
    onRegenerate: (String) -> Unit = {}
) {
    // 微信配色：自己发出 = 浅绿 #95EC69（暗色深绿），对方 = 白（暗色深灰）
    val bubbleColor = if (isUser)
        (if (dark) Color(0xFF3B5323) else Color(0xFF95EC69))
    else
        (if (dark) Color(0xFF2C2C2C) else Color.White)
    val textColor = if (dark) Color(0xFFE5E5E5) else Color(0xFF181818)
    val dividerColor = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f)

    // 显示端剥离 markdown 语法（API 不塞任何额外提示词，纯客户端处理）。
    // 用 remember 缓存：滚动回收 item 重 compose 时不再每屏重跑 6+ 条正则。
    val displayText = remember(text) { stripMarkdown(text) }

    // 菜单态 / 编辑态 / 全选态：只对当前这一条气泡生效，item 滑出回收后自然复位
    var menuOpen by remember(behaviorId) { mutableStateOf(false) }
    var editing by remember(behaviorId) { mutableStateOf(false) }
    var selecting by remember(behaviorId) { mutableStateOf(false) }
    var draft by remember(behaviorId) { mutableStateOf(text) }

    // 仅用于「抑制系统浮动菜单 + 拿选区复制回调」；真正的菜单是气泡底部的图标行
    val toolbar = remember(behaviorId) { BubbleTextToolbar() }
    val sel = toolbar.data
    val clipboard = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val editFocus = remember { FocusRequester() }
    val bid = behaviorId

    val rowVisible = menuOpen || editing

    // 外部文本变化（重新生成/编辑落库后回灌）时同步草稿，编辑中不覆盖用户正在改的内容
    LaunchedEffect(text) { if (!editing) draft = text }
    LaunchedEffect(editing) { if (editing) editFocus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .wrapContentWidth(if (isUser) Alignment.End else Alignment.Start)
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
        // 气泡本体：SelectionContainer 保留长按划选（光标手柄照旧），系统浮动菜单被
        // BubbleTextToolbar 抑制；点一下气泡在「气泡底端」展开半透明图标行（编辑态点空白处=保存）。
        Box(
            modifier = Modifier
                .background(bubbleColor, RoundedCornerShape(8.dp))
                .pointerInput(bid, editing) {
                    detectTapGestures(onTap = {
                        if (editing) {
                            val t = draft.trim()
                            editing = false
                            menuOpen = false
                            focusManager.clearFocus()
                            if (t.isNotEmpty() && t != text && bid != null) onSaveEdit(bid, t)
                        } else {
                            menuOpen = !menuOpen
                            if (!menuOpen) selecting = false
                        }
                    })
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                CompositionLocalProvider(LocalTextToolbar provides toolbar) {
                    Box(Modifier.align(if (isUser) Alignment.End else Alignment.Start)) {
                        when {
                            editing -> BasicTextField(
                                value = draft,
                                onValueChange = { draft = it },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                                cursorBrush = SolidColor(textColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(editFocus)
                            )
                            selecting -> SelectAllText(text = displayText, color = textColor)
                            else -> SelectionContainer {
                                Text(
                                    text = displayText,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                if (rowVisible) {
                    Spacer(Modifier.height(4.dp))
                    BubbleActionRow(
                        editing = editing,
                        dark = dark,
                        iconTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        dividerColor = dividerColor,
                        canEdit = bid != null,
                        canRegenerate = canRegenerate && bid != null,
                        onSave = {
                            val t = draft.trim()
                            editing = false
                            menuOpen = false
                            focusManager.clearFocus()
                            if (t.isNotEmpty() && t != text && bid != null) onSaveEdit(bid, t)
                        },
                        onCancel = {
                            draft = text
                            editing = false
                            menuOpen = false
                            focusManager.clearFocus()
                        },
                        onSelectAll = { selecting = true },
                        onCopy = {
                            val copy = sel?.onCopy
                            if (copy != null) copy.invoke()
                            else clipboard.setText(AnnotatedString(displayText))
                            selecting = false
                        },
                        onDelete = { menuOpen = false; if (bid != null) onDelete(bid) },
                        onEdit = { draft = text; editing = true },
                        onRegenerate = { menuOpen = false; if (bid != null) onRegenerate(bid) },
                        modifier = Modifier.align(if (isUser) Alignment.End else Alignment.Start)
                    )
                }
            }
        }

    }
}

/** 气泡底端的图标行：半透明 tint + 小尺寸，低存在感；点击热区靠 padding 撑开 */
@Composable
private fun BubbleActionRow(
    editing: Boolean,
    dark: Boolean,
    iconTint: Color,
    dividerColor: Color,
    canEdit: Boolean,
    canRegenerate: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val danger = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (dark) Color(0xFF1C1C1C).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.55f))
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        if (editing) {
            BubbleAction(Icons.Filled.Check, "保存", iconTint, onSave)
            Box(Modifier.width(1.dp).height(14.dp).background(dividerColor))
            BubbleAction(Icons.Filled.Close, "取消", iconTint, onCancel)
        } else {
            BubbleAction(Icons.Outlined.SelectAll, "全选", iconTint, onSelectAll)
            BubbleAction(Icons.Outlined.ContentCopy, "复制", iconTint, onCopy)
            if (canEdit) {
                BubbleAction(Icons.Outlined.Delete, "删除", danger, onDelete)
                BubbleAction(Icons.Outlined.Edit, "修改", iconTint, onEdit)
            }
            if (canRegenerate) {
                BubbleAction(Icons.Outlined.Refresh, "重新生成", iconTint, onRegenerate)
            }
        }
    }
}

/** 全选态：只读文本框（聚焦才能画出选区），长按可继续调选区，复制走系统选区回调 */
@Composable
private fun SelectAllText(text: String, color: Color) {
    var value by remember(text) {
        mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(text) { focus.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        readOnly = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = color),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
    )
}

/** 图标行里的单个动作：低存在感（半透明 tint + 小尺寸），点击热区仍够大 */
@Composable
private fun BubbleAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp)
            .size(18.dp)
    )
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
