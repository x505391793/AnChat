package com.anchat.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.anchat.ui.contacts.AvatarUploadBox
import com.anchat.ui.contacts.GroupCard
import com.anchat.ui.contacts.SectionHeader
import com.anchat.ui.contacts.rememberAvatarPicker
import com.anchat.ui.main.LocalApp

/**
 * 个人资料编辑页（仿「我 → 个人信息」）：
 * 与新增角色卡页视觉一致——居中头像上传框 + 基本信息卡（名字 / 性别 / 微信号 / 身份描述）。
 * 保存写入全局主身份配置（AppConfig），对话时回退到此处的值。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(navController: NavHostController) {
    val app = LocalApp.current

    var name by remember { mutableStateOf(app.configManager.getDefaultUserName()) }
    var avatar by remember { mutableStateOf(app.configManager.getDefaultUserAvatar()) }
    var gender by remember { mutableStateOf(app.configManager.getDefaultUserGender()) }
    var wechatId by remember { mutableStateOf(app.configManager.getDefaultUserWechatId()) }
    var description by remember { mutableStateOf(app.configManager.getDefaultUserDescription()) }
    var genderExpanded by remember { mutableStateOf(false) }

    val pickAvatar = rememberAvatarPicker { path -> avatar = path }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("个人信息") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        app.configManager.saveDefaultIdentity(
                            userName = name,
                            description = description,
                            avatar = avatar,
                            gender = gender,
                            wechatId = wechatId
                        )
                        navController.popBackStack()
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
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 居中头像上传框（与角色卡页一致） ──
            AvatarUploadBox(
                name = name.ifBlank { "我" },
                avatarPath = avatar.ifBlank { null },
                onPick = pickAvatar,
                onClear = { avatar = "" }
            )

            // ── 基本信息卡 ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                SectionHeader("基本信息")
                Spacer(Modifier.height(8.dp))
                GroupCard {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 性别：官方 ExposedDropdownMenuBox
                    val genderLabel = gender.ifBlank { "不设置" }
                    ExposedDropdownMenuBox(
                        expanded = genderExpanded,
                        onExpandedChange = { genderExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = genderLabel,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("性别") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = genderExpanded,
                            onDismissRequest = { genderExpanded = false }
                        ) {
                            listOf("", "男", "女").forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(if (g.isBlank()) "不设置" else g) },
                                    onClick = {
                                        gender = g
                                        genderExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = wechatId,
                        onValueChange = { wechatId = it },
                        label = { Text("微信号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("身份描述") },
                        placeholder = { Text("向角色介绍你自己，例如：我喜欢坦诚直接的交流") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6
                    )
                }
            }
        }
    }
}
