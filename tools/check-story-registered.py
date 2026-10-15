#!/usr/bin/env python3
"""
check-story-registered.py - can a player reach every story the repository ships?

    python3 tools/check-story-registered.py

WHY. A story can exist, be audited CLEAN, get a web build, get a shelf build, render into the contact sheet, and pass
every checker this repository has - AND STILL HAVE NO GAME REGISTERED IN `aside.game.Games`, which means no player can
open it. Nothing noticed that, and nothing could: every other step of the story chain LEAVES EVIDENCE (a build is a
file, the shelf is a file, the frames are files, the README is compared), while A REGISTRATION IS A LINE OF JAVA.

FOUND THE USUAL WAY. `the-plot`, the thirtieth story, went through the chain and the chain stopped - for the README's
count and for its own build, which is what the chain is for. The registration was mine to add by hand, and NOTHING
WOULD HAVE SAID SO IF I HAD FORGOTTEN. A story with no module is a story nobody can play, and the failure is silent
by construction - which is the shape this repository keeps finding.

WHAT IT CHECKS, all three by reading the source rather than by trusting a convention:
  1. some module under `games/` names `stories/<name>.aside`, so the story can be loaded;
  2. that module is added in `Games.java`, so the app will offer it;
  3. that module's own `id()` has a `case` in `PhoneShelf.java` with a line to show, so the shelf is not blank.
WHAT IT CANNOT SEE: whether any of it is any good. Presence, not correctness.
"""
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STORIES = os.path.join(ROOT, "stories")
GAMES = os.path.join(ROOT, "src/main/java/aside/game/Games.java")
SHELF = os.path.join(ROOT, "src/main/java/aside/game/PhoneShelf.java")

# SelfTest's own fault fixture: a story file on purpose, and deliberately not a game.
FIXTURE = {"night-shift"}


def module_for(story):
    """The module whose source names this story's path, or None."""
    hits = [p for p in glob.glob(os.path.join(ROOT, "src/main/java/aside/games/*/*.java"))
            if "stories/%s.aside" % story in open(p, encoding="utf-8").read()]
    return hits[0] if hits else None


def main():
    for p in (GAMES, SHELF):
        if not os.path.exists(p):
            print("check-story-registered: no %s" % os.path.relpath(p, ROOT), file=sys.stderr)
            return 2
    games = open(GAMES, encoding="utf-8").read()
    shelf = open(SHELF, encoding="utf-8").read()

    problems = []
    checked = 0
    for f in sorted(glob.glob(os.path.join(STORIES, "*.aside"))):
        story = os.path.basename(f)[:-6]
        if story in FIXTURE:
            continue
        checked += 1
        mod = module_for(story)
        if mod is None:
            problems.append("%s: no module names stories/%s.aside - NOTHING CAN OPEN THIS STORY" % (story, story))
            continue
        cls = os.path.basename(mod)[:-5]
        src = open(mod, encoding="utf-8").read()
        if not re.search(r"g\.add\(new\s+\S*\.?%s\(\)\)" % re.escape(cls), games):
            problems.append("%s: %s exists but Games.java never adds it - NOTHING CAN OFFER THIS STORY" % (story, cls))
            continue
        # the id the shelf is keyed on, taken from the module rather than guessed from the filename
        m = re.search(r'String\s+id\(\)\s*\{\s*return\s+"([^"]+)"', src)
        if not m:
            problems.append("%s: %s has no readable id() - the shelf key cannot be checked" % (story, cls))
            continue
        gid = m.group(1)
        if not re.search(r'case\s+"%s"\s*->' % re.escape(gid), shelf):
            problems.append("%s: PhoneShelf.java has no case \"%s\" - the shelf would show a blank line"
                            % (story, gid))

    if problems:
        print("=== %d of %d story(ies) cannot be reached by a player ===" % (len(problems), checked))
        for p in problems:
            print("    " + p)
        return 1
    print("=== %d story(ies), every one loadable, registered, and on the shelf ===" % checked)
    return 0


if __name__ == "__main__":
    sys.exit(main())
