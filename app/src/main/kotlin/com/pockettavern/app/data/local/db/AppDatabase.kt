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
    version = 3,
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
    }
}
