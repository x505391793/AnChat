package com.anchat.data.avatar

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 头像加载仓库：UI 读取头像的唯一入口。
 *
 * 数据流：UI 请求 → 查内存缓存 → 命中直接返回 / 未命中 → IO 解码 → 回写缓存 → 返回。
 *
 * 职责单一：只管头像的「缓存 + 解码 + 加载策略」。
 *  - UI 层（CharacterAvatar / AvatarUploadBox）只负责把 ImageBitmap 画出来，
 *    不再持有 BitmapFactory。
 *  - 缓存为应用级 LruCache，跨 composable / LazyColumn item 生命周期存活，
 *    避免「滑出屏幕被回收 → 滑回重新解码」的掉帧。
 *  - 并发去重：同一 path 同时多次请求只解码一次，其余 await 同一结果。
 *  - [peek] 供 UI 同步预查缓存（命中则零闪烁直出，不触发 IO）。
 *
 * 未来扩展（当前不做）：磁盘缩略图缓存、换 Coil/Glide 只改本类。
 *
 * @param scope 应用级协程作用域，承载后台解码任务（生命周期随 Application）。
 */
class AvatarRepository(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)
    private val inflight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private val mutex = Mutex()

    /** 同步预查：只读缓存，不解码、不阻塞。供 UI 渲染首帧使用（命中零闪烁）。 */
    fun peek(path: String?): ImageBitmap? =
        path?.takeIf { it.isNotBlank() }?.let { cache.get(it) }

    /** 异步加载：命中缓存直接返回；未命中则 IO 解码后回写缓存。并发请求自动去重。 */
    suspend fun load(path: String?): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        cache.get(path)?.let { return it }

        val deferred = mutex.withLock {
            // double-check：拿锁期间别人可能已塞进缓存
            cache.get(path)?.let { return@withLock CompletableDeferred(it) }
            // 已有解码任务在跑 → 复用，不重复解码
            inflight.getOrPut(path) {
                scope.async(Dispatchers.IO) {
                    val bmp = decode(path)
                    if (bmp != null) cache.put(path, bmp)
                    mutex.withLock { inflight.remove(path) }
                    bmp
                }
            }
        }
        return deferred.await()
    }

    /** 清缓存：换头像 / 删角色卡后调用。path=null = 全清。 */
    fun evict(path: String? = null) {
        if (path == null) cache.evictAll() else cache.remove(path)
    }

    /** 采样解码本地图片为 ImageBitmap，支持 content:// / file:// / 绝对路径。 */
    private fun decode(path: String): ImageBitmap? = runCatching {
        when {
            path.startsWith("content://") -> {
                val uri = Uri.parse(path)
                val sample = context.contentResolver.openInputStream(uri)?.use { s ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(s, null, o)
                    if (o.outWidth <= 0 || o.outHeight <= 0) return@use 1
                    var sz = 1
                    while (o.outWidth / (sz * 2) >= REQ_SIZE && o.outHeight / (sz * 2) >= REQ_SIZE) sz *= 2
                    sz
                } ?: 1
                context.contentResolver.openInputStream(uri)?.use { s ->
                    BitmapFactory.decodeStream(s, null,
                        BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
            path.startsWith("file://") -> decodeSampled(Uri.parse(path).path ?: path)
            else -> decodeSampled(path)
        }?.asImageBitmap()
    }.getOrNull()

    /** 采样解码本地文件，限制最大边长避免 OOM（头像用 256px 足够清晰）。 */
    private fun decodeSampled(path: String): android.graphics.Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        val rawW = opts.outWidth.coerceAtLeast(1)
        val rawH = opts.outHeight.coerceAtLeast(1)
        var sample = 1
        while (rawH / (sample * 2) >= REQ_SIZE && rawW / (sample * 2) >= REQ_SIZE) {
            sample *= 2
        }
        opts.inSampleSize = sample
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, opts)
    }

    companion object {
        private const val MAX_ENTRIES = 64
        private const val REQ_SIZE = 256
    }
}
