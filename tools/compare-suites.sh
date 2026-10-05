#!/usr/bin/env bash
#
# compare-suites.sh - which checks exist in one game's suite and not in its siblings'?
#
#   tools/compare-suites.sh [games-dir]
#
# WHY. Sweeping the FNAF modules on 2026-10-05 found that fnaf7 and fnaf8 both protect NIGHTS = 5 and fnaf2 did
# not - the same structure, the same nightRow/nightAt pair, the same comment, and the check had simply never been
# copied across. That is minutes to fix and it is invisible to a sweep that looks at one module at a time, because
# a NOT CAUGHT in a module with no siblings is a question rather than a gap.
#
# THIS IS THE CHEAP VERSION OF THAT QUESTION. It does not run anything - it reads the check NAMES out of every
# suite and reports the ones that appear in exactly one. A check unique to one sibling is either something only
# that game does, or something the others should have copied.
#
# IT IS TEXTUAL AND THEREFORE APPROXIMATE. Check names are Java string literals, often built by concatenation, so
# only the literal prefix is compared. That is enough to see "the same check under the same name" and not enough
# to prove two checks differ in what they ASSERT - for that, read them.
#
# AND THE FIRST VERSION REPORTED THE WRONG THING. It listed checks unique to ONE suite - which sounds like the
# question, and is not. The gap that started this was fnaf2 missing `check("five nights", NIGHTS == 5)`, which
# fnaf7 AND fnaf8 both have - so the check is not unique to anything, and the tool hid it. WHAT MATTERS IS
# PARTIAL COVERAGE: a check some siblings have and others do not. It reports the count and the modules now.
set -u
cd "${1:-$(dirname "$0")/..}" || exit 2

SUITES=$(find src/main/java -name "SelfTest.java" | sort)
[ -n "$SUITES" ] || { echo "compare-suites: found no SelfTest.java" >&2; exit 2; }

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
for s in $SUITES; do
  mod=$(echo "$s" | sed 's|src/main/java/aside/||; s|^games/||; s|/engine/SelfTest.java||; s|/SelfTest.java||')
  # the literal prefix of every check() call: check("some words ..." or check("some words " + x
  grep -oE 'check\("[^"]{12,90}' "$s" 2>/dev/null | sed 's/check("//' | sed 's/[[:space:]]*$//' \
    | sort -u | sed "s|$|\t$mod|" >> "$TMP/all.tsv"
done

[ -s "$TMP/all.tsv" ] || { echo "compare-suites: no check names found"; exit 0; }

GROUP="${2:-fnaf}"
TOTAL=$(for s in $SUITES; do echo "$s" | sed 's|src/main/java/aside/||; s|^games/||; s|/engine/SelfTest.java||; s|/SelfTest.java||'; done | grep -cE "^$GROUP" || true)

echo "checks held by SOME $GROUP suites and not others ($TOTAL suites in the group):"
echo "--------------------------------------------------------------------"
cut -f1 "$TMP/all.tsv" | sort | uniq -c | awk '{print $1"\t"substr($0, index($0,$2))}' | while IFS=$'\t' read -r count name; do
  # how many of THIS GROUP have it
  n=$(awk -F'\t' -v n="$name" -v g="^$GROUP" '$1==n && $2 ~ g {c++} END {print c+0}' "$TMP/all.tsv")
  [ "$n" -eq 0 ] && continue
  [ "$n" -eq "$TOTAL" ] && continue      # every sibling has it: fine
  mods=$(awk -F'\t' -v n="$name" -v g="^$GROUP" '$1==n && $2 ~ g {printf "%s ", $2}' "$TMP/all.tsv")
  printf '%-4s %-58s %s\n' "$n/$TOTAL" "${name:0:58}" "$mods"
done | sort -t/ -k1 -n

echo
echo "A PARTIAL count is the useful signal: some siblings hold this check and others do not. It is still a"
echo "QUESTION - the check may genuinely only apply to some games - so read both before copying anything."
echo "The trap to avoid is 'same numbers, different questions': two suites can agree on output while"
echo "verifying different things, so compare what the checks ASSERT, not just their names."
