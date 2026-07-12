package com.anchat.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anchat.data.local.entity.Message
import kotlinx.coroutines.flow.Flow

/** 每个会话的未读（助手消息且 is_read=0）计数，供红点展示 */
data class UnreadCount(
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    @ColumnInfo(name = "cnt") val count: Int
)

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun observeByConversation(conversationId: Long): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun getByConversation(conversationId: Long): List<Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: Message): Long

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)

    /** 按回合 batch_id 整批删除（用户提问 + AI 回复） */
    @Query("DELETE FROM messages WHERE batch_id = :batchId")
    suspend fun deleteByBatchId(batchId: String)

    /** 按主键单条删除（长按气泡删除这一句） */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 打开会话时清未读（仅助手消息） */
    @Query("UPDATE messages SET is_read = 1 WHERE conversation_id = :conversationId AND role = 'assistant'")
    suspend fun markReadByConversation(conversationId: Long)

    /** 每个会话未读计数（红点）：实时 Flow */
    @Query(
        "SELECT conversation_id, COUNT(*) AS cnt FROM messages " +
            "WHERE is_read = 0 AND role = 'assistant' GROUP BY conversation_id"
    )
    fun unreadByConversation(): Flow<List<UnreadCount>>
}
