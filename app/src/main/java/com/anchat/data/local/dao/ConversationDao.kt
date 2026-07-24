package com.anchat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.ConversationListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // 列表实时联查预览：取该会话「可读状态(status>=1)」下最后一条 speech/text，
    // 按 excu_time 倒序取最近一条，截断前 50 字。不再持久化 preview 列，避免 stale。
    @Query(
        """SELECT c.id AS id, c.title AS title, c.is_star AS is_star, c.is_pinned AS is_pinned,
                  c.char_remark AS char_remark, c.char_name AS char_name, c.char_avatar AS char_avatar,
                  c.updated_at AS updated_at,
                  COALESCE((SELECT substr(b.content, 1, 50) FROM behaviors b
                              JOIN raw_replies r ON b.batch_id = r.id
                              WHERE r.conversation_id = CAST(c.id AS TEXT)
                                AND b.status >= 1 AND b.type IN ('speech','text')
                              ORDER BY b.excu_time DESC LIMIT 1), '') AS preview
           FROM conversations c
           ORDER BY c.is_pinned DESC, c.updated_at DESC"""
    )
    fun observeAll(): Flow<List<ConversationListItem>>

    @Query(
        """SELECT c.id AS id, c.title AS title, c.is_star AS is_star, c.is_pinned AS is_pinned,
                  c.char_remark AS char_remark, c.char_name AS char_name, c.char_avatar AS char_avatar,
                  c.updated_at AS updated_at,
                  COALESCE((SELECT substr(b.content, 1, 50) FROM behaviors b
                              JOIN raw_replies r ON b.batch_id = r.id
                              WHERE r.conversation_id = CAST(c.id AS TEXT)
                                AND b.status >= 1 AND b.type IN ('speech','text')
                              ORDER BY b.excu_time DESC LIMIT 1), '') AS preview
           FROM conversations c
           ORDER BY c.is_pinned DESC, c.updated_at DESC"""
    )
    suspend fun getAll(): List<ConversationListItem>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<Conversation?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation): Long

    @Update
    suspend fun update(conversation: Conversation)

    @Query("UPDATE conversations SET title = :title, updated_at = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET is_star = :isStar WHERE id = :id")
    suspend fun setStar(id: Long, isStar: Boolean)

    @Query("UPDATE conversations SET is_pinned = :pinned, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
