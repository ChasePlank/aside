#!/usr/bin/env bash
#
# make-bundle-win.sh - a jar into a Windows download that runs with nothing installed.
#
#   tools/make-bundle-win.sh <name> <jar> [readme]
#
# WHY. make-bundle.sh builds the Linux bundle. On 2026-10-05 I built the WINDOWS one by hand twice in an hour -
# once for the platformer, once for the engine - and the person who asked for a download-and-play build uses
# Windows, so the Linux-only releases did not help him.
#
# THERE IS NO WINDOWS MACHINE HERE, so this is a CROSS-BUILD:
#
#   * a JDK 21 for LINUX supplies `jlink` (it must match the jmods' major version - jlink 27 refuses JDK 21 jmods
#     with "cannot find the build signature")
#   * a JDK 21 for WINDOWS supplies the platform jmods
#   * a JavaFX SDK for WINDOWS supplies the jars and the .dll natives
#
# Pruning takes the runtime from 146 MB to 47 MB: java.base, java.desktop, java.logging, java.xml, jdk.unsupported
# is everything the game uses.
#
# WHAT IT CANNOT DO IS RUN IT. There is no Windows here, so the verification checks the contents - the runtime
# reports the modules the launcher asks for, the JavaFX jars and natives are present, and the jar is byte-identical
# to the one verified on Linux - and SAYS that the launcher itself is untested.

# WHAT IT LEAVES BEHIND, AND WHY THAT MATTERS. This tool is the expensive one: it caches a JDK 21 for Linux, a
# JDK 21 for Windows and a Windows JavaFX SDK under /root/win-cache (519 MB, by far the largest thing either
# bundle tool creates), and it downloads more JDKs to /root as loose zips. None of it is cleaned up. On
# 2026-10-06 the sandbox hit 91 per cent full - the point at which memory commits start failing - and this cache
# was most of it. It is regenerable and safe to delete; the cost is a re-download of about 300 MB.

set -u

NAME="${1:-}"; JAR="${2:-}"; README="${3:-}"
if [ -z "$NAME" ] || [ -z "$JAR" ]; then
  echo "usage: tools/make-bundle-win.sh <name> <jar> [readme]" >&2; exit 2
fi
[ -f "$JAR" ] || { echo "make-bundle-win: no such jar: $JAR" >&2; exit 2; }
command -v unzip >/dev/null || { echo "make-bundle-win: needs unzip" >&2; exit 2; }
command -v curl  >/dev/null || { echo "make-bundle-win: needs curl" >&2; exit 2; }

CACHE="${CACHE:-/root/win-cache}"
OUT="${OUT:-/root/bundle}"
ADOPT="https://api.adoptium.net/v3/binary/latest/21/ga"
mkdir -p "$CACHE"

# Fetch each piece once, then reuse. ~450 MB total, so a rebuild is seconds rather than minutes.
if [ ! -d "$CACHE/jdk-linux" ]; then
  echo "fetching a JDK 21 for linux (jlink) ..."
  curl -sL -o "$CACHE/jdk21-linux.tar.gz" "$ADOPT/linux/x64/jdk/hotspot/normal/eclipse" || exit 1
  mkdir -p "$CACHE/jdk-linux" && tar xzf "$CACHE/jdk21-linux.tar.gz" -C "$CACHE/jdk-linux" --strip-components=1 || exit 1
  rm -f "$CACHE/jdk21-linux.tar.gz"
fi
if [ ! -d "$CACHE/jdk-win/jmods" ]; then
  echo "fetching a JDK 21 for windows (jmods) ..."
  rm -rf "$CACHE/jdk-win" && mkdir -p "$CACHE/jdk-win"
  curl -sL -o "$CACHE/jdk21-win.zip" "$ADOPT/windows/x64/jdk/hotspot/normal/eclipse" || exit 1
  unzip -q -o "$CACHE/jdk21-win.zip" -d "$CACHE/jdk-win-tmp" || exit 1
  # the zip has one top-level jdk-... directory; lift it up and throw the rest away
  mv "$CACHE/jdk-win-tmp"/*/jmods "$CACHE/jdk-win/jmods" || exit 1
  rm -rf "$CACHE/jdk-win-tmp" "$CACHE/jdk21-win.zip"
fi
if [ ! -d "$CACHE/fx-win" ]; then
  echo "fetching a JavaFX SDK for windows ..."
  curl -sL -o "$CACHE/fx21-win.zip" "https://download2.gluonhq.com/openjfx/21/openjfx-21_windows-x64_bin-sdk.zip" || exit 1
  unzip -q -o "$CACHE/fx21-win.zip" -d "$CACHE/fx-win-tmp" || exit 1
  mv "$CACHE/fx-win-tmp"/* "$CACHE/fx-win" || exit 1
  rm -rf "$CACHE/fx-win-tmp" "$CACHE/fx21-win.zip"
fi

B="$OUT/$NAME-win"
rm -rf "$B"; mkdir -p "$B/javafx/lib" "$B/javafx/bin"
"$CACHE/jdk-linux/bin/jlink" --module-path "$CACHE/jdk-win/jmods" \
  --add-modules java.base,java.desktop,java.logging,java.xml,jdk.unsupported \
  --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "$B/runtime" || exit 1
[ -f "$B/runtime/bin/java.exe" ] || { echo "make-bundle-win: jlink produced no java.exe" >&2; exit 1; }

FX=$(find "$CACHE/fx-win" -maxdepth 2 -name lib -type d | head -1)
[ -d "$FX" ] || { echo "make-bundle-win: no lib in the JavaFX SDK" >&2; exit 1; }
FXBIN=$(dirname "$FX")/bin
for j in javafx.base.jar javafx.controls.jar javafx.graphics.jar javafx.media.jar javafx.properties; do
  [ -f "$FX/$j" ] && cp "$FX/$j" "$B/javafx/lib/"
done
cp "$FXBIN"/*.dll "$B/javafx/bin/" 2>/dev/null
# the web and media natives are 60 MB of a 78 MB directory and neither game touches them
rm -f "$B/javafx/bin/jfxwebkit.dll" "$B/javafx/bin/glib-lite.dll" \
      "$B/javafx/bin/gstreamer-lite.dll" "$B/javafx/bin/jfxmedia.dll"
cp "$JAR" "$B/$NAME.jar"

cat > "$B/$NAME.bat" <<BAT
@echo off
rem $NAME - everything needed is in this folder: a Java runtime in .\\runtime and the JavaFX modules in .\\javafx.
rem YOU DO NOT NEED JAVA INSTALLED. Double-click this file.
cd /d "%~dp0"
runtime\\bin\\java.exe --module-path javafx\\lib --add-modules javafx.controls,javafx.graphics,javafx.media --enable-native-access=javafx.graphics,javafx.media -Djava.library.path=javafx\\bin -jar $NAME.jar %*
if errorlevel 1 pause
BAT
[ -n "$README" ] && [ -f "$README" ] && cp "$README" "$B/README.txt"

ZIP="/root/$NAME-windows-x64.zip"
rm -f "$ZIP"; (cd "$OUT" && zip -qr "$ZIP" "$NAME-win") || { echo "make-bundle-win: needs zip" >&2; exit 1; }
printf '  %-10s %s  (%.0f MB compressed, %.0f MB extracted)\n' "$NAME" "$ZIP" \
  "$(du -k "$ZIP" | cut -f1 | awk '{print $1/1024}')" "$(du -sk "$B" | cut -f1 | awk '{print $1/1024}')"

# --- VERIFY WHAT CAN BE VERIFIED. See the header: the launcher itself cannot be run from here. ---
fail=0
grep -q "java.desktop" "$B/runtime/release" 2>/dev/null || { echo "  FAIL: the runtime is missing java.desktop"; fail=1; }
for m in javafx.controls javafx.graphics javafx.media; do
  [ -f "$B/javafx/lib/$m.jar" ] || { echo "  FAIL: missing $m.jar"; fail=1; }
done
for d in glass.dll prism_d3d.dll prism_sw.dll javafx_font.dll; do
  [ -f "$B/javafx/bin/$d" ] || { echo "  FAIL: missing native $d"; fail=1; }
done
[ -f "$B/$NAME.jar" ] || { echo "  FAIL: the jar did not travel"; fail=1; }
if [ "$fail" = "0" ]; then
  echo "  contents verified: runtime, JavaFX jars and Windows natives all present"
  echo "  NOTE: the Windows launcher is UNTESTED - there is no Windows here. The .bat is the only unproven part."
fi
exit "$fail"
