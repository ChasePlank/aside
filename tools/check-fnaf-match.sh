#!/usr/bin/env bash
#
# Does the FNAF engine in this repository still match the standalone copy?
#
#   tools/check-fnaf-match.sh [path-to-the-standalone]
#
# The default path is ../fnaf, which is where the two sit side by side in a working checkout. If the standalone
# is not there the script says so and exits 2 rather than reporting a pass - a comparison that did not happen is
# not a comparison that succeeded.
#
# WHY THIS EXISTS. These two are meant to be the same game, and the README says so: "the two are kept
# behaviourally identical, verified by their self-tests reporting the same numbers rather than by diffing text".
# That is the right criterion for BEHAVIOUR and it is not a check anyone can run in a second - it means building
# both and reading two sets of win rates. So drift is found late, by hand, or not at all. This does the text
# comparison as a first pass: it is cheap, it is exact, and every difference it reports is a question worth
# asking even when the answer is "that is just where the constant sits".
#
# WHAT IT COMPARES. The engine package, file by file. The two repositories write the same package under two
# names - aside.games.fnaf.engine and fnaf.engine - so the package line and any reference to it are normalised
# away. Imports of aside.ui.* are dropped: those exist only on this side, and a file that has them cannot travel.
# Blank lines are dropped, because they are not content.
#
# WHAT IT DOES NOT COMPARE. The screens. They are genuinely different code - one runs on this engine's UiManager,
# the other on its own Screen/ScreenManager - and a text comparison of them would report the framework as drift
# every single time, which is how a check becomes noise. The engine is where the game is.
set -u

cd "$(dirname "$0")/.." || exit 2
HERE="$(pwd)"
# Find the standalone. It is called `fnaf` in a checkout of the two side by side and `fnaf2` in the one this was
# written in, and a script that says "not checked" while the thing is sitting right there is worse than one that
# looks in three places. FNAF_STANDALONE overrides everything.
THEIRS="${1:-${FNAF_STANDALONE:-}}"
if [ -z "$THEIRS" ]; then
  for cand in "../fnaf" "../fnaf2" "../ChasePlank/fnaf" "../ChasePlank/fnaf2"; do
    if [ -d "$cand/src/main/java/fnaf/engine" ]; then THEIRS=$(cd "$cand" && pwd); break; fi
  done
fi
[ -z "$THEIRS" ] && THEIRS="$(cd .. && pwd)/fnaf"

if [ ! -d "$THEIRS/src/main/java/fnaf/engine" ]; then
  echo "no standalone checkout at $THEIRS" >&2
  echo "  pass the path as an argument:  tools/check-fnaf-match.sh /path/to/fnaf" >&2
  exit 2
fi

norm() {
  sed -e 's/^package aside\.games\.fnaf\.engine;/package fnaf.engine;/' \
      -e 's/aside\.games\.fnaf\.engine/fnaf.engine/g' \
      -e '/^import aside\.ui\./d' \
      -e '/^[[:space:]]*$/d' "$1"
}

pass=0; differ=0; differing=()
for f in "$HERE"/src/main/java/aside/games/fnaf/engine/*.java; do
  name=$(basename "$f")
  theirs="$THEIRS/src/main/java/fnaf/engine/$name"
  if [ ! -f "$theirs" ]; then
    printf '  %-22s ONLY HERE\n' "$name"
    differ=$((differ + 1)); differing+=("$name")
    continue
  fi
  if diff -q <(norm "$f") <(norm "$theirs") >/dev/null; then
    printf '  %-22s identical\n' "$name"
    pass=$((pass + 1))
  else
    n=$(diff <(norm "$f") <(norm "$theirs") | grep -c '^[<>]')
    printf '  %-22s DIFFERS   %s line(s)\n' "$name" "$n"
    differ=$((differ + 1)); differing+=("$name")
  fi
done

echo
echo "=== $pass engine file(s) identical, $differ differing ==="
if [ $differ -gt 0 ]; then
  echo "differing: ${differing[*]}"
  echo
  echo "A difference is not automatically a fault - a constant can simply sit in a different place, which is"
  echo "where one of these two files has been for a day. It is a question. The answer for behaviour is the"
  echo "suites: run both and compare the numbers, which is what the README says these two are aligned by."
  exit 1
fi
