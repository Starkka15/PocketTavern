package com.pockettavern.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pockettavern.app.ui.screens.characters.CharactersScreen
import com.pockettavern.app.ui.screens.chat.ChatScreen
import com.pockettavern.app.ui.screens.charavault.CharaVaultScreen
import com.pockettavern.app.ui.screens.createcharacter.CreateCharacterScreen
import com.pockettavern.app.ui.screens.formatting.FormattingScreen
import com.pockettavern.app.ui.screens.main.MainScreen
import com.pockettavern.app.ui.screens.profile.ProfileScreen
import com.pockettavern.app.ui.screens.recentchats.RecentChatsScreen
import com.pockettavern.app.ui.screens.settings.SettingsScreen
import com.pockettavern.app.ui.screens.textgen.TextGenSettingsScreen
import com.pockettavern.app.ui.screens.settings.ApiConfigScreen
import com.pockettavern.app.ui.screens.settings.SettingsHubScreen
import com.pockettavern.app.ui.screens.worldinfo.WorldInfoScreen
import com.pockettavern.app.ui.screens.context.ContextSettingsScreen
import com.pockettavern.app.ui.screens.charactersettings.CharacterSettingsScreen
import com.pockettavern.app.ui.screens.persona.PersonaScreen
import com.pockettavern.app.ui.screens.help.SetupGuideScreen
import com.pockettavern.app.ui.screens.groups.GroupChatScreen
import com.pockettavern.app.ui.screens.stimport.StImportScreen
import com.pockettavern.app.ui.screens.oaipreset.OaiPresetSettingsScreen
import com.pockettavern.app.ui.screens.extensions.ExtensionsScreen
import com.pockettavern.app.ui.screens.extensions.quickreply.QuickReplySettingsScreen
import com.pockettavern.app.ui.screens.extensions.regex.RegexSettingsScreen
import com.pockettavern.app.ui.screens.connectionprofiles.ConnectionProfilesScreen
import com.pockettavern.app.ui.screens.debug.DebugLogScreen

@Composable
fun SillyTavernNavGraph(
    navController: NavHostController = rememberNavController()
) {
    // Track when character list needs refresh (after add/import/edit)
    var shouldRefreshCharacters by rememberSaveable { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = Route.Main
    ) {
        composable<Route.Main> {
            MainScreen(
                onNavigateToCharacters = {
                    navController.navigate(Route.Characters)
                },
                onNavigateToRecentChats = {
                    navController.navigate(Route.RecentChats)
                },
                onNavigateToCreateCharacter = {
                    navController.navigate(Route.CreateCharacter)
                },
                onNavigateToCharaVault = {
                    navController.navigate(Route.CharaVault)
                },
                onNavigateToSettings = {
                    navController.navigate(Route.SettingsHub)
                },
                onNavigateToProfile = {
                    navController.navigate(Route.Profile)
                }
            )
        }

        composable<Route.Characters> {
            CharactersScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { characterAvatar ->
                    navController.navigate(Route.Chat(characterAvatar))
                },
                onNavigateToCreateCharacter = {
                    navController.navigate(Route.CreateCharacter)
                },
                onNavigateToEditCharacter = { avatarUrl ->
                    navController.navigate(Route.EditCharacter(avatarUrl))
                },
                onNavigateToCharacterSettings = { avatarUrl ->
                    navController.navigate(Route.CharacterSettings(avatarUrl))
                },
                onNavigateToGroupChat = { groupId ->
                    navController.navigate(Route.GroupChat(groupId))
                },
                shouldRefresh = shouldRefreshCharacters,
                onRefreshHandled = { shouldRefreshCharacters = false }
            )
        }

        composable<Route.RecentChats> {
            RecentChatsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { characterAvatar ->
                    navController.navigate(Route.Chat(characterAvatar))
                }
            )
        }

        composable<Route.SettingsHub> {
            SettingsHubScreen(
                onBack = { navController.popBackStack() },
                onNavigateToConnection = { navController.navigate(Route.ConnectionSettings) },
                onNavigateToApiConfig = { navController.navigate(Route.ApiConfig) },
                onNavigateToTextGen = { navController.navigate(Route.TextGenSettings) },
                onNavigateToFormatting = { navController.navigate(Route.Formatting) },
                onNavigateToWorldInfo = { navController.navigate(Route.WorldInfo) },
                onNavigateToContextSettings = { navController.navigate(Route.ContextSettings) },
                onNavigateToPersonas = { navController.navigate(Route.Personas) },
                onNavigateToSetupGuide = { navController.navigate(Route.SetupGuide) },
                onNavigateToStImport = { navController.navigate(Route.StImport) },
                onNavigateToOaiPresets = { navController.navigate(Route.OaiPresetSettings) },
                onNavigateToExtensions = { navController.navigate(Route.Extensions) },
                onNavigateToConnectionProfiles = { navController.navigate(Route.ConnectionProfiles) }
            )
        }

        composable<Route.ConnectionProfiles> {
            ConnectionProfilesScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.Extensions> {
            ExtensionsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToQuickReply = { navController.navigate(Route.QuickReplySettings) },
                onNavigateToRegex = { navController.navigate(Route.RegexSettings) }
            )
        }

        composable<Route.QuickReplySettings> {
            QuickReplySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.RegexSettings> {
            RegexSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.StImport> {
            StImportScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.OaiPresetSettings> {
            OaiPresetSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.ConnectionSettings> {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.CharaVault> {
            CharaVaultScreen(
                onNavigateBack = {
                    shouldRefreshCharacters = true
                    navController.popBackStack()
                }
            )
        }

        composable<Route.CreateCharacter> {
            CreateCharacterScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    shouldRefreshCharacters = true
                    navController.popBackStack()
                }
            )
        }

        composable<Route.EditCharacter> { backStackEntry ->
            val route: Route.EditCharacter = backStackEntry.toRoute()
            CreateCharacterScreen(
                onBack = { navController.popBackStack() },
                onCreated = {
                    shouldRefreshCharacters = true
                    navController.popBackStack()
                },
                editAvatarUrl = route.avatarUrl
            )
        }

        composable<Route.Chat> { backStackEntry ->
            val route: Route.Chat = backStackEntry.toRoute()
            ChatScreen(
                characterAvatar = route.characterAvatar,
                onBack = { navController.popBackStack() },
                onNavigateToEditCharacter = { avatarUrl ->
                    navController.navigate(Route.EditCharacter(avatarUrl))
                },
                onNavigateToCharacterSettings = { avatarUrl ->
                    navController.navigate(Route.CharacterSettings(avatarUrl))
                },
                onNavigateToDebugLog = { navController.navigate(Route.DebugLog) }
            )
        }

        composable<Route.DebugLog> {
            DebugLogScreen(onBack = { navController.popBackStack() })
        }

        composable<Route.Profile> {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    // Navigate back to main and clear backstack
                    navController.popBackStack(Route.Main, inclusive = false)
                }
            )
        }

        composable<Route.TextGenSettings> {
            TextGenSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.Formatting> {
            FormattingScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.ApiConfig> {
            ApiConfigScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable<Route.WorldInfo> {
            WorldInfoScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.ContextSettings> {
            ContextSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.CharacterSettings> { backStackEntry ->
            val route: Route.CharacterSettings = backStackEntry.toRoute()
            CharacterSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.Personas> {
            PersonaScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.SetupGuide> {
            SetupGuideScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.GroupChat> { backStackEntry ->
            val route: Route.GroupChat = backStackEntry.toRoute()
            GroupChatScreen(
                groupId = route.groupId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
