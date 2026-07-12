package com.anchat.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.anchat.ui.theme.CharacterAvatar
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.ui.main.LocalApp
import kotlin.math.absoluteValue
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(navController: NavHostController) {
    val app = LocalApp.current
    val characters by app.localRepository.observeCharacters()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("通讯录", style = MaterialTheme.typography.titleMedium) }) }) { padding ->
        // 按显示名首字母分组：英文直接取；中文取拼音首字母；其余归 #；参考微信通讯录吸顶字母头
        val sections = remember(characters) {
            characters.groupBy { char ->
                    val name = char.remark?.takeIf { it.isNotBlank() } ?: char.name
                    groupLetter(name)
                }.entries.sortedWith(compareBy { if (it.key == "#") "ZZ" else it.key })
                .map { (letter, list) ->
                    letter to list.sortedBy {
                        sortKey(it.remark?.takeIf { r -> r.isNotBlank() } ?: it.name)
                    }
                }
        }

        // 扁平化为「字母头 / 联系人」行，便于手动实现吸顶
        val rows = remember(sections) {
            buildList<ContactRow> {
                // 顶部固定入口「新的朋友」：不参与拼音排序，始终置顶
                add(ContactRow.NewFriend)
                sections.forEach { (letter, list) ->
                    add(ContactRow.Header(letter))
                    list.forEach { add(ContactRow.Item(it)) }
                }
            }
        }

        val listState = rememberLazyListState()

        // 当前吸顶的字母：取首个可见项之前（含）最后一个字母头
        val stickyLetter by remember(rows, listState) {
            derivedStateOf {
                val visible = listState.firstVisibleItemIndex
                var letter = ""
                for (i in 0..visible.coerceAtMost(rows.lastIndex)) {
                    val row = rows[i]
                    if (row is ContactRow.Header) letter = row.letter
                }
                letter
            }
        }
        // 当首个可见项本身就是「完全露出的字母头」时不重复显示吸顶条
        val firstVisibleIsFullyShownHeader by remember(rows, listState) {
            derivedStateOf {
                val visible = listState.firstVisibleItemIndex
                val row = rows.getOrNull(visible)
                row is ContactRow.Header && listState.firstVisibleItemScrollOffset == 0
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState, modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(
                    rows, key = {
                        when (it) {
                            is ContactRow.NewFriend -> "new_friend"
                            is ContactRow.Header -> "hdr_${it.letter}"
                            is ContactRow.Item -> "item_${it.char.id}"
                        }
                    }) { row ->
                    when (row) {
                        is ContactRow.NewFriend -> NewFriendRow(
                            onClick = { navController.navigate("character/create") }
                        )
                        is ContactRow.Header -> LetterHeader(row.letter)
                        is ContactRow.Item -> CharacterItem(
                            character = row.char, onClick = {
                                navController.navigate("character/${row.char.id}")
                            })
                    }
                }

                if (characters.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无角色卡", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (stickyLetter.isNotEmpty() && !firstVisibleIsFullyShownHeader) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(padding)
                ) {
                    LetterHeader(stickyLetter)
                }
            }
        }
    }
}

/** 通讯录列表行：顶部固定入口 / 字母分区头 / 单个联系人 */
private sealed interface ContactRow {
    data object NewFriend : ContactRow
    data class Header(val letter: String) : ContactRow
    data class Item(val char: CharacterEntity) : ContactRow
}

@Composable
private fun LetterHeader(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    )
}

@Composable
private fun NewFriendRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PersonAdd,
                contentDescription = null,
                tint = Color.White
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            "新的朋友",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CharacterItem(
    character: CharacterEntity, onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 名称：备注优先，其次角色名（仅用于文字标签）
        val displayName = character.remark?.takeIf { it.isNotBlank() } ?: character.name
        // 头像：有上传图显示图片，否则按「原名」首字母+配色（默认头像）
        CharacterAvatar(
            name = character.name,
            avatarPath = character.avatar,
            size = 48.dp,
            corner = 8.dp
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!character.description.isNullOrBlank()) {
                Text(
                    character.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 拼音转换格式：小写、不带声调（用于分组首字母与排序键） */
private val pinyinFormat = HanyuPinyinOutputFormat().apply {
    setCaseType(HanyuPinyinCaseType.LOWERCASE)
    setToneType(HanyuPinyinToneType.WITHOUT_TONE)
}

/** CJK 统一表意文字基本区 */
private const val CJK_START = 0x4E00
private const val CJK_END = 0x9FFF

/** 取名称首字母分组键：英文直接取；中文取拼音首字母；其余（符号/数字/表情等）归 # */
private fun groupLetter(name: String): String {
    val ch = name.firstOrNull() ?: return "#"
    return when {
        ch in 'A'..'Z' || ch in 'a'..'z' -> ch.uppercaseChar().toString()
        ch.code in CJK_START..CJK_END -> {
            PinyinHelper.toHanyuPinyinStringArray(ch, pinyinFormat)
                ?.firstOrNull()
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.takeIf { it in 'A'..'Z' }
                ?.toString() ?: "#"
        }
        else -> "#"
    }
}

/** 排序键：中文逐字转全拼、其余原样，整体转小写，得到拼音序（如 安 an 排在 昂 ang 之前） */
private fun sortKey(name: String): String = buildString {
    name.forEach { ch ->
        if (ch.code in CJK_START..CJK_END) {
            append(PinyinHelper.toHanyuPinyinStringArray(ch, pinyinFormat)?.firstOrNull() ?: ch)
        } else {
            append(ch)
        }
    }
}.lowercase()
