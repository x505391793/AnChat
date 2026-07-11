package com.anchat.data.engine

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BehaviorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<BehaviorEntity>)

    @Query("SELECT * FROM behaviors WHERE completed = 0 ORDER BY excu_time ASC")
    suspend fun getDue(): List<BehaviorEntity>

    @Query("UPDATE behaviors SET completed = 1 WHERE id = :id")
    suspend fun markCompleted(id: String)

    @Query("UPDATE behaviors SET is_read = 1 WHERE id = :id")
    suspend fun markRead(id: String)
}
