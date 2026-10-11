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
SCENES = re.compile(r"(\d+)\s+scenes?\b")
ENDINGS = re.compile(r"(\d+)\s+ending(?:s| scenes?)?\b")


def audited_counts():
    out = subprocess.run([str(ROOT / "tools" / "audit-stories.sh")], cwd=ROOT,
                         capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=300).stdout
    counts = {}
    for m in re.finditer(r"^  ([a-z0-9-]+)\s+\S+\s+\d+/(\d+) scene\(s\), (\d+) ending", out, re.M):
        counts[m.group(1)] = (int(m.group(2)), int(m.group(3)))
    return counts


def main():
    text = README.read_text()
    counts = audited_counts()
    if not counts:
        print("could not read the auditor's counts - run tools/audit-stories.sh and see why", file=sys.stderr)
        return 2

    problems = []
    seen_endings = set()
    for story, (scenes, endings) in sorted(counts.items()):
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
            found = SCENES.search(candidate)
            if found:
                window, m = candidate, found
                break
        if m is None:
            problems.append("%s: named but no scene count near it" % story)
            continue
        if int(m.group(1)) != scenes:
            problems.append("%s: README says %s scenes, the auditor counts %d" % (story, m.group(1), scenes))
        # AND THE ENDING COUNT IS NOT COMPARED, because the two sides mean different things by it. `the-water-line`
        # says "three endings" in its own header and the auditor counts ONE `-> END`, and both are right: the
        # branches converge on a single ending scene. Comparing them produced a false positive within an hour of the
        # tool existing, so this reports what it checks and says plainly what it does not - a check that quietly
        # covers less than its name suggests is the thing this week keeps finding.
        seen_endings.add((story, e.group(1) if (e := ENDINGS.search(window)) else "unknown"))

    if not problems:
        print("=== %d story(ies): every one is named, and every stated SCENE count matches ===" % len(counts))
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
