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
P=src/main/java/aside/games/fruitjump/engine/Projectile.java
R=src/main/java/aside/games/fruitjump/engine/Piranha.java
C=src/main/java/aside/games/fruitjump/engine/Combat.java
A=src/main/java/aside/engine
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
  # THE AUDITOR'S OWN HEURISTIC. Nothing was mutating the tool that judges the stories, and setting its floor to
  # zero makes EVERY pair of options count as near-identical - not caught by 644 checks until this one existed. A
  # heuristic that over-fires is how the-last-crossing's legitimate pair got reported as a fault.
  "audit: the wording heuristic over-fires|$A/Bot.java|a.length() >= 8 && b.length() >= 8|a.length() >= 0 \&\& b.length() >= 0|aside.engine.SelfTest"
  # Found by tools/tautologies.py, not by reading: the stall guard could be removed entirely and a check that
  # asserted only "a stalled pull releases" still passed, because the 1.5s cap ends it anyway.
  "hookshot: the stall guard switched off|$W|STALL_TIME = 0.3;|STALL_TIME = 0.0;|aside.engine.SelfTest"
  # Both found by tools/tautologies.py as "NOTHING NOTICES IT BEING SWITCHED OFF": an arrow that flies dead
  # straight, and a blast that hurts nobody. Neither behaviour had a check at all.
  "weapons: arrows fly straight|$P|ARROW_GRAVITY = 250;|ARROW_GRAVITY = 0.0;|aside.engine.SelfTest"
  "weapons: the blast hurts nobody|$P|BLAST_DAMAGE_RANGE = 100;|BLAST_DAMAGE_RANGE = 0.0;|aside.engine.SelfTest"
  # The piranha's three documented claims. HIT_COOLDOWN is deliberately NOT here: at its shipped 0.6 it sits below
  # REAGGRO_DELAY (2.5), so it cannot bind at that value and zero is correctly invisible. See PiranhaTest's header.
  "piranha: it never drifts when idle|\$R|IDLE_SPEED = 28;|IDLE_SPEED = 0.0;|aside.games.fruitjump.engine.PiranhaTest"
  "piranha: no punish window after a miss|\$R|RECOVER_TIME = 1.1;|RECOVER_TIME = 0.0;|aside.games.fruitjump.engine.PiranhaTest"
  "piranha: it re-engages instantly|\$R|REAGGRO_DELAY = 2.5;|REAGGRO_DELAY = 0.0;|aside.games.fruitjump.engine.PiranhaTest"
  # The knockback, which nothing looked at: damageIsMetered covered how OFTEN a hit lands and not what it does.
  # The direction is the one that matters - the wrong way round shoves the player INTO the spikes they were hurt by.
  "damage: a hit does not move you|$C|KNOCKBACK_X = 250;|KNOCKBACK_X = 0.0;|aside.engine.SelfTest"
  "damage: the knockback pushes you IN|$C|player.vx = dir * KNOCKBACK_X;|player.vx = -dir * KNOCKBACK_X;|aside.engine.SelfTest"
  "damage: no upward pop|$C|KNOCKBACK_Y = -300;|KNOCKBACK_Y = 0.0;|aside.engine.SelfTest"
  # Slopes, which had no checks at all until Oct 10. The snap-down one is the seam case: walking off a flat ledge
  # onto a ramp that starts slightly lower. On a ramp walked at speed the PENETRATION branch holds you and this
  # constant changes the result by three tenths of a pixel.
  "slopes: no snap onto a ramp from a ledge|$E/World.java|SLOPE_SNAP_DOWN = 14.0;|SLOPE_SNAP_DOWN = 0.0;|aside.engine.SelfTest"
  "slopes: a steep slope does not slide you|$E/World.java|SLOPE_SLIDE_ACC = 900.0;|SLOPE_SLIDE_ACC = 0.0;|aside.engine.SelfTest"
  # The stomp, found only after the sweep learned to read negative numbers.
  "stomp: no bounce off an enemy|$C|STOMP_BOUNCE = -400;|STOMP_BOUNCE = 0.0;|aside.engine.SelfTest"
  "stomp: the positional rule flipped|$C|return (player.y + player.hh) < enemy.y;|return (player.y + player.hh) > enemy.y;|aside.engine.SelfTest"
  # The blast's damage to a boss, claimed in a comment as "worth three arrows" and asserted nowhere. Measured
  # through handleExplosion rather than Boss.hit, so it tests the damage the game deals and not the weak-point window.
  "blast: a bomb does not hurt the boss|\$E/World.java|BLAST_DAMAGE = 9.0;|BLAST_DAMAGE = 0.0;|aside.games.fruitjump.engine.BlastTest"
  "blast: a bomb is worth thirteen arrows|\$E/World.java|BLAST_DAMAGE = 9.0;|BLAST_DAMAGE = 40.0;|aside.games.fruitjump.engine.BlastTest"
  # THE BOT'S MODEL OF THE PLAYER. GameplayScreen's comment claimed its tuned constants "match the validator's
  # verified values" and nothing checked it: a drift is silent, and the gate goes on certifying levels with
  # velocities the game no longer has. Paths here are the game layer, not the engine.
  "model: the player's jump drifts|src/main/java/aside/games/fruitjump/GameplayScreen.java|JUMP_V = -420;|JUMP_V = -380;|aside.games.fruitjump.engine.PlayerModelTest"
  "model: the player's run speed drifts|src/main/java/aside/games/fruitjump/GameplayScreen.java|RUN_SPEED = 200;|RUN_SPEED = 240;|aside.games.fruitjump.engine.PlayerModelTest"
  # The 'A' water cell - an upwelling - which the generator never places and nothing was checking. Only the zero is
  # listed: a stronger current still lifts you higher, so the magnitude is the designer's, which is the healthy state.
  "water: the up-current does nothing|$E/LevelMap.java|CURRENT_UP_SPEED = 70.0;|CURRENT_UP_SPEED = 0.0;|aside.engine.SelfTest"
  # The bat's stun, which had an upper bound only: 'the bats do not pin you' is satisfied by bats that never stun
  # anybody. The same one-directional-bound shape as the hookshot's stall guard.
  "bats: the stun does nothing|$E/Bat.java|STUN_SECONDS = 1.0;|STUN_SECONDS = 0.0;|aside.engine.SelfTest"
  # The spiral-of-death clamp, which could not be exercised before GameLoop.advance existed: the constant was used
  # only by run(), where the frame time is always DT, and the live clamp was a hard-coded 0.25 in GameplayScreen.
  "loop: the frame clamp stops the world|$E/GameLoop.java|MAX_FRAME = 0.25;|MAX_FRAME = 0.0;|aside.engine.SelfTest"
  "loop: the frame clamp protects nothing|$E/GameLoop.java|MAX_FRAME = 0.25;|MAX_FRAME = 5.0;|aside.engine.SelfTest"
  # The bat's pursuit. The existing bat check starts its bats INSIDE swoop range, so they close by diving and a bat
  # that cannot pursue still gets there; between SWOOP_RANGE and AGGRO_RANGE pursuit is the only thing moving it.
  "bats: the pursuit does nothing|$E/Bat.java|PURSUE_SPEED = 130;|PURSUE_SPEED = 0.0;|aside.engine.SelfTest"
  "bats: it outruns the player it is meant to lure|$E/Bat.java|PURSUE_SPEED = 130;|PURSUE_SPEED = 240.0;|aside.engine.SelfTest"
  # The bat's dive - the one mechanic no check observed until Oct 10, and the reason SWOOP_RECOVER = 20 was
  # invisible: the bat stuns on CONTACT, so bats that cannot dive at all still reach the player by pursuing.
  "bats: the dive is not faster than pursuit|$E/Bat.java|SWOOP_SPEED = 330;|SWOOP_SPEED = 0.0;|aside.engine.SelfTest"
  "bats: the dive never ends|$E/Bat.java|SWOOP_TIME = 0.5;|SWOOP_TIME = 0.0;|aside.engine.SelfTest"
  "bats: it cannot dive again for twenty seconds|$E/Bat.java|SWOOP_RECOVER = 0.85;|SWOOP_RECOVER = 20.0;|aside.engine.SelfTest"
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
