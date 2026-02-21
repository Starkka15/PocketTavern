package com.pockettavern.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pockettavern.app.data.local.db.dao.CharacterDao
import com.pockettavern.app.data.local.db.dao.ChatDao
import com.pockettavern.app.data.local.db.entity.CharacterEntity
import com.pockettavern.app.data.local.db.entity.ChatEntity

@Database(
    entities = [CharacterEntity::class, ChatEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
}
