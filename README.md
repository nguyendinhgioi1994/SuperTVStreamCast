# TV Space · SuperTVStreamCast

Kotlin Multiplatform app for Android and iOS: television controls, system screen sharing and user-provided IPTV playlists.

Current foundation includes original Midnight Cinema artwork/icons, three-step onboarding, brand selection, five-tab Home shell, compatible Sony BRAVIA IP/PSK remote, bounded M3U import/search/favorites, native Android/iOS video players and system screen-sharing guidance. Sony remote has mocked protocol tests; TV hardware compatibility is still to be verified. Other direct vendor remotes are tracked in the roadmap, with explicit unavailable messages in the UI.

## Build

```sh
./gradlew :androidApp:assembleDebug
./gradlew :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Android APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

iOS: open `iosApp/iosApp.xcodeproj` and run on an iOS simulator/device. Native framework is `Shared`. Code currently compiles for iOS; runtime validation remains open.

## Project documents

- [Roadmap and market research](docs/plans/ROADMAP.md)
- [ASO keyword clusters by TV brand](docs/plans/ASO_KEYWORDS.csv)
- [Design system](DESIGN.md)
- [Coding rules adapted from PetTranslator](CLAUDE.md)
- [Implemented feature behavior](docs/context/foundation.md)
- [Validation and limitations](docs/context/validation.md)

All imported TV channels/content must be provided by the user. Library/favorites and TV credentials remain in session memory in the current foundation; onboarding settings persist locally. Development localization is English/Vietnamese. Production readiness, remaining vendor support, advanced casting and monetization are separate roadmap gates.
