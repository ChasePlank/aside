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

# A FIXED TIMESTAMP ON EVERY ENTRY, so that the same source produces the same bytes - ported from the release
# repository's make-jar.sh on 2026-10-13, and MEASURED before it was ported: two consecutive builds of THIS jar from
# identical source came out 06fe5d4315e2de455a9607f2... against 9411fb4a8833e63324822abb..., while every class inside
# was byte-identical.
#
# IT IS NOT COSMETIC. `tools/artifact-changed.sh` compares BYTES and refuses to release an artifact identical to the
# published one - that guard exists because a byte-identical release shipped on 2026-10-04. With a non-reproducible
# jar it can never fire for the library: rebuilding always changes the bytes, so the guard is defeated by building
# again, which is the case it was written for. The release repository's make-jar says the same thing in the same
# words, which is why the fix is a copy rather than a second opinion.
#
# `jar` has no --date on this JDK, so the mtimes are set on the extracted tree instead and `jar` records those.
find "$CLASSES" -exec touch -t 202601010000 {} + 2>/dev/null

"$JAR" --create --file "$OUT_JAR" --main-class "$MAIN" -C "$CLASSES" . >/dev/null || {
    echo "jar --create failed" >&2; exit 1; }

# AND THE TWO ENTRIES `jar` WRITES ITSELF. It stamps META-INF/ and META-INF/MANIFEST.MF with the current time after
# the mtimes above have been set, so they cannot be fixed from outside - the last bytes of the difference between two
# builds of identical source.
python3 "$(dirname "$0")/normalize-jar.py" "$OUT_JAR" >/dev/null || { echo "normalize failed" >&2; exit 1; }

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
