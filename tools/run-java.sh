#!/usr/bin/env bash
#
# run-java.sh - run a JavaFX class from this repository, with everything a run needs.
#
#   tools/run-java.sh <MainClass> [args...]
#   tools/run-java.sh -cp <classpath> <MainClass> [args...]      # override the default classpath
#
# WHY THIS EXISTS. `java -cp out:src/main/resources aside.engine.SelfTest` LOOKS right and dies with
#
#     NoClassDefFoundError: javafx/scene/Parent
#
# which reads exactly like a broken suite. JavaFX is on the MODULE path here, and forgetting `--module-path` and
# `--add-modules` has cost this session three misreadings in one week - each time a run that looked like a fault in
# the code. The gate and the suites always pass them; hand-written commands do not, because remembering is the
# weak part. This is the same move as `tools/with-display.sh`: put the precondition IN THE PATH rather than in my
# head, so the mistake cannot be made by typing the obvious thing.
#
# IT ALSO GUARANTEES A DISPLAY, because every class worth running here is JavaFX: that is what with-display.sh does,
# and this calls it rather than repeating it.
set -u
cd "$(dirname "$0")/.." || exit 2

. tools/find-java.sh >/dev/null 2>&1 || { echo "run-java: no usable JDK - see the message above" >&2; exit 2; }

CP="$OUT:src/main/resources"
if [ "${1:-}" = "-cp" ]; then
  [ $# -ge 3 ] || { echo "usage: tools/run-java.sh [-cp <classpath>] <MainClass> [args...]" >&2; exit 2; }
  CP="$2"; shift 2
fi
[ $# -ge 1 ] || { echo "usage: tools/run-java.sh [-cp <classpath>] <MainClass> [args...]" >&2; exit 2; }

exec tools/with-display.sh "$JAVA" --module-path "$FX" \
    --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -cp "$CP" "$@"
