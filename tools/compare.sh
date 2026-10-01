#!/usr/bin/env bash
#
# compare.sh - diff two files and SAY what it ignored.
#
#   tools/compare.sh a.java b.java [--ignore-imports] [--ignore-comments]
#
# Why this exists. A diff that filters lines out to ignore package differences will hide a real structural
# difference and report a clean result that is not true. That happened: stripping ^import lines hid the fact that
# one copy imports a package the other does not have, and copying files in produced ten compile errors.
#
# Five checks this week narrowed what they looked at without saying so - a grep that read a ternary, a head -5
# that hid a tool, a file size standing in for content, a diff against the wrong branch, and the filtered diff
# above. A note saying "remember what your filter hides" did not survive any of them. A tool that prints its own
# exclusions might, because the number is in front of you rather than in a resolution.
set -euo pipefail
A="$1"; B="$2"; shift 2 || true
IGNORE_IMPORTS=0; IGNORE_COMMENTS=0
for arg in "$@"; do
  case "$arg" in
    --ignore-imports)  IGNORE_IMPORTS=1 ;;
    --ignore-comments) IGNORE_COMMENTS=1 ;;
    *) echo "compare: unknown option $arg" >&2; exit 2 ;;
  esac
done

filter() {
  local f="$1"
  if [ "$IGNORE_IMPORTS" = 1 ] && [ "$IGNORE_COMMENTS" = 1 ]; then
    grep -vE '^(package|import) |^[[:space:]]*(\*|/\*|//)' "$f"
  elif [ "$IGNORE_IMPORTS" = 1 ]; then
    grep -vE '^(package|import) ' "$f"
  elif [ "$IGNORE_COMMENTS" = 1 ]; then
    grep -vE '^[[:space:]]*(\*|/\*|//)' "$f"
  else
    cat "$f"
  fi
}

tot() { wc -l < "$1" | tr -d ' '; }
ka=$(tot "$A"); kb=$(tot "$B")
fa=$(tot <(filter "$A")); fb=$(tot <(filter "$B"))

echo "comparing: $A ($ka lines)  vs  $B ($kb lines)"
if [ "$IGNORE_IMPORTS" = 1 ] || [ "$IGNORE_COMMENTS" = 1 ]; then
  ignored_a=$((ka - fa)); ignored_b=$((kb - fb))
  echo "IGNORING: $( [ "$IGNORE_IMPORTS" = 1 ] && echo -n 'package/import lines ' )$( [ "$IGNORE_COMMENTS" = 1 ] && echo -n 'comment lines' )"
  echo "IGNORED:   $ignored_a lines from A, $ignored_b lines from B"
  echo "           ^ if that number is not what you expect, your filter is hiding something"
else
  echo "IGNORING:  nothing - this is a full comparison"
fi
n=$(diff <(filter "$A") <(filter "$B") | grep -c '^[<>]' || true)
echo "DIFFERING: $n lines"
echo "---"
diff <(filter "$A") <(filter "$B") || true
