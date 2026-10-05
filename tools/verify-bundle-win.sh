#!/usr/bin/env bash
#
# verify-bundle-win.sh - run a WINDOWS bundle here, under Wine, and look at it.
#
#   tools/verify-bundle-win.sh <path-to-zip> [name-inside]
#
# WHY. make-bundle-win.sh builds a Windows bundle and cannot run it, so it says the launcher is untested. That is
# honest and it is not good enough: on 2026-10-05 the person who asked for a download-and-play build uses Windows,
# and shipping him an unverified launcher is shipping him a guess.
#
# Wine closes the gap. It is not Windows, but it runs java.exe, reads the .bat, and loads the JavaFX natives - so a
# bundle that starts and DRAWS under Wine has a launcher, a runtime and natives that all work.
#
# WHAT IT CANNOT SHOW: fonts. A modern JDK ships none of its own; it uses the system's through fontconfig.bfc,
# which Windows has and Wine emulates imperfectly. So the TEXT comes out as empty boxes under Wine and that is
# expected - the layout, the colours, the selection and the scrollbars are all real.
#
# COST: wine is 39 packages and about a gigabyte, and the prefix it makes is another 700 MB. Check df first.
set -u

ZIP="${1:-}"
[ -f "$ZIP" ] || { echo "usage: tools/verify-bundle-win.sh <path-to-zip> [name-inside]" >&2; exit 2; }

WORK="${WORK:-/root/winverify}"
PREFIX="${PREFIX:-/root/wineprefix}"
export WINEPREFIX="$PREFIX" WINEDEBUG=-all

if ! command -v wine >/dev/null; then
  echo "installing wine (39 packages, ~1 GB) ..."
  apt-get update -qq && apt-get install -y -qq wine64 || { echo "could not install wine" >&2; exit 2; }
fi
command -v wine >/dev/null || { echo "verify-bundle-win: no wine" >&2; exit 2; }

# A display, waited for rather than assumed - the lesson from make-bundle.sh.
D="${DISPLAY:-:99}"
if ! xdpyinfo -display "$D" >/dev/null 2>&1; then
  Xvfb "$D" -screen 0 1400x900x24 >/dev/null 2>&1 &
  for _ in $(seq 1 40); do xdpyinfo -display "$D" >/dev/null 2>&1 && break; sleep 0.25; done
fi
xdpyinfo -display "$D" >/dev/null 2>&1 || { echo "verify-bundle-win: no display on $D" >&2; exit 2; }

rm -rf "$WORK"; mkdir -p "$WORK"
unzip -q "$ZIP" -d "$WORK" || { echo "verify-bundle-win: could not unzip $ZIP" >&2; exit 1; }
NAME="${2:-$(ls "$WORK" | head -1)}"
DIR="$WORK/$NAME"
[ -d "$DIR" ] || { echo "verify-bundle-win: no $NAME inside $ZIP" >&2; exit 1; }
BAT=$(ls "$DIR"/*.bat 2>/dev/null | head -1)
[ -n "$BAT" ] || { echo "verify-bundle-win: no .bat in $DIR" >&2; exit 1; }
echo "running $(basename "$BAT") under wine ..."

[ -d "$PREFIX" ] || timeout 300 wineboot -i >/dev/null 2>&1
LOG="$WORK/run.log"
( cd "$DIR" && DISPLAY="$D" timeout 90 wine cmd /c "$(basename "$BAT")" >"$LOG" 2>&1 ) &
sleep 45

if pgrep -f "java.exe" >/dev/null; then
  echo "  java.exe is running"
else
  echo "  FAIL: no java.exe. Log:"; sed 's/^/    /' "$LOG" | tail -12; exit 1
fi
grep -qiE "^Exception|Error:|ClassNotFound|UnsatisfiedLink" "$LOG" && { echo "  FAIL: errors in the log"; sed 's/^/    /' "$LOG" | grep -iE "exception|error" | head -6; exit 1; }
echo "  no exceptions in the log"

if command -v import >/dev/null; then
  import -window root -display "$D" "$WORK/shot.png" 2>/dev/null && \
    echo "  screenshot: $WORK/shot.png"
fi
wait 2>/dev/null
echo
echo "  It started and drew. Note that TEXT WILL BE EMPTY BOXES under Wine - a modern JDK ships no fonts and"
echo "  uses the system's through fontconfig.bfc. Windows has them; Wine emulates them imperfectly."
exit 0
