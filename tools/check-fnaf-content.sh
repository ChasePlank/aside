#!/usr/bin/env bash
#
# check-fnaf-content.sh - compare the FNAF screens between aside and the standalone, IGNORING the framework.
#
#   tools/check-fnaf-content.sh [path-to-the-standalone]
#
# WHY THIS EXISTS, AND WHY check-fnaf-match.sh IS NOT ENOUGH.
#
# check-fnaf-match.sh compares the ENGINE files byte-for-byte and the SCREENS by marker. It has to compare the
# screens by marker because they genuinely differ in framework: aside's screens extend aside.ui.UiScreen and use
# UiManager and Audio, and the standalone's extend fnaf.Screen and carry their own Canvas, GraphicsContext and
# StackPane, because there is no aside.ui in the standalone.
#
# That reasoning is sound and it was recorded as the reason screens cannot be compared cheaply. But "we cannot
# compare this cheaply" is not "there is nothing to compare", and content drift in the screens has gone undetected
# before. So this is the proxy: normalise the KNOWN framework differences away, then diff what is left.
#
# The normalisation is deliberately explicit rather than clever. Every substitution here is one that was verified
# by diffing the two smallest screens, where the ONLY differences were these:
#
#   Assets    "fnaf/images/"            vs "images/"           resource root differs with the package
#   Progress  Games.saveDir("fnaf")     vs Paths.get(user.dir)  no Games class in the standalone
#
# A pair that differs by 2 lines is framework. A pair that differs by more needs looking at, and this says how
# much more.
set -u
STANDALONE="${1:-../fnaf2}"
cd "$(dirname "$0")/.." || exit 2

A_DIR=src/main/java/aside/games/fnaf
S_DIR="$STANDALONE/src/main/java/fnaf"
[ -d "$A_DIR" ] || { echo "no $A_DIR" >&2; exit 2; }
[ -d "$S_DIR" ] || { echo "no $S_DIR (pass the standalone path)" >&2; exit 2; }

norm() {
  sed -E '
    /^(package|import) /d
    s/aside\.games\.fnaf/fnaf/g
    s/aside\.ui\.//g
    s/UiScreen/Screen/g
    s/UiManager/Manager/g
    s/extends Screen/extends Screen/g
    s/"fnaf\/images\/"/"images\/"/g
    s/Games\.saveDir\("fnaf"\)/SAVE_DIR/g
    s/Paths\.get\(System\.getProperty\("user\.dir"\), "fnaf-progress\.txt"\)/SAVE_DIR/g
    # THE CANVAS PLUMBING, learned by reading PauseScreen line by line after the first run flagged 21 lines and
    # every single one turned out to be framework. The standalone carries its own Canvas/GraphicsContext/StackPane
    # and wires them to the window itself; aside inherits all of that from UiScreen. Same behaviour, different
    # plumbing - so it comes out before the comparison, and what is left is content.
    # ANY declaration of the canvas plumbing, not just `private final`. The standalone declares canvas, gc, root
    # and timer as plain package-private fields; aside inherits every one of them from UiScreen, so they are
    # declared in one and not the other and the comparison read that as 84 lines of difference.
    /^[[:space:]]*(private |public |protected )?(final )?(Canvas|GraphicsContext|StackPane|AnimationTimer)[[:space:]]+(canvas|gc|root|timer);/d
    /^[[:space:]]*(private |public |protected )?(final )?long lastPulse/d
    s/canvas = new Canvas\(W, H\);//g
    s/gc = canvas\.getGraphicsContext2D\(\);//g
    s/root = new StackPane\(canvas\);//g
    s/canvas\.setManaged\(false\);//g
    s/GameScreen\.fitToWindow\(root, canvas\);//g
    /@Override public Parent getRoot\(\) \{ return root; \}/d
    s/ScreenManager manager/Manager ui/g
    s/\bmanager\b/ui/g
    s/public void tick\(\)/public void tick(double dt)/g
    s/[[:space:]]+$//
    # BLANK LINES LAST. Stripping them first meant every line this normalises away punched a gap that then read
    # as a difference - PauseScreen came back at 5 lines and all five were empty. An artefact of the instrument,
    # reported as a fault in the thing measured.
    /^[[:space:]]*$/d
  ' "$1"
}

total=0; worst=0; worstname=""
for f in "$A_DIR"/*.java; do
  n=$(basename "$f")
  s="$S_DIR/$n"
  if [ ! -f "$s" ]; then
    printf '  %-18s only in aside\n' "${n%.java}"
    continue
  fi
  norm "$f" > /tmp/nm-a.txt
  norm "$s" > /tmp/nm-s.txt
  d=$(diff /tmp/nm-a.txt /tmp/nm-s.txt | grep -cE '^[<>]' || true)
  total=$((total + d))
  if [ "$d" -gt "$worst" ]; then worst="$d"; worstname="${n%.java}"; fi
  if [ "$d" -eq 0 ]; then printf '  %-18s same\n' "${n%.java}"
  else printf '  %-18s %s line(s)\n' "${n%.java}" "$d"; fi
done

echo
echo "=== $total differing line(s) across the screens after normalising the framework; worst is $worstname at $worst ==="
echo "A pair at 0-4 is framework. Anything higher is content and should be read, not assumed."
