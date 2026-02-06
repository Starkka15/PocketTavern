package com.stark.sillytavern.ui.navigation

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
import com.stark.sillytavern.ui.screens.characters.CharactersScreen
import com.stark.sillytavern.ui.screens.chat.ChatScreen
import com.stark.sillytavern.ui.screens.cardvault.CardVaultScreen
import com.stark.sillytavern.ui.screens.chub.ChubBrowserScreen
import com.stark.sillytavern.ui.screens.chub.ChubLoginScreen
import com.stark.sillytavern.ui.screens.createcharacter.CreateCharacterScreen
import com.stark.sillytavern.ui.screens.formatting.FormattingScreen
import com.stark.sillytavern.ui.screens.main.MainScreen
import com.stark.sillytavern.ui.screens.profile.ProfileScreen
import com.stark.sillytavern.ui.screens.recentchats.RecentChatsScreen
import com.stark.sillytavern.ui.screens.settings.SettingsScreen
import com.stark.sillytavern.ui.screens.textgen.TextGenSettingsScreen
import com.stark.sillytavern.ui.screens.settings.ApiConfigScreen
import com.stark.sillytavern.ui.screens.settings.SettingsHubScreen
import com.stark.sillytavern.ui.screens.worldinfo.WorldInfoScreen
import com.stark.sillytavern.ui.screens.context.ContextSettingsScreen
import com.stark.sillytavern.ui.screens.charactersettings.CharacterSettingsScreen
import com.stark.sillytavern.ui.screens.persona.PersonaScreen
import com.stark.sillytavern.ui.screens.help.SetupGuideScreen

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
                onNavigateToChub = {
                    navController.navigate(Route.ChubBrowser)
                },
                onNavigateToCardVault = {
                    navController.navigate(Route.CardVault)
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
                onNavigateToSetupGuide = { navController.navigate(Route.SetupGuide) }
            )
        }

        composable<Route.ConnectionSettings> {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable<Route.ChubBrowser> {
            ChubBrowserScreen(
                onBack = { navController.popBackStack() },
                onImportSuccess = {
                    shouldRefreshCharacters = true
                    navController.popBackStack()
                },
                onLogin = {
                    navController.navigate(Route.ChubLogin)
                }
            )
        }

        composable<Route.ChubLogin> {
            ChubLoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.popBackStack()
                }
            )
        }

        composable<Route.CardVault> {
            CardVaultScreen(
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
                }
            )
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
    }
}
