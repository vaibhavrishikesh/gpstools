# gpstools — Scope Draft (v0, for discussion)

> Status: **DRAFT for decision on 2026-06-15.** Nothing committed. Options laid out so we can pick a direction, not a final spec.

## 1. Product thesis
A **fast, clean, trustworthy geotag camera** that wins on quality where the 130M-download leader fails (3.34★). Beautiful customizable location stamps (our design strength) + reliable proof export (the paid hook).

Working name: **gpstools** (final brand TBD).

## 2. Positioning decision (PICK ONE to lead)
- **Option A — B2C casual first:** travel/memory stamps, weather, map gallery. Easier build, ad-monetized, but commoditized. Leans on our design edge.
- **Option B — B2B field-proof first (recommended):** tamper-evident geotag + PDF reports for construction/survey/NGO/audit. Harder, but pays and the leader is weak here.
- **Option C — hybrid:** ship casual MVP, layer field-proof + reports as the premium tier. Lowest risk, slower to revenue.

> Recommendation: build the **camera core once**, ship casual MVP fast (Option A surface), monetize via the **field-proof report tier** (Option B value). = Option C executed deliberately.

## 3. MVP scope (proposed)
**Core (must-have)**
- GPS camera: capture photo with live overlay → lat/long, address, date/time, map thumbnail.
- Editable, **good-looking stamp templates** (this is our differentiator vs ugly incumbents).
- Save to gallery; in-app album browse.
- Custom fields (note, project/site name).
- Accurate location (high-accuracy + fallback), graceful permission flows.

**Differentiators (early)**
- Clean, ad-light, fast UX — explicitly counter the leader's complaints.
- Map view of captured photos.
- Multiple stamp styles + brand/logo on stamp.

**Premium (paid hook)**
- **PDF / multi-photo report export** (photo-proof document) — the thing field users pay for.
- Remove ads, advanced templates, batch export.
- (Later) cloud backup, team/shared albums.

**Explicitly out of MVP**
- Video geotagging, cloud sync, web dashboard, team accounts, NavIC tuning — phase 2+.

## 4. India considerations
- Hindi + key regional languages from day one.
- NavIC mention/support as a trust/marketing signal (leader uses this heavily).
- Pricing tuned for India (low IAP / annual sub).

## 5. Monetization (proposed)
- Free tier: ads + basic stamps + watermark on reports.
- One-time IAP: remove ads + premium templates.
- Subscription: unlimited reports + cloud backup + batch (the LTV driver).

## 6. Tech (to decide tomorrow)
- Native Android (Kotlin/Compose) vs cross-platform (Flutter) — Android-first either way.
- Reuse from SharePoster: rendering/compositing approach, template engine, design system.
- Maps/geocoding provider + cost (Google vs alternatives), offline strategy.

## 7. Success criteria / first milestones
- M1: camera core + one beautiful stamp template, capture→save working.
- M2: 3–4 templates + album + map view → internal dogfood.
- M3: PDF report export + IAP → closed beta.
- North-star early metric: rating > 4.3★ and capture-to-save success rate.

## 8. Decisions needed (2026-06-15)
1. Lead positioning: A / B / C?
2. Monetization model: ads-only / freemium IAP / subscription / stacked?
3. India-first or global?
4. Tech stack + how much to reuse from SharePoster?
5. Is this a serious next product or a quick utility experiment? (scopes the whole effort)

---
See [01-competitive-research.md](01-competitive-research.md) and [02-market-landscape.md](02-market-landscape.md) for the data behind this.
