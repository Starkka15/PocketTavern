package com.pockettavern.app.data.local.inference

import android.content.Context
import com.pockettavern.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates RAM between the on-device engines (LiteRT-LM, GGUF/llama.cpp, MNN SDXL). Each one
 * keeps its model fully resident once loaded and never evicts itself — there was previously no
 * eviction at all, so a device that loaded both an LLM and SDXL in the same session kept both
 * resident for the rest of the process's life. Only one is meant to be resident at a time.
 *
 * Engines register an unload callback under their own [Slot] here instead of depending on each
 * other directly (a 3-way circular Hilt dependency). Before loading, an engine calls
 * [prepareLoad], which unloads every *other* registered slot first, then checks
 * [DeviceCapabilities.canFit] against the (now freed) RAM.
 */
@Singleton
class OnDeviceMemoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class Slot { LITERTLM, GGUF, SDXL }

    private val unloaders = mutableMapOf<Slot, suspend () -> Unit>()
    private val mutex = Mutex()

    /** Called once by each engine (in its constructor) to register how to free its RAM. */
    fun register(slot: Slot, unload: suspend () -> Unit) {
        unloaders[slot] = unload
    }

    /**
     * Unloads every slot other than [slot], then verifies [modelBytes] fits. Throws if it still
     * doesn't fit even with everything else freed — callers surface this as a normal
     * generation/load error (existing error-event plumbing), not a crash.
     */
    suspend fun prepareLoad(slot: Slot, modelBytes: Long) = mutex.withLock {
        for ((otherSlot, unload) in unloaders) {
            if (otherSlot == slot) continue
            try {
                unload()
            } catch (e: Exception) {
                DebugLogger.logError("OnDeviceMemoryManager", "unload of $otherSlot failed", e)
            }
        }
        if (!DeviceCapabilities.canFit(context, modelBytes)) {
            val neededGb = "%.1f".format(modelBytes / (1024.0 * 1024 * 1024))
            val totalGb = "%.1f".format(DeviceCapabilities.totalRamGb(context))
            throw IllegalStateException(
                "Not enough RAM to load this model (~${neededGb}GB needed, ${totalGb}GB total on this device)."
            )
        }
    }
}
