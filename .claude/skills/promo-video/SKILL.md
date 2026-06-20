---
name: promo-video
description: "Build a vertical (1080x1920) app-store promo / ad video from real store screenshots — PIL title+CTA cards, ffmpeg xfade scene chain, drifting gold-bokeh + vignette decorations, and AI background music. Use when asked to make a promo video, app ad, marketing video, or store trailer. Triggers on: make a promo video, app ad, marketing video, store trailer, video for the app."
user-invocable: true
---

# App promo-video pipeline

Produce a polished ~16s vertical (1080×1920) promo video for a mobile app from the
assets a launched app already has (framed store screenshots), plus generated title/CTA
cards, subtle decorations, and AI music. House style: **navy canvas + one gold accent**
(matches the tranquilwaters Android family — see `framework/design-system.md`).

> Reference build: **Gps Camera Location** (`gpstools`). 7 scenes, 16.4s, ~2.3 MB,
> H.264 + AAC. Output lives at `~/Desktop/<app>-promo.mp4`.

## Prerequisites

- `ffmpeg` (8.x) + `ffprobe` — `brew install ffmpeg`
- Python 3 with Pillow — `pip3 install Pillow`
- Framed store screenshots (the launch kit's `store/screenshot-*.png`, 1080×1920)
- **Music (optional but recommended):** Higgsfield MCP `generate_audio`
  (model `sonilo_music`, text-to-music, no vocals). Any royalty-free track also works.

## The pipeline (4 steps)

Work in a scratch dir (e.g. `/tmp/ad/`). All scripts are in `scripts/`.

### 1. Generate the title + CTA cards (PIL)
`python3 scripts/make_cards.py --app "Gps Camera Location" --tagline "Geotag your field photos" --out /tmp/ad`
→ `00-title.png` (logo + app name in gold + tagline + feature bullets) and
`99-cta.png` ("DOWNLOAD FREE" + Google Play badge). Navy gradient (39,66,115)→(9,18,36).

### 2. Generate decorations (PIL)
`python3 scripts/make_decorations.py --out /tmp/ad`
→ `bokeh.png` (1080×2300 transparent — soft gold particles, drifts during render) and
`vignette.png` (1080×1920 — dark soft edges for a premium look). **Subtle is the point.**

### 3. Generate music (Higgsfield MCP) — optional
- **Preflight cost** first: `generate_audio({model:"sonilo_music", prompt:"…", duration:17, get_cost:true})`
  — typically ~1 credit for ~17s.
- Then generate without `get_cost`. The job returns `status:"pending"` with an id.
- **Poll** with `job_display({id})` until `status:"completed"`; download `results.rawUrl`
  (an `.m4a`) with `curl -sL <url> -o /tmp/ad/music.m4a`.
- Prompt that worked: *"Upbeat modern tech brand promo background music, clean uplifting
  corporate, subtle electronic, energetic but professional, no vocals, builds confidence."*
- Match `duration` to the video length (~17s for a 16.4s video).

### 4. Render (ffmpeg, two passes)
`bash scripts/render.sh` — edit the `SCENES`/`STORE` vars at the top first.
- **Pass A** — `base.mp4`: 7 stills, each `-loop 1 -t 2.8` (CTA 3.2), `scale=…:increase,
  crop=1080:1920,setsar=1,fps=30`, chained with `xfade` (fade/slideleft, duration 0.6,
  offsets 2.2/4.4/6.6/8.8/11.0/13.2). libx264 yuv420p.
- **Pass B** — final: overlay drifting bokeh (`overlay y='-(t*23)'`, alpha ~0.55) →
  vignette overlay → mux music with `afade` in/out + `-shortest`, AAC 192k.

Output: `~/Desktop/<app>-promo.mp4`.

## Verify before declaring done

```
ffmpeg -ss 3 -i ~/Desktop/<app>-promo.mp4 -frames:v 1 /tmp/ad/check.png   # eyeball a frame
ffprobe -v error -show_entries format=duration:stream=codec_type -of default=noprint_wrappers=1 ~/Desktop/<app>-promo.mp4
open ~/Desktop/<app>-promo.mp4
```
Confirm: **two streams** (video h264 + audio aac), duration ~16–17s, bokeh visible but
not busy, gold accent reads, no letterboxing (the `crop` removes it).

## Scene recipe (tune per app)

| # | Scene | Why |
|---|-------|-----|
| 1 | Title card | brand + one-line promise |
| 2 | Home / dashboard | "everything in one place" |
| 3 | Hero feature (camera) | the core action |
| 4 | Secondary feature (templates) | breadth |
| 5 | Map / proof | credibility |
| 6 | Pro / upsell | monetization |
| 7 | CTA card | download |

Keep it 5–8 scenes, ~2.2–2.8s each. Lead with the promise, end with the ask.

## Gotchas (from shipping)

- **Music is async.** `generate_audio` returns `pending` — you must `job_display` poll +
  `curl` the `rawUrl`. Don't assume the first response has the file.
- **Letterboxing.** Use `force_original_aspect_ratio=increase` + `crop`, never `decrease`
  (which pillarboxes). All scenes must be the exact same WxH/SAR/fps or `xfade` errors.
- **Decorations: subtle wins.** Bokeh alpha ~0.55, vignette soft. Heavy particles look
  cheap and fight the screenshots. This is an ad for a utility app, not a music video.
- **`-shortest`** so the (slightly longer) music track doesn't pad black tail frames.
- **Store screenshots, not emulator grabs,** make the best scenes — they're already
  framed + captioned for the listing.
