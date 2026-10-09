#!/usr/bin/env bash
#
# Are the audio assets reproducible - and are the committed ones the ones the generators actually produce?
#
#   tools/check-audio-reproducible.sh
#
# WHY THIS EXISTS. Three faults in one week came from the same silence, and NOTHING in the gate was looking:
#
#   * tools/fruitjump-audio.py seeded its noise from Python's hash() of the cue name. Python salts string hashing
#     per process, so the same cue came out different on every run - and re-running the generator MODIFIED committed
#     assets. Fixed there; the fix was never propagated.
#   * Five more generators (fnaf5..fnaf9) had the same bug, two of them via hash("step") and hash("push") - hashing
#     a LITERAL, which a search for hash(name) misses. 43 committed cues were unreproducible.
#   * main's Synth.java wrote TEN cues while audio/ held SEVENTEEN: seven files had no generator at all.
#
# Every file-level check passed throughout: the cues existed, the games' cue lists were complete, the gate was green
# on ~9960 checks. "The file is there" and "the file can be produced" are different statements, and only the second
# is reproducibility.
#
# WHAT IT DOES. Runs every generator TWICE into a scratch copy of the tools directory - the generators write to
# ./audio relative to themselves, so a copy keeps this repository untouched - and then:
#   1. asserts run 1 and run 2 produce identical bytes (the determinism half);
#   2. reports any cue the generators produce that differs from the committed one, or that is missing from it
#      (the drift half).
# Both halves are failures. A generator whose output changes run to run cannot be trusted with an asset, and a
# committed asset its generator does not produce is an asset nobody can regenerate.
set -u

HERE="$(cd "$(dirname "$0")/.." && pwd)"
cd "$HERE" || exit 2

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cp -r tools "$TMP/tools" || { echo "could not copy tools/ to a scratch directory" >&2; exit 2; }
mkdir -p "$TMP/audio"

mapfile -t GENERATORS < <(ls tools/*-audio*.py 2>/dev/null)
if [ "${#GENERATORS[@]}" -eq 0 ]; then
  echo "no audio generators found - is this the right directory?" >&2
  exit 2
fi
echo "  generators: ${#GENERATORS[@]}"

run_all() {
  for g in "${GENERATORS[@]}"; do
    ( cd "$TMP" && timeout 300 python3 "tools/$(basename "$g")" >/dev/null 2>&1 )
  done
}

run_all
cp -r "$TMP/audio" "$TMP/run1"

run_all
nondet=$(diff -rq "$TMP/run1" "$TMP/audio" 2>/dev/null | wc -l)
if [ "$nondet" -eq 0 ]; then
  echo "  deterministic: run 1 and run 2 produce identical bytes (${#GENERATORS[@]} generator(s))"
else
  echo "  NOT DETERMINISTIC: $nondet file(s) differ between two runs of the same generators" >&2
  diff -rq "$TMP/run1" "$TMP/audio" 2>/dev/null | head -5 | sed 's/^/      /' >&2
fi

drift=0; absent=0
for f in "$TMP/audio"/*.wav; do
  b=$(basename "$f")
  if [ ! -f "audio/$b" ]; then
    absent=$((absent + 1)); echo "  PRODUCED BUT NOT COMMITTED: $b" >&2
  elif ! cmp -s "$f" "audio/$b"; then
    drift=$((drift + 1))
    [ "$drift" -le 5 ] && echo "  DIFFERS from its generator: $b" >&2
  fi
done
[ "$drift" -gt 5 ] && echo "  ... and $((drift - 5)) more" >&2

echo "  produced: $(ls "$TMP/audio" | wc -l)   differing: $drift   not committed: $absent"
if [ "$nondet" -eq 0 ] && [ "$drift" -eq 0 ] && [ "$absent" -eq 0 ]; then
  echo "  OK every cue in audio/ is exactly what its generator produces, twice over"
  exit 0
fi
echo "  FAIL: the audio in this repository is not reproducible" >&2
exit 1
