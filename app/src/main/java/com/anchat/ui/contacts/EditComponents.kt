package com.anchat.ui.contacts

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anchat.ui.theme.loadAvatarBitmap
import java.io.File
import java.util.UUID

/**
 * 头像上传框（角色卡编辑页 / 对话内编辑页共用，视觉一致）：
 * 有图显示预览（可清除），无图显示「上传」占位（点击选图）。
 */
@Composable
fun AvatarUploadBox(
    name: String,
    avatarPath: String?,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    val bitmap = remember(avatarPath) { loadAvatarBitmap(avatarPath) }
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (bitmap == null) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                modifier = Modifier.fillMaxSize()
            )
            // 右上角清除按钮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(onClick = onClear)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "清除头像",
                    tint = Color.White
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "上传头像",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 分组标题（微信式灰字小标题，左对齐） */
@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 卡片容器：圆角 + 语义背景，内部字段纵向排列（微信分组列表风格） */
@Composable
fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
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

/** 图片选择器：选中本地图 → 复制到应用内部存储，成功时回传本地绝对路径 */
@Composable
fun rememberAvatarPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        copyUriToAppStorage(context, uri)?.let { onPicked(it) }
    }
    return { launcher.launch("image/*") }
}

/** 将选中的图片 URI 复制到应用内部存储，返回本地绝对路径（可离线解码、持久保留） */
fun copyUriToAppStorage(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val dir = File(context.filesDir, "avatars").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { out -> input.copyTo(out) }
            file.absolutePath
        }
    }.getOrNull()
}
