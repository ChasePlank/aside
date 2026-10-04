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

# OUT MUST MATCH THE OTHER TOOLS. This defaulted to `classes`, while run-suites.sh and check-sheets.sh both
# default to `out`. So this tool rendered the games from a STALE BUILD, compared the results against frames that
# were themselves made from that same stale build, found them identical, and reported "nothing to change" - while
# the gate, rendering from `out`, reported ten frames stale. That is why regenerating the frames was done by hand
# four times in one day: the tool that exists to do it could not see the difference it was there to fix.
# OUT IS NOT DEFAULTED HERE: find-java.sh looks for a build directory,
# because a default that names one is a guess. See the note there.
# FX IS NOT DEFAULTED HERE, for the same reason JAVA is not: a value set
# before find-java.sh runs makes its search a no-op, and this one defaulted
# to a path that does not exist. Let the lookup do its job.
# JAVA IS NOT DEFAULTED HERE. It used to be `JAVA="${JAVA:-java}"` and then `. tools/find-java.sh`, which meant
# find-java.sh could never override it - the variable was already set, so the lookup was a no-op and every run
# died with "exec: java: not found" from run-headless.sh. The script then globbed an empty directory, created a
# file literally named `docs/frames/*.png` because the pattern did not expand, and printed "1 frame(s) updated".
# It had never rendered a single game. Let find-java.sh do its job.
. tools/find-java.sh

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

echo "running CheckGames into $TMP ..."
tools/run-headless.sh "$JAVA" --module-path "$FX" \
  --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
  -Dshotdir="$TMP" -cp "$OUT:src/main/resources" aside.tools.CheckGames > "$TMP/log" 2>&1
# DID IT ACTUALLY RUN? The first version of this script did not ask, and it
# could not have been more wrong: JAVA was defaulted before the lookup so every
# run died with "exec: java: not found", the unguarded glob then wrote a file
# called `docs/frames/*.png` and counted it, and the script reported "1 frame(s)
# updated" and told you to commit. It had never rendered a single game.
#
# A tool that reports success without checking is worse than no tool, because it
# is believed. So the verdict is required now, and its absence is a failure.
if ! grep -qE '=== [0-9]+ game' "$TMP/log"; then
  echo "CheckGames did not run -- no verdict in its log. Nothing was written." >&2
  tail -5 "$TMP/log" | sed 's/^/  /' >&2
  exit 1
fi
grep -E '=== [0-9]+ game' "$TMP/log" | sed 's/^/  /'

changed=0
# GUARD THE GLOB. Without this an empty directory leaves the pattern unexpanded, basename gives "*", and the
# script writes a file called `docs/frames/*.png` and counts it as an update.
shopt -s nullglob
for f in "$TMP"/*.png; do
  n=$(basename "$f")
  case "$n" in contact-sheet.png|drift.png|ledger.png) continue;; esac
  if [ -f "docs/frames/$n" ] && cmp -s "$f" "docs/frames/$n"; then continue; fi
  cp "$f" "docs/frames/$n"
  echo "  docs/frames/$n"
  changed=$((changed + 1))
done
echo "  $changed frame(s) updated"

# The sheet itself, which is the frames in one image -- BUT ONLY WHEN A FRAME
# MOVED. The sheet contains drift.png and ledger.png, so it is different every
# time it is generated; copying it unconditionally meant this tool produced a
# diff on every run, including runs where nothing had changed. Nothing compares
# the sheet either (check-sheets.sh skips it by name for the same reason), so
# that diff was pure noise -- a commit every fire that said nothing.
if [ "$changed" -gt 0 ]; then
  cp "$TMP/contact-sheet.png" docs/contact-sheet.png
  echo "  docs/contact-sheet.png"
fi

# And the phone's, if the browser tooling is here.
if [ -d node_modules/puppeteer ]; then
  SHOTDIR="$TMP/webshots" SHEET="$TMP/phone.png" node tools/check-web.mjs >/dev/null 2>&1
  SHOTDIR="$TMP/webshots" SHEET="$TMP/phone.png" node tools/contact-sheet-web.mjs >/dev/null 2>&1
  if [ -f "$TMP/phone.png" ] && [ "$changed" -gt 0 ]; then
    cp "$TMP/phone.png" docs/contact-sheet-phone.png
    echo "  docs/contact-sheet-phone.png"
  fi
else
  echo "  phone sheet NOT regenerated - run 'npm install' first"
fi

echo
if [ "$changed" -gt 0 ]; then
  echo "Now: git add docs/ && commit."
else
  echo "Nothing changed, so there is nothing to commit."
fi
