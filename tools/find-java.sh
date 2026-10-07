#!/usr/bin/env bash
#
# Source this to get JAVA and FX set, and OUT for a build directory.
#
#   . "$(dirname "$0")/find-java.sh"
#
# WHY THIS IS A FILE. Three shell tools each grew their own copy of "find a JDK": mutate.sh, check-sheets.sh and
# run-suites.sh. They were written at different times, each after a failure that looked like something else - a
# missing compiler reported as COMPILE FAIL, a missing display reported as a stale sheet - and each fix was
# local. That is three copies of a lookup that should be one, and a fourth tool would have made four.
#
# The same failure happened again in JavaScript, to Loona's port traces, which called `execFileSync('java', ...)`
# and died with `spawnSync java ENOENT`. Those use tools/java.mjs, which is this file's other half.
#
#   JAVA   the java binary        (override with JAVA=)
#   FX     the JavaFX lib dir     (override with FX=)
#   OUT    the build directory    (override with OUT=; defaults to out/ or classes/)
#   JAVAC  the compiler            (derived from JAVA)
#   JAR    the packer              (derived from JAVA)
#
# Exits 2 with a message rather than letting a caller misread "no toolchain" as "the thing I was checking failed".

# --- java
#
# A JAVA THAT IS ALREADY SET IS CHECKED, NOT TRUSTED. This used to be a plain
# `if [ -z "${JAVA:-}" ]`, which meant a caller that wrote `JAVA="${JAVA:-java}"`
# BEFORE sourcing this file made the whole lookup a no-op -- and that is exactly
# what tools/regenerate-frames.sh did. Every run died with "exec: java: not
# found", the script never checked, and it reported success for a job it had
# never done.
#
# So a value that is not a runnable java is treated as no value at all, and the
# search runs anyway. The trap is removed rather than documented: a note about
# the ordering is read after the mistake, not before it.
java_works() {
  [ -n "${1:-}" ] || return 1
  case "$1" in
    */*) [ -x "$1" ] ;;
    *)   command -v "$1" >/dev/null 2>&1 ;;
  esac
}
# A JDK, NOT A JRE. EVERY TOOL HERE COMPILES, so the java that matters is one with a javac beside it - and the
# order used to be the opposite: "whatever `java` is on PATH, and only go looking for a real JDK if nothing is on
# PATH at all". On 2026-10-06 installing maven for an unrelated verification pulled in openjdk-17-jre-headless, so
# /usr/bin/java appeared with no javac next to it, find-java picked it, and THE WHOLE GATE STOPPED - on a machine
# with a perfectly good JDK sitting at /root/jdk-27+35. That is not a corner case: a system JRE on PATH is the
# normal state of a normal machine. The search now looks for a JDK first and only falls back to a bare runtime.
java_with_javac() {
  [ -n "${1:-}" ] || return 1
  case "$1" in
    */*) [ -x "$1" ] && [ -x "$(dirname "$1")/javac" ] ;;
    *)   p=$(command -v "$1" 2>/dev/null) && [ -x "$(dirname "$p")/javac" ] ;;
  esac
}
if ! java_with_javac "${JAVA:-}"; then
  JAVA=""
  for c in /root/jdk-*/bin/java /usr/lib/jvm/*/bin/java "$HOME"/jdk*/bin/java; do
    [ -x "$c" ] && [ -x "$(dirname "$c")/javac" ] && { JAVA="$c"; break; }
  done
  # only if there is no JDK anywhere: a runtime that cannot compile is still better than nothing for the tools
  # that only RUN things, and they will say so if it is wrong for them
  if [ -z "$JAVA" ] && java_works "${JAVA_OR_PATH:-}"; then :; fi
  if [ -z "$JAVA" ] && command -v java >/dev/null 2>&1; then JAVA=$(command -v java); fi
fi
if [ -z "${JAVA:-}" ]; then
  echo "find-java: no java found - set JAVA=/path/to/bin/java" >&2
  return 2 2>/dev/null || exit 2
fi

# --- javac and jar
#
# SIBLINGS OF JAVA, and derived from it rather than looked up again. They live next to it in every JDK, and the
# alternative was a FOURTH variable each tool derives for itself - which is the exact failure that created this
# file in the first place: three tools each grew their own "find a JDK". A tool that needs the compiler now sources
# this and has it.
# AND RESOLVE THE BARE CASE. `JAVA` is often just `java` from PATH, and `${java%/java}` strips nothing, so the
# derivation gave `java/javac` - which is not a path. That failure surfaced as "line 34: /root/release/aside.jar:
# No such file or directory" from a *different* script, which is the worst kind: the error names the caller's
# variable and not the lookup that left it empty.
java_bin_dir() {
  case "$JAVA" in
    */*) dirname "$JAVA" ;;
    *)   dirname "$(command -v "$JAVA" 2>/dev/null || echo "$JAVA")" ;;
  esac
}
JBIN="$(java_bin_dir)"
JAVAC="${JAVAC:-$JBIN/javac}"
JAR="${JAR:-$JBIN/jar}"
if [ ! -x "$JAVAC" ]; then
  echo "find-java: no javac next to $JAVA - set JAVAC=/path/to/javac" >&2
  return 2 2>/dev/null || exit 2
fi

# --- the build directory
#
# OUT IS FOUND, NOT DEFAULTED, and this is the third variable in the same family.
# Three tools wrote `OUT="${OUT:-out}"` and the README documents `-d out`, but a
# build can go anywhere -- the sandbox builds to `classes/`, and every tool run
# from here had to be told OUT=classes or it looked in a directory that was not
# there. A default that names a directory is a guess; a lookup is not.
# AND A PRE-SET OUT IS CHECKED, NOT TRUSTED -- the same rule as JAVA and FX.
# This was the trap one more time: `self-test.sh` passed `OUT="${OUT:-out}"`
# down to `audit-stories.sh`, which sources this file, and because the variable
# was already set the lookup never ran -- so the audit exited 2 with "no build
# in out" and the tool self-test read that as "reported clean". A directory
# that is not there is not a value.
if [ -z "${OUT:-}" ] || [ ! -d "${OUT}/aside" ]; then
  OUT=""
  for c in out classes build target/classes; do
    [ -d "$c/aside" ] && { OUT="$c"; break; }
  done
fi
if [ -z "${OUT:-}" ]; then
  OUT=out   # nothing built yet; the caller's build will create it
fi

# --- JavaFX
#
# AND THE SAME FOR FX, which is the same trap one variable over. Both
# check-sheets.sh and regenerate-frames.sh defaulted FX to /root/javafx-sdk-27/lib
# BEFORE sourcing this file -- a path that does not exist on this machine -- so
# the search below was a no-op and the tools only worked because a caller passed
# FX= explicitly. A directory that is not there is not a value.
fx_works() {
  [ -n "${1:-}" ] && [ -d "$1" ]
}
if ! fx_works "${FX:-}"; then
  FX=""
  for c in /root/javafx-sdk-*/lib /usr/share/openjfx/lib "$HOME"/javafx-sdk-*/lib "$HOME"/Downloads/javafx-sdk-*/lib; do
    [ -d "$c" ] && { FX="$c"; break; }
  done
fi
if [ -z "${FX:-}" ]; then
  echo "find-java: no JavaFX SDK found - set FX=/path/to/javafx-sdk/lib" >&2
  return 2 2>/dev/null || exit 2
fi

# --- build directory
if [ -z "${OUT:-}" ]; then
  for d in out classes; do
    [ -d "$d" ] && { OUT="$d"; break; }
  done
fi
