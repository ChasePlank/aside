# Aside v1.20 — a sound that was never there, and four stories

**Download `Aside-linux-x64.tar.gz` (Linux, one file, no Java needed) or `aside.html` (every story that has a web
build, in one page).**

## A cue two published stories had been asking for, and the library did not have

`the-discrepancy` and `the-lamp-room` both say `sfx clank` — **three uses between them — and there has never been an
`audio/clank.*`**, only `pot_clank`. **A cue with no file fails silently by construction:** the player looks up a name,
finds nothing, and plays nothing.

**So two stories have had silence where a clank should be since the 1st of October, through a release, and nothing
anywhere said so.** Both now point at `pot_clank`, which exists and is the same sound.

The reason it went unnoticed for two weeks is worth stating plainly: **the auditor collected the cues a story asks for
and printed them, and never checked whether the library had a sound for any of them** — while checking the
backgrounds for exactly the same thing. The list read as coverage. It is checked now, and the faults count toward the
verdict, so this cannot happen again quietly.

## Four stories

- **The Chart** — an optician, twenty minutes, and a man of eighty-three whose daughter booked the appointment and
  drove him in. He can still read the line in one eye, **in good light, if he takes his time** — and there is a form
  with a box on it called VISUAL STANDARD FOR DRIVING, **with two options and neither of them is "nearly"**. Four
  endings about who a result belongs to.
- **Gold Leaf** — a signwriter of forty years, and a grocer shutting in March who wants her 1948 fascia repainted
  before she goes: *"It's for me. I've looked at that sign for fifty-one years… and I'm not having it go grey on my
  watch."* Real gold laid over four days, gold paint in an afternoon, or telling her she is paying for something
  nobody will see.
- **Two Pints** — a milk round, and two pints at the end of Chandlers Lane that have been on the book since 1994.
  **The bottles have stopped coming back empty: they are coming back FULL.** Four endings, four answers to how far a
  job reaches — including the man of forty drinking two pints a day and hating milk, *because the alternative is a
  doorstep with nothing on it*.
- **Under Cover** — **the first story here with a crowd in it.** Twelve market pitches under cover, eleven of them the
  same eleven people for years, and a new van in the third pitch with a board that says EVERYTHING A POUND. He is two
  along, and he is selling what you sell.

That is **thirty-four stories**. Each is audited rather than asserted: every scene reachable, every ending reachable,
no choice leading nowhere, no branch quietly converging on another.

## And the library can now notice two more things about itself

**A phrase it has used twice.** Thirty-four stories, one author: a sentence copied from one story into another reads
as voice in *both*, and each story is read on its own. The check compares runs of ten or more words **between
different stories**, scene by scene — and the split matters more than the count, because **339 shared runs in this
library are *within* one story and are deliberate** (sibling endings telling the same night several ways).

**A story nobody can reach.** Every other step of shipping a story leaves evidence — a build is a file, the shelf is a
file, the frames are files — **while a registration is a line of Java.** A story could pass everything and be
unopenable. Thirty-four stories now, every one loadable, registered and on the shelf.

## Four more stories cost 216 KB

`aside.html` is **9,514,762 bytes** against **9,293,118** for v1.19 — **216 KB for four more stories**, still under the
size a phone should be asked to download in one page. **And the phone builds carry no sound at all, deliberately**:
measured across all of them, no inlined audio and no audio playback, because the shelf is already nine megabytes of
artwork and sound for thirty-four stories is the size that was not worth paying. It is said out loud here because a
silent build and a broken one look identical.

## What is verified, and what is not

Every story is walked by the auditor above, including its art and its cues. The game builds are checked against the
generators that write them, the frames against the code that draws them, the audio against the scripts that
synthesise it, and the README's counts against the library itself — 65 cues produced twice and byte-identical, 44
suites, about 10,780 checks, and 36 self-tests of the tools themselves.

**Nothing here can tell you whether a story lands.** Structure and reachability are checkable; whether a story is
worth reading is not, and no test in this repository claims otherwise.
