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

# PUT THE STORY BACK EVEN IF THIS SCRIPT IS INTERRUPTED.
#
# Two of the checks below BREAK A TRACKED FILE on purpose - they copy
# stories/a440.aside aside, inject a fault, run the tool, and copy it back. The
# copy-back was a plain line at the end of each block, so a kill in between left
# the fault in the working tree: found on 2026-10-07 with a440.aside sitting at
# "ELEVENTH REGISTER" and ten stories, which is exactly the over-claim the check
# exists to catch, left behind by the check itself.
#
# A mutation harness that can leave a mutation behind is worse than no harness,
# for the same reason a check that cannot fail is worse than no check.
STORY_BAK=""
# RESTORE ON INTERRUPTION, which this suite has needed twice now.
#
# It modifies TRACKED files on purpose - it plants a fault in the story, the tutorial, the shelf page and the other
# edition - and each entry puts its own file back at the end. That is fine until the run is cut short, and then the
# fault stays in the tree: `git add -A` sweeps it into a commit, and what ships is the fault rather than the story.
# It has happened twice in two hours, once from my own `timeout` and once from a lost session.
#
# The individual restore_* calls stay where they are - they are the normal path. This is the backstop for not
# reaching them.
# RESTORE EVERYTHING ON INTERRUPTION, which this suite has needed three times now.
#
# It modifies TRACKED files on purpose - the story, the tutorial, the tutorial count in the game screen, and three
# web builds in two repositories - and each entry puts its own file back at the end. That is fine until the run is
# cut short, and then the fault stays in the tree: `git add -A` sweeps it into a commit, and what ships is the fault
# rather than the story. It has happened three times in three hours: my own `timeout`, a lost session, and a killed
# run while I was testing this very trap.
#
# The individual restore_* calls stay where they are - they are the normal path. This is the backstop for not
# reaching them, and it restores from the same backups they use.
cleanup() {
  restore_story 2>/dev/null || true
  restore_tut 2>/dev/null || true
  for pair in "web/night-shift.html:/tmp/st-web.bak" \
              "web/bearings.html:/tmp/st-br.bak" \
              "${TP:-../tp}/src/main/resources/style.css:/tmp/st-css.bak"; do
    f="${pair%%:*}"; b="${pair##*:}"
    [ -f "$b" ] && cp "$b" "$f" 2>/dev/null || true
  done
}
# AND THE SIGNAL TRAPS EXIT, which the first version did not - it cleaned up and then carried on, so a TERM during a
# run left the suite still going and still faulting files. Found by killing a run on purpose and watching it refuse
# to die: four orphaned runs were alive at once, from two lost sessions and two of my own tests, all racing over the
# same story file.
trap cleanup EXIT
trap 'cleanup; exit 130' INT TERM

restore_story() {
  if [ -n "$STORY_BAK" ] && [ -f "$STORY_BAK" ]; then
    cp "$STORY_BAK" stories/a440.aside 2>/dev/null || true
    rm -f "$STORY_BAK"
    STORY_BAK=""
  fi
}
# THE SAME HAZARD FOR THE TUTORIAL-COUNT INJECTIONS BELOW, and the same fix. They break GameplayScreen.java on
# purpose, so a kill between the injection and the copy-back would leave a stale count sitting in the tree - which
# is the exact fault the check exists to catch, left behind by the check itself.
TUT_BAK=""
TUT_FILE=src/main/java/aside/games/fruitjump/GameplayScreen.java
# THE "CANNOT READ LAST" INJECTION NEEDS A SECOND FILE, and I got this wrong the first time: `LAST` is declared in
# Tutorial.java, so a sed aimed at GameplayScreen did not apply at all. The block now greps for its own injection
# before trusting the result, which is the only reason it failed loudly instead of passing quietly - and a negative
# test that cannot apply is the worst kind, because it reports success for having done nothing.
LAST_BAK=""
LAST_FILE=src/main/java/aside/games/fruitjump/Tutorial.java
restore_tut() {
  if [ -n "$TUT_BAK" ] && [ -f "$TUT_BAK" ]; then
    cp "$TUT_BAK" "$TUT_FILE" 2>/dev/null || true
    rm -f "$TUT_BAK"
    TUT_BAK=""
  fi
  if [ -n "$LAST_BAK" ] && [ -f "$LAST_BAK" ]; then
    cp "$LAST_BAK" "$LAST_FILE" 2>/dev/null || true
    rm -f "$LAST_BAK"
    LAST_BAK=""
  fi
}

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
#
# THE INJECTED ORDINAL IS DERIVED NOW, NOT TYPED, and that is the second version. It used to replace NINTH with a
# hardcoded ELEVENTH - and the day an eleventh story was actually written, claiming eleven stopped being an
# over-claim, so the injection injected nothing and this block reported "reported clean with a story claiming to be
# 11 of 10" against a library that legitimately has eleven. Same shape as the tutorial-count fixture fixed the day
# before: A FIXTURE THAT COPIES THE CURRENT VALUE BREAKS ON THE ONE OCCASION IT EXISTS FOR.
if [ -x tools/audit-stories.sh ] && [ -f stories/a440.aside ]; then
  STORIES=$(ls stories/*.aside 2>/dev/null | wc -l | tr -d ' ')
  WORDS=(ZERO FIRST SECOND THIRD FOURTH FIFTH SIXTH SEVENTH EIGHTH NINTH TENTH ELEVENTH TWELFTH THIRTEENTH
         FOURTEENTH FIFTEENTH SIXTEENTH)
  OVER=$((STORIES + 1))
  OVER_WORD="${WORDS[$OVER]:-}"
  if [ -z "$OVER_WORD" ]; then
    bad "audit-stories.sh notices an over-claimed ordinal" "no ordinal word for $OVER stories - extend the table"
  else
    STORY_BAK=$(mktemp); cp stories/a440.aside "$STORY_BAK"
    python3 - "$OVER_WORD" <<'PY'
import sys
p = 'stories/a440.aside'; s = open(p).read()
old = 'NINTH REGISTER'
assert old in s, 'the ordinal is not there to change'
open(p, 'w').write(s.replace(old, sys.argv[1] + ' REGISTER', 1))
PY
    out=$(tools/audit-stories.sh 2>&1); rc=$?
    restore_story
    if [ $rc -ne 0 ] && grep -q "claims to be story $OVER" <<<"$out"; then
      ok "audit-stories.sh notices an over-claimed ordinal" "$(echo "$out" | grep 'highest ordinal' | tr -s ' ')"
    else
      bad "audit-stories.sh notices an over-claimed ordinal" "reported clean with a story claiming to be $OVER of $STORIES"
    fi
  fi
else
  bad "audit-stories.sh notices an over-claimed ordinal" "no audit-stories.sh or no a440.aside"
fi

# ---- audit-stories.sh must notice a story with a broken jump ------------------------------------------------
# Seven stories were not audited by anything until this step existed, so the step itself has to be shown to work.
# The fault is a jump to a scene that does not exist - the plainest thing the auditor is for.
if [ -x tools/audit-stories.sh ] && [ -f stories/a440.aside ]; then
  STORY_BAK=$(mktemp); cp stories/a440.aside "$STORY_BAK"
  python3 - <<'PY'
p='stories/a440.aside'; s=open(p).read()
old = '-> second'
assert old in s, 'the jump to break is not there'
open(p,'w').write(s.replace(old, '-> a_scene_that_does_not_exist', 1))
PY
  out=$(tools/audit-stories.sh 2>&1); rc=$?
  restore_story
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

# ---- check-tutorial-counts.py must notice a claim SPELLED AS A WORD -------------------------------------------------
# THE INJECTION THAT WAS MISSING WHERE IT MATTERED. A check for this exact class of staleness already existed in the
# release repository, and its self-test injected the phrasing it was written for - `ends at 8` - so it passed for
# months while four comments across the two repositories said "eight hand-built levels". A self-test that only
# exercises the shape the implementer was already thinking about certifies the blind spot. This injects the shape
# that actually escaped.
#
# NOTHING HERE NAMES A NUMBER, and that is the second version. The first hardcoded `first nine levels` and
# `int LAST = 9`, and the moment the tutorial grew a tenth level all three blocks stopped applying - they said so,
# because each greps for its own injection first - but a test whose fixture is a copy of the current value breaks
# every time the value changes, which is the one occasion it exists for. The wrong value is DERIVED now: one more
# than the code says, so it is wrong whatever the code says.
WORD_OF=(zero one two three four five six seven eight nine ten eleven twelve)
LAST_N=$(grep -oE "int LAST = [0-9]+" "$LAST_FILE" | grep -oE "[0-9]+" | head -1)
WRONG_W="${WORD_OF[$((LAST_N + 1))]:-}"
if [ -z "$WRONG_W" ]; then
  bad "check-tutorial-counts notices a word-form count" "cannot derive a wrong count: LAST=$LAST_N is past the word table"
else
  TUT_BAK=$(mktemp); cp "$TUT_FILE" "$TUT_BAK"
  printf '\n// injection: %s hand-built levels\n' "$WRONG_W" >> "$TUT_FILE"
  if ! grep -q "injection: $WRONG_W hand-built levels" "$TUT_FILE"; then
    bad "check-tutorial-counts notices a word-form count" "the injection did not apply"
  else
    out=$(python3 tools/check-tutorial-counts.py 2>&1); rc=$?
    if [ $rc -ne 0 ] && echo "$out" | grep -q "STALE.*$WRONG_W hand-built levels"; then
      ok "check-tutorial-counts notices a word-form count" "$(echo "$out" | grep -m1 'STALE' | tr -s ' ')"
    else
      bad "check-tutorial-counts notices a word-form count" "reported clean with a comment saying $WRONG_W"
    fi
  fi
  restore_tut
fi

# ---- and it must stay QUIET about a claim it is only DISCUSSING ---------------------------------------------------
# A NEGATIVE TEST, which the block above cannot be. The tool skips quoted text, because Tutorial.java's header
# quotes the old wrong wording in order to record the mistake - so a check that could not tell a quoted claim from
# an asserted one would fail on the correction itself. That makes "does not fire" the correct behaviour here, and
# a rule tested only in the firing direction is not tested. If the quote-skipping breaks, nothing else notices.
#
# THE SAME WRONG VALUE, QUOTED, so the two blocks differ in exactly one thing - whether the claim is asserted or
# discussed - and a difference of behaviour can only come from that.
if [ -z "$WRONG_W" ]; then
  bad "check-tutorial-counts ignores a quoted claim" "cannot derive a wrong count: LAST=$LAST_N"
else
  TUT_BAK=$(mktemp); cp "$TUT_FILE" "$TUT_BAK"
  printf '\n// injection: the header once claimed "%s hand-built levels"\n' "$WRONG_W" >> "$TUT_FILE"
  if ! grep -q "the header once claimed \"$WRONG_W hand-built levels\"" "$TUT_FILE"; then
    bad "check-tutorial-counts ignores a quoted claim" "the injection did not apply"
  else
    out=$(python3 tools/check-tutorial-counts.py 2>&1)
    # ASSERT THE SPECIFIC THING, NOT GLOBAL CLEANLINESS. This used to require rc=0 - and the day a genuinely stale
    # count appeared elsewhere in the tree (GameplayScreen's "first ten levels", caught by this same tool while the
    # library grew to eleven levels), this NEGATIVE test failed and reported "flagged quoted text" about a fault
    # that was real and somewhere else. A negative test that asserts nothing else was found is a test that fails
    # for other people's reasons. What it is actually about is whether the QUOTED claim was reported, so that is
    # what it checks: the injected wording must not appear in the output.
    if echo "$out" | grep -q "$WRONG_W hand-built levels"; then
      bad "check-tutorial-counts ignores a quoted claim" "the quoted claim WAS reported: $(echo "$out" | grep -m1 'STALE' | tr -s ' ')"
    else
      ok "check-tutorial-counts ignores a quoted claim" "quoted text was not reported (any other finding is not this test's business)"
    fi
  fi
  restore_tut
fi

# ---- and it must FAIL rather than pass when it cannot read the value it compares against --------------------------
# A TOOL THAT CANNOT RUN IS NOT A TOOL THAT PASSED. If `int LAST = ...` stops being parseable - renamed, moved, or
# turned into a computed expression - the tool has nothing to compare a claim against, and reporting "clean" there
# is the exact defect this project keeps finding. Exit 2, never 0.
#
# `[0-9]*` rather than the literal value, for the same reason as above.
LAST_BAK=$(mktemp); cp "$LAST_FILE" "$LAST_BAK"
sed -i 's/int LAST = [0-9]*;/int LAST = LAST;/' "$LAST_FILE"
if ! grep -q "int LAST = LAST;" "$LAST_FILE"; then
  bad "check-tutorial-counts refuses to pass without LAST" "the injection did not apply"
else
  out=$(python3 tools/check-tutorial-counts.py 2>&1); rc=$?
  if [ $rc -eq 2 ]; then
    ok "check-tutorial-counts refuses to pass without LAST" "exit 2, not 0"
  else
    bad "check-tutorial-counts refuses to pass without LAST" "exit $rc - it did not refuse"
  fi
fi
restore_tut

# ---- check-readme-stories.py must notice a README that disagrees with a story ----------------------------------
# The plainest thing it is for: a count in prose that the stories do not support. The fault is one scene added to
# bell-codes' claim, which is the shape of the two real stale claims it found on its first run.
if [ -x tools/check-readme-stories.py ] && [ -f README.md ]; then
  README_BAK=$(mktemp); cp README.md "$README_BAK"
  python3 - <<'PY'
import pathlib, re
p = pathlib.Path('README.md'); s = p.read_text()
i = s.index('`stories/bell-codes.aside`**')
seg = s[i:i + 1400]
m = re.search(r'(\d+) scenes?', seg)
assert m, 'no scene count on the bell-codes line to change'
bigger = seg.replace(m.group(0), str(int(m.group(1)) + 1) + ' scenes', 1)
p.write_text(s[:i] + bigger + s[i + 1400:])
PY
  out=$(timeout 600 tools/check-readme-stories.py 2>&1); rc=$?
  cp "$README_BAK" README.md
  if [ $rc -ne 0 ] && grep -q "bell-codes" <<<"$out"; then
    ok "check-readme-stories notices a count that disagrees" "$(echo "$out" | grep STALE | head -1 | tr -s ' ')"
  else
    bad "check-readme-stories notices a count that disagrees" "exit $rc, and nothing about bell-codes"
  fi
fi

# ---- the two match tools must notice an edition that drifts -----------------------------------------------------
# These are the tools I trust most and they had no fault injected, which is the wrong way round: a comparison that
# cannot fail is the quietest kind, because it agrees with whatever it is pointed at. The fault is one character in
# one engine file of the other edition.
for pair in "check-fnaf-match.sh:../fnaf2:src/main/java/fnaf/engine/Animatronic.java" \
            "check-platformer-match.sh:../tp:src/main/java/tropical/engine/Bat.java"; do
  tool="${pair%%:*}"; rest="${pair#*:}"; other="${rest%%:*}"; file="${rest#*:}"
  target="$other/$file"
  if [ -x "tools/$tool" ] && [ -f "$target" ]; then
    BAK=$(mktemp); cp "$target" "$BAK"
    python3 - "$target" <<'PY'
import sys
p = sys.argv[1]; s = open(p).read()
i = s.index(';') + 1          # somewhere harmless but real, on the first statement line
open(p, 'w').write(s[:i] + ' ' + s[i:])
PY
    out=$(timeout 600 "tools/$tool" "$other" 2>&1); rc=$?
    cp "$BAK" "$target"
    if [ $rc -ne 0 ]; then
      ok "$tool notices a drifted edition" "$(echo "$out" | grep -E '^differing|differ' | head -1 | tr -s ' ' | cut -c1-70)"
    else
      bad "$tool notices a drifted edition" "exit 0 with a file changed - it agreed with a difference"
    fi
  fi
done

# ---- and the two sweeps, on synthetic input, because their subjects are reports and trees ----------------------
if [ -x tools/find-silent-suites.sh ]; then
  printf '  something ok     === 0 passed, 0 failed ===\n  other ok         === 7 passed, 0 failed ===\n' > /tmp/silent-probe.txt
  out=$(timeout 300 tools/find-silent-suites.sh /tmp/silent-probe.txt 2>&1); rc=$?
  rm -f /tmp/silent-probe.txt
  if [ $rc -ne 0 ] && grep -q "SILENT" <<<"$out"; then
    ok "find-silent-suites notices a suite that ran nothing" "$(echo "$out" | grep '===' | head -1 | tr -s ' ')"
  else
    bad "find-silent-suites notices a suite that ran nothing" "exit $rc, and no SILENT line"
  fi
fi

if [ -x tools/find-duplicated-lists.py ]; then
  PROBE=$(mktemp -d)
  printf 'A="ALPHA, BRAVO, CHARLIE"\nB="ALPHA, BRAVO, CHARLIE"\n' > "$PROBE/one.sh"
  out=$(timeout 300 tools/find-duplicated-lists.py "$PROBE" 2>&1); rc=$?
  rm -rf "$PROBE"
  if grep -q "appear in more than one place" <<<"$out" && grep -q "2 places" <<<"$out"; then
    ok "find-duplicated-lists notices a list written twice" "$(echo "$out" | grep '===' | head -1 | tr -s ' ')"
  else
    bad "find-duplicated-lists notices a list written twice" "it did not report the pair"
  fi
fi

# ---- check-literal-drift.py, on a pair of trees where one number is off -----------------------------------------
# The bug it exists for: the release fired arrows from `player.y - 10` where aside fires `player.y`, the fix reached
# one edition and not the other, and the file diffs differ by 179 lines so one number does not stand out.
if [ -x tools/check-literal-drift.py ]; then
  PROBE=$(mktemp -d); mkdir -p "$PROBE/a" "$PROBE/b"
  printf 'x = player.y - 10;\ny = other;\n' > "$PROBE/a/one.java"
  printf 'x = player.y;\ny = other;\n'      > "$PROBE/b/one.java"
  out=$(timeout 300 tools/check-literal-drift.py "$PROBE/a" "$PROBE/b" 2>&1); rc=$?
  rm -rf "$PROBE"
  if [ $rc -ne 0 ] && grep -qE "one.java|differ|drift" <<<"$out"; then
    ok "check-literal-drift notices one number changed" "$(echo "$out" | grep -iE 'differ|drift' | head -1 | tr -s ' ' | cut -c1-70)"
  else
    bad "check-literal-drift notices one number changed" "exit $rc, and nothing about the pair"
  fi
fi

# ---- check-fnaf-content.sh: NO ENTRY, and why --------------------------------------------------------------
# I wrote one and it could not be made to work, which is worth more than a passing entry that proves nothing.
#
# The tool reports a NORMALISED line count per screen - GameScreen is 64 as a baseline - and it normalises imports,
# class names, paths and trailing comments. A fault that adds a line or a comment therefore does not move the count,
# and a fault that changes real logic moves it by one or two against a baseline of sixty-four. The first version of
# the entry compared the count and reported "unchanged from 64" for a screen I had just edited.
#
# A SYNTHETIC PAIR WILL NOT WORK EITHER: the tool takes one argument, the standalone's path, and hardcodes its own
# side - so it needs the real repositories and the real framework difference between them.
#
# THE HONEST POSITION IS THAT ITS SUBJECT IS A JUDGEMENT CALL. It prints how far apart the two editions' screens are
# and says "anything higher is content and should be read, not assumed". That is a report, not a gate, and there is
# nothing for a fault injection to make it say that it would not already say.

# ---- check-audio-reproducible.sh must notice an asset that is not what the generator makes ---------------------
# Three faults in one week came from the same silence: a generator seeding noise from Python's per-process salted
# hash(), so re-running it MOVED the committed assets. The fault here is one byte of one asset.
if [ -x tools/check-audio-reproducible.sh ]; then
  ASSET=$(ls audio/*.wav 2>/dev/null | head -1)
  if [ -n "${ASSET:-}" ]; then
    BAK=$(mktemp); cp "$ASSET" "$BAK"
    printf '\x00' | dd of="$ASSET" bs=1 seek=2000 count=1 conv=notrunc 2>/dev/null
    out=$(timeout 900 tools/check-audio-reproducible.sh 2>&1); rc=$?
    cp "$BAK" "$ASSET"
    if [ $rc -ne 0 ]; then
      ok "check-audio-reproducible notices a changed asset" "$(echo "$out" | grep -iE 'differ|reproduc|FAIL' | head -1 | tr -s ' ' | cut -c1-70)"
    else
      bad "check-audio-reproducible notices a changed asset" "exit 0 with an asset byte changed"
    fi
  fi
fi

# THE SUMMARY, WHICH I DELETED. A line-removal script I wrote earlier ran from the write-only probe to the next
# `# ----` marker, and there was no next marker, so it consumed the end of the file - summary and exit code with it.
# The suite still ran and printed every ok and bad line, and said nothing at the end, which is the quietest possible
# way for a suite to stop reporting. Restored, and worth remembering: a script that deletes "up to the next marker"
# deletes the rest of the file when there is no next marker.
echo
echo "=== $pass tool self-test(s) passed, $fail failed ==="
[ "$fail" = 0 ] || exit 1
