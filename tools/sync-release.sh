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

# --- what this script does NOT carry ---------------------------------------
#
# Both of these were found the hard way on 2026-10-02, in one update, and the second one cost the public
# build a soft-lock. They are printed every run rather than written in a comment, because a comment is read
# after the failure.
echo
echo "--- NOT CARRIED BY THIS SCRIPT ---"

# 1. The screens. Seven files import aside.(ui|game).* and are skipped above. That is by design, but it has
#    a consequence the skip line does not state: an engine feature whose OTHER half is a screen arrives
#    half-built, and half-built can be worse than absent. The water arrived with a field, no renderer, no
#    vertical input and a grounded-only jump - so a player who walked into a pool floated at the surface and
#    could never leave. The bats arrived flying and stunning the player, invisible. The splash arrived as
#    particles nothing drew. Three features, one cause.
echo "  1. SCREENS: the $wiring wiring files above are skipped, so an engine feature whose other half is a"
echo "     screen arrives half-built. Check the release's GameplayScreen for anything the engine now"
echo "     PRODUCES that nothing there DRAWS or DRIVES. On 2026-10-02 that was water (a soft-lock), bats"
echo "     (an invisible stun) and the splash particles - and none of them showed up in any test the"
echo "     release had."

# 2. Assets. Only *.java is copied, so anything the engine loads from disk stays behind.
AUDIO_SRC="$SRC_REPO/audio"
if [ -d "$AUDIO_SRC" ]; then
  n=$(find "$AUDIO_SRC" -type f 2>/dev/null | wc -l | tr -d ' ')
  in_release=$(find "$DEST_REPO" -name '*.wav' -o -name '*.mp3' 2>/dev/null | wc -l | tr -d ' ')
  echo "  2. ASSETS: $n files under aside/audio/ are not copied. The release currently holds $in_release"
  echo "     sound files, so it is silent - which is pre-existing, but it means a cue added here will not"
  echo "     be heard there."
fi

echo
echo "Both are the same shape: this script carries CODE, and a feature is not only code."
