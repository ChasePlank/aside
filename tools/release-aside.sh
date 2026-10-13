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

  Write the notes FIRST and name the files exactly as they are attached. The notes guard reads the draft, so a name
  attached to nothing stops the release here rather than on the download page.
EOF
