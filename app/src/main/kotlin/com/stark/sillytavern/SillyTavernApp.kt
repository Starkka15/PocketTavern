package com.stark.sillytavern

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.stark.sillytavern.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SillyTavernApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        // Initialize debug logger
        DebugLogger.init(this)
        DebugLogger.log("SillyTavern App started")

        // Set up global uncaught exception handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugLogger.logError("CRASH", "Uncaught exception on thread ${thread.name}", throwable)
            // Call default handler to let the app crash normally (shows crash dialog)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun newImageLoader(): ImageLoader = imageLoader
}
