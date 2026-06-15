# gpstools — Project Research & Scope

Geotag / GPS camera app opportunity. Research kicked off **2026-06-14**.
**Decision (2026-06-15): 🟢 GREEN LIT.** Hybrid positioning · Native Android (Kotlin + Compose) ·
Stacked monetization (ads + IAP + subscription) · India-first.
MVP PRD: [tasks/prd-gpstools-mvp.md](tasks/prd-gpstools-mvp.md) · Ralph build queue: [scripts/ralph/prd.json](scripts/ralph/prd.json) (18 stories).
To run the autonomous build, see [RALPH.md](RALPH.md) — needs `claude` CLI installed first.

## Docs
1. [01-competitive-research.md](01-competitive-research.md) — the app we checked + competitor stats
2. [02-market-landscape.md](02-market-landscape.md) — market size, segments, monetization
3. [03-scope-draft.md](03-scope-draft.md) — proposed scope / MVP for gpstools

## TL;DR
- **Huge market, low satisfaction.** Category leader *GPS Map Camera* has **130M downloads but only 3.34★**. The app we checked (*GPS Photo* by gonext) has 21M downloads @ 4.10★.
- **Crowded** at the casual "stamp location on photo" layer. Real moat is the **B2B / field-proof** angle (construction, NGO, survey, audit) + **India-specific** features (NavIC, regional languages).
- **Opportunity thesis:** a polished, fast, trustworthy geotag camera that fixes the leader's quality complaints, with PDF photo-proof reports as the paid hook.

## Open questions for tomorrow
- B2C casual vs B2B field-proof — pick one to lead with.
- Monetization: ads + IAP (remove ads) vs subscription (reports/cloud).
- India-first (NavIC, Hindi/regional) vs global.
- Reuse SharePoster's design/render strengths here, or fully separate build?
