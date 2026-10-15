#!/usr/bin/env python3
"""Does the README still describe the stories that are actually here?

    tools/check-readme-stories.py

WHY THIS EXISTS. The release has had a readme-counts.sh for weeks and it has caught wrong numbers twice - "25 classes"
when the engine had 35, then "35 classes" when it had 32. THIS REPOSITORY HAD NOTHING, and its README carries a line
per story with its scene count and its ending count, which is the same kind of claim: a measurement written once and
never re-run. Three stories were added in three hours and the README did not know about any of them.

WHAT IT CHECKS, in both directions, because one of them is the quiet one:
  * every story in stories/ is NAMED in the README - the missing-story direction, which is how a library grows
    past its own description;
  * every scene count the README states MATCHES what the auditor counts - the wrong-number direction.

It reads the auditor rather than the files, so the number it compares against is the one the project already trusts.
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
README = ROOT / "README.md"
STORIES = ROOT / "stories"

# `- **`stories/night-shift.aside`** - ... 9 scenes, 3 endings.` and the many shapes around it: the scene count is
# the first `N scenes` after a story path, and the ending count is the `N ending(s)` anywhere on that line or the
# two that follow, because the prose wraps.
WORDS = {
    "one": 1,
    "two": 2,
    "three": 3,
    "four": 4,
    "five": 5,
    "six": 6,
    "seven": 7,
    "eight": 8,
    "nine": 9,
    "ten": 10,
    "eleven": 11,
    "twelve": 12,
    "thirteen": 13,
    "fourteen": 14,
    "fifteen": 15,
    "sixteen": 16,
    "seventeen": 17,
    "eighteen": 18,
    "nineteen": 19,
    "twenty": 20,
}

# AND THE COMPOUNDS, GENERATED RATHER THAN LISTED. This table stopped at "twenty", and a library of twenty-one
# stories then produced "no '**N stories**' line to check the total against" - A CHECKER THAT COULD NOT READ A
# CORRECT SENTENCE, which is worse than one that reads it and disagrees. The regex beside it also only accepted
# one word, so "Twenty-one" never reached the table at all. A list of ordinals inside a checker is the same fault
# as a list of stories inside a README: it goes stale the first time somebody forgets it.
_TENS = ["", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"]
_ONES = ["", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"]
for _t in range(2, 10):
    for _o in range(0, 10):
        WORDS.setdefault(_TENS[_t] if _o == 0 else "%s-%s" % (_TENS[_t], _ONES[_o]), _t * 10 + _o)

# WHERE A NUMBER AND ITS NOUN MAY BE SEPARATED BY A LINE BREAK, AND WHERE THEY MAY NOT.
#
# Two real cases pull in opposite directions, and both were measured:
#   * the README's prose WRAPS between a number and its noun ("21 scenes, 90" / "  beats, 17 choice options"), and a
#     pattern restricted to horizontal whitespace MISSED that claim - then read a neighbouring story's number from
#     further down the same window and reported `the-discrepancy: README says 113 beats, the auditor counts 90`;
#   * a report block quoted in the README ends a line with one count and starts the next with a LABEL
#     ("states expanded:     5296553" / "scenes:              131/131 reached"), and a plain `\s+` read that as a
#     claim of "5296553 scenes".
# A CONTINUATION LINE IS INDENTED AND A REPORT LABEL STARTS AT COLUMN ZERO, so the separator is horizontal
# whitespace, or a line break followed by indentation. Both cases now behave, and neither is a guess.
_W = r"(?:[^\S\n]+|\n[ \t]{2,})"

SCENES = re.compile(r"(\d+)" + _W + r"scenes?\b")
ENDINGS = re.compile(r"(\d+)" + _W + r"ending(?:s| scenes?)?\b")
# AND THE TWO OTHER NUMBERS THE PER-STORY LINES STATE. `the-water-line` was documented as 103 beats and has 113 -
# in the same section whose whole point is that these counts are audited rather than asserted. Nothing checked them
# because the tool did not print them; it does now, so they are checked here like the scene count is.
BEATS = re.compile(r"(\d+)" + _W + r"beats?\b")
OPTIONS = re.compile(r"(\d+)" + _W + r"choice options?\b")


def audited_counts():
    out = subprocess.run([str(ROOT / "tools" / "audit-stories.sh")], cwd=ROOT,
                         capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=300).stdout
    counts = {}
    # ONE REGEX PER NUMBER, on each per-story line, rather than one long pattern that has to match the whole line's
    # shape: the tool has three line shapes (`ok`, `all reached ...`, `ISSUES`) and a pattern over the whole line
    # would silently stop matching the day a fourth is added.
    for line in out.splitlines():
        head = re.match(r"^  ([a-z0-9-]+)\s+\S+\s+", line)
        if not head:
            continue
        sc = re.search(r"(\d+)/(\d+) scene\(s\)", line)
        en = re.search(r"(\d+) ending\(s\)", line)
        be = re.search(r"(\d+) beats\b", line)
        op = re.search(r"(\d+) options\b", line)
        if not sc:
            continue
        counts[head.group(1)] = (int(sc.group(2)), int(en.group(1)) if en else -1,
                                 int(be.group(1)) if be else -1, int(op.group(1)) if op else -1)
    return counts


def main():
    text = README.read_text()
    counts = audited_counts()
    # every story file, which is what a total counts - the auditor's list excludes its own fixture
    total_files = len(list(STORIES.glob("*.aside")))
    if not counts:
        print("could not read the auditor's counts - run tools/audit-stories.sh and see why", file=sys.stderr)
        return 2

    problems = []
    seen_endings = set()
    for story, (scenes, endings, beats, options) in sorted(counts.items()):
        if story not in text:
            problems.append("%s: not mentioned in the README at all" % story)
            continue
        # EVERY MENTION, not the first: the README names the stories in a list before it describes them, so the
        # first occurrence of a name has no count anywhere near it. The first version read that one and reported
        # nine stories as "named but no scene count near it" when every one of them was described further down -
        # a false positive in the direction of noise, which is the way a tool gets ignored.
        window, m = None, None
        for hit in re.finditer(re.escape(story), text):
            candidate = text[hit.start():hit.start() + 1400]
            # AND IT STOPS AT THE NEXT STORY'S BULLET. A fixed 1400-character window runs past the end of this
            # story's line and into the next one's, so a story that states no beat count was checked against a
            # NEIGHBOUR'S - which is what adding `the-lock` exposed, four stories at once reporting "README says
            # 100 beats, 6 choice options", because those were the-lock's numbers sitting further down the window.
            # A CLAIM BELONGS TO THE BULLET IT IS IN.
            nxt = candidate.find("- **`stories/")
            if nxt > 0:
                candidate = candidate[:nxt]
            found = SCENES.search(candidate)
            if found:
                window, m = candidate, found
                break
        if m is None:
            problems.append("%s: named but no scene count near it" % story)
            continue
        if int(m.group(1)) != scenes:
            problems.append("%s: README says %s scenes, the auditor counts %d" % (story, m.group(1), scenes))
        # THE SAME TREATMENT FOR THE OTHER TWO, and only when the line states them: most stories do not.
        b = BEATS.search(window)
        if b and beats >= 0 and int(b.group(1)) != beats:
            problems.append("%s: README says %s beats, the auditor counts %d" % (story, b.group(1), beats))
        o = OPTIONS.search(window)
        if o and options >= 0 and int(o.group(1)) != options:
            problems.append("%s: README says %s choice options, the auditor counts %d"
                            % (story, o.group(1), options))
        # AND THE ENDING COUNT IS NOT COMPARED, because the two sides mean different things by it. `the-water-line`
        # says "three endings" in its own header and the auditor counts ONE `-> END`, and both are right: the
        # branches converge on a single ending scene. Comparing them produced a false positive within an hour of the
        # tool existing, so this reports what it checks and says plainly what it does not - a check that quietly
        # covers less than its name suggests is the thing this week keeps finding.
        seen_endings.add((story, e.group(1) if (e := ENDINGS.search(window)) else "unknown"))

    # AND THE TOTAL, which is the number a reader trusts most and the one nobody re-runs. The per-story lines were
    # all correct twice while the sentence adding them up was wrong: "Eleven stories" when there were thirteen, and
    # "Fourteen" when there were sixteen. A document can be right in every particular and wrong in its summary.
    total_line = re.search(r"\*\*([A-Za-z]+(?:-[A-Za-z]+)?) stories\*\*", text)
    if not total_line:
        problems.append("no '**N stories**' line to check the total against")
    else:
        claimed = WORDS.get(total_line.group(1).lower())
        if claimed is None:
            problems.append("the total says '%s', which is not a number this check knows" % total_line.group(1))
        elif claimed != total_files:
            # AGAINST THE FILE COUNT, NOT THE AUDITED COUNT. The auditor skips night-shift, which is its own
            # fixture, so the audited list is one shorter than the library - and my first version of this compared
            # the README's total against that shorter number and reported "the README says Sixteen stories, and
            # there are 15" on a README that was right. The fault injection made it worse: changing the total to
            # Fifteen made the check PASS, because it had the wrong denominator.
            problems.append("the README says %s stories, and there are %d" % (total_line.group(1), total_files))

    if not problems:
        print("=== %d story(ies): every one is named, every stated SCENE count matches, and the total agrees ===" % total_files)
        print("ending counts are listed, not compared: the README counts branch endings and the auditor counts")
        print("ending scenes, and the two disagreed on the first story that had branches.")
        for story, claimed in sorted(seen_endings):
            print("    %-20s README says %s ending(s), the auditor counts %d" % (story, claimed, counts[story][1]))
        return 0
    for p in problems:
        print("  STALE  " + p)
    print("\n=== %d claim(s) in the README no longer match the stories ===" % len(problems))
    print("A count in a document is a measurement. A measurement nobody re-runs is a claim.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
