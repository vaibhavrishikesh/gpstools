#!/usr/bin/env bash
# Render the promo video: Pass A builds the silent xfade scene chain, Pass B overlays
# the drifting bokeh + vignette and muxes the music. Edit the vars, then run.
#   bash render.sh
set -euo pipefail

AD=${AD:-/tmp/ad}                 # scratch dir holding 00-title.png, 99-cta.png, bokeh.png, vignette.png, music.m4a
STORE=${STORE:-/Users/vaibhav/workspace/gpstools/store}   # framed store screenshots
OUT=${OUT:-$HOME/Desktop/gpstools-promo.mp4}
MUSIC=${MUSIC:-$AD/music.m4a}     # optional; if missing, renders silent

# Scenes in order. Title + CTA come from $AD; screenshots from $STORE.
SCENES=(
  "$AD/00-title.png"
  "$STORE/screenshot-4-home.png"
  "$STORE/screenshot-1-camera.png"
  "$STORE/screenshot-2-templates.png"
  "$STORE/screenshot-5-map.png"
  "$STORE/screenshot-6-pro.png"
  "$AD/99-cta.png"
)
DUR=2.8           # seconds per scene (last one gets +0.4)
XF=0.6            # xfade duration
TRANS=(fade slideleft fade slideleft fade fade)   # one per transition (scenes-1)

# --- Pass A: build the inputs + filtergraph ---
inputs=(); n=${#SCENES[@]}
for i in "${!SCENES[@]}"; do
  t=$DUR; [ "$i" -eq $((n-1)) ] && t=3.2
  inputs+=(-loop 1 -t "$t" -i "${SCENES[$i]}")
done

fc=""
for i in "${!SCENES[@]}"; do
  fc+="[$i:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,fps=30[v$i];"
done
prev="v0"; off=0
for ((i=1; i<n; i++)); do
  off=$(echo "$off + $DUR - $XF" | bc)
  tr=${TRANS[$((i-1))]}
  out="x$i"; [ "$i" -eq $((n-1)) ] && out="vout"
  fc+="[$prev][v$i]xfade=transition=$tr:duration=$XF:offset=$off[$out];"
  prev="$out"
done
fc=${fc%;}

ffmpeg -y -loglevel error "${inputs[@]}" -filter_complex "$fc" \
  -map "[vout]" -c:v libx264 -pix_fmt yuv420p -r 30 "$AD/base.mp4"
echo "Pass A done: $AD/base.mp4"

# --- Pass B: decorations + music ---
LEN=$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$AD/base.mp4")
if [ -f "$MUSIC" ]; then
  fout=$(echo "$LEN - 1.0" | bc)
  ffmpeg -y -loglevel error -i "$AD/base.mp4" -i "$AD/bokeh.png" -i "$AD/vignette.png" -i "$MUSIC" \
    -filter_complex "[1:v]format=rgba,colorchannelmixer=aa=0.55[bok];[0:v][bok]overlay=x=0:y='-(t*23)':format=auto[b1];[b1][2:v]overlay=0:0:format=auto[vout]" \
    -map "[vout]" -map 3:a \
    -af "afade=t=in:st=0:d=0.5,afade=t=out:st=$fout:d=1.0,volume=0.85" \
    -c:v libx264 -pix_fmt yuv420p -r 30 -c:a aac -b:a 192k -shortest "$OUT"
else
  ffmpeg -y -loglevel error -i "$AD/base.mp4" -i "$AD/bokeh.png" -i "$AD/vignette.png" \
    -filter_complex "[1:v]format=rgba,colorchannelmixer=aa=0.55[bok];[0:v][bok]overlay=x=0:y='-(t*23)':format=auto[b1];[b1][2:v]overlay=0:0:format=auto[vout]" \
    -map "[vout]" -c:v libx264 -pix_fmt yuv420p -r 30 "$OUT"
fi
echo "Done: $OUT"
ffprobe -v error -show_entries format=duration:stream=codec_type,codec_name -of default=noprint_wrappers=1 "$OUT"
