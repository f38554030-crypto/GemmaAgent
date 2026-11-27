#!/usr/bin/env bash
# Usage: analyze.sh <video.mp4> <outdir>
# Automated technical QA pass for a rendered video. Produces text reports + frame contact sheets.
set -uo pipefail
V="$1"; O="$2"
mkdir -p "$O"/frames "$O"/sheets "$O"/scenes
cd "$O"

echo "== ffprobe ==" > report.txt
ffprobe -v error -show_format -show_streams -of json "$V" > probe.json
ffprobe -v error -show_entries format=duration,bit_rate,size:stream=index,codec_type,codec_name,profile,width,height,pix_fmt,r_frame_rate,avg_frame_rate,nb_frames,sample_rate,channels,bit_rate,color_space,color_range -of default=nw=1 "$V" >> report.txt

DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$V")
echo "duration=$DUR" >> report.txt

echo "== black frames (pic_th=0.98, d>=0.05s) ==" >> report.txt
ffmpeg -hide_banner -nostats -i "$V" -vf "blackdetect=d=0.05:pic_th=0.98:pix_th=0.10" -an -f null - 2>&1 | grep -o "black_start.*" >> report.txt

echo "== frozen video (>=1.5s, noise -60dB) ==" >> report.txt
ffmpeg -hide_banner -nostats -i "$V" -vf "freezedetect=n=-60dB:d=1.5" -an -f null - 2>&1 | grep -o "lavfi.freezedetect.*" >> report.txt

echo "== scene changes (score>0.25) ==" >> report.txt
ffmpeg -hide_banner -nostats -i "$V" -vf "select='gt(scene,0.25)',showinfo" -an -f null - 2>&1 \
  | grep -o "pts_time:[0-9.]*" | sed 's/pts_time://' > scene_times.txt
cat scene_times.txt | tr '\n' ' ' >> report.txt; echo >> report.txt

echo "== audio: silence (<-40dB, >=0.6s) ==" >> report.txt
ffmpeg -hide_banner -nostats -i "$V" -af "silencedetect=n=-40dB:d=0.6" -vn -f null - 2>&1 | grep -o "silence_.*" >> report.txt

echo "== audio: EBU R128 loudness ==" >> report.txt
ffmpeg -hide_banner -nostats -i "$V" -af "ebur128=peak=true" -vn -f null - 2>&1 | sed -n '/Summary:/,$p' >> report.txt

echo "== audio: astats ==" >> report.txt
ffmpeg -hide_banner -nostats -i "$V" -af "astats=metadata=0:reset=0" -vn -f null - 2>&1 \
  | grep -E "Overall|Peak level|RMS level|Flat factor|Peak count|Number of samples|DC offset|Noise floor dB" | head -40 >> report.txt

# Short-term loudness per 0.5 s (for spotting volume jumps / music ducking problems)
ffmpeg -hide_banner -nostats -i "$V" -af "ebur128=metadata=1,ametadata=print:key=lavfi.r128.M:file=loudness_M.txt" -vn -f null - >/dev/null 2>&1

# Audio track for later inspection
ffmpeg -hide_banner -loglevel error -y -i "$V" -vn -ac 1 -ar 16000 audio16k.wav
ffmpeg -hide_banner -loglevel error -y -i "$V" -vn -c:a pcm_s16le audio_full.wav

# One frame every 0.5 s at half res (for close reading) + labelled contact sheets (1 fps, 4x4 per sheet)
ffmpeg -hide_banner -loglevel error -y -i "$V" -vf "fps=2,scale=540:-2" -q:v 3 frames/f_%05d.jpg
ffmpeg -hide_banner -loglevel error -y -i "$V" -vf "fps=1,scale=270:-2,drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:text='%{pts\:hms}':x=6:y=6:fontsize=20:fontcolor=yellow:box=1:boxcolor=black@0.7,tile=6x3:padding=4:margin=4:color=0x333333" -q:v 3 sheets/sheet_%03d.jpg

# A frame just after each scene change (full res) for detailed review
i=0
while read -r t; do
  i=$((i+1)); tt=$(python3 -c "print(max(0,float('$t')+0.12))")
  ffmpeg -hide_banner -loglevel error -y -ss "$tt" -i "$V" -frames:v 1 -q:v 2 scenes/s_$(printf %03d $i)_${t}.jpg
done < scene_times.txt

echo "done: $O"
