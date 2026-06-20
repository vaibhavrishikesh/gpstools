#!/usr/bin/env python3
"""Generate the promo-video decorations: a drifting gold-bokeh layer + a soft vignette.
Both are navy/gold house-style and deliberately SUBTLE.

  python3 make_decorations.py --out /tmp/ad [--seed 7]
"""
import argparse
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import random

GOLD = (245, 200, 110)
SPARK = (255, 235, 180)


def make_bokeh(out: Path, seed: int):
    random.seed(seed)
    W, H = 1080, 2300  # taller than the frame so it can drift during render
    bok = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(bok)
    for _ in range(70):  # soft gold orbs, varied size + opacity
        x, y = random.randint(0, W), random.randint(0, H)
        r = random.randint(6, 46)
        a = random.randint(18, 70)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(*GOLD, a))
    for _ in range(22):  # a few brighter sparkles
        x, y = random.randint(0, W), random.randint(0, H)
        r = random.randint(2, 6)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(*SPARK, 150))
    bok = bok.filter(ImageFilter.GaussianBlur(3))
    bok.save(out / "bokeh.png")


def make_vignette(out: Path):
    W, H = 1080, 1920
    mask = Image.new("L", (W, H), 140)
    md = ImageDraw.Draw(mask)
    md.ellipse([-220, -300, W + 220, H + 300], fill=0)  # clear the centre
    mask = mask.filter(ImageFilter.GaussianBlur(160))
    dark = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    vig = Image.composite(dark, Image.new("RGBA", (W, H), (0, 0, 0, 0)), mask)
    vig.save(out / "vignette.png")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="/tmp/ad")
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    make_bokeh(out, a.seed)
    make_vignette(out)
    print(f"wrote {out}/bokeh.png + {out}/vignette.png")
