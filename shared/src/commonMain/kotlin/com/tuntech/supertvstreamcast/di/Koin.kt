package com.tuntech.supertvstreamcast.di

import com.tuntech.supertvstreamcast.constant.KoinQualifier
import com.tuntech.supertvstreamcast.data.repository.AppSettingRepository
import com.tuntech.supertvstreamcast.ui.TvViewModel
import com.tuntech.supertvstreamcast.ui.app_loading.AppLoadingViewModel
import com.tuntech.supertvstreamcast.ui.onboarding.OnboardingViewModel
import com.tuntech.supertvstreamcast.ui.paywall.PaywallViewModel
import com.tuntech.supertvstreamcast.ui.splash_screen.SplashViewModel
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dataModule = module {
    single(
        qualifier = KoinQualifier.AppScope,
    ) {
        CoroutineScope(Dispatchers.Default)
    }

    // :monetization's IapManager downloads its discount-campaign media through this client.
    single { HttpClient() }

    single {
        AppSettingRepository(get(KoinQualifier.AppSettingDataStore))
    }

    single {
        val context = get<coil3.PlatformContext>()
        com.tuntech.supertvstreamcast.data.IptvStore(directory = { com.tuntech.supertvstreamcast.data.iptvDirectory(context) },
            cipher = com.tuntech.supertvstreamcast.data.IptvCipher(com.tuntech.supertvstreamcast.data.iptvSecretStore(context)))
    }

    single {
        com.tuntech.supertvstreamcast.data.repository.ThemeRepository(get(KoinQualifier.AppSettingDataStore), get(KoinQualifier.AppScope))
    }
}

val UiKoinModule: Module = module {
    viewModel {
        SplashViewModel(
            get(),
            get(qualifier = KoinQualifier.AppScope),
        )
    }
    viewModel {
        OnboardingViewModel(
            get(),
            get(),
            get(qualifier = KoinQualifier.AppScope),
        )
    }
    viewModel {
        TvViewModel(
            prefs = get(),
            secrets = get(),
            startTab = get<AppSettingRepository>().consumeStartFeature(),
        )
    }
    viewModel { com.tuntech.supertvstreamcast.ui.iptv.IptvViewModel(store = get()) }
    viewModelOf(::PaywallViewModel)
    viewModelOf(::AppLoadingViewModel)
}
