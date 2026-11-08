package com.anchat.data.local.dao

import androidx.room.*
import com.anchat.data.local.entity.WorldBook
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldBookDao {

    @Insert
    suspend fun insert(worldBook: WorldBook): Long

    @Update
    suspend fun update(worldBook: WorldBook)

    @Delete
    suspend fun delete(worldBook: WorldBook)

    @Query("SELECT * FROM world_book ORDER BY createdAt DESC")
    suspend fun getAll(): List<WorldBook>

    @Query("SELECT * FROM world_book ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WorldBook>>

    @Query("SELECT * FROM world_book WHERE id = :id")
    suspend fun getById(id: Long): WorldBook?

    @Query("SELECT * FROM world_book WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<WorldBook>

    /** 备份导入：批量插入并返回新 id（REPLACE 避免 id 撞本地导致崩溃） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<WorldBook>): List<Long>
}
