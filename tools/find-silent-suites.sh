#!/usr/bin/env bash
#
# Which suites pass without checking anything?
#
#   tools/run-suites.sh > /tmp/gate.log 2>&1
#   tools/find-silent-suites.sh /tmp/gate.log
#
# WHY THIS EXISTS. Twice this week a class compiled, ran, and printed `0 passed, 0 failed` - once when two methods
# were moved into a new test class and their CALLS were not, and once more in a fixture. That line reads like a clean
# run. It is the worst possible output, because nothing about it says the checks were defined and never invoked.
#
# The gate already prints every suite's verdict, so this reads its own log rather than running anything.
#
# AND IT COUNTS WHAT IT CANNOT READ, because the first version of this did not and was wrong in exactly the way it
# exists to catch. It matched one verdict shape and silently skipped the rest - seventeen suites parsed, twenty-three
# unreported, and the summary line said "none reported zero" as though the other twenty-three had been looked at. A
# sweep that cannot say how much it saw is a sweep you cannot trust, so it now prints the unread ones too.
set -u
LOG="${1:-}"
if [ -z "$LOG" ] || [ ! -f "$LOG" ]; then
  echo "usage: find-silent-suites.sh <gate-log>   (run tools/run-suites.sh > log 2>&1 first)" >&2
  exit 2
fi

total=0; silent=0; unread=0
while IFS= read -r line; do
  # A suite line is indented and names a class or tool, then a verdict. Skip the gate's own headers and prose.
  # A suite line is indented, names a class or tool, carries a verdict, and is not one of the gate's own summaries.
  case "$line" in
    "  "*" ok "*|"  "*" FAIL "*) ;;
    *) continue ;;
  esac
  case "$line" in *"==="*) continue ;; esac
  name=$(echo "$line" | awk '{print $1}')
  # THREE SHAPES, and the second one caught me: `N passed`, `N checks`, and `N/N scene(s)`. The first version knew
  # only `N passed` and reported seventeen suites read out of forty - silent about the rest, which is the exact defect
  # this script exists to find. Anything with none of the three is UNREAD, not fine.
  passed=$(echo "$line" | grep -oE '[0-9]+ (passed|checks)' | head -1 | grep -oE '^[0-9]+')
  if [ -z "$passed" ]; then
    passed=$(echo "$line" | grep -oE '[0-9]+/[0-9]+ scene' | head -1 | grep -oE '^[0-9]+')
  fi
  if [ -z "$passed" ]; then
    unread=$((unread + 1)); total=$((total + 1))
    echo "  UNREAD  $name   $(echo "$line" | cut -c1-100)"
    continue
  fi
  total=$((total + 1))
  if [ "$passed" -eq 0 ]; then
    silent=$((silent + 1))
    echo "  SILENT  $name   $passed verdict(s)"
  fi
done < "$LOG"

echo
echo "=== $total suite(s) read: $silent silent, $unread unread ==="
if [ "$silent" -gt 0 ]; then
  echo "A suite that runs nothing looks exactly like a suite that passed. Check each one: either its checks are"
  echo "defined and never called, or it reports its verdict in prose instead of counting."
  exit 1
fi
if [ "$unread" -gt 0 ]; then
  echo "Unread means the line did not carry a count this script recognises - not that the suite is fine."
fi
