package com.anchat.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.anchat.data.config.ModelConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManageScreen(navController: NavHostController) {
    val viewModel: ModelManageViewModel = viewModel()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val apiUrl by viewModel.apiUrl.collectAsStateWithLifecycle()
    val fetched by viewModel.fetched.collectAsStateWithLifecycle()
    val isFetching by viewModel.isFetching.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle(initialValue = emptyList())
    val defaultModelId by viewModel.defaultModelId.collectAsStateWithLifecycle(initialValue = null)
    val chatModelId by viewModel.repo.observeChatModelId().collectAsStateWithLifecycle(initialValue = null)
    val realConvModelId by viewModel.repo.observeRealConversationModelId().collectAsStateWithLifecycle(initialValue = null)
    val message by viewModel.message.collectAsStateWithLifecycle()

    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500L)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("模型管理", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            if (message != null) Snackbar { Text(message!!) }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ─── 步骤①：输入 apiKey + apiUrl ─────────
            item {
                SectionTitle("添加模型")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "填写服务商凭证后拉取可用模型，可多选并添加。添加的模型会连同 Key 一起存入配置文件。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = apiKey,
                            onValueChange = viewModel::onKeyChange,
                            placeholder = { Text("API Key", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(autoCorrect = false),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors()
                        )
                        Spacer(Modifier.height(10.dp))
                        TextField(
                            value = apiUrl,
                            onValueChange = viewModel::onUrlChange,
                            placeholder = { Text("API 地址（基地址）", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            keyboardOptions = KeyboardOptions(autoCorrect = false),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                if (isFetching) "拉取中…" else "拉取模型",
                                color = if (isFetching) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(enabled = !isFetching) { viewModel.fetch() }
                            )
                        }
                    }
                }
            }

            // ─── 步骤②+③：勾选拉取到的模型 ─────────
            if (fetched.isNotEmpty()) {
                item {
                    SectionTitle("拉取到的模型（可多选）")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column {
                            fetched.forEachIndexed { index, m ->
                                val checked = selectedIds.contains(m.id)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedIds = selectedIds.toMutableSet().apply {
                                                if (checked) remove(m.id) else add(m.id)
                                            }.toSet()
                                        }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            selectedIds = selectedIds.toMutableSet().apply {
                                                if (it) add(m.id) else remove(m.id)
                                            }.toSet()
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(m.name.ifBlank { m.id }, color = MaterialTheme.colorScheme.onSurface)
                                        Text(m.id, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                if (index < fetched.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "添加所选（${selectedIds.size}）",
                            color = if (selectedIds.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(enabled = selectedIds.isNotEmpty()) {
                                viewModel.addSelected(selectedIds)
                                selectedIds = emptySet()
                            }
                        )
                    }
                }
            }

            // ─── 已添加模型（来自配置文件） ─────────
            item {
                SectionTitle("已添加模型")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    if (models.isEmpty()) {
                        Text(
                            "还没有模型，先在上方拉取并添加。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Column {
                            models.forEachIndexed { index, model ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(model.name.ifBlank { model.id }, color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            "${model.id} · ${if (model.apiKey.isNotBlank()) "Key ${maskKey(model.apiKey)}" else "未配置 Key"} · ${model.apiUrl.ifBlank { "无地址" }}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "删除",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.clickable { viewModel.removeModel(model.id) }
                                    )
                                }
                                if (index < models.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── 模型分配（两个独立下拉） ─────────
            item {
                SectionTitle("模型分配")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ModelPicker(
                            title = "聊天 AI 模型",
                            hint = "角色/对话未单独指定模型时使用",
                            selectedId = chatModelId,
                            models = models,
                            onSelect = { viewModel.setChatModel(it) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ModelPicker(
                            title = "真实对话管理 AI",
                            hint = "选定后才可在角色卡开启「真实对话」；开启后原始回复会发此模型做行为拆解",
                            selectedId = realConvModelId,
                            models = models,
                            onSelect = { viewModel.setRealConversationModel(it) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/**
 * 单个模型下拉框：选项为「未选择」+ 已添加模型列表。两个下拉（聊天模型 / 真实对话管理）
 * 相互独立，分别由 ViewModel 写入配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(
    title: String,
    hint: String,
    selectedId: String?,
    models: List<ModelConfig>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = models.firstOrNull { it.id == selectedId }?.name
        ?: models.firstOrNull { it.id == selectedId }?.id
        ?: "未选择"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(2.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("未选择") },
                    onClick = { onSelect(null); expanded = false }
                )
                models.forEach { m ->
                    DropdownMenuItem(
                        text = { Text(m.name.ifBlank { m.id }) },
                        onClick = { onSelect(m.id); expanded = false }
                    )
                }
            }
        }
    }
}

private fun maskKey(key: String): String {
    return if (key.length <= 4) "****" else "****${key.takeLast(4)}"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
)
