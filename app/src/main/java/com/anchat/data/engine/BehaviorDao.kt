package com.anchat.data.engine

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
           WHERE status = 1 AND raw_id IN (SELECT id FROM raw_replies WHERE conversation_id = :conversationId)"""
    )
    suspend fun markAllReadByConversation(conversationId: String)

    /** 按 raw_id（即 batch_id）整批删除行为 */
    @Query("DELETE FROM behaviors WHERE raw_id = :rawId")
    suspend fun deleteByRawId(rawId: String)

    /**
     * 取某对话下已完成（已到点）的行为，按执行时间升序。
     * 行为表经 raw_replies.conversation_id 关联到对话；调度器翻 completed 后由 UI 观察此流按时序渲染。
     */
    @Query(
        """SELECT b.* FROM behaviors b
           JOIN raw_replies r ON b.raw_id = r.id
           WHERE r.conversation_id = :conversationId AND b.status >= 1
           ORDER BY b.excu_time ASC"""
    )
    fun observeCompletedByConversation(conversationId: String): Flow<List<BehaviorEntity>>

    /** 同上，但一次性返回（用于进入对话时的初始加载） */
    @Query(
        """SELECT b.* FROM behaviors b
           JOIN raw_replies r ON b.raw_id = r.id
           WHERE r.conversation_id = :conversationId AND b.status >= 1
           ORDER BY b.excu_time ASC"""
    )
    suspend fun getCompletedByConversation(conversationId: String): List<BehaviorEntity>
}
