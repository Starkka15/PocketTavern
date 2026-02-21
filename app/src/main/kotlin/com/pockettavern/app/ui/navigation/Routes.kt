package com.pockettavern.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class Route {
    @Serializable
    data object Main : Route()

    @Serializable
    data object Characters : Route()

    @Serializable
    data object RecentChats : Route()

    @Serializable
    data object SettingsHub : Route()

    @Serializable
    data object ConnectionSettings : Route()

    @Serializable
    data object CreateCharacter : Route()

    @Serializable
    data class EditCharacter(val avatarUrl: String) : Route()

    @Serializable
    data class Chat(val characterAvatar: String) : Route()

    @Serializable
    data object Profile : Route()

    @Serializable
    data object TextGenSettings : Route()

    @Serializable
    data object Formatting : Route()

    @Serializable
    data object ApiConfig : Route()

    @Serializable
    data object WorldInfo : Route()

    @Serializable
    data object ContextSettings : Route()

    @Serializable
    data class CharacterSettings(val avatarUrl: String) : Route()

    @Serializable
    data object Personas : Route()

    @Serializable
    data object CharaVault : Route()

    @Serializable
    data object SetupGuide : Route()

    @Serializable
    data object Groups : Route()

    @Serializable
    data class GroupChat(val groupId: String) : Route()

    @Serializable
    data object CreateGroup : Route()

    @Serializable
    data object StImport : Route()

    @Serializable
    data object OaiPresetSettings : Route()

    @Serializable
    data object Extensions : Route()

    @Serializable
    data object QuickReplySettings : Route()

    @Serializable
    data object RegexSettings : Route()

    @Serializable
    data object ConnectionProfiles : Route()

    @Serializable
    data object DebugLog : Route()
}
