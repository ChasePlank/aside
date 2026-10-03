#!/bin/bash
#
# Ask a suite what it does not cover.
#
#   tools/mutate.sh <suite-class> <file> <anchor> <replacement> [more...]
#
# Breaks one constant at a time, runs a suite after each, and restores the file.
# A mutation the suite does not notice is a mutation no check covers.
#
# WHY THIS EXISTS, AND WHY IT CHECKS ITS OWN MUTATION. The first version of this
# test was written by hand and its sed anchors did not match the file it was
# editing, so two mutations silently did nothing -- and a mutation that never
# applied reads EXACTLY like a mutation the suite cannot see. It reported that
# two of FNAF 9's engine constants were inert. They are not: every one of them
# is caught. A mutation test that does not verify its own mutation reports that
# the suite is blind whenever the script is wrong, which is the same failure as
# a check that cannot fail, one level up.
#
# So every mutation here is verified to have applied before the suite is run,
# and a mutation that does not apply is reported as ANCHOR MISSING rather than
# counted as a survivor.
#
# WHAT TO MUTATE, AND WHAT NOT TO. Mutate the GAME's constants, not the bot's.
# Each FNAF game keeps its night in engine/Game.java (or Shift.java, or
# Meeting.java) and its policies in engine/Bot.java, and a bot constant is part
# of the measuring instrument rather than the thing being measured -- changing
# SEEN_HOLD or GREED changes what the policies do, not what the night is, so a
# suite that notices is noticing something else. The tool cannot tell the two
# apart, so it reports ANCHOR MISSING when a constant is not in the file you
# named, which is the signal to check which side of the instrument it is on.
#
# RESULTS SO FAR, 2026-10-02. Every FNAF suite has now been mutation-tested and
# every engine constant tried is caught:
#
#   FNAF 2   PUPPET_GRACE, FOXY_HALL_WINDOW, OPENING_GRACE_MAX,
#            MUSIC_BOX_DRAIN, MUSIC_BOX_WIND                  5 of 5 caught
#   FNAF 3   REBOOT_TIME, LURE_DURATION, LURE_COOLDOWN,
#            VENT_DRAIN, MOVE_MAX                             5 of 5 caught
#   FNAF 4   HOP_TIME, FLASH_TIME, LIT_TIME, BREATH_EVERY,
#            NOISE_PER_FLASH                                  5 of 5 caught
#   FNAF 5   ten of thirteen (see its SelfTest comment)        10 of 10 caught
#   FNAF 7   LIGHT_MAX, LIGHT_COOL, LIGHT_WARM, LIGHT_RESET    4 of 4 caught
#   FNAF 8   SWIVEL, DIM_RUSH, BRIGHT_RUSH, JITTER             4 of 4 caught
#   FNAF 9   six of seven (see its SelfTest comment)            6 of 6 caught
#
# The engine suite itself has no double constants to break; its subject is the
# script format and the shelf, and mutating those is a different job.
#
# THE VERB GAMES HAVE NO CONSTANTS AT ALL -- zero across all ten -- so there is
# nothing to break that way. What they have is TABLES, and that is what to
# mutate: the seeded room, the seeded log, the order list. Tested 2026-10-02:
#
#   residue    the seeded room's chair decay 2 -> 9    caught (6 checks fail)
#   drift      a line's number 1 -> 3                  caught (3 of 584)
#   bearings   a clock's name "A" -> "Z"               caught (2 of 791)
#   ledger     a night's name "Night one" -> "Night 1" caught (1 of 820)
#   handoff    an order's wording changed              caught (1 of 1180)
#
# residue is the one that did NOT report cleanly the first time: four checks
# read r.traceOf(thing).age directly, so losing the thing made the suite die
# with a NullPointerException instead of failing a check -- and a suite that
# dies takes every check after it with it. Two null-safe helpers fixed it, and
# the same mutation now fails six checks and finishes. Worth trying on the
# other five (testimony, outside, redaction, lesson, tell), whose tables are
# built in loops rather than written out as literals.
#
# EXAMPLES
#
#   tools/mutate.sh aside.games.fnaf5.engine.SelfTest \
#       src/main/java/aside/games/fnaf5/engine/Game.java \
#       "BALLORA_GRACE = 1.40" "BALLORA_GRACE = 3.00" \
#       "FEED_PACE = 2.0"      "FEED_PACE = 1.0"
#
# Run from the repository root, after building into classes/.
#
set -u
cd "$(dirname "$0")/.." || exit 2

if [ $# -lt 4 ]; then
  sed -n '2,10p' "$0"
  exit 2
fi

SUITE="$1"; shift
FILE="$1"; shift
if [ $(( $# % 2 )) -ne 0 ]; then
  echo "pairs of <anchor> <replacement> expected after the file" >&2
  exit 2
fi
if [ ! -f "$FILE" ]; then
  echo "no such file: $FILE" >&2
  exit 2
fi

FX=${FX:-/usr/share/openjfx/lib}

# A MISSING COMPILER IS NOT A FAILED MUTATION. The first version ran `javac ... 2>/dev/null` and reported
# COMPILE FAIL when it returned non-zero - so on a machine where javac is simply not on PATH, every mutation
# reads as "this change broke the build", which is the opposite of what it means and sends you looking at the
# mutation. It is the same trap as `grep -c error` scoring zero when the compiler is absent. Checked for, and
# said plainly, before anything is mutated.
if ! command -v javac >/dev/null 2>&1; then
  echo "no javac on PATH - set PATH or JAVA_HOME first (JDK 17+ with JavaFX)" >&2
  echo "  e.g. PATH=/root/jdk-27+35/bin:\$PATH FX=/root/javafx-sdk-27/lib tools/mutate.sh ..." >&2
  exit 2
fi
CP=$(ls "$FX"/*.jar 2>/dev/null | tr '\n' ':')
BAK=$(mktemp)
cp "$FILE" "$BAK"
trap 'cp "$BAK" "$FILE"; rm -f "$BAK"' EXIT

fail=0
while [ $# -gt 0 ]; do
  FROM="$1"; TO="$2"; shift 2
  cp "$BAK" "$FILE"
  if ! grep -qF "$FROM" "$FILE"; then
    printf '%-44s ANCHOR MISSING\n' "$FROM"
    fail=1
    continue
  fi
  # Python rather than sed, because sed needs a delimiter and the anchors here
  # are regexes -- the stage pattern contains "|", which is the delimiter the
  # first version used, so that mutation could not be expressed at all.
  FROM="$FROM" TO="$TO" FILE="$FILE" python3 -c '
import os, sys
p = os.environ["FILE"]
s = open(p).read()
f, t = os.environ["FROM"], os.environ["TO"]
if f not in s: sys.exit(3)
open(p, "w").write(s.replace(f, t, 1))
'
  if ! grep -qF "$TO" "$FILE"; then
    printf '%-44s MUTATION DID NOT APPLY\n' "$FROM"
    fail=1
    continue
  fi
  if ! javac -nowarn -cp "$CP" -d classes $(find src/main/java -name '*.java') 2>/tmp/mutate-javac.log; then
    printf '%-44s COMPILE FAIL   %s\n' "$FROM" "$(head -1 /tmp/mutate-javac.log)"
    fail=1
    continue
  fi
  OUT=$(java -cp "classes:$CP" "$SUITE" 2>&1 | tail -1)
  # Two verdict formats in this repository: the engine and FNAF suites end with
  # "N passed, M failed", and the verb suites end with "N/M checks passed". The
  # first version of this test only knew the first, so every verb suite read as
  # "caught" whether or not anything failed -- a mutation that changed nothing
  # looked like a mutation the suite noticed.
  # A failure is tested for FIRST: the verb suites print "409/410 checks passed
  # -- 1 FAILED", which contains both phrases, so a case that looks for
  # "checks passed" before "FAILED" reports a caught mutation as not caught.
  case "$OUT" in
    *"FAILED"*|*"FAIL "*)         printf '%-44s caught       %s\n' "$FROM" "$OUT" ;;
    *"0 failed"*)                 printf '%-44s NOT CAUGHT   %s\n' "$FROM" "$OUT" ;;
    *"checks passed"*)            printf '%-44s NOT CAUGHT   %s\n' "$FROM" "$OUT" ;;
    *)                            printf '%-44s UNKNOWN      %s\n' "$FROM" "$OUT" ;;
  esac
done

cp "$BAK" "$FILE"
javac -nowarn -cp "$CP" -d classes $(find src/main/java -name '*.java') 2>/dev/null
exit $fail
