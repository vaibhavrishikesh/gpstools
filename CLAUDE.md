# gpstools — Build & Run Notes (for agents)

Native Android app. Code lives in the repo root (`app/`), NOT in `scripts/`.

## Toolchain (pinned — change deliberately)
- Gradle **8.9** (wrapper committed at `gradle/wrapper/`)
- Android Gradle Plugin **8.5.2**, Kotlin **2.0.21**
- Compose: Kotlin 2.0 compiler plugin (`org.jetbrains.kotlin.plugin.compose`), Compose BOM `2024.10.01`
- `compileSdk`/`targetSdk` = **35**, `minSdk` = **24**, Java/JVM target **17**
- Package / applicationId: `com.gpstools.camera`

## Dependencies = version catalog
All versions/libs go in `gradle/libs.versions.toml`. Reference in build files as
`libs.<alias>` / `libs.plugins.<alias>`. Add a new lib there first, then use it.

## Quality gate
```
./gradlew assembleDebug      # MUST pass before commit
```
JDK 17 is required (toolchain detects system Java 17). No `gradle` on PATH — always use `./gradlew`.

## Launcher icons
Vector-only (no PNGs): `mipmap-anydpi-v26/` has the adaptive icon; `mipmap-anydpi/`
holds vector fallbacks for API 24–25. If you change the icon, update both.

## Emulator verification
SDK at `~/Library/Android/sdk`. An emulator (`emulator-5554`, AVD `SharePoster_API35`)
is usually already running. Use full path `~/Library/Android/sdk/platform-tools/adb`.
```
~/.../adb install -r app/build/outputs/apk/debug/app-debug.apk
~/.../adb shell am start -n com.gpstools.camera/.MainActivity
~/.../adb exec-out screencap -p > /tmp/shot.png
```
- The emulator is slow/loaded: a **cold** first launch can hit an ART "start timeout"
  and get killed (NOT an app crash). Re-launch once dex/oat is warmed; poll `adb shell
  pidof com.gpstools.camera` until alive.
- A separate `com.shareposter.app` also runs here; its ANR dialog can overlay the
  screen. `adb shell am force-stop com.shareposter.app` to clear it, then relaunch ours.
- Confirm foreground via `adb shell dumpsys window | grep mCurrentFocus`.

## App structure
Single-activity Compose (`MainActivity`). Theme in `ui/theme/` (Material 3,
dynamic color on API 31+). Strings in `res/values/strings.xml` (English) with Hindi
in `res/values-hi/strings.xml` — every new UI string MUST be added to BOTH files
(localization shipped in US-013). In-app language toggle: `locale/AppLocale.kt`
(`AppLanguage` + `LocaleStore` + `Context.wrapWithStoredLocale()`); `MainActivity`
applies it in `attachBaseContext`, Settings persists + `recreate()`s to apply live.
