package com.anchat.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.anchat.data.local.entity.Conversation
import com.anchat.ui.main.LocalApp
import kotlinx.coroutines.launch

/**
 * 对话内身份二次编辑页（仿微信名片式二级页）。
 * 从聊天页点头像进入：AI 头像 → part="ai" 编辑「角色部分」，
 * 自己头像 → part="self" 编辑「用户部分」。
 * 只读写当前 conversation 的快照列，不影响主角色卡。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationProfileEditScreen(
    navController: NavHostController,
    convId: Long,
    part: String = "ai"
) {
    val app = LocalApp.current
    val models by app.settingsRepository.observeModels()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var conv by remember { mutableStateOf<Conversation?>(null) }

    // 可编辑字段（prefill 自对话快照「有效值」，回退主角色卡/全局主身份）
    var charName by remember { mutableStateOf("") }
    var charAvatar by remember { mutableStateOf("") }
    var charDescription by remember { mutableStateOf("") }
    var charRemark by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var charGreeting by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userAvatar by remember { mutableStateOf("") }
    var userDescription by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf<String?>(null) }
    var thinkingEnabled by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val isAi = part == "ai"
    val scope = rememberCoroutineScope()

    // 加载对话快照并回填（取有效值，回退主角色卡 / 全局主身份）
    LaunchedEffect(convId) {
        val c = app.localRepository.getConversation(convId)
        conv = c
        val character = if ((c?.characterId ?: -1L) >= 0) {
            app.localRepository.getCharacter(c!!.characterId)
        } else null
        charName = (c?.charName ?: character?.name ?: c?.title) ?: ""
        charAvatar = c?.charAvatar ?: character?.avatar ?: ""
        charDescription = c?.charDescription ?: character?.description ?: ""
        charRemark = c?.charRemark ?: character?.remark ?: ""
        systemPrompt = c?.systemPrompt ?: character?.systemPrompt ?: ""
        charGreeting = c?.charGreeting ?: character?.greeting ?: ""
        userName = c?.userName ?: character?.userName ?: app.configManager.getDefaultUserName()
        userAvatar = c?.userAvatar ?: character?.userAvatar ?: ""
        userDescription = c?.userDescription ?: character?.userDescription ?: app.configManager.getDefaultUserDescription()
        modelId = c?.modelId ?: character?.modelId
        thinkingEnabled = c?.charThinkingEnabled ?: character?.thinkingEnabled ?: false
    }

    // 兼容旧数据：若 modelId 存的是「模型名称」而非 id，归一化为配置里的模型 id。
    LaunchedEffect(models) {
        if (modelId != null && models.none { it.id == modelId }) {
            models.firstOrNull { it.name == modelId }?.let { modelId = it.id }
        }
    }

    val titleText = if (isAi) "编辑角色" else "编辑我的身份"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        when {
                            isAi && charName.isBlank() -> error = "请填写角色名称"
                            isAi && systemPrompt.isBlank() -> error = "请填写系统提示词"
                            else -> {
                                val c = conv
                                if (c != null) {
                                    val updated = c.copy(
                                        charName = if (isAi) charName.trim().ifBlank { null } else c.charName,
                                        charAvatar = if (isAi) charAvatar.trim().ifBlank { null } else c.charAvatar,
                                        charDescription = if (isAi) charDescription.trim().ifBlank { null } else c.charDescription,
                                        charRemark = if (isAi) charRemark.trim().ifBlank { null } else c.charRemark,
                                        systemPrompt = if (isAi) systemPrompt.trim().ifBlank { null } else c.systemPrompt,
                                        charGreeting = if (isAi) charGreeting.trim().ifBlank { null } else c.charGreeting,
                                        charThinkingEnabled = if (isAi) thinkingEnabled else c.charThinkingEnabled,
                                        modelId = if (isAi) modelId else c.modelId,
                                        userName = if (!isAi) userName.trim().ifBlank { null } else c.userName,
                                        userAvatar = if (!isAi) userAvatar.trim().ifBlank { null } else c.userAvatar,
                                        userDescription = if (!isAi) userDescription.trim().ifBlank { null } else c.userDescription
                                    )
                                    scope.launch {
                                        app.localRepository.updateConversation(updated)
                                        navController.popBackStack()
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (error != null) {
                Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (isAi) {
                Text("角色信息", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = charName,
                    onValueChange = {
                        charName = it
                        if (error != null) error = null
                    },
                    label = { Text("角色名称 *") },
                    singleLine = true,
                    isError = isAi && charName.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = charAvatar,
                    onValueChange = { charAvatar = it },
                    label = { Text("角色头像（路径或 URL，可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = charDescription,
                    onValueChange = { charDescription = it },
                    label = { Text("角色简介（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 备注：在对话列表 / 顶栏优先显示的名（不影响主角色卡）
                OutlinedTextField(
                    value = charRemark,
                    onValueChange = { charRemark = it },
                    label = { Text("备注（可选，显示在对话列表与顶栏）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("对话设置", style = MaterialTheme.typography.titleMedium)

                val selectedModelName =
                    models.firstOrNull { it.id == modelId || it.name == modelId }?.name
                        ?: "默认（跟随全局）"
                ExposedDropdownMenuBox(
                    expanded = modelExpanded,
                    onExpandedChange = { modelExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedModelName,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("对话模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = modelExpanded,
                        onDismissRequest = { modelExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("默认（跟随全局）") },
                            onClick = {
                                modelId = null
                                modelExpanded = false
                            }
                        )
                        models.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.name) },
                                onClick = {
                                    modelId = m.id
                                    modelExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("思考模式", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = thinkingEnabled,
                        onCheckedChange = { thinkingEnabled = it }
                    )
                }

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = {
                        systemPrompt = it
                        if (error != null) error = null
                    },
                    label = { Text("系统提示词 *") },
                    minLines = 4,
                    isError = isAi && systemPrompt.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = charGreeting,
                    onValueChange = { charGreeting = it },
                    label = { Text("开场白（可选，进入对话时自动发送）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text("用户身份", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("用户姓名（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = userAvatar,
                    onValueChange = { userAvatar = it },
                    label = { Text("用户头像（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = userDescription,
                    onValueChange = { userDescription = it },
                    label = { Text("用户身份描述（可选）") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
