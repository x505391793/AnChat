package com.anchat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 18,
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

        // v11 → v12：conversations 增加 user_identity_overridden 标志列，
        // 用于区分「用户主动在对话内改过身份」与「创建时自动烤入的旧值」。
    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE conversations ADD COLUMN user_identity_overridden INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    // v12 → v13：messages 增加 batch_id（同一回合关联键，用于整批删除消息+原始数据）
    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN batch_id TEXT")
        }
    }

    // v13 → v14：角色卡 character 增加 real_conversation（「真实对话」开关），
    // 以及对话快照 conversations 增加 char_real_conversation（对话级覆盖），均默认关闭。
    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE character ADD COLUMN real_conversation INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE conversations ADD COLUMN char_real_conversation INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v14 → v15：行为表 behaviors 增加 duration（movement 离开时长）；
    // 消息表 messages 增加 hidden（真实对话下原始整段回复仅入库供上下文、不直接展示）。
    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE behaviors ADD COLUMN duration TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v15 → v16：行为表 behaviors 增加 conversation_id（真实对话行为按对话隔离，
    // 调度器 emit 的 BehaviorDue 事件据此过滤，修复「实时推送被拦、须重进才见」）。
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE behaviors ADD COLUMN conversation_id TEXT NOT NULL DEFAULT ''")
        }
    }

    // v16 → v17：原始回复表 raw_replies 增加 kind（区分 聊天/拆解/系统 来源），
    // 供日志页审计展示；旧行默认 'chat'。
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE raw_replies ADD COLUMN kind TEXT NOT NULL DEFAULT 'chat'")
        }
    }

    // v17 → v18：行为表 behaviors 把 completed + is_read 两列合成单一 status 状态机
    // （0=未到点 / 1=已执行未读 / 2=已读），消除冗余与非法组合。SQLite 不支持 DROP COLUMN，
    // 走「改名旧表 → 建新表 → 回填 → 删旧表」四步；旧 completed=1 行回填为 status=1。
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE behaviors RENAME TO behaviors_old")
            db.execSQL(
                """CREATE TABLE behaviors (
                    id TEXT NOT NULL PRIMARY KEY,
                    raw_id TEXT NOT NULL,
                    `order` INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    content TEXT NOT NULL,
                    duration TEXT,
                    excu_time INTEGER NOT NULL,
                    status INTEGER NOT NULL DEFAULT 0,
                    conversation_id TEXT NOT NULL DEFAULT ''
                )"""
            )
            db.execSQL(
                """INSERT INTO behaviors (id, raw_id, `order`, type, content, duration, excu_time, status, conversation_id)
                   SELECT id, raw_id, `order`, type, content, duration, excu_time,
                          CASE WHEN completed = 1 THEN 1 ELSE 0 END,
                          conversation_id
                   FROM behaviors_old"""
            )
            db.execSQL("DROP TABLE behaviors_old")
        }
    }

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
            .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18)
            .fallbackToDestructiveMigration()  // 开发阶段，其他未覆盖的版本跳跃重建
            .build().also { INSTANCE = it }
        }
    }
    }
}
