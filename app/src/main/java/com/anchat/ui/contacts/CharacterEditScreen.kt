package com.anchat.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.anchat.data.config.ModelConfig
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation
import com.anchat.ui.main.LocalApp
import kotlinx.coroutines.launch

/**
 * 统一的角色名片编辑页（新建 / 编辑角色卡 / 对话内编辑，三入口合一）。
 *
 * 通过传入参数区分两种模式，数据行为与原两页完全一致、不混合：
 * - 角色卡模式（convId < 0）：按 editId 新建或编辑 CharacterEntity，保存落库到角色卡表。
 * - 对话模式（convId >= 0）：读/写该对话的快照列（Conversation 实体），part="ai" 改角色部分、
 *   part="self" 改我的身份部分，绝不改动主角色卡。
 *
 * UI 与「新增/编辑角色卡」页保持一致；对话模式下按 part 仅展示对应区块（与原对话编辑页一致）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(
    navController: NavHostController,
    editId: Long = -1L,
    convId: Long = -1L,
    part: String = "ai"
) {
    val app = LocalApp.current
    val models by app.settingsRepository.observeModels()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // 仅在已配置「真实对话管理 AI」时才允许开启真实对话（全局开关未选则禁用）
    val realConvAvailable = app.configManager.getRealConversationModelId() != null

    val convMode = convId >= 0
    val isAi = part == "ai"

    // ── 可编辑字段（角色侧 + 身份侧，统一命名，供两种模式共用） ──
    var charName by remember { mutableStateOf("") }
    var charAvatar by remember { mutableStateOf("") }
    var charDescription by remember { mutableStateOf("") }   // 保留旧值，仅隐藏 UI
    var charRemark by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var charGreeting by remember { mutableStateOf("") }

    var userAvatar by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userDescription by remember { mutableStateOf("") }

    var modelId by remember { mutableStateOf<String?>(null) }
    var thinkingEnabled by remember { mutableStateOf(false) }
    var realConversation by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    var existing by remember { mutableStateOf<CharacterEntity?>(null) }   // 角色卡模式：原实体
    var conv by remember { mutableStateOf<Conversation?>(null) }         // 对话模式：原对话

    var nameError by remember { mutableStateOf(false) }
    var promptError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 角色侧头像 / 身份侧头像 各一个选择器
    val pickCharAvatar = rememberAvatarPicker { charAvatar = it }
    val pickUserAvatar = rememberAvatarPicker { userAvatar = it }

    // ── 预填：根据模式分别加载，逻辑与原两页一致 ──
    LaunchedEffect(editId, convId) {
        if (convMode) {
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
            userName = if (c?.userIdentityOverridden == true) c.userName ?: "" else app.configManager.getDefaultUserName()
            userAvatar = if (c?.userIdentityOverridden == true) c.userAvatar ?: "" else (character?.userAvatar ?: app.configManager.getDefaultUserAvatar())
            userDescription = if (c?.userIdentityOverridden == true) c.userDescription ?: "" else app.configManager.getDefaultUserDescription()
            modelId = c?.modelId ?: character?.modelId
            thinkingEnabled = c?.charThinkingEnabled ?: character?.thinkingEnabled ?: false
            realConversation = c?.charRealConversation ?: character?.realConversation ?: false
        } else if (editId >= 0) {
            val e = app.localRepository.getCharacter(editId)
            existing = e
            if (e != null) {
                charName = e.name
                charAvatar = e.avatar ?: ""
                charDescription = e.description ?: ""
                charRemark = e.remark ?: ""
                systemPrompt = e.systemPrompt
                charGreeting = e.greeting ?: ""
                userAvatar = e.userAvatar ?: ""
                userName = e.userName ?: ""
                userDescription = e.userDescription ?: ""
                modelId = e.modelId
                thinkingEnabled = e.thinkingEnabled
                realConversation = e.realConversation
            }
        }
    }

    // 兼容旧数据：若 modelId 存的是「模型名称」而非 id，归一化为配置里的模型 id
    LaunchedEffect(models) {
        if (modelId != null && models.none { it.id == modelId }) {
            models.firstOrNull { it.name == modelId }?.let { modelId = it.id }
        }
    }

    val titleText = when {
        convMode -> if (isAi) "编辑角色" else "编辑我的身份"
        editId >= 0 -> "编辑角色卡"
        else -> "新建角色卡"
    }

    // 角色侧字段变更（带错误清除）
    val onName: (String) -> Unit = { charName = it; nameError = false }
    val onSystemPrompt: (String) -> Unit = { systemPrompt = it; promptError = false }
    val onRemark: (String) -> Unit = { charRemark = it }
    val onGreeting: (String) -> Unit = { charGreeting = it }

    val needsRole = !convMode || isAi

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titleText, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        when {
                            needsRole && charName.isBlank() -> nameError = true
                            needsRole && systemPrompt.isBlank() -> promptError = true
                            else -> {
                                if (convMode) {
                                    // 对话模式：只写该对话的快照列，不触碰主角色卡
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
                                            charRealConversation = if (isAi) realConversation else c.charRealConversation,
                                            modelId = if (isAi) modelId else c.modelId,
                                            userIdentityOverridden = if (!isAi) true else c.userIdentityOverridden,
                                            userName = if (!isAi) userName.trim().ifBlank { null } else c.userName,
                                            userAvatar = if (!isAi) userAvatar.trim().ifBlank { null } else c.userAvatar,
                                            userDescription = if (!isAi) userDescription.trim().ifBlank { null } else c.userDescription
                                        )
                                        scope.launch {
                                            app.localRepository.updateConversation(updated)
                                            navController.popBackStack()
                                        }
                                    }
                                } else {
                                    // 角色卡模式：新建或编辑 CharacterEntity
                                    val ex = existing
                                    val entity = if (editId >= 0 && ex != null) {
                                        ex.copy(
                                            name = charName.trim(),
                                            avatar = charAvatar.trim().ifBlank { null },
                                            description = charDescription.trim().ifBlank { null },
                                            remark = charRemark.trim().ifBlank { null },
                                            systemPrompt = systemPrompt.trim(),
                                            greeting = charGreeting.trim().ifBlank { null },
                                            userAvatar = userAvatar.trim().ifBlank { null },
                                            userName = userName.trim().ifBlank { null },
                                            userDescription = userDescription.trim().ifBlank { null },
                                            modelId = modelId,
                                            thinkingEnabled = thinkingEnabled,
                                            realConversation = realConversation,
                                            createdAt = ex.createdAt,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    } else {
                                        CharacterEntity(
                                            name = charName.trim(),
                                            avatar = charAvatar.trim().ifBlank { null },
                                            description = charDescription.trim().ifBlank { null },
                                            remark = charRemark.trim().ifBlank { null },
                                            systemPrompt = systemPrompt.trim(),
                                            greeting = charGreeting.trim().ifBlank { null },
                                            userAvatar = userAvatar.trim().ifBlank { null },
                                            userName = userName.trim().ifBlank { null },
                                            userDescription = userDescription.trim().ifBlank { null },
                                            modelId = modelId,
                                            thinkingEnabled = thinkingEnabled,
                                            realConversation = realConversation
                                        )
                                    }
                                    scope.launch {
                                        if (editId >= 0 && ex != null) {
                                            app.localRepository.updateCharacter(entity)
                                        } else {
                                            app.localRepository.insertCharacter(entity)
                                        }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (nameError) {
                Text("请填写角色名称", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (promptError) {
                Text("请填写系统提示词", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (convMode && !isAi) {
                // 对话内「我的身份」编辑
                AvatarUploadBox(
                    name = userName.ifBlank { "我" },
                    avatarPath = userAvatar.ifBlank { null },
                    onPick = pickUserAvatar,
                    onClear = { userAvatar = "" }
                )
                Text(
                    text = if (userAvatar.isBlank()) "点击上传头像" else "点击更换头像",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SectionHeader("你的身份")
                GroupCard {
                    IdentityEditFields(
                        userName = userName,
                        onUserName = { userName = it },
                        userDescription = userDescription,
                        onUserDescription = { userDescription = it }
                    )
                }
            } else {
                // 角色卡模式 / 对话内「角色」编辑：角色头像 + 角色信息 + 模型设置（+ 角色卡模式的「你的身份」）
                AvatarUploadBox(
                    name = charName.ifBlank { if (convMode) "角" else "新" },
                    avatarPath = charAvatar.ifBlank { null },
                    onPick = pickCharAvatar,
                    onClear = { charAvatar = "" }
                )
                Text(
                    text = if (charAvatar.isBlank()) "点击上传头像" else "点击更换头像",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SectionHeader("角色信息")
                GroupCard {
                    RoleEditFields(
                        name = charName,
                        onName = onName,
                        remark = charRemark,
                        onRemark = onRemark,
                        systemPrompt = systemPrompt,
                        onSystemPrompt = onSystemPrompt,
                        greeting = charGreeting,
                        onGreeting = onGreeting,
                        nameError = nameError,
                        promptError = promptError
                    )
                }

                SectionHeader("模型设置")
                GroupCard {
                ModelSettingsFields(
                    models = models,
                    modelId = modelId,
                    onModelId = { modelId = it },
                    thinkingEnabled = thinkingEnabled,
                    onThinking = { thinkingEnabled = it },
                    realConversation = realConversation,
                    onRealConversation = { realConversation = it },
                    realConversationEnabled = realConvAvailable,
                    expanded = modelExpanded,
                    onExpanded = { modelExpanded = it }
                )
                }

                if (!convMode) {
                    SectionHeader("你的身份")
                    GroupCard {
                        IdentityEditFields(
                            userName = userName,
                            onUserName = { userName = it },
                            userDescription = userDescription,
                            onUserDescription = { userDescription = it }
                        )
                    }
                }
            }
        }
    }
}

/** 角色信息区块：名称+备注同行、系统提示词、开场白 */
@Composable
private fun RoleEditFields(
    name: String,
    onName: (String) -> Unit,
    remark: String,
    onRemark: (String) -> Unit,
    systemPrompt: String,
    onSystemPrompt: (String) -> Unit,
    greeting: String,
    onGreeting: (String) -> Unit,
    nameError: Boolean,
    promptError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("角色名称 *") },
            singleLine = true,
            isError = nameError,
            supportingText = if (nameError) { { Text("请填写角色名称") } } else null,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = remark,
            onValueChange = onRemark,
            label = { Text("备注（可选）") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }

    OutlinedTextField(
        value = systemPrompt,
        onValueChange = onSystemPrompt,
        label = { Text("系统提示词 *") },
        minLines = 4,
        isError = promptError,
        supportingText = if (promptError) { { Text("请填写系统提示词") } } else null,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = greeting,
        onValueChange = onGreeting,
        label = { Text("开场白（可选，进入对话时自动发送）") },
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 模型设置区块：对话模型下拉 + 思考模式开关 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSettingsFields(
    models: List<ModelConfig>,
    modelId: String?,
    onModelId: (String?) -> Unit,
    thinkingEnabled: Boolean,
    onThinking: (Boolean) -> Unit,
    realConversation: Boolean,
    onRealConversation: (Boolean) -> Unit,
    realConversationEnabled: Boolean,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit
) {
    val selectedModelName =
        models.firstOrNull { it.id == modelId || it.name == modelId }?.name
            ?: "默认（跟随全局）"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpanded,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedModelName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("对话模型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpanded(false) }
        ) {
            DropdownMenuItem(
                text = { Text("默认（跟随全局）") },
                onClick = {
                    onModelId(null)
                    onExpanded(false)
                }
            )
            models.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.name) },
                    onClick = {
                        onModelId(m.id)
                        onExpanded(false)
                    }
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("思考模式", style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = thinkingEnabled,
            onCheckedChange = onThinking
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("真实对话", style = MaterialTheme.typography.bodyLarge)
            if (!realConversationEnabled) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "需先在「模型管理」选择真实对话管理 AI",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Switch(
            checked = realConversation,
            onCheckedChange = onRealConversation,
            enabled = realConversationEnabled
        )
    }
}

/** 身份区块：用户姓名 + 身份描述 */
@Composable
private fun IdentityEditFields(
    userName: String,
    onUserName: (String) -> Unit,
    userDescription: String,
    onUserDescription: (String) -> Unit
) {
    OutlinedTextField(
        value = userName,
        onValueChange = onUserName,
        label = { Text("用户姓名（可选）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = userDescription,
        onValueChange = onUserDescription,
        label = { Text("用户身份描述（可选）") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
}
