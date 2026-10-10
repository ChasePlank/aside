# Holdfast — the story the game already tells

The menu has said it since the name was chosen: **"A climber, a sunset, and a way home."** This is that sentence
written out, and every part of it is read off mechanics that already exist rather than invented on top of them.

---

## The premise, in the words a title screen would use

The island is low, and the water is already in the low ground. It is not rising fast — it is not a flood — it is
rising the way a tide does, which is to say you can see it and you cannot argue with it.

The way off is up. Above the water line there is a path that goes to the top of the island, and at the top there is
the light that has been visible from the first beach: something up there is still burning, and it is the only way
home.

You have until dusk. After that the path is not walkable and the light is not findable, and the island keeps what it
has.

**Holdfast** is the order a rope crew gives: hold what you have, do not let go, keep the line. It is also what you
would call somebody who did.

---

## What the mechanics already carry, and what they mean

Nothing here is a change. This is the story the existing game tells, named so that it can be shown on purpose.

| the mechanic | what it is, in the story |
|---|---|
| the sun sinking across the whole run | **the clock.** It is the only timer in the game and it is drawn rather than counted — the sky says how much light is left. |
| `LEVELS_TO_DUSK` = 40, and the ending at dusk | **the length of the day.** The run has somewhere to be going; that is what the ending added. |
| the exit six cells up, every level | **the climb.** Every level is a little higher than the last, and the way on is always upward. |
| one level in four is flooded | **the water is already in.** Not a hazard that arrives — the thing that is happening, visible in the low ground first. |
| twelve seconds of breath | **you cannot wait it out.** The water is passable and it is never somewhere to stay. |
| the boss at the top | **what is out there.** The tutorial's last sign asks the question; the boss is the answer, and it does not need explaining. |
| the night horizon on the victory screen | **home.** The same horizon the whole run has been sinking towards, after dark, from the other side. |
| the hookshot | **the way up when there is no way up.** It is the one tool that reaches. |
| coins, collected and spent on nothing | **see below — this is the one gap.** |

---

## The lines the game already says, and the one it does not

**MOST OF THIS IS ALREADY WRITTEN, AND TWO OF THE LINES ARE BETTER THAN THE ONES I DRAFTED HERE FIRST.** The game is
quieter and more confident than a design note would make it, so the honest version of this section is an inventory
rather than a proposal.

**Already there, and should not be touched:**

- **the menu**: *A climber, a sunset, and a way home.*
- **the victory screen's title**: `HOME`
- **the victory line**: *"Climbed 40 levels, and the sun went down on the way."* — which says the whole thing: the
  length, the climb, and the cost, in one sentence, without naming a feeling.
- **the rooms variant of the same screen**: *"another way out"* — the same sentence about the same picture, which is
  what the class doc means by *the same screen, because it is the same sentence.*
- **the tutorial's last sign**: `WHAT IS OUT THERE` — a question, which is better than an answer, and the boss is the
  answer.

**The one line that might be missing**, and the reason it is only a *might*: the moment the climb stops being about
the water and starts being about the light.

> THE LIGHT IS UP THERE

**AND IT CANNOT BE A SIGN, which this document claimed first and had to check.** Signs exist only in the hand-built
tutorial — thirty-seven of them there, none in the generator — so the run itself has nowhere to put a line of text.
The options are a one-off HUD line at the moment the sun first touches the water, or nothing at all.

Nothing at all is defensible, and it is the more likely answer: the sun is already the message, the sky has been
saying it for the whole run, and a game that says *the light is up there* in words may be a game that does not trust
its own sky. This is written down because it was proposed and then checked, not because it is recommended.

## The one gap: the coins

**Coins are collected, displayed, and counted on the victory screen, and nothing reads them.** That is the only part
of this story the game does not already tell, and it is the one place where the story suggests a mechanic rather than
describing one.

The proposal, and it is small: **a coin buys a little light.** Spend them — automatically, at dusk, or on the death
screen — to push the sunset back by a few seconds each. It costs almost nothing to build (the sun already climbs to 1
by `LEVELS_TO_DUSK`, so it is one term in a value that already exists) and it turns a number in the corner into the
thing the whole game is about.

That is a design decision, so it is written down here rather than implemented. Everything above it is not.

---

## What would need building to show this

- **One string** on the victory screen. It takes a `line` already.
- **One sign** on the level where the light first appears.
- **Nothing else.** The sun, the water, the climb and the ending are all in the game; this document is about naming
  them, not adding them.

The test for whether this is the right story is not whether it reads well. It is whether a player who finishes the
run would describe it the way the menu does — *a climber, a sunset, and a way home* — without ever being told.
