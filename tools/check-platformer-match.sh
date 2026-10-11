#!/usr/bin/env bash
#
# Do the two editions of the platformer have the same engine?
#
#   tools/check-platformer-match.sh ../tp
#
# WHY THIS EXISTS. The FNAF game has had a tool like this for weeks, and it has caught real drift. THE PLATFORMER
# HAS HAD NONE - and on 11 October that cost a day: the frame-clamp fix (MAX_FRAME made public, a new advance()
# method, and the screen's bare `Math.min(frameTime, 0.25)` pointed at the named constant) was made here and never
# crossed over. The release kept a clamp that could not fire and a magic number where the name should be. Nothing
# compared the two, so nothing said so. What noticed in the end was porting a check that needed the missing method.
#
# WHAT IT COMPARES, AND WHAT IT DOES NOT. The engine classes only. The game layer - the screens - legitimately
# differs: one edition is the library's copy and the other is the release, and their menus, their room mode and
# their endings are not the same code. The engine is the part that is meant to be the same, and it is the part a
# fix has to be applied to twice.
#
# A DIFFERENCE IS NOT AUTOMATICALLY A FAULT. Some of these files differ because a comment names the other
# repository, or because a test class names its own package. This prints them and asks; the answer for behaviour is
# to run both gates and compare the numbers.
set -u
cd "$(dirname "$0")/.." || exit 2

RELEASE="${1:-../tp}"
HERE_ENGINE="src/main/java/aside/games/fruitjump/engine"
THERE_ENGINE="$RELEASE/src/main/java/tropical/engine"

[ -d "$HERE_ENGINE" ]  || { echo "check-platformer-match: no engine at $HERE_ENGINE" >&2; exit 2; }
[ -d "$THERE_ENGINE" ] || { echo "check-platformer-match: no engine at $THERE_ENGINE - pass the release path" >&2; exit 2; }

# Every reference to either package is stripped, so the comparison is of the CODE and not of the names it lives
# under. The first version of this normalised only the `package` line and reported eighteen differences, most of
# them test classes naming their own package.
normalise() {
  sed -e 's/^package .*;//' \
      -e 's/aside\.games\.fruitjump\.engine\.//g' \
      -e 's/aside\.games\.fruitjump\.//g' \
      -e 's/tropical\.engine\.//g' \
      -e 's/tropical\.//g' \
      -e 's/aside\.//g' \
      -e 's/\baside\b//g' \
      -e 's/\btropical\b//g' "$1"
}

same=0; differ=0; differing=(); missing=(); detail=()
for f in "$HERE_ENGINE"/*.java; do
  name="$(basename "$f")"
  other="$THERE_ENGINE/$name"
  if [ ! -f "$other" ]; then
    missing+=("$name"); differ=$((differ + 1)); continue
  fi
  if diff -q <(normalise "$f") <(normalise "$other") >/dev/null; then
    same=$((same + 1))
    continue
  fi
  # AND THE DIFFERENCE IS MEASURED TWO WAYS, because the first version counted a reordering as a change: diffing the
  # two files line by line reported the Boss as different when the same methods were simply in a different order.
  # The SORTED diff is the one that says what is in one and not the other - the content - and the unsorted count is
  # kept beside it so "reordered" and "genuinely different" are told apart.
  content=$(diff <(normalise "$f" | sort) <(normalise "$other" | sort) | grep -cE '^[<>]' || true)
  order=$(diff <(normalise "$f") <(normalise "$other") | grep -cE '^[<>]' || true)
  differing+=("$name")
  detail+=("$(printf '%-16s %4d line(s) in one and not the other, %4d when order counts' "$name" "$content" "$order")")
  differ=$((differ + 1))
done

echo "=== $same engine file(s) identical, $differ differing ==="
if [ ${#missing[@]} -gt 0 ]; then
  echo "in this repository and NOT in the release: ${missing[*]}"
fi
if [ ${#differing[@]} -gt 0 ]; then
  for d in "${detail[@]}"; do echo "  $d"; done
  echo "differing: ${differing[*]}"
  echo
  echo "A difference is not automatically a fault - a comment can name the other repository, and a test class"
  echo "names its own package. What it means is that somebody changed one edition and the other has not been"
  echo "looked at. The answer for behaviour is both gates, compared."
  exit 1
fi
echo "  OK every engine class is the same code in both editions"
