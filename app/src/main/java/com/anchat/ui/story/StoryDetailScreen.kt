package com.anchat.ui.story

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.anchat.AnChatApplication
import com.anchat.data.repository.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 故事详情：电子书式纯阅读（上下滑动翻阅）。
 * 书名（章节标题）在「故事集列表」长按后弹菜单修改，这里只展示、不编辑。
 * 阅读进度按「已读比例」存于 AppConfig（不碰 Room，随备份走），退出重进自动续读。
 */
class StoryDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val anchatApp = app as AnChatApplication
    private val repo: LocalRepository = anchatApp.localRepository
    private val configManager = anchatApp.configManager

    fun observe(id: Long) = repo.observeConversation(id)
    fun progress(id: Long): Float = configManager.getStoryProgress(id)
    fun saveProgress(id: Long, ratio: Float) = configManager.setStoryProgress(id, ratio)
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun StoryDetailScreen(navController: NavHostController, convId: Long) {
    val viewModel: StoryDetailViewModel = viewModel()
    val conv by viewModel.observe(convId).collectAsStateWithLifecycle(initialValue = null)
    val text = conv?.storyText ?: ""

    val scrollState = rememberScrollState()
    var restored by remember(convId) { mutableStateOf(false) }

    // 正文加载完、布局稳定后，一次性跳回上次的阅读比例（比例而非像素，换屏/换字号仍大致对得上）
    LaunchedEffect(convId, text.length) {
        if (restored || text.isBlank()) return@LaunchedEffect
        val ratio = viewModel.progress(convId)
        if (ratio <= 0f) {
            restored = true
            return@LaunchedEffect
        }
        val max = snapshotFlow { scrollState.maxValue }.debounce(120L).first()
        // max=0 表示一屏就能看完（或内容已变短），无需跳转，直接结束恢复
        if (max > 0) scrollState.scrollTo((ratio * max).roundToInt().coerceIn(0, max))
        restored = true
    }

    // 停止滚动 400ms 才写一次进度，避免每帧写配置文件
    LaunchedEffect(convId) {
        snapshotFlow { scrollState.value }
            .debounce(400L)
            .collect { value ->
                val max = scrollState.maxValue
                if (max > 0) {
                    withContext(Dispatchers.IO) {
                        viewModel.saveProgress(convId, value.toFloat() / max)
                    }
                }
            }
    }

    // 退出页面兜底：滑到一半立刻返回也不丢进度
    DisposableEffect(convId) {
        onDispose {
            val max = scrollState.maxValue
            if (max > 0) viewModel.saveProgress(convId, scrollState.value.toFloat() / max)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("故事", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 书名（章节标题）居中置顶
            Text(
                text = conv?.title?.takeIf { it.isNotBlank() } ?: "未命名",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
            // 正文：舒适行距，左右留白，保留「我：/角色名：」分段与换行
            Text(
                text = text.ifBlank { "（这个故事还没有内容）" },
                style = MaterialTheme.typography.bodyLarge.copy(
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.6f
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
