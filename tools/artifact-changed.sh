#!/usr/bin/env bash
#
# artifact-changed.sh - is the thing I am about to publish actually different from the thing already published?
#
#   tools/artifact-changed.sh <owner/repo> <local-file>
#
# Exits 0 if a player would get something different, 1 if the bytes are IDENTICAL, 2 if it could not tell.
#
# WHY THIS EXISTS. On 2026-10-04 I published Aside v1.11 whose artifact was BYTE-IDENTICAL to v1.10. The
# release-status check had said "the download CHANGED", and it was reporting a moment: by the time the artifact
# was uploaded, it matched what v1.10 already held. Nothing compared the two files. The shelf had not moved -
# only contact-sheet frames had - and the shelf does not embed the platformer at all, because it is a desktop
# game and not one of the twenty-three web builds. So a release went out that a player would have gained nothing
# from, and it had to be deleted along with its tag.
#
# A NOTE IS NOT A GUARD. The lesson was written down the moment it happened and the very next release could have
# repeated it, because "remember to hash the artifact" is a thing to remember and this is a thing to run. It is
# four lines of sha256 and it is the one step the publishing flow was missing.
#
# It reads the LATEST release rather than a named one, because the question is always "different from what is
# out there now". GH_REPO or a different tag can be forced with ARTIFACT_TAG.
set -u

REPO="${1:-}"
LOCAL="${2:-}"
[ -z "$REPO" ] || [ -z "$LOCAL" ] && {
  echo "usage: tools/artifact-changed.sh <owner/repo> <local-file>" >&2; exit 2; }
[ -f "$LOCAL" ] || { echo "artifact-changed: no such file: $LOCAL" >&2; exit 2; }
command -v gh >/dev/null 2>&1 || { echo "artifact-changed: no gh on PATH" >&2; exit 2; }

TAG="${ARTIFACT_TAG:-$(gh release view -R "$REPO" --json tagName -q .tagName 2>/dev/null)}"
if [ -z "$TAG" ]; then
  echo "artifact-changed: $REPO has no releases yet, so anything is a change"
  exit 0
fi

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
if ! gh release download "$TAG" -R "$REPO" -D "$TMP" >/dev/null 2>&1; then
  echo "artifact-changed: could not download the assets of $REPO $TAG - NOT a pass" >&2
  exit 2
fi

mine=$(sha256sum "$LOCAL" | cut -d' ' -f1)
# compare against every asset of the release: a release can carry more than one, and the artifact being replaced
# is whichever one has the same NAME as the file being published.
want=$(basename "$LOCAL")
theirs=""
for f in "$TMP"/*; do
  [ -f "$f" ] || continue
  if [ "$(basename "$f")" = "$want" ]; then theirs=$(sha256sum "$f" | cut -d' ' -f1); fi
done
if [ -z "$theirs" ]; then
  # the asset may be renamed on upload (Fruit-Jump uploads tropical-punch.jar as fj18.jar). Fall back to the only
  # asset if there is exactly one, and say so rather than guessing when there are several.
  n=$(find "$TMP" -maxdepth 1 -type f | wc -l)
  if [ "$n" = "1" ]; then
    theirs=$(sha256sum "$(find "$TMP" -maxdepth 1 -type f)" | cut -d' ' -f1)
    echo "  (no asset named $want in $TAG; compared against the release's only asset)"
  else
    echo "artifact-changed: $TAG has $n assets and none is named $want - cannot tell which one this replaces" >&2
    exit 2
  fi
fi

if [ "$mine" = "$theirs" ]; then
  echo "  IDENTICAL to $TAG ($(echo "$mine" | cut -c1-12)) - a player would get nothing new. DO NOT PUBLISH."
  exit 1
fi
echo "  differs from $TAG ($(echo "$theirs" | cut -c1-12) -> $(echo "$mine" | cut -c1-12))"
exit 0
