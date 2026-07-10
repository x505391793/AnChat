package com.anchat.ui.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.ui.main.LocalApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterEditScreen(navController: NavHostController) {
    val app = LocalApp.current
    val characters by app.localRepository.observeCharacters()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }
    var greeting by remember { mutableStateOf("") }

    var userAvatar by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }
    var userDescription by remember { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("新建角色卡") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        when {
                            name.isBlank() -> error = "请填写角色名称"
                            systemPrompt.isBlank() -> error = "请填写系统提示词"
                            characters.any { it.name == name.trim() } ->
                                error = "已存在同名角色"
                            else -> {
                                val entity = CharacterEntity(
                                    name = name.trim(),
                                    avatar = avatar.trim().ifBlank { null },
                                    description = description.trim().ifBlank { null },
                                    systemPrompt = systemPrompt.trim(),
                                    greeting = greeting.trim().ifBlank { null },
                                    userAvatar = userAvatar.trim().ifBlank { null },
                                    userName = userName.trim().ifBlank { null },
                                    userDescription = userDescription.trim().ifBlank { null }
                                )
                                scope.launch {
                                    app.localRepository.insertCharacter(entity)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("角色信息", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("角色名称 *") },
                singleLine = true,
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

            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("系统提示词 *") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = greeting,
                onValueChange = { greeting = it },
                label = { Text("开场白（可选，进入对话时自动发送）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Text("用户身份（非必填）", style = MaterialTheme.typography.titleMedium)

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

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
