# PRD: Gps Camera Location — Phase 2 (Pro redesign + features)

Builds on the shipped MVP. Driven by owner feedback (`05-feedback-round1.md`) and the
detailed UI spec (`06-ui-redesign-v2-spec.md`). Brand accent is **navy + gold** (NOT the
Google-blue in the v2 spec — substitute our brand colours everywhere).

## Brand tokens (use these, not the spec's blue)
- Navy (primary): `#15294D` · Gold (accent): `#F2A93B`
- On-dark text: `#FFFFFF` primary, `#9AA0A6` secondary
- GPS accuracy: green `#34A853` (Good <10m) · amber `#FB8C00` (Avg 10–20m) · red `#E53935` (Poor >20m)
- Card: black 90% opacity, 16dp corners, 12–16dp padding

## Goals
- Remove capture friction (no forced dialogs) and make the camera feel pro.
- Match/beat competitor parity (layouts, weather, home dashboard, photo grid) on quality.
- Add field-proof trust (EXIF GPS, altitude/compass, accuracy guidance).
- Consistent navy+gold brand across the app + new icon.

---

## User Stories (priority order; Android = `./gradlew assembleDebug` + emulator verify)

### P2-US-001: Navy+gold brand theme
**As a** user, I want a consistent premium look.
- [ ] Material3 theme uses navy `#15294D` primary + gold `#F2A93B` accent; light/dark safe
- [ ] Define GPS accuracy colours (green/amber/red) as theme tokens
- [ ] Existing screens pick up the new colours (no leftover green/blue placeholders)
- [ ] Build passes; verify on emulator

### P2-US-002: One-tap capture — remove forced "Stamp details" dialog (F1)
**As a** user, I want to just tap and capture; details auto-fill.
- [ ] Tapping shutter captures immediately; no blocking modal appears
- [ ] Stamp auto-fills address, coords, date/time (and weather when available)
- [ ] Last-used project/site name + note (if any) apply silently
- [ ] Build passes; verify capture has no forced dialog on emulator

### P2-US-003: Optional stamp-details bottom sheet (F1, edit affordance)
**As a** field user, I want to optionally add project/site/note/logo.
- [ ] "Edit" affordance (icon + "Edit" text) on the GPS card opens a bottom sheet
- [ ] Fields: project/site name, note, logo — persisted; never blocks capture
- [ ] Build passes; verify bottom sheet open/save/persist on emulator

### P2-US-004: Top GPS card redesign + remove duplicate block (R2-A, F8)
**As a** user, I want a clean, readable info card.
- [ ] Layout: address bold white 16sp → plus code + pincode 14sp 90% white → coords 12sp grey
- [ ] Accuracy chip colour-coded: green Good <10m / amber Avg 10–20m / red Poor >20m
- [ ] Card bg black 90%, 16dp corners, 12dp padding
- [ ] Remove the redundant filename/dimensions block from the photo/gallery overlay (one clean stamp)
- [ ] Build passes; verify card + chip + no duplicate block on emulator

### P2-US-005: Mode selector redesign + Pro badge (R2-B, F6)
**As a** user, I want clear mode chips.
- [ ] Centered chips with icons: Classic, Minimal, Field Report
- [ ] Selected = navy/gold fill + white text; unselected = transparent + grey `#9AA0A6`
- [ ] Replace the lock on Field Report with a small gold "PRO" badge; keep it usable (unlocked for now)
- [ ] Build passes; verify selected state + badge on emulator

### P2-US-006: Camera controls redesign (R2-C)
**As a** user, I want pro camera controls.
- [ ] Shutter ~72dp, white fill, ring + 8dp elevation
- [ ] Add Flash toggle (off/on/auto); keep flip-camera; side buttons 48dp grey 20%
- [ ] Build passes; verify shutter + flash + flip on emulator

### P2-US-007: Bottom nav polish + map badge (R2-D)
**As a** user, I want a clean nav and to see my map count.
- [ ] Selected tab navy/gold + bold; height 64dp; labels 11sp
- [ ] Map tab shows a badge with count of location-tagged photos (hidden when 0)
- [ ] Build passes; verify nav styling + badge on emulator

### P2-US-008: Status-bar padding + low-GPS guidance (R2-F)
**As a** user, I don't want UI under the status bar, and I want GPS hints.
- [ ] Apply statusBarsPadding so content isn't under the clock/battery
- [ ] When accuracy > 20m: GPS card turns amber/red + show "Move to open sky for better GPS"
- [ ] Build passes; verify padding + low-accuracy hint on emulator

### P2-US-009: Weather on the stamp (F3, A2)
**As a** user, I want weather on my stamp.
- [ ] Fetch current weather (temp °C + condition) via Open-Meteo (no API key) for the coords
- [ ] Add weather to the location data and render it on the stamp; graceful offline fallback
- [ ] Build passes; verify weather shows in overlay + saved image on emulator

### P2-US-010: Camera layout presets (F3, v2 §3)
**As a** user, I want to choose which fields appear.
- [ ] Layout selector with presets: Map+Address+Weather · Map+Lat/Lng+Weather · Map+Address · Address+Weather · Address · Lat/Lng
- [ ] Selection persists and controls what the stamp renders
- [ ] Build passes; verify switching presets changes the saved stamp on emulator

### P2-US-011: WYSIWYG stamp position (F5)
**As a** user, I want the preview to match the photo.
- [ ] On-screen stamp preview position matches the final burned stamp
- [ ] Position option (bottom/top) in settings; persists
- [ ] Build passes; verify preview == saved photo position on emulator

### P2-US-012: Viewfinder overlays — date/time, altitude, grid (R2-C, v2 §3)
**As a** user, I want quick on-screen info + framing grid.
- [ ] Bottom-left: date/time + altitude, 12sp white with shadow
- [ ] 3×3 grid lines (30% white) toggle from Settings
- [ ] Build passes; verify overlays + grid toggle on emulator

### P2-US-013: Altitude + compass direction (R2-E, B2)
**As a** field user, I want altitude and facing direction.
- [ ] Capture altitude (m) and compass bearing → cardinal (e.g. "NE")
- [ ] Show on stamp/overlay, e.g. "Altitude 342m · Facing NE"
- [ ] Build passes; verify altitude + direction render on emulator

### P2-US-014: EXIF GPS into the saved file (B1)
**As a** field user, I want machine-readable GPS in the file.
- [ ] Write EXIF GPS (lat/long, timestamp, altitude) into the saved JPEG
- [ ] Verify with an EXIF reader that tags are present
- [ ] Build passes; verify EXIF GPS on a captured file on emulator

### P2-US-015: Home dashboard screen (F2)
**As a** user, I want a home with quick tiles.
- [ ] New Home as launch destination with tiles: Camera, Gallery, Map, Reports
- [ ] Clean navy+gold design; tiles navigate to the right screens
- [ ] Build passes; verify home tiles + navigation on emulator

### P2-US-016: Photo Grid / collage (F4)
**As a** user, I want to combine photos into a grid.
- [ ] Select 2–4 photos in Gallery → arrange in a grid (2×2, side-by-side)
- [ ] Export the grid as one shareable image; each photo keeps its stamp
- [ ] Build passes; verify grid creation + export on emulator

### P2-US-017: Timestamp toggle + custom watermark (R2-E)
**As a** user, I want to toggle the timestamp and add my watermark.
- [ ] Settings: date/time ON/OFF on the stamp
- [ ] Custom watermark (company name/logo) renders bottom-right of the stamp (from custom fields)
- [ ] Build passes; verify toggle + watermark on saved image on emulator

### P2-US-018: Pro gestures (R2-E extras)
**As a** power user, I want quick shortcuts.
- [ ] Long-press shutter = 3-sec self-timer (with countdown)
- [ ] Swipe left on the viewfinder = open last photo / gallery preview
- [ ] Build passes; verify both gestures on emulator

---

## Non-goals (Phase 2)
- Cloud backup / accounts / web dashboard
- KML/CSV export and batch (10–15) mode → Phase 3
- Real AdMob/Play Console wiring (still test/stub)
- iOS

## Success metrics
- Capture friction removed (0 forced dialogs); rating trajectory ≥ 4.4★
- Consistent navy+gold brand; clean single stamp; map reliably loads
- Field-proof signals present (EXIF, altitude, accuracy guidance)
