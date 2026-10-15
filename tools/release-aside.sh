#!/usr/bin/env bash
#
# release-aside.sh - do every step of a LIBRARY release except the one that publishes.
#
#   tools/release-aside.sh v1.15
#
# WHY THIS EXISTS. `release.sh` is the game's release, six steps with a guard on each, and it has existed since
# 2026-10-12. The library's release was done BY HAND for v1.13 and again for v1.14 - the second time is the signal,
# and it is the same signal `tools/make-bundle.sh` opens with: "a process done twice by hand is a process that will be
# done wrong the third time". v1.14's own release notes were written, then the jar, then the bundle, then the artifact
# comparison, then the notes check - in that order, because I worked it out as I went. That order is the thing worth
# keeping, and a checklist in a conversation is not a guard.
#
# IT DOES NOT PUBLISH: publishing needs a decision, and a script that can publish is a script that can publish by
# accident. It prints the command instead, exactly as release.sh does.
#
# WHAT IT CANNOT VERIFY, said out loud: the stories. The gate audits structure and reachability; nothing here can
# tell you whether a story lands.
set -u

VERSION="${1:-}"
[ -n "$VERSION" ] || { echo "usage: tools/release-aside.sh <version>   e.g. v1.15" >&2; exit 2; }

HERE="$(cd "$(dirname "$0")/.." && pwd)"
TP="${TP:-$(cd "$HERE/../tp" 2>/dev/null && pwd)}"     # holds the notes guard, which reads the drafts
JAR=/root/release/aside.jar
BUNDLE=/root/Aside-linux-x64.tar.gz                    # unversioned, as the published releases are: the URL is
NOTES="$HERE/docs/release-notes-$VERSION.md"           # stable and artifact-changed.sh compares the BYTES

# The toolchain, from the one place that looks for it - this script uses $JAVA, $FX and $OUT directly for the shelf,
# and with `set -u` an unsourced variable is not a warning, it is the end of the run. The same sourcing, and the same
# refusal to continue without a JDK, that run-suites.sh does.
. "$(dirname "$0")/find-java.sh" || { echo "release-aside: no usable JDK - see the message above" >&2; exit 2; }

step() { printf '\n=== %s ===\n' "$1"; }
die()  { printf '\nSTOPPED: %s\n' "$1" >&2; exit 1; }

step "1/6  does anything need publishing?"
"$HERE/tools/release-status.sh" || true

step "2/6  the engine repository's gate"
( cd "$HERE" && DISPLAY="${DISPLAY:-:99}" tools/run-suites.sh ) \
  || die "the engine gate failed - fix it before building anything"

step "3/6  the notes exist, and are the ones this release will attach"
# FIRST, because they are the one document the publish command uses and the only one written by hand. The guard
# reads names out of them and refuses a name attached to nothing - it caught that fault fifteen times over, and it
# could not see `.html` at all until 2026-10-13, which is the extension THIS release attaches.
[ -f "$NOTES" ] || die "no notes at $NOTES - write them before building anything"
( cd "$TP" && tools/check-release-notes.sh --draft "$NOTES" aside.html Aside-linux-x64.tar.gz ) \
  || die "the DRAFT notes name a file this release would not attach - fix them before publishing"

# AND THE FIGURES IN THE PROSE, WHICH NOTHING READ.
#
# The guard above verifies that the files the notes NAME exist. IT DOES NOT READ A SINGLE NUMBER IN THEM. Measured
# 2026-10-15, by fault injection: it passed a draft whose shelf size was wrong by one byte AND whose story total was
# wrong by one - and both were wrong in the real draft, because a story landed after the notes were written.
# THE NOTES ARE ATTACHED TO THE ARTIFACT, so they have to describe it. Two figures here can be checked mechanically
# and are: the shelf's size in bytes, and the number of stories in the library.
# WHICH ARTIFACT THE FIGURES ARE COMPARED AGAINST, AND THE FIRST VERSION OF THIS GOT IT WRONG WITHIN THE HOUR.
#
# It read the WORKING COPY. That is right while a release is being prepared, and WRONG THE MOMENT IT IS PUBLISHED:
# the notes then describe the TAGGED artifact, and the next story moves the working copy on - so the check would fail
# for every release after its own, for a reason that is not a fault. Measured 2026-10-15: v1.18 was published, and
# the twenty-ninth story would have made this refuse the v1.18 notes immediately.
#
# So: the tag if it exists, the working copy if it does not.
if git -C "$HERE" rev-parse -q --verify "refs/tags/$VERSION" >/dev/null 2>&1; then
  SHELF_BYTES=$(git -C "$HERE" show "$VERSION:web/aside.html" | wc -c)
  STORY_COUNT=$(git -C "$HERE" ls-tree -r --name-only "$VERSION" stories/ | grep -c '\.aside$')
  echo "  (comparing against the PUBLISHED $VERSION, not the working copy)"
else
  SHELF_BYTES=$(stat -c%s "$HERE/web/aside.html" 2>/dev/null)
  STORY_COUNT=$(ls "$HERE"/stories/*.aside 2>/dev/null | wc -l)
fi
if [ -n "$SHELF_BYTES" ] && [ "$SHELF_BYTES" != "0" ]; then
  shelf_bytes="$SHELF_BYTES"
  shelf_want=$(printf '%s' "$shelf_bytes" | sed -E ':a; s/\B[0-9]{3}\b/,&/; ta')
  grep -q "$shelf_want bytes" "$NOTES" \
    || die "the notes never state the shelf's size ($shelf_want bytes) - they are attached to the artifact"
fi
story_count="$STORY_COUNT"
python3 - "$NOTES" "$story_count" <<'PY' || die "the notes never state the library's story count ($story_count)"
import sys
notes, n = sys.argv[1], int(sys.argv[2])
tens = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"]
ones = ["", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]
w = {tens[t] if o == 0 else "%s-%s" % (tens[t], ones[o]): t * 10 + o
     for t in range(2, 10) for o in range(10)}
words = [k for k, v in w.items() if v == n]
text = open(notes).read()
if not any("%s stories" % k in text for k in words):
    sys.exit("no '%s stories'" % (words[0] if words else str(n)))
PY

step "4/6  the jar, and the shelf the library ships"
( cd "$HERE" && tools/make-jar.sh ) || die "make-jar failed"
[ -f "$JAR" ] || die "make-jar did not write $JAR"
# THE SHELF IS DERIVED, SO IT IS REGENERATED AND THEN DIFFED. `web/aside.html` inlines every story that has a web
# build; the suite compares the checked-in copy against a fresh generation, so this cannot drift quietly - but the
# release is the moment it would matter most, and one command proves the file being published is the file the tree
# describes.
( cd "$HERE" && DISPLAY="${DISPLAY:-:99}" "$JAVA" --module-path "$FX" \
    --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -cp "$OUT:src/main/resources" aside.game.PhoneShelf ) || die "the shelf generator failed"
if [ -n "$( cd "$HERE" && git status --porcelain web/aside.html )" ]; then
  ( cd "$HERE" && git diff --stat web/aside.html )
  die "regenerating the shelf CHANGED it - commit the shelf before releasing"
fi
echo "  the shelf is current: web/aside.html regenerates to no diff"

step "5/6  the bundle, and is it actually different from the published one?"
( cd "$HERE" && tools/make-bundle.sh Aside "$JAR" ) || die "the Linux bundle failed"
"$HERE/tools/artifact-changed.sh" ChasePlank/aside "$BUNDLE" \
  || die "this artifact is byte-identical to the published one - there is nothing to release"

step "6/6  the notes name files that exist, in the PUBLISHED releases too"
( cd "$TP" && tools/check-release-notes.sh ) \
  || die "a published release names a file attached to nothing - fix that before adding another"

cat <<EOF

=== everything that can be checked here has passed ===

NOT VERIFIED: the stories. The gate audits structure and reachability - every branch walked, no choice leading
nowhere - and none of that can tell you whether a story lands.

TO PUBLISH, which is a decision and not a step:

  gh release create $VERSION -R ChasePlank/aside \\
     --title "Aside $VERSION - <one line about this week>" \\
     --notes-file $NOTES \\
     web/aside.html $BUNDLE

  AND PUSH BEFORE YOU PUBLISH. `gh release create` tags the REMOTE's head, not your working tree, so a notes commit
  that is only local puts the tag one commit behind the notes it attaches - measured on v1.19, where the tag landed at
  the previous commit and the notes file is in the next one. The tag's TREE was still correct (thirty stories, the
  shelf that was built), which is why this is a refinement rather than a fault; what a reader checking out v1.19 would
  not get is the notes themselves.

  Write the notes FIRST and name the files exactly as they are attached. The notes guard reads the draft, so a name
  attached to nothing stops the release here rather than on the download page.
EOF
