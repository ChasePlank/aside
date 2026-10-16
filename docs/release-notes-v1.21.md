# Aside v1.21 — a vet, an old dog, and a question that is not about the dog

**Download `Aside-linux-x64.tar.gz` (Linux, one file, no Java needed) or `aside.html` (every story that has a web
build, in one page).**

One story this time, because one story is what the library had waiting.

## A Small Lump

A vet of eleven years, an appointment at ten past four, and a lump — which is most of what a small animal practice
does in a week: somebody has found something and they want to be told it is nothing. **You can tell a fatty lump
through the skin the way you can tell a full stop from a comma, and you tell people it is nothing about forty times a
week and you are right about thirty-nine of them.**

This one is not a fatty lump. It is fixed to the muscle underneath, about the size of a walnut, and the honest answer
costs a hundred and forty pounds before anybody has decided anything. The dog is a lurcher of about thirteen called
Bramble. The owner is eighty-one, came on the bus, and has the dog's basket under the chair rather than leave it in
the waiting room.

**It turns on the question nobody asks in the right order** — *"which one of those is for him?"* — and on the answer,
which is that none of them are, because the dog does not know what a needle is or what six hundred pounds is, and all
three options are for the man.

**The first story here with a patient that cannot speak.** Four choices, three endings, and the asymmetry is
deliberate: two of the choices are routes into the same conversation rather than different decisions.

That is **thirty-five stories**. Each is audited rather than asserted: every scene reachable, every ending reachable,
no choice leading nowhere, no branch quietly converging on another.

## And the check on the prose caught a draft of this story

The library compares runs of ten or more words **between different stories**, because a sentence copied from one story
into another reads as voice in *both* and each story is read on its own. **It found a ten-word run in this story's
first draft, echoing `the-chart`** — the second time it has earned itself on the first draft of a story written after
it. The auditor found the other thing, which was two of the four paths converging on one ending when the draft intended
four.

**Worth stating plainly rather than quietly fixing: the same slip has now happened three stories running** — renaming a
scene and leaving the jump behind it — and the auditor has caught it every time, which is why it has never shipped. A
check that catches a habit is doing its job.

## One story costs 54 KB

`aside.html` is **9,570,154 bytes** against **9,514,762** for v1.20 — **fifty-four kilobytes for one more story**, still
well under the size a phone should be asked to download in one page.

**And the phone builds carry no sound at all, deliberately**: measured across all of them, no inlined audio and no audio
playback, because the shelf is nine megabytes of artwork already and sound for thirty-five stories is the size that was
not worth paying. It is said out loud here because a silent build and a broken one look identical.

## What is verified, and what is not

Every story is walked by the auditor above, including its art and its cues. The game builds are checked against the
generators that write them, the frames against the code that draws them, the audio against the scripts that synthesise
it, and the README's counts against the library itself — 44 suites, about 10,800 checks, and 36 self-tests of the tools
themselves.

**Nothing here can tell you whether a story lands.** Structure and reachability are checkable; whether a story is worth
reading is not, and no test in this repository claims otherwise.
