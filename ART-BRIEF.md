# Overtime — character art brief

Sprites for the visual novel. **Same four characters as the FNAF game, same
Glamrock designs** — continuity matters, because both games are the same
building on the same night. What changes is the *framing*: the FNAF game
shoots them performing, this shoots them after the audience went home.

---

## Format (applies to every sprite)

| | |
|---|---|
| Format | **PNG with alpha.** Transparent background — these composite over scene art, so a white box is fatal. |
| Framing | **Bust or 3/4 body** (waist up). They stand beside a text box, so full-body-with-props won't frame. |
| Height | ~800–1000 px tall |
| Angle | **3/4 view, turned slightly inward** so they read whether placed left or right |
| Scale | Consistent across all four — heads should end up the same size |
| Camera height | Consistent — pick one eye level and keep it for the whole cast |
| Lighting | One consistent direction across the cast, so the set looks like one room |
| Props | **None.** No guitars, no plates, no mics. The absence is the point. |

**Naming:** `<character>-<pose>.png` in lowercase — `monty-quiet.png`,
`roxanne-warm.png`. I'll wire them up by filename so nothing needs
explaining when they arrive.

---

## The big thing to get right

Every render supplied so far is **on stage**: teeth bared, arms up,
dramatic light. That's one emotion wearing four faces.

Emotion on these characters has to come from:

1. **Posture** — does most of the work. Shoulders down reads tired. Hunched reads guarded. Leaning in reads interested.
2. **Head angle** — a downward tilt or a turned-away head carries more than a mouth can.
3. **Eye and eyelid state** — dimmed vs. bright, narrowed vs. wide. On a hard face, this is where feeling leaks out.
4. **Mouth** — closed, slightly open, or a grin *held too long*. That last one is a whole characterisation for Chica.
5. **Lighting** — warm on the figure vs. cold, and how much of the face sits in shadow.

They have **fixed faces** — they physically can't emote like a person. That's
the gift: a design that's bad at expressing feelings is the right design for
a story about feelings that have nowhere to go. Let it come out through
posture and eyes.

---

## MONTY

*The one who lost his predecessor. Bitter, territorial — but the wound is
grief, not anger. He respects silence more than he respects answers.*

**`monty-neutral`** — Standing square, chin slightly up, hands at sides or
loosely crossed. Mouth closed. Eyes steady but not hard. Reads as *"you're in
my room."* Guarding the space, not threatening it.

**`monty-tired`** — Shoulders dropped, head tilted down and slightly away.
One hand hanging or rubbing the back of the neck. Eyes dimmed.
*Thirty years of this.* The oldest-looking pose of the four.

**`monty-flat`** — Dead-on, perfectly still, no expression at all. Eyes bright
but showing nothing. This is the door shutting. The coldest Monty — and the
stillest, because stillness is what makes it land.

**`monty-quiet`** ⭐ — **The payoff.** Shoulders down, head level or tilted a
touch, eyes softened and dimmed. Mouth closed but *not* tight. Something like
relief, or the beginning of it. He's just decided you might be alright. Should
look as different from `neutral` as you can make it while staying the same
character.

---

## ROXANNE

*Performs confidence over something fragile. The performance is load-bearing
and she knows it.*

**`roxanne-smirk`** — Chin down, eyes up (looking through the lashes), weight
on one hip, one hand on hip. Teeth just showing. **The performance, up.** This
is the mask on.

**`roxanne-laughs`** — Head back or tilted, mouth genuinely open, eyes
crinkled. Warm light. Brief and real — this is the one that *escapes* her.

**`roxanne-flat`** — Half-lidded eyes, head level, arms crossed, slight chin
up. Bored and cool. She's testing you and doesn't mind you knowing.

**`roxanne-cold`** — Turned a little away, eyes hard and bright, mouth closed
tight. The door shut. You failed politely, and she's already stopped caring.

**`roxanne-warm`** ⭐ — **The payoff.** The performance *dropped*. Soft eyes,
closed mouth with a slight smile, shoulders relaxed, head tilted a touch.
Direct eye contact, and kind. Should read as a genuinely different person from
`smirk` while obviously being the same character.

---

## FREDDY

*Oldest. Speaks obliquely. The emotional centre of the building — he's the one
who's been here longest and knows most and says least.*

**`freddy-idle`** — Mostly still, hands low, head level, eyes glowing gently.
**Stillness is the whole character note here** — he doesn't fidget. If the
cove curtain edge can be visible, that adds a lot.

**`freddy-warm`** ⭐ — *"Stay a minute."* Head tilted slightly, eyes soft and
warm, small closed-mouth smile. Settled, open posture. The one that lands.

**`freddy-wry`** — Head tilted, brow area raised as much as the design allows,
half-smile, eyes bright with humour. Deflecting with affection rather than
avoiding the question.

---

## CHICA

*Kind, cheerful, and the saddest of the four. Her cheer is a performance and
it's wearing through.*

**`chica-neutral`** — Bright and upright, hands clasped or raised, eyes wide,
mouth open in a greeting. Genuinely welcoming. This is the *true* version —
use it as the baseline the other three depart from.

**`chica-bright`** — The performance of cheerfulness. A bigger smile than the
situation warrants, eyes a touch too wide, **held a beat too long**. Slight
stiffness in the posture. Should read happy and feel wrong.

**`chica-flat`** — The kitchen never finishes. Shoulders down, head slightly
down, eyes dimmed, mouth closed. Not sad exactly — *empty*. The battery
running out of a thing that only knows one motion.

**`chica-small`** ⭐ — **The saddest one.** Making herself smaller: shoulders
in, head down and turned slightly away, eyes lowered. Quiet acceptance, not
crying. She's agreeing to be one of the quiet ones. Make this one hurt a
little.

---

## Priority

If you do four and stop, do the starred ones — they're the emotional payloads:

1. **`roxanne-warm`**
2. **`monty-quiet`**
3. **`chica-small`**
4. **`freddy-warm`**

The neutral/`flat`/`idle` poses I can approximate from what already exists if
you'd rather not do all sixteen. The payoffs I genuinely can't.

## Full list (16)

```
monty-neutral     monty-tired      monty-flat      monty-quiet ⭐
roxanne-smirk     roxanne-laughs   roxanne-flat    roxanne-cold
roxanne-warm ⭐
freddy-idle       freddy-wry       freddy-warm ⭐
chica-neutral     chica-bright     chica-flat      chica-small ⭐
```

Anything you can't get to, say so and I'll write around it — the script is
mine to adjust, and I'd rather change a line than have you grind on a pose
you're not happy with.
