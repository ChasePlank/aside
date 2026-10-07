#!/usr/bin/env python3
"""
find-drift.py - compare two copies of the same source file and say what has drifted or gone missing.

    tools/find-drift.py <file-a> <file-b> [--near 0.90] [--gap 0.80] [--limit N]

WHY THIS EXISTS, and why it is a file rather than a habit.

The platformer exists twice: `aside` is the source of truth and `ChasePlank/Fruit-Jump` is the public release.
Seven files are wiring files a sync script cannot carry, and the screens are where the two drift apart. On
2026-10-05 and -06 this comparison found SIX real bugs that no gate had ever caught, every one of them live in a
release people download:

    the safe-room JAR pickup   the release drew it with the key sprite, because the branch was missing
    hitFlash                   the release had NO damage feedback at all - a hit was silent
    playerBlastPending         the release could not be hurt by its own bomb, while tutorial 3 says STAND BACK
    stunTimer / KNEEL_UNTIL    a bat knocked you down and NOTHING happened, while tutorial 5 says it does
    drainDrownTicks            running out of air did nothing, and there was no meter
    the enemy kind             EVERY enemy was a spider: the snake did not exist in the release at all

It was written from scratch three times in two days - once for FNAF, once for the platformer screens, once more
after the sandbox wiped /tmp - and each rewrite was slightly different. This project's own rule is that a lesson
recorded once is a note and a lesson recorded twice is a tool. Three times is not a judgement call.

TWO REPORTS, because drift and absence are different failures:

  NEAR MISS     a line that exists in both but differs a little. This is where a fix that went one way and not
                the other hides - a number, a sprite name, a condition. It is also where variable renames show up
                (`sign` vs `s`), which is why this is a report to read and not a gate.

  NO COUNTERPART  a line in A with nothing remotely like it in B. This is where MISSING FEATURES live. Six for
                six of the bugs above were found here.

Comment-only and blank-line differences are noise in both reports, so comments are stripped before comparing. A
file that differs only in its comments reports clean, which is correct - this is about behaviour.

Usage note: run it with A = the source of truth, so "no counterpart" means "missing from B".
"""
import argparse, difflib, os, re, sys

def strip_comment(line):
    # no string-literal awareness on purpose: these are game sources and a `//` inside a string is rare enough
    # that quoting it in the report is more useful than silently missing a real difference.
    return re.sub(r'\s+', ' ', line.split('//')[0]).strip()


def load(path):
    out = []
    for i, raw in enumerate(open(path, errors='replace'), 1):
        t = strip_comment(raw)
        if t and not t.startswith(('package ', 'import ', '*', '/*')):
            out.append((i, t))
    return out


def main():
    ap = argparse.ArgumentParser(add_help=True)
    ap.add_argument('a'); ap.add_argument('b')
    ap.add_argument('--near', type=float, default=0.90, help='ratio at or above which two lines are a near miss')
    ap.add_argument('--gap', type=float, default=0.80, help='below this, a line has no counterpart at all')
    ap.add_argument('--limit', type=int, default=20)
    args = ap.parse_args()

    for p in (args.a, args.b):
        if not os.path.isfile(p):
            print(f"find-drift: no such file: {p}", file=sys.stderr)
            return 2

    A, B = load(args.a), load(args.b)
    bset = {t for _, t in B}
    blines = [t for _, t in B]

    near, gaps, changed = [], [], []
    for i, t in A:
        if t in bset:
            continue
        best, ratio = None, 0.0
        for m in blines:
            r = difflib.SequenceMatcher(None, t, m).ratio()
            if r > ratio:
                best, ratio = m, r
        if ratio >= args.near:
            near.append((ratio, i, t, best))
        elif ratio < args.gap:
            gaps.append((i, t))
        else:
            # THE BAND BETWEEN THE TWO THRESHOLDS, WHICH USED TO BE DROPPED.
            #
            # A line whose best match falls between --gap and --near was reported NOWHERE: not as a gap, not as a
            # near miss. On 2026-10-07 that hid a real divergence - the release's LevelValidator had gained
            # "player.grounded && " in front of a condition, nineteen characters added to a sixty-character line,
            # a similarity of about 0.75 - and this tool said "0 line(s) with NO counterpart, 0 near miss(es)" and
            # "nothing: the two differ only in comments, imports, or not at all" for a file that had diverged.
            #
            # The band is exactly where small edits to long lines live, which is most real drift. It is reported
            # as its own list now, because a silent band is worse than either threshold being wrong.
            changed.append((ratio, i, t, best))

    print(f"  {os.path.basename(args.a)}: {len(A)} line(s)   {os.path.basename(args.b)}: {len(B)} line(s)")
    print(f"  {len(gaps)} line(s) with NO counterpart in B   {len(near)} near miss(es)"
          f"   {len(changed)} changed between the thresholds\n")

    if gaps:
        print("  --- NO COUNTERPART (A has it, B does not) ---")
        for i, t in gaps[:args.limit]:
            print(f"  A:{i:<5} {t[:112]}")
        if len(gaps) > args.limit:
            print(f"  ... and {len(gaps) - args.limit} more")
        print()

    if near:
        print("  --- NEAR MISS (same line, changed) ---")
        for r, i, t, m in sorted(near, reverse=True)[:args.limit]:
            print(f"  {r:.2f}  A:{i}")
            print(f"        A  {t[:104]}")
            print(f"        B  {m[:104]}")
        print()

    if changed:
        print("  --- CHANGED, BETWEEN THE THRESHOLDS (neither a gap nor a near miss) ---")
        for r, i, t, m in sorted(changed, reverse=True)[:args.limit]:
            print(f"  {r:.2f}  A:{i}")
            print(f"        A  {t[:104]}")
            print(f"        B  {(m or '')[:104]}")
        print()

    if not gaps and not near and not changed:
        print("  nothing: the two differ only in comments, imports, or not at all")
    return 0


if __name__ == '__main__':
    sys.exit(main())
