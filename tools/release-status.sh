#!/usr/bin/env bash
#
# Does any of these repositories need a new release?
#
#   tools/release-status.sh
#
# WHY THIS IS A SCRIPT AND NOT A HABIT. The routine was written down as a paragraph - "check the release when the
# week turns; if main is ahead by anything a player would notice, publish a new one" - and a paragraph is read
# after the mistake, not before it. This is the same lesson as the other tools here.
#
# THE QUESTION IS NOT "IS MAIN AHEAD". It is "would a player notice", and those are different answers. On the day
# this was written, Fruit-Jump and wake were each dozens of commits ahead of their releases and needed nothing:
# every one of those commits was documentation or tooling, and the source a player downloads had not moved.
#
# So each repository declares what a player actually gets, and this compares THAT range.
set -u

cd "$(dirname "$0")/.." || exit 2
WORKSPACE="${WORKSPACE:-$(cd .. && pwd)}"

command -v gh >/dev/null 2>&1 || { echo "no gh on PATH - needed to read the releases" >&2; exit 2; }

# repo:local-dir:what-a-player-downloads
REPOS=(
  "ChasePlank/aside:aside:web/aside.html"
  "ChasePlank/Fruit-Jump:tp:src/main/java:tropical-punch.jar"
  "ChasePlank/wake:wake:index.html"
)

need=0
for entry in "${REPOS[@]}"; do
  IFS=':' read -r repo dir art1 art2 <<< "$entry"
  printf '%-22s ' "$repo"
  if [ ! -d "$WORKSPACE/$dir/.git" ]; then
    echo "no checkout at $WORKSPACE/$dir"; continue
  fi
  # TAG= overrides the latest release, which is how the "something changed" path gets tested without waiting for
  # something to change.
  tag="${TAG:-$(gh release list -R "$repo" --limit 1 --json tagName --jq '.[0].tagName' 2>/dev/null)}"
  if [ -z "$tag" ]; then
    echo "no release yet"; need=1; continue
  fi
  cd "$WORKSPACE/$dir" || continue
  git fetch -q origin --tags 2>/dev/null
  # A tag that is not in THIS repository is not a release that is unchanged. The first version of this compared
  # against a tag that did not exist, got an empty diff, and reported "the download is unchanged" - which is the
  # reassuring answer for the wrong reason.
  if ! git rev-parse --verify --quiet "$tag" >/dev/null; then
    echo "$tag is not a tag in this repository - nothing to compare"
    continue
  fi
  behind=$(git rev-list --count "$tag..origin/main" 2>/dev/null || echo '?')
  # The artifacts, and nothing else. An empty diff means a player would get the same thing.
  changed=$(git diff --stat "$tag..origin/main" -- "$art1" ${art2:+"$art2"} 2>/dev/null | tail -1)
  if [ -z "$changed" ]; then
    printf '%-8s %3s commit(s) behind, but the download is unchanged\n' "$tag" "$behind"
  else
    printf '%-8s %3s commit(s) behind, and the download CHANGED: %s\n' "$tag" "$behind" "$changed"
    need=1
  fi
done

echo
if [ $need -eq 0 ]; then
  echo "=== no release needs publishing ==="
else
  echo "=== something needs publishing - a player would get something different ==="
  echo
  echo "BEFORE UPLOADING, CHECK THE ARTIFACT ITSELF:"
  echo "  tools/artifact-changed.sh <owner/repo> <local-artifact>"
  echo "  This script compares the SOURCE against the tag. That one compares the BYTES you are about to publish"
  echo "  against the bytes already in the latest release, and refuses when they are identical. On 2026-10-04 a"
  echo "  release went out byte-identical to the previous one, because nothing did that comparison."
fi
exit $need
