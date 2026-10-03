#!/usr/bin/env bash
#
# Are the two contact sheets in docs/ still what the games and the phone builds produce?
#
#   tools/check-sheets.sh
#
# Both sheets are committed images of generated frames, which makes them claims about the current code - and a
# committed generated file that nothing compares is a claim that goes stale quietly. This regenerates both into
# a temporary directory and diffs.
#
# IT WAS WRITTEN AFTER GETTING THE REPRODUCIBILITY WRONG IN THE OTHER DIRECTION. The README said the sheets
# could not be compared because "a typewriter is mid-word and a fan is at a different angle each time". That was
# an assumption. Measured: two independent runs of all 24 desktop frames and all 23 phone frames are
# byte-identical, because everything in them advances on tick count and nothing reads a clock. So they can be
# compared, and now they are.
set -u

cd "$(dirname "$0")/.." || exit 2
OUT="${OUT:-out}"
FX="${FX:-/root/javafx-sdk-27/lib}"

# Find a JDK rather than assuming one is on PATH. It is not on PATH in the sandbox this was written in - the
# toolchain lives under /root - and the first run here failed with `exec: java: not found`, which reads as "the
# sheets are stale" if you only look at the verdict.
if [ -z "${JAVA:-}" ]; then
  if command -v java >/dev/null 2>&1; then JAVA=java
  else
    for cand in /root/jdk-*/bin/java /usr/lib/jvm/*/bin/java; do
      [ -x "$cand" ] && JAVA="$cand" && break
    done
  fi
fi
if [ -z "${JAVA:-}" ]; then
  echo "no java found - set JAVA=/path/to/bin/java" >&2
  exit 2
fi

if [ ! -d "$OUT" ]; then echo "no build in $OUT" >&2; exit 2; fi

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
fail=0

# Desktop: CheckGames writes the frames AND the sheet. It needs a display, so it goes through run-headless.sh -
# which starts one if the last reboot took the old one. The first version of this called java directly and, with
# no display, CheckGames never ran at all: the sheet was never written, cmp failed, and the script reported the
# committed sheet as STALE. A comparison that did not happen reported as a comparison that failed - the third
# time this week a check has raised a problem that was its own.
tools/run-headless.sh "$JAVA" --module-path "$FX" \
      --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
      -Dshotdir="$TMP/games" -cp "$OUT:src/main/resources" aside.tools.CheckGames > "$TMP/checkgames.log" 2>&1
if [ ! -f "$TMP/games/contact-sheet.png" ]; then
  echo "  docs/contact-sheet.png        COULD NOT CHECK - CheckGames wrote no sheet"
  tail -3 "$TMP/checkgames.log" | sed 's/^/      /'
  fail=1
elif cmp -s "$TMP/games/contact-sheet.png" docs/contact-sheet.png; then
  echo "  docs/contact-sheet.png        current"
else
  echo "  docs/contact-sheet.png        STALE - regenerate with aside.tools.CheckGames"
  fail=1
fi

# Phone: check-web writes the frames, contact-sheet-web puts them together.
if [ -d node_modules/puppeteer ]; then
  SHOTDIR="$TMP/webshots" SHEET="$TMP/phone.png" node tools/check-web.mjs >/dev/null 2>&1
  SHOTDIR="$TMP/webshots" SHEET="$TMP/phone.png" node tools/contact-sheet-web.mjs >/dev/null 2>&1
  if cmp -s "$TMP/phone.png" docs/contact-sheet-phone.png; then
    echo "  docs/contact-sheet-phone.png  current"
  else
    echo "  docs/contact-sheet-phone.png  STALE - regenerate with tools/check-web.mjs and tools/contact-sheet-web.mjs"
    fail=1
  fi
else
  echo "  phone sheet NOT CHECKED - run 'npm install'"
fi

echo
[ $fail -eq 0 ] && echo "=== both sheets are current ===" || echo "=== a sheet is stale ==="
exit $fail
