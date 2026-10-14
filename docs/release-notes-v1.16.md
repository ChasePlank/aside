# Aside v1.16 — four more stories, and the download got 713 KB smaller

**Download `Aside-linux-x64.tar.gz` (Linux, one file, no Java needed) or `aside.html` (every story that has a web
build, in one page).**

## Four stories

- **The Stage Door** — a doorkeeper, a ledger with a line for everybody who comes in, and the last night of a run.
  A woman arrives who is not on the list: the second cover eleven years ago, in this building, in the other
  production, who never went on.
- **The Canteen** — a works canteen, and a strip of card on the board in the same marker as the day's menu that says
  `for R. no onion.` Nineteen years of one small pie, and the question underneath it: what do you do with a habit
  that has outlived its reason.
- **The Round** — a night cleaner, a card in a cupboard with fourteen rooms on it in their own handwriting, and one
  office cleared by contractors. The list and the building no longer agree, and the list is the only thing that says
  what the job is.
- **The Riser** — a plumber called out at two in the morning. The riser is wet to the shoulder with **four tide lines
  on the pipe**, so the leak is older than tonight by months, and the form has one box that decides who pays: it is
  called DATE OF FAULT.

That is twenty-one stories, each with its own ending count, and every one of them audited rather than asserted.

## The download is smaller than the last one

`aside.html` is **8,431,690 bytes** against **9,162,113** for v1.15 — **713 KB smaller with four more stories in
it**, and about 1.7 MB smaller than it would have been.

The reason is worth stating because I had it wrong twice. The library's browser builds inline their art, and
`tools/vn-art.py` — the whole-library generator that writes the downscaled copies — **had never been run for the
eight newest stories**. Those builds were carrying full-size desktop art: The Harbour's page was 670 KB, The
Canteen's 363, The Bothy's 353. Running it and rebuilding them took those to 66, 65 and 89 KB.

## And a frame that looked stale was a save file

`fruitjump` was reported as 8,653 pixels different from what the code draws, on a machine where the two were
byte-identical. It was the platformer's autosave: its menu gains a "Continue" item whenever that file exists, so the
frame depends on whether *anybody* has played the game — **including the capture tool, which plays every game it
opens**. The tool moved the save aside before its run and its own run then wrote a new one. It is cleared now, only
during captures, so a real save is never touched.

## What is verified, and what is not

Every story is checked by an auditor that walks it: scenes reached, endings reached, choices that lead somewhere,
branches that do not quietly converge, variables that are read and written. The game builds are checked against
their generators, the frames against the code that draws them, and the audio against the scripts that synthesise it
— **65 cues, produced twice, byte-identical**.

**Nothing here can tell you whether a story lands.** Structure and reachability are checkable; whether a story is
worth reading is not, and no test in this repository claims otherwise.
