# gpstools — Pro UI Redesign Spec v2.0

Source: external designer "Meta bhai", round 3. Detailed, dev-ready. This is the
authoritative UI spec for Phase-2 camera/screens work. Pairs with the feedback in
`05-feedback-round1.md`.

## 1. Top GPS card — new layout
```
┌─────────────────────────────────────┐
│ 📍 Tapovan, Rishikesh, Uttarakhand   │  16sp Bold, White
│    48JC+R93  |  249192, India        │  14sp Regular, 90% White
│                                      │
│    30.132034, 78.321448  [±8m ✓][EDIT]│  12sp Grey + chip + edit
└─────────────────────────────────────┘
```
- Background: black 90% opacity, 16dp rounded corners, 12dp padding.
- **Accuracy chip** colour-coded: <10m = green "Good" · 10–20m = orange "Avg" · >20m = red "Poor".
- **Edit**: icon **+ text** ("Edit"); tap opens a **bottom sheet** (not the old blocking modal).
- Hierarchy: address bold on top → plus code + pincode → coords small/grey.

## 2. Mode selector — centered
```
[ 📷 Classic ]   🎯 Minimal    📋 Field Report  ᴾᴿᴼ
```
- Selected: blue fill `#1A73E8`, white text + icon.
- Unselected: transparent bg, grey text `#9AA0A6`.
- Pro badge: small yellow "PRO" on Field Report.
- Each mode gets an icon (📷 / 🎯 / 📋) for fast scanning.

## 3. Viewfinder overlays
- **Top-right**: small map thumbnail 80×80dp with red pin.
- **Bottom-left**: date/time + `Altitude: 342m` — 12sp white, black shadow.
- **Grid**: 3×3 white lines @ 30% opacity, toggled from Settings.

## 4. Camera controls — bottom
```
[⚡ Flash]      (  ⃝  )      [⊞ Grid] [🔄 Flip]
              72dp Shutter
            white ring + shadow
```
- Shutter: white fill, 4dp grey border, 8dp elevation, ~72dp.
- Side buttons: 48dp circle, grey 20% bg.

## 5. Bottom nav bar
```
   📷        🖼️        🗺️        ⚙️
 Camera    Gallery     Map    Settings
```
- Selected: blue `#1A73E8` + bold. Height 64dp, label 11sp.
- **Map tab badge**: show count e.g. "24 photos" when location-tagged photos exist.

## Colour & spacing tokens (dev spec)
| Element | Value | Use |
|---|---|---|
| Primary Blue | `#1A73E8` | Buttons, selected states |
| Success Green | `#34A853` | GPS "Good" chip |
| Warning Orange | (amber) | GPS "Avg" / low-accuracy |
| Danger Red | (red) | GPS "Poor" |
| Card BG | `#000000` @ 90% | Top GPS card |
| Text Primary | `#FFFFFF` | Main labels |
| Text Secondary | `#9AA0A6` | Coords, labels |
| Padding | 16dp | Cards, screen edges |
| Corner radius | 16dp | All cards |

## Extra pro features that fit the UI
- **Long-press shutter** = 3-sec timer + beep.
- **Swipe left on viewfinder** = gallery preview.
- **Compass**: top-left `↗ NE` indicator as the phone rotates.
- **Low-GPS warning**: accuracy > 20m → top card turns orange + "Move to open sky" toast.

## ⚠️ Brand-consistency note (decide before building)
This spec uses **Google-style blue `#1A73E8`** as the accent (mirrors the competitor).
Our **logo direction** was **gold + navy** (clean line-art, owner's taste). Pick ONE accent
so the app + icon feel consistent:
- Option 1: adopt blue `#1A73E8` everywhere (matches this spec, familiar to users).
- Option 2: keep our gold/navy brand and swap the spec's blue for our accent.
Recommend deciding this with the logo choice.
