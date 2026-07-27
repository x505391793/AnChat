package com.anchat.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anchat.ui.main.LocalApp

/**
 * 统一头像组件（仿微信圆角方形）：
 * - 有上传的本地头像图 → 经 [com.anchat.data.avatar.AvatarRepository] 异步解码后显示；
 * - 无图 / URL（无法离线解码）→ 回退为「原名」首字母 + 稳定配色（默认头像）。
 *
 * 供通讯录、角色名片、对话列表、聊天页共用，保证跨页一致。
 *
 * 纯渲染组件：不持有 BitmapFactory，解码与缓存全部下沉到 AvatarRepository。
 * 缓存命中时 [peek] 同步直出（零闪烁）；未命中先显示默认首字母，解码完成后自动刷新。
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
    val repo = LocalApp.current.avatarRepository
    // 同步预查缓存：命中则首帧直出（零闪烁）；未命中 initialValue=null 先显示默认首字母
    val initial = remember(avatarPath) { repo.peek(avatarPath) }
    val bitmap by produceState<ImageBitmap?>(initial, avatarPath) {
        value = repo.load(avatarPath)
    }
    // produceState 是委托属性，无法 smart cast 到非空，取局部变量以供智能转换
    val bmp = bitmap

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(if (bmp == null) avatarColor(name) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
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
