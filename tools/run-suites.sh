#!/usr/bin/env bash
#
# Run every game suite in the repository and report a total.
#
#   tools/run-suites.sh              # uses ./out
#   OUT=classes tools/run-suites.sh
#
# WHY THIS EXISTS. Nineteen games in this repository have a SelfTest and NOTHING RAN THEM. Not a script, not a
# doc, not a habit - `grep -rl SelfTest tools/` found only mutate.sh, which names one suite at a time as an
# argument. They were all green the first time anyone ran the whole set, which is the lucky version of this: a
# gate nobody runs is not a gate, it is a file.
#
# It also fixes a comparison problem. The suites report in FOUR different shapes:
#
#   791/791 checks passed          === 584 checks, 0 failed
#   === 626 passed, 0 failed ===   all 112 checks passed
#
# so no two of them can be compared without a person reading both, and no total can be printed at all. This
# script reads all four and prints one line per suite plus a total, and it treats a suite as failed if the exit
# code is non-zero OR the text says anything failed - because the two do not always agree, and a report that
# prints FAIL and exits clean is a defect this repository has now found three times.
#
# The suite list is DISCOVERED, not written down: a new game is included the day it is added, and a list in this
# file would be stale the first time someone forgot it.
set -u

cd "$(dirname "$0")/.." || exit 2
OUT="${OUT:-out}"
# The toolchain, from the one place that looks for it. This file used to carry its own default and then tell the
# user to set JAVA when it was wrong - which it was, on the machine this runs on.
. "$(dirname "$0")/find-java.sh"

if [ ! -d "$OUT" ]; then
  echo "no build in $OUT - compile first, e.g." >&2
  echo "  javac --module-path \$FX --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing -d out \$(find src/main/java -name '*.java')" >&2
  exit 2
fi

# THE GATES. Curated, because the name does not decide: `DoorStressTest` reports a distribution and never
# fails, and `SpriteProbe` is an asset tool that says "not a folder: art/raw" and exits 0. A pattern cannot tell
# those from `WaterProbe`, which exits non-zero when a pool is the wrong shape.
#
# The list is safe to curate because the runner WATCHES FOR CLASSES IT IS NOT RUNNING, below: every class with a
# main in this repository is either run here or named as a tool, and anything else is printed. A list that can go
# stale silently would be a problem; a list that reports what it is missing is just a list.
SUITES=(
  aside.engine.SelfTest                          # the engine, the auditor, the shelf
  aside.audio.AudioTest                          # every cue has a file, and plays
  aside.games.fruitjump.engine.WaterProbe        # every pool is the shape it was built to be
  aside.games.fruitjump.engine.WaterSuite
  aside.games.fruitjump.engine.WaterEnemyTest
  aside.games.fruitjump.engine.CrackedPocketTest
  aside.games.fruitjump.engine.DoorStressTest    # a report, not a gate - it never fails, and is listed here
)                                                # so that its silence is deliberate rather than an oversight

# Everything else, discovered: every SelfTest.java under games/ and its package.
while IFS= read -r f; do
  pkg=$(sed -n 's/^package \(.*\);/\1/p' "$f" | head -1)
  [ -n "$pkg" ] && SUITES+=("$pkg.SelfTest")
done < <(find src/main/java/aside/games -name 'SelfTest.java' | sort)

if [ ${#SUITES[@]} -eq 0 ]; then
  echo "found no suites under src/main/java/aside/games" >&2
  exit 2
fi

# A live display, because the FNAF suites and AudioTest open one. NOTE that AudioTest also wants a SOUND DEVICE
# to fully verify that every cue plays; on a machine without one it reports the cue list, prints a media
# exception from its own thread after the summary, and exits 0. Its cue-presence half is the part that works
# everywhere, and that half is a gate - it exits non-zero when a file is missing.
DISPLAY_NUM="${DISPLAY_NUM:-:99}"
if ! xdpyinfo -display "$DISPLAY_NUM" >/dev/null 2>&1; then
  Xvfb "$DISPLAY_NUM" -screen 0 "${SCREEN:-1400x900x24}" >/tmp/xvfb.log 2>&1 &
  for _ in $(seq 1 40); do xdpyinfo -display "$DISPLAY_NUM" >/dev/null 2>&1 && break; sleep 0.25; done
fi
export DISPLAY="$DISPLAY_NUM"

pass=0; fail=0; total_checks=0
failed_names=()
for suite in "${SUITES[@]}"; do
  name=$(echo "$suite" | sed 's/^aside\.games\.//; s/\.SelfTest$//')
  out=$("$JAVA" --module-path "$FX" \
        --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
        -cp "$OUT:src/main/resources" "$suite" 2>&1)
  rc=$?
  # The SUMMARY line, and it took three passes to get right - each one a version of the defect this runner
  # exists to catch, a check reporting something that is not true:
  #
  #   1. "the last line with a digit in it" picked up `Exception in thread "Thread-24" ... Could not create
  #      player!` for AudioTest, because a thread name has digits. A media failure reported as a suite's result.
  #   2. Filtering to lines matching passed|failed|CHECK|checks fixed that and broke the total, because
  #      AudioTest's real summary is "=== all cues present and playable ===" - no match - so the fallback took
  #      the last line again. The total then swung between ~9,600 and ~22,250 on identical work, depending on
  #      whether an asynchronous media exception happened to land after the summary.
  #   3. What is here: drop exception and stack lines FIRST, then prefer a summary-shaped line, then fall back
  #      to the last line standing. AudioTest's exception is on its own thread and arrives when it likes; a
  #      number that changes between identical runs is worse than no number.
  clean=$(echo "$out" | grep -viE '^WARNING|^[[:space:]]+at |Exception in thread|MediaException|^Caused by')
  last=$(echo "$clean" | grep -E 'passed|failed|CHECK|checks|present|OK|PASS' | tail -1)
  [ -z "$last" ] && last=$(echo "$clean" | tail -1)
  # The FIRST number anywhere in the line, not only at the start. Anchored to the start it missed
  # "=== 308 passed, 0 failed ===" and "all 112 checks passed", so the total quietly excluded the engine gate
  # and the audio test - a total that is wrong in the direction of looking fine.
  n=$(echo "$last" | grep -oE '[0-9]+' | head -1)
  [ -n "$n" ] && total_checks=$((total_checks + n))
  # Failed if the exit code says so, or the text does. The two do not always agree.
  #
  # NOT a bare search for "FAIL", and not a search for "check" either. The first version searched for FAIL and
  # reported twelve suites as failed while every one of them said "0 failed" - because "failed" contains "fail".
  # The second counted any "N checks" as a failure. It is the same defect the script exists to catch, one level
  # up: a check that reports a problem that is not there, which is why the pattern below was TESTED against ten
  # real summary lines - six passing, four failing - before it was run.
  if [ $rc -ne 0 ] || echo "$last" | grep -qE '(^|[^0-9])[1-9][0-9]* failed|CHECK\(S\) FAILED|(^| )FAIL( |$)'; then
    printf '  %-12s FAIL   %s\n' "$name" "$last"
    fail=$((fail + 1)); failed_names+=("$name")
  else
    printf '  %-12s ok     %s\n' "$name" "$last"
    pass=$((pass + 1))
  fi
done

# THE CONTACT SHEETS. Two committed images of generated frames - a claim about the current code, and one that
# goes stale quietly. Verified reproducible before being gated: two independent runs of all 24 desktop frames and
# all 23 phone frames are byte-identical, because everything in them advances on tick count.
if [ -x tools/check-sheets.sh ]; then
  sheets_out=$(OUT="$OUT" JAVA="$JAVA" FX="$FX" tools/check-sheets.sh 2>&1); sheets_rc=$?
  echo "  $(echo "$sheets_out" | grep -E '^=== ' | tail -1)"
  if [ $sheets_rc -ne 0 ]; then
    echo "$sheets_out" | grep -E 'STALE|COULD NOT' | sed 's/^/    /'
    fail=$((fail + 1)); failed_names+=("contact-sheets")
  fi
fi

# THE FNAF MATCH. This repository and ChasePlank/fnaf are meant to be the same game, and the README says they
# are aligned "by their self-tests reporting the same numbers rather than by diffing text" - which is the right
# criterion for behaviour and not something anyone can run in a second. This is the cheap exact first pass. It
# exits 2 when the standalone is not checked out beside this one, which is not a failure and is not counted as
# one: a comparison that did not happen is not a comparison that succeeded.
if [ -x tools/check-fnaf-match.sh ]; then
  match_out=$(tools/check-fnaf-match.sh 2>&1); match_rc=$?
  if [ $match_rc -eq 0 ]; then
    echo "  $(echo "$match_out" | grep -E '=== [0-9]+ engine' | tail -1)"
  elif [ $match_rc -eq 2 ]; then
    echo "  fnaf match NOT CHECKED - no standalone checkout beside this one"
  else
    echo "  $(echo "$match_out" | grep -E '=== [0-9]+ engine' | tail -1)"
    echo "$match_out" | grep -E 'DIFFERS|ONLY HERE' | sed 's/^/    /'
    fail=$((fail + 1)); failed_names+=("fnaf-match")
  fi
fi

# THE SCREENS. Every game has a suite and none of them opens a screen - a game can pass every check it has and
# still show a blank window on start. This opens all of them and measures what they drew. Needs a display, which
# this script already guarantees.
if [ -d "$OUT/aside/tools" ]; then
  games_out=$("$JAVA" --module-path "$FX" \
        --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
        -cp "$OUT:src/main/resources" aside.tools.CheckGames 2>&1)
  games_rc=$?
  games_last=$(echo "$games_out" | grep -E '=== [0-9]+ game' | tail -1)
  if [ $games_rc -ne 0 ]; then
    echo "  ${games_last:-games: check failed}"
    echo "$games_out" | grep -E 'FAIL' | sed 's/^/    /'
    fail=$((fail + 1)); failed_names+=("game-screens")
  else
    echo "  ${games_last:-games ok}"
  fi
else
  echo "  game screens NOT CHECKED - no build in $OUT"
fi

# THE WEB BUILDS. Twenty-one phone builds in web/, which the Java suites cannot see and which nothing opened
# until 2026-10-03 - a build can be perfectly current and still render nothing. Separate tool, separate
# dependency (puppeteer), so it is called here rather than folded in, and it is skipped with a warning if its
# dependency is not installed rather than counted as a pass.
web_note=""
if command -v node >/dev/null 2>&1 && [ -d node_modules/puppeteer ]; then
  web_out=$(node tools/check-web.mjs 2>&1)
  web_rc=$?
  web_last=$(echo "$web_out" | grep -E '=== [0-9]+ web' | tail -1)
  if [ $web_rc -ne 0 ]; then
    echo "  ${web_last:-web builds: check failed}"
    echo "$web_out" | grep -E 'FAIL|did not boot' | sed 's/^/    /'
    fail=$((fail + 1)); failed_names+=("web-builds")
  else
    echo "  ${web_last:-web builds ok}"
  fi
else
  web_note="  web builds NOT CHECKED - run 'npm install' for tools/check-web.mjs"
fi

# THE PORT TRACES. Each of these drives the phone build and the desktop engine under the same scripted policy
# and compares the whole state, second by second - the only thing that can catch a ported model being wrong on
# both sides at once. They were written one at a time in September and October, run once by hand, and then NOT
# WIRED INTO ANYTHING: the audio splice landed inside the block they parse, every one of them broke, and nothing
# said so for two days. A tool that verified a port once is a tool that verified a port once.
if command -v node >/dev/null 2>&1; then
  for t in tell-trace drift-trace lesson-trace drift-play lesson-play; do
    [ -f "tools/$t.mjs" ] || continue
    t_out=$(node "tools/$t.mjs" 2>&1)
    t_rc=$?
    t_last=$(echo "$t_out" | tail -1)
    if [ $t_rc -ne 0 ]; then
      echo "  $t FAILED"
      echo "$t_out" | head -3 | sed 's/^/    /'
      fail=$((fail + 1)); failed_names+=("$t")
    else
      echo "  $t ok     $t_last"
    fi
  done
fi

# WHAT THIS IS NOT RUNNING. Every class with a main is either in the list above or matched by the pattern
# below, and anything else is printed - so a checker added tomorrow is visible rather than silently absent. This
# is the half that makes curating the list safe.
unclassified=()
while IFS= read -r f; do
  case "$f" in */tools/*) continue;; esac
  base=$(basename "$f" .java)
  case "$base" in Web*|*Trace|Synth|Audit|WebExport|FnafForensics|PhoneShelf|Launcher|Main|Sound|Lineup|SpriteProcess|SpriteProbe) continue;; esac
  pkg=$(sed -n 's/^package \(.*\);/\1/p' "$f" | head -1)
  fqn="$pkg.$base"
  found=0
  for s in "${SUITES[@]}"; do [ "$s" = "$fqn" ] && found=1 && break; done
  [ $found -eq 0 ] && unclassified+=("$fqn")
done < <(grep -rl "public static void main" src/main/java --include='*.java' | sort)

echo
if [ ${#unclassified[@]} -gt 0 ]; then
  echo "NOT RUN HERE (${#unclassified[@]} class(es) with a main that are neither a gate nor a known tool):"
  for u in "${unclassified[@]}"; do echo "  $u"; done
fi
[ -n "$web_note" ] && echo "$web_note"
echo "=== $pass suite(s) passed, $fail failed, ~$total_checks checks ==="
if [ $fail -gt 0 ]; then
  echo "failed: ${failed_names[*]}"
  exit 1
fi
