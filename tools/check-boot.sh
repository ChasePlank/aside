#!/usr/bin/env bash
#
# Does the app BOOT? Not "do the suites pass" - do the things a person double-clicks actually start.
#
#   tools/check-boot.sh
#
# WHY THIS EXISTS. `aside.ui.Main` - and `Launcher`, which launches it - were named in this repository only by
# `tools/make-jar.sh`, AS A STRING: the class the jar's manifest points at. Nothing ran them. That means a break in
# the ENTRY POINT is invisible to the whole gate: the suites construct their screens directly, the frames come from
# `CheckGames` (a tools class), and the phone builds are separate files. Asset loading, the UiManager's construction,
# the LibraryScreen the app opens on and the window itself had no check between them.
#
# WHAT IT USES, RATHER THAN A NEW HARNESS. Main already has a snapshot mode - `-Daside.snapshot=<file>` renders 90
# frames and writes a PNG - which is the app's own path, not a stand-in for it: root resolution, Assets.load,
# Audio.load, the UiManager, ninety ticks of the same AnimationTimer the game uses, and a composited frame.
#
# THE EXIT CODE CARRIES NO INFORMATION HERE, and that is measured rather than assumed: Main's snapshot branch prints
# `[snapshot] failed: <exception>` and exits 0 anyway, because it only calls Platform.exit(). So this asserts on the
# OUTPUT and the FILE. A boot check that trusted the exit code would pass on a frame that was never written.
set -u
cd "$(dirname "$0")/.." || exit 2

. "$(dirname "$0")/find-java.sh"

SHOT=$(mktemp -u /tmp/aside-boot-XXXXXX.png)
LOG=$(mktemp)
trap 'rm -f "$SHOT" "$LOG"' EXIT

# THROUGH run-headless.sh, because a JavaFX command needs a display that may have just died. This called "$JAVA"
# directly, so with no live display the app failed at PlatformImpl.startup and this check exited 1 WITHOUT EVER
# LOOKING AT THE BOOT -- and its own fault injection then failed for the wrong reason: it asserts the output says
# "could not find its art", and what it got was a JavaFX startup stack trace. run-headless.sh asks the display
# whether it is SERVING with xdpyinfo and starts Xvfb if not, which is what check-sheets.sh and
# regenerate-frames.sh already do for CheckGames.
tools/run-headless.sh "$JAVA" --module-path "$FX" --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -cp "$OUT:src/main/resources" -Daside.snapshot="$SHOT" aside.ui.Main > "$LOG" 2>&1
rc=$?

# THE FAILURE THIS IS FOR IS LOUD IN THE OUTPUT AND SILENT IN THE EXIT CODE, so read the output first.
if grep -q "\[snapshot\] failed" "$LOG"; then
  echo "  the app started but could not draw a frame:"
  grep "\[snapshot\] failed" "$LOG" | head -3 | sed 's/^/    /'
  exit 1
fi
if ! grep -q "\[snapshot\] wrote" "$LOG"; then
  echo "  the app did not reach its own snapshot: exit $rc, and no frame written"
  grep -viE '^WARNING' "$LOG" | tail -6 | sed 's/^/    /'
  exit 1
fi
[ -f "$SHOT" ] || { echo "  it says it wrote a frame and the file is not there: $SHOT" >&2; exit 1; }

# THE APP REPORTS WHAT IT LOADED, AND THAT IS A BETTER SIGNAL THAN ANY PIXEL MEASUREMENT. MEASURED both ways:
#   with art/     "[assets] loaded 12 backgrounds, 17 sprites"
#   without art/  "[assets] no background folder at ./art/backgrounds" ... "[assets] loaded 0 backgrounds, 0 sprites"
# and the app BOOTS AND DRAWS EITHER WAY - 434017 bytes and 11761 colours against 78145 bytes and 1245 colours - so a
# check that only looked at the frame called a missing asset folder a pass. It did: the first version of this tool
# said "boots, loads its assets, and draws the library screen" over a screen with no art in it.
if grep -qE '^\[assets\] (no |missing)' "$LOG"; then
  echo "  the app could not find its art:"
  grep -E '^\[assets\] (no |missing)' "$LOG" | head -4 | sed 's/^/    /'
  exit 1
fi
COUNTS=$(grep -oE '^\[assets\] loaded [0-9]+ backgrounds, [0-9]+ sprites' "$LOG" | head -1)
BGS=$(echo "$COUNTS" | grep -oE '[0-9]+ backgrounds' | grep -oE '[0-9]+')
SPRS=$(echo "$COUNTS" | grep -oE '[0-9]+ sprites' | grep -oE '[0-9]+')
if [ -z "${BGS:-}" ] || [ "${BGS:-0}" -eq 0 ] || [ "${SPRS:-0}" -eq 0 ]; then
  echo "  the app loaded no art: ${COUNTS:-no asset line at all}" >&2
  exit 1
fi

SIZE=$(stat -c%s "$SHOT")
COLOURS=$(python3 - "$SHOT" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB")
print(len(im.getcolors(maxcolors=1 << 24) or []))
PY
)
MAGIC=$(python3 -c "print(open('$SHOT','rb').read(4) == b'\x89PNG')")
printf '  booted: %s backgrounds, %s sprites; drew %s bytes, %s distinct colour(s), PNG magic %s\n' "$BGS" "$SPRS" "$SIZE" "$COLOURS" "$MAGIC"

# A BLACK RECTANGLE IS NOT A SCREEN. The window could open, the scene could be empty and the frame would still be
# a file - so the picture has to have more than one colour in it. The exact floor is printed above on every run.
if [ "$MAGIC" != "True" ]; then echo "  the frame is not a PNG" >&2; exit 1; fi
if [ "$SIZE" -lt 4000 ]; then echo "  the frame is $SIZE bytes - too small to be a screen" >&2; exit 1; fi
if [ "$COLOURS" -lt 50 ]; then echo "  only $COLOURS distinct colour(s): the app drew an empty screen" >&2; exit 1; fi
echo "  === the app boots, finds its art, and draws the library screen ==="
