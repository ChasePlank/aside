#!/usr/bin/env bash
#
# self-test.sh - break what each tool looks at, and check the tool notices.
#
#   tools/self-test.sh
#
# WHY THIS EXISTS. Rule 15 in this project's history is "test the thing you changed, not what it operates on", and
# it was written down after a mutation harness shipped with a Python syntax error: THE SUITES PASSED BEAUTIFULLY
# WHILE THE HARNESS COULD NOT EVEN START, and nobody found out for an hour. The instrument was broken while the
# thing it operated on looked fine.
#
# Every tool in this repository is a check. A check that cannot fail is worse than no check, because it is
# believed. So this breaks a known thing on purpose and requires the tool to complain.
#
# I have now done this by hand three times in one week - compare-ports.sh, ShotSprites, diff.sh - and twice by hand
# is the signal this project uses for "build it into the path".
set -u
cd "$(dirname "$0")/.." || exit 2

pass=0; fail=0
ok()   { printf '  %-38s ok      %s\n' "$1" "$2"; pass=$((pass+1)); }
bad()  { printf '  %-38s FAIL    %s\n' "$1" "$2"; fail=$((fail+1)); }

# ---- diff.sh must report what a filter hides ---------------------------------------------------------------
printf 'a\nb\nc\n' > /tmp/st-a; printf 'a\nB\nC\n' > /tmp/st-b
out=$(tools/diff.sh /tmp/st-a /tmp/st-b '^[+-][bB]' 2>&1)
if echo "$out" | grep -q "you would NOT have:   2"; then ok "diff.sh counts hidden lines" "$(echo "$out" | grep 'would NOT' | tr -s ' ')"
else bad "diff.sh counts hidden lines" "did not report 2 hidden"; fi
if echo "$out" | grep -q '^    -c'; then ok "diff.sh prints the hidden lines" "yes"
else bad "diff.sh prints the hidden lines" "hidden lines not shown"; fi

# ---- diff.sh must NOT claim a difference where there is none -------------------------------------------------
cp /tmp/st-a /tmp/st-c
out=$(tools/diff.sh /tmp/st-a /tmp/st-c 2>&1)
if echo "$out" | grep -q "0 changed line(s)"; then ok "diff.sh reports identical files as identical" "0 changed"
else bad "diff.sh reports identical files as identical" "claimed a difference"; fi

# ---- check-web.mjs must notice a broken build ----------------------------------------------------------------
if command -v node >/dev/null 2>&1 && [ -f web/night-shift.html ]; then
  cp web/night-shift.html /tmp/st-web.bak
  python3 -c "
p='web/night-shift.html'; s=open(p).read(); i=s.find('<script')
open(p,'w').write(s[:i+8] + ' this is not javascript ' + s[i+8:])"
  out=$(node tools/check-web.mjs 2>&1 | tail -3)
  cp /tmp/st-web.bak web/night-shift.html
  if echo "$out" | grep -q "did not boot"; then ok "check-web.mjs notices a broken build" "$(echo "$out" | grep 'did not boot')"
  else bad "check-web.mjs notices a broken build" "reported clean on a syntax error"; fi
else
  printf '  %-38s skip    no node or no web build\n' "check-web.mjs notices a broken build"
fi

# ---- compare-ports.sh must notice a rule that drifts ---------------------------------------------------------
if [ -f web/bearings.html ]; then
  cp web/bearings.html /tmp/st-br.bak
  python3 -c "
p='web/bearings.html'; s=open(p).read(); old='\"days\":16,'
assert old in s, 'rule constant not found'
open(p,'w').write(s.replace(old,'\"days\":15,',1))"
  out=$(tools/compare-ports.sh 2>&1 | tail -2)
  cp /tmp/st-br.bak web/bearings.html
  if echo "$out" | grep -q "FAILED"; then ok "compare-ports.sh notices a drifted rule" "$(echo "$out" | grep 'comparison' | tr -s ' ')"
  else bad "compare-ports.sh notices a drifted rule" "reported agree on a changed rule"; fi
else
  printf '  %-38s skip    no web/bearings.html\n' "compare-ports.sh notices a drifted rule"
fi

echo
echo "=== $pass tool self-test(s) passed, $fail failed ==="
[ "$fail" = 0 ] || exit 1
