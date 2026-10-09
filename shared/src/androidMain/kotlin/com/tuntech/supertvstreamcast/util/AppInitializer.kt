package com.tuntech.supertvstreamcast.util

import android.content.Context
import co.touchlab.kermit.Logger
import com.tuntech.common.util.AppLogger
import com.tuntech.common.util.getAppName
import com.tuntech.mmp.MmpManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module

/**
 * One-shot process start-up for the shared app. The Android `Application` calls this once from
 * `onCreate`, passing any app-level Koin modules (Android-only implementations of shared interfaces).
 */
object AppInitializer {
    private var started = false

    fun onApplicationStart(context: Context, appModules: List<Module> = emptyList()) {
        if (started) return
        started = true

        initAppLogger(context)
        initKoin {
            androidContext(context)
            modules(appModules)
        }
        initMmp(context)
    }

    private fun initAppLogger(context: Context) {
        try {
            AppLogger.init(context, context.getAppName())
        } catch (e: Throwable) {
            Logger.w(throwable = e, tag = "AppLogger") { "Skipping AppLogger init." }
        }
    }

    private fun initMmp(context: Context) {
        try {
            MmpManager.shared?.init(context)
        } catch (e: Throwable) {
            Logger.w(throwable = e, tag = "MMP") {
                "Skipping MMP init — a tracking SDK is not configured yet."
            }
        }
    }
}
