package com.tuntech.supertvstreamcast.di

import com.tuntech.monetization.ad.AdKoinModule
import com.tuntech.monetization.iap.IapKoinModule
import com.tuntech.supertvstreamcast.PlatformKoinModule
import org.koin.core.module.Module

/** Every Koin module the shared app needs; the platform entry points add their own on top. */
fun getAllModules(): List<Module> = listOf(
    PlatformKoinModule,
    UiKoinModule,
    dataModule,
    AdKoinModule,
    IapKoinModule,
)
