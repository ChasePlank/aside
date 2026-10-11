#!/usr/bin/env python3
"""Which lists of words does this repository spell out more than once?

    tools/find-duplicated-lists.py [root ...]

WHY THIS EXISTS. Four times in one week the defect was two lists that must agree: a mutation entry's anchors and the
code, a README's line counts and the file, the two editions of the platformer engine, and a regex beside a `case`
statement. Every one was found by accident while doing something else. This looks for the shape on purpose.

WHAT IT IS FOR, AND WHAT IT IS NOT. Most of what it reports is COPIES BY DESIGN - each game in this library is its own
copy of the engine, so the same enum appears in four FNAF games and that is the model, not a defect. Its value is
saying that in one screen instead of one afternoon, and surfacing the pairs that nothing compares: it found
aside/ui/Audio.java against the standalone's copy, which had drifted.

ITS OUTPUT IS CANDIDATES, NOT FINDINGS. One of the first run's hits was a fillRoundRect and a strokeRoundRect of the
same rectangle - the same four constants used twice, legitimately. Read the list; do not fix it wholesale.
"""
import collections
import pathlib
import re
import sys

SUFFIXES = {".sh", ".py", ".java", ".js", ".mjs"}
LIST = re.compile(r"\(?([A-Z][A-Z0-9_]{2,}(\s*[|,]\s*[A-Z][A-Z0-9_]{2,}){2,})\)?")


def lists_in(path):
    try:
        text = path.read_text(errors="ignore")
    except OSError:
        return
    for m in LIST.finditer(text):
        words = tuple(sorted(set(re.split(r"\s*[|,]\s*", m.group(1)))))
        if len(words) >= 3:
            yield words, text[: m.start()].count("\n") + 1


def main(roots):
    found = collections.defaultdict(list)
    for root in roots:
        for path in pathlib.Path(root).rglob("*"):
            if path.is_file() and path.suffix in SUFFIXES:
                for words, line in lists_in(path):
                    found[words].append((str(path), line))
    repeats = 0
    for words, places in sorted(found.items(), key=lambda kv: -len(kv[1])):
        if len(places) < 2:
            continue
        repeats += 1
        print("%d places, %d words: %s" % (len(places), len(words), " ".join(words)[:70]))
        for path, line in places[:6]:
            print("    %s:%d" % (path, line))
    print("\n=== %d list(s) appear in more than one place ===" % repeats)
    print("Most will be copies by design. The ones worth an hour are the pairs nothing compares.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or ["tools", "src"]))
