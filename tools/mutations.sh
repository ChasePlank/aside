#!/bin/bash
#
# Every invariant this project claims to check, broken on purpose, to ask whether a suite would notice.
#
#   tools/mutations.sh              run them all
#   tools/mutations.sh boss         only those whose label matches
#   tools/mutations.sh --list       show them without running anything
#
# WHY A LIST RATHER THAN A HABIT. "Remember to mutation-test" is useless as a note, and this project has learned
# that the hard way three times: a suite that passed while the code it protected was broken, a check that compared a
# measurement to the constant defining it, and - the hour this file was written - two constants that 636 checks
# could not see at all, both of them "verified" by probes run by hand once. A habit does not survive the next
# session; a list does, and it is the list that makes the next missing check findable.
#
# tools/mutate.sh does the breaking and the restoring. This file is the durable set of things worth breaking.
#
# A RESULT OF "NOT CAUGHT" IS THE POINT, not a failure of the tool: it means a claim is unverified and needs either
# a check or an admission. "ANCHOR MISSING" is a different thing and also a problem - the mutation never applied,
# which reads exactly like a suite that cannot see it, and telling those two apart is why mutate.sh reports it.
#
# NOT IN run-suites.sh, deliberately: six mutations are six compiles and six suite runs, which is minutes rather
# than seconds. This is an audit to run when adding a check, not a tax on every commit.
set -u
HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE" || exit 2

E=src/main/java/aside/games/fruitjump/engine
G=src/main/java/aside/games/fnaf/engine
W=src/main/java/aside/games/fruitjump/engine/WaterSystem.java
T=src/main/java/aside/games/fruitjump

# label | file | anchor | replacement | the suite that must notice
MUTATIONS=(
  "boss: an arrow does no damage|$E/World.java|static final double ARROW_DAMAGE = 3.0;|static final double ARROW_DAMAGE = 0.0;|aside.engine.SelfTest"
  "enemy: a death with no fade|$E/Enemy.java|DEATH_SECONDS = 0.35;|DEATH_SECONDS = 0.0;|aside.engine.SelfTest"
  "run: no one-way planks at all|$E/LevelGen.java|int PLANK_EVERY = 4;|int PLANK_EVERY = 40000;|aside.engine.SelfTest"
  "run: no bosses at all|$E/LevelGen.java|int BOSS_EVERY = 10;|int BOSS_EVERY = 40000;|aside.engine.SelfTest"
  "run: the game ends after ten levels|$E/LevelGen.java|int FINAL_LEVEL = 40;|int FINAL_LEVEL = 10;|aside.engine.SelfTest"
  "fnaf: no grace at the start of a night|$G|GRACE_SECONDS = 8.0;|GRACE_SECONDS = 0.0;|aside.games.fnaf.engine.SelfTest"
  "fnaf: a night is one hour, not six|$G|NIGHT_HOURS = 6;|NIGHT_HOURS = 1;|aside.games.fnaf.engine.SelfTest"
  "fnaf: the power starts empty|$G|POWER_START = 100.0;|POWER_START = 0.0;|aside.games.fnaf.engine.SelfTest"
  # THE ABSENCE SHAPE. A value chosen to mean "as if unimplemented", which is what an equality-to-the-constant
  # check can never catch - the measurement and the constant would move together. Borrowed from the branch's
  # mutations.py, which keeps a whole set of these for exactly this reason.
  "water: nothing is deep enough to swim|$W|SWIM_DEPTH = 48.0;|SWIM_DEPTH = 100000.0;|aside.games.fruitjump.engine.WaterSuite"
  "water: the water has no buoyancy|$W|BUOYANCY = 1.35;|BUOYANCY = 0.0;|aside.games.fruitjump.engine.WaterSuite"
  "water: the water does not slow you|$W|DRAG_X = 2.5;|DRAG_X = 0.0;|aside.games.fruitjump.engine.WaterSuite"
  "tutorial: one level short of the boss|$T/Tutorial.java|int LAST = 11;|int LAST = 10;|aside.engine.SelfTest"
)

if [ "${1:-}" = "--list" ]; then
  for m in "${MUTATIONS[@]}"; do echo "  ${m%%|*}"; done
  exit 0
fi

filter="${1:-}"
caught=0; missed=0; broken=0
for m in "${MUTATIONS[@]}"; do
  label="${m%%|*}"; rest="${m#*|}"
  file="${rest%%|*}"; rest="${rest#*|}"
  anchor="${rest%%|*}"; rest="${rest#*|}"
  repl="${rest%%|*}"; suite="${rest##*|}"
  if [ -n "$filter" ] && [[ "$label" != *"$filter"* ]]; then continue; fi
  printf '  %-40s ' "$label"
  out=$(timeout 600 tools/mutate.sh "$suite" "$file" "$anchor" "$repl" 2>&1)
  if echo "$out" | grep -q "ANCHOR MISSING"; then
    echo "ANCHOR MISSING - the mutation never applied, so this proves nothing"; broken=$((broken + 1))
  elif echo "$out" | grep -q "NOT CAUGHT"; then
    echo "NOT CAUGHT - no check covers this"; missed=$((missed + 1))
  else
    echo "caught"; caught=$((caught + 1))
  fi
done

echo
echo "  caught: $caught   not caught: $missed   never applied: $broken"
if [ "$missed" -eq 0 ] && [ "$broken" -eq 0 ]; then
  echo "  OK every invariant listed here is one a suite would notice breaking"
  exit 0
fi
echo "  A mutation nobody notices is a claim nobody is checking."
exit 1
