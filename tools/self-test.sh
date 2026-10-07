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
# ---- style-classes.sh must notice a class the code needs that is not defined ---------------------------------
# It runs against the RELEASE, where style.css lives; from this repository it exits 2 saying "nothing to compare",
# which is honest rather than silent. Tested where it works.
TP="${TP:-../tp}"
if [ -f "$TP/src/main/resources/style.css" ]; then
  cp "$TP/src/main/resources/style.css" /tmp/st-css.bak
  python3 - "$TP" <<'PYX'
import re, sys
p = sys.argv[1] + "/src/main/resources/style.css"
css = open(p).read()
m = re.search(r'\.menu-item\s*\{[^}]*\}', css, flags=re.S)
assert m, "no .menu-item rule to remove"
open(p, "w").write(css[:m.start()] + css[m.end():])
PYX
  # NO tail HERE. The first version piped the tool through `tail -3` and then looked for the class name - and the
  # tool reports "1 class(es) used but not defined" in its summary WITHOUT naming the class in the last three lines,
  # so the test failed while the tool was working perfectly. A narrowed check, inside the tool built to prevent
  # narrowed checks. Match the tool's own verdict over its whole output.
  out=$(tools/style-classes.sh "$TP" 2>&1); rc=$?
  cp /tmp/st-css.bak "$TP/src/main/resources/style.css"
  if [ $rc -ne 0 ] && echo "$out" | grep -q "used but not defined"; then
    ok "style-classes.sh notices an undefined class" "$(echo "$out" | grep 'used but not defined' | tr -s ' ')"
  else bad "style-classes.sh notices an undefined class" "reported clean with .menu-item removed"; fi
else
  printf '  %-38s skip    no %s/src/main/resources/style.css\n' "style-classes.sh notices an undefined class" "$TP"
fi

# ---- audit-stories.sh must notice a story claiming to be later than the library -------------------------------
# THE FAULT IS THE ONE THAT ACTUALLY HAPPENED: A440 said "TENTH REGISTER" when there were nine stories, and nothing
# would have caught it. The count only came right by accident when a tenth was written.
if [ -x tools/audit-stories.sh ] && [ -f stories/a440.aside ]; then
  OR_BAK=$(mktemp); cp stories/a440.aside "$OR_BAK"
  python3 - <<'PY'
p='stories/a440.aside'; s=open(p).read()
old = 'NINTH REGISTER'
assert old in s, 'the ordinal is not there to change'
open(p,'w').write(s.replace(old, 'ELEVENTH REGISTER', 1))
PY
  out=$(JAVA="${JAVA:-/root/jdk-27+35/bin/java}" OUT="${OUT:-out}" tools/audit-stories.sh 2>&1); rc=$?
  cp "$OR_BAK" stories/a440.aside; rm -f "$OR_BAK"
  if [ $rc -ne 0 ] && echo "$out" | grep -q "claims to be story 11"; then
    ok "audit-stories.sh notices an over-claimed ordinal" "$(echo "$out" | grep 'highest ordinal' | tr -s ' ')"
  else
    bad "audit-stories.sh notices an over-claimed ordinal" "reported clean with a story claiming to be 11 of 10"
  fi
else
  bad "audit-stories.sh notices an over-claimed ordinal" "no audit-stories.sh or no a440.aside"
fi

# ---- audit-stories.sh must notice a story with a broken jump ------------------------------------------------
# Seven stories were not audited by anything until this step existed, so the step itself has to be shown to work.
# The fault is a jump to a scene that does not exist - the plainest thing the auditor is for.
if [ -x tools/audit-stories.sh ] && [ -f stories/a440.aside ]; then
  AS_BAK=$(mktemp); cp stories/a440.aside "$AS_BAK"
  python3 - <<'PY'
p='stories/a440.aside'; s=open(p).read()
old = '-> second'
assert old in s, 'the jump to break is not there'
open(p,'w').write(s.replace(old, '-> a_scene_that_does_not_exist', 1))
PY
  out=$(tools/audit-stories.sh 2>&1); rc=$?
  cp "$AS_BAK" stories/a440.aside; rm -f "$AS_BAK"
  if [ $rc -ne 0 ] && echo "$out" | grep -q "a440.*ISSUES"; then
    ok "audit-stories.sh notices a broken jump" "$(echo "$out" | grep 'with issues' | tr -s ' ')"
  else
    bad "audit-stories.sh notices a broken jump" "reported clean with a jump to a missing scene"
  fi
else
  bad "audit-stories.sh notices a broken jump" "no audit-stories.sh or no a440.aside"
fi

# ---- find-drift.py must report a line that falls BETWEEN its two thresholds --------------------------------
# THE FAULT IS THE ORIGINAL BUG, and it hid a real divergence: the release's LevelValidator gained
# "player.grounded && " in front of a condition - nineteen characters added to a sixty-character line, a
# similarity of about 0.75 - and the tool said "0 with NO counterpart, 0 near miss(es)" and "nothing: the two
# differ only in comments, imports, or not at all" for a file that had diverged. The band between --gap and
# --near is where small edits to long lines live, which is most real drift.
tmpd=$(mktemp -d)
cat > "$tmpd/a.java" <<'JA'
class A {
    void f() { if (player.grounded && player.x - windowX < 10) player.vy = JUMP_V; }
}
JA
cat > "$tmpd/b.java" <<'JB'
class A {
    void f() { if (player.x - windowX < 10) player.vy = JUMP_V; }
}
JB
out=$(python3 tools/find-drift.py "$tmpd/a.java" "$tmpd/b.java" 2>&1); rc=$?
rm -rf "$tmpd"
if echo "$out" | grep -q "changed between the thresholds" && echo "$out" | grep -q "player.grounded"; then
  ok "find-drift reports the band between its thresholds" "$(echo "$out" | grep 'changed between' | head -1 | tr -s ' ')"
else
  bad "find-drift reports the band between its thresholds" "a small edit to a long line was reported nowhere"
fi

echo
echo "=== $pass tool self-test(s) passed, $fail failed ==="
[ "$fail" = 0 ] || exit 1
