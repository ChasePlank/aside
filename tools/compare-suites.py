#!/usr/bin/env python3
"""Run every suite both editions share, and compare the numbers the SUITE reports.

    python3 tools/compare-suites.py [engine-repo] [release-repo]

WHY NOT COMPARE THE GATE LOGS. I tried that first and it cannot work: the two gates surface different lines from the
same suite - the engine's gate prints the suite's own summary (`=== 14 passed, 0 failed ===`) and the release's
prints the last line matching its own filter (a `PASS:` check line, no numbers) - so comparing the logs compared
verdicts and almost nothing else. THIS RUNS THE SUITES and reads what each one says about itself, which is the claim
in the README: the editions are "aligned by their self-tests reporting the same numbers rather than by diffing text".

A suite that exists in only one edition is skipped, not reported as a failure: the two editions do not run the same
list, and the shared ones are the question.
"""
import os, re, subprocess, sys

JAVA = os.environ.get("JAVA", "/root/jdk-27+35/bin/java")
ENGINE = sys.argv[1] if len(sys.argv) > 1 else "/root/workspace/aside"
RELEASE = sys.argv[2] if len(sys.argv) > 2 else "/root/workspace/tp"
E_DIR = os.path.join(ENGINE, "src/main/java/aside/games/fruitjump/engine")
R_DIR = os.path.join(RELEASE, "src/main/java/tropical/engine")
NUMS = re.compile(r"(\d+)\s+(?:passed|checks?)\D+(\d+)\s+failed", re.I)

def run(repo, out, package, cls):
    cp = os.pathsep.join([out, os.path.join(repo, "src/main/resources")])
    cmd = [JAVA, "--module-path", "/root/javafx-sdk-27/lib",
           "--add-modules", "javafx.controls,javafx.graphics,javafx.media,javafx.swing",
           "-cp", cp, package + cls]
    env = dict(os.environ, DISPLAY=os.environ.get("DISPLAY", ":99"))
    r = subprocess.run(cmd, cwd=repo, capture_output=True, text=True, timeout=300, env=env)
    m = NUMS.search(r.stdout)
    return (m.group(1), m.group(2)) if m else None, r.returncode

SUITE = re.compile(r"(Test|Suite)\.java$")
shared = sorted(f[:-5] for f in os.listdir(E_DIR)
                if SUITE.search(f) and os.path.exists(os.path.join(R_DIR, f)))
print(f"suites in both editions: {len(shared)}")
by_numbers, by_verdict, mismatch, unnamed = [], [], [], []
for cls in shared:
    a, arc = run(ENGINE, "out", "aside.games.fruitjump.engine.", cls)
    t, trc = run(RELEASE, "classes", "tropical.engine.", cls)
    if a is not None and t is not None:
        (by_numbers if a == t else mismatch).append((cls, a, t))
    else:
        # NEITHER OF THESE IS "THE SAME NUMBERS", AND THEY ARE NOT THE SAME THING AS EACH OTHER:
        #   * one side reports no numbers at all -> the verdict is all there is to compare, and it IS compared
        #   * one side reports numbers and the other does not -> the two suites are not telling the same story,
        #     which is worth naming without calling it a disagreement about behaviour
        if arc == trc:
            by_verdict.append((cls, a, t, arc))
        else:
            mismatch.append((cls, a, t))
        if (a is None) != (t is None):
            unnamed.append(cls)
for cls, a, t, rc in by_verdict:
    print(f"  {cls:<22} verdict only (both exit {rc}; numbers: engine {a}, release {t})")
for cls, a, t in mismatch:
    ea = f"engine {a[0]}/{a[1]}" if a else "engine none"
    ra = f"release {t[0]}/{t[1]}" if t else "release none"
    print(f"  {cls:<22} DIFFERS: {ea}, {ra}")
extra = f" of which {len(unnamed)} report numbers on one side only" if unnamed else ""
print(f"\n=== {len(by_numbers)} agreed on numbers, {len(by_verdict)} agreed on the verdict alone{extra}, "
      f"{len(mismatch)} disagreeing ===")
sys.exit(1 if mismatch else 0)
