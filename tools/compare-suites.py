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

# THE TOOLCHAIN IS A LOOKUP, NOT A DEFAULT.
#
# This was `os.environ.get("JAVA", "/root/jdk-27+35/bin/java")` with the module path hardcoded to
# `/root/javafx-sdk-27/lib`. Neither path exists on the sandbox this runs on, so the tool died in
# subprocess.run with a traceback, printed no DIFFERS line, and its own self-test failed -- which is how this was
# found. It is the family this repository closed on 2026-10-07: a default that is a GUESS standing where a LOOKUP
# belongs, and tools/find-java.sh is the one place that looks. A tool written after the closure reintroduced it.
def _toolchain():
    here = os.path.dirname(os.path.abspath(__file__))
    try:
        r = subprocess.run(["bash", "-c", '. tools/find-java.sh >/dev/null 2>&1; printf "%s\n%s" "$JAVA" "$FX"'],
                           cwd=os.path.dirname(here), capture_output=True, text=True, timeout=120)
        lines = r.stdout.splitlines()
        if len(lines) >= 2 and lines[0] and lines[1]:
            return lines[0], lines[1]
    except Exception:
        pass
    return os.environ.get("JAVA", "java"), os.environ.get("FX", "")

JAVA, FX = _toolchain()
ENGINE = sys.argv[1] if len(sys.argv) > 1 else "/root/workspace/aside"
RELEASE = sys.argv[2] if len(sys.argv) > 2 else "/root/workspace/tp"
E_DIR = os.path.join(ENGINE, "src/main/java/aside/games/fruitjump/engine")
R_DIR = os.path.join(RELEASE, "src/main/java/tropical/engine")

# WHICH BUILD DIRECTORY THE RELEASE IS RUN FROM, AND WHY THIS IS NOT A DETAIL.
#
# It was hardcoded to "classes", which is NOT COMMITTED and NOT WHAT THE RELEASE'S OWN GATE PRODUCES - that gate
# compiles into "out". So this comparison was reading a build directory nothing in either repository refreshes, and
# it worked here only because an old `mutations.sh` run happened to leave one.
#
# MEASURED 2026-10-15: with classes/ present and current, 19 of 20 shared suites agree on their numbers. With
# classes/ absent, all 20 are reported as DISAGREEING - THE TOOL BLAMING THE CODE FOR A DIRECTORY THAT WAS NEVER
# BUILT, which is a failure with no fault behind it and the most expensive kind to chase.
#
# So: prefer what the gate produces, accept what a hand build leaves, and if neither is there, say so ONCE and exit
# 2. A comparison that did not happen is not a comparison that succeeded - the rule `check-fnaf-match.sh` already
# uses for a missing checkout.
def _release_build():
    for d in ("out", "classes"):
        p = os.path.join(RELEASE, d)
        if os.path.isdir(os.path.join(p, "tropical", "engine")):
            return d
    return None

if not os.path.isdir(R_DIR):
    print("=== the release is not checked out beside this one - nothing to compare ===")
    sys.exit(2)
R_BUILD = _release_build()
if R_BUILD is None:
    print("=== the release is not built - nothing to compare ===")
    print("  looked for out/ and classes/ under %s; run the release's gate first" % RELEASE)
    sys.exit(2)
NUMS = re.compile(r"(\d+)\s+(?:passed|checks?)\D+(\d+)\s+failed", re.I)

def run(repo, out, package, cls):
    cp = os.pathsep.join([out, os.path.join(repo, "src/main/resources")])
    cmd = [JAVA, "--module-path", FX,
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
    t, trc = run(RELEASE, R_BUILD, "tropical.engine.", cls)
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
