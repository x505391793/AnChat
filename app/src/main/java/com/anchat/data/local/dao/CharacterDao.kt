package com.anchat.data.local.dao

import androidx.room.*
import com.anchat.data.local.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    @Insert
    suspend fun insert(character: CharacterEntity): Long

    @Update
    suspend fun update(character: CharacterEntity)

    @Delete
    suspend fun delete(character: CharacterEntity)

    @Query("SELECT * FROM character ORDER BY createdAt DESC")
    suspend fun getAll(): List<CharacterEntity>

    /** 备份导入：批量插入并返回新 id */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CharacterEntity>): List<Long>

    @Query("SELECT * FROM character ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM character WHERE id = :id")
    suspend fun getById(id: Long): CharacterEntity?

    @Query("SELECT * FROM character WHERE id = :id")
    fun observeById(id: Long): Flow<CharacterEntity?>

    @Query("SELECT * FROM character WHERE name = :name")
    suspend fun getByName(name: String): CharacterEntity?
}
