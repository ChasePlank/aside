# retired games

Eight games used to be in the library and are not any more. They are here,
whole, and none of them is broken.

## Why

Chase read the library's blurbs on 2026-09-30 and said:

> a lot of them seem repetitive and seem to follow the same premise, if
> possible, each game needs to be unique, not empty copies of something
> already existing.

He was right, and the evidence was in the blurbs. Six of them were
*"N nights, M things, and a constraint"* -- **ledger** ("Six nights. Five
lines. You cannot keep it all."), **vigil** ("Twelve days, five things, and
less of you every day."), **interval** ("Twelve days, six watches, and a ship
that may not come."), **promise** ("Five nights, eight people asking..."),
**omission** ("Five things fit in the bag...") and **handoff** ("Five nights
under somebody else's orders..."). Four more were *"Two X"* -- **bearings**
("Two clocks that agree are still just two clocks."), **corroboration** ("Two
people saw it. Neither of them is an instrument."), **drift** ("Two copies of
one record.") and **relay** ("Two people are not speaking.").

Eighteen verb games, three mechanics. That is a tic, not a portfolio, and no
amount of different nouns fixes it.

## The rule now

**One game per mechanic.** A game is in `Games.all()` because it does
something no other game in that list does. When a new game is written that
does what an old one does, the old one comes here instead of the library
growing a second copy of an idea it already has.

The franchise is the one exception. FNAF 1 through 5 were asked for by name,
with *"each gets harder than the last"* -- a sequel is supposed to be the
same shape, harder.

## What left, and what already does it

| retired | it was | the library already has |
|---|---|---|
| **Vigil** | a fixed run and a small capacity, and choosing what to keep in it | **ledger** |
| **Interval** | a fixed run and a table of what you know, and deciding when to act | **ledger** |
| **Promise** | a budget spent across a fixed number of nights | **ledger** |
| **Omission** | a small number of slots, and choosing what goes in them | **ledger** |
| **Corroboration** | two sources that disagree, and deciding what to believe | **bearings** |
| **Relay** | two parties who are not speaking, and deciding what crosses | **bearings** |
| **Attribution** | a fixed record you edit under a budget, where the edit is the point | **redaction** |
| **Inventory** | the record you write being the thing that survives | **redaction** |

Each of these was the best of its own family when it was written. The one that
stayed is the one that states the mechanic most sharply, not the one that came
first or the one that is longest.

The same list, with the same reasons, is `Games.retired()` in
`src/main/java/aside/game/Games.java`. It is there rather than only here so
that the next person adding a game reads it before adding a ninth copy of
ledger.

## What is here

```
retired-games/
  src/main/java/aside/games/<id>/   the game, exactly as it was
  src/main/resources/<id>/          its phone-build template
  web/<id>.html                     its phone build
  tools/<id>-*.mjs                  its trace or play script, if it had one
```

**None of this is compiled.** It is out of `src/main/java`, so `javac` never
sees it and its `SelfTest` never runs. That is the cost of retirement: this
code can rot against the engine without anything telling you. It is kept
because it is a record of what was tried, and because bringing one back
should be cheap.

## Bringing one back

```bash
git mv retired-games/src/main/java/aside/games/<id> src/main/java/aside/games/<id>
git mv retired-games/src/main/resources/<id>        src/main/resources/<id>
git mv retired-games/web/<id>.html                  web/<id>.html
git mv retired-games/tools/<id>-*.mjs               tools/       # if it had one
```

Then in `Games.java`: add the import, add the `g.add(new <Id>Game());` line,
and delete the entry from `retired()`. Then regenerate the shelf
(`java -cp classes aside.game.PhoneShelf`) and run
`java -cp classes aside.engine.SelfTest` -- it checks that all four of those
steps happened and that this file names the game.

If it has been a while, expect the restored game not to compile. The engine
has moved; the game has not.
