package com.pockettavern.app.data.local.db.dao

import androidx.room.*
import com.pockettavern.app.data.local.db.entity.CharacterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY isFavorite DESC, name ASC")
    fun getAllFlow(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters ORDER BY isFavorite DESC, name ASC")
    suspend fun getAll(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE fileName = :fileName")
    suspend fun getByFileName(fileName: String): CharacterEntity?

    @Query("SELECT * FROM characters WHERE name LIKE '%' || :query || '%' ORDER BY isFavorite DESC, name ASC")
    suspend fun search(query: String): List<CharacterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CharacterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CharacterEntity>)

    @Query("DELETE FROM characters WHERE fileName = :fileName")
    suspend fun deleteByFileName(fileName: String)

    @Query("UPDATE characters SET isFavorite = :favorite WHERE fileName = :fileName")
    suspend fun setFavorite(fileName: String, favorite: Boolean)

    @Query("UPDATE characters SET lastChatDate = :date WHERE fileName = :fileName")
    suspend fun updateLastChatDate(fileName: String, date: Long)

    @Query("UPDATE characters SET useAvatarForImageGen = :useAvatar WHERE fileName = :fileName")
    suspend fun setUseAvatarForImageGen(fileName: String, useAvatar: Boolean)

    @Query("SELECT useAvatarForImageGen FROM characters WHERE fileName = :fileName")
    suspend fun getUseAvatarForImageGen(fileName: String): Boolean?

    @Query("DELETE FROM characters")
    suspend fun deleteAll()
}
