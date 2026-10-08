# TV Space development rules

Inherited architecture/design principles from `../PetTranslator/CLAUDE.md`; flow reference `../ElectronicsRepair`. See `docs/plans/ROADMAP.md` for adaptation and remaining work.

- Keep existing modules `:androidApp`, `:shared`, `iosApp`; platform entrypoints already use Shared.
- UI → domain → repository; platform APIs only in platform source sets. ViewModel exposes StateFlow; children receive values and callbacks.
- Externalize app strings. Development EN/VI; complete 13 template locales before international release. No text baked into imagery.
- Use TvColors/TvDimens and Material typography. Artwork WebP, UI icons scalable, source launcher SVG kept in design/.
- Never claim support or connection without a successful protocol response. Native Cast settings/AirPlay instructions are not a custom mirroring engine.
- Sony manual IP only on RFC1918; PSK session-only; no credential/playlist URL logging. IPTV content is supplied by the user.
- Do not copy sibling project code or alter tuntech submodules. Import whole modules unchanged when needed. No imported Firebase/ad IDs/secrets.
- Pure domain changes require commonTest. Check Android and iOS compilation; runtime verification is separate from compile success.
- Document implemented behavior in docs/context; unfinished work in docs/plans.

Build:
```sh
./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```
