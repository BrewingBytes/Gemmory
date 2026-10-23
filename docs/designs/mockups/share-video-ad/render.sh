#!/bin/zsh
# Renders frame $1 of the share-video mock (15 fps) to mp4frames/.
cd "${0:A:h}"
B="/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"
i=$1
t=$(echo "scale=4; $i/15" | bc)
timeout 60 "$B" --headless=new --hide-scrollbars --user-data-dir=/tmp/brave-mock-$((i % 4)) --window-size=540,960 \
  --virtual-time-budget=3000 --screenshot="$PWD/mp4frames/f$(printf %04d $i).png" \
  "http://127.0.0.1:8765/video.html?raw&t=$t" >/dev/null 2>&1
