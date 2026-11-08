package com.anchat.data.engine

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BehaviorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<BehaviorEntity>)

    @Query("SELECT * FROM behaviors WHERE status = 0 ORDER BY excu_time ASC")
    suspend fun getDue(): List<BehaviorEntity>

    @Query("UPDATE behaviors SET status = 1 WHERE id = :id")   // 0 → 1（已执行未读）
    suspend fun markCompleted(id: String)

    @Query("UPDATE behaviors SET status = 2 WHERE id = :id")   // 1 → 2（已读）
    suspend fun markRead(id: String)

    /** 对话打开时：把该对话下所有「已执行未读」行为翻为已读（status 1 → 2） */
    @Query(
        """UPDATE behaviors SET status = 2
           WHERE status = 1 AND batch_id IN (SELECT id FROM raw_replies WHERE conversation_id = :conversationId)"""
    )
    suspend fun markAllReadByConversation(conversationId: String)

    /** 按 conversation_id 直接翻已读（覆盖 raw_replies 联查不到的历史/孤儿行为） */
    @Query("UPDATE behaviors SET status = 2 WHERE status = 1 AND conversation_id = :conversationId")
    suspend fun markReadByConversationId(conversationId: String)

    /** 清理用：取所有「已执行未读」行为的不同 conversation_id（含空串/非数字孤儿值） */
    @Query("SELECT DISTINCT conversation_id FROM behaviors WHERE status = 1")
    suspend fun getUnreadConversationIds(): List<String>

    /** 按整回合键 batch_id 整批删除行为（用户 text + AI speech/emotion/leave） */
    @Query("DELETE FROM behaviors WHERE batch_id = :batchId")
    suspend fun deleteByBatchId(batchId: String)

    /** 按对话 id 整清（删对话时联动） */
    @Query("DELETE FROM behaviors WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    /** 按主键单条删除（长按气泡删这一句） */
    @Query("DELETE FROM behaviors WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 按主键取单条（判断是否为用户行以联动删 raw） */
    @Query("SELECT * FROM behaviors WHERE id = :id")
    suspend fun getById(id: String): BehaviorEntity?

    /**
     * 取某对话下已完成（已到点）的行为，按执行时间升序。
     * 行为表经 raw_replies.conversation_id 关联到对话；调度器翻 completed 后由 UI 观察此流按时序渲染。
     */
    @Query(
        """SELECT b.* FROM behaviors b
           JOIN raw_replies r ON b.batch_id = r.id
           WHERE r.conversation_id = :conversationId AND b.status >= 1
           ORDER BY b.excu_time ASC"""
    )
    fun observeCompletedByConversation(conversationId: String): Flow<List<BehaviorEntity>>

    /** 同上，但一次性返回（用于进入对话时的初始加载） */
    @Query(
        """SELECT b.* FROM behaviors b
           JOIN raw_replies r ON b.batch_id = r.id
           WHERE r.conversation_id = :conversationId AND b.status >= 1
           ORDER BY b.excu_time ASC"""
    )
    suspend fun getCompletedByConversation(conversationId: String): List<BehaviorEntity>

    /**
     * 取某对话下「早于 beforeExcuTime」的已完成行为，最多 limit 条，按执行时间降序返回（最新在前）。
     * 用于进入对话只加载最近一页、上滑再增量取更早历史——避免长对话一次性全量加载导致卡顿。
     */
    @Query(
        """SELECT b.* FROM behaviors b
           JOIN raw_replies r ON b.batch_id = r.id
           WHERE r.conversation_id = :conversationId AND b.status >= 1 AND b.excu_time < :beforeExcuTime
           ORDER BY b.excu_time DESC
           LIMIT :limit"""
    )
    suspend fun getCompletedBefore(conversationId: String, beforeExcuTime: Long, limit: Int): List<BehaviorEntity>

    /** 备份导出：分页取全表，避免长历史一次性载入内存 */
    @Query("SELECT * FROM behaviors ORDER BY excu_time ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedAll(limit: Int, offset: Int): List<BehaviorEntity>

    /** 备份去重用：只取主键与指纹所需字段（status 是运行时状态，不参与指纹） */
    @Query("SELECT id, batch_id, `order`, type, role, content, duration, excu_time, conversation_id FROM behaviors LIMIT :limit OFFSET :offset")
    suspend fun getPagedIdentity(limit: Int, offset: Int): List<BehaviorIdentity>

    /** 未读红点：按对话统计「已执行未读」行为（status=1） */
    @Query(
        "SELECT conversation_id AS conversationId, COUNT(*) AS cnt " +
            "FROM behaviors WHERE status = 1 GROUP BY conversation_id"
    )
    fun observeUnread(): Flow<List<BehaviorUnread>>


    /** 取对话下全部排队消息（status=-1），按时间序 */
    @Query(
        "SELECT * FROM behaviors WHERE conversation_id = :conversationId AND status = -1 ORDER BY excu_time ASC"
    )
    suspend fun getQueuedByConversation(conversationId: String): List<BehaviorEntity>

    /** 将对话下全部排队消息标记为已发送：统一 batchId + status=READ */
    @Query(
        "UPDATE behaviors SET batch_id = :batchId, status = 2 WHERE conversation_id = :conversationId AND status = -1"
    )
    suspend fun markQueuedAsSent(conversationId: String, batchId: String)

    /** 气泡编辑：只改展示文本，不动批次/时间/状态 */
    @Query("UPDATE behaviors SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    /**
     * 重新生成：删掉「目标批次本身 + 该时间点之后」的全部行为。
     * 用 batch_id 兜底同一批次内 excu_time 更早的兄弟行为（如 speech 已出、emotion 未出），
     * 避免留下找不到 raw 的孤儿行为（界面上不可见但会污染数据）。
     */
    @Query(
        "DELETE FROM behaviors WHERE conversation_id = :conversationId AND (batch_id = :batchId OR excu_time >= :cutoff)"
    )
    suspend fun deleteFrom(conversationId: String, batchId: String, cutoff: Long)
}

/** 备份去重用最小字段行：主键 + 参与指纹的列（不含运行时 status） */
data class BehaviorIdentity(
    val id: String,
    @ColumnInfo(name = "batch_id") val batchId: String,
    @ColumnInfo(name = "order") val order: Int,
    val type: String,
    val role: String,
    val content: String,
    val duration: String?,
    @ColumnInfo(name = "excu_time") val excuTime: Long,
    @ColumnInfo(name = "conversation_id") val conversationId: String
)

/** 未读计数行：conversation_id（字符串，与 behaviors.conversation_id 同值）+ 数量 */
data class BehaviorUnread(
    @ColumnInfo(name = "conversationId") val conversationId: String,
    @ColumnInfo(name = "cnt") val cnt: Int
)
