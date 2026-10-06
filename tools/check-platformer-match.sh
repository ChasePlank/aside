#!/usr/bin/env bash
#
# check-platformer-match.sh - compare the platformer's screens between aside and the release, ignoring the hosting.
#
#   tools/check-platformer-match.sh [path-to-the-release]
#
# WHY THIS EXISTS. On 6 October the release was found to have fired arrows from `player.y - 10` when aside fires
# from `player.y` - so arrows sailed over anything level with the player. The fix landed in aside on 1 October and
# never reached the release, which shipped the bug for five days.
#
# NO TOOL WAS LOOKING. The FNAF pair has a nightly check; the platformer pair has none. A hand comparison found the
# arrow, and a sweep afterwards found EIGHT screens differing by line count. Some of that is the framework gap the
# sync cannot carry - Skyline, for instance, says outright that it has no text fade because this copy's screens are
# centred. Some may be more fixes that never travelled. THE COUNT CANNOT TELL YOU WHICH, which is how the arrow hid.
#
# So this normalises the known framework difference away and diffs what is left, the same shape as
# check-fnaf-content.sh. It is a REPORT, not a gate: a difference here is a question, not a fault.
set -u
REL="${1:-../tp}"
cd "$(dirname "$0")/.." || exit 2

A_DIR=src/main/java/aside/games/fruitjump
R_DIR="$REL/src/main/java/tropical"
[ -d "$A_DIR" ] || { echo "no $A_DIR" >&2; exit 2; }
[ -d "$R_DIR" ] || { echo "no $R_DIR (pass the release path)" >&2; exit 2; }

norm() {
  # COMMENTS OUT, and they are not a detail. Reading the first two small differences by hand showed both were
  # comments: Tutorial's 7 lines are the SAME fix with a longer explanation in aside, and Skyline's 16 are a
  # deliberate feature difference it documents itself. Comments are not drift.
  #
  # The comment strip uses # as its delimiter because the pattern contains /. The first version used : and the
  # explanatory comment above it was inside the sed script, so sed read the prose as commands and tried to open
  # files called "7" and "lines".
  sed -E '
    s#//.*$##
    /^(package|import) /d
    s/aside\.games\.fruitjump/tropical/g
    s/aside\.ui\.//g
    s/\bUiScreen\b/Screen/g
    s/\bUiManager\b/ScreenManager/g
    s/\bLibraryScreen\b/Library/g
    s/^final class/public final class/
    s/^    enum Mood/    public enum Mood/
    s/[[:space:]]+$//
    /^[[:space:]]*$/d
  ' "$1"
}

total=0
for f in "$A_DIR"/*.java; do
  n=$(basename "$f")
  [ "$n" = "FruitJumpGame.java" ] && continue    # the module wrapper exists only where there is a Game registry
  r="$R_DIR/$n"
  if [ ! -f "$r" ]; then printf '  %-22s only in aside\n' "${n%.java}"; continue; fi
  norm "$f" > /tmp/pm-a.txt; norm "$r" > /tmp/pm-r.txt
  d=$(diff /tmp/pm-a.txt /tmp/pm-r.txt | grep -cE '^[<>]' || true)
  total=$((total + d))
  if [ "$d" -eq 0 ]; then printf '  %-22s same\n' "${n%.java}"
  else printf '  %-22s %4s line(s)\n' "${n%.java}" "$d"; fi
done

echo
echo "=== $total differing line(s) after normalising the hosting framework ==="
echo "This is a REPORT. A difference is a question to read, not a fault - and a fix that never"
echo "travelled looks exactly like a framework difference until someone reads it."
