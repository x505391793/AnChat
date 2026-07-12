package com.anchat.ui.contacts

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anchat.ui.theme.CharacterAvatar
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.anchat.ui.main.LocalApp
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * 角色名片页（仿微信个人资料页）：
 * 标题居中显示角色名，右上「…」菜单含「删除」；
 * 内容区为头像 + 名称 + 简介，底部绿色「发消息」进入新会话、描边「编辑名片」进入编辑。
 * 参考文档 §6 / §11.3。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterCardScreen(navController: NavHostController, characterId: Long) {
    val app = LocalApp.current
    val character by remember(characterId) {
        app.localRepository.observeCharacter(characterId)
    }.collectAsStateWithLifecycle(initialValue = null)

    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(character?.name ?: "名片", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteConfirm = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        // 委托属性无法智能转型，先存进普通局部 val，再对 c 判空
        val c = character
        if (c == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("角色不存在或已删除", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 名片头部：头像 + 名称 + 简介（微信式资料页头部）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 头像：有上传图显示图片，否则按「原名」首字母+配色（默认头像）
                    CharacterAvatar(
                        name = c.name,
                        avatarPath = c.avatar,
                        size = 64.dp,
                        corner = 10.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = c.name,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (!c.description.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = c.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 发消息（底部绿色主按钮，仿微信资料页「发消息」）
                Button(
                    onClick = {
                        navController.navigate("chat/-1?characterId=${c.id}") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "发消息",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                // 编辑名片
                OutlinedButton(
                    onClick = { navController.navigate("character/edit/${c.id}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("编辑名片")
                }
            }
        }
    }

    // 删除确认（微信式居中对话框）
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除角色") },
            text = {
                Text(
                    "确定删除「${character?.name ?: ""}」？\n" +
                        "该角色将从通讯录移除，但已产生的对话会保留。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        character?.let { c ->
                            scope.launch {
                                try {
                                    app.localRepository.deleteCharacter(c)
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        app,
                                        "删除失败：${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

