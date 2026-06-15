# gpstools — Phase-2 Planning & Improvement Insights

Created: **2026-06-15** (right after MVP v0.1.0 shipped to phone).
Status: **planning / ideas** — to be validated with real-device feedback, then turned into a Phase-2 PRD for Ralph.

## Where we are
MVP v0.1.0 is built and running on device: camera → location + address → stamp burn
(3 templates) → custom fields → gallery → map view (osmdroid) → Hindi/English →
settings → ads/IAP/PDF report/subscription (last three in test/stub mode).

## The strategic frame (why these improvements)
- Category leader *GPS Map Camera* = **130M downloads but only 3.34★**. The market is
  proven; the win is **quality, design, and trust** — not new categories.
- The **money** is in the **field-proof B2B** segment (construction, survey, NGO, audit,
  real estate): they pay for tamper-evident proof + PDF reports.
- Our **unfair advantage** = design/render quality (SharePoster lineage) in an ugly
  category, plus India-first (NavIC, Hindi/regional).

Every improvement below ladders up to one of: **look better**, **be trusted**, **earn more**, or **get found**.

---

## Improvement backlog (prioritized)

Legend — Impact: 🔥 high / ➖ medium. Effort: S / M / L.

### A. Design polish — our biggest edge 🎨 (do first)
| # | Improvement | Impact | Effort | Why |
|---|---|---|---|---|
| A1 | Beautiful stamp templates (typography, clean gold line-art, shadows, rounded panels) | 🔥 | M | This is where we visibly beat the 3.34★ incumbents |
| A2 | Weather + temperature on stamp | 🔥 | S | gonext competitor has it; users love it |
| A3 | WYSIWYG live stamp preview before capture | 🔥 | M | "What you see is what you get" = trust + delight |
| A4 | Stamp color themes (light/dark/branded) | ➖ | S | Personalization, brand fit |
| A5 | Logo placement + transparency controls | ➖ | S | Field/brand users want their logo clean |

### B. Trust & accuracy — field-proof (where the money is) 📍
| # | Improvement | Impact | Effort | Why |
|---|---|---|---|---|
| B1 | Write **EXIF GPS** into the file (not just visible stamp) | 🔥 | S | Legal/proof value; many tools expect it |
| B2 | Compass direction + altitude on stamp | 🔥 | M | Surveyors/engineers need it |
| B3 | "Verified capture" tamper-evident badge/feel | ➖ | M | Differentiates as a *proof* tool |
| B4 | Manual location pin correction on map | 🔥 | M | When GPS drifts, let user fix it |
| B5 | Accuracy boost — average multiple fixes; actually use **NavIC** | 🔥 | L | India edge; leader markets this hard |

### C. New features — widen the moat 🚀
| # | Improvement | Impact | Effort | Why |
|---|---|---|---|---|
| C1 | Bulk/batch capture mode | 🔥 | M | Field teams shoot 50+ photos per site |
| C2 | CSV/Excel export of photo metadata | 🔥 | M | Auditors/reporting; B2B sticky |
| C3 | Custom-field presets per project (employee ID, ticket no.) | ➖ | M | Speeds repeat workflows |
| C4 | Cloud backup + cross-device sync | 🔥 | L | Big subscription value driver |
| C5 | Video geotagging | ➖ | L | Demand exists (was MVP non-goal) |

### D. Technical / quality ⚙️
| # | Improvement | Impact | Effort | Why |
|---|---|---|---|---|
| D1 | Speed: launch→capture ≤3s, fast stamp render | 🔥 | M | Perceived quality; reviews mention lag |
| D2 | Crash-free hardening + low-end device testing (Redmi-class) | 🔥 | M | India device reality |
| D3 | Offline maps (osmdroid supports it) | ➖ | M | Field sites have poor network |
| D4 | App size trim (currently ~17MB) | ➖ | S | Faster installs, lower churn |

### E. Monetization 💰 (after AdMob + Play Console wired)
| # | Improvement | Impact | Effort | Why |
|---|---|---|---|---|
| E1 | Smart paywall at the PDF-report moment (highest intent) | 🔥 | S | Convert where value is felt |
| E2 | Free trial for subscription | 🔥 | S | Standard conversion lift |
| E3 | Gentle free-tier watermark | ➖ | S | Nudges upgrade without anger |

### F. Growth / ASO 📈
| # | Improvement | Impact | Effort | Why |
|---|---|---|---|---|
| F1 | India keywords: "GPS camera Hindi", "geotag survey", "site photo proof" | 🔥 | S | Cheap discovery wins |
| F2 | NavIC angle in store listing + marketing | 🔥 | S | Leader leans on this; we can too |
| F3 | First-run onboarding / quick tour | ➖ | S | Activation + better first impression |

---

## Recommended Phase-2 scope (first cut)
Lead with our edge + the trust basics — fast, high-impact, sets us apart:
1. **A1** Beautiful stamp templates
2. **A3** WYSIWYG live preview
3. **A2** Weather on stamp
4. **B1** EXIF GPS into file
5. **B4** Manual location correction
6. **D1/D2** Speed + crash hardening

Defer to Phase-3: cloud sync (C4), video (C5), NavIC deep work (B5), full monetization tuning (E).

## How we'll execute
Same proven flow: validate with feedback → write Phase-2 PRD (`/prd`) → convert to
`prd.json` (`/ralph`) → run the loop on a `ralph/gpstools-phase2` branch.
Infra reminders: `claude` CLI logged in, `ANDROID_SERIAL=emulator-5554`, emulator on `-gpu host`.

## Open questions to resolve with feedback
- Which template style does the owner love? (drives A1 direction)
- B2C-casual polish first vs B2B-field-proof depth first?
- Cloud backend choice for C4 (Firebase vs custom) — affects cost.
- Regional languages beyond Hindi for v2?
