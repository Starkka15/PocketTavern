package com.pockettavern.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pockettavern.app.ui.audio.ThemeAudioManager
import com.pockettavern.app.ui.navigation.SillyTavernNavGraph
import com.pockettavern.app.ui.theme.SillyTavernTheme
import com.pockettavern.app.ui.theme.ThemeManager
import com.pockettavern.app.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var themeManager: ThemeManager
    @Inject lateinit var themeAudioManager: ThemeAudioManager

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeColors by themeManager.colors.collectAsStateWithLifecycle()
            val particleEffect by themeManager.particleEffect.collectAsStateWithLifecycle()
            val themeAssets by themeManager.themeAssets.collectAsStateWithLifecycle()
            SillyTavernTheme(colors = themeColors, particleEffect = particleEffect, themeAssets = themeAssets) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        // System bars and cutout only — deliberately NOT safeDrawing,
                        // which also folds in the IME. Applying keyboard insets here,
                        // above the NavHost, meant one padding node was shared by every
                        // screen: the reserved space outlived the screen that opened the
                        // keyboard and persisted across navigation. Each screen now
                        // applies imePadding() on its own Scaffold instead.
                        .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                ) {
                    SillyTavernNavGraph(themeAudioManager = themeAudioManager)
                }
            }
        }
    }
}
