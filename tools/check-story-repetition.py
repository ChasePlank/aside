#!/usr/bin/env python3
"""
check-story-repetition.py - does a distinctive phrase appear in two different stories?

    python3 tools/check-story-repetition.py [stories-dir]

WHY. Twenty-nine stories, five and a half thousand lines, one author. Self-repetition is the risk nobody checks, and
it is invisible by construction: each story is read on its own, so a sentence copied from one into another reads as
voice in both.

THE UNIT IS THE SCENE, AND THE COMPARISON IS ACROSS STORIES. Both halves of that matter, and both were measured
before this tool existed:

  * PER SCENE, because joining a whole story lets a run span a SCENE BOUNDARY - two unrelated sentences that happen
    to be adjacent then look like a repetition. The first measurement joined by file and produced hits like "and you
    close the book and you sit in the" that are not repetitions at all.
  * CROSS-STORY ONLY, because **339 shared runs in this library are WITHIN one story** - sibling endings that tell
    the same night several ways, deliberately (the-stage-door's three endings share a whole passage; so do
    the-lookout's two, bell-codes' three, changeover's two). **That is craft, and a check that flagged it would be
    firing on valid content**, which is the failure this project keeps finding.

WHY TEN WORDS. Measured across the library on 2026-10-15: at 10 words there were 8 cross-story runs (4 of them
accidental, since varied); at 12 words there was 1; at 14 there were none. Ten is where coincidence stops being
plausible, and the four accidental ones - "the bell over the door does what it does", eleven words, in two stories -
are exactly what this is for.

WHAT IT CANNOT SEE, said plainly rather than left implied: it compares WORD SEQUENCES, so a paraphrase of another
story's sentence passes, and so does a repeated IMAGE (the same bell, the same kettle) built from different words.
It catches copy, not tics. And it reads narration only - dialogue is excluded, because a shared line of dialogue
between two stories is a quotation or a habit of speech rather than a copy of prose.
"""
import glob
import os
import re
import sys

MIN_WORDS = 10

# DELIBERATE, AND ALLOWED BY NAME. "The morning comes the way mornings do: without ceremony and slightly early"
# opens the alternate dawn endings of both the-lamp-room and the-last-crossing - the same morning, two ways. A run
# that falls INSIDE an allowed sentence is allowed with it, which is what makes the windows of it quiet too.
ALLOWED = [
    "the morning comes the way mornings do without ceremony and slightly early",
]

DIRECTIVES = ("#", "==", "title:", "author:", "start:", "bg ", "music ", "sfx ", "show ",
              "*", "->", "~", "if ")


def scenes(path):
    """(scene-name, narration-with-punctuation, words, source-offsets) per scene."""
    cur, name = [], None
    out = []

    def emit(n, lines):
        text = " ".join(lines)
        words, offs = [], []
        for m in re.finditer(r"[A-Za-z']+", text):
            words.append(m.group(0).lower())
            offs.append(m.start())
        out.append((n, text, words, offs))

    for line in open(path, encoding="utf-8"):
        s = line.strip()
        if s.startswith("== "):
            if name is not None:
                emit(name, cur)
            name, cur = s[3:].strip(), []
            continue
        if not s or s.startswith(DIRECTIVES):
            continue
        if re.match(r"^[a-z_]+:\s", s):      # dialogue
            continue
        cur.append(s)
    if name is not None:
        emit(name, cur)
    return out


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "stories"
    files = sorted(glob.glob(os.path.join(root, "*.aside")))
    if not files:
        print("check-story-repetition: no stories in %s" % root, file=sys.stderr)
        return 2

    grams = {}
    store = {}
    for f in files:
        story = os.path.basename(f)[:-6]
        for scene, text, words, offs in scenes(f):
            store[(story, scene)] = (text, words, offs)
            for i in range(len(words) - MIN_WORDS + 1):
                grams.setdefault(" ".join(words[i:i + MIN_WORDS]), set()).add((story, scene))

    cross = {g: s for g, s in grams.items() if len({st for st, _ in s}) > 1}
    problems = []
    allowed_hits = 0
    reported = set()
    for g, where in sorted(cross.items()):
        if any(g in a for a in ALLOWED):
            allowed_hits += 1
            continue
        pair = tuple(sorted({st for st, _ in where}))
        if pair in reported:
            continue
        reported.add(pair)
        # NAME THE ACTUAL TEXT, not the normalised run: the run has no punctuation in it, so a reader who greps
        # for it will find nothing and conclude the tool is wrong. (I did exactly that, to myself, on 2026-10-15.)
        st, sc = sorted(where)[0]
        text, words, offs = store[(st, sc)]
        idx = next(i for i in range(len(words) - MIN_WORDS + 1)
                   if " ".join(words[i:i + MIN_WORDS]) == g)
        a = offs[max(0, idx - 4)]
        b = offs[min(len(words) - 1, idx + MIN_WORDS + 3)]
        problems.append("%s and %s share a %d-word run:\n      ...%s..."
                        % (pair[0], pair[1], MIN_WORDS, re.sub(r"\s+", " ", text[a:b]).strip()))

    print("=== %d story(ies), %d cross-story run(s) of %d+ words, %d allowed by name ==="
          % (len(files), len(cross), MIN_WORDS, allowed_hits))
    if problems:
        print("=== %d pair(s) share a distinctive phrase ===" % len(problems))
        for p in problems:
            print("    " + p)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
