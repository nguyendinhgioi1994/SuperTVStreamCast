# App entry flow

Mirrors the Smart Printer / Cam Scanner structure (reference: `AirPrint`, the same app on `tuntech_*` submodules). Only the submodules differ: `tuntech_common_kmp` (`:common`), `tuntech_monetization_kmp` (`:monetization`), `tuntech_mmp_kmp` (`:mmp`), `tuntech_mmp_firebase_kmp` (`:mmp_firebase`), all on `main`, never edited from this repo.

## Flow

```
MainApplication → AppInitializer.onApplicationStart (AppLogger, Koin, MMP)
MainActivity → MainApp → MainNavHost (Navigation 3, start = Screen.Splash)

Splash ──first launch──▶ Language ──▶ Onboarding (+ full-page natives) ──INTRODUCTION interstitial──▶ first-start Paywall ──▶ Dashboard
   └────later launches──SPLASH app-open ad─────────────────────────────────────────────────────────────────────▶ Dashboard
Dashboard: AppFlowEffect → startup Paywall (source "home") → in-app update check
Back to foreground: app-open ad (APP_FOREGROUND) unless the top screen has skipAds
```

- **Splash** (`ui/splash_screen`): `SplashViewModel.init` sets Remote Config defaults from `composeResources/files/remote_config_defaults.json`, fetches in the background, reads `isOnboardingSelected` from DataStore and leaves immediately; ad units, IAP and the ads SDK (UMP consent) initialise afterwards on the app scope. No artificial delay.
- **Language** (`ui/language`, `Screen.Language(isInitial)`), modelled on RetroOne: one card per shipped language (`domain/AppLanguage` — 15, same set as RetroOne: en, ar, de, es, fr, pt, id, fil, hi, th, vi, zh, ko, ja, tr; native name, name in the current UI language, vector flag from `LanguageFlags.kt`, Brazil’s for Portuguese), `LANGUAGE` small native ad below the CTA. First launch (`isInitial`): device language preselected (`AppLanguage.resolve`: legacy `in`/`tl` map to Indonesian/Filipino, unshipped languages → English), Continue replaces it with the Onboarding and stores the choice. From Settings → General → Language: back button, Apply enabled only for a different language, returns to Settings. Applying uses `:common`'s `LocalAppLocale.changeLocale` (Android: AppCompat per-app locale, which recreates the activity with the saved back stack; iOS: `AppleLanguages`, re-resolved by `AppEnvironment`). The screen navigates before applying. Quitting before the Onboarding is finished shows the picker again on the next launch.
- **Onboarding** (`ui/onboarding`): 3-page pager — introduction, TV brand, first goal — plus a native ad slot. Finishing shows the interstitial, stores brand/goal (`AppPreferences`) and the finished flag (`AppSettingRepository`), then opens the first-start paywall or the Dashboard on the chosen tab.
- **Paywall** (`ui/paywall`): `Screen.Paywall` / `Screen.Paywall2` chosen by `IAP_SETTINGS.paywallId`; both render `PaywallScaffold` and share `PaywallActions` (skip → interstitial → navigate → win-back offer; purchase; restore). Plans, CTA and terms are `:monetization` widgets. Also reachable from Settings while not premium.
- **Dashboard** (`ui/dashboard`): hosts the existing `TvScaffold` tabs with `TvViewModel` from Koin.
- `MainApp` wraps everything in `AppLifecycleMonitorProvider` → `AppRelaunchEffect` → `AppEnvironment` → `TvTheme` → `TvAdTheme` → `ModalController`.

## Layout (`shared/src/commonMain/.../supertvstreamcast`)

`MainApp.kt`, `MainNavHost.kt`, `NavAnimation.kt`, `PlatformKoinModule.kt` (expect) · `ui/Screen.kt` (routes; register nothing by hand, `subclassesOfSealed`), `ui/AppState.kt` (back-stack helpers) · `di/` (`Koin.kt`, `KoinModules.kt`) · `constant/` (`AdConstant` expect, `AdName`, `IapConstant`, `RemoteConfigKey`, `KoinQualifier`) · `data/repository/AppSettingRepository.kt` · `widgets/` (`AppBackHandler`, `AppLifecycleObserver`, `dialog/AppDialog.kt`) · `ui/app_loading`, `ui/common/base/AppFlowEffect.kt` · `util/` (`KoinInitializer`, platform `AppInitializer`). Compose resources class: `shared.resources.Res`; monetization strings come from `monetization.resources.Res`.

## Not configured yet (placeholders)

| Item | Where | Effect while missing |
|---|---|---|
| Firebase project | `androidApp/google-services.json`, `iosApp/iosApp/GoogleService-Info.plist` — real project `supertvstreamcast` ("Super TV Cast"), Android `com.tuntech.supertvstreamcast`, iOS `com.tuntech.supertvstreamcast.SuperTVStreamCast` | Configured; Remote Config template v1 published (12 params mirroring `remote_config_defaults.json`); Analytics/Crashlytics still need enabling in the console |
| Qonversion products | `IapConstant.PROJECT_KEY` set. App Store Connect app `6821385537` ("Super TV Cast: Remote & IPTV"), group "Premium": `com.tuntech.supertvstreamcast.monthly` ($4.99, 3-day free trial), `.yearly` ($39.99), non-consumable `.lifetime` ($69.99), all 175 territories. Qonversion (REST API v4, 2026-10-10) maps products `monthly` (subscription_with_promo, P1M), `yearly` (subscription, P1Y), `lifetime` to them under entitlement `premium`; Apple store config enabled with app id, bundle id, App-Specific Shared Secret and In-App Purchase key `ZLYP97RNX2`. App Store Server Notifications URL not set; Google Play products not created | Paywall lists no plan until Qonversion resolves the products; ASC products stay `MISSING_METADATA` until a review screenshot is added |
| AdMob app id / ad units | Android manifest, iOS `Info.plist`, `AdConstant.*` (Google sample ids) | Test ads only |
| Terms / privacy URLs | `remote_config_defaults.json` (empty) | Paywall footer links do nothing |

Ad placements, their defaults and the premium gates are in [monetization.md](monetization.md). Returning users may see the `SPLASH` app-open ad before the Dashboard; the onboarding can carry full-page native ads between its pages.

## Verification

Covered by `EntryFlowTest` (routes, back-stack serialization, onboarding persistence, language resolution). Runtime behaviour of ads, consent, purchases and the iOS app needs real project ids and devices — see `validation.md`.

## Notifications

- No daily/re-engagement notification and no Settings switch for one (removed 2026-10-11); the app does not ask for notification permission at startup. Remote Config `NOTIFICATION_SETTINGS` is not read.
- The only local notifications are TV guide reminders (`ReminderReceiver`, see `iptv-library.md`), which ask for permission when a reminder is set and use the small icon `ic_stat_notification`.

## Localization

`composeResources/values-<code>/strings.xml` exists for every `AppLanguage` (base `values` is English). `values-in` and `values-tl` are identical copies of `values-id` and `values-fil` because Java/Android may report the legacy codes. Every new string must be added to all of them (and plurals keep their `other` form; Arabic also has `two`/`few`). iOS lists the same languages in `Info.plist` `CFBundleLocalizations`. Arabic switches the layout to RTL through `:common`'s `AppEnvironment`. Machine-assisted translations: have a native speaker review store-facing and legal text before release.
