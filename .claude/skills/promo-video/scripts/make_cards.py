#!/usr/bin/env python3
"""Generate the promo-video title + CTA cards (1080x1920) in the navy+gold house style.

  python3 make_cards.py --app "Gps Camera Location" \
      --tagline "Geotag your field photos" \
      --logo /path/to/ic_play_store_512.png \
      --features "GPS + address stamp" "5 pro templates" "Map + PDF reports" \
      --out /tmp/ad

Produces 00-title.png and 99-cta.png. Fonts auto-resolve to a system sans (override
with --font / --font-bold). The Play badge is drawn as text ("GET IT ON Google Play")
so no external asset is needed; pass --badge to composite a real badge PNG instead.
"""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

NAVY_TOP = (39, 66, 115)
NAVY_BOT = (9, 18, 36)
GOLD = (242, 169, 59)
CREAM = (235, 220, 190)
WHITE = (240, 244, 250)

FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/Library/Fonts/Arial.ttf",
]


def _font(path, size):
    try:
        return ImageFont.truetype(path, size)
    except Exception:
        for c in FONT_CANDIDATES:
            try:
                return ImageFont.truetype(c, size)
            except Exception:
                continue
        return ImageFont.load_default()


# Note: stick to ASCII-safe glyphs in card text (•, ✓ may be tofu in Arial).


def gradient(w, h):
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        t = y / h
        r = int(NAVY_TOP[0] + (NAVY_BOT[0] - NAVY_TOP[0]) * t)
        g = int(NAVY_TOP[1] + (NAVY_BOT[1] - NAVY_TOP[1]) * t)
        b = int(NAVY_TOP[2] + (NAVY_BOT[2] - NAVY_TOP[2]) * t)
        for x in range(w):
            px[x, y] = (r, g, b)
    return img


def _center(d, y, text, font, fill, w):
    bb = d.textbbox((0, 0), text, font=font)
    d.text(((w - (bb[2] - bb[0])) / 2, y), text, font=font, fill=fill)
    return bb[3] - bb[1]


def title_card(a, w, h):
    img = gradient(w, h)
    d = ImageDraw.Draw(img)
    y = 380
    if a.logo and Path(a.logo).exists():
        logo = Image.open(a.logo).convert("RGBA").resize((300, 300))
        img.paste(logo, ((w - 300) // 2, y), logo)
        y += 360
    y += _center(d, y, a.app, _font(a.font_bold, 78), GOLD, w) + 40
    _center(d, y, a.tagline, _font(a.font, 44), CREAM, w)
    y += 140
    fb = _font(a.font, 40)
    for feat in a.features:
        _center(d, y, f"•  {feat}", fb, GOLD, w)  # gold bullet — Unicode ✓ is tofu in Arial
        y += 90
    return img


def cta_card(a, w, h):
    img = gradient(w, h)
    d = ImageDraw.Draw(img)
    y = 520
    if a.logo and Path(a.logo).exists():
        logo = Image.open(a.logo).convert("RGBA").resize((220, 220))
        img.paste(logo, ((w - 220) // 2, y), logo)
        y += 280
    y += _center(d, y, "DOWNLOAD FREE", _font(a.font_bold, 84), GOLD, w) + 60
    _center(d, y, a.app, _font(a.font, 46), CREAM, w)
    y += 200
    if a.badge and Path(a.badge).exists():
        badge = Image.open(a.badge).convert("RGBA")
        bw = 560
        bh = int(badge.height * bw / badge.width)
        badge = badge.resize((bw, bh))
        img.paste(badge, ((w - bw) // 2, y), badge)
    else:  # draw a simple Play-style pill
        bw, bh = 540, 150
        bx = (w - bw) // 2
        d.rounded_rectangle([bx, y, bx + bw, y + bh], radius=24,
                            fill=(20, 20, 24), outline=GOLD, width=2)
        _center(d, y + 34, "GET IT ON  Google Play", _font(a.font_bold, 40), WHITE, w)
    return img


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--app", required=True)
    ap.add_argument("--tagline", default="")
    ap.add_argument("--logo", default="")
    ap.add_argument("--badge", default="")
    ap.add_argument("--features", nargs="*", default=[])
    ap.add_argument("--font", default=FONT_CANDIDATES[2])
    ap.add_argument("--font-bold", dest="font_bold", default=FONT_CANDIDATES[0])
    ap.add_argument("--out", default="/tmp/ad")
    a = ap.parse_args()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    W, H = 1080, 1920
    title_card(a, W, H).save(out / "00-title.png")
    cta_card(a, W, H).save(out / "99-cta.png")
    print(f"wrote {out}/00-title.png + {out}/99-cta.png")
