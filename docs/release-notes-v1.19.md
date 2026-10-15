# Aside v1.19 — two stories, and five that begin differently now

**Download `Aside-linux-x64.tar.gz` (Linux, one file, no Java needed) or `aside.html` (every story that has a web
build, in one page).**

## Two stories

- **The Pane** — a glazier of thirty years who *can read a broken window the way other people read a face*: which
  pane went first, whether it was hit or leaned on, **and whether the glass is on the inside or the outside of the
  frame, because glass goes the way the force went.** A back door put through overnight, a crime number, an insurance
  form with a line on it that says **CAUSE OF BREAK** — and glass on a step that is under a porch, with no wind in
  this county that puts glass under a porch. Four endings, and they are four answers to whether a fact you can see is
  yours to say.
- **The Plot** — a sexton, a grave open for a burial on Thursday, and a headstone by the gate that has come loose **at
  the base**, which is the worse of the two places for it to move. The diocese's rule is two sentences and it is about
  safety: **a memorial found unstable is made safe, and the usual way to make it safe is to lay it down flat, because
  a stone lying down cannot fall on a child.** The rule is right, the family is right, and there is nobody to be angry
  with — which is the whole of it.

That is **thirty stories**. Each is audited rather than asserted: every scene reachable, every ending reachable, no
choice leading nowhere, no branch quietly converging on another.

## And five stories that begin differently now

Twenty-three of the twenty-nine stories in the last release opened the same way — *"The \<place\> is \<physical
description\>"*: *The lock is one chamber and four gates…*, *The forge is at the end of a lane…*, *The jetty is four
planks and a bell…*.

Some of that is a register and some of it was a habit. **At 79% a reader of three stories sees the formula rather than
the story, and these stories are varied in what they are about**, so the shape was flattening them. Five now open on
something specific to their own story instead of a description of their own room:

  **ninety-days** — *"Ninety days. That is the rule, and it is a good rule, because it means you never have to decide
  anything: you write the date in the book and the date decides for you."*
  **the-lock** — *"Eleven minutes a boat, and you have done it eleven thousand times…"*
  **the-exchange** — *"Forty lines to the north side of town, thirty to the south, and a board of little brass
  shutters…"*
  **the-round** — *"Four hours, fourteen rooms, the same order for six years…"*
  **the-bothy** — *"The track stops being a track about a mile before you get to it, which is the bothy's first
  warning."*

**23 of 29 became 18 of 29.** Still a plurality, still the register, no longer a rule. And **one was deliberately not
opened on its strongest image**: `the-round`'s card with fourteen rooms on it is the story's *mid-story reveal* —
the list and the building disagreeing is what the story is — so opening on it would have spent the reveal on the first
line. A better first line is not worth breaking the structure under it.

## The library can now notice two things it could not before

- **A phrase it has used twice.** Thirty stories, five and a half thousand lines, one author: a sentence copied from
  one story into another reads as voice in *both*, and each story is read on its own. The check compares runs of ten
  or more words **between different stories**, scene by scene — and the split matters more than the count: **339
  shared runs in this library are *within* one story and are deliberate** (sibling endings telling the same night
  several ways; `the-stage-door`'s three endings share a whole passage). A check that flagged those would be firing
  on valid content.
  **It found four accidents and caught a fifth in the story written the day it was built.**
- **A story nobody can reach.** Every other step of shipping a story leaves evidence — a build is a file, the shelf is
  a file, the frames are files, the README is compared — **while a registration is a line of Java.** A story could
  pass everything and be unopenable. Thirty stories now, every one loadable, registered and on the shelf.

## Three more stories cost 230 KB

`aside.html` is **9,293,118 bytes** against **9,056,661** for v1.18 — **230 KB for two more stories**, and it is still
under the size a phone should be asked to download in one page. The library keeps this number because it grew 1.7 MB
by accident once: builds were carrying full-size artwork because a generator had never been run for the newest
stories.

## What is verified, and what is not

Every story is walked by the auditor above. The game builds are checked against the generators that write them, the
frames against the code that draws them, the audio against the scripts that synthesise it, and the README's counts
against the library itself — 65 cues produced twice and byte-identical, 44 suites, about 10,680 checks, and 36
self-tests of the tools themselves.

**Nothing here can tell you whether a story lands.** Structure and reachability are checkable; whether a story is
worth reading is not, and no test in this repository claims otherwise.
