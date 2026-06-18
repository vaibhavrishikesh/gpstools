# Redesign v3 — Camera-first home, no bottom nav, banner, 5 templates

Owner ask (2026-06-18, with 3 competitor reference screenshots):
1. Home screen competitor jaisa banana hai (screenshot-1 = camera screen).
2. Bottom navigation remove.
3. Home pe ad banner.
4. 4–5 templates do (competitor: Advance / Reporting / Classic / Custom / Modern).
5. Home + camera dono pe content/controls **bottom** pe shift → screen behtar dikhe.

---

## ⚠️ One decision to confirm before build

The reference (screenshot-1) IS a camera screen, used as the "home" example. So the
strongest reading is **camera-first**: the app opens straight into the camera, the GPS
stamp + controls live at the bottom, and a top-left ☰ hamburger drawer reaches
everything else. That's the plan below.

If instead you want Home to stay a **separate dashboard** (tiles + banner) with Camera
as its own screen, say so — only Phase A changes; the rest stays.

**Recommended: camera-first.** Matches the screenshot, removes the redundant nav, and
gets the user shooting in one tap (our core "field proof" job).

---

## Target layout (camera-first)

```
┌─────────────────────────────┐
│ ☰   GPS Camera Location  ⟳ ⚡│  ← top bar: hamburger | title | flip | flash
│                             │
│        [ live camera ]      │
│                             │
│ ┌──────┐ Nusantara, Jakarta │  ← GPS stamp card, bottom-anchored
│ │ map  │ full address…      │     (current LocationInfoOverlay, restyled)
│ └──────┘ lat/lng · wx row   │
│   [Classic][Work][Modern]…  │  ← template chips (horizontal scroll)
│   ◐ gallery   ⬤ shutter  ⚙  │  ← controls row: thumb | shutter | adjust
│ ─────── thin ad banner ─────│  ← adaptive banner pinned to bottom edge
└─────────────────────────────┘
```

Drawer (☰): Gallery · Map · Templates · Reports (PDF) · Settings · Go Pro · Premium.

---

## Phases (Ralph-ready stories)

### Phase A — Navigation rework
- **A1** Remove the bottom `NavigationBar` + `Scaffold` bottomBar from `MainActivity`.
  Set start destination = **Camera**. Keep NavHost for Gallery/Map/Settings/Reports/
  Templates so back-stack + deep nav still work.
- **A2** Add a `ModalNavigationDrawer` (Material3) wrapping the NavHost. Drawer items:
  Gallery, Map, Templates, Reports, Settings, Go Pro. Navy+gold styled, logo header.
- **A3** Camera top bar: add the ☰ icon (opens drawer) on the left; keep flip + flash
  on the right; centered "GPS Camera Location" title. Replace the old standalone
  camera top controls.
- **A4** Delete/retire `HomeScreen.kt` dashboard (or keep behind a flag if owner picks
  the dashboard option). Update `Destinations.kt` (drop Home/bottom-nav enum usage).

### Phase B — Bottom-anchored content + controls (home/camera)
- **B1** Restyle the GPS stamp card to sit bottom-anchored, white rounded card with the
  map thumbnail on the left + address/coords/weather rows on the right (screenshot-1).
- **B2** Controls row at the very bottom: **gallery thumbnail** (last photo, tap → gallery)
  · **shutter** (existing) · **adjust/settings** icon (⚙, opens template+field options).
- **B3** Ensure everything respects `navigationBarsPadding()` so nothing clips behind the
  system bar (the bug we already hit once).

### Phase C — Ad banner
- **C1** Reuse existing `BannerAd.kt` (AdMob test unit for now; real unit deferred).
  Pin an adaptive banner to the bottom edge, **below** the controls. Hidden when
  Premium/Pro (no-ads entitlement) is active.
- **C2** Same banner on Gallery / Map / Settings bottoms. (Banner on the camera is allowed
  by AdMob as long as it doesn't overlap the shutter — keep it as a separate bottom row.)

### Phase D — 5 templates
Extend `StampTemplate` enum to 5 (keep `usesMap`, `premium` flags; map old saved names):
| Template | Map | Style | Premium |
|----------|-----|-------|---------|
| **Classic** | yes | current classic panel | free |
| **Reporting** (Work Report) | yes | green "Work Report" tag + map + full block | free |
| **Modern** | no | text-left, weather icons right column (screenshot-3 "Morden") | free |
| **Advance** | yes | header band + all fields (most detailed) | Pro |
| **Custom** | yes | user-toggled fields (see D2) | Pro |

- **D1** Implement the 4 new layouts in `PhotoStamp.kt` drawStamp() + add chips +
  string resources (en + hi) + preview rendering in the picker.
- **D2** (optional, bigger) "Advance/Custom" field editor (screenshot-2): a settings
  sheet to toggle Map type (Satellite/Terrain/Hybrid), Short/Full address, Lat-Long,
  Date, Timezone, Compass, Weather, Humidity, Wind, Person name, Custom note. Persist
  per-template. **Recommend deferring D2 to a follow-up** unless owner wants it now —
  it's roughly as much work as A+B+C combined.

### Phase E — Polish + ship
- Build, install on phone (Android 10) + emulator, verify flows, signed AAB, bump
  version, update `PLAY-RELEASE.md` screenshots.

---

## Files touched (estimate)
- `MainActivity.kt` (nav rework, drawer)
- `ui/screens/CameraPreview.kt` + `CameraScreen.kt` (top bar, controls, thumbnail)
- `ui/screens/LocationInfoOverlay.kt` (stamp card restyle)
- `ui/navigation/Destinations.kt` (drop bottom-nav model)
- `media/StampTemplate.kt` + `media/PhotoStamp.kt` (2→5 templates)
- `ads/BannerAd.kt` (reuse) + screen bottoms
- new: `ui/screens/AppDrawer.kt`, maybe `ui/screens/TemplatesScreen.kt`
- `res/values/strings.xml` + `values-hi/strings.xml`
- retire `HomeScreen.kt`

## Risks / notes
- Saved-template migration: old `MINIMAL`/`FIELD_REPORT` names → map to nearest new one.
- Banner needs the **real AdMob app id + unit id** before public launch (still stubbed).
- Camera-as-start means cold-launch goes straight to camera permission prompt — keep the
  existing permission gate screen as the very first thing.
- "Map type Satellite/Terrain/Hybrid" (screenshot-2) needs a tile source that has those
  layers; OSM raster is street-only. Satellite would need a keyed provider → part of D2,
  flag for owner.

## Suggested order to build (via Ralph or direct)
A → B → C → D1 → (D2 optional) → E. A+B+C+D1 = the visible competitor-style redesign.
