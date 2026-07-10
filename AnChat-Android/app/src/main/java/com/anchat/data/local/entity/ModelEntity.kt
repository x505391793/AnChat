package com.anchat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A selectable DeepSeek model. Seeded from [com.anchat.data.remote.DeepSeekConstants]
 * and optionally refreshed from the DeepSeek `/models` endpoint.
 */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false
)
