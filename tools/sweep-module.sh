#!/usr/bin/env bash
#
# sweep-module.sh - break every constant in a module, one at a time, and report what the suite misses.
#
#   tools/sweep-module.sh <module-dir> <suite-class> [limit]
#
# WHY. tools/mutate.sh breaks the mutations you THINK of. On 2026-10-05 that found seventeen unprotected fixes in
# the platformer - but only because I hand-picked each one after reading the code. That does not scale to twenty
# modules, and it only finds what I already suspect.
#
# This walks every `static final` constant in a directory instead. It does not need to know what they mean: a
# constant whose value does not matter to any check is a constant nothing is watching.
#
# IT IS DELIBERATELY BLUNT. Every mutation is the same shape - a number to 0, a string to "X", an array reversed -
# so a NOT CAUGHT is a QUESTION rather than a verdict. Some constants are genuinely free (a label, a colour) and
# some are covered by a check that happens to survive one blunt change. What it does is rank where to look.
#
# It restores the file after every mutation and recompiles between them, because a stale out/ has produced a wrong
# answer four times this week.
set -u

# ONE COPY OF "FIND A JDK", shared with mutate.sh, check-sheets.sh and run-suites.sh. The first version of this
# tool called mutate.sh without it and every mutation came back "java: command not found" - which the report
# faithfully showed as UNKNOWN for all four constants, rather than as a fault. A sweep that cannot run its suite
# must not read as a sweep that found nothing.
. "$(dirname "$0")/find-java.sh"
JAVAC="${JAVAC:-$(dirname "$JAVA")/javac}"   # find-java.sh sets JAVA and FX, not JAVAC

MOD="${1:-}"; SUITE="${2:-}"; LIMIT="${3:-12}"
[ -d "$MOD" ] || { echo "usage: tools/sweep-module.sh <module-dir> <suite-class> [limit]" >&2; exit 2; }
[ -n "$SUITE" ] || { echo "sweep-module: needs a suite class" >&2; exit 2; }

# A SUITE CLASS THAT DOES NOT EXIST MUST NOT READ AS A SWEEP THAT FOUND NOTHING. I passed
# `aside.games.fnaf.SelfTest` when the class is `aside.games.fnaf.engine.SelfTest`, and every one of twelve
# mutations came back UNKNOWN with a ClassNotFoundException - a sweep that measured nothing, in a report whose
# verdict line says how many were caught. The header already warns about this for a missing JDK; the same silence
# applies to a missing class, and it is the mistake a person actually makes.
if [ ! -f "$OUT/$(printf '%s' "$SUITE" | tr '.' '/').class" ]; then
  echo "sweep-module: no compiled class $SUITE in $OUT" >&2
  echo "  suites that DO exist for this module:" >&2
  find "$MOD" -name 'SelfTest.java' 2>/dev/null | sed 's|.*src/main/java/||; s|\.java$||; s|/|.|g' | sed 's|^|    |' >&2
  exit 2
fi

caught=0; missed=0; n=0
while IFS= read -r line; do
  file="${line%%:*}"; rest="${line#*:}"
  lineno="${rest%%:*}"; decl="${rest#*:}"
  [ "$n" -ge "$LIMIT" ] && break

  # what kind of constant is it, and what is a blunt wrong version of it
  name=$(echo "$decl" | sed -n 's/.* \([A-Z_][A-Z_0-9]*\) *=.*/\1/p')
  # THE ANCHOR CARRIES THE NAME, so it matches once. It used to be the value alone - "= 6" - AND A VALUE LIKE 6
  # APPEARS IN MORE THAN ONE CONSTANT, so mutate.sh correctly refused it as AMBIGUOUS and the constant went
  # UNTESTED. The refusal is right (a mutation must change exactly one place; "a false positive is the quiet one")
  # but the anchor was this caller's job. MEASURED 2026-10-14: 2 of 14 constants in fnaf4's engine were skipped
  # this way, and the sweep reported them as questions rather than as its own gap.
  case "$decl" in
    # ARRAYS ARE SKIPPED. The anchor has to match the file's own spacing exactly, and the first version built
    # "{ 1, 3, 5 }" where the file said "{1, 3, 5}" - so it reported ANCHOR MISSING, which reads like a fault in
    # the code rather than in the tool. Better to say nothing than to say it wrongly.
    *'[]'*)  printf '%-34s (array, skipped)\n' "$(echo "$decl" | sed -n 's/.* \([A-Z_][A-Z_0-9]*\) *=.*/\1/p')"
             continue ;;
    *String*) val=$(echo "$decl" | sed -n 's/.*= *"\([^"]*\)".*/\1/p'); [ -n "$val" ] || continue
             old="\"$val\""; new="\"X\"" ;;
    *int*|*double*|*long*|*float*)
             val=$(echo "$decl" | sed -n 's/.*= *\([0-9.]*\).*/\1/p'); [ -n "$val" ] || continue
             # A MUTATION THAT CHANGES NOTHING IS NOT AN UNWATCHED CONSTANT. fnaf2's `WIND = 0` came back
             # NOT CAUGHT for hours, and the honest report is that the mutation set it to the value it already
             # had: the constant is not unwatched, the sweep asked it a question with one answer. Reported
             # rather than counted, because a false NOT CAUGHT sends the next reader looking at real code.
             if [ "$val" = "0" ]; then printf '%-34s (already 0, nothing to change)\n' "$name"; continue; fi
             old="$name = $val"; new="$name = 0" ;;
    *) continue ;;
  esac

  name=$(echo "$decl" | sed -n 's/.* \([A-Z_][A-Z_0-9]*\) *=.*/\1/p')
  [ -n "$name" ] || continue
  n=$((n + 1))
  printf '%-34s ' "$name"
  out=$(JAVA="$JAVA" FX="$FX" timeout 600 tools/mutate.sh "$SUITE" "$file" "$old" "$new" 2>&1 | tail -1)
  case "$out" in
    *caught*)    echo "caught";   caught=$((caught + 1)) ;;
    *NOT\ CAUGHT*) echo "NOT CAUGHT"; missed=$((missed + 1)) ;;
    # A MUTATION THAT WILL NOT COMPILE IS NOT A TESTED CONSTANT. The blunt "to 0" collides with a switch's case
    # labels - fnaf2 has four of those - and the honest fallback is a value that cannot collide: one more than the
    # value it already has. MEASURED 2026-10-14: 4 of 12 constants in fnaf2's engine reported COMPILE FAIL and so
    # were never actually asked a question.
    # AND THE ONE LEVEL UP, WHICH I MISSED THE FIRST TIME: mutate.sh prints a distinct FAILED verdict
    # when the suite dies, and this case statement had no branch for it, so it fell through to `??`
    # and the sweep reported UNKNOWN anyway. A VERDICT IS ONLY AS GOOD AS THE PARSER ABOVE IT.
    *FAILED*)   echo "caught (the suite died - see the trace)"; caught=$((caught + 1)) ;;
    *COMPILE*)
      if [ -n "$val" ] && [ "$val" != "0" ]; then
        # AND +1 IS NOT ALWAYS ENOUGH: fnaf2's CHECK_HALL/CHECK_VENT_L/CHECK_VENT_R are 1, 2 and 3 in a switch,
        # so bumping one lands on the next. A value well outside the range is the fallback for that, which also
        # asks the switch what it does with a value it has no case for.
        bump=$(awk -v v="$val" 'BEGIN { printf "%.10g", v + 1 }')
        out2=$(JAVA="$JAVA" FX="$FX" timeout 600 tools/mutate.sh "$SUITE" "$file" "$old" "$name = $bump" 2>&1 | tail -1)
        case "$out2" in
          *caught*)    echo "caught (0 would not compile, tried $bump)"; caught=$((caught + 1)) ;;
          *NOT\ CAUGHT*) echo "NOT CAUGHT ($bump)"; missed=$((missed + 1)) ;;
          *COMPILE*)
            far=$(awk -v v="$val" 'BEGIN { printf "%.10g", v + 100 }')
            out3=$(JAVA="$JAVA" FX="$FX" timeout 600 tools/mutate.sh "$SUITE" "$file" "$old" "$name = $far" 2>&1 | tail -1)
            case "$out3" in
              *caught*)    echo "caught (tried $far)"; caught=$((caught + 1)) ;;
              *NOT\ CAUGHT*) echo "NOT CAUGHT ($far)"; missed=$((missed + 1)) ;;
              *)           echo "?? $out3" ;;
            esac ;;
          *)           echo "?? $out2" ;;
        esac
      else
        echo "?? $out"
      fi ;;
    *)           echo "?? $out" ;;
  esac
done < <(grep -rn "static final" "$MOD" --include=*.java 2>/dev/null \
           | grep -vE "Test\.java" \
           | grep -vE "^[^:]*:[0-9]+:.*=.*,.*=" \
           | sort)
# LINES THAT DECLARE SEVERAL CONSTANTS ARE SKIPPED. `static final int W = 1280, H = 720;` gives a greedy name match
# and a value from a different constant - the first version reported NOT CAUGHT for "H" and "OY", which are not
# constants at all. A tool that mangles its input reports confident nonsense, which is worse than reporting less.
# One declaration per line is the rule, and the ones that break it are named in the header rather than silently
# mishandled.

# PUT THE BUILD BACK. mutate.sh restores the SOURCE file after every mutation, but it compiles into classes/ and
# leaves whatever it last built there. A sweep that ends mid-injection leaves the gate failing on code that is not
# in the tree - which cost a wrong conclusion five times this week. Recompiling is the sweep's job, not the
# caller's.
# No -cp: CP belongs to mutate.sh, not to find-java.sh, and reaching for it here was the third unbound variable
# in three runs. But it DOES need the JavaFX module path - without it the sources do not compile at all, and the
# first fix reported "could not recompile" while looking correct.
"$JAVAC" --module-path "$FX" --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
  -nowarn -d classes $(find src/main/java -name '*.java') 2>/dev/null || \
  echo "NOTE: could not recompile after the sweep - recompile before believing the next result."

echo
echo "$MOD against $SUITE: $caught caught, $missed not caught, of $n tried."
echo "A NOT CAUGHT is a QUESTION: the constant may be free (a label, a colour) or covered by a check that"
echo "survives this particular blunt change. It is where to look, not what to fix."
