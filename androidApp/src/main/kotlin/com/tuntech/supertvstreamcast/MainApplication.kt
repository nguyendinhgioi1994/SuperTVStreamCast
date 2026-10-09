package com.tuntech.supertvstreamcast

import android.app.Application
import com.tuntech.supertvstreamcast.di.appKoinModule
import com.tuntech.supertvstreamcast.util.AppInitializer

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        AppInitializer.onApplicationStart(this, listOf(appKoinModule(this)))
    }
}
