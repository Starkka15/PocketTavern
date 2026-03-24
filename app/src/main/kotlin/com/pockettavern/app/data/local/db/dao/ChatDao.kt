package com.pockettavern.app.data.local.db.dao

import androidx.room.*
import com.pockettavern.app.data.local.db.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE characterName = :characterName ORDER BY modifyDate DESC")
    fun getForCharacterFlow(characterName: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE characterName = :characterName ORDER BY modifyDate DESC")
    suspend fun getForCharacter(characterName: String): List<ChatEntity>

    @Query("SELECT * FROM chats ORDER BY modifyDate DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<ChatEntity>

    @Query("SELECT * FROM chats WHERE fileName = :fileName")
    suspend fun getByFileName(fileName: String): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatEntity)

    @Query("DELETE FROM chats WHERE fileName = :fileName")
    suspend fun deleteByFileName(fileName: String)

    @Query("DELETE FROM chats WHERE characterName = :characterName")
    suspend fun deleteAllForCharacter(characterName: String)

    @Query("UPDATE chats SET messageCount = :count, modifyDate = :modifyDate WHERE fileName = :fileName")
    suspend fun updateStats(fileName: String, count: Int, modifyDate: Long)
}
