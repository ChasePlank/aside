#!/usr/bin/env bash
#
# Run ONE test class and report only whether it passed. Exit 0 if it did.
#
#   tools/run-one-suite.sh aside.engine.SelfTest
#
# WHY THIS EXISTS. The mutation list needs to run a suite against the UNMUTATED tree - that is how it tells "a check
# noticed the mutation" apart from "this suite fails anyway". Three false verdicts in one week came from the
# difference: a dead display failed fourteen suites at once and a mutation looked caught by all of them; a mutated
# build that would not start was reported as a detection; and a flaky Robot test aborting was counted as one, which
# HID A REAL GAP. run-suites.sh cannot do this job - it judges a curated list, prints verdicts, and knows about the
# display and the jars. This is the one-suite primitive underneath it.
set -u
cd "$(dirname "$0")/.." || exit 2
. ./tools/find-java.sh    # sets JAVA, JAVAC and FX, or exits 2 naming what to set

suite="${1:-}"
if [ -z "$suite" ]; then
  echo "usage: tools/run-one-suite.sh <fully.qualified.Class>" >&2
  exit 2
fi

# The class has to exist, or this reports a pass for a suite that was never there.
if [ ! -f "src/main/java/$(printf '%s' "$suite" | tr '.' '/').java" ]; then
  echo "run-one-suite: no such class: $suite" >&2
  exit 2
fi

# The display is needed by the screen suites and its absence is the whole reason this exists, so it is started
# rather than warned about - and a failure to start it is a failure, not a quiet pass.
if ! command -v xdpyinfo >/dev/null 2>&1 || ! xdpyinfo -display "${DISPLAY:-:99}" >/dev/null 2>&1; then
  Xvfb "${DISPLAY_NUM:-:99}" -screen 0 "${SCREEN:-1400x900x24}" >/tmp/run-one-suite-xvfb.log 2>&1 &
  export DISPLAY="${DISPLAY_NUM:-:99}"
  for _ in $(seq 1 40); do xdpyinfo -display "$DISPLAY" >/dev/null 2>&1 && break; sleep 0.25; done
  if ! xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
    echo "run-one-suite: no display and Xvfb did not come up - this suite's result would mean nothing" >&2
    exit 2
  fi
fi

# AND IT COMPILES FIRST, which is not tidiness: the mutation list calls this immediately after mutate.sh has put the
# source back, and mutate.sh's classes are still built from the MUTATION. Running the suite without rebuilding here
# would test the mutated build and report every mutation as caught - the exact false positive this exists to prevent.
if ! "$JAVAC" --module-path "$FX" --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
      -cp out -d out $(find src/main/java -name '*.java') >/tmp/run-one-suite-compile.log 2>&1; then
  echo "run-one-suite: the tree does not compile - see /tmp/run-one-suite-compile.log" >&2
  exit 2
fi

exec timeout "${SUITE_TIMEOUT:-300}" "$JAVA" --module-path "$FX" \
  --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
  -cp "out:src/main/resources" "$suite"
