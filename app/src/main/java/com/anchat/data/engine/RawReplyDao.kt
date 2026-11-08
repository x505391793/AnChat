package com.anchat.data.engine

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RawReplyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(raw: RawReplyEntity)

    /** 按 id 删除单条原始回复 */
    @Query("DELETE FROM raw_replies WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 按主键取单条（气泡编辑/重新生成定位批次） */
    @Query("SELECT * FROM raw_replies WHERE id = :id")
    suspend fun getById(id: String): RawReplyEntity?

    /** 气泡编辑：同步改上下文原文（否则界面改了、下次请求仍发旧文本） */
    @Query("UPDATE raw_replies SET content = :content WHERE id = :id")
    suspend fun updateContent(id: String, content: String)

    /** 重新生成：删掉目标批次本身 + 该时间点之后的所有原始回复 */
    @Query(
        "DELETE FROM raw_replies WHERE conversation_id = :conversationId AND (id = :rawId OR created_at >= :cutoff)"
    )
    suspend fun deleteFrom(conversationId: String, rawId: String, cutoff: Long)

    /** 取本对话最后一条用户原文（重新生成时作为输入，确认末尾确实是用户回合） */
    @Query(
        """SELECT * FROM raw_replies
           WHERE conversation_id = :conversationId AND role = 'user' AND kind <> 'decomp'
           ORDER BY created_at DESC LIMIT 1"""
    )
    suspend fun getLastUser(conversationId: String): RawReplyEntity?

    /** 按对话 id 整清（删对话时联动） */
    @Query("DELETE FROM raw_replies WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: String)

    /**
     * 拼请求上下文：取本对话下 user/assistant 的「真实对话」原文（排除 decomp 拆解源数据），
     * 按发送时间升序。取代旧 messages 表读 hidden JSON。
     */
    @Query(
        """SELECT * FROM raw_replies
           WHERE conversation_id = :conversationId
             AND role IN ('user','assistant')
             AND kind <> 'decomp'
           ORDER BY created_at ASC"""
    )
    suspend fun getHistory(conversationId: String): List<RawReplyEntity>

    /** 备份导出：分页取全表（按时间升序，offset 续拉），避免长历史一次性载入内存 */
    @Query("SELECT * FROM raw_replies ORDER BY created_at ASC LIMIT :limit OFFSET :offset")
    suspend fun getPagedAll(limit: Int, offset: Int): List<RawReplyEntity>

    /** 备份导入：批量插入 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RawReplyEntity>)

    /** 备份去重用：只取主键与指纹所需字段，避免整表实体驻留内存 */
    @Query("SELECT id, conversation_id, role, content, reasoning_content, kind, created_at FROM raw_replies LIMIT :limit OFFSET :offset")
    suspend fun getPagedIdentity(limit: Int, offset: Int): List<RawIdentity>

    /** 日志页分页：最新在前（created_at 倒序），offset 续拉 */
    @Query("SELECT * FROM raw_replies ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<RawReplyEntity>

    /** 聚合统计：全表 token 汇总（日志页顶部总览） */
    @Query(
        """
        SELECT COUNT(*) AS count,
               COALESCE(SUM(total_tokens), 0) AS totalTokens,
               COALESCE(SUM(prompt_tokens), 0) AS inputTokens,
               COALESCE(SUM(completion_tokens), 0) AS outputTokens,
               COALESCE(SUM(prompt_cache_hit_tokens), 0) AS hitTokens,
               COALESCE(SUM(prompt_cache_miss_tokens), 0) AS missTokens
        FROM raw_replies
        """
    )
    suspend fun getTotals(): RawReplyTotals
}

/** 备份去重用最小字段行：主键 + 参与指纹的列 */
data class RawIdentity(
    val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    val role: String,
    val content: String,
    @ColumnInfo(name = "reasoning_content") val reasoningContent: String?,
    val kind: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

/** 日志页顶部总览：全表 token 聚合结果 */
data class RawReplyTotals(
    val count: Int,
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val hitTokens: Long,
    val missTokens: Long
)
