#!/usr/bin/env bash
#
# audit-stories.sh - run the story auditor over every story in stories/.
#
#   tools/audit-stories.sh
#
# WHY THIS EXISTS. The gate's suite list says "aside.engine.SelfTest  # the engine, the auditor, the shelf" - and
# SelfTest audits TWO stories: night-shift as its fixture, and ninety-days for the hub check. There are NINE.
# Nothing in run-suites.sh mentioned `stories/` or `.aside` at all, so seven stories - including overtime, the
# largest thing in the library at 131 scenes - were not being audited by anything.
#
# THE COMMENT WAS THE CLAIM AND THE COVERAGE WAS TWO FIXTURES, which is the shape this project keeps finding.
#
# A GENEROUS HEAP, because the default one cannot finish the largest story. MEASURED: overtime reaches 93 of 131
# scenes at 512m, 117 at 2g, and all 131 with all six endings at 3g. The auditor used to report that as a budget
# hit and advise raising the budget, which does nothing; that is fixed, and this passes the heap it needs.
#
# CLEAN and INCONCLUSIVE are different verdicts and only one of them is a failure. A story the auditor could not
# finish is not a story with a fault in it - but it IS worth printing, because "we could not check this" is not
# "this is fine".
set -u
cd "$(dirname "$0")/.." || exit 2

# JAVA, FX AND OUT ARE FOUND, NOT GUESSED. This script defaulted JAVA to a
# path that does not exist here and OUT to `out` while this sandbox builds to
# `classes`, so it exited 2 with "no build in out" -- and the tool self-test,
# which injects a broken jump and requires this script to complain, read that
# as "reported clean with a jump to a missing scene". The tool was fine; the
# guess was not. find-java.sh looks for the toolchain and the build directory,
# and an explicit JAVA/OUT still wins.
. "$(dirname "$0")/find-java.sh"
HEAP="${HEAP:-3g}"
# AND THE BUDGET, WHICH IS A SEPARATE DIAL. MEASURED on overtime: a 3g heap alone still stops at 114 of 131
# scenes, because the default budget is 600,000 states and the traversal needs about 3.1 million. The two limits
# are independent and the largest story needs both raised - which is exactly the confusion that made the auditor
# report a heap failure as a budget failure in the first place.
BUDGET="${BUDGET:-30000000}"

# NIGHT-SHIFT IS A FIXTURE, NOT A STORY. Its unreachable scenes and its authored-after-a-jump beat are deliberate:
# SelfTest asserts the auditor FINDS them, which is how the auditor's own tests work. Auditing it here would report
# a fault that is the point, so it is skipped by name and the skip is printed rather than silent.
SKIP="${SKIP:-night-shift}"

[ -d "$OUT/aside/engine" ] || { echo "  stories: no build in $OUT" >&2; exit 2; }

clean=0; issues=0; inconclusive=0; partial=0; failed_names=()
for f in stories/*.aside; do
  [ -f "$f" ] || continue
  name=$(basename "$f" .aside)
  case " $SKIP " in
    *" $name "*) printf '  %-22s skipped      a deliberate fixture - SelfTest asserts its faults\n' "$name"; continue;;
  esac
  out=$(timeout 900 "$JAVA" -Xmx"$HEAP" -Daside.budget="$BUDGET" -cp "$OUT" aside.engine.Audit "$f" 2>&1)
  scenes=$(echo "$out" | grep -oE "scenes: *[0-9]+/[0-9]+" | grep -oE "[0-9]+/[0-9]+")
  endings=$(echo "$out" | grep -oE "distinct endings: *[0-9]+" | grep -oE "[0-9]+")
  # AND THE TWO NUMBERS THE README ALSO STATES PER STORY - beats and choice options - which nothing checked. The
  # auditor prints them in its header line, so this only has to pass them through. `the-water-line` was documented
  # as 103 beats and has 113: A NUMBER NOBODY RE-RUNS, in the same section that says the counts are audited.
  beats=$(echo "$out" | sed -n 's/^scenes [0-9]* *beats \([0-9]*\).*/\1/p' | head -1)
  options=$(echo "$out" | sed -n 's/^scenes [0-9]* *beats [0-9]* *choice options \([0-9]*\).*/\1/p' | head -1)
  # CLEAN SO FAR IS NOT CLEAN, AND THE AUDITOR IS EXPLICIT ABOUT IT.
  #
  # `grep -q "^CLEAN"` MATCHES BOTH `CLEAN` AND `CLEAN SO FAR (traversal partial: ...)`, so a walk that did not
  # exhaust was reported here as a plain `ok` - and the distinction is the one the README spends a paragraph on:
  # "A clean report is not always a finished one." THE REPORT THAT EXISTS TO PRESERVE THAT DISTINCTION WAS
  # COLLAPSING IT. So the partial case is tested FIRST and named, and the reader can see which stories the search
  # could not finish rather than inferring it from a scene count that happens to be complete.
  #
  # It is still a pass: the auditor exits 0 for a partial walk that found nothing, and so does this.
  if echo "$out" | grep -q "^CLEAN SO FAR"; then
    printf '  %-22s partial      %s scene(s), %s ending(s), %s beats, %s options - the traversal did not exhaust; nothing found in what it saw\n' \
      "$name" "${scenes:-?}" "${endings:-?}" "${beats:-?}" "${options:-?}"
    partial=$((partial + 1))
  elif echo "$out" | grep -q "^CLEAN"; then
    printf '  %-22s ok           %s scene(s), %s ending(s), %s beats, %s options\n' "$name" "${scenes:-?}" "${endings:-?}" "${beats:-?}" "${options:-?}"
    clean=$((clean + 1))
  elif echo "$out" | grep -q "INCONCLUSIVE"; then
    # TWO DIFFERENT THINGS, AND THE DIFFERENCE MATTERS. "the traversal did not exhaust" is not the same as
    # "the story is unchecked": overtime reaches 131 of 131 scenes and all six endings at a 3g heap and a 30M
    # budget, and still reports inconclusive because the STATE SPACE - every combination of scene, position and
    # variables - is larger than any budget worth running. Every scene is looked at; the search just cannot prove
    # it looked at everything.
    total=${scenes##*/}
    reached=${scenes%%/*}
    if [ "$reached" = "$total" ] && [ "$total" != "0" ]; then
      printf '  %-22s all reached  %s scene(s), %s ending(s), %s beats, %s options - traversal did not exhaust, no issues found\n' \
        "$name" "$scenes" "${endings:-?}"
      inconclusive=$((inconclusive + 1))
    else
      printf '  %-22s INCONCLUSIVE %s scene(s) - the auditor could not finish it\n' "$name" "${scenes:-?}"
      inconclusive=$((inconclusive + 1))
    fi
  else
    printf '  %-22s ISSUES       %s scene(s), %s ending(s), %s beats, %s options\n' "$name" "${scenes:-?}" "${endings:-?}" "${beats:-?}" "${options:-?}"
    echo "$out" | grep -E "^    !!|^    \?\?" | head -4 | sed 's/^/      /'
    issues=$((issues + 1)); failed_names+=("$name")
  fi
done

# AND THAT NO STORY CLAIMS TO BE LATER THAN THE LIBRARY IS. Four of the scripts open with an ordinal - Bell Codes
# says SEVENTH, Ninety Days EIGHTH, A440 NINTH, Changeover TENTH - and they are claims like any other. I wrote
# "TENTH" on A440 when there were nine stories, and nothing would have caught it; the count only came right by
# accident when a tenth was written.
#
# This catches OVER-claiming, which is the direction that actually happened. It cannot catch under-claiming - a new
# story that says nothing at all is not wrong, just quiet - and that is fine, because the fault was a number that
# had run ahead of the thing it counted.
total_stories=$(ls stories/*.aside 2>/dev/null | wc -l)

# ONE LIST, BECAUSE TWO LISTS THAT MUST AGREE WILL NOT.
#
# This scan used to hold its vocabulary twice: a regex of ordinal words and a case statement of their numbers. The
# regex knew twelve words, the case knew twelve numbers, and a story claiming to be THIRTEENTH matched neither - so it
# fell through the `*)` arm to n=0, which sorts BELOW every real value and made an over-claim invisible. The fixture
# that should have caught that was injecting the thirteenth ordinal, and the tool could not read it.
#
# Built from one array the two cannot disagree, and there is no fallback to fall through: the regex is made from the
# same words the lookup uses, so a word it matches is a word it can number.
ORDINALS=(ZERO FIRST SECOND THIRD FOURTH FIFTH SIXTH SEVENTH EIGHTH NINTH TENTH ELEVENTH TWELFTH THIRTEENTH FOURTEENTH FIFTEENTH SIXTEENTH SEVENTEENTH EIGHTEENTH NINETEENTH TWENTIETH TWENTY-FIRST TWENTY-SECOND TWENTY-THIRD TWENTY-FOURTH TWENTY-FIFTH TWENTY-SIXTH TWENTY-SEVENTH TWENTY-EIGHTH TWENTY-NINTH THIRTIETH)
ORD_RE=$(IFS='|'; echo "${ORDINALS[*]:1}")
highest=0
for f in stories/*.aside; do
  [ -f "$f" ] || continue
  word=$(grep -oE "\\b($ORD_RE)\\b" "$f" | head -1)
  n=0
  for i in "${!ORDINALS[@]}"; do [ "${ORDINALS[$i]}" = "$word" ] && n=$i; done
  if [ "$n" -gt "$highest" ]; then highest=$n; highest_name=$(basename "$f" .aside); fi
done
echo "  highest ordinal claimed: $highest ($highest_name)   stories: $total_stories"
if [ "$highest" -le "$total_stories" ]; then
  echo "  ok"
else
  echo "  MISMATCH - $highest_name claims to be story $highest and there are only $total_stories" >&2
  issues=$((issues + 1)); failed_names+=("ordinal")
fi

echo
if [ "$issues" -eq 0 ]; then
  echo "=== $clean story(ies) clean, $partial partial, $inconclusive inconclusive, 0 with issues ==="
  [ "$partial" -eq 0 ] || echo "    (partial: nothing found, but the traversal did not exhaust - so the search cannot prove a scene is unreachable)"
  [ "$inconclusive" -eq 0 ] || echo "    (an inconclusive run is not a fault, but it is not a pass either)"
  exit 0
fi
echo "=== $issues story(ies) with issues: ${failed_names[*]} ===" >&2
exit 1
