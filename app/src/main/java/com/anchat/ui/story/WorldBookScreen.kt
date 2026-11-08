package com.anchat.ui.story

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.anchat.AnChatApplication
import com.anchat.data.local.entity.WorldBook
import com.anchat.data.repository.LocalRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 世界书管理页：列出全部卡片，支持新增 / 编辑（标题 + 内容）/ 删除 */
class WorldBookViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: LocalRepository = (app as AnChatApplication).localRepository
    val books: StateFlow<List<WorldBook>> = repo.observeWorldBooks()
        .map { it }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun upsert(title: String, content: String, id: Long = 0L) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (id > 0) {
                val existing = repo.getWorldBook(id)
                if (existing != null) {
                    repo.updateWorldBook(existing.copy(title = title.trim(), content = content, updatedAt = now))
                    return@launch
                }
            }
            repo.insertWorldBook(WorldBook(title = title.trim(), content = content, createdAt = now, updatedAt = now))
        }
    }

    fun delete(book: WorldBook) {
        viewModelScope.launch { repo.deleteWorldBook(book) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldBookScreen(navController: NavHostController) {
    val viewModel: WorldBookViewModel = viewModel()
    val books by viewModel.books.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<WorldBook?>(null) }   // null = 关闭弹窗
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("世界书", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = WorldBook(title = "", content = "", id = 0L) }) {
                Icon(Icons.Filled.Add, contentDescription = "新增世界书")
            }
        }
    ) { padding ->
        if (books.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有世界书。点右下角 + 添加设定卡片，\n之后在角色编辑页勾选它们，\n内容会自动追加到该角色的提示词。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(books, key = { it.id }) { wb ->
                    WorldBookCard(
                        book = wb,
                        onClick = { editing = wb },
                        onDelete = { viewModel.delete(wb) }
                    )
                }
            }
        }
    }

    if (editing != null) {
        WorldBookEditDialog(
            book = editing!!,
            onDismiss = { editing = null },
            onSave = { title, content ->
                viewModel.upsert(title, content, editing!!.id)
                editing = null
            }
        )
    }
}

@Composable
private fun WorldBookCard(
    book: WorldBook,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                book.title.ifBlank { "未命名" },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                book.content.takeIf { it.isNotBlank() } ?: "（空内容）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WorldBookEditDialog(
    book: WorldBook,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(book.title) }
    var content by remember { mutableStateOf(book.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (book.id > 0) "编辑世界书" else "新增世界书") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容（设定正文，会追加到角色提示词）") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isBlank()) return@TextButton
                    onSave(title, content)
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
