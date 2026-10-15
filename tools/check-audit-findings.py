#!/usr/bin/env python3
"""
check-audit-findings.py - does the README's table of findings still match what the auditor prints?

    python3 tools/check-audit-findings.py

WHY. The README lists the auditor's findings in a table, and THAT TABLE IS A CLAIM NOBODY RE-RUNS. On 2026-10-15
it was wrong in BOTH DIRECTIONS AT ONCE:

  * it did not name `conditions that cannot fail`, which had just been added to the auditor; and
  * it described `choices that do not matter` as "NOT IN THIS TREE - on origin/roxanne/audit-verdict" while the
    auditor had been printing it for six days.

THE SECOND ONE IS THE INVERSE OF THE FAULT THE README'S OWN PARAGRAPH WARNS ABOUT. That paragraph is about a
document advertising something the tree does not have; this was a document disowning something the tree does have,
and it is just as expensive, because a reader who believes it will not look at what the line reports. The
paragraph's own remedy is the one applied here: re-run the claim.

HOW. It reads the labels out of the report builder in `Bot.java` rather than running the auditor, because the
report also has labels that only print under conditions (the `warnings` section) and because a checker that needs
a JDK, a display and a fixture to answer a question about a table is a checker that will be skipped. The shape it
looks for is the one every finding uses: `sb.append("<label>:  ").append(<something>.size())`.

WHAT IT CANNOT SEE, said plainly rather than left implied: it compares LABELS, not prose. A row that names the
right finding and describes it wrongly still passes. The two failure modes it does catch are the two that happened.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BOT = ROOT / "src/main/java/aside/engine/Bot.java"
README = ROOT / "README.md"

# Labels the report prints that are NOT findings - measurements about the walk, and the section that carries the
# parser's and the walk's own remarks. Adding to this list is how you say "this line is not a finding", and it is
# deliberately the smaller list: the default is that something the auditor prints must be documented.
COUNTERS = {
    "paths explored",
    "states expanded",
    "scenes",
    "distinct endings",
    "loops",
    "warnings",
}


def printed_labels():
    src = BOT.read_text()
    return {
        m.group(1).strip().rstrip(":")
        for m in re.finditer(r'sb\.append\("([a-z][a-z ,-]*):', src)
    }


def documented_labels():
    """Every backticked name in the README's findings table, which is the table under 'What it reports'."""
    text = README.read_text()
    start = text.find("**What it reports, and which findings you can trust:**")
    if start < 0:
        sys.exit("check-audit-findings: the README no longer has that heading - it was renamed or removed")
    end = text.find("**The verdict tells you which.**", start)
    table = text[start:end if end > 0 else start + 4000]
    names = set()
    for row in table.splitlines():
        if not row.startswith("|"):
            continue
        # THE FIRST CELL ONLY, because that is where findings are named. The first version read every backticked
        # span in the row - so a description that quoted the text of a `warnings` line ("ran out of memory after N
        # states expanded") was read as a documented finding that the auditor does not print. A CHECKER TOO BROAD
        # TO DESCRIBE ITS OWN MATERIAL is the same fault one level up, and it showed up on the first run.
        first = row.split("|")[1] if row.count("|") >= 2 else ""
        for cell in re.findall(r"`([^`]+)`", first):
            names.add(cell.strip())
    return names, table


def main():
    printed = printed_labels()
    if not printed:
        sys.exit("check-audit-findings: found no report labels in Bot.java - the report was rewritten, and this "
                 "check has been reading nothing since. THAT IS THE FAILURE THIS TOOL EXISTS TO CATCH, in itself.")
    documented, table = documented_labels()
    findings = sorted(printed - COUNTERS)

    problems = []
    for f in findings:
        if f not in documented:
            problems.append("the auditor prints '%s' and the README's table does not name it" % f)
    for d in sorted(documented):
        if d not in printed:
            problems.append("the README's table names '%s' and the auditor does not print it" % d)
    # AND THE ANNOTATION THAT WAS WRONG: a row disowning a finding the tree has.
    for row in table.splitlines():
        if "NOT IN THIS TREE" in row:
            for cell in re.findall(r"`([^`]+)`", row):
                if cell.strip() in printed:
                    problems.append("the table says '%s' is NOT IN THIS TREE, and Bot.java prints it" % cell.strip())

    if problems:
        print("=== %d claim(s) about the auditor's findings no longer match it ===" % len(problems))
        for p in problems:
            print("  STALE  " + p)
        print("  The table is at 'What it reports, and which findings you can trust' in README.md.")
        return 1
    print("=== %d finding(s) printed, %d of them documented, and no stale ones ===" % (len(findings), len(findings)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
