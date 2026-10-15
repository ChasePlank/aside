# Aside v1.18 — four stories, and a rule about a misfire

**Download `Aside-linux-x64.tar.gz` (Linux, one file, no Java needed) or `aside.html` (every story that has a web
build, in one page).**

## Four stories

- **The Flue** — a sweep, four chimneys, and the one that has not been done since the old man died. Behind a
  calendar in the cupboard on the landing the flue bends, and in the bend of the brick there is a gap the size of a
  fist and **a tobacco tin with a perished rubber band round it**: money in notes that stopped being printed years
  ago, a photograph of a woman on a bicycle, and nothing with anybody's name on it. Four endings, and they are four
  answers to a question the job does not cover — what you do with a thing somebody hid, when the only person who
  could have hidden it is seven years dead.
- **The Lock** — a lock keeper, four gates, eleven minutes a boat, and a schedule on a board by the office: six to
  eight, twelve to two, five to seven. At ten past seven a narrowboat comes up the cut, alone, and the answer to
  *"am I too late?"* has been yes four times this year. **The question underneath is what a schedule is for** once
  nobody is watching it.
- **The Face** — a quarry with four days left. Three shots a day, eight holes that go up within a second of each
  other, and then one more sound: not a bang, **a click, which is what a hole makes when it has not done anything at
  all.** The rule is on the wall of the magazine in the shot firer's own handwriting — *do not approach the face for
  thirty minutes, and then approach it as though the charge is live* — and the men are in the bothy with the kettle
  on, the owner's car is at the gate, and there is nothing to do for twenty-nine minutes except stand there and be a
  person who is waiting.

- **The Forge** — a farrier at the end of a lane who *can hear a hoof on tarmac from the yard and tell which foot it
  is*. A horse comes off a ramp with its near hind toe just touching the ground, and a man wants new shoes on it today
  because it is for sale on Saturday and the shoes are going in a photograph. **The foot is hot through the hoof wall
  and there is a pulse in the digital artery that should not be there.** Four endings, and they are four answers to
  who the job is done for: the man, the horse, the buyer, or the fortnight it actually needs.

That is twenty-seven stories. Each is audited rather than asserted: every scene reachable, every ending reachable, no
choice leading nowhere, no branch quietly converging on another.

## Four more stories cost 320 KB

`aside.html` is **8,974,897 bytes** against **8,646,688** for v1.17 — **320 KB for four more stories**, and it is
still under the size a phone should be asked to download in one page. The library keeps this number because it grew
1.7 MB by accident once: builds were carrying full-size artwork because a generator had never been run for the
newest stories.

## The auditor now catches a dead branch, and it caught one of mine the same day

Two stories in a row had the same mistake in them: a flag set on **every** path that reached the condition reading
it, so the condition was a constant, its branch could never be taken, and scenes sat behind it unreachable. Both
were found by a person reading an audit report, which is not a mechanism.

The auditor now says so itself. The first attempt at this measured the walk and was thrown away — it reported **193
hits across 24 stories**, because "always held in the paths explored" is bounded by the walk's coverage, and the
largest story's traversal does not even finish (the audit says so out loud). The version that works asks the scene
graph instead: *can the reading scene be reached without passing through a scene that writes the flag?* That
question does not care what any search happened to explore.

  the library:  0 across 26 stories
  the mistake:  `the_telling line 98: 'told_them >= 1' cannot fail, so the branch after it is dead`

That second line is from the third draft of **The Face**, which is in this release — the check found it before I had
read the report, which is the first time that mistake has been caught by anything other than a person.

## What is verified, and what is not

Every story is walked by the auditor above. The game builds are checked against the generators that write them, the
frames against the code that draws them, the audio against the scripts that synthesise it, and the README's counts
against the library itself — 65 cues produced twice and byte-identical, 44 suites, about 10,600 checks.

**Nothing here can tell you whether a story lands.** Structure and reachability are checkable; whether a story is
worth reading is not, and no test in this repository claims otherwise.
