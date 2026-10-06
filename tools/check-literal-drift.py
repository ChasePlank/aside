#!/usr/bin/env python3
"""
check-literal-drift.py - find lines that are the SAME in two copies of a project except for their NUMBERS.

    tools/check-literal-drift.py <dir-a> <dir-b> [--pairs]

WHY THIS EXISTS. On 6 October the release was found to fire arrows from `player.y - 10` where aside fires from
`player.y`, so arrows sailed over anything level with the player. The fix landed in aside on 1 October and never
reached the release, which shipped the bug for five days.

That drift was INVISIBLE to every check this repository has:
  - the file diffs differ by 179 lines, so one number does not stand out
  - there are no named constants involved, so a constants comparison finds nothing (verified: zero differences)
  - the gate compiles and passes on both copies, because both are valid code

It was a bare literal in a call that exists in both. So: normalise the numbers OUT to make a shape, and where the
SAME shape appears in both copies with DIFFERENT numbers, report it. That is the signature of a tuning change that
went one way, or a fix that never travelled.

Report only. A shape that differs by a number is a question, not a fault - some numbers legitimately differ between
the two because one is a standalone and one is hosted.
"""
import os, re, sys
from collections import defaultdict

NUM = re.compile(r'\b\d+(?:\.\d+)?\b')
WSP = re.compile(r'\s+')
# A number used as an OFFSET: `player.y - 10`, `x + 2`. Removed entirely for the second comparison, because the
# arrow bug was a literal present on ONE side and absent on the other - `player.y` against `player.y - 10` - and
# the exact-shape comparison can never match those, so it reported zero differences against a tree that had the bug.
OFFSET = re.compile(r'\s*[-+]\s*#')


def shape(line):
    """The line with every number removed, so two lines that differ only by numbers share a shape."""
    s = line.split('//')[0]
    s = NUM.sub('#', s)
    s = WSP.sub(' ', s).strip()
    return s


def numbers(line):
    return tuple(NUM.findall(line.split('//')[0]))


def collapsed(line):
    """The shape with numeric offsets removed: `player.y - 10` and `player.y` both become `player.y`."""
    return WSP.sub(' ', OFFSET.sub('', shape(line))).strip()


def lines_of(path):
    try:
        with open(path, errors='replace') as f:
            return [l.rstrip('\n') for l in f]
    except OSError:
        return []


def collect(root):
    """exact shape -> [(file, line, numbers, original)] for every line containing a number."""
    table = defaultdict(list)
    for dirpath, _dirs, files in os.walk(root):
        for name in sorted(files):
            if not name.endswith('.java'):
                continue
            full = os.path.join(dirpath, name)
            for i, ln in enumerate(lines_of(full), 1):
                if not NUM.search(ln) or not ln.strip():
                    continue
                sh = shape(ln)
                if not sh:
                    continue
                table[sh].append((name, i, numbers(ln), ln.strip()))
    return table


def collect_collapsed(root):
    """collapsed shape -> [(file, line, numbers, original)] for EVERY line, including those with no number at all.

    THE `NUM.search` GUARD HAD TO GO, and it is the difference between this check working and not. The arrow bug is
    `player.y` on one side and `player.y - 10` on the other: the aside line contains NO NUMBER, so a collection that
    requires one skipped it, the two lines never met, and the check reported zero against a tree that had the bug.
    A check for a literal that only one side has must look at the side that does not have it."""
    table = defaultdict(list)
    for dirpath, _dirs, files in os.walk(root):
        for name in sorted(files):
            if not name.endswith('.java'):
                continue
            for i, ln in enumerate(lines_of(os.path.join(dirpath, name)), 1):
                if not ln.strip():
                    continue
                key = collapsed(ln)
                if not key or key in ('{', '}', ');', ');'):
                    continue
                table[key].append((name, i, numbers(ln), ln.strip()))
    return table


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    a_dir, b_dir = sys.argv[1], sys.argv[2]
    a, b = collect(a_dir), collect(b_dir)

    # UNIQUE SHAPES ONLY. The first version compared every shared shape and reported seven differences, all of
    # them false: `gc.fillRect(#, #, #, #)` is one shape shared by dozens of unrelated calls, so the set of numbers
    # seen under it differs between the files without any single line having drifted. It printed the FIRST
    # occurrence of each, which is why the reported lines looked identical - they were. A shape that appears once
    # in each copy is unambiguously the same source line, and only there does a differing number mean anything.
    ambiguous = sum(1 for sh in a if sh in b and (len(a[sh]) > 1 or len(b[sh]) > 1))
    shared = [sh for sh in a if sh in b and len(a[sh]) == 1 and len(b[sh]) == 1]
    drift = []
    for sh in shared:
        if a[sh][0][2] != b[sh][0][2]:
            drift.append((sh, a[sh][0], b[sh][0]))

    print(f"  {len(a)} distinct numbered shape(s) in {a_dir}")
    print(f"  {len(b)} distinct numbered shape(s) in {b_dir}")
    print(f"  {len(shared)} shape(s) appear exactly once in EACH (comparable)")
    print(f"  {ambiguous} shape(s) appear more than once and are skipped as ambiguous")
    print(f"  {len(drift)} of those have DIFFERENT numbers")

    # PASS B: same line up to a numeric OFFSET that only one side has.
    ac, bc = collect_collapsed(a_dir), collect_collapsed(b_dir)
    offset_drift = []
    for key in ac:
        if key not in bc or len(ac[key]) != 1 or len(bc[key]) != 1:
            continue
        if ac[key][0][2] != bc[key][0][2]:
            offset_drift.append((key, ac[key][0], bc[key][0]))
    print(f"  {len(offset_drift)} line(s) match up to an offset number present on only ONE side")
    print()
    for sh, av, bv in drift:
        print(f"  {av[0]}:{av[1]}  {av[3]}")
        print(f"  {bv[0]}:{bv[1]}  {bv[3]}")
        print()
    for key, av, bv in offset_drift:
        print(f"  OFFSET  {av[0]}:{av[1]}  {av[3]}")
        print(f"          {bv[0]}:{bv[1]}  {bv[3]}")
        print()
    if not drift and not offset_drift:
        print("  nothing found: no shared line differs by a number, or by an offset one side has and the other")
    return 0


if __name__ == '__main__':
    sys.exit(main())
