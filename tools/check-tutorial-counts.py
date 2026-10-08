#!/usr/bin/env python3
"""
check-tutorial-counts.py - find every comment that states a count for the Fruit Jump tutorial and compare it
against the number the code actually uses.

    tools/check-tutorial-counts.py

WHY THIS EXISTS. `Tutorial.LAST` is how many hand-built tutorial levels there are, and comments across the game
state it in prose - in the menu, in the screen that enters tutorial mode, in the tests, in the tutorial's own
header. On 7 October FOUR of them were found saying "eight" while `LAST` was 9.

The reason they were found by hand is the point. A check for exactly this class of staleness already existed, in
the release repository, and it had been passing the whole time: it matched the phrase `ends at <N>`, and not one of
the four said that. Three were written "eight hand-built levels" - the number spelled as a WORD - and the fourth
was "the first eight levels". A self-test that only ever injected the phrasing the check already handled passed
throughout, which is what made the check believable.

So this does not match a phrasing. It matches a CLAIM: a count attached to the tutorial, in either numeral form.

TWO TIERS, AND THE SECOND IS GATED ON PURPOSE. The unambiguous shapes - "N hand-built levels", "Level N ends",
"ends at N", "the N levels" - are checked on every line. The bare "N levels" shape is checked ONLY on a line that
also says "tutorial", because this is a tree full of generated-level talk: "40 levels", "5 levels" and "ten levels"
appear in LevelGen and WaterProbe about the generator, and none of them is claiming anything about the tutorial.
That is measured rather than feared - the unqualified pattern alone reported seven false positives in those two
files, and a check that flags them reports its own overreach as a fault in the repository.

QUOTED TEXT IS SKIPPED, and that is not a convenience. A claim inside quotes is being DISCUSSED rather than
asserted - Tutorial.java's header quotes the old wrong wording in order to record the mistake - so a check that
could not tell the two apart would fail on the correction itself. `self-test.sh` injects a quoted wrong claim and
requires this tool to stay quiet about it, because a rule tested only in the firing direction is not tested.

SCOPE IS DELIBERATE. This looks at `aside/games/fruitjump` only. The same patterns over the whole tree, which holds
27 other games, report "ends at 6" in fnaf code and "all 40 levels" in the engine's own SelfTest - both correct
statements about something else.

Report only, with a per-claim line, so the coverage is visible rather than summarised. Exit 0 clean, 1 if any claim
disagrees with the code, 2 if it could not run at all - because a tool that cannot run is not a tool that passed.
"""
import os
import re
import sys

SCOPE = "src/main/java/aside/games/fruitjump"
TUTORIAL = os.path.join(SCOPE, "Tutorial.java")

WORDS = {w: i + 1 for i, w in enumerate(
    "one two three four five six seven eight nine ten eleven twelve".split())}

# Unambiguous: these shapes are a tutorial count wherever they appear.
ALWAYS = [
    r"(\w+)\s+hand-(?:built|written)\s+(?:tutorial\s+)?(?:levels?|tutorials?)",
    r"level\s+(\w+)\s+ends",
    r"ends\s+at\s+(?:level\s+)?(\w+)",
    r"(?:all|its|the)\s+(\w+)\s+levels\b",
]
# Only meaningful where the line is about the tutorial; see the header for why.
IN_TUTORIAL = [
    r"first\s+(\w+)\s+levels",
    r"(\w+)\s+levels\b",
]

QUOTED = re.compile(r'"[^"\n]*"|`[^`\n]*`')


def strip_quotes(text):
    """Blank out quoted spans, keeping the line's length so a match is still where it looks."""
    return QUOTED.sub(lambda m: " " * len(m.group(0)), text)


def value(token):
    """A count as a number, or None if the token is not a count at all."""
    t = token.lower().strip(".,;:")
    if t.isdigit():
        return int(t)
    return WORDS.get(t)


def tutorial_last():
    if not os.path.isfile(TUTORIAL):
        return None
    with open(TUTORIAL, encoding="utf-8", errors="replace") as fh:
        m = re.search(r"int\s+LAST\s*=\s*(\d+)", fh.read())
    return int(m.group(1)) if m else None


def claims():
    for dirpath, _, filenames in os.walk(SCOPE):
        for name in sorted(filenames):
            if not name.endswith(".java"):
                continue
            path = os.path.join(dirpath, name)
            with open(path, encoding="utf-8", errors="replace") as fh:
                for number, line in enumerate(fh, 1):
                    clean = strip_quotes(line)
                    patterns = list(ALWAYS)
                    if re.search(r"tutorial", clean, re.I):
                        patterns += IN_TUTORIAL
                    for pattern in patterns:
                        for m in re.finditer(pattern, clean, re.I):
                            got = value(m.group(1))
                            if got is not None:
                                yield path, number, got, m.group(0)


def main():
    last = tutorial_last()
    if last is None:
        print("check-tutorial-counts: could not read `int LAST = ...` from %s" % TUTORIAL, file=sys.stderr)
        return 2
    if not os.path.isdir(SCOPE):
        print("check-tutorial-counts: no such scope: %s" % SCOPE, file=sys.stderr)
        return 2

    found = list(claims())
    stale = []
    for path, number, got, text in found:
        bad = got != last
        if bad:
            stale.append((path, number, got, text))
        print("  %-5s %s:%d  says %d  %r" % ("STALE" if bad else "ok", path[len(SCOPE) + 1:], number, got, text))

    print()
    print("  %d claim(s) about the tutorial   Tutorial.LAST: %d" % (len(found), last))
    if stale:
        print("  MISMATCH - %d claim(s) disagree with the code:" % len(stale), file=sys.stderr)
        for path, number, got, text in stale:
            print("    %s:%d says %d, LAST is %d  (%r)" % (path, number, got, last, text), file=sys.stderr)
        return 1
    print("=== every stated tutorial count agrees with Tutorial.LAST ===")
    return 0


if __name__ == "__main__":
    sys.exit(main())
