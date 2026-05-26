package com.pockettavern.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pockettavern.app.data.local.db.dao.CharacterDao
import com.pockettavern.app.data.local.db.dao.ChatDao
import com.pockettavern.app.data.local.db.entity.CharacterEntity
import com.pockettavern.app.data.local.db.entity.ChatEntity

@Database(
    entities = [CharacterEntity::class, ChatEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN useAvatarForImageGen INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN avatar TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN personality TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN scenario TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN firstMessage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN messageExample TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN creatorNotes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN systemPrompt TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN postHistoryInstructions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE characters ADD COLUMN alternateGreetings TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE characters ADD COLUMN characterBookEntryCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN memoryBlock TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN summarizedTurnCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE characters ADD COLUMN loreHints TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
