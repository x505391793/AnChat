package com.anchat.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.ui.main.LocalApp
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
 * 角色卡编辑页（新建 / 编辑，仿微信资料编辑风格）：
 * 分组卡片式布局，微信绿仅作点缀（开关 / 保存）。
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

    // 编辑模式：加载并回填现有角色（主键 id 唯一，允许重名，不做查重）
    LaunchedEffect(editId) {
        if (editId >= 0) {
            val e = app.localRepository.getCharacter(editId)
            existing = e
            if (e != null) {
                name = e.name
                avatar = e.avatar ?: ""
                description = e.description ?: ""
                remark = e.remark ?: ""
                systemPrompt = e.systemPrompt
                greeting = e.greeting ?: ""
                userAvatar = e.userAvatar ?: ""
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── 角色信息 ──
            SectionHeader("角色信息")
            GroupCard {
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
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = avatar,
                    onValueChange = { avatar = it },
                    label = { Text("角色头像（路径或 URL，可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("角色简介（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )

                // 备注：用户在通讯录 / 对话列表中对该角色的显示名（优先于角色名称）
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注（可选，显示在通讯录与对话列表）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 对话设置 ──
            SectionHeader("对话设置")

            GroupCard {
                // 对话模型选择器（ExposedDropdownMenuBox，官方下拉组件）。
                // 之前的 Box+clickable 写法里，OutlinedTextField 内部自带 clickable 会吞掉点击，
                // 父 Box 永远收不到，导致下拉点不开；改用官方 menuAnchor 彻底解决。
                // 按 id 或 name 匹配：兼容「模型以名称存入 modelId」的旧数据。
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

            // ── 用户身份（非必填） ──
            SectionHeader("用户身份（非必填）")
            GroupCard {
                OutlinedTextField(
                    value = userAvatar,
                    onValueChange = { userAvatar = it },
                    label = { Text("用户头像（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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

/** 分组标题（微信式灰字小标题） */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 卡片容器：圆角 + 语义背景，内部字段纵向排列（微信分组列表风格） */
@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
