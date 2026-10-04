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
# OUT IS NOT DEFAULTED HERE: find-java.sh looks for a build directory,
# because a default that names one is a guess. See the note there.
# FX IS NOT DEFAULTED HERE, for the same reason JAVA is not: a value set
# before find-java.sh runs makes its search a no-op, and this one defaulted
# to a path that does not exist. Let the lookup do its job.

. "$(dirname "$0")/find-java.sh"

[ -d docs/frames ] || { echo "no docs/frames to compare against" >&2; exit 2; }

A=$(mktemp -d)
trap 'rm -rf "$A"' EXIT

# The run's output is KEPT, because it carries the verdict the gate needs: every
# game opened and drew something. That verdict used to come from a second
# CheckGames run in run-suites.sh, and a second run of this is several minutes
# for an answer this one already has.
GAMES_LOG="$A/checkgames.log"
run() {
  tools/run-headless.sh "$JAVA" --module-path "$FX" \
    --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -Dshotdir="$1" -cp "$OUT:src/main/resources" aside.tools.CheckGames > "$GAMES_LOG" 2>&1
}
# ONE run, not two. The second was there to detect non-determinism by running
# twice, and that was replaced by NAMING the two frames that cannot be compared
# -- so the second run's output was never read. It cost about five minutes of a
# gate that already takes ten to fifteen, and CheckGames opens all twenty-three
# games each time. Found by asking what the gate spends its time on.
run "$A"

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

# The games verdict, passed through so the gate does not have to run CheckGames
# a second time to get it.
echo
echo "  $(grep -E '=== [0-9]+ game' "$GAMES_LOG" | tail -1)"
grep -E 'did not draw' "$GAMES_LOG" | sed 's/^/    /'
if grep -qE '=== [0-9]+ game' "$GAMES_LOG" && ! grep -q ', 0 did not' "$GAMES_LOG"; then
  stale=1
fi

if [ $stale -eq 1 ]; then
  echo
  echo "  FIX: tools/regenerate-frames.sh    (then git add docs/ and commit)"
  echo
  echo "  WHY THIS KEEPS HAPPENING, and it is not a mystery: the frames are a shared derived artifact and more"
  echo "  than one agent commits here. A frame goes stale whenever a game changes OR whenever a merge brings"
  echo "  someone else's game change in - and the merge is the one that surprises you, because you did not touch"
  echo "  the game. It has fired four times on 2026-10-04 for that reason alone."
  echo
  echo "  Regenerating is safe and cheap: the frames are a function of the code, so this either changes nothing"
  echo "  or records what the code now draws. The three names above are the ones that cannot be compared at all."
fi

echo
echo "=== $checked frame(s) compared, $skipped not comparable ==="
echo "not comparable, by name and for a reason:"
echo "  drift    - Drift.of() seeds from System.nanoTime(), so the frame is a function of when you ran it"
echo "  ledger   - opens from a save, so the frame is a function of what a previous run left behind"
if [ $stale -ne 0 ]; then
  echo
  # NAME THE TOOL, not the steps it replaced. This said "regenerate with
  # aside.tools.CheckGames and copy the frames to docs/frames/", which is the
  # two-command job tools/regenerate-frames.sh was written to do -- so the one
  # place a person looks when a frame is stale was telling them to do it by
  # hand. A message that describes the manual version of a solved problem is
  # worse than no message.
  echo "Regenerate with tools/regenerate-frames.sh, then commit what it wrote."
  exit 1
fi
