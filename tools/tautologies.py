#!/usr/bin/env python3
"""
Sweep every constant a file defines, in BOTH directions, and report which ones nothing pins.

    python3 tools/tautologies.py WaterSystem                    # all of them
    python3 tools/tautologies.py WaterSystem BUOYANCY           # one constant
    python3 tools/tautologies.py --list

WHY. Rule 2 in [[reference/verification-discipline.md]]: a test that compares a measurement to the constant that
defines it is a tautology - it passes every mutation, because both sides move together. `tools/mutations.sh` holds
the invariants I already know are worth breaking; this finds the ones I do not know about yet.

READING THE OUTPUT, which is the whole skill. "UNCONSTRAINED" means *nothing pins this*, and there are three very
different reasons:

  * A TAUTOLOGY - a check that appears to test it while comparing the measurement to the constant. A broken check.
  * A FREE PARAMETER - gravity is the example. Both sides of any relation scale with it, so no relation CAN pin it,
    and it should stay unconstrained like any other tuning value.
  * A CONSTANT NOTHING USES YET, or one whose only consumer is a different suite.

THE SWEEP FLAGS; I DECIDE. Good instruments narrow the judgement; they do not replace it.

AND BOTH DIRECTIONS, which the first version of this got wrong on the branch: sweeping only upward missed a jump
buffer whose check asserts something still works, because a bigger buffer never fails that. The instrument had the
blind spot, not the test.

It reuses tools/mutate.sh for the breaking and the restoring rather than doing its own - that tool already verifies
the mutation APPLIED, which matters more than it sounds: a mutation that never applied reads exactly like one the
suite cannot see.
"""
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent.parent
ENGINE = HERE / "src/main/java/aside/games/fruitjump/engine"
CONST = re.compile(r"^\s*(?:public |private |protected )?static final (double|int) ([A-Z_0-9]+) = ([0-9.]+);")


def constants_in(path: pathlib.Path):
    out = []
    for i, line in enumerate(path.read_text().split("\n"), 1):
        m = CONST.match(line)
        if m:
            out.append((m.group(2), m.group(3), line.strip()))
    return out


def variants(value: str):
    """Three replacements: bigger, smaller, and ABSENT.

    THE THIRD ONE IS THE ONE THAT MATTERS MOST. "Nothing pins this" and "nothing tests this" are different claims,
    and the first is usually fine: a speed, a distance and a duration are tuning values, and a project that pinned
    every one of them would be pinning its own numbers instead of its behaviour. The question worth asking is
    whether a check notices the thing being switched OFF - and zero is what "off" looks like for a speed, a range, a
    duration or a cooldown.

    A constant whose zero is caught and whose doubling is not is a FREE PARAMETER with its behaviour pinned, which
    is the healthy answer, and reporting it as "unconstrained" would send the next session hunting a tautology that
    is not there. A constant whose ZERO is not caught is a real finding.
    """
    v = float(value)
    if v == 0:
        return ["1.0", "-1.0", "0.0"]
    return [repr(v * 2), repr(v / 2), "0.0"]


def main() -> int:
    args = [a for a in sys.argv[1:]]
    if not args or args[0] == "--list":
        for f in sorted(ENGINE.glob("*.java")):
            cs = constants_in(f)
            if cs:
                print("  %-24s %d" % (f.name, len(cs)))
        print("\n  usage: python3 tools/tautologies.py <File.java-name> [CONSTANT] <suite-class>")
        return 0
    name = args[0]
    only = args[1] if len(args) > 2 else None
    suite = args[-1] if len(args) > 1 and ("." in args[-1] or args[-1][0].isupper()) else None
    if suite in (name, only):
        suite = None
    suite = suite or "aside.games.fruitjump.engine.WaterSuite"

    path = ENGINE / (name if name.endswith(".java") else name + ".java")
    if not path.exists():
        print("no such file: %s" % path, file=sys.stderr)
        return 2

    cs = [c for c in constants_in(path) if not only or c[0] == only]
    print("  %s: %d constant(s), suite %s" % (path.name, len(cs), suite))
    unconstrained = []
    for cname, cvalue, cline in cs:
        verdicts = []
        for repl in variants(cvalue):
            out = subprocess.run(["tools/mutate.sh", suite, str(path.relative_to(HERE)), cline, cline.replace(cvalue, repl)],
                                 cwd=HERE, capture_output=True, text=True)
            text = out.stdout + out.stderr
            if "ANCHOR MISSING" in text:
                verdicts.append("not-applied")
            elif "NOT CAUGHT" in text:
                verdicts.append("unconstrained")
            else:
                verdicts.append("caught")
        absent_caught = verdicts[-1] == "caught"
        if all(v == "caught" for v in verdicts):
            print("  %-28s %s pinned" % (cname, cvalue))
        elif not absent_caught:
            print("  %-28s %s NOTHING NOTICES IT BEING SWITCHED OFF  (%s)" % (cname, cvalue, ", ".join(verdicts)))
            unconstrained.append(cname)
        elif "not-applied" in verdicts:
            print("  %-28s %s NOT APPLIED - this proves nothing" % (cname, cvalue))
            unconstrained.append(cname)
        elif "caught" in verdicts:
            # ONE DIRECTION CAUGHT IS A REAL ANSWER, and a better one than "pinned" or "unconstrained": the CLAIM the
            # constant carries is pinned and its MAGNITUDE is free. STUNNED_MULTIPLIER is the example - halving it
            # makes a stunned recovery no longer than a normal one, which a check catches; doubling it makes the stun
            # longer still, which nothing SHOULD catch, because the comment claims "lasts longer" and not "twice as
            # long". Reporting that as unconstrained would send the next session hunting a tautology that is not
            # there - which is the failure this whole file exists to avoid, one level up.
            print("  %-28s %s claim pinned, magnitude free  (%s)" % (cname, cvalue, ", ".join(verdicts)))
        else:
            print("  %-28s %s UNCONSTRAINED  (%s)" % (cname, cvalue, ", ".join(verdicts)))
            unconstrained.append(cname)

    print()
    if not unconstrained:
        print("  OK every constant in %s is pinned by at least one check in %s" % (path.name, suite))
        return 0
    print("  %d of %d constant(s) nothing pins: %s" % (len(unconstrained), len(cs), ", ".join(unconstrained)))
    print("  A tautology, a free parameter, or an unused value - the sweep flags, and that judgement is mine.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
