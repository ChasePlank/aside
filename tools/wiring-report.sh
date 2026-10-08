#!/usr/bin/env bash
#
# wiring-report.sh - which engine classes the GAME never uses.
#
#   tools/wiring-report.sh [path-to-the-source-root]
#
# WHY THIS EXISTS. On 2026-10-02 a sweep for "engine features with no consumer" found invisible bats, invisible
# particles and several dead classes, and the sweep was done by hand with grep. It has been re-done by hand twice
# since, each time with a slightly different pattern, and each time the pattern produced a false answer: a grep
# for `new Boss(` misses a class built through a factory, and a grep for a list name I GUESSED (`oneway`) missed
# the real field (`oneways`) and reported a live feature as dead. Guessing names is the failure mode.
#
# So this reads the names out of the source instead of being told them, and it reports three things:
#
#   1. Engine classes the game never names. A test naming a class does not count - a class only a test can reach
#      is a class the player cannot.
#   2. World collections whose ONLY writer is an `add*` method that nothing calls. That is the exact shape of
#      the moving-platform list: `movers` is filled by `addMover`, `addMover` is never called, so the list is
#      always empty and the physics loop over it does nothing forever. Counting `list.add(` inside its own
#      accessor is what makes a naive check miss this.
#   3. Mutators on engine fields that nothing calls, for the same reason.
#
# AND THE CALLER TEST IS BY NAME, WHICH IS A KNOWN WEAKNESS. This file's own warning is that "guessing names is
# the failure mode", and section 3 guesses: it asks whether anything anywhere calls `.<name>(`, which cannot see
# the TYPE of the receiver. Found on 2026-10-08: `AudioSystem.toggleMute` has no caller in the game, and this
# report does not list it, because UiManager calls `Audio.A.toggleMute()` - a different class's method that
# happens to share the name. A name collision anywhere in the tree therefore makes a dead method look alive, and
# that method was instead found by reading the class by hand.
#
# It is left name-based rather than "fixed" with a heuristic, because the obvious fix - require the calling file
# to name the declaring class - would produce FALSE DEAD reports for every call through a variable (`body.setX()`
# where the declaring class is never spelled out), and a false report is the failure this whole file exists to
# avoid. So the limitation is written down instead of half-solved. Anything section 3 does NOT list is "called
# somewhere, by something", not "called by the right thing".
#
# IT IS A REPORT, NOT A GATE. Dead code is a decision - wire it or delete it - and these four have been waiting
# on that decision since 2026-10-02. A report that fails every run would be turned off. What it must not be is
# silent: the point is that a NEW one shows up next to the four known ones.
set -u
cd "$(dirname "$0")/.." || exit 2
SRC="${1:-src/main/java}"
ENGINE="$SRC/aside/games/fruitjump/engine"
# Every finding this run, so the KNOWN list at the bottom can be CHECKED against it rather than asserted. See there.

[ -d "$ENGINE" ] || { echo "wiring-report: no engine package at $ENGINE" >&2; exit 2; }

# A reference from a test file is not a use by the game. This is the filter that makes the rest honest.
game_files() { grep -rl "$1" "$SRC" --include=*.java 2>/dev/null | grep -vE "(Test|Suite)\.java$"; }

echo "=== engine classes the game never names ==="
FINDINGS=""
found=0
for f in "$ENGINE"/*.java; do
  n=$(basename "$f" .java)
  case "$n" in *Test|*Suite) continue;; esac
  refs=$(game_files "\b$n\b" | grep -v "/$n\.java$" | wc -l)
  if [ "$refs" = "0" ]; then printf '  %-22s never named outside itself\n' "$n"; found=$((found+1)); FINDINGS="$FINDINGS $n"; fi
done
[ "$found" = "0" ] && echo "  none"

echo
echo "=== World collections whose only writer is an uncalled add* method ==="
found=0
for l in $(grep -oE "List<[A-Za-z.]+> [a-zA-Z]+" "$ENGINE/World.java" | awk '{print $NF}' | sort -u); do
  # WHICH adder writes to it, and does the write sit INSIDE that adder's body?
  #
  # The first version asked whether any write happened OUTSIDE World.java. That is not the same question:
  # `emitters.add(Emitter.burst(...))` sits in World's own splash code, so it counted as "inside the adder" and
  # the report called a LIVE list dead. What matters is the method body, not the file.
  adder=""
  for m in $(grep -oE "public void add[A-Za-z]+\(" "$ENGINE/World.java" | sed 's/public void //; s/($//' | tr -d '('); do
    if sed -n "/public void $m(/,/^    }/p" "$ENGINE/World.java" | grep -q "\b$l\.add("; then adder="$m"; break; fi
  done
  [ -z "$adder" ] && continue
  # writes that are NOT in that body: those are real uses and the list is alive
  body=$(sed -n "/public void $adder(/,/^    }/p" "$ENGINE/World.java")
  alive=0
  while IFS= read -r line; do
    grep -qF "$line" <<<"$body" || alive=$((alive+1))
  done < <(grep -rh "\b$l\.add(" "$SRC" --include=*.java 2>/dev/null)
  if [ "$alive" = "0" ]; then
    callers=$(grep -rn "\.$adder(" "$SRC" --include=*.java 2>/dev/null | grep -v "engine/World.java" | grep -vE "(Test|Suite)\.java$" | wc -l)
    if [ "$callers" = "0" ]; then printf '  %-12s filled only by %s(), which nothing calls\n' "$l" "$adder"; found=$((found+1)); FINDINGS="$FINDINGS $l"; fi
  fi
done
[ "$found" = "0" ] && echo "  none"

echo
echo "=== engine mutators nothing calls ==="
# BOTH SHAPES, and the second one is why this line changed. The scan used to be `public void set[A-Z]*` only,
# so a method named toggleMute() was invisible to it - and AudioSystem.toggleMute had no caller at all, which
# went unnoticed for a day and was found by reading the class by hand rather than by this report. A scan pattern
# is a claim about what the code looks like, and the thing it cannot see is the thing it will never report.
# Kept to two shapes rather than a general "public void <verb>" sweep, because a broad pattern turns a report
# about dead code into a list of every method with a conventional name.
found=0
for m in $(grep -rhoE "public void (set|toggle)[A-Z][A-Za-z]*\(" "$ENGINE"/*.java | sed 's/public void //; s/($//' | tr -d '(' | sort -u); do
  callers=$(grep -rn "\.$m(" "$SRC" --include=*.java 2>/dev/null | grep -vE "(Test|Suite)\.java$" | grep -vE "public void $m\(" | wc -l)
  if [ "$callers" = "0" ]; then printf '  %-22s never called\n' "$m"; found=$((found+1)); FINDINGS="$FINDINGS $m"; fi
done
[ "$found" = "0" ] && echo "  none"

echo
echo "A finding here is a QUESTION, not a fault: dead code is either a feature waiting to be wired or code"
echo "waiting to be deleted, and only the person who wanted it can say which."
echo
# THE KNOWN LIST IS CHECKED AGAINST THIS RUN, NOT TYPED. It used to be six lines of echo naming what to
# expect, and within one working day two of its four entries had been wired - MovingPlatform and ParallaxLayer -
# so it was telling a reader to expect findings that could no longer happen, which is worse than having no list:
# it would make the next real finding look familiar. The list is a claim about the code, so the tool measures it
# against the report it just produced. Both branches are exercised right now, because WaterProbe is still on the
# report and the two that were wired are not.
#
# A NEW SECTION MUST APPEND TO $FINDINGS. The footer can only be as complete as what the sections tell it, so a
# fourth section that prints findings without recording them would leave the list silently short - not wrong in
# the way the typed list was, but wrong the same direction. Left as a comment rather than a guard because the
# guard would be a second copy of the same analysis, and this is a report rather than a gate.
echo "KNOWN FINDINGS - each one checked against the report above, not assumed:"
known() {
  for n in $1; do
    case " $FINDINGS " in
      *" $n "*) printf '  %-26s still unwired   (%s)\n' "$2" "$n"; return;;
    esac
  done
  printf '  %-26s NOT ON THIS RUN - wired, or deleted, or renamed\n' "$2"
}
known "WaterProbe LevelValidator" "test tools"
known "movers"                   "MovingPlatform"
known "setDirector"              "AIDirector"
known "setSeed setVolleyCallback" "Boss"
known "ParallaxLayer"            "ParallaxLayer"
echo
echo "  The four from 2026-10-02 are Boss, AIDirector, MovingPlatform and ParallaxLayer, and they have been"
echo "  waiting on a wire-or-delete decision since. ANYTHING ELSE ON THIS REPORT IS NEW AND WORTH A LOOK - and a"
echo "  name above that has left the report has been dealt with, so its return would be a REGRESSION, not a"
echo "  familiar sight."
