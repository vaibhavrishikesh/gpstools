# gpstools — Feedback Round 1 (owner, real-device)

Date: **2026-06-16** · Source: owner used v0.1.0 on Redmi Note 7 Pro, compared against
competitor *GPS Map Camera*. Screenshots shared on WhatsApp.

These drive Phase-2. Each maps to a feature below.

## F1 — Kill the forced "Stamp details" input dialog → auto-fill
**What owner said:** "ye kya hai... automatic fill hona chahiye na."
**Problem:** Our capture flow pops a mandatory *Stamp details* modal (Project/site name,
Note, Logo) — friction. Competitor stamps location/time/weather automatically, no form.
**Fix:**
- Capture is one tap → stamp auto-fills address, coords, date/time, (weather).
- Project/site name + Note become **optional**, set once and persisted, reachable via a
  small edit affordance — **never a blocking modal** before capture.
- Keep these fields for the B2B/field use case, just don't force them on casual users.

## F2 — Add a Home/dashboard launcher screen
**What owner said:** "aisi screen maine wali" (competitor home screen).
**Competitor home:** big colorful tiles — Advance Camera, Photo Grid, Camera, Gallery,
Album — plus a map preview. App opens here, not directly on the camera.
**Fix:**
- New **Home** as the launch destination with tiles: **Camera, Gallery, Albums/Map**
  (+ optionally "Quick Capture", "Reports/PDF").
- Keep it clean and on-brand (our design edge) — not as loud as the competitor, but the
  same one-tap-to-everything dashboard feel.

## F3 — On-screen camera layout presets (field selector) + weather + stamp position
**What owner said:** "kuch options on screen, add position" (screenshots of competitor's
*Camera Layouts*).
**Competitor offers layouts:** Map+Address+Weather · Map+Lat/Lng+Weather · Map+Address ·
Address+Weather · Address · Lat/Lng.
**Fix:**
- A **layout/preset selector** (from the camera screen) to choose which fields appear on
  the stamp: map, address, lat/lng, date/time, weather — in named combos.
- Add **weather** (temperature + condition) to the stamp data. (New — not in MVP.)
- Allow **stamp position** choice (e.g. bottom / top) and per-layout arrangement.
- This extends our existing 3 templates (US-009) into data-driven layouts.

## F4 — Photo Grid (collage of geotagged photos)
**What owner said:** "photo grid" (circled the competitor's PHOTO GRID home tile).
**Competitor:** a tile that lets you combine multiple captured photos into a single grid/
collage image to share.
**Fix:**
- A **Photo Grid** feature: select 2–4+ photos from the gallery, arrange in a grid layout
  (2x2, 1+2, side-by-side, etc.), export as one combined image.
- Each photo keeps its stamp; grid is shareable. Reachable as a Home tile (see F2).

## F5 — Stamp position consistency (preview ↔ photo)
**What owner said:** "isme ye upar dikha raha hai, photo kheenchne ke baad neeche aa jata hai."
**Problem:** On the camera screen the location card sits at the **TOP**, but on the saved
photo the stamp is at the **BOTTOM** — inconsistent / confusing.
**Fix:** WYSIWYG — the on-screen preview position must match the final burned stamp
position. (Ties into F3 position control + a true live preview.)

## F6 — "Field Report" locked template is confusing
**What owner said:** "ye kya hai" (circled the 🔒 *Field Report* chip).
**Problem:** A locked/premium template chip on the camera with no explanation reads as
confusing, not enticing.
**Fix:** Either clearly mark it as Premium with a tap → "what you get" sheet, or unlock it
for now. Don't show a bare lock with no context.

## F7 — App display name → "Gps Camera Location"
**What owner said:** "iska naam Gps camera location rakhna hai" / "app name Gps Camera Location".
**Fix:** Change the launcher/display name (app_name) to **"Gps Camera Location"**
(keyword-friendly for India ASO). Package id stays `com.gpstools.camera`.

## F8 — Duplicate info blocks on the photo / clean up card details
**What owner said:** "2 2 aa rahe hain photo mei" + "fix details on card."
**Problem:** Two info blocks appear — the burned stamp (address/coords/time + map) AND a
second block showing **filename / captured date / 3000×4000 px**. Looks duplicated/cluttered.
**Fix:** Show ONE clean stamp on the photo. Remove the redundant filename/dimensions block
from the photo/gallery-viewer overlay (keep file metadata in details only, not on the image).
Tighten the card layout so it reads clean.

## F9 — App needs a real logo / icon
Currently the default Android launcher icon (from scaffold). Need a proper brand
logo + adaptive icon. Ties to F7 rename ("Gps Camera Location").

## F10 — BUG: Map not loading on device
Owner reports the Map view tiles don't load. Likely cause: **osmdroid needs a User-Agent**
(`Configuration.getInstance().userAgentValue = packageName`) or OSM tile servers return 403
→ blank tiles. Also confirm INTERNET permission + cache dir. (Stamp map thumbnail renders,
so it's specific to the osmdroid MapView tab.) **Fix in Phase-2 (or hotfix).**

---

## Round 2 — Detailed UI/UX review (external designer "Meta bhai")
Specific, high-quality polish notes. Mostly design-edge (our differentiator) + a few pro
features + bug fixes. Grouped:

### R2-A. GPS info card (top strip)
- Background opacity → **85–90% black** for full readability (scene bleeding through now).
- Font hierarchy: **Plus Code bold (top)** → address normal → coords small+grey → **accuracy as a chip bottom-right**.
- Edit pencil: clarify action — add "Edit" label, not a bare icon.

### R2-B. Mode selector (Classic / Minimal / Field Report)
- Clear **active state**: accent border/fill on the selected chip (Classic-white vs Minimal-grey is unclear).
- Field Report **lock → small "Pro" badge** if paid (lock looks like a bug). [overlaps F6]
- Add **icons**: Classic 📷 · Minimal 🎯 · Field Report 📋 for fast scanning.

### R2-C. Camera controls
- **Shutter bigger** (~72dp) + subtle white ring + elevation (it "floats" now).
- Add **Flash toggle** next to flip-camera (essential for a GPS camera).
- **3×3 grid lines** option in Settings (surveyors ask for it).

### R2-D. Bottom nav
- Label sizing tweak; **selected tab in brand color**.
- **Map tab**: show all captured photos on the map + thumbnail preview (make the value obvious).

### R2-E. Missing "pro" features (market-movers)
- **Timestamp toggle** (date/time ON/OFF).
- **Custom watermark** (company logo/name, bottom-right). [overlaps F7 custom fields/logo]
- **Altitude + Direction** (e.g. "Altitude 340m · Facing NE") — field work. [overlaps B2]
- **Map snapshot** in photo corner with pin — *already have (US-008)*; keep/refine.
- **Batch mode** (10–15 photos, auto GPS stamp). [overlaps C1]
- **Export KML/CSV** (survey location lists). [overlaps C2]

### R2-F. Bugs / UX issues
- **Status bar overlap** → add `statusBarsPadding` (UI sticking under the clock/battery).
- **Shutter shadow/elevation** → elevation 8dp + solid white bg.
- **High accuracy hint** → when accuracy > 10m, show "Move to open sky for better GPS".

---

## Priority for Phase-2 (from this feedback)
1. **F1** auto-fill / remove forced dialog  — friction fix, P0
2. **F3** layout presets + weather + position — core parity + our design polish
3. **F2** home dashboard screen — navigation/first impression

These supersede/extend the generic ideas in `04-phase2-improvements.md`
(F1↔flow, F3↔A1/A2/A3, plus the trust items B1/B4 still queued).

## Open questions to confirm before building
- Home tiles: which ones exactly? (Camera, Gallery, Albums, Reports?)
- Weather data source: free API (e.g. Open-Meteo, no key) vs OpenWeather (key)?
- Keep Project/site name + Note for field users (optional), or drop entirely for now?
