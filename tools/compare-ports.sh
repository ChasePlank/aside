#!/usr/bin/env bash
#
# compare-ports.sh - run the two Java-vs-JavaScript port comparisons, both sides, and diff them.
#
#   tools/compare-ports.sh
#
# WHY THIS EXISTS. tools/bearings-trace.mjs and tools/redaction-trace.mjs each compare a copy of a RULE: the phone
# build carries a second implementation of aside.games.bearings.Bearings and aside.games.redaction.Redaction,
# because neither rule can be resolved into a table. Two copies of a rule is the situation that produced the
# Inventory save-format bug - both builds self-consistent, disagreeing about what the same save meant.
#
# run-suites.sh already runs five of these mjs tools and its comment says why: they "were written one at a time,
# run once by hand, and then NOT WIRED INTO ANYTHING: the audio splice landed inside the block they parse, every
# one of them broke, and nothing said so for two days."
#
# THESE TWO WERE STILL IN THAT STATE. Not because they were broken - both pass - but because they need a SETUP STEP
# that the five wired ones do not. bearings-trace takes a seed and needs the Java side run separately;
# redaction-trace wants a reference dump at /tmp/redaction-java.txt that something else has to make first. Run by
# hand with neither, both exit 1, which looks exactly like a failure and is not one. That is why nobody ran them.
set -u
. "$(dirname "$0")/find-java.sh"
cd "$(dirname "$0")/.." || exit 2

fails=0

# ---- bearings: same seed through both implementations, then diff ------------------------------------------------
SEED="${SEED:-7}"
JB=$(mktemp); JJ=$(mktemp)
node tools/bearings-trace.mjs "$SEED" > "$JB" 2>&1 || { echo "  bearings-trace FAILED to run"; head -3 "$JB" | sed 's/^/    /'; fails=$((fails+1)); }
"$JAVA" --module-path "$FX" --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -cp "$OUT:src/main/resources" aside.games.bearings.Trace "$SEED" > "$JJ" 2>&1 || { echo "  the java side FAILED to run"; fails=$((fails+1)); }
if [ "$fails" = 0 ]; then
  if diff -q "$JB" "$JJ" >/dev/null 2>&1; then
    echo "  bearings   ok     js and java agree over $SEED, $(wc -l < "$JB") lines"
  else
    echo "  bearings   FAIL   the two copies of the rule disagree:"; diff "$JB" "$JJ" | head -6 | sed 's/^/    /'
    fails=$((fails+1))
  fi
fi
rm -f "$JB" "$JJ"

# ---- redaction: the java side makes the reference, then the js side checks every filing in it -------------------
DUMP=/tmp/redaction-java.txt
"$JAVA" --module-path "$FX" --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -cp "$OUT:src/main/resources" aside.games.redaction.Trace --dump "$DUMP" > /dev/null 2>&1 \
  || { echo "  redaction  FAIL   the java side could not write the reference dump"; fails=$((fails+1)); }
RO=$(node tools/redaction-trace.mjs 2>&1); rc=$?
if [ $rc -ne 0 ]; then
  echo "  redaction  FAIL   $(echo "$RO" | head -1)"; fails=$((fails+1))
else
  echo "  redaction  ok     $(echo "$RO" | grep -E 'disagreements|OK' | tr '\n' ' ' | sed 's/  */ /g')"
fi

echo
if [ "$fails" = 0 ]; then
  echo "=== 2 port comparison(s) agree ==="
else
  echo "=== $fails port comparison(s) FAILED ==="
fi
exit "$fails"
