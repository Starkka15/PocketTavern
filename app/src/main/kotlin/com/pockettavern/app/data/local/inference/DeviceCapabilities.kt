package com.pockettavern.app.data.local.inference

import android.app.ActivityManager
import android.content.Context

/**
 * Detects device RAM / CPU so on-device inference params scale to the hardware instead of being
 * hardcoded — a 16GB flagship gets a large context + more threads; a 4GB budget phone stays
 * conservative so it doesn't thrash/swap.
 */
object DeviceCapabilities {

    fun totalRamBytes(context: Context): Long = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem
    } catch (e: Exception) {
        4L * 1024 * 1024 * 1024  // assume 4GB if unknown
    }

    fun totalRamGb(context: Context): Double = totalRamBytes(context) / (1024.0 * 1024 * 1024)

    fun cores(): Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    /**
     * KV-cache context window scaled to RAM (KV grows ~linearly with context). Conservative on
     * low-RAM devices, generous on flagships.
     */
    fun recommendedContextLength(context: Context): Int = when {
        totalRamGb(context) < 4 -> 1024
        totalRamGb(context) < 6 -> 2048
        totalRamGb(context) < 8 -> 4096
        totalRamGb(context) < 12 -> 6144
        else -> 8192
    }

    /**
     * Inference threads. On big.LITTLE SoCs, ~half the cores tracks the performance cluster and
     * avoids slow efficiency-core thrash; clamp lets flagships use more without penalizing them.
     */
    fun recommendedThreads(): Int = (cores() / 2).coerceIn(2, 6)

    /** Rough guidance: can this device comfortably hold a model of [modelBytes]? (model + KV + headroom) */
    fun canFit(context: Context, modelBytes: Long): Boolean =
        modelBytes + 1_500L * 1024 * 1024 < totalRamBytes(context)  // ~1.5GB headroom for KV + app + OS
}
