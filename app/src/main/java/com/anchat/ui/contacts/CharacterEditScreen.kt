package com.anchat.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.ui.main.LocalApp
import com.anchat.ui.theme.CharacterAvatar
import kotlinx.coroutines.launch

/** 保存校验失败的类型：仅用于在对应输入框下方显示红字提示 */
private sealed interface SaveError {
    val message: String
    data object Name : SaveError {
        override val message = "请填写角色名称"
    }
    data object Prompt : SaveError {
        override val message = "请填写系统提示词"
    }
}

/**
 * 角色卡编辑页（新建 / 编辑，仿微信个人名片 / 主页风格）：
 * 顶部居中头像上传框 → 名称+备注同一行 → 系统提示词 → 模型设置卡（模型+思考）
 * → 「你的身份」卡（用户姓名+描述）。去掉了原来的「角色简介」字段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(navController: NavHostController, editId: Long = -1L) {
    val app = LocalApp.current
    val models by app.settingsRepository.observeModels()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }

    var userAvatar by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userDescription by remember { mutableStateOf("") }

    var modelId by remember { mutableStateOf<String?>(null) }
    var thinkingEnabled by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    /** 编辑模式：保存被编辑的原始实体，保存时走 copy 更新 */
    var existing by remember { mutableStateOf<CharacterEntity?>(null) }

    var error by remember { mutableStateOf<SaveError?>(null) }
    val scope = rememberCoroutineScope()

    // 选择本地图片 → 复制到应用内部存储（复用共享组件）
    val pickAvatar = rememberAvatarPicker { avatar = it }

    // 编辑模式：加载并回填现有角色（主键 id 唯一，允许重名，不做查重）
    LaunchedEffect(editId) {
        if (editId >= 0) {
            val e = app.localRepository.getCharacter(editId)
            existing = e
            if (e != null) {
                name = e.name
                avatar = e.avatar ?: ""
                description = e.description ?: ""   // 保留旧值，仅隐藏 UI
                remark = e.remark ?: ""
                systemPrompt = e.systemPrompt
                greeting = e.greeting ?: ""
                userAvatar = e.userAvatar ?: "" // 保留旧值，仅隐藏 UI
                userName = e.userName ?: ""
                userDescription = e.userDescription ?: ""
                modelId = e.modelId
                thinkingEnabled = e.thinkingEnabled
            }
        }
    }

    // 兼容旧数据：若 modelId 存的是「模型名称」而非 id，归一化为配置里的模型 id，
    // 保证下拉默认选中正确，且保存时落库的是 id。
    LaunchedEffect(models) {
        if (modelId != null && models.none { it.id == modelId }) {
            models.firstOrNull { it.name == modelId }?.let { modelId = it.id }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (editId >= 0) "编辑角色卡" else "新建角色卡") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        when {
                            name.isBlank() -> error = SaveError.Name
                            systemPrompt.isBlank() -> error = SaveError.Prompt
                            else -> {
                                // 委托属性无法智能转型，先存进普通局部 val ex
                                val ex = existing
                                val entity = if (editId >= 0 && ex != null) {
                                    // 编辑模式：基于原实体 copy 更新（主键 id 不变，允许重名）
                                    ex.copy(
                                        name = name.trim(),
                                        avatar = avatar.trim().ifBlank { null },
                                        description = description.trim().ifBlank { null },
                                        remark = remark.trim().ifBlank { null },
                                        systemPrompt = systemPrompt.trim(),
                                        greeting = greeting.trim().ifBlank { null },
                                        userAvatar = userAvatar.trim().ifBlank { null },
                                        userName = userName.trim().ifBlank { null },
                                        userDescription = userDescription.trim().ifBlank { null },
                                        modelId = modelId,
                                        thinkingEnabled = thinkingEnabled,
                                        createdAt = ex.createdAt,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                } else {
                                    // 新建模式
                                    CharacterEntity(
                                        name = name.trim(),
                                        avatar = avatar.trim().ifBlank { null },
                                        description = description.trim().ifBlank { null },
                                        remark = remark.trim().ifBlank { null },
                                        systemPrompt = systemPrompt.trim(),
                                        greeting = greeting.trim().ifBlank { null },
                                        userAvatar = userAvatar.trim().ifBlank { null },
                                        userName = userName.trim().ifBlank { null },
                                        userDescription = userDescription.trim().ifBlank { null },
                                        modelId = modelId,
                                        thinkingEnabled = thinkingEnabled
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
            // ── 头像上传框（顶部居中，仿个人主页） ──
            AvatarUploadBox(
                name = name.ifBlank { "新" },
                avatarPath = avatar.ifBlank { null },
                onPick = pickAvatar,
                onClear = { avatar = "" }
            )
            Text(
                text = if (avatar.isBlank()) "点击上传头像" else "点击更换头像",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 角色信息 ──
            SectionHeader("角色信息")
            GroupCard {
                // 名称 + 备注：同一行（备注优先于名称显示在列表）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            if (error is SaveError.Name) error = null
                        },
                        label = { Text("角色名称 *") },
                        singleLine = true,
                        isError = error is SaveError.Name,
                        supportingText = if (error is SaveError.Name) {
                            { Text((error as SaveError.Name).message) }
                        } else null,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = remark,
                        onValueChange = { remark = it },
                        label = { Text("备注（可选）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = {
                        systemPrompt = it
                        if (error is SaveError.Prompt) error = null
                    },
                    label = { Text("系统提示词 *") },
                    minLines = 4,
                    isError = error is SaveError.Prompt,
                    supportingText = if (error is SaveError.Prompt) {
                        { Text((error as SaveError.Prompt).message) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = greeting,
                    onValueChange = { greeting = it },
                    label = { Text("开场白（可选，进入对话时自动发送）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 模型设置 ──
            SectionHeader("模型设置")
            GroupCard {
                // 对话模型选择器（官方 ExposedDropdownMenuBox）。
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

                // 思考模式开关
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
                        onCheckedChange = { thinkingEnabled = it }
                    )
                }
            }

            // ── 你的身份 ──
            SectionHeader("你的身份")
            GroupCard {
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("用户姓名（可选）") },
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
