package com.anchat.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import com.anchat.ui.theme.CharacterAvatar
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.anchat.data.local.entity.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavHostController) {
    val viewModel: HistoryViewModel = viewModel()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val unread by viewModel.unread.collectAsStateWithLifecycle()
    val menuExpanded = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("AnChat", style = MaterialTheme.typography.titleMedium) },
                actions = {
                    // 微信式「+」弹出菜单：添加朋友（新建角色卡）
                    Box {
                        IconButton(onClick = { menuExpanded.value = true }) {
                            Icon(Icons.Outlined.Add, contentDescription = "添加朋友")
                        }
                        DropdownMenu(
                            expanded = menuExpanded.value,
                            onDismissRequest = { menuExpanded.value = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("添加朋友") },
                                onClick = {
                                    menuExpanded.value = false
                                    navController.navigate("character/create")
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有对话", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(conversations, key = { it.id }) { conv ->
                    ConversationItem(
                        conversation = conv,
                        unreadCount = unread[conv.id] ?: 0,
                        onClick = { navController.navigate("chat/${conv.id}") },
                        onPin = { viewModel.togglePin(conv.id, !conv.isPinned) },
                        onDelete = { viewModel.delete(conv.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit
) {
    val revealWidth = 160.dp
    val density = LocalDensity.current
    val revealPx = with(density) { revealWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pinned = conversation.isPinned

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景操作区（左滑后从右侧露出）
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    onPin()
                    scope.launch { offset.animateTo(0f) }
                },
                modifier = Modifier.width(80.dp)
            ) {
                Text(
                    if (pinned) "取消置顶" else "置顶",
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(
                onClick = {
                    onDelete()
                    scope.launch { offset.animateTo(0f) }
                },
                modifier = Modifier.width(80.dp)
            ) {
                Text("删除", color = Color(0xFFE54D42))
            }
        }

        // 前景内容（可左滑）
        Surface(
            color = if (conversation.isPinned) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target =
                                    if (offset.value < -revealPx / 2f) -revealPx else 0f
                                offset.animateTo(target)
                            }
                        }
                    ) { _, dragAmount ->
                        val newOffset =
                            (offset.value + dragAmount).coerceIn(-revealPx, 0f)
                        scope.launch { offset.snapTo(newOffset) }
                    }
                }
        ) {
            ConversationRowContent(
                conversation = conversation,
                unreadCount = unreadCount,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun ConversationRowContent(
    conversation: Conversation,
    unreadCount: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (conversation.isPinned) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 名称：备注优先，其次角色名（对话内改名后列表同步），其次标题
        val displayName = conversation.charRemark?.takeIf { it.isNotBlank() }
            ?: conversation.charName?.takeIf { it.isNotBlank() } ?: conversation.title

        // 头像配色种子：必须用「原名」(charName)，绝不可取备注(remark)或标题(title)，
        // 否则同一角色在通讯录与对话列表会算出不同颜色/首字母，破坏跨列表一致。
        val avatarSeed = conversation.charName?.takeIf { it.isNotBlank() } ?: conversation.title
        Box(modifier = Modifier.size(48.dp)) {
            CharacterAvatar(
                name = avatarSeed,
                avatarPath = conversation.charAvatar,
                size = 48.dp,
                corner = 8.dp
            )
            // 未读红点（头像右上角）
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 42.dp, y = (-6).dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE54D42))
                        .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.isPinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "已置顶",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (conversation.preview.isNotBlank()) {
                Text(
                    conversation.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            formatDate(conversation.updatedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDate(ts: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}
