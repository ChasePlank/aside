#!/usr/bin/env bash
#
# diff.sh - diff two things, and TELL YOU WHAT A FILTERED DIFF WOULD HAVE HIDDEN.
#
#   tools/diff.sh <a> <b> [filter-regex]
#
# WHY THIS EXISTS. Six times in this project a check has silently narrowed what it looked at and reported a clean
# result that was not true:
#
#   1. grep for "error" -> missed an absent compiler
#   2. head -5 on a find -> hid an existing tool
#   3. file size as a content proxy -> hid a game-over screen
#   4. diff against the wrong branch -> hid a frozen backup
#   5. diff filtering ^import -> hid a structural dependency
#   6. head -6 on a correct tool -> hid the lines being looked for
#
# Every one was a filter. Every one reported clean. The lesson was written down as advice - "prefer full diffs you
# explain over filtered diffs you trust" - and advice is what failed six times.
#
# So: this prints the FULL diff, and if you pass a filter it also prints WHAT THE FILTER REMOVED, WITH A COUNT.
# The count is the point. "4 lines excluded" is a thing you have to look at; an empty diff is not.
set -u
A="${1:-}"; B="${2:-}"; FILTER="${3:-}"
if [ -z "$A" ] || [ -z "$B" ]; then
  echo "usage: tools/diff.sh <a> <b> [filter-regex]" >&2; exit 2
fi
[ -e "$A" ] || { echo "diff.sh: no such file: $A" >&2; exit 2; }
[ -e "$B" ] || { echo "diff.sh: no such file: $B" >&2; exit 2; }

ALL=$(diff -u "$A" "$B" || true)
TOTAL=$(printf '%s\n' "$ALL" | grep -cE '^[+-]' || true)
CHANGED=$(printf '%s\n' "$ALL" | grep -cE '^[+-][^+-]' || true)

echo "=== full diff: $A vs $B ==="
printf '%s\n' "$ALL"
echo
echo "=== the count, which is the part that matters ==="
echo "  $CHANGED changed line(s), $TOTAL +/- lines including the file headers"

if [ -n "$FILTER" ]; then
  KEPT=$(printf '%s\n' "$ALL" | grep -E '^[+-][^+-]' | grep -cE "$FILTER" || true)
  HIDDEN=$((CHANGED - KEPT))
  echo
  echo "=== if you had filtered on /$FILTER/ ==="
  echo "  you would have seen:  $KEPT line(s)"
  echo "  you would NOT have:   $HIDDEN line(s)"
  if [ "$HIDDEN" -gt 0 ]; then
    echo "  --- and here they are, because that is the whole point:"
    printf '%s\n' "$ALL" | grep -E '^[+-][^+-]' | grep -vE "$FILTER" | sed 's/^/    /' | head -40
    [ "$HIDDEN" -gt 40 ] && echo "    ... and $((HIDDEN - 40)) more"
  fi
fi
echo
echo "A clean filtered diff is not a clean diff. The count is what tells you which one you have."
