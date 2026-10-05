#!/usr/bin/env bash
#
# fix-comments.sh - where has this code been taught something, and is anything checking it?
#
#   tools/fix-comments.sh [source-root]
#
# WHY THIS EXISTS. Over six hours on 2026-10-05 I broke fifteen fixes on purpose - every one of them a change made
# because a player reported something - and found that NOT ONE was protected by a check. The mutation sweep reports
# a broken constant as NOT CAUGHT, but a fix is not always a constant: it can be a guard clause, a box dimension, a
# default, a timing window. The sweep does not know where to look.
#
# WHAT THE FIXES LOOK LIKE IN THIS CODEBASE. Every one is recorded in a comment - "was X", "used to X", "invisible",
# "the first version", "playtest", "the bug". Those comments are the INDEX of what this code has learned, and this
# script counts them per file against how often that file's name appears in the gate.
#
# IT IS A MAP, NOT A GATE. A low ratio is not a fault: a screen cannot be tested headlessly at all, and some
# comments are history rather than live behaviour. What it does is rank the files so the next sweep starts where
# the unprotected lessons are rather than at the top of the tree.
set -u
cd "${1:-$(dirname "$0")/..}" || exit 2

SRC="src/main/java/aside/games/fruitjump"
GATE="src/main/java/aside/engine/SelfTest.java"
[ -d "$SRC" ] || { echo "fix-comments: no $SRC here" >&2; exit 2; }

PATTERN='//.*(was |used to|invisible|the first version|playtest|the bug)'
# THERE WAS A "drawing" COLUMN HERE AND IT WAS WRONG. The idea was right: a file with twenty comments and three
# mentions is fine if seventeen of them are about how things are drawn, and a target if ten are about behaviour.
# The implementation counted LINES matching a list of drawing words, and a three-line comment saying "the pool was
# drawn over the floor under it" matches on a line that does not carry the word. It reported 5 for GameplayScreen
# where reading them gives 17. A column wrong by three times is worse than no column - it reads as a measurement.
#
# Reading the twenty by hand is what the ratio is FOR. It says where to look; it cannot say what you will find.

printf '%-22s %-14s %s\n' "file" "fix-comments" "mentions in the gate"
echo "------------------------------------------------------------"
for f in $(grep -rlE "$PATTERN" "$SRC" --include=*.java | sort); do
  n=$(basename "$f" .java)
  c=$(grep -cE "$PATTERN" "$f")
  # the gate is one file; a mention is the class name appearing anywhere in it
  m=$(grep -c "\b$n\b" "$GATE" 2>/dev/null || echo 0)
  printf '%-22s %-14s %s\n' "$n" "$c" "$m"
done | sort -k2 -rn
echo
echo "A low ratio is a QUESTION, not a fault - a screen cannot be tested headlessly, and some comments are"
echo "history. The files at the top are where a sweep should start: they carry the most lessons and the"
echo "fewest things watching them."
