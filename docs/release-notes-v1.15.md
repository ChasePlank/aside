# Aside v1.15 — five stories you could not open

**Download `Aside-linux-x64.tar.gz` (Linux, one file, no Java needed) or `aside.html` (every story that has a web
build, in one page).**

## Five of the stories were in the list and could not be opened

The library builds its catalogue from the list of **game modules**, and a story without one is not merely missing
its artwork — it does not appear at all. Six stories went into v1.14's notes; five of them were unreachable:

- **The Bothy** — two rooms and a porch at the end of a track that stops being a track, and a book you write in.
- **The Dispensary** — the back half of a shop that shut at six, and the answerphone that still rings in it.
- **The Harbour** — eighteen feet of fibreglass, and the job of meeting ships at the bar and bringing them in.
- **The Last Route** — a depot at ten to eleven, and the route being cut in March.
- **The Lookout** — thirty feet of timber on a ridge, eleven summers in the chair, and four fires called in.

They are all openable now. Measured rather than assumed: the library listed **31 games against 17 story files**
before, and **36 against the same 17** after. The sixth was *Night Shift*, which is the engine's test fixture with
two deliberately planted bugs, and it stays off the shelf on purpose.

## And one of them was drawing somebody else's room

**The Exchange** asked its scene for a projection booth and the picture that appeared was a pizzeria office. Its art
was in another story's folder, and nothing loads a story's folder except its game module — which it did not have.
Both halves are fixed, and the second was the real one.

## The one-file download is 1.7 MB smaller

The shelf was carrying **six builds twice** — the same six stories, listed once by the catalogue and once by hand —
and 1,707,028 bytes of it were exact duplication. It is 8,947 KB now against 10,615 KB, and it is under the size
limit the project sets for a single page a phone downloads. Nothing had noticed: the checks that compare the shelf
against the files use sets, and a build appearing twice collapses to one entry.

## What is verified, and what is not

Everything above is measured — counts, byte sizes, and the frames the gate renders by opening every game and looking
at what it drew. The story audit walks every branch of every story and fails one whose choices lead nowhere.

**Nothing here can tell you whether a story lands.** Structure and reachability are checkable; whether a story is
worth your evening is not, and that is what reading one is for.

This is a Linux download and a web page. The library has no Windows bundle.
