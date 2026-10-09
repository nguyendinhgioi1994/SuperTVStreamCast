package com.tuntech.supertvstreamcast.util

import com.tuntech.supertvstreamcast.di.getAllModules
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication {
    return startKoin {
        appDeclaration()
        modules(getAllModules())
    }
}
