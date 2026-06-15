# PRD: gpstools — Geotag GPS Camera (MVP)

## Introduction

gpstools is a native Android geotag camera app. It captures a photo and burns a
clean, good-looking **location stamp** onto it — latitude/longitude, address, date/time,
and a small map thumbnail — plus optional custom fields (note, project/site name, logo).

The opportunity: the category leader *GPS Map Camera* has **130M downloads but only 3.34★**.
The market is proven and huge; the leaders are beatable on **quality, speed, and design**.
We win casual users with a clean, ad-light, beautiful app, and we earn revenue from
**field-proof** users (construction, survey, NGO, audit, real estate) who pay for
tamper-evident **PDF photo-proof reports**.

**Strategy (locked):** Hybrid positioning · Native Android (Kotlin + Jetpack Compose) ·
Stacked monetization (ads + one-time IAP + subscription) · India-first (NavIC, Hindi/regional).

This PRD is sized for the **Ralph autonomous agent loop**: each user story is small enough
for one focused session and has verifiable acceptance criteria.

## Goals

- Capture a photo with an accurate, attractive location/time stamp in under ~5 seconds.
- Beat incumbents on look-and-feel and speed (target rating ≥ 4.3★).
- Ship a free, ad-supported casual experience.
- Monetize via: one-time IAP (remove ads + premium templates) and subscription (unlimited PDF reports, batch export).
- India-first: Hindi + English at launch, NavIC-aware, India-appropriate pricing.
- Reliable on a wide range of Android devices (min SDK 24 / Android 7.0).

## The 5 questions, answered (for context)

- **Kya chahiye (what):** a fast, beautiful geotag camera + a premium PDF photo-proof report.
- **Kyon chahiye (why):** 150M+ proven installs in category, leader stuck at 3.34★ = quality gap.
- **Kaise (how):** native Android Compose app, camera + fused location + on-canvas stamp render.
- **Kaise pahunchenge (reach):** India ASO on geotag/GPS-camera keywords, NavIC + Hindi angle, field-work audience.
- **Kaise milega (revenue):** free+ads → IAP remove-ads/premium templates → subscription for reports/batch.

---

## User Stories

> Build order = priority. Android verification means: app builds with
> `./gradlew assembleDebug` and the screen/behavior is checked on an emulator/device.

### Phase 0 — Foundation

### US-001: Project scaffold
**Description:** As a developer, I need a buildable Android Compose project so all later work has a base.

**Acceptance Criteria:**
- [ ] Android Studio project: Kotlin, Jetpack Compose, single-activity, Material 3
- [ ] Package id `com.gpstools.camera`, app name "gpstools", minSdk 24, targetSdk current
- [ ] App launches to a placeholder Home screen on the emulator
- [ ] `./gradlew assembleDebug` succeeds (build passes)
- [ ] Verify launch on Android emulator

### US-002: Navigation skeleton
**Description:** As a user, I want to move between the main screens of the app.

**Acceptance Criteria:**
- [ ] Bottom navigation with three tabs: Camera, Gallery, Settings
- [ ] Each tab shows a distinct placeholder screen
- [ ] Camera is the default/start destination
- [ ] Build passes; verify navigation on emulator

### US-003: Runtime permissions flow
**Description:** As a user, I must grant camera + location so the core feature works.

**Acceptance Criteria:**
- [ ] Request CAMERA and ACCESS_FINE_LOCATION at the right time (on entering Camera tab)
- [ ] Clear rationale UI if denied; deep-link to app settings if permanently denied
- [ ] App does not crash when permissions are denied; shows a helpful empty state
- [ ] Build passes; verify grant + deny paths on emulator

### Phase 1 — Camera core

### US-004: Camera preview
**Description:** As a user, I want a live camera preview so I can frame my shot.

**Acceptance Criteria:**
- [ ] CameraX preview fills the Camera screen
- [ ] Shutter button visible; front/back camera toggle works
- [ ] Handles lifecycle (pause/resume) without crashing
- [ ] Build passes; verify preview on emulator (or device)

### US-005: Acquire location + reverse-geocoded address
**Description:** As a user, I want my current coordinates and address detected automatically.

**Acceptance Criteria:**
- [ ] Use FusedLocationProvider to get lat/long (high accuracy) with timeout + fallback
- [ ] Reverse-geocode to a human address (Geocoder); show "locating…" then result
- [ ] Show accuracy in meters; graceful message if location unavailable
- [ ] Build passes; verify a real lat/long + address render on screen

### US-006: Capture photo to storage
**Description:** As a user, I want to take a photo and have it saved.

**Acceptance Criteria:**
- [ ] Shutter captures a full-resolution image via CameraX ImageCapture
- [ ] Saved to app-scoped storage (and MediaStore on capture-complete)
- [ ] Capture works without a stamp yet (stamp added in US-007)
- [ ] Build passes; verify a captured file exists and is viewable

### Phase 2 — The differentiator: the stamp

### US-007: Render a basic location stamp onto the photo
**Description:** As a user, I want location/time info burned onto my photo so it's proof-ready.

**Acceptance Criteria:**
- [ ] After capture, composite an overlay onto the bitmap: address, lat/long, date/time
- [ ] Overlay is legible (semi-transparent panel, readable type) and saved into the final image
- [ ] Stamp renders correctly in both portrait and landscape captures
- [ ] Build passes; verify the saved image visibly contains the stamp on emulator

### US-008: Map thumbnail on the stamp
**Description:** As a user, I want a small map showing where the photo was taken.

**Acceptance Criteria:**
- [ ] Fetch a static map thumbnail for the coordinates (provider chosen in Tech Considerations)
- [ ] Thumbnail composited into the stamp area; falls back gracefully if offline
- [ ] Build passes; verify the map thumbnail appears in the saved image

### US-009: Multiple stamp templates
**Description:** As a user, I want to choose how my stamp looks.

**Acceptance Criteria:**
- [ ] At least 3 visually distinct, good-looking templates (e.g. Classic, Minimal, Field-Report)
- [ ] Template picker on the Camera screen; selection persists
- [ ] Each template lays out address/coords/time/map cleanly without overlap
- [ ] Build passes; verify switching templates changes the saved stamp

### US-010: Custom fields on the stamp
**Description:** As a field user, I want to add a note, project/site name, and my logo.

**Acceptance Criteria:**
- [ ] Editable fields: free-text note, project/site name (persist between captures)
- [ ] Optional logo image picked from gallery and shown on the stamp
- [ ] Fields render in the stamp and are saved into the image
- [ ] Build passes; verify custom fields appear in the saved image

### Phase 3 — Organize

### US-011: In-app gallery of captured photos
**Description:** As a user, I want to browse the geotagged photos I've taken.

**Acceptance Criteria:**
- [ ] Gallery tab shows a grid of captured images (newest first)
- [ ] Tap opens a full-screen viewer with the captured metadata
- [ ] Share + delete actions per photo
- [ ] Build passes; verify grid, viewer, share, delete on emulator

### US-012: Map view of captured photos
**Description:** As a user, I want to see my photos plotted on a map by where I took them.

**Acceptance Criteria:**
- [ ] A map shows pins for captured photos with coordinates
- [ ] Tapping a pin previews that photo
- [ ] Handles the empty state (no geotagged photos yet)
- [ ] Build passes; verify pins + preview on emulator

### Phase 4 — Localization

### US-013: Localization scaffold (Hindi + English)
**Description:** As an Indian user, I want the app in Hindi or English.

**Acceptance Criteria:**
- [ ] All user-facing strings in resources; English + Hindi translations provided
- [ ] In-app language toggle in Settings that applies immediately and follows device by default
- [ ] No hardcoded strings in new UI
- [ ] Build passes; verify switching to Hindi changes the UI on emulator

### US-014: Settings (units, NavIC info, about)
**Description:** As a user, I want to control formats and learn about accuracy.

**Acceptance Criteria:**
- [ ] Coordinate format toggle (decimal / DMS); date-time format option
- [ ] "Location accuracy / NavIC" info section explaining India GNSS support
- [ ] About + version; links to privacy policy placeholder
- [ ] Build passes; verify settings persist and affect the stamp

### Phase 5 — Monetization

### US-015: Ads on the free tier
**Description:** As the business, I need ad revenue from free users.

**Acceptance Criteria:**
- [ ] Integrate AdMob (test ad unit ids in code, configurable)
- [ ] Non-intrusive placement (e.g. banner in Gallery / interstitial after N captures)
- [ ] Ads never block the capture action or cause crashes; respect a global "ads enabled" flag
- [ ] Build passes; verify a test ad shows on emulator

### US-016: One-time IAP — remove ads + premium templates
**Description:** As a user, I want to pay once to remove ads and unlock premium templates.

**Acceptance Criteria:**
- [ ] Google Play Billing: one-time product `remove_ads_premium`
- [ ] On purchase: ads disabled app-wide; premium templates unlocked
- [ ] Purchase state restored on reinstall (query owned purchases on launch)
- [ ] Build passes; verify with Play Billing test/license flow (or a debug stub)

### US-017: PDF photo-proof report export (premium hook)
**Description:** As a field user, I want to export selected photos as a single PDF proof document.

**Acceptance Criteria:**
- [ ] Multi-select photos in Gallery → "Export PDF report"
- [ ] PDF includes each photo with its stamp, plus a header (project/site, date range)
- [ ] Free users get a watermarked / limited (e.g. ≤3 photos) export; premium = unlimited
- [ ] Build passes; verify a valid multi-page PDF is generated and openable

### US-018: Subscription gating for premium reports/batch
**Description:** As the business, I need recurring revenue from heavy field users.

**Acceptance Criteria:**
- [ ] Google Play Billing subscription `pro_monthly` / `pro_yearly`
- [ ] Subscriber unlocks: unlimited PDF reports, batch export, no watermark
- [ ] Clear paywall screen describing benefits + India pricing
- [ ] Entitlement checked on launch and gated correctly
- [ ] Build passes; verify subscribe + gate behavior with test flow

---

## Functional Requirements

- FR-1: Capture a full-resolution photo via CameraX.
- FR-2: Acquire device location (lat/long, accuracy) via FusedLocationProvider with fallback.
- FR-3: Reverse-geocode coordinates to a postal address.
- FR-4: Composite a configurable stamp (address, coords, date/time, map thumbnail, custom fields, logo) into the saved image.
- FR-5: Provide ≥3 stamp templates, selectable and persisted.
- FR-6: Persist project/site name and note between captures.
- FR-7: Browse captured photos in a gallery; view, share, delete.
- FR-8: Plot captured photos on a map by coordinates.
- FR-9: Support English + Hindi with an in-app language toggle.
- FR-10: Offer coordinate (decimal/DMS) and date-time format options.
- FR-11: Show ads to free users via AdMob; suppress when ad-free is owned.
- FR-12: Sell a one-time IAP that removes ads and unlocks premium templates.
- FR-13: Export selected photos to a multi-page PDF report (gated free vs premium).
- FR-14: Sell a subscription unlocking unlimited reports + batch export + no watermark.

## Non-Goals (Out of Scope for MVP)

- iOS app (Android-first; revisit later).
- Cloud backup / account sync / web dashboard (post-MVP).
- Team accounts / multi-user collaboration.
- Video geotagging.
- Live editing of an existing photo's stamp after capture.
- Advanced GIS export (GeoJSON, KML, shapefiles).
- AI coordinate correction (leader has it; future differentiator, not MVP).

## Design Considerations

- Clean, modern Material 3 UI — explicitly counter incumbents' cluttered, ad-heavy feel.
- Stamp templates are the visual differentiator: borrow design/render discipline from the
  SharePoster project (template + compositing approach, typographic polish).
- Fast path to capture: minimize taps from launch → stamped photo.
- Light/dark theme support.

## Technical Considerations

- **Camera:** CameraX (preview + ImageCapture).
- **Location:** Google Play Services FusedLocationProvider; Geocoder for addresses.
- **Maps:** decide between Google Maps Static API (needs key + billing) vs an OSM static
  tile approach (no per-call cost). MVP can start with one provider behind an interface.
- **Stamp render:** draw to a Canvas/Bitmap and merge with the captured image; keep a
  template abstraction so new templates are data, not code.
- **PDF:** Android `PdfDocument` (no external dependency) for the report export.
- **Billing:** Google Play Billing Library (one-time + subscriptions).
- **Ads:** Google AdMob; keep an `adsEnabled` flag driven by entitlements.
- **Localization:** standard Android string resources; ensure language toggle applies at runtime.
- **Min SDK 24** for broad India device coverage.
- Reuse learnings/patterns from the SharePoster codebase where applicable.

## Success Metrics

- Play Store rating ≥ 4.3★ (vs leader's 3.34★).
- Capture-to-saved-stamped-photo success rate ≥ 98%.
- Median time from app launch to stamped photo ≤ 5s.
- Free→paid conversion measurable; PDF report is the top paid trigger.
- Crash-free sessions ≥ 99%.

## Open Questions

- Map provider for thumbnails/map view: Google Maps (key + billing) vs OSM/MapLibre (free)?
- Exact regional languages beyond Hindi for v1 (e.g. Marathi, Tamil, Telugu, Bengali)?
- IAP/subscription price points for India (₹) — one-time and monthly/yearly.
- Do we need EXIF GPS written into the file too (in addition to the visible stamp)?
- Watermark design + free-tier limits (photo count per report).
