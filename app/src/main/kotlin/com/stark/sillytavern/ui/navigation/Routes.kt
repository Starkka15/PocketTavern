package com.stark.sillytavern.ui.navigation

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
    data object ChubBrowser : Route()

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
    data object ChubLogin : Route()

    @Serializable
    data object CardVault : Route()

    @Serializable
    data object SetupGuide : Route()
}
