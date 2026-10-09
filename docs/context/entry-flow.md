# App entry flow

Mirrors the Smart Printer / Cam Scanner structure (reference: `AirPrint`, the same app on `tuntech_*` submodules). Only the submodules differ: `tuntech_common_kmp` (`:common`), `tuntech_monetization_kmp` (`:monetization`), `tuntech_mmp_kmp` (`:mmp`), `tuntech_mmp_firebase_kmp` (`:mmp_firebase`), all on `main`, never edited from this repo.

## Flow

```
MainApplication → AppInitializer.onApplicationStart (AppLogger, Koin, MMP)
MainActivity → MainApp → MainNavHost (Navigation 3, start = Screen.Splash)

Splash ──first launch──▶ Onboarding ──INTRODUCTION interstitial──▶ first-start Paywall ──▶ Dashboard
   └────later launches──────────────────────────────────────────────────────────────────▶ Dashboard
Dashboard: AppFlowEffect → startup Paywall (source "home") → in-app update check
Back to foreground: app-open ad (APP_FOREGROUND) unless the top screen has skipAds
```

- **Splash** (`ui/splash_screen`): `SplashViewModel.init` sets Remote Config defaults from `composeResources/files/remote_config_defaults.json`, fetches in the background, reads `isOnboardingSelected` from DataStore and leaves immediately; ad units, IAP and the ads SDK (UMP consent) initialise afterwards on the app scope. No artificial delay.
- **Onboarding** (`ui/onboarding`): 3-page pager — introduction, TV brand, first goal — plus a native ad slot. Finishing shows the interstitial, stores brand/goal (`AppPreferences`) and the finished flag (`AppSettingRepository`), then opens the first-start paywall or the Dashboard on the chosen tab.
- **Paywall** (`ui/paywall`): `Screen.Paywall` / `Screen.Paywall2` chosen by `IAP_SETTINGS.paywallId`; both render `PaywallScaffold` and share `PaywallActions` (skip → interstitial → navigate → win-back offer; purchase; restore). Plans, CTA and terms are `:monetization` widgets. Also reachable from Settings while not premium.
- **Dashboard** (`ui/dashboard`): hosts the existing `TvScaffold` tabs with `TvViewModel` from Koin.
- `MainApp` wraps everything in `AppLifecycleMonitorProvider` → `AppRelaunchEffect` → `AppEnvironment` → `TvTheme` → `TvAdTheme` → `ModalController`.

## Layout (`shared/src/commonMain/.../supertvstreamcast`)

`MainApp.kt`, `MainNavHost.kt`, `NavAnimation.kt`, `PlatformKoinModule.kt` (expect) · `ui/Screen.kt` (routes; register nothing by hand, `subclassesOfSealed`), `ui/AppState.kt` (back-stack helpers) · `di/` (`Koin.kt`, `KoinModules.kt`) · `constant/` (`AdConstant` expect, `AdName`, `IapConstant`, `RemoteConfigKey`, `KoinQualifier`) · `data/repository/AppSettingRepository.kt` · `widgets/` (`AppBackHandler`, `AppLifecycleObserver`, `dialog/AppDialog.kt`) · `ui/app_loading`, `ui/common/base/AppFlowEffect.kt` · `util/` (`KoinInitializer`, platform `AppInitializer`). Compose resources class: `shared.resources.Res`; monetization strings come from `monetization.resources.Res`.

## Not configured yet (placeholders)

| Item | Where | Effect while missing |
|---|---|---|
| Firebase project | `androidApp/google-services.json`, `iosApp/iosApp/GoogleService-Info.plist` (dummy values) | Firebase starts, every network call fails; only bundled Remote Config defaults apply; no analytics/crashlytics |
| Qonversion project key | `IapConstant.PROJECT_KEY` (blank) | IAP init skipped: user stays free, no paywall is auto-shown, the Settings paywall lists no plan |
| AdMob app id / ad units | Android manifest, iOS `Info.plist`, `AdConstant.*` (Google sample ids) | Test ads only |
| Terms / privacy URLs | `remote_config_defaults.json` (empty) | Paywall footer links do nothing |

Default ad policy in `remote_config_defaults.json`: interstitial only for `INTRODUCTION`; native only on onboarding; app-open, banner, rewarded, `PREMIUM` and `APP_EXIT` off. Nothing is shown on tab switches or remote key presses.

## Verification

Covered by `EntryFlowTest` (routes, back-stack serialization, onboarding persistence). Runtime behaviour of ads, consent, purchases and the iOS app needs real project ids and devices — see `validation.md`.
