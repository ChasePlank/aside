#!/usr/bin/env bash
#
# What has the engine got that the public release has not?
#
#   tools/compare-release.sh [path-to-the-release]
#
# The release (ChasePlank/Fruit-Jump) is a FORK of this game: same class names under `tropical/` instead of
# `aside/games/fruitjump/`, its own copy of every file, and its own gate. The two are meant to be the same game,
# and they have been drifting for weeks - on 2026-10-08 a hand-check found that EVERY feature of the previous week
# was missing from it, and that its gameplay sky was still flat #87CEEB, meaning the SUNSET never reached it
# either. The sunset is the game's identity.
#
# WHY THIS EXISTS. `compare-ports.sh` compares this project's OTHER ports (the story engine's JavaScript). Nothing
# compared the engine against the release, so the gap was found by hand, twice, by grepping for feature names one
# at a time - and the second time it was found the day before a scheduled packaging run. This is that grep, kept.
#
# WHAT IT IS NOT. A gate. The release may legitimately lag - a release is a decision about what to ship - so this
# reports and exits 0. It is a question with a list of answers.
#
# A MARKER MUST BE THE FEATURE'S USE, NOT A NAME THAT CAN APPEAR ANYWHERE. The first version of this list used
# `E8763A` for the sunset and `class Boss` for the boss, and both reported PRESENT in a release that has neither:
# the sky colour appears in a sprite palette over there, and the boss CLASS is a file nothing builds. A marker that
# can be satisfied by an unrelated mention is a check that cannot fail, which is the fault this project has a whole
# section of lessons about. Ask what the working feature DOES that nothing else does - here, `setFill(Color.web(`
# for the sky and `addBoss` for the wiring - and use that.
#
# THE FEATURE LIST IS CURATED, AND THAT IS ITS WEAKNESS. Each entry is a marker string that exists in this
# repository when the feature is present. A feature nobody has added here will not be reported as missing over
# there - the list only knows what it has been told. ADD TO IT WHEN SOMETHING LANDS, which is cheap, and it will
# not go stale the way a hand-check does.
set -u

RELEASE="${1:-../tp}"
if [ ! -d "$RELEASE/src/main/java/tropical" ]; then
  echo "compare-release: no release checkout at $RELEASE (expected $RELEASE/src/main/java/tropical)" >&2
  echo "  this is not a failure - pass the path, or clone ChasePlank/Fruit-Jump beside this repository" >&2
  exit 2
fi

HERE="$(cd "$(dirname "$0")/.." && pwd)"
ENGINE="$HERE/src/main/java/aside/games/fruitjump"
FORK="$RELEASE/src/main/java/tropical"

# feature | marker | what it is
FEATURES=(
  "the sunset sky|setFill(Color.web(\"#E8763A\"|the game's identity: warm sky, low sun, black silhouette"
  "the sun itself|sunR|the disc the sky is built around"
  "parallax ridges|drawRidge|two ridges at 30% and 60% of the camera"
  "ridges hung on the horizon|horizonScreenY|the fix for ridges buried behind the terrain"
  "the sinking sun|LEVELS_TO_DUSK|the sky deepens and the sun sinks across a run"
  "a boss in a level|addBoss|the fight the tutorial teaches"
  "the boss's sprite|boss2x|generated art, 64x64, in the sprite format"
  "bosses in the run|BOSS_EVERY|every tenth generated level"
  "moving platforms in the run|MOVER_EVERY|a ferry over a gap, every third level"
  "one-way planks in the run|PLANK_EVERY|a plank over a gap, every fourth level"
  "an ending|VictoryScreen|the way home, at the end of a run"
  "a run with a length|FINAL_LEVEL|forty levels, and the sun is down at the end"
  "rooms mode's exit|isExit|arriving at the generated exit room ends the run"
  "the coin count on screen|\"Coins \"|the inventory counted them and nothing showed them"
  "the death fade|deathFade|enemies and the boss fade instead of vanishing"
  "music that plays|currentMusicName|the four tracks, wired through the audio player"
)

echo "=== the engine ($ENGINE)"
echo "=== the release ($FORK)"
echo
missing=0
for row in "${FEATURES[@]}"; do
  IFS='|' read -r name marker what <<< "$row"
  here=$(grep -rl "$marker" --include=*.java "$HERE/src/main/java" 2>/dev/null | wc -l)
  there=$(grep -rl "$marker" --include=*.java "$FORK/.." 2>/dev/null | wc -l)
  if [ "$here" -gt 0 ] && [ "$there" -eq 0 ]; then
    printf '  MISSING   %-30s %s\n' "$name" "$what"
    missing=$((missing + 1))
  elif [ "$here" -eq 0 ]; then
    printf '  GONE?     %-30s not in this repository either (%s)\n' "$name" "$marker"
  else
    printf '  present   %-30s %s\n' "$name" "$what"
  fi
done

echo
echo "=== the four music tracks ==="
for t in title level boss victory; do
  if [ -f "$HERE/audio/$t-theme.wav" ] && [ ! -f "$RELEASE/audio/$t-theme.wav" ]; then
    echo "  MISSING   $t-theme.wav"
    missing=$((missing + 1))
  elif [ -f "$HERE/audio/$t-theme.wav" ]; then
    echo "  present   $t-theme.wav"
  fi
done

echo
echo "=== the engine class lists, both ways ==="
# THE DIRECTION NOTHING WAS MEASURING. Every section above asks what this repository has that the release lacks.
# Nothing asked the reverse - and the first time it was asked, by hand, the answer was RoomsProbe: a CHECK the engine
# had and the release did not, which the curated feature markers above could never have named. A class list is not
# curated and the fork copies the class names, so the two are directly comparable.
#
# ENGINE ONLY, AND THE ROOT PACKAGES ARE DELIBERATELY NOT COMPARED. They differ by eighteen files, and every one of
# them is architecture rather than drift: the release is a standalone game with its own Main, Screen and
# ScreenManager, while this repository embeds the same game inside a larger engine whose entry point is
# FruitJumpGame. The two shells are SUPPOSED to differ; the engines are supposed to mirror each other, which is what
# makes this section a check rather than a wish.
here_classes=$(ls "$HERE/src/main/java/aside/games/fruitjump/engine"/*.java 2>/dev/null | xargs -n1 basename | sort)
there_classes=$(ls "$RELEASE/src/main/java/tropical/engine"/*.java 2>/dev/null | xargs -n1 basename | sort)
echo "  this repository: $(echo "$here_classes" | grep -c .)   release: $(echo "$there_classes" | grep -c .)"
only_there=$(comm -13 <(echo "$here_classes") <(echo "$there_classes"))
only_here=$(comm -23 <(echo "$here_classes") <(echo "$there_classes"))
if [ -n "$only_there" ]; then
  echo "  IN THE RELEASE AND NOT HERE:"
  echo "$only_there" | sed 's/^/    /'
else
  echo "  nothing in the release that this repository lacks"
fi
if [ -n "$only_here" ]; then
  echo "  HERE AND NOT IN THE RELEASE:"
  echo "$only_here" | sed 's/^/    /'
else
  echo "  and nothing here that the release lacks"
fi

echo
echo "=== $missing feature(s) in this repository and not in the release ==="
echo "  A release may lag on purpose. What it must not do is lag by accident: this repo ships a packaged"
echo "  release on a schedule, and the packaging step does not port anything - it packages what is there."
exit 0
