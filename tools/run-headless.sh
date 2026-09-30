#!/usr/bin/env bash
#
# Run a JavaFX command under a virtual display, starting one if it is not there.
#
#   tools/run-headless.sh java -cp out aside.tools.ShotVn the-lamp-room
#
# Why this exists rather than a note in a docstring: the sandbox reboots hourly and Xvfb does not survive it, so
# every JavaFX command in this repo needs a display that may have just died. ShotVn's docstring has said so since
# the day it was written - "DISPLAY=:99 on the command... a pgrep for Xvfb does not tell you whether the display
# you are about to use is live" - and I have hit "Unable to open DISPLAY" three times anyway, because a comment
# is read after the failure, not before the command.
#
# The check is xdpyinfo, not pgrep: it asks the display whether it is SERVING, which is the question that
# matters, and that is the difference between this script and the docstring it replaces.
set -e
DISPLAY_NUM="${DISPLAY_NUM:-:99}"
if ! xdpyinfo -display "$DISPLAY_NUM" >/dev/null 2>&1; then
  echo "[run-headless] no live display on $DISPLAY_NUM - starting Xvfb"
  Xvfb "$DISPLAY_NUM" -screen 0 "${SCREEN:-1400x900x24}" >/tmp/xvfb.log 2>&1 &
  for _ in $(seq 1 40); do
    xdpyinfo -display "$DISPLAY_NUM" >/dev/null 2>&1 && break
    sleep 0.25
  done
  xdpyinfo -display "$DISPLAY_NUM" >/dev/null 2>&1 || { echo "[run-headless] Xvfb failed to start; see /tmp/xvfb.log"; exit 1; }
fi
export DISPLAY="$DISPLAY_NUM"
exec "$@"
