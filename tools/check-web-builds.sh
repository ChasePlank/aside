#!/usr/bin/env bash
#
# check-web-builds.sh - are the GAME phone builds still what their generators produce?
#
#   tools/check-web-builds.sh
#
# WHY THIS EXISTS. The README says "The suite regenerates each build and compares it to the checked-in copy, so a
# stale build fails rather than shipping." THAT WAS TRUE FOR THE STORIES AND FALSE FOR THE GAMES. `WebExport` covers
# the twenty story builds and SelfTest checks each one; the eighteen GAME builds come from `Web<Game>` classes
# (`WebPizzeria`, `WebBearings`, `WebDrift`, ...) and NOTHING regenerated them - so an edit to one of those
# generators would ship a stale page, and the only trace would be a file that no longer matches its source.
# MEASURED on 2026-10-14: `grep -c 'Web[A-Z]' tools/mutations.sh` is 0, so no mutation entry touches one either, and
# `web/fnaf2.html` regenerates byte-identically TODAY - the claim was unchecked, not false.
#
# HOW IT CHECKS, without needing to know which generator writes which file: hash every `web/*.html`, run every
# generator, hash them again, and report what moved. Each generator's output name is its own business; the directory
# is the contract.
#
# A FAILURE RESTORES THE FILES IT CHANGED, from git and by name, so a run that fails does not leave the tree
# modified - and a run that is interrupted is caught by the same trap.
set -u
cd "$(dirname "$0")/.." || exit 2

. tools/find-java.sh >/dev/null 2>&1 || { echo "check-web-builds: no usable JDK" >&2; exit 2; }

hashdir() { for f in web/*.html; do printf '%s %s\n' "$(sha256sum "$f" | cut -d' ' -f1)" "$f"; done; }

BEFORE=$(mktemp); AFTER=$(mktemp); trap 'rm -f "$BEFORE" "$AFTER"' EXIT
hashdir > "$BEFORE"

# AND THE PROPERTY THE BROWSER BUILDS ARE SUPPOSED TO HAVE: NO SOUND AT ALL.
#
# MEASURED 2026-10-16 ACROSS ALL 24 BUILDS: zero `new Audio`, zero `data:audio`, zero `.mp3` or `.wav` - the FNAF
# builds carry `cue(...)` calls against a silent stub, and the story builds carry none. So a player on a phone hears
# nothing from any of them, AND IT WAS DOCUMENTED NOWHERE: the README describes audio/ and the desktop builds and
# never said whether the browser shelf was meant to be quiet. A SILENT BUILD AND A BROKEN ONE LOOK IDENTICAL.
#
# CHECKED HERE, so the README's sentence cannot go quietly stale: if somebody adds audio, this fails and the sentence
# has to change with it. That is the only thing that keeps a documented property honest.
NOISY=0
for f in web/*.html; do
  if grep -qE "new Audio|data:audio|\.mp3|\.wav" "$f"; then
    echo "  $f carries audio, and the README says the browser builds are silent" >&2
    NOISY=$((NOISY + 1))
  fi
done
if [ "$NOISY" -ne 0 ]; then
  echo "  $NOISY build(s) carry audio - either that is new and the README needs a line, or it is a mistake" >&2
  exit 1
fi

GENS=0
for f in src/main/java/aside/games/*/Web*.java; do
  [ -f "$f" ] || continue
  cls=$(printf '%s' "$f" | sed 's|src/main/java/||; s|/|.|g; s|\.java$||')
  timeout 300 tools/run-headless.sh "$JAVA" -cp "$OUT:src/main/resources" "$cls" >/dev/null 2>&1 || {
    echo "  $cls FAILED to run - a generator that cannot run is not a generator that agrees" >&2
    hashdir > "$AFTER"
    for f2 in $(comm -13 <(cut -d' ' -f2 "$BEFORE" | sort) <(cut -d' ' -f2 "$AFTER" | sort)); do git checkout -- "$f2" 2>/dev/null; done
    exit 1
  }
  GENS=$((GENS + 1))
done

hashdir > "$AFTER"
CHANGED=$(comm -3 <(sort "$BEFORE") <(sort "$AFTER") | awk '{print $2}' | sort -u)
if [ -z "$CHANGED" ]; then
  echo "  === $GENS generator(s) run, and every web/*.html is what it produces ==="
  exit 0
fi
echo "  STALE: these builds are not what their generators produce:" >&2
for f in $CHANGED; do
  echo "    $f" >&2
  git checkout -- "$f" 2>/dev/null || true      # restore by name, so a failure leaves the tree as it found it
done
echo "  regenerate them and commit the result" >&2
exit 1
