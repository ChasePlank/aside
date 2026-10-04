#!/usr/bin/env bash
#
# Regenerate the frames docs/frames/ holds, and the two contact sheets.
#
#   tools/regenerate-frames.sh
#
# WHY THIS EXISTS. The frames go stale whenever a game changes, and games change
# most days -- four times on 2026-10-03 alone. Each time it was the same
# two-command job done by hand: run CheckGames into a temp directory, copy the
# frames that differ, skip the three that cannot be compared. A job done by hand
# four times in a day is a job that should be a script.
#
# WHAT IT DOES NOT DO. It does not run the gate, and the gate does not run it: a
# check that rewrites the thing it is checking is not a check. This is the tool
# you run when the gate says STALE, and then you commit what it wrote.
#
# THE THREE IT SKIPS, and why they are named rather than detected:
#
#   contact-sheet.png  contains drift.png and ledger.png, so it inherits both
#   drift.png          Drift.of() seeds from System.nanoTime() -- a function of
#                      WHEN you ran it
#   ledger.png         opens from a save, so it is a function of what a previous
#                      run left behind
#
# Detecting them by running twice is a heuristic that lies: a coincidence and a
# real change look identical from one pair of runs.
set -u
cd "$(dirname "$0")/.." || exit 2

OUT="${OUT:-classes}"
FX="${FX:-/root/javafx-sdk-27/lib}"
JAVA="${JAVA:-java}"
. tools/find-java.sh

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "running CheckGames into $TMP ..."
tools/run-headless.sh "$JAVA" --module-path "$FX" \
  --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
  -Dshotdir="$TMP" -cp "$OUT:src/main/resources" aside.tools.CheckGames > "$TMP/log" 2>&1
grep -E '=== [0-9]+ game' "$TMP/log" | sed 's/^/  /'

changed=0
for f in "$TMP"/*.png; do
  n=$(basename "$f")
  case "$n" in contact-sheet.png|drift.png|ledger.png) continue;; esac
  if [ -f "docs/frames/$n" ] && cmp -s "$f" "docs/frames/$n"; then continue; fi
  cp "$f" "docs/frames/$n"
  echo "  docs/frames/$n"
  changed=$((changed + 1))
done
echo "  $changed frame(s) updated"

# The sheet itself, which is the frames in one image.
cp "$TMP/contact-sheet.png" docs/contact-sheet.png
echo "  docs/contact-sheet.png"

# And the phone's, if the browser tooling is here.
if [ -d node_modules/puppeteer ]; then
  SHOTDIR="$TMP/webshots" SHEET="$TMP/phone.png" node tools/check-web.mjs >/dev/null 2>&1
  SHOTDIR="$TMP/webshots" SHEET="$TMP/phone.png" node tools/contact-sheet-web.mjs >/dev/null 2>&1
  if [ -f "$TMP/phone.png" ]; then
    cp "$TMP/phone.png" docs/contact-sheet-phone.png
    echo "  docs/contact-sheet-phone.png"
  fi
else
  echo "  phone sheet NOT regenerated - run 'npm install' first"
fi

echo
echo "Now: git add docs/ && commit."
