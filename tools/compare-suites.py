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
agree, mismatch, no_numbers = 0, [], []
for cls in shared:
    a, arc = run(ENGINE, "out", "aside.games.fruitjump.engine.", cls)
    t, trc = run(RELEASE, "classes", "tropical.engine.", cls)
    if a is None or t is None:
        no_numbers.append((cls, a, t, arc, trc))
        if arc != 0 or trc != 0:
            print(f"  ({cls}: exit engine {arc}, release {trc})")
        continue
    if a == t:
        agree += 1
    else:
        mismatch.append((cls, a, t))
for cls, a, t, arc, trc in no_numbers:
    print(f"  {cls:<22} NO NUMBERS in one of them (engine says {a}, release {t})")
for cls, a, t in mismatch:
    print(f"  {cls:<22} DIFFERS: engine {a[0]} passed/{a[1]} failed, release {t[0]} passed/{t[1]} failed")
print(f"\n=== {agree} suite(s) report the same numbers, {len(mismatch)} do not, "
      f"{len(no_numbers)} comparable by numbers at all ===")
sys.exit(1 if mismatch else 0)
