package com.anchat.data.engine

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RawReplyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(raw: RawReplyEntity)

    /** 按 id（即 batch_id）删除整批原始回复 */
    @Query("DELETE FROM raw_replies WHERE id = :id")
    suspend fun deleteById(id: String)

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

/** 日志页顶部总览：全表 token 聚合结果 */
data class RawReplyTotals(
    val count: Int,
    val totalTokens: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val hitTokens: Long,
    val missTokens: Long
)
