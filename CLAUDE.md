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
Single-activity Compose (`MainActivity`). Theme in `ui/theme/` (Material 3).
**Brand (Phase 2) = navy `#15294D` + gold `#F2A93B`**: tokens in `Color.kt`
(`BrandNavy`/`BrandGold`/… + GPS accuracy tokens `AccuracyGood/Avg/Poor` with the
`accuracyColor(meters)` helper). `Theme.kt` = light(navy primary)/dark(gold primary)
schemes; dynamic colour is OFF by default so the brand is consistent. Screens use
`MaterialTheme.colorScheme.*` only — NO hardcoded brand `Color(0x..)` — so brand
changes live entirely in `ui/theme/`. Strings in `res/values/strings.xml` (English) with Hindi
in `res/values-hi/strings.xml` — every new UI string MUST be added to BOTH files
(localization shipped in US-013). In-app language toggle: `locale/AppLocale.kt`
(`AppLanguage` + `LocaleStore` + `Context.wrapWithStoredLocale()`); `MainActivity`
applies it in `attachBaseContext`, Settings persists + `recreate()`s to apply live.
Stamp-affecting format prefs (US-014) live in `settings/AppSettings.kt`
(`CoordinateFormat` decimal/DMS, `TimeFormat` 24/12h, `LayoutPreset` (P2-US-010),
`StampPosition` top/bottom (P2-US-011), `AppSettingsStore`); they work because
`StampData` CARRIES the value (defaulted),
snapshotted from the store at shutter-press in `CameraPreview` — so a setting change
affects the next capture. `LayoutPreset` is the field-set chooser (6 presets =
boolean `showMap/showAddress/showCoords/showWeather` combos, picked in a Settings
radio group); `drawStamp`/`PhotoStorage` gate the map and each template gates its
address/coords/weather lines off these flags. Custom fields (project/site, note,
logo) + date-time ALWAYS render, independent of the preset. `showMap` composes with
`StampTemplate.usesMap` (Minimal never draws a map regardless of preset).
`StampPosition` (P2-US-011, WYSIWYG) anchors the burned panel to the TOP or BOTTOM
edge: each `drawClassic/drawMinimal/drawFieldReport` computes `panelHeight` then
`panelTop = if (top) 0f else height - panelHeight` (header band / map / lines are all
relative to `panelTop`, so they work both ways). The live `LocationInfoOverlay` GPS
card is placed at the SAME edge in `CameraPreview` (top → `Alignment.TopCenter`;
bottom → first child of the bottom controls Column, above the mode chips) so the
on-screen preview matches the photo. The position is read once with `remember` at
camera-tab entry (NavHost re-composes the tab on return, so a Settings change applies).
NON-stamp viewfinder prefs also live in `AppSettings.kt`: `loadShowGrid/saveShowGrid`
(P2-US-012) — a plain boolean (not a `StampData` field; it only affects the live
preview, never the burned photo). The Settings UI for a boolean uses `ToggleRow`
(label + Material3 `Switch`) inside an `OptionCard`, vs the radio `OptionRow` used for
enums. Viewfinder overlays (P2-US-012) are drawn in `CameraPreview`: `RuleOfThirdsGrid`
(a `Canvas` drawing 2 vertical + 2 horizontal lines at the thirds, white@0.3, gated on
`showGrid`) and `ViewfinderInfoOverlay` (bottom-left HUD = a live-ticking date/time line
via `LaunchedEffect`+`delay(1000)` using the `TimeFormat` pattern, plus an `Altitude: Nm`
line when the fix has one — 12sp white with a `Shadow`). Altitude rides on
`GpsFix.altitudeMeters` (nullable, from `Location.hasAltitude()`/`.altitude` in
`rememberCurrentLocation`). The HUD is the first (left-aligned) child of the bottom
controls Column so it never overlaps the variable-height controls.

## Weather on the stamp (P2-US-009)
`location/WeatherProvider.kt`: `fetchWeather(lat,lng): Weather?` hits Open-Meteo's
free `current_weather` API (NO key) via `HttpURLConnection` on `Dispatchers.IO` with
an 8s timeout, parses with `org.json`, and returns null on ANY failure (offline / HTTP
/ parse) — same graceful-null contract as `OsmStaticMapProvider`. `Weather`
(temperatureC + WMO weatherCode) maps the code to a coarse condition string and
`describe(context)` renders "28°C · Clear". `LocationUiState.Available.weather`
carries it; `rememberCurrentLocation` fetches it after the geocode. `StampData.weather`
is a PRE-FORMATTED string snapshotted at shutter (`available.weather.describe(context)`)
so PhotoStamp draws it with no Context — all 3 templates render it. `weather_*` strings
in BOTH values + values-hi. `INTERNET` perm was already present (map tiles).

## Ads (US-015)
AdMob lives in `ads/`: `Ads` (object) holds the configurable test ad unit ids +
the SINGLE global `adsEnabled` Compose-state flag (persisted to SharedPreferences;
the US-016 remove-ads IAP just calls `Ads.setEnabled`). Every placement gates on
`Ads.adsEnabled`. `BannerAd()` (adaptive `AdView` via AndroidView) renders nothing
when disabled — placed at the bottom of the Gallery. `InterstitialAdManager`
(remembered in `CameraPreview`, preloaded once) shows an interstitial after every
`CAPTURES_PER_INTERSTITIAL` (5) captures, called from the POST-save callback so an
ad can never block/delay a capture; all SDK calls are `runCatching`-guarded.
`MainActivity.onCreate` calls `Ads.initialize(this)`. Manifest needs the AdMob
`APPLICATION_ID` meta-data (Google's test app id committed).
GOTCHA — guava/ListenableFuture conflict: `play-services-ads` pulls full Guava,
which makes Gradle dedupe CameraX's `listenablefuture:1.0` down to the empty stub,
dropping `ListenableFuture` off the COMPILE classpath (CameraX won't compile). Fix
= declare `implementation(libs.guava)` directly so the real class stays on compile.

## PDF photo-proof report (US-017)
Export lives in `media/PdfReport.kt` using the FRAMEWORK `android.graphics.pdf.PdfDocument`
(NO dependency). `generatePhotoReport(context, photos, projectName, isPremium)` renders
one A4 page (595×842 pts) per photo — header (project/site from `CustomFieldsStore` +
date range), the already-stamped photo decoded downsampled (`inSampleSize`) to fit, a
caption (filename / capture date / coords joined from `GeotagStore.loadAll`), and a
footer. FREE tier (`!Premium.isPremium`) = `take(FREE_REPORT_MAX_PHOTOS=3)` + a diagonal
translucent "gpstools" watermark per page; PREMIUM = unlimited, no watermark. Saved to
Downloads/gpstools via `MediaStore.Downloads` (RELATIVE_PATH+IS_PENDING) on Q+ /
`MediaStore.Files` DATA path on API24-28 → content Uri; `openReport()` fires an
ACTION_VIEW chooser. PDF text is English (a document graphic, intentionally NOT
localized — same rationale as the FIELD REPORT stamp). Run it OFF the main thread (it
touches the resolver + decodes bitmaps). Gallery selection mode uses `combinedClickable`
(`@OptIn(ExperimentalFoundationApi)`); the viewer state var is `viewing`, the selection
set is `selectedUris`. Verify a PDF with `qlmanage -t -s 1000 -o /tmp x.pdf` +
`file x.pdf` (reports page count) after `adb pull` from /sdcard/Download/gpstools.

## Premium / one-time IAP (US-016)
Google Play Billing (`libs.billing.ktx`, billing-ktx 7.1.1). `billing/Premium.kt`
(object, mirrors `Ads`) is the single source of truth: global Compose-state
`isPremium` flag persisted to SharedPreferences + `grant(context)` (the sink for
every purchase/restore — sets the flag AND calls `Ads.setEnabled(false)` so the
remove-ads side is automatic) + `load(context)` (call once in `MainActivity.onCreate`).
`billing/BillingManager.kt` wraps `BillingClient` (one instance per use site; each
holds its own connection): `start()` connects then auto-restores via
`queryOwnedPurchases()` (handles reinstall) + loads `ProductDetails`;
`launchPurchase(activity)` returns false when not connected / details not loaded
(caller toasts "unavailable"); the `PurchasesUpdatedListener` + restore both funnel
PURCHASED purchases into `Premium.grant` + acknowledge. ALL SDK calls runCatching-
guarded. `MainActivity` creates a `BillingManager` in onCreate (restore) and
`release()`s in onDestroy. Premium templates: `StampTemplate.premium` flag
(FIELD_REPORT = true); the picker in `CameraPreview` shows a `Icons.Filled.Lock`
leadingIcon + routes taps to a purchase-launch callback while `!Premium.isPremium`,
and capture falls back to `StampTemplate.DEFAULT` if a premium template is selected
without the entitlement. Settings has a `PremiumSection` (buy/restore, or "unlocked"
status). DEBUG verify: `buildConfig = true` is enabled so a `BuildConfig.DEBUG`-only
"simulate/reset purchase" `OutlinedButton` (calls `Premium.grant`/`revokeForDebug`)
unlocks on an emulator with no configured Play product. The BILLING manifest
permission is merged in by the billing library. NOTE: the `PurchasesUpdatedListener`
must be created via the `PurchasesUpdatedListener { .. }` SAM ctor, NOT a bare
`{ r, p -> }` lambda — the latter infers a non-Unit return and fails to compile.

## Pro subscription (US-018)
Recurring "Pro" subscriptions (`pro_monthly`, `pro_yearly`) unlock unlimited /
watermark-free PDF reports + batch export. `billing/Subscription.kt` (object,
mirrors `Premium`) is the entitlement: Compose-state `isSubscribed` + `activePlan`
persisted to SharedPreferences (`subscription_settings`) + `grant(context, productId)`
+ `clear(context)` + `load(context)` (called once in `MainActivity.onCreate`, like
`Premium.load`). `Subscription` is INDEPENDENT of `Premium` — it does NOT touch ads.
The combined gate is `Entitlements.hasUnlimitedReports = Premium.isPremium ||
Subscription.isSubscribed` (same file); `GalleryScreen` passes THAT (not
`Premium.isPremium`) as the `isPremium` arg of `generatePhotoReport` — so EITHER the
one-time IAP or the subscription gives uncapped, watermark-free reports. The SAME
`BillingManager` now handles BOTH product types: `start()`/`queryOwnedPurchases()`
query INAPP **and** SUBS (so reinstall restores either); `querySubscriptionDetails()`
loads the SUBS `ProductDetails`; `subscriptionPrice(productId)` reads the first
offer's first pricing-phase `formattedPrice`; `launchSubscriptionPurchase(activity,
productId)` builds `BillingFlowParams` with `.setOfferToken(offerDetails.first().offerToken)`
(REQUIRED for SUBS — without an offer token it returns false); `handlePurchase`
routes by `purchase.products` → `Premium.grant` (INAPP) or `Subscription.grant` (SUBS).
Like Premium, restore NEVER auto-revokes (avoids yanking access on a transient
offline launch). Paywall = `ui/screens/PaywallScreen.kt` `PaywallDialog` — a centered
`Dialog`+`Card` (NOT fullscreen, so the edge-to-edge inset-clipping gotcha doesn't
apply) listing the 3 benefits + monthly/yearly India pricing (live price when loaded,
else static `pro_price_*` strings) + subscribe/restore + a `BuildConfig.DEBUG`
simulate button. `SettingsScreen` now creates ONE shared `BillingManager` (lifted out
of `PremiumSection`) passed to both `PremiumSection(billing)` and a new
`ProSubscriptionSection(billing)` (Go-Pro→paywall, or "Pro active" + debug cancel).
DEBUG verify without a Play product: the paywall's "(debug) Simulate subscription"
calls `Subscription.grant(.., YEARLY_PRODUCT_ID)`. New `pro_*` strings in BOTH
values + values-hi.
