#!/usr/bin/env bash
#
# release.sh - do every step of a release except the one that publishes.
#
#   tools/release.sh <version>          e.g. tools/release.sh 1.15
#
# WHY THIS EXISTS. The release routine is six steps across two repositories, it has been done by hand three times
# (1.12, 1.13, 1.14), and EVERY ONE OF THOSE STEPS ALREADY HAS A GUARD. The problem is not that the guards are
# missing. It is that this week's recurring finding - four times over - is a check that existed and was never run:
# the style-class check was in the engine and the bug happened in the release; the audio check printed a count and
# exited 0; the release-notes check did not exist until eleven releases had named files that were not attached.
# A guard that depends on somebody remembering to run it is a guard that will not run.
#
# SO THIS RUNS THEM ALL, IN ORDER, AND STOPS AT THE FIRST FAILURE. It does not publish: publishing needs a
# decision, and a script that can publish is a script that can publish by accident. It prints the command instead.
#
# WHAT IT CANNOT VERIFY, said out loud rather than implied: the Windows launcher. There is no Windows here. The
# bundle tool says so itself, and this passes that on.
set -u

VERSION="${1:-}"
[ -n "$VERSION" ] || { echo "usage: tools/release.sh <version>   e.g. 1.15" >&2; exit 2; }

HERE="$(cd "$(dirname "$0")/.." && pwd)"          # the engine repository
TP="${TP:-$(cd "$HERE/../tp" 2>/dev/null && pwd)}"  # the release repository, resolved - the printed publish
                                                   # command must not contain a ".." for someone to copy
[ -d "$TP" ] || { echo "release: no release repository at $TP - set TP=" >&2; exit 2; }

step() { printf '\n=== %s ===\n' "$1"; }
die()  { printf '\nSTOPPED: %s\n' "$1" >&2; exit 1; }

step "1/6  does anything need publishing?"
"$HERE/tools/release-status.sh" || true

step "2/6  the release repository's own gate"
( cd "$TP" && DISPLAY="${DISPLAY:-:99}" tools/run-suites.sh ) || die "the release gate failed - fix it before building anything"

step "3/6  rebuild the jar from the current source"
( cd "$TP" && tools/make-jar.sh ) || die "make-jar failed"
( cd "$TP" && [ -f tropical-punch.jar.new ] && mv tropical-punch.jar.new tropical-punch.jar )
( cd "$TP" && tools/check-jar-current.sh ) || die "the jar does not match the source after a rebuild - that is a bug in make-jar"

step "4/6  build both bundles"
"$HERE/tools/make-bundle.sh"     "holdfast-$VERSION" "$TP/tropical-punch.jar" || die "the Linux bundle failed"
"$HERE/tools/make-bundle-win.sh" "holdfast-$VERSION" "$TP/tropical-punch.jar" || die "the Windows bundle failed"

step "5/6  is the artifact actually different from the last one?"
"$HERE/tools/artifact-changed.sh" ChasePlank/Fruit-Jump "/root/holdfast-$VERSION-linux-x64.tar.gz" \
  || die "this artifact is byte-identical to the published one - there is nothing to release"

step "6/6  do the release notes name files that exist?"
( cd "$TP" && tools/check-release-notes.sh ) || die "the notes name a file attached to nothing - fix them before publishing"

# AND THE DRAFT, WHICH IS THE ONE THAT MATTERS. The check above reads PUBLISHED releases, so the notes about to be
# attached were the only ones never verified - on 2026-10-07 the whole release passed and 1.15's notes were not
# among the fifteen it checked. The publish command below uses those notes. The artifacts are named here because a
# draft has no release to read them from.
( cd "$TP" && tools/check-release-notes.sh --draft "docs/release-notes-$VERSION.md" \
    "holdfast-$VERSION-windows-x64.zip" "holdfast-$VERSION-linux-x64.tar.gz" "tropical-punch.jar" ) \
  || die "the DRAFT notes name a file this release would not attach - fix them before publishing"

cat <<EOF

=== everything that can be checked here has passed ===

NOT VERIFIED: the Windows launcher. There is no Windows on this machine, so the .bat is the one unproven part of
the bundle. The bundle tool says this too.

TO PUBLISH, which is a decision and not a step:

  gh release create $VERSION -R ChasePlank/Fruit-Jump \\
     --title "Holdfast $VERSION" \\
     --notes-file $TP/docs/release-notes-$VERSION.md \\
     /root/holdfast-$VERSION-windows-x64.zip /root/holdfast-$VERSION-linux-x64.tar.gz $TP/tropical-punch.jar

  Write the notes FIRST and name the files exactly as they are attached. Eleven of fifteen past releases named a
  file that was not attached, and gave copy-pasteable commands built on it.
EOF
