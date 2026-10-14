#!/usr/bin/env bash
#
# status-all.sh - the state of every project, in one place.
#
#   tools/status-all.sh
#
# WHY THIS EXISTS. Three scheduled tasks ask the same question in different words - is each project pushed, is the
# standalone copy in step with the engine's, is the newest edition published - and I have answered it by hand about
# five times this week, in six commands each, and twice got a wrong answer by reading only part of it. THE RULE
# "TWICE BY HAND MEANS BUILD IT INTO THE PATH" applies.
#
# AND IT IS NOT release-status.sh. That one answers "does this release need publishing" for ONE repository and is
# the right tool for a release. This answers "where does every project actually stand" across all four, including
# the two things a release tool does not look at: whether a local branch is on the remote, and whether a remote
# branch holds work that main does not.
#
# IT DOES NOT FIX ANYTHING AND DOES NOT PUSH. It prints, and it says UNKNOWN rather than guessing when a tool or
# the network is unavailable - an unrun check reported as fine is the failure this project keeps finding.
set -u
cd "$(dirname "$0")/.." || exit 2

REPOS="aside:ChasePlank/aside tp:ChasePlank/Fruit-Jump wake:ChasePlank/wake fnaf2:ChasePlank/fnaf"
WORKSPACE=$(cd .. && pwd)
problems=0

for entry in $REPOS; do
  dir=${entry%%:*}; repo=${entry##*:}
  printf '\n=== %s  (%s)\n' "$dir" "$repo"
  if [ ! -d "$WORKSPACE/$dir/.git" ]; then
    echo "  NOT PRESENT at $WORKSPACE/$dir"; problems=$((problems + 1)); continue
  fi
  cd "$WORKSPACE/$dir" || continue

  branch=$(git rev-parse --abbrev-ref HEAD)
  dirty=$(git status --porcelain | wc -l | tr -d ' ')
  ahead=0; behind=0
  if git show-ref -q refs/remotes/origin/main; then
    set -- $(git rev-list --left-right --count HEAD...origin/main 2>/dev/null || echo "0 0")
    ahead=${1:-0}; behind=${2:-0}
  fi
  url=$(git remote get-url origin 2>/dev/null | sed 's|.*github.com[:/]||; s|\.git$||')
  printf '  branch %s   uncommitted %s   ahead of origin/main %s   behind %s\n' "$branch" "$dirty" "$ahead" "$behind"
  [ "$url" = "$repo" ] || { echo "  REMOTE IS $url, expected $repo"; problems=$((problems + 1)); }
  [ "$dirty" = "0" ] || { echo "  UNCOMMITTED WORK: commit or discard it before calling this finished"; problems=$((problems + 1)); }
  [ "$ahead" = "0" ] || { echo "  UNPUSHED COMMITS: $ahead not on the remote"; problems=$((problems + 1)); }

  # REMOTE BRANCHES THAT HOLD WORK MAIN DOES NOT. Not a fault by itself - one of them is a pending design
  # decision - but it is the thing that makes "newest edition" a question, so it is printed every time.
  for b in $(git for-each-ref --format='%(refname:short)' refs/remotes/origin | grep -v 'origin/HEAD\|origin/main$'); do
    n=$(git log --oneline "origin/main..$b" 2>/dev/null | wc -l | tr -d ' ')
    printf '  %-34s %s commit(s) main does not have\n' "${b#origin/}" "$n"
  done

  # AND THE PUBLISHED FORM, which is what "added to github" means to anybody who wants to play it.
  if command -v gh >/dev/null 2>&1; then
    latest=$(gh release list -R "$repo" --limit 1 2>/dev/null | cut -f1 | head -1)
    if [ -n "$latest" ]; then printf '  latest release: %s\n' "$latest"
    else printf '  latest release: NONE\n'; fi
  else
    printf '  latest release: UNKNOWN - gh is not on the PATH\n'
  fi
done

cd "$WORKSPACE"/aside 2>/dev/null || true
printf '\n=== %s ===\n' "$([ "$problems" = "0" ] && echo 'every project is pushed, clean and published' || echo "$problems thing(s) above need attention")"
exit 0
