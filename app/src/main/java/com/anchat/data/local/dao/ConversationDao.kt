package com.anchat.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.anchat.data.local.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY is_pinned DESC, updated_at DESC")
    fun observeAll(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY is_pinned DESC, updated_at DESC")
    suspend fun getAll(): List<Conversation>

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

    @Query("UPDATE conversations SET preview = :preview, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePreview(id: Long, preview: String, updatedAt: Long = System.currentTimeMillis())

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
