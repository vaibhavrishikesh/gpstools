# Play Store release prep — Gps Camera Location

Package: `com.gpstools.camera` · versionName `0.3.0` · versionCode `4`

## 1. Build artifacts (already generated)

| File | Path | Use |
|------|------|-----|
| **AAB** (upload this) | `app/build/outputs/bundle/release/app-release.aab` | Play Console → Production / Internal testing |
| Signed APK | `app/build/outputs/apk/release/app-release.apk` | Sideload / WhatsApp share |
| Desktop copies | `~/Desktop/GpsCameraLocation-v0.3.0.aab` and `.apk` | hand-off |

Rebuild anytime: `./gradlew clean bundleRelease assembleRelease`
(signed automatically via `keystore.properties` → `release.keystore`).

## 2. Signing — IMPORTANT

- The release artifacts are signed with **`release.keystore`** (alias `gpstools`).
  Credentials live in `keystore.properties` (gitignored). **Both files are NOT in
  git — back them up safely. If lost, you can never update the app under the same
  upload key.**
- Recommended: enrol in **Play App Signing** (default for new apps). Then
  `release.keystore` becomes the *upload key*; Google holds the real app-signing key.
- Upload key fingerprints (for reference / API console SHA registration):
  - SHA-1:  `20:FC:AC:CA:D4:5F:2E:DE:B4:9B:3A:91:50:36:41:36:3B:E8:18:7C`
  - SHA-256: `66:AA:67:85:15:48:F8:65:48:32:2B:72:66:F7:81:94:B7:EA:0F:12:6F:7C:CD:08:A3:31:BC:CA:53:36:CB:5F`

## 3. Store listing assets

| Asset | Spec | Status |
|-------|------|--------|
| App icon | 512×512 PNG, 32-bit | ✅ `ic_play_store_512.png` (white bg, navy+gold logo — verified) |
| Feature graphic | 1024×500 PNG/JPG | ❌ TODO — required |
| Phone screenshots | 2–8, min 320px, 16:9 or 9:16 | ❌ TODO — capture Home / Camera / Map / Report |
| (opt) 7"/10" tablet shots | — | optional |
| Short description | ≤80 chars | ❌ draft below |
| Full description | ≤4000 chars | ❌ draft below |
| App category | "Photography" or "Tools" | decide |
| Contact email | required | `vaibhavgupta.rishikesh@gmail.com` |
| Privacy policy URL | **required** (app uses camera+location) | ❌ TODO — must host a page |

### Draft short description
> Geotag your photos with GPS location, address, map, date-time & weather — field-proof.

### Draft full description (starter)
> Gps Camera Location stamps every photo with exact GPS coordinates, address, a map
> thumbnail, altitude, compass direction, date-time and live weather — proof of where
> and when a photo was taken. Built for field work: construction, surveying, site
> audits, insurance, delivery and inspection.
>
> • One-tap geotagged capture — location, address, map & timestamp burned onto the photo
> • Multiple stamp styles (Classic, Minimal, Field Report)
> • Custom fields — project/site name, notes, company watermark & logo
> • NavIC + GPS/GLONASS/Galileo high-accuracy positioning (India-ready)
> • Map view of all your geotagged photos
> • Export PDF photo-proof reports
> • EXIF GPS written into each file
> • Hindi & English
>
> Free to use with ads. Go Pro for unlimited watermark-free PDF reports.

## 4. Play Console forms (required before publishing)

- [ ] **Data safety** form — declare: Location (precise), Photos, collected/stored
      on-device; whether shared (ads SDK may collect device/ads data — declare AdMob).
- [ ] **Content rating** questionnaire (likely "Everyone").
- [ ] **Target audience & content** — not directed at children.
- [ ] **App access** — all features available without login (note this).
- [ ] **Ads** — declare the app **contains ads** (AdMob).
- [ ] **Permissions** — CAMERA, ACCESS_FINE_LOCATION justified in listing.
- [ ] Government-app / financial declarations: N/A.

## 5. Code TODOs before a REAL public launch (currently stubbed/test-mode)

- [x] **AdMob**: real App ID + Banner unit wired (release build only; debug keeps
      TEST ids). App ID `ca-app-pub-4765907187067298~4557234825`, Banner
      `ca-app-pub-4765907187067298/2339436450`. **TODO: create an Interstitial ad
      unit in AdMob and replace the TEST interstitial id in `build.gradle.kts`.**
- [ ] **Play Billing products**: create in Play Console and verify IDs match —
      `remove_ads_premium` (IAP), `pro_monthly`, `pro_yearly` (subs). (`US-016/018`.)
- [ ] Remove the DEBUG-only "simulate purchase / subscription" affordances from the
      release flow (they're already `BuildConfig.DEBUG`-gated — confirm).
- [ ] Decide final `versionName` for launch (e.g. `1.0.0`) and bump `versionCode`.
- [x] `ic_play_store_512.png` background verified white (navy+gold logo).

## 6. Suggested rollout

1. Upload AAB to **Internal testing** track first → install via the opt-in link on a
   couple of real devices (incl. an Android 12+ phone to confirm the splash).
2. Fill all the Console forms above.
3. Promote to **Closed/Open testing**, then **Production** with a staged rollout.
