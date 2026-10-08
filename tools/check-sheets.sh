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
# THE PLATFORMER'S AUTOSAVE MOVES ASIDE FOR THE RUN, and this is what made the frames look machine-specific.
# Holdfast writes user.home/.tropical-punch-autosave.txt while it is played, and its menu adds a "Continue" item
# whenever that file exists - so the fruitjump frame is a function of whether ANYBODY has played the game since the
# last regeneration, not of the code. MEASURED: with the file present this machine disagreed with the committed
# frame in 8653 pixels, every one of them inside the menu; with the file absent it renders the committed frame BYTE
# FOR BYTE, and check-sheets reports 0 STALE. Two agents spent an hour flipping that one frame back and forth,
# blamed the machine, and one of them wrote it into memory as an environment difference. It was a save file.
#
# NOT $HOME. Java takes user.home from the OS, not from the environment: here $HOME is /root/workspace and
# user.home is /root, so a script that moved "$HOME/..." would move the wrong file and look like it worked. The
# JVM is asked, because the JVM is what decides.
AUTOSAVE_HOME=$("$JAVA" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *user.home = //p' | head -1)
[ -n "$AUTOSAVE_HOME" ] || AUTOSAVE_HOME="${HOME:-/root}"
AUTOSAVE="$AUTOSAVE_HOME/.tropical-punch-autosave.txt"
AUTOSAVE_BAK=""
if [ -e "$AUTOSAVE" ]; then AUTOSAVE_BAK="$A/autosave.bak"; mv "$AUTOSAVE" "$AUTOSAVE_BAK"; fi
restore_autosave() {
  if [ -n "$AUTOSAVE_BAK" ] && [ -e "$AUTOSAVE_BAK" ]; then mv "$AUTOSAVE_BAK" "$AUTOSAVE"; fi
}
trap 'restore_autosave; rm -rf "$A"' EXIT

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
# NAMED, WITH THEIR REASONS, and the list was incomplete until now. drift and ledger were the two that were
# known; adding a third kind of game exposed the rest. MEASURED this hour: with the platformer's autosave handled
# and saves/ pristine, ELEVEN frames still differ from the committed ones and the reasons split into two groups
# that need two different mechanisms.
#
# THIS GROUP IS NON-DETERMINISTIC and is named, which is this tool's own rule - "a check that quietly tolerates
# non-determinism is worse than one that names it". Nine FNAF games and Lesson all seed from the clock:
#
#   lesson   Lesson.of() is `new Lesson(System.nanoTime())` - character for character the shape of Drift.of(),
#            which is already named below for exactly this reason.
#   fnaf*    fnaf/GameScreen.java: `this.seed = forced != null ? Long.parseLong(forced) : System.nanoTime();`,
#            with a comment of its own admitting that the inline version "made every run unreplayable". fnaf2
#            through fnaf9 do the same, and fnaf also drives rotation, pulse and zoom straight off `nanoTime`.
#
# The frames ARE stable across two consecutive runs on one machine, which is why a two-run test cannot detect
# this and why they stayed on the comparable list: two runs a minute apart agree, and two runs on different days
# do not. The frame is a function of WHEN it was taken, which is the criterion above.
varying=" drift.png ledger.png lesson.png fnaf.png fnaf2.png fnaf3.png fnaf4.png fnaf5.png fnaf6.png fnaf7.png fnaf8.png fnaf9.png"

# AND A SECOND GROUP NEEDS A TOLERANCE RATHER THAN A NAME. Two frames differ between this machine and the one
# whose frames are committed by a handful of pixels and nothing else - MEASURED: bell-codes 6 pixels,
# discrepancy 2, out of 921,600, all of them along sprite and text edges. That is antialiasing precision, not
# content, and these two frames are otherwise identical and perfectly comparable within a machine.
#
# Naming them would be wrong (they are deterministic - the rule above does not apply) and comparing them exactly
# would be wrong too (2 pixels then read as a stale frame, and a gate that fails on an antialiasing difference
# between two machines is one people learn to ignore). So there is a small, printed allowance. It is a measured
# noise floor with margin, not a guess: the observed worst case is 6 pixels and this allows 32, which is still
# far below any real change - a moved sprite or a changed string is thousands.
TOLERANCE=32

stale=0; checked=0; skipped=0
for f in "$A"/*.png; do
  n=$(basename "$f")
  [ "$n" = "contact-sheet.png" ] && continue
  case " $varying " in *" $n "*) skipped=$((skipped + 1)); continue;; esac
  if [ ! -f "docs/frames/$n" ]; then
    printf '  %-22s MISSING from docs/frames\n' "$n"; stale=1; continue
  fi
  checked=$((checked + 1))
  # HOW MANY PIXELS, not just whether the bytes match. `cmp` was the whole comparison and it reports a two-pixel
  # antialiasing difference between two machines exactly as loudly as a changed screen.
  d=$(python3 - "$f" "docs/frames/$n" <<'PY'
import sys
from PIL import Image, ImageChops
a = Image.open(sys.argv[1]).convert("RGB"); b = Image.open(sys.argv[2]).convert("RGB")
if a.size != b.size:
    print(-1); raise SystemExit
h = ImageChops.difference(a, b).convert("L").histogram()
print(sum(h) - h[0])
PY
)
  if [ "$d" = "-1" ]; then
    printf '  %-22s STALE    the frame is a different size\n' "$n"; stale=1
  elif [ "$d" -gt "$TOLERANCE" ]; then
    printf '  %-22s STALE    %s pixel(s) differ\n' "$n" "$d"; stale=1
  elif [ "$d" -gt 0 ]; then
    printf '  %-22s ok       %s pixel(s) differ, within the %s allowance\n' "$n" "$d" "$TOLERANCE"
  fi
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
  echo "  the game. Every merge that carries someone else's game change will do it again."
  echo
  echo "  (This line used to say how many times it had fired on a particular day. A count in a message goes"
  echo "  stale the next day, which is the same fault as a comment that outlives its code - so it is gone.)"
  echo
  echo "  Regenerating is safe and cheap: the frames are a function of the code, so this either changes nothing"
  echo "  or records what the code now draws. The three names above are the ones that cannot be compared at all."
fi

echo
echo "=== $checked frame(s) compared, $skipped not comparable ==="
echo "not comparable, by name and for a reason:"
echo "  drift    - Drift.of() seeds from System.nanoTime(), so the frame is a function of when you ran it"
echo "  ledger   - opens from a save, so the frame is a function of what a previous run left behind"
echo "  lesson   - Lesson.of() seeds from System.nanoTime() too, the same shape as Drift"
echo "  fnaf..9  - each seeds its run from System.nanoTime(), and fnaf drives rotation and pulse off it as well"
echo "             (all nine are also stable for a few minutes on one machine, which is why a two-run test)"
echo "             (does not find them - the frame is a function of the day, not of the second)"
echo
echo "  and a frame within ${TOLERANCE} pixel(s) of the committed one is reported as ok with the count printed,"
echo "  because two machines render antialiasing differently: bell-codes and discrepancy differ by 2 to 6 pixels"
echo "  here and by nothing else."
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
