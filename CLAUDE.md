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
Single-activity Compose (`MainActivity`). Nav = bottom `NavigationBar` over a
`NavHost`; the 5 top-level tabs are the `Destination` enum (`ui/navigation/Destinations.kt`)
— `Home`/Camera/Gallery/Map/Settings. **`Home` is the start destination** (P2-US-015
dashboard). Tab clicks AND the Home tiles both go through `NavHostController.navigateTopLevel`
(in MainActivity — `popUpTo(start){saveState}` + `launchSingleTop` + `restoreState`).
`HomeScreen` (`ui/screens/HomeScreen.kt`) = navy-gradient landing with a 2×2 tile grid
(Camera/Gallery/Map/Reports); each tile carries a `Destination.route` and the "Reports"
tile routes to `Gallery.route` (the PDF report export lives there, US-017). It takes
`onTileClick:(route)->Unit` — no NavController dependency in the screen.
Theme in `ui/theme/` (Material 3).
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
Date/time on the stamp is toggleable (P2-US-017): `AppSettingsStore.load/saveShowDateTime`
(default true) snapshots into `StampData.showDateTime` and gates the `dateLine` in ALL 3
templates (`drawClassic`/`drawMinimal` early-return when their line list ends up empty —
e.g. Lat/Lng preset, no fix, date/time off). The custom watermark (P2-US-017) is a
`watermark` text field on `CustomFields` (persisted via `CustomFieldsStore.saveFields`,
edited in `CustomFieldsSheet`) → `StampData.watermark`; `drawWatermark` draws it
bottom-right over a rounded backing in `drawStamp`, independent of the template (mirrors
the top-right `drawLogo`). Both ship `settings_datetime_*` / `custom_field_watermark`
strings in BOTH values + values-hi.
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
controls Column so it never overlaps the variable-height controls. The HUD's altitude
line is now the combined altitude+facing line (P2-US-013, below).

## Pro gestures (P2-US-018)
The shutter's capture body is extracted into a local `captureNow()` fun in `CameraPreview`
(shared by every trigger). The shutter is a NON-clickable `Surface` (container overload,
keeps the ring/elevation/border) with a `Modifier.pointerInput { detectTapGestures(onTap =
captureNow, onLongPress = startSelfTimer) }` — so a **tap** captures immediately (one-tap,
US-002 preserved) and a **long-press** starts a 3-second self-timer. `startSelfTimer` runs a
`scope.launch` loop (`countdown = 3..1`, `delay(1000)`) then `captureNow()`; `countdown`
(nullable Int state) renders a large centered number over the preview while it ticks.
Both `captureNow`/`startSelfTimer` early-return if `isCapturing || countdown != null`.
**Swipe left** on the viewfinder opens the last capture: the `AndroidView` preview has a
`pointerInput { detectHorizontalDragGestures(...) }` that accumulates the drag and, on
`onDragEnd` past ~-80px, calls `openLastPhoto(context)` (in `PhotoGallery.kt` — queries
newest-first off `Dispatchers.IO`, fires an `ACTION_VIEW` image chooser; returns false →
toast `camera_no_photos` when none exist). `camera_timer_countdown`/`camera_no_photos`
strings ship in BOTH values + values-hi.

## Altitude + compass facing (P2-US-013)
`location/CompassProvider.kt`: `rememberCompassBearing(): State<Float?>` subscribes to the
device `TYPE_ROTATION_VECTOR` sensor (via `DisposableEffect`;
`SensorManager.getRotationMatrixFromVector` + `getOrientation`, azimuth radians → 0–360°
clockwise from N), emitting null when the device has no such sensor (no permission needed
— same graceful-null contract as weather/map). `bearingToCardinalRes(deg)` maps a bearing
to one of 8 cardinal string resources (`compass_n`..`compass_nw`).
`formatAltitudeFacing(context, altitude, bearing)` builds the combined
"Altitude 342m · Facing NE" line from whichever pieces exist (null if neither). The HUD
(`ViewfinderInfoOverlay`) shows that line live; `StampData.altitudeFacing` carries the
pre-formatted snapshot (taken at shutter), rendered in ALL 3 templates — Classic/Minimal
after weather, Field Report as an `ALTITUDE / FACING` labelled row. It is ALWAYS rendered
when present (like project/note/date-time), NOT gated by the `LayoutPreset`. Hindi cardinal
abbrevs (उ/उपू/पू/…) live in `values-hi`.

## EXIF GPS metadata (P2-US-014)
The capture pipeline re-encodes the JPEG (decode → stamp → encode), so the original
frame's EXIF is lost — we write fresh, machine-readable EXIF GPS onto the SAVED file.
Dependency `androidx.exifinterface:exifinterface` (the platform `android.media.ExifInterface`
can't reliably write to a FileDescriptor; the androidx backport does + adds GPS helpers).
`StampData.altitudeMeters` carries the RAW altitude (separate from the pre-formatted
`altitudeFacing` line) for EXIF. `PhotoStorage.writeGpsExif(context, uri, stamp)` opens the
MediaStore uri with a `"rw"` FileDescriptor → `ExifInterface(fd)` → `setLatLong` +
`setAltitude` + `TAG_GPS_DATESTAMP`/`TAG_GPS_TIMESTAMP` (UTC; timestamp is the rational
`h/1,m/1,s/1`, NOT "HH:mm:ss") when a fix exists, plus `TAG_DATETIME`/`_ORIGINAL`/
`_DIGITIZED` (LOCAL, "yyyy:MM:dd HH:mm:ss") always. Called in `saveBitmap` AFTER `compress`
and BEFORE clearing `IS_PENDING` on Q+ (entry still writable), wrapped in `runCatching` so
an EXIF write failure never discards the photo (the burned-in stamp is still the primary
proof). Verify after `adb pull` with `python3` + Pillow (`Image._getexif()` +
`PIL.ExifTags.GPSTAGS`); set emulator altitude via the 3rd arg of `adb emu geo fix`.

## Photo grid / collage (P2-US-016)
`media/PhotoCollage.kt`: `createPhotoCollage(context, photos): Uri?` combines 2–4
(`COLLAGE_MIN_PHOTOS`/`COLLAGE_MAX_PHOTOS`) selected gallery photos into ONE 1200px-wide
grid image. Layout = 2 columns, `rows = ceil(n/2)` (2 = side-by-side, 4 = 2×2, 3 = 2+1).
Each photo is drawn **fit-inside (contain, NEVER cropped)** its square cell on a white bg
so every photo's burned-in stamp is preserved (a center-crop would chop the top/bottom
stamp). Saved as `GPS_collage_<ts>.jpg` into `Pictures/gpstools` via the same scoped-
storage `saveBitmap` pattern, keeping the `GPS_` prefix so `queryCapturedPhotos`
(DISPLAY_NAME LIKE 'GPS_%') lists it in the in-app gallery (no GeotagStore entry — it has
no single location). `shareImage(context, uri, title)` = generic `ACTION_SEND` image share
(the existing `sharePhoto` only takes a `CapturedPhoto`). `GalleryScreen` selection mode
gained a 'Make grid' (`GridView` icon) action beside Export PDF; `createCollageSelected()`
validates the 2–4 count, reuses the one `generating` overlay via a `generatingLabel: Int`
string-res state (shared by PDF + collage), runs off the main thread, then refreshes +
fires the share sheet. `collage_*` strings in BOTH values + values-hi.

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
