# Aside v1.14 — eleven stories in a week

**Download `Aside-linux-x64.tar.gz` (Linux, one file, no Java needed) or `aside.html` (every story that has a web
build, in one page).**

The library is **seventeen stories**. Eleven of them arrived since the last release:

- **A440** — *"The fork goes on the bench first, because it is the only thing in the room that is not an opinion."*
  A workshop, and a standard to hold something up against.
- **Bell Codes** — the last manned night in a signal box, read the way you read a diagram you have been asked to
  check.
- **Changeover** — *"The carbon arc is the part nobody warns you about."* A projection box, and the change from one
  reel to the next.
- **Ninety Days** — a lost-property office with a shelf of five things and a book of five lines.
- **The Bothy** — two rooms and a porch at the end of a track that stops being a track, and a book you write in when
  you arrive and read when you do.
- **The Dispensary** — the back half of a shop that shut at six, an answerphone, and who rings it.
- **The Exchange** — one room with two walls of sockets, forty lines to the north side of town and thirty to the
  south.
- **The Harbour** — eighteen feet of fibreglass and the job of meeting ships at the bar and bringing them in.
- **The Last Crossing** — *"The jetty is four planks and a bell, and the bell is older than the planks."*
- **The Last Route** — a depot at ten to eleven, and a route being cut in March.
- **The Lookout** — a tower on a ridge, eleven summers in the chair, and four fires called in.

## What a story is, here

Stories are plain text with a hand-written script format, and they are **audited rather than trusted**: the gate walks
every branch of every story, checks that no choice leads nowhere, and fails a story whose counts disagree with
themselves. Two faults in my own stories were found by that audit rather than by playing them, and both are fixed.

**Everything is measured, and the shelf is derived.** `aside.html` is not a list — it walks the library and inlines
every story that has a web build, so adding a story puts it on the shelf with no change to the shelf. The suite
regenerates each web build and compares it to the checked-in copy, so a stale one fails rather than shipping.

**And the app is started by the gate now.** Until this week nothing in the repository ran `aside.ui.Main` — the class
the desktop download starts — so a break in the entry point was invisible to every check. It now boots the app, reads
its own report of what art it loaded, and requires a drawn frame.

## What is not verified

The stories are checked for structure and for reachability, not for being good. Nothing here can tell you whether a
story lands; that is what reading it is for.

This is a Linux download and a web page. There is no Windows bundle for the library yet, and the phone builds are the
ones the shelf inlines rather than a separate app.
