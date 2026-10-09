package com.tuntech.supertvstreamcast.util

import co.touchlab.kermit.Logger
import com.tuntech.common.util.AppLogger
import com.tuntech.common.util.PlatformContext
import com.tuntech.common.util.getAppName
import com.tuntech.mmp.MmpManager

/** One-shot process start-up for the shared app; `iOSApp.init()` calls `AppInitializer.shared.onApplicationStart()`. */
object AppInitializer {
    private var started = false

    fun onApplicationStart() {
        if (started) return
        started = true

        initAppLogger()
        initKoin()
        initMmp()
    }

    private fun initAppLogger() {
        try {
            AppLogger.init(PlatformContext.INSTANCE, PlatformContext.INSTANCE.getAppName())
        } catch (e: Throwable) {
            Logger.w(throwable = e, tag = "AppLogger") { "Skipping AppLogger init." }
        }
    }

    private fun initMmp() {
        try {
            MmpManager.shared?.init(PlatformContext.INSTANCE)
        } catch (e: Throwable) {
            Logger.w(throwable = e, tag = "MMP") {
                "Skipping MMP init — a tracking SDK is not configured yet."
            }
        }
    }
}
