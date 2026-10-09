package com.tuntech.supertvstreamcast

import android.content.Context
import com.tuntech.mmp.MmpManager
import com.tuntech.mmp.firebase.FirebaseMmpClient
import com.tuntech.supertvstreamcast.constant.KoinQualifier
import com.tuntech.supertvstreamcast.data.constant.DataStoreName
import com.tuntech.supertvstreamcast.data.repository.createDataStore
import com.tuntech.supertvstreamcast.platform.AppPreferences
import com.tuntech.supertvstreamcast.platform.SecretStore
import com.tuntech.supertvstreamcast.platform.createAppPreferences
import com.tuntech.supertvstreamcast.platform.createSecretStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual val PlatformKoinModule: Module = module {
    single(
        qualifier = KoinQualifier.AppSettingDataStore,
    ) {
        createDataStore(
            producePath = {
                get<Context>().filesDir.resolve(DataStoreName.APP_SETTING).absolutePath
            }
        )
    }
    single<AppPreferences> { createAppPreferences(get()) }
    single<SecretStore> { createSecretStore(get()) }
    single {
        MmpManager()
            .add(FirebaseMmpClient())
    }
}
