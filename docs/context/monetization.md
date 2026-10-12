# Ads and premium gates

Everything below is driven by Remote Config; `shared/src/commonMain/composeResources/files/remote_config_defaults.json` holds the in-app defaults. A value published in the Firebase template overrides the default for the whole key, so the console template must carry the same keys; it was published with them on 11/10/2026. Premium users get no ads at all: `:monetization` deactivates every ad manager once the entitlement is active.

## Placements (`constant/AdConstant.kt` → `AdName`)

| Format | Name | Where | Default |
|---|---|---|---|
| App open | `SPLASH` | Cold start of a returning user, before the Dashboard (`ui/splash_screen/SplashAd.kt`). Waits ≤3 s for the SDK + entitlement, ≤4 s for the request; skipped when premium, off, or late | on |
| App open | `APP_FOREGROUND` | Back from background after ≥10 s (`backgroundIdleTime`), ≥30 s apart (`nextDisplayAdTime`) | on |
| Interstitial | `INTRODUCTION` | Finishing the onboarding | on |
| Interstitial | `PREMIUM` | Closing a paywall | on |
| Interstitial | `FEATURE_OPEN` | Home quick-access card → Remote / Cast / IPTV (not the dock) | on |
| Interstitial | `IPTV_SOURCE_OPEN` | Opening a saved source from the IPTV hub | on |
| Interstitial | `IPTV_PLAYER_CLOSE` | After the player's close button | on |
| Interstitial | `APP_EXIT` | Back on the Dashboard root (Android) | off |
| Native small | `LANGUAGE`, `INTRODUCTION` | Under the CTA of the language picker / onboarding content pages | on |
| Native large | `ONBOARDING_FULL_1` | Full onboarding page after content page `position` (2 = between brand and goal) | on |
| Native large | `ONBOARDING_FULL_2` | Same, `position` 3 = after the last page | off |
| Rewarded | `UNLOCK_REMOTE`, `UNLOCK_IPTV_TIME`, `UNLOCK_IPTV_SOURCE` | The "watch an ad" option in the premium gate | on |

Rules kept by the code:
- In-app interstitials (`FEATURE_OPEN`, `IPTV_*`) show only when one is already loaded, so a tap never waits on an ad request; interstitials are ≥45 s apart and the module skips every second request (repeat policy). Never on dock tab switches or remote key presses.
- Onboarding ad pages join the pager only while the user is on the first page (positions are ≥1, so the page in view never moves). If no ad fills the page, it shows the "Zero ads" premium pitch instead of a blank page.
- `widgets/AppOpenAdGate.kt` keeps the app-open ad off returns the app caused (file/media pickers, share sheets, screen sharing, store, mail, links) and off returns while IPTV plays or a cast session has media.

## Premium (what free users do not get)

| Feature | Free | Premium | Rewarded alternative |
|---|---|---|---|
| Ads | Yes | None | — |
| Remote keyboard, touchpad, TV app launcher | Locked (crown badge) | Yes | Unlock that feature for `rewardUnlockMinutes` (30) this session |
| IPTV saved sources | `IPTV_SETTINGS.maximumSources` = 1 | Unlimited | +`rewardSources` (1) slot this session |
| IPTV playback per day | `maximumWatchSeconds` = 1800 | Unlimited | +`rewardWatchSeconds` (1800) today |
| D-pad, volume, power, channels, media keys, keypad, Cast tab, mirroring guide, guide/reminders | Yes | Yes | — |

`PREMIUM_SETTINGS.lockedFeatures` lists the locked remote features (`REMOTE_KEYBOARD`, `REMOTE_TOUCHPAD`, `REMOTE_APPS`); an empty list or unreadable JSON locks nothing. IPTV limits of 0 mean unlimited. Hitting a lock or limit opens `PremiumGateDialog` (`ui/PremiumGate.kt`): Premium first (paywall source `remote_lock` / `iptv_limit`), then the rewarded option when that placement is enabled. The grant is applied only after the SDK reports the reward; an unavailable ad shows a notice. Click events: `ec_premium_gate`, `ec_premium_gate_upgrade`, `ec_premium_gate_reward`, `ec_premium_gate_rewarded` with `feature`.

Domain and tests: `domain/Premium.kt`, `IptvLimits.canAddSource(…, extraSources)`, `ui/onboarding/OnboardingPages.kt`; `PremiumTest`, `IptvViewModelTest.rewardedGrantsAddWatchTimeAndASourceSlot`. Ad display itself needs devices with real ad units.
