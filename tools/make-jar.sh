#!/usr/bin/env bash
#
# make-jar.sh - build the one file a release ships.
#
#   tools/make-jar.sh [out-dir]        (default: /root/release)
#
# WHY THIS EXISTS. The v1.11 and v1.12 releases each shipped a jar, and there was NO SCRIPT that made one - it was
# done by hand both times and the result deleted, so the third time would have been a third hand-build from memory.
# Twice by hand is the signal this project uses for "build it into the path", and Rule 17 says a documented command
# must be run before it is repeated. This is that command, and it verifies its own jar at the end rather than
# trusting the exit status of `jar`.
set -u
. "$(dirname "$0")/find-java.sh"
cd "$(dirname "$0")/.." || exit 2

OUT_DIR="${1:-/root/release}"
# OUT_JAR, not JAR: JAR is the PACKER from find-java.sh, and naming the output file JAR too meant this script
# overwrote the tool path with a filename and then tried to execute its own output. The trace was unambiguous -
# "+ JAR=/root/release/aside.jar" followed by running that path as a command.
OUT_JAR="$OUT_DIR/aside.jar"
MAIN=aside.ui.Main

mkdir -p "$OUT_DIR"
CLASSES="$OUT_DIR/classes"
rm -rf "$CLASSES" "$OUT_JAR"

echo "compiling..."
"$JAVAC" --module-path "$FX" --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
    -d "$CLASSES" $(find src/main/java -name '*.java') 2>&1 | grep "error:" && { echo "compile failed"; exit 1; }

# The whole resources tree, not a hand-picked part of it. web/ alone is 6 MB of shelf and every story's art is
# under art/; a jar that carries the code and not the content starts and then shows nothing.
echo "packing..."
cp -r src/main/resources/. "$CLASSES/" 2>/dev/null
for d in stories art audio; do [ -d "$d" ] && cp -r "$d" "$CLASSES/"; done

"$JAR" --create --file "$OUT_JAR" --main-class "$MAIN" -C "$CLASSES" . >/dev/null || {
    echo "jar --create failed" >&2; exit 1; }

# VERIFY, RATHER THAN TRUST THE EXIT STATUS. make-bundle.sh already learned this: a jar whose Main-Class
# names a class that does not exist comes back VERIFIED from `jar`, because nothing checks the name until it runs.
SIZE=$(stat -c%s "$OUT_JAR")
echo "wrote $OUT_JAR ($((SIZE / 1048576)) MB)"
if ! unzip -l "$OUT_JAR" | grep -q "$(echo "$MAIN" | tr '.' '/')\.class"; then
    echo "  the main class $MAIN is NOT in the jar" >&2; exit 1
fi
for need in stories/two-of-everything.aside art/stories/two-of-everything/sprites/nell-neutral.png; do
    unzip -l "$OUT_JAR" | grep -q "$need" || { echo "  missing from the jar: $need" >&2; exit 1; }
done
echo "  main class present, and the newest story and its art are inside"
