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
import com.anchat.data.local.entity.CharacterEntity
import com.anchat.data.local.entity.Conversation

@Database(
    entities = [
        Conversation::class, CharacterEntity::class,
        RawReplyEntity::class, BehaviorEntity::class
    ],
    version = 23,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun characterDao(): CharacterDao
    abstract fun conversationDao(): ConversationDao
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

    // v14 → v15：行为表 behaviors 增加 duration（leave 离开时长）；
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

    // v18 → v19：角色卡 character 增加 real_conv_version（真实对话版本，默认 v1）；
    // 对话快照 conversations 增加 char_real_conv_version（对话级覆盖，默认 v1）。
    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE character ADD COLUMN real_conv_version TEXT NOT NULL DEFAULT 'v1'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN char_real_conv_version TEXT NOT NULL DEFAULT 'v1'")
        }
    }

    // v19 → v20：数据层重构——messages 表退役，raw_replies/behaviors 升级为统一消息表。
    // 旧三表关系断裂（messages↔raw_replies 无外键、raw/behaviors 只存 AI），
    // 无法完美迁移，故按「重建」策略：DROP 三表后按新实体 schema 原样重建
    // raw_replies(含 role/batch_id) 与 behaviors(含 role/batch_id)，空表起步。
    // 这是有意为之的显式迁移（符合「缺迁移即崩」铁律，副作用清数据已授权）。
    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS messages")
            db.execSQL("DROP TABLE IF EXISTS raw_replies")
            db.execSQL("DROP TABLE IF EXISTS behaviors")

            db.execSQL(
                """CREATE TABLE raw_replies (
                    id TEXT NOT NULL PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    reasoning_content TEXT,
                    prompt_tokens INTEGER,
                    completion_tokens INTEGER,
                    total_tokens INTEGER,
                    reasoning_tokens INTEGER,
                    prompt_cache_hit_tokens INTEGER,
                    prompt_cache_miss_tokens INTEGER,
                    is_error INTEGER NOT NULL,
                    kind TEXT NOT NULL DEFAULT 'chat',
                    batch_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )"""
            )

            db.execSQL(
                """CREATE TABLE behaviors (
                    id TEXT NOT NULL PRIMARY KEY,
                    raw_id TEXT NOT NULL,
                    `order` INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    duration TEXT,
                    excu_time INTEGER NOT NULL,
                    status INTEGER NOT NULL DEFAULT 0,
                    conversation_id TEXT NOT NULL DEFAULT '',
                    batch_id TEXT NOT NULL DEFAULT ''
                )"""
            )
        }
    }

        // v20 → v21：删除 raw_replies.batch_id 列（生数据层是源头，不需要「批次」标记；
        // batch_id 仅属行为层，= 源 raw.id）。SQLite 不支持 DROP COLUMN，
        // 走「改名旧表 → 建无该列新表 → 回填 → 删旧表」四步。
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE raw_replies RENAME TO raw_replies_old")
                db.execSQL(
                    """CREATE TABLE raw_replies (
                        id TEXT NOT NULL PRIMARY KEY,
                        conversation_id TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        reasoning_content TEXT,
                        prompt_tokens INTEGER,
                        completion_tokens INTEGER,
                        total_tokens INTEGER,
                        reasoning_tokens INTEGER,
                        prompt_cache_hit_tokens INTEGER,
                        prompt_cache_miss_tokens INTEGER,
                        is_error INTEGER NOT NULL,
                        kind TEXT NOT NULL DEFAULT 'chat',
                        created_at INTEGER NOT NULL
                    )"""
                )
                db.execSQL(
                    """INSERT INTO raw_replies (id, conversation_id, role, content, reasoning_content,
                           prompt_tokens, completion_tokens, total_tokens, reasoning_tokens,
                           prompt_cache_hit_tokens, prompt_cache_miss_tokens, is_error, kind, created_at)
                       SELECT id, conversation_id, role, content, reasoning_content,
                              prompt_tokens, completion_tokens, total_tokens, reasoning_tokens,
                              prompt_cache_hit_tokens, prompt_cache_miss_tokens, is_error, kind, created_at
                       FROM raw_replies_old"""
                )
                db.execSQL("DROP TABLE raw_replies_old")
            }
        }

        // v21 → v22：删除 behaviors.raw_id 列（与 batch_id 值恒等，合并到 batch_id）
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE behaviors RENAME TO behaviors_old")
                db.execSQL(
                    """CREATE TABLE behaviors (
                        id TEXT NOT NULL PRIMARY KEY,
                        batch_id TEXT NOT NULL DEFAULT '',
                        `order` INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        duration TEXT,
                        excu_time INTEGER NOT NULL,
                        status INTEGER NOT NULL,
                        conversation_id TEXT NOT NULL DEFAULT ''
                    )"""
                )
                db.execSQL(
                    """INSERT INTO behaviors (id, batch_id, `order`, type, role, content, duration, excu_time, status, conversation_id)
                       SELECT id, batch_id, `order`, type, role, content, duration, excu_time, status, conversation_id
                       FROM behaviors_old"""
                )
                db.execSQL("DROP TABLE behaviors_old")
            }
        }

        // v22 → v23：删除 conversations.preview 列（列表预览改为实时联查 behaviors
        // 最后一条可读消息，不再持久化缓存——旧逻辑只在删除时刷新、发送/回复时不更新，
        // 导致「AI 已回复但预览仍是用户消息」的 stale bug。SQLite 不支持 DROP COLUMN，
        // 走「改名旧表 → 建无该列新表 → 回填 → 删旧表」四步；preview 列直接丢弃。
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations RENAME TO conversations_old")
                db.execSQL(
                    """CREATE TABLE conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL DEFAULT '新对话',
                        is_star INTEGER NOT NULL DEFAULT 0,
                        is_pinned INTEGER NOT NULL DEFAULT 0,
                        character_id INTEGER NOT NULL DEFAULT -1,
                        model_id TEXT,
                        system_prompt TEXT,
                        char_remark TEXT,
                        char_name TEXT,
                        char_avatar TEXT,
                        char_description TEXT,
                        char_greeting TEXT,
                        char_thinking_enabled INTEGER NOT NULL DEFAULT 0,
                        char_real_conversation INTEGER NOT NULL DEFAULT 0,
                        char_real_conv_version TEXT NOT NULL DEFAULT 'v1',
                        user_identity_overridden INTEGER NOT NULL DEFAULT 0,
                        user_name TEXT,
                        user_avatar TEXT,
                        user_description TEXT,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                db.execSQL(
                    """INSERT INTO conversations (id, title, is_star, is_pinned, character_id,
                           model_id, system_prompt, char_remark, char_name, char_avatar,
                           char_description, char_greeting, char_thinking_enabled,
                           char_real_conversation, char_real_conv_version, user_identity_overridden,
                           user_name, user_avatar, user_description, created_at, updated_at)
                       SELECT id, title, is_star, is_pinned, character_id,
                              model_id, system_prompt, char_remark, char_name, char_avatar,
                              char_description, char_greeting, char_thinking_enabled,
                              char_real_conversation, char_real_conv_version, user_identity_overridden,
                              user_name, user_avatar, user_description, created_at, updated_at
                       FROM conversations_old"""
                )
                db.execSQL("DROP TABLE conversations_old")
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
            .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)
            .build().also { INSTANCE = it }
        }
    }
    }
}
