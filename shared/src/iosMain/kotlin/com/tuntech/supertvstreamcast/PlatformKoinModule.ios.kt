package com.tuntech.supertvstreamcast

import com.tuntech.common.util.PlatformContext
import com.tuntech.common.widget.effect.AppUpdateViewModel
import com.tuntech.mmp.MmpManager
import com.tuntech.mmp.firebase.FirebaseMmpClient
import com.tuntech.supertvstreamcast.constant.KoinQualifier
import com.tuntech.supertvstreamcast.data.constant.DataStoreName
import com.tuntech.supertvstreamcast.data.repository.createDataStore
import com.tuntech.supertvstreamcast.platform.AppPreferences
import com.tuntech.supertvstreamcast.platform.SecretStore
import com.tuntech.supertvstreamcast.platform.createAppPreferences
import com.tuntech.supertvstreamcast.platform.createSecretStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun appSupportDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return requireNotNull(url?.path)
}

actual val PlatformKoinModule: Module = module {
    single<PlatformContext> { PlatformContext.INSTANCE }
    single(
        qualifier = KoinQualifier.AppSettingDataStore,
    ) {
        createDataStore(
            producePath = {
                "${appSupportDirectory()}/${DataStoreName.APP_SETTING}"
            }
        )
    }
    single<AppPreferences> { createAppPreferences() }
    single<SecretStore> { createSecretStore() }
    single { com.tuntech.supertvstreamcast.net.createLanBrowser() }
    single {
        MmpManager()
            .add(FirebaseMmpClient())
    }
    // Resolved by the iOS AppUpdateEffect (App Store version check) that AppFlowEffect runs once the
    // startup paywall is closed; without it Koin throws NoDefinitionFoundException and the app aborts.
    viewModel {
        AppUpdateViewModel(
            httpClient = get(),
            json = Json { ignoreUnknownKeys = true },
            context = get(),
        )
    }
}
