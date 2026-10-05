#!/usr/bin/env bash
#
# make-bundle.sh - turn a jar into something a stranger can download and run.
#
#   tools/make-bundle.sh <name> <jar> [readme]
#
# Produces /root/bundle/<name>/ and /root/<name>-linux-x64.tar.gz, then VERIFIES it.
#
# WHY THIS EXISTS. On 2026-10-05 the weekly release task asked for the games "packaged to include java and javafx
# so anyone new can just download and immediately play", and I built the same thing by hand twice in one hour -
# once for the platformer and once for the engine. The second time is the signal: a process done twice by hand is
# a process that will be done wrong the third time.
#
# WHAT A BUNDLE IS, and why it is this shape. jlink CANNOT absorb JavaFX on this machine, because the SDK ships
# lib/ and no jmods. So:
#
#   <name>/
#     <name>.sh          a launcher that sets the module path
#     <name>.jar         the game
#     runtime/           jlink runtime: java.base, java.desktop, java.logging, java.xml, jdk.unsupported
#     javafx/            the JavaFX modules, PRUNED to what a game uses
#     README.txt
#
# PRUNING IS MOST OF THE SAVING. The full JavaFX SDK is 142 MB and most of it is libjfxwebkit.so and the ffmpeg
# plugins, which neither game touches. Dropping web, fxml, swt, the two incubator jars, libjfxwebkit.so and the
# unneeded libavplugin-* takes it to 31 MB.
#
# --enable-native-access IS NOT OPTIONAL. JDK 27 warns that "Restricted methods will be blocked in a future
# release" without it. A bundle that works today and breaks on the next runtime is not a bundle.
#
# THE VERIFICATION IS THE POINT. `env -i PATH=/usr/bin:/bin` clears Java off the PATH entirely, and the bundle is
# extracted into an empty directory first. Anything less is testing this machine, not the download.
set -u

NAME="${1:-}"; JAR="${2:-}"; README="${3:-}"
# NO MAIN-CLASS ARGUMENT. The first version took one, never used it, and a failure injection that set it to a
# class that does not exist came back VERIFIED - because the launcher runs `-jar`, which reads the jar's own
# manifest and ignores anything on the command line. An argument that is accepted and ignored is worse than no
# argument: it looks like a knob.
if [ -z "$NAME" ] || [ -z "$JAR" ]; then
  echo "usage: tools/make-bundle.sh <name> <jar> [readme]" >&2; exit 2
fi
[ -f "$JAR" ] || { echo "make-bundle: no such jar: $JAR" >&2; exit 2; }

JDK="${JDK:-/root/jdk-27+35}"
FXSRC="${FXSRC:-/root/javafx-sdk-27/lib}"
OUT="${OUT:-/root/bundle}"
CACHE="${CACHE:-/root/bundle-cache}"
[ -x "$JDK/bin/jlink" ] || { echo "make-bundle: no jlink at $JDK" >&2; exit 2; }
[ -d "$FXSRC" ] || { echo "make-bundle: no JavaFX at $FXSRC" >&2; exit 2; }

# The runtime is identical for every bundle, so it is built once and copied. ~60 MB, a few seconds.
if [ ! -d "$CACHE/runtime" ]; then
  echo "building the jlink runtime once into $CACHE ..."
  mkdir -p "$CACHE"
  "$JDK/bin/jlink" --add-modules java.base,java.desktop,java.logging,java.xml,jdk.unsupported \
    --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$CACHE/runtime" || exit 1
fi

B="$OUT/$NAME"
rm -rf "$B"; mkdir -p "$B"
cp -r "$CACHE/runtime" "$B/runtime"
mkdir -p "$B/javafx"
cp "$FXSRC"/javafx.base.jar "$FXSRC"/javafx.controls.jar "$FXSRC"/javafx.graphics.jar \
   "$FXSRC"/javafx.media.jar "$FXSRC"/javafx.swing.jar "$B/javafx/" 2>/dev/null
# the natives that go with those modules, and not the ones that do not
for so in libglass.so libglassgtk3.so libjavafx_font.so libjavafx_font_freetype.so libjavafx_font_pango.so \
          libjavafx_iio.so libjfxmedia.so libprism_common.so libprism_es2.so libprism_sw.so libfxplugins.so \
          libgstreamer-lite.so libavplugin-56.so libavplugin-ffmpeg-56.so; do
  [ -f "$FXSRC/$so" ] && cp "$FXSRC/$so" "$B/javafx/"
done
cp "$JAR" "$B/$NAME.jar"

cat > "$B/$NAME.sh" <<LAUNCHER
#!/usr/bin/env bash
# $NAME
#
# Everything needed is in this folder: a Java runtime in ./runtime and the JavaFX modules in ./javafx. You do not
# need Java installed. Extract and run this file.
cd "\$(dirname "\$0")" || exit 1
exec ./runtime/bin/java \\
  --module-path ./javafx \\
  --add-modules javafx.controls,javafx.graphics,javafx.media --enable-native-access=javafx.graphics,javafx.media \\
  -jar $NAME.jar "\$@"
LAUNCHER
chmod +x "$B/$NAME.sh"
[ -n "$README" ] && [ -f "$README" ] && cp "$README" "$B/README.txt"

TAR="/root/$NAME-linux-x64.tar.gz"
tar czf "$TAR" -C "$OUT" "$NAME" 2>/dev/null
printf '  %-10s %s  (%.0f MB compressed, %.0f MB extracted)\n' "$NAME" "$TAR" \
  "$(du -k "$TAR" | cut -f1 | awk '{print $1/1024}')" "$(du -sk "$B" | cut -f1 | awk '{print $1/1024}')"

# --- VERIFY: extract into an empty directory, take Java off the PATH, run it -----------------------------
V=$(mktemp -d); trap 'rm -rf "$V"' EXIT
tar xzf "$TAR" -C "$V" || { echo "  FAIL: the tarball would not extract"; exit 1; }
D=":${DISPLAY_NUM:-99}"
if ! xdpyinfo -display "$D" >/dev/null 2>&1; then
  # WAIT FOR IT, do not just background it and hope. The first version wrote `Xvfb ... & sleep 3` inside a `||`,
  # which backgrounds the whole compound command - so the sleep did not wait and the verification ran against a
  # dead display and reported a JavaFX crash. The bundle was fine; the harness was lying.
  Xvfb "$D" -screen 0 1400x900x24 >/dev/null 2>&1 &
  for _ in $(seq 1 40); do xdpyinfo -display "$D" >/dev/null 2>&1 && break; sleep 0.25; done
fi
xdpyinfo -display "$D" >/dev/null 2>&1 || { echo "  cannot verify: no display on $D, so the run would fail for a reason that is not the bundle"; exit 2; }
env -i PATH=/usr/bin:/bin HOME=/root DISPLAY="$D" timeout 25 "$V/$NAME/$NAME.sh" > "$V/log" 2>&1 &
PID=$!
sleep 15
if pgrep -f "$NAME.jar" >/dev/null; then
  echo "  verified: runs from an empty directory with no Java on the PATH"
  pkill -f "$NAME.jar" 2>/dev/null
  exit 0
fi
echo "  FAIL: it did not stay up. Log:"
sed 's/^/    /' "$V/log" | head -12
exit 1
