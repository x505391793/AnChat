package com.anchat.ui.story

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.anchat.AnChatApplication
import com.anchat.data.local.entity.Conversation
import com.anchat.data.repository.LocalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 故事集列表：展示所有「封存为故事」的对话 */
class StoriesViewModel(app: Application) : AndroidViewModel(app) {
    private val anchatApp = app as AnChatApplication
    private val repo: LocalRepository = anchatApp.localRepository
    private val configManager = anchatApp.configManager
    val stories: StateFlow<List<Conversation>> = repo.getArchivedConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun rename(id: Long, title: String) {
        viewModelScope.launch { repo.renameConversation(id, title) }
    }

    /** 删除故事：联动清 raw_replies / behaviors / 对话本体（不可恢复） */
    fun deleteStory(id: Long) {
        viewModelScope.launch {
            repo.deleteConversation(id)
            configManager.clearStoryProgress(id)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoriesScreen(navController: NavHostController) {
    val viewModel: StoriesViewModel = viewModel()
    val stories by viewModel.stories.collectAsStateWithLifecycle()

    // 长按菜单状态：当前弹出的那条故事 id + 改名弹窗状态 + 删除确认弹窗状态
    var menuForId by remember { mutableStateOf<Long?>(null) }
    var renaming by remember { mutableStateOf<Conversation?>(null) }
    var deleting by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("故事集", style = MaterialTheme.typography.titleMedium) }) }
    ) { padding ->
        if (stories.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有故事", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "在对话列表左滑 → 封存，即可把对话变成故事",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(stories, key = { it.id }) { conv ->
                    val onClick = remember(conv.id, navController) {
                        { navController.navigate("story/${conv.id}") }
                    }
                    StoryListItem(
                        conv = conv,
                        onClick = onClick,
                        onLongClick = { menuForId = conv.id },
                        onRename = { renaming = conv }
                    )
                    DropdownMenu(
                        expanded = menuForId == conv.id,
                        onDismissRequest = { menuForId = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("修改名称") },
                            onClick = {
                                renaming = conv
                                menuForId = null
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                deleting = conv
                                menuForId = null
                            }
                        )
                    }
                }
            }
        }
    }

    if (renaming != null) {
        RenameStoryDialog(
            current = renaming!!.title,
            onDismiss = { renaming = null },
            onConfirm = { newName ->
                viewModel.rename(renaming!!.id, newName)
                renaming = null
            }
        )
    }

    if (deleting != null) {
        DeleteStoryDialog(
            title = deleting!!.title,
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteStory(deleting!!.id)
                deleting = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StoryListItem(
    conv: Conversation,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit
) {
    val partner = conv.charName?.takeIf { it.isNotBlank() } ?: conv.title
    val wordCount = conv.storyText?.length ?: 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            conv.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "与 $partner 的故事 · $wordCount 字 · ${formatDate(conv.updatedAt)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DeleteStoryDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除故事") },
        text = {
            Text(
                "确定删除《$title》吗？\n删除后该故事及其原始聊天记录将无法恢复。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("删除") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun RenameStoryDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改名称") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("故事书名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotBlank()) onConfirm(trimmed)
                }
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun formatDate(ts: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date(ts))
}
