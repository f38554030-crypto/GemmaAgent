#!/usr/bin/env bash
# strip.sh <video> <out.jpg> <width_each> t1 t2 t3 ...  -> side-by-side frames with timestamps
V="$1"; O="$2"; W="$3"; shift 3
TMP=$(mktemp -d); i=0; inputs=()
for t in "$@"; do
  i=$((i+1))
  ffmpeg -hide_banner -loglevel error -y -ss "$t" -i "$V" -frames:v 1 -vf "scale=$W:-2,drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:text='$t s':x=8:y=8:fontsize=22:fontcolor=yellow:box=1:boxcolor=black@0.7" "$TMP/$(printf %03d $i).png"
done
ffmpeg -hide_banner -loglevel error -y -pattern_type glob -i "$TMP/*.png" -vf "tile=${i}x1:padding=6:color=0x333333" -q:v 2 "$O"
rm -rf "$TMP"
