package com.pockettavern.app.di

import android.content.Context
import androidx.room.Room
import com.pockettavern.app.data.local.CharacterStorage
import com.pockettavern.app.data.local.ChatStorage
import com.pockettavern.app.data.local.LoreBookStorage
import com.pockettavern.app.data.local.ConnectionProfileStorage
import com.pockettavern.app.data.local.PresetStorage
import com.pockettavern.app.data.local.SettingsDataStore
import com.pockettavern.app.data.local.db.AppDatabase
import com.pockettavern.app.data.local.db.dao.CharacterDao
import com.pockettavern.app.data.local.db.dao.ChatDao
import com.pockettavern.app.data.repository.LocalRepository
import com.pockettavern.app.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore = SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: SettingsDataStore
    ): SettingsRepository = SettingsRepository(dataStore)

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "pockettavern.db"
    ).fallbackToDestructiveMigration().build()

    @Provides
    @Singleton
    fun provideCharacterDao(db: AppDatabase): CharacterDao = db.characterDao()

    @Provides
    @Singleton
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    @Singleton
    fun provideCharacterStorage(
        @ApplicationContext context: Context,
        characterDao: CharacterDao
    ): CharacterStorage = CharacterStorage(context, characterDao)

    @Provides
    @Singleton
    fun provideChatStorage(
        @ApplicationContext context: Context,
        chatDao: ChatDao
    ): ChatStorage = ChatStorage(context, chatDao)

    @Provides
    @Singleton
    fun provideLoreBookStorage(
        @ApplicationContext context: Context
    ): LoreBookStorage = LoreBookStorage(context)

    @Provides
    @Singleton
    fun providePresetStorage(
        @ApplicationContext context: Context
    ): PresetStorage = PresetStorage(context)

    @Provides
    @Singleton
    fun provideConnectionProfileStorage(
        @ApplicationContext context: Context
    ): ConnectionProfileStorage = ConnectionProfileStorage(context)

    @Provides
    @Singleton
    fun provideLocalRepository(
        @ApplicationContext context: Context,
        characterStorage: CharacterStorage,
        chatStorage: ChatStorage,
        loreBookStorage: LoreBookStorage,
        presetStorage: PresetStorage,
        settingsDataStore: SettingsDataStore,
        connectionProfileStorage: ConnectionProfileStorage
    ): LocalRepository = LocalRepository(
        context, characterStorage, chatStorage, loreBookStorage, presetStorage, settingsDataStore, connectionProfileStorage
    )
}
