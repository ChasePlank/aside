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

MOD="${1:-}"; SUITE="${2:-}"; LIMIT="${3:-12}"
[ -d "$MOD" ] || { echo "usage: tools/sweep-module.sh <module-dir> <suite-class> [limit]" >&2; exit 2; }
[ -n "$SUITE" ] || { echo "sweep-module: needs a suite class" >&2; exit 2; }

caught=0; missed=0; n=0
while IFS= read -r line; do
  file="${line%%:*}"; rest="${line#*:}"
  lineno="${rest%%:*}"; decl="${rest#*:}"
  [ "$n" -ge "$LIMIT" ] && break

  # what kind of constant is it, and what is a blunt wrong version of it
  case "$decl" in
    *'[]'*)  val=$(echo "$decl" | sed -n 's/.*{\(.*\)}.*/\1/p'); [ -n "$val" ] || continue
             repl=$(echo "$val" | tr ',' '\n' | tac | tr '\n' ',' | sed 's/,$//')
             old="{ $val }"; new="{ $repl }" ;;
    *String*) val=$(echo "$decl" | sed -n 's/.*= *"\([^"]*\)".*/\1/p'); [ -n "$val" ] || continue
             old="\"$val\""; new="\"X\"" ;;
    *int*|*double*|*long*|*float*)
             val=$(echo "$decl" | sed -n 's/.*= *\([0-9.]*\).*/\1/p'); [ -n "$val" ] || continue
             old="= $val"; new="= 0" ;;
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
    *)           echo "?? $out" ;;
  esac
done < <(grep -rn "static final" "$MOD" --include=*.java 2>/dev/null | grep -vE "Test\.java" | sort)

# PUT THE BUILD BACK. mutate.sh restores the SOURCE file after every mutation, but it compiles into classes/ and
# leaves whatever it last built there. A sweep that ends mid-injection leaves the gate failing on code that is not
# in the tree - which cost a wrong conclusion five times this week. Recompiling is the sweep's job, not the
# caller's.
"$JAVAC" -nowarn -cp "$CP" -d classes $(find src/main/java -name '*.java') 2>/dev/null || \
  echo "NOTE: could not recompile after the sweep - recompile before believing the next result."

echo
echo "$MOD against $SUITE: $caught caught, $missed not caught, of $n tried."
echo "A NOT CAUGHT is a QUESTION: the constant may be free (a label, a colour) or covered by a check that"
echo "survives this particular blunt change. It is where to look, not what to fix."
