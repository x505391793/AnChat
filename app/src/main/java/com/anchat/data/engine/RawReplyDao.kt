package com.anchat.data.engine

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy

@Dao
interface RawReplyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(raw: RawReplyEntity)
}
