#!/usr/bin/env python3
"""
find-write-only.py - a value that something WRITES and nothing READS.

    tools/find-write-only.py [source-root]

WHY THIS EXISTS. Twice in one week the same fault was found by hand: a system had finished machinery and no
recognition of it. `RoomWorld` picked a start room, walked a guaranteed path to an exit room and published
`exitRoomId` - and nothing outside that file read it, so crossing into the goal did nothing. And the platformer had
a final level, a dusk ramp and a way to end a run, and no ending. Both were found by asking "who reads this?", and
both took a while because the question was asked of one thing at a time.

This asks it of every field at once. A field that is written and never read is a PROMISE WITH NO PAYOFF, and it is
the cheapest mechanical form of the question: a value that something bothered to compute, keep up to date, or set
deliberately, and which no other code acts on.

FIELDS AND LOCALS BOTH, and the second is not scope creep. The report was written for fields - `exitRoomId` is
one - and the first sweep of the real code turned up `World.isOneway`, a LOCAL in the collision sweep that is
declared, assigned in four branches and read nowhere. That is dead in exactly the same way and for the same reason:
something computes it and nothing consults it. Locals are matched by the same declaration rule, which is why the
count says "value" and not "field".

WHAT IT IS NOT. It is a REPORT, not a gate, for the reason the wiring report gives: dead state is a decision - wire
it or delete it - and a report that fails every run gets turned off. A finding here is a question. A NEW one is
worth a look; the ones below were found on 2026-10-08 and are characterised in the briefing.

WHAT COUNTS AS A READ. Any mention of the name that is not an assignment, an increment or a compound assignment, in
any non-test source file. So a field read only in its own constructor or only by a test is NOT reported: that
distinction is the difference between "nothing reads this" and "only the thing that made it reads this", and it is
the one that matters here.

SCOPE. The engine package, the same scope the wiring report uses. Test and Suite and Probe files are excluded
throughout: a field only a test can read is a field the player cannot.
"""
import glob
import os
import re
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
ENGINE = os.path.join(SRC, "aside/games/fruitjump/engine")

DECL = re.compile(r"^\s*(?:public |private |protected |static |final |transient |volatile )*"
                  r"([A-Za-z_][A-Za-z0-9_<>\[\], .]*?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:=[^=]|;)")
NOT_A_TYPE = {"void", "return", "if", "for", "while", "new", "class", "enum", "record", "interface"}
NOT_A_NAME = {"class", "enum", "record", "interface"}


def is_test(path):
    return re.search(r"(Test|Suite|Probe)\.java$", path) is not None


def declared_fields():
    """(class, name, path, line-number) for every field in the engine, including `Type a, b;` lines."""
    found = []
    for f in sorted(glob.glob(ENGINE + "/*.java")):
        if is_test(f):
            continue
        for lineno, line in enumerate(open(f, encoding="utf-8", errors="replace"), 1):
            if re.match(r"^\s*(//|\*|/\*)", line):
                continue
            for m in DECL.finditer(line):
                type_part, name = m.group(1), m.group(2)
                if type_part.split()[-1] in NOT_A_TYPE or name in NOT_A_NAME:
                    continue
                found.append((os.path.basename(f)[:-5], name, f, lineno))
                # `public String startRoomId, exitRoomId;` declares two, and this is the line shape that hid the
                # bug this tool was written for - a hand sweep with a simpler pattern missed it entirely.
                head = line.split(";")[0].split("=")[0]
                for extra in re.findall(r",\s*([A-Za-z_][A-Za-z0-9_]*)", head):
                    found.append((os.path.basename(f)[:-5], extra, f, lineno))
    return found


SOURCES = None


INDEX = None


def index():
    """name -> [(path, line-number, is_write)] over every non-test line, built in ONE pass.

    The second version read the tree once per RUN rather than once per value, and still took 68 seconds: the cost
    is 753 values x ~20k lines of two regular expressions each, which is tens of millions of matches. Counting each
    identifier once and looking values up in that is the same answer in about a second.
    """
    global INDEX
    if INDEX is None:
        INDEX = {}
        ident = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
        assign = re.compile(r"\s*(=[^=]|\+\+|--|\+=|-=|\*=|/=)")
        for f, lines in sources():
            for lineno, line in enumerate(lines, 1):
                for m in ident.finditer(line):
                    name = m.group(0)
                    is_write = assign.match(line[m.end():]) is not None
                    INDEX.setdefault(name, []).append((f, lineno, is_write))
    return INDEX


def sources():
    """Every non-test source file, read ONCE.

    The first version re-read the whole tree for every value it checked, which is 753 x 60 files and takes minutes
    - long enough that it had to be killed. Reading each file once and counting all the values against that is the
    same answer in a second.
    """
    global SOURCES
    if SOURCES is None:
        SOURCES = []
        for f in sorted(glob.glob(SRC + "/**/*.java", recursive=True)):
            if is_test(f):
                continue
            SOURCES.append((f, open(f, encoding="utf-8", errors="replace").readlines()))
    return SOURCES


def reads_and_writes(name, decl_path, decl_line):
    """Reads and writes of a field, NOT counting its own declaration.

    THE DECLARATION LINE IS NOT A READ, and the first version of this counted it as one: a field declared without
    an initialiser (`boolean armed;`) has its name on a line that is not an assignment, so it looked read, and a
    field that was DECLARED AND ASSIGNED AND NEVER USED came out clean. That is precisely the fault this tool was
    written for. `exitRoomId` - declared, assigned in the constructor, read nowhere - would have been missed.
    Found by injecting a write-only field on purpose and watching the tool not report it, which is the whole reason
    for fault-injecting a check before trusting it.
    """
    reads = writes = 0
    for f, lineno, is_write in index().get(name, ()):
        if True:
            if f == decl_path and lineno == decl_line:
                # ITS OWN DECLARATION IS A WRITE IF IT HAS AN INITIALISER AND NEVER A READ. Excluding the line
                # entirely was the second version and it lost the dead constants - `Boss.maxPhase = 3` with nothing
                # reading it is a value no code consults, which is the whole question. Counting it as a READ was
                # the first version and it hid the fields that are declared bare, assigned once, and never used.
                if is_write:
                    writes += 1
                continue
            if is_write:
                writes += 1
            else:
                reads += 1
    return reads, writes


def main():
    if not os.path.isdir(ENGINE):
        print("find-write-only: no engine package at " + ENGINE, file=sys.stderr)
        return 2

    seen = set()
    findings = []
    for cls, name, path, lineno in declared_fields():
        if (cls, name) in seen:
            continue
        seen.add((cls, name))
        reads, writes = reads_and_writes(name, path, lineno)
        if writes > 0 and reads == 0:
            findings.append((cls, name, writes))

    print("=== values something WRITES and nothing READS ===")
    if not findings:
        print("  none")
    for cls, name, writes in sorted(findings):
        print("  %-22s %-24s %d write(s), 0 read(s)" % (cls, name, writes))
    print()
    print("  %d value(s) declared; %d write-only." % (len(seen), len(findings)))
    print()
    print("A finding here is a QUESTION, not a fault: a value may be kept for a test, for a debug readout, or for")
    print("something about to be written. What it cannot be is a thing the game acts on - something computes it and")
    print("nothing consults it, which is the shape of a goal no code recognises.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
