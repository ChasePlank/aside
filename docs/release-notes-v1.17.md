# Aside v1.17 — two stories, and the first one that answers another

**Download `Aside-linux-x64.tar.gz` (Linux, one file, no Java needed) or `aside.html` (every story that has a web
build, in one page).**

## Two stories

- **The Pledge** — a pawnbroker, a christening mug pledged for sixty pounds when its silver is worth ninety, and an
  engraving that reads *for Ellis, on his baptism*. The name on the ticket is not that name. Three hundred and sixty
  days is the whole of the trade and it fits on the ticket; the question underneath is what a person does with a
  thing they have noticed about a thing they are holding.
- **The Survey** — **the first story here that pairs with another.** It is the other side of *The Canteen*: an
  eighteen-year-old with a clipboard, a summer of levels, and the only hour of the week he is not being looked at —
  ten past two, the end table under the clock, a small pie with no onion in it, eleven minutes. If you have played
  The Canteen, you already know one of the two people in this one from the outside.

That is twenty-three stories. Each is audited rather than asserted: every scene reachable, every ending reachable,
no choice leading nowhere, no branch quietly converging on another.

## Two more stories cost 210 KB

`aside.html` is **8,646,688 bytes** against **8,431,690** for v1.16 — **210 KB for two more stories**, and it is
still well under the size a phone should be asked to download in one page. The library keeps this number because it
grew 1.7 MB by accident once: builds were carrying full-size artwork because a generator had never been run for the
newest stories.

## And a frame that kept changing for two nights was measuring the wrong thing

The pictures in `docs/` are checked by rendering every game and comparing. Thirteen of them kept moving between two
machines, each of us regenerating and committing and seeing the same thirteen change back.

The cause was that **both tools counted how many pixels differed, which conflates two completely different events**.
Measured across the thirteen: one frame differed in **120,842 pixels whose worst channel delta was 6** — a rasteriser
rounding spread thinly over a whole screen — while another differed in 42,466 pixels with a **worst delta of 224**,
which is a different screen. A rule that counts pixels calls those the same kind of event.

Both halves now print the count *and* the worst delta, and the delta decides. Same image, same verdict, in the tool
that writes and the tool that checks — which is what stops two people handing the same frames back and forth.

## What is verified, and what is not

Every story is walked by the auditor above. The game builds are checked against the generators that write them, the
frames against the code that draws them, the audio against the scripts that synthesise it, and the README's counts
against the library itself — 65 cues produced twice and byte-identical, 43 suites, about 10,400 checks.

**Nothing here can tell you whether a story lands.** Structure and reachability are checkable; whether a story is
worth reading is not, and no test in this repository claims otherwise.
