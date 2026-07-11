package com.anchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anchat.data.engine.BehaviorDao
import com.anchat.data.engine.BehaviorEntity
import com.anchat.data.engine.RawReplyDao
import com.anchat.data.engine.RawReplyEntity
import com.anchat.data.local.dao.CharacterDao
import com.anchat.data.local.dao.ConversationDao
import com.anchat.data.local.dao.MessageDao
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation
import com.anchat.data.local.entity.Message

@Database(
    entities = [
        Conversation::class, Message::class, CharacterEntity::class,
        RawReplyEntity::class, BehaviorEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun characterDao(): CharacterDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun rawReplyDao(): RawReplyDao
    abstract fun behaviorDao(): BehaviorDao

    companion object {
        private const val DB_NAME = "anchat.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigration()  // 开发阶段，表结构变动时重建
                .build().also { INSTANCE = it }
            }
        }
    }
}
