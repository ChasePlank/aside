#!/usr/bin/env bash
#
# sync-release.sh - carry the platformer from aside (source of truth) to Fruit-Jump (public release).
#
#   tools/sync-release.sh --check     report what would change, change nothing
#   tools/sync-release.sh             do it
#
# Kinger's workflow, 2026-10-01, verbatim: "the real one is the one on aside right now, the main. all editing
# will be there, and fruit jump repo will be updated weekly and released to the public for them to play."
#
# Why a script rather than a habit. The two copies diverged far enough that the tutorial existed on only one
# side, CharacterConfig and CustomizeScreen on only one side, and a playtest bug got fixed in the copy the
# player was not using. That was me working in the wrong repo twice in one hour.
#
# It is not a straight copy. aside's module is package aside.games.fruitjump (and .engine), the release is
# package tropical (and .engine), so packages and imports are rewritten on the way. Anything that still
# references aside.* afterwards is reported rather than silently shipped - that is exactly what produced ten
# compile errors in the FNAF sync when the imports did not travel.
set -euo pipefail

SRC_REPO="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$SRC_REPO/src/main/java/aside/games/fruitjump"
DEST_REPO="${DEST_REPO:-$(cd "$SRC_REPO/../Fruit-Jump" 2>/dev/null && pwd || true)}"
CHECK=0
[ "${1:-}" = "--check" ] && CHECK=1

if [ -z "$DEST_REPO" ] || [ ! -d "$DEST_REPO" ]; then
  echo "sync-release: no Fruit-Jump checkout found (set DEST_REPO=/path/to/Fruit-Jump)" >&2
  exit 2
fi
DST="$DEST_REPO/src/main/java/tropical"

echo "source: $SRC"
echo "target: $DST"
[ "$CHECK" = 1 ] && echo "MODE:   check only, nothing will be written"
echo "---"

copied=0; same=0; newer=0; wiring=0
for f in $(cd "$SRC" && find . -name '*.java' | sort); do
  src="$SRC/$f"; dst="$DST/$f"
  mkdir -p "$(dirname "$dst")"
  # rewrite the package and its imports on the way across
  tmp="$(mktemp)"
  sed -e 's/^package aside\.games\.fruitjump/package tropical/' \
      -e 's/\baside\.games\.fruitjump\./tropical./g' \
      -e 's/\baside\.games\.fruitjump\b/tropical/g' "$src" > "$tmp"
  # A file that imports aside.* cannot compile in the release repo: the engine travels, the wiring does not.
  # Reported and skipped rather than copied, because shipping it would break the release build - and that is
  # what happened in the FNAF sync when imports were assumed to travel.
  if grep -qE '^import aside\.(ui|game)\.' "$src"; then
    echo "  WIRING   ${f#./}  (imports aside.* - needs a release equivalent, not a copy)"
    wiring=$((wiring+1))
    rm -f "$tmp"
    continue
  fi
  if [ ! -f "$dst" ]; then
    echo "  NEW      ${f#./}"; copied=$((copied+1))
    [ "$CHECK" = 1 ] || cp "$tmp" "$dst"
  elif ! diff -q "$tmp" "$dst" >/dev/null; then
    n=$(diff "$tmp" "$dst" | grep -c '^[<>]' || true)
    echo "  DIFFERS  ${f#./}  ($n lines)"; newer=$((newer+1))
    [ "$CHECK" = 1 ] || cp "$tmp" "$dst"
  else
    same=$((same+1))
  fi
  rm -f "$tmp"
done

echo "---"
echo "new: $copied   differing: $newer   identical: $same   wiring: $wiring"

# Anything still referencing aside.* cannot compile in the release repo. Reported, never shipped quietly.
left=$(grep -rl "aside\." "$DST" 2>/dev/null | head -5 || true)
if [ -n "$left" ]; then
  echo
  echo "STILL REFERENCING aside.* - these will not compile in the release repo:"
  echo "$left" | sed 's/^/  /'
  echo "  (the UI and engine classes they need either have release equivalents or must be stubbed)"
fi
