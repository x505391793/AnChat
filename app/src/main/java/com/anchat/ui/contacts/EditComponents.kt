package com.anchat.ui.contacts

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
    val context = LocalContext.current
    val bitmap = remember(avatarPath) { loadAvatarBitmap(avatarPath, context) }
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
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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

/**
 * 图片选择器：优先使用系统 Photo Picker（PickVisualMedia）。
 * 优势：API 33+ 系统级组件、免权限、不依赖任何第三方 app —— 在无 GMS 的模拟器或
 * 精简 ROM 上也能稳定调起（GetContent 在这些环境常因无可用处理者而不回调）。
 * 选中后复制到应用内部存储，返回本地绝对路径（离线可解码、进程重启后仍可读）。
 * API < 33 无系统 Photo Picker，契约会回退为 GetContent，需 READ_EXTERNAL_STORAGE
 * 才能读取选中图片，因此先申请权限再启动。
 */
@Composable
fun rememberAvatarPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: run {
            Log.d("ANCHAT_AVATAR", "picker returned NULL (user cancelled or no handler)")
            return@rememberLauncherForActivityResult
        }
        Log.d("ANCHAT_AVATAR", "picker returned uri=$uri scheme=${uri.scheme}")
        // 对回退场景得到的 content:// 尝试持久化读取授权（部分系统需要），失败则忽略
        if (uri.scheme == "content") {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Photo Picker 返回的 URI 不保证支持持久化授权，复制已即时完成，忽略即可
            }
        }
        val copied = copyUriToAppStorage(context, uri)
        onPicked(copied ?: uri.toString())
    }

    // API < 33 回退 GetContent 时需要存储读取权限
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("ANCHAT_AVATAR", "storage permission granted=$granted")
        if (granted) pickLauncher.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    return {
        Log.d("ANCHAT_AVATAR", "launchAvatarPicker invoked; sdk=${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: Photo Picker 免权限，直接调起系统选择器
            Log.d("ANCHAT_AVATAR", "-> launching PhotoPicker (PickVisualMedia)")
            pickLauncher.launch(
                PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    .build()
            )
        } else {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
                pickLauncher.launch(
                    PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build()
                )
            } else {
                permissionLauncher.launch(perm)
            }
        }
    }
}

/**
 * 将选中的图片 URI 复制到应用内部存储，返回本地绝对路径（可离线解码、持久保留）。
 * 兼容 content:// 与 file:// 两种 scheme（部分设备 / 文件管理器会直接返回 file://，
 * 此时 contentResolver.openInputStream 会失败，需直接按文件读取）。
 */
fun copyUriToAppStorage(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "avatars").apply { if (!exists()) mkdirs() }
        val ext = when (context.contentResolver.getType(uri)?.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "img"
        }
        val file = File(dir, "${UUID.randomUUID()}.$ext")
        val input = when (uri.scheme) {
            "file" -> uri.path?.let { File(it).inputStream() }
            else -> context.contentResolver.openInputStream(uri)
        } ?: run {
            Log.e("AnChat", "copyUriToAppStorage: cannot open input for $uri")
            return null
        }
        input.use { inp ->
            file.outputStream().use { out -> inp.copyTo(out) }
        }
        if (file.length() == 0L) {
            file.delete()
            Log.e("AnChat", "copyUriToAppStorage: copied file is empty")
            return null
        }
        file.absolutePath
    } catch (e: Exception) {
        Log.e("AnChat", "copyUriToAppStorage failed for $uri", e)
        null
    }
}
