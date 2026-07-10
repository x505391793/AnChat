package com.anchat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.anchat.data.local.entity.ModelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {

    @Query("SELECT * FROM models ORDER BY id ASC")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY id ASC")
    suspend fun getAll(): List<ModelEntity>

    @Query("SELECT * FROM models WHERE is_default = 1 LIMIT 1")
    suspend fun getDefault(): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(models: List<ModelEntity>)

    @Query("UPDATE models SET is_default = 0")
    suspend fun clearDefault()

    @Query("UPDATE models SET is_default = 1 WHERE id = :id")
    suspend fun setDefault(id: String)

    @Query("SELECT COUNT(*) FROM models")
    suspend fun count(): Int

    @Query("DELETE FROM models")
    suspend fun clear()
}
