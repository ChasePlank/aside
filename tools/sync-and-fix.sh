#!/usr/bin/env bash
#
# sync-and-fix.sh - merge origin/main, and regenerate the frames the merge made stale.
#
#   tools/sync-and-fix.sh
#
# WHY THIS EXISTS. The contact-sheet gate failed after a merge FOUR TIMES on 2026-10-05, and every time the fix was
# the same: run regenerate-frames.sh, commit docs/frames, carry on. The frames are a SHARED DERIVED ARTIFACT - two
# agents commit to this repo, the generator reads the game code, and a merge that brings someone else's game change
# makes every frame of it stale. The gate is right to fail; the work of fixing it was just never attached to the
# step that causes it.
#
# Record once as a note. Record four times and build it into the path.
set -e
cd "$(dirname "$0")/.." || exit 2

git fetch -q origin
behind=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo 0)
if [ "$behind" != "0" ]; then
  echo "merging $behind commit(s) from origin/main"
  git merge origin/main --no-edit
else
  echo "already up to date with origin/main"
fi

# The frames are derived from the game code, so anything that changed the code changed them.
# KEEP THE OUTPUT. This used to run the regeneration with `>/dev/null 2>&1` and then blame the failure on "no
# display?" - which hid the callee's own diagnosis. regenerate-frames.sh now distinguishes a missing display from a
# refused write and says which, and a caller that swallows that is a caller that cannot be told what is wrong.
FRAMES_LOG=/tmp/sync-frames.log
if DISPLAY="${DISPLAY:-:99}" tools/regenerate-frames.sh >"$FRAMES_LOG" 2>&1; then
  if ! git diff --quiet -- docs/frames 2>/dev/null; then
    git add docs/frames
    git commit -q -m "Regenerate the frames the merge made stale" -- docs/frames
    echo "frames regenerated and committed"
  else
    echo "frames still current"
  fi
else
  echo "NOTE: could not regenerate frames. What it said:"
  tail -5 "$FRAMES_LOG" | sed 's/^/    /'
  echo "  The contact-sheet gate will fail until this is fixed."
fi
