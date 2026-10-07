#!/usr/bin/env bash
#
# briefing.sh - measure the current state of all four repositories, for the WHERE THINGS STAND section of
# reference/pending-messages.md.
#
#   tools/briefing.sh
#
# WHY THIS EXISTS. That section is what Kinger reads, and its numbers were hand-typed. On 2026-10-07 it said 27
# games (there were 28), ~9,864 checks (~9,914), 6 tool self-tests (9), 540 SelfTest checks (564), two new stories
# (four), and it named a commit for each repository that had moved. A briefing whose numbers are typed will always
# go stale, and a stale briefing does not merely fail to inform - it misinforms.
#
# So this measures rather than asserts. Nothing here is a remembered figure. Run it, paste the output.
#
# It does NOT run the gates - that is several minutes and the gate prints its own numbers. Run the gate and this
# together, or read the gate's last line. What this covers is everything cheap and everything that drifts silently:
# commit hashes, counts of things, and the release gap.
set -u

WS="${WS:-/root/workspace}"
JAVA_BIN="${JAVA:-/root/jdk-27+35/bin/java}"
MEM="${MEMORY_DIR:-$WS/.letta/agents/agent-74836eae-f7c4-4b90-818a-4e3f29afd8bb/memory}"

echo "## WHERE THINGS STAND (measured $(date -u '+%b %-d, %Y') - read this one screen)"
echo
echo "Four repositories. Measured, not remembered."
echo

for repo in aside tp wake fnaf2; do
  d="$WS/$repo"
  [ -d "$d/.git" ] || continue
  name=$(git -C "$d" remote get-url origin 2>/dev/null | sed 's#.*github.com[:/]##; s#\.git$##')
  head=$(git -C "$d" log --oneline -1 2>/dev/null | cut -c1-7)
  dirty=$(git -C "$d" status --short 2>/dev/null | wc -l)
  behind=$(git -C "$d" log --oneline origin/main..HEAD 2>/dev/null | wc -l)
  printf -- '- **`%s`** - `main` at `%s`' "${name:-$repo}" "$head"
  if [ "$dirty" -eq 0 ] && [ "$behind" -eq 0 ]; then
    echo ", clean and pushed."
  else
    echo ", **$dirty uncommitted file(s), $behind unpushed commit(s)**."
  fi
done
echo

# counts of things, which is what drifts
# THE SHELF'S NUMBER, NOT THE PACKAGE COUNT. `ls src/main/java/aside/games/ | wc -l` gives 29 and the shelf gives
# 28, because one directory is the shared engine rather than a game. The first version of this printed 29 and
# called them games, which is the same class of error as the numbers it was written to replace.
stories=$(ls "$WS/aside/stories/"*.aside 2>/dev/null | wc -l)
shelf=$(DISPLAY="${DISPLAY:-:99}" "$JAVA_BIN" -cp "$WS/aside/out" aside.game.PhoneShelf 2>/dev/null \
        | grep -oE '[0-9]+ on the shelf' | grep -oE '^[0-9]+')
games=${shelf:-?}
web=$(ls "$WS/aside/web/"*.html 2>/dev/null | grep -vc 'aside\.html$')
echo "- **Games:** $games on the phone shelf (measured by building it), **$stories stories** in \`stories/\`, $web web build(s)."
echo "- **Story ordinals claimed:** $(grep -hoE '\b(FIRST|SECOND|THIRD|FOURTH|FIFTH|SIXTH|SEVENTH|EIGHTH|NINTH|TENTH|ELEVENTH|TWELFTH) REGISTER' "$WS/aside/stories/"*.aside 2>/dev/null | wc -l) of $stories (checked by tools/audit-stories.sh)."

# the release gap, which is the thing with a clock on it
if [ -x "$WS/aside/tools/release-status.sh" ]; then
  echo
  echo "### The release gap"
  echo '```'
  "$WS/aside/tools/release-status.sh" 2>&1 | head -20
  echo '```'
fi

echo
echo "### Gate numbers"
echo
echo "Not measured here - they take minutes and the gate prints them. Run:"
echo
echo '```'
echo "cd $WS/aside && DISPLAY=:99 tools/run-suites.sh | tail -2"
echo "cd $WS/tp    && DISPLAY=:99 tools/run-suites.sh | tail -2"
echo '```'
