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
if ! java_works "${JAVA:-}"; then
  JAVA=""
  if command -v java >/dev/null 2>&1; then JAVA=java
  else
    for c in /root/jdk-*/bin/java /usr/lib/jvm/*/bin/java "$HOME"/jdk*/bin/java; do
      [ -x "$c" ] && { JAVA="$c"; break; }
    done
  fi
fi
if [ -z "${JAVA:-}" ]; then
  echo "find-java: no java found - set JAVA=/path/to/bin/java" >&2
  return 2 2>/dev/null || exit 2
fi

# --- JavaFX
if [ -z "${FX:-}" ]; then
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
