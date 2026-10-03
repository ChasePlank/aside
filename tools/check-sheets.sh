#!/usr/bin/env bash
#
# Are the committed frames still what the games produce?
#
#   tools/check-sheets.sh
#
# WHY FRAMES AND NOT THE SHEET. The sheet is composed from the frames, so comparing it compares everything at
# once - including the games that cannot be compared. Two of them seed from a clock: `Drift.of()` is
# `new Drift(System.nanoTime())`, so drift's frame and ledger's (which reads it) are different on every run. The
# first version of this compared the sheet, went stale twice in a row, and the second time it was reporting its
# own non-determinism as a stale file.
#
# So: run the games TWICE, see which frames vary between two identical runs, and compare the rest against what is
# committed. The frames that vary are named and skipped rather than silently tolerated - a check that quietly
# ignores something is the thing this repository keeps finding.
set -u

cd "$(dirname "$0")/.." || exit 2
OUT="${OUT:-out}"
FX="${FX:-/root/javafx-sdk-27/lib}"

. "$(dirname "$0")/find-java.sh"

[ -d docs/frames ] || { echo "no docs/frames to compare against" >&2; exit 2; }

A=$(mktemp -d); B=$(mktemp -d)
trap 'rm -rf "$A" "$B"' EXIT

run() {
  tools/run-headless.sh "$JAVA" --module-path "$FX" \
    --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -Dshotdir="$1" -cp "$OUT:src/main/resources" aside.tools.CheckGames >/dev/null 2>&1
}
run "$A"
run "$B"

# TWO GAMES CANNOT BE COMPARED, and they are named rather than detected.
#
# Detecting them by running twice was the first version and it is a heuristic that lies: on the run that prompted
# this, drift varied and ledger happened to match, so ledger was reported as STALE - a real change and a
# coincidence look the same from one pair of runs.
#
# The reasons are structural, and both are the same shape - the frame is not a function of the code alone:
#
#   drift   `Drift.of()` is `new Drift(System.nanoTime())`. The frame is a function of WHEN you ran it.
#   ledger  opens from a SAVE (`Ledger.load(save)`), so its frame is a function of what a previous run left
#           behind. CheckGames runs the games in sequence, so the second run of it sees the first run's saves.
#
# Both are worth knowing beyond this script: a game whose opening frame depends on a clock or on a save cannot be
# screenshot-compared by anything, and the honest response is to say so rather than to add tolerance.
varying=" drift.png ledger.png"

stale=0; checked=0; skipped=0
for f in "$A"/*.png; do
  n=$(basename "$f")
  [ "$n" = "contact-sheet.png" ] && continue
  case " $varying " in *" $n "*) skipped=$((skipped + 1)); continue;; esac
  if [ ! -f "docs/frames/$n" ]; then
    printf '  %-22s MISSING from docs/frames\n' "$n"; stale=1; continue
  fi
  checked=$((checked + 1))
  cmp -s "$f" "docs/frames/$n" || { printf '  %-22s STALE\n' "$n"; stale=1; }
done

echo
echo "=== $checked frame(s) compared, $skipped not comparable ==="
echo "not comparable, by name and for a reason:"
echo "  drift    - Drift.of() seeds from System.nanoTime(), so the frame is a function of when you ran it"
echo "  ledger   - opens from a save, so the frame is a function of what a previous run left behind"
if [ $stale -ne 0 ]; then
  echo
  echo "Regenerate with aside.tools.CheckGames and copy the frames to docs/frames/."
  exit 1
fi
