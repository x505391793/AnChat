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

    @Query("SELECT * FROM character ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM character WHERE id = :id")
    suspend fun getById(id: Long): CharacterEntity?

    @Query("SELECT * FROM character WHERE name = :name")
    suspend fun getByName(name: String): CharacterEntity?
}
