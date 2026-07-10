package com.anchat.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val viewModel: SettingsViewModel = viewModel()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle(initialValue = emptyList())
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val configDisplayPath by viewModel.configDisplayPath.collectAsStateWithLifecycle()

    // Directory picker launcher
    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                viewModel.setSafUri(uri)
            }
        }
    )

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = {
            if (message != null) Snackbar { Text(message!!) }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── 配置文件路径 ────────────────────────────────
            Text("配置文件路径", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = configDisplayPath,
                onValueChange = { /* read-only display, changes via picker */ },
                label = { Text("当前路径") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { directoryPicker.launch(null) }) { Text("选择目录") }
                Button(onClick = viewModel::resetConfigPath) { Text("恢复默认") }
            }

            // ─── API Key ────────────────────────────────────
            Text("DeepSeek API Key", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = apiKey,
                onValueChange = viewModel::onKeyChange,
                label = { Text("API Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrect = false),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveKey) { Text("保存 Key") }
                Button(
                    onClick = viewModel::refresh,
                    enabled = !isRefreshing
                ) { Text(if (isRefreshing) "刷新中…" else "从 API 拉取模型") }
            }

            // ─── 默认模型 ───────────────────────────────────
            Text("默认模型", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(models, key = { it.id }) { model ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setDefault(model.id) },
                        headlineContent = { Text(model.name) },
                        supportingContent = {
                            if (model.description.isNotBlank()) Text(model.description)
                        },
                        leadingContent = {
                            RadioButton(
                                selected = model.isDefault,
                                onClick = { viewModel.setDefault(model.id) }
                            )
                        }
                    )
                }
            }
        }
    }
}
