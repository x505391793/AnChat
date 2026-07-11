package com.anchat.ui.theme

import android.graphics.BitmapFactory
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一头像组件（仿微信圆角方形）：
 * - 有上传的本地头像图 → 采样解码后显示图片；
 * - 无图 / URL（无法离线解码）→ 回退为「原名」首字母 + 稳定配色（默认头像）。
 * 供通讯录、角色名片、编辑页共用，保证跨页一致。
 */
@Composable
fun CharacterAvatar(
    name: String,
    avatarPath: String?,
    size: Dp,
    corner: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(avatarPath) { loadAvatarBitmap(avatarPath) }

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
                modifier = Modifier.size(size)
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

/** 本地头像图采样解码为 ImageBitmap；无图 / URL / 解码失败 → null（由调用方回退默认头像） */
fun loadAvatarBitmap(path: String?, reqSize: Int = 256): ImageBitmap? {
    val p = path?.takeIf { it.isNotBlank() && !it.startsWith("http") && !it.startsWith("content://") }
        ?: return null
    return runCatching { decodeSampledBitmap(p, reqSize, reqSize)?.asImageBitmap() }.getOrNull()
}

/** 采样解码本地图片，限制最大边长避免 OOM（头像用 256px 足够清晰） */
private fun decodeSampledBitmap(path: String, reqW: Int, reqH: Int): android.graphics.Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, opts) ?: return null
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
