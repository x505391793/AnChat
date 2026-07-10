package com.anchat.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

/** WeChat palette: white primary, green as accent. */
private val WeChatGreen = Color(0xFF07C160)
private val PageBg = Color(0xFFEDEDED)
private val CardBg = Color.White
private val TextPrimary = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFF9A9A9A)
private val DividerColor = Color(0xFFE5E5E5)
private val FieldBg = Color(0xFFF5F5F5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavHostController) {
    val viewModel: SettingsViewModel = viewModel()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle(initialValue = emptyList())
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val configDisplayPath by viewModel.configDisplayPath.collectAsStateWithLifecycle()
    val defaultUserName by viewModel.defaultUserName.collectAsStateWithLifecycle()
    val defaultUserDesc by viewModel.defaultUserDescription.collectAsStateWithLifecycle()

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) viewModel.setSafUri(uri)
        }
    )

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardBg,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
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
            // ─── 主身份 ───────────────────────────────
            item {
                SectionTitle("主身份")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "角色卡未设置用户身份时，使用此身份进行对话。",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        TextField(
                            value = defaultUserName,
                            onValueChange = viewModel::onUserNameChange,
                            placeholder = { Text("姓名", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors()
                        )
                        Spacer(Modifier.height(10.dp))
                        TextField(
                            value = defaultUserDesc,
                            onValueChange = viewModel::onUserDescChange,
                            placeholder = { Text("身份描述", color = TextSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            minLines = 2,
                            colors = fieldColors()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                "保存身份",
                                color = WeChatGreen,
                                modifier = Modifier.clickable { viewModel.saveIdentity() }
                            )
                        }
                    }
                }
            }

            // ─── 配置文件路径 ─────────────────────────
            item {
                SectionTitle("配置文件路径")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            configDisplayPath.ifBlank { "未设置" },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "选择目录",
                                color = WeChatGreen,
                                modifier = Modifier.clickable { directoryPicker.launch(null) }
                            )
                            Spacer(Modifier.width(20.dp))
                            Text(
                                "恢复默认",
                                color = WeChatGreen,
                                modifier = Modifier.clickable { viewModel.resetConfigPath() }
                            )
                        }
                    }
                }
            }

            // ─── API Key ─────────────────────────────
            item {
                SectionTitle("DeepSeek API Key")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        TextField(
                            value = apiKey,
                            onValueChange = viewModel::onKeyChange,
                            placeholder = { Text("API Key", color = TextSecondary) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(autoCorrect = false),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors()
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "保存 Key",
                                color = WeChatGreen,
                                modifier = Modifier.clickable { viewModel.saveKey() }
                            )
                            Spacer(Modifier.width(20.dp))
                            Text(
                                if (isRefreshing) "刷新中…" else "从 API 拉取模型",
                                color = if (isRefreshing) TextSecondary else WeChatGreen,
                                modifier = Modifier.clickable(enabled = !isRefreshing) {
                                    viewModel.refresh()
                                }
                            )
                        }
                    }
                }
            }

            // ─── 默认模型 ────────────────────────────
            item {
                SectionTitle("默认模型")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column {
                        models.forEachIndexed { index, model ->
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setDefault(model.id) },
                                headlineContent = {
                                    Text(model.name, color = TextPrimary)
                                },
                                supportingContent = {
                                    if (model.description.isNotBlank()) {
                                        Text(model.description, color = TextSecondary)
                                    }
                                },
                                leadingContent = {
                                    RadioButton(
                                        selected = model.isDefault,
                                        onClick = { viewModel.setDefault(model.id) },
                                        colors = RadioButtonDefaults.colors(selectedColor = WeChatGreen)
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = CardBg)
                            )
                            if (index < models.lastIndex) {
                                HorizontalDivider(
                                    color = DividerColor,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = TextSecondary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = FieldBg,
    unfocusedContainerColor = FieldBg,
    disabledContainerColor = FieldBg,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = WeChatGreen,
    focusedPlaceholderColor = TextSecondary,
    unfocusedPlaceholderColor = TextSecondary
)
