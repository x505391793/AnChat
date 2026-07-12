package com.anchat.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一头像组件（仿微信圆角方形）：
 * - 有上传的本地头像图 → 采样解码后显示图片；
 * - 无图 / URL（无法离线解码）→ 回退为「原名」首字母 + 稳定配色（默认头像）。
 * 供通讯录、角色名片、对话列表、聊天页共用，保证跨页一致。
 *
 * @param name 默认头像的「种子」：必须传角色**原名**（character.name / charName），
 *            用于计算首字母与配色。严禁传备注(remark)或标题(title)，否则同一角色
 *            在不同列表颜色/首字母会不一致。
 */
@Composable
fun CharacterAvatar(
    name: String,
    avatarPath: String?,
    size: Dp,
    corner: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(avatarPath) { loadAvatarBitmap(avatarPath, context) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(if (bitmap == null) avatarColor(name) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = name,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = avatarInitial(name),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * 本地头像图采样解码为 ImageBitmap。
 * 支持三种来源：应用内部绝对路径（复制后的副本，持久）、content://（Photo Picker / 系统媒体）、file://。
 * 无图 / 解码失败 → null（由调用方回退默认头像）。
 */
fun loadAvatarBitmap(path: String?, context: Context, reqSize: Int = 256): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        when {
            path.startsWith("content://") -> {
                val uri = Uri.parse(path)
                val sample = context.contentResolver.openInputStream(uri)?.use { s ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(s, null, o)
                    if (o.outWidth <= 0 || o.outHeight <= 0) return@use 1
                    var sz = 1
                    while (o.outWidth / (sz * 2) >= reqSize && o.outHeight / (sz * 2) >= reqSize) sz *= 2
                    sz
                } ?: 1
                context.contentResolver.openInputStream(uri)?.use { s ->
                    BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
            path.startsWith("file://") -> decodeSampledBitmap(Uri.parse(path).path ?: path, reqSize, reqSize)
            else -> decodeSampledBitmap(path, reqSize, reqSize)
        }?.asImageBitmap()
    }.getOrNull().also {
        if (it == null) Log.e("ANCHAT_AVATAR", "loadAvatarBitmap FAILED: $path")
        else Log.d("ANCHAT_AVATAR", "loadAvatarBitmap OK: $path (${it.width}x${it.height})")
    }
}

/** 采样解码本地图片，限制最大边长避免 OOM（头像用 256px 足够清晰） */
private fun decodeSampledBitmap(path: String, reqW: Int, reqH: Int): android.graphics.Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    // 注意：inJustDecodeBounds 模式下 decodeFile 必然返回 null，仅填充 outWidth/outHeight。
    // 不能用返回值判空（否则永远 return null），必须以 out 尺寸是否有效为准。
    BitmapFactory.decodeFile(path, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    val rawH = opts.outHeight.coerceAtLeast(1)
    val rawW = opts.outWidth.coerceAtLeast(1)
    var sample = 1
    while (rawH / (sample * 2) >= reqH && rawW / (sample * 2) >= reqW) {
        sample *= 2
    }
    opts.inSampleSize = sample
    opts.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(path, opts)
}
