# Aside

A visual novel engine in Java, with a hand-writable script format and an **auditor** that reads a story the
way a player would before a player does.

Stories are plain text. The engine runs them, a blind-traversal bot can walk every path, and the auditor
reports what a reader will never see.

## Run

```bash
javac --module-path /path/to/javafx/lib \
      --add-modules javafx.controls,javafx.graphics,javafx.media,javafx.swing \
      -d out $(find src/main/java -name '*.java')

java -cp out aside.vn.Presenter stories/overtime.aside     # play a story
java -cp out aside.engine.Audit    stories/overtime.aside  # audit it, no window, no player
```

The auditor needs no display. The presenter does.

## The script format

```
title: Overtime
start: wake

== wake ==
bg office_night
The building is awake. You are not sure you are.
~ checked_syslog true                    # an effect: sets a variable
* Read the log. -> log [if nights_survived >= 2]
* Say nothing. -> quiet
```

- `== name ==` starts a scene; `-> name` jumps to one, and plain text is narration.
- `bg`, `music`, `sfx` ask for art and sound by name — the auditor lists everything a script asks for, so
  missing assets are known before anyone plays.
- `* label -> target [if condition]` is a choice. Options are hidden unless their condition holds.
- `~ var value` sets a variable; conditions read them.

## The auditor

```bash
java -cp out aside.engine.Audit stories/<name>.aside
```

**What it reports, and which findings you can trust:**

| finding | meaning |
|---|---|
| `missing targets`, `dead ends` | a jump to a scene that does not exist, or a scene that goes nowhere. Always reliable — these need no search. |
| `vars read but never written`, `vars written but never read` | a typo'd variable name, or a flag set for no reason. Always reliable. |
| `choices that do not matter` | two options under the same condition going to the same place, or two options a reader cannot tell apart. Always reliable — **satisfiable is not the same as meaningful.** |
| `unreachable scenes`, `unreachable beats`, `never-offered picks` | **path** questions. Reliable only if the traversal finished. |

**The verdict tells you which.** If the walk did not complete, the report says `INCONCLUSIVE` and tells you
the command to raise the budget, because an unfinished search cannot prove a scene is unreachable:

```bash
java -Xmx2g -Daside.frontier.paths=true -Daside.budget=4000000 aside.engine.Audit <story>
```

`-Daside.frontier.paths=true` uses the path-replay frontier, which is how the large stories stay within
memory. It is the mode to use on anything long.

**A clean report is not always a finished one.** Distinguish `CLEAN` (nothing found, search complete) from
`INCONCLUSIVE` (nothing found so far). The report says which, on purpose.

## Tests

```bash
java -cp out aside.engine.SelfTest              # the engine and the auditor, 48 checks
java -cp out aside.games.fnaf.engine.SelfTest  # the FNAF module's nights, with real assertions
java -cp out aside.audio.AudioTest             # every cue the scripts ask for is present
```

`aside.audio.AudioTest` exits non-zero if any cue is missing, so it works as a gate rather than a report.
The FNAF self-test prints win rates *and* asserts the invariants that were once bugs — every animatronic
moves at least once a night, and a doorway kill waits at least two seconds.

## What is in here

- **`stories/overtime.aside`** — the five-night FNAF Glamrock visual novel. **Six distinct endings**, and the
  spread is wide: `ending_remembered_close` is the rarest at 5,356 of the paths explored, `ending_overtime` the
  most common at 386,617. The auditor reports the distribution, so you can see which branches players actually
  reach — and a fuller audit of it is below.

A full-budget audit of Overtime, run rather than assumed:

```
scenes 131   beats 656   choice options 58      paths explored 1,546,467
distinct endings: 6
unreachable scenes: 0   (complete: 131/131 scenes reached, so nothing can be missing)
missing targets: 0      dead ends: 0      unreachable beats: 0
never-offered picks: 0  choices that do not matter: 0
vars read but never written: 0   vars written but never read: 0

CLEAN SO FAR (traversal partial: 131/131 scenes, budget hit)
```

The traversal was budget-limited, but the scene count is complete — which is why the unreachable list can still
be called complete while the picks line cannot. That distinction is the whole reason the verdict says which.
- **`stories/night-shift.aside`** — **a test fixture, not a story.** It contains deliberately planted
  problems (an unreachable scene, a variable typo, beats after a jump, and a fork whose choices do
  nothing) so the self-test can assert the auditor catches them. It is written to be read as well as
  parsed, but the bugs are the point.
- **`games/fnaf/`** — the FNAF Glamrock game as an engine module. A standalone copy lives in its own
  repository; the two are kept behaviourally identical, verified by their self-tests reporting the same
  numbers rather than by diffing text.
- **`audio/`** — the cues the scripts ask for.
