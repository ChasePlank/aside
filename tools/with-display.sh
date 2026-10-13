#!/usr/bin/env bash
#
# with-display.sh - run a command that needs an X display, with one that is definitely serving.
#
#   tools/with-display.sh <command> [args...]
#
# WHY THIS EXISTS. "Check that the display is alive before you render" has been written down for weeks, and it has
# still cost this session twice in one day: `ShotVn` reported a stack trace twice as if the tool were broken, and
# once I read a STALE output file as a fresh render, because the run had died on "Unable to open DISPLAY" before
# writing anything. Both times the variable was exported and looked set - which is exactly the trap: `DISPLAY=:99`
# is a NAME, and the test is not whether it is set but whether the server behind it answers.
#
# A RULE I HAVE TO REMEMBER IS A RULE THAT WILL BE FORGOTTEN. This is the same rule expressed as a precondition the
# command cannot skip, which is the shape every other guard in this repository ended up taking.
#
# It does NOT start a second server on a port already in use, and it keeps the server's log so a failure to start
# says why.
set -u
cd "$(dirname "$0")/.." || exit 2

NUM="${DISPLAY_NUM:-:99}"
if ! xdpyinfo -display "$NUM" >/dev/null 2>&1; then
  LOG=$(mktemp)
  setsid Xvfb "$NUM" -screen 0 1280x900x24 > "$LOG" 2>&1 &
  for _ in $(seq 1 60); do
    xdpyinfo -display "$NUM" >/dev/null 2>&1 && break
    sleep 0.25
  done
  if ! xdpyinfo -display "$NUM" >/dev/null 2>&1; then
    echo "with-display: $NUM never came up; Xvfb said:" >&2
    head -5 "$LOG" >&2
    exit 2
  fi
  rm -f "$LOG"
fi

DISPLAY="$NUM" exec "$@"
