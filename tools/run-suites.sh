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
FX="${FX:-/root/javafx-sdk-27/lib}"
JAVA="${JAVA:-java}"

if [ ! -d "$OUT" ]; then
  echo "no build in $OUT - compile first, e.g." >&2
  echo "  javac --module-path \$FX --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing -d out \$(find src/main/java -name '*.java')" >&2
  exit 2
fi
if ! command -v "$JAVA" >/dev/null 2>&1; then
  echo "no java on PATH - set JAVA, e.g. JAVA=/root/jdk-27+35/bin/java" >&2
  exit 2
fi

# Named, because they are not under games/ and discovery cannot find them. Both are gates in their own right:
# the engine suite covers the engine, the auditor and the shelf, and AudioTest exits non-zero when a cue is
# missing. Leaving them out would have made this runner a runner of nineteen of the twenty-one things that
# check this repository - which is the same shape of gap as not having it.
SUITES=("aside.engine.SelfTest" "aside.audio.AudioTest")

# Discover: every file called SelfTest.java under a game, and its package.
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
  # The SUMMARY line, not the last line with a digit in it. Matching any line with a number picked up
  # "Exception in thread \"Thread-24\" ... Could not create player!" as AudioTest's result, because a thread name
  # has digits in it - so the runner reported a media failure as that suite's outcome.
  last=$(echo "$out" | grep -viE '^WARNING|^[[:space:]]+at ' \
        | grep -E 'passed|failed|CHECK|checks' | tail -1)
  [ -z "$last" ] && last=$(echo "$out" | grep -viE '^WARNING|^[[:space:]]+at ' | tail -1)
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

echo
echo "=== $pass suite(s) passed, $fail failed, ~$total_checks checks ==="
if [ $fail -gt 0 ]; then
  echo "failed: ${failed_names[*]}"
  exit 1
fi
