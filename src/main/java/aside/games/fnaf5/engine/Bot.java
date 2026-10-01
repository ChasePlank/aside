package aside.games.fnaf5.engine;

/**
 * A blind player, for tuning.
 *
 * A game with a random outcome cannot be balanced by playing it. Twenty
 * nights by hand tell you nothing about a 40%-vs-28% difference, and they
 * tell you nothing at all about night 5 if you cannot get past night 3. So
 * the loop gets a player that can be run ten thousand times with no
 * display, and the difficulty is set by sweeping it.
 *
 * The bot is deliberately *not* a good player. It reacts on a fixed delay
 * and it only knows what a player knows:
 *
 * <pre>
 *   the room you are standing in   you can hear what is in it
 *   the room on the monitor        you can see what is in it
 *   a step next door               you hear it arrive, once
 * </pre>
 *
 * That last one is the whole warning system and the bot has to have it or
 * the sweep measures nothing. FNAF 4's first sweep is the cautionary tale:
 * the competent policy survived every night of the week because the bot was
 * reading each threat's distance straight out of the engine and camping the
 * door it was walking toward. Nothing two or more moves out makes a sound,
 * so no player has that information, and the "hard" reading was a cheat.
 *
 * <b>FNAF 5's first sweep was wrong twice, in opposite directions, and both
 * mistakes are worth knowing about.</b>
 *
 * The first was the same shape as FNAF 4's. The competent policy parked in
 * the middle of the building and waited to hear something arrive next door
 * -- which is the one place a pursuer wants you. It burned all five charges
 * inside forty seconds and died on every night, while the policy that never
 * stopped moving survived. A sweep that says "panic beats competence" is
 * not measuring the game, it is measuring the bot.
 *
 * The second was in the engine, and the sweep is the only reason it was
 * ever found: <b>there was no randomness in FNAF 5 at all.</b> Every threat
 * moved on its interval, every time, and the seed did nothing -- see the
 * note in {@link Threat#update}. A night with no randomness cannot produce
 * a difficulty curve, and the sweep returned 0% or 100% with nothing in
 * between, at every grace value and every pace tried. That is what a
 * broken instrument looks like: not noise, but the absence of it.
 *
 * Five policies, because one number is not a reading:
 *
 *   IDLE    does nothing at all. Must die on every night.
 *   REACT   answers what is in the room with it, and nothing else. The
 *           player who has learned the three counters and stops there.
 *   HOLD    does that too, and keeps walking. The competent player: it
 *           never parks, because parking is what lets a pursuer arrive.
 *   FLEE    does that too, and runs from wherever Funtime Freddy was last
 *           seen or heard. This is the strategy the design *describes* --
 *           Freddy is the only thing that can end a night, so keep
 *           distance -- and it is here to be measured rather than
 *           assumed.
 *   PANIC   shocks the moment anything is in the room and never stops
 *           moving. Must be worse than HOLD, or the shock has no cost.
 *   PRO     plays all three counters and keeps the monitor down unless
 *           Foxy has to be turned. Added 2026-10-01, because the set did
 *           not contain a competent player -- see {@link #pro}.
 *
 * <b>What the fixed sweep found.</b> HOLD beats REACT, which is the ladder
 * the design wants. FLEE is measurably *worse* than HOLD -- about ten
 * points over the week -- and that is the interesting result, because it
 * says the skill the design advertises is not the skill the game rewards.
 * Every step is a noise and the noise belongs to Ballora, so a player who
 * runs from Freddy all night is a player who is being followed all night;
 * and Freddy is not what kills you. Ballora is. The counter-intuitive
 * answer -- walk, and answer the room -- is the one that survives.
 *
 * <b>And the week does not get harder.</b> HOLD is 95/94/97/97/100 across
 * the five nights. That is the open item now: the AI level tops out at 20,
 * which means "always moves", so the last night is the most *predictable*
 * one, and predictability is a gift to a player who has learned the
 * building. The franchise convention is that AI 20 is the top of the
 * scale; FNAF 5 is the first game in it where the player can walk, so it
 * is the first where a deterministic threat is an easy one.
 *
 * <h2>2026-10-01: the week gets harder, and the ladder turns out to be
 * flatter than it looked</h2>
 *
 * The paragraph above was written from a sweep that could not be trusted,
 * and both halves of it have now moved.
 *
 * <b>The week gets harder.</b> HOLD is 95/94/91/82/49 across the five
 * nights, which is the shape Chase asked the franchise for. Two things had
 * to change. The first is {@link Game#INTERVAL_JITTER}: at AI 20 every roll
 * succeeds, so the last night was one scripted night and all sixty seeds
 * returned the identical result -- which is why the old table read 100% at
 * the end and why it could not be tuned. The second is that the ramp lives
 * in {@link Game#grace} now rather than in the interval: a threat that
 * moves faster also leaves your room faster, so shortening the interval
 * can make a night *easier*, and grace is the only dial with no
 * counter-effect.
 *
 * <b>And the ladder is flatter than it looked.</b> The old reading -- HOLD
 * clearly best, FLEE clearly worst -- was an artifact of the deterministic
 * engine: with every threat on a fixed interval, a fixed patrol is a
 * *resonant* patrol, and it happened to be a good one. With the night a
 * distribution, the three moving policies converge: HOLD 79%, FLEE 84%,
 * PANIC 86% over the week, a spread of seven points. Running from Freddy
 * is not worse than patrolling, and moving at random is not worse either.
 *
 * That is the open design problem, and it is not a tuning number. In a
 * line the player is faster than everything in it, so the *direction* of a
 * step barely matters -- the only threat that cares where you are is
 * Funtime Freddy, and he is the one that never kills anybody. The design
 * says the three threats demand contradictory things; what the sweep says
 * is that Ballora does almost all of the killing. Over the week HOLD takes
 * 64 deaths and 59 of them are hers; Funtime Foxy takes 4 and Funtime
 * Freddy takes 1. The contradiction is never felt, and fixing that means
 * giving Freddy and Foxy a way to matter, which is a redesign rather than
 * a table.
 *
 * <h2>2026-10-01, later: the redesign, and what it moved</h2>
 *
 * The paragraph above was right about the diagnosis and wrong about one
 * detail, and the detail is what made the fix possible.
 *
 * <b>The detail: the player was faster than everything, so the *stop* --
 * Ballora's actual counter -- was never needed.</b> The note above says the
 * direction of a step barely matters. What it does not say is that the
 * reason is speed: Ballora moved every 3.0 seconds on night 5 while the
 * player moved every 1.5, so a player who simply kept walking was always
 * ahead of her, and the only thing that ever caught them was the end of the
 * line. The design's answer to her is silence, and silence was a formality
 * nobody had to use.
 *
 * <b>Three rules changed, and the numbers moved with them.</b>
 *
 *   1. {@link Game#shock} removes the pursuer only. The shock was a
 *      universal answer, so "shock everything" beat "know which counter
 *      belongs to which threat" -- PANIC 86%, HOLD 79%.
 *   2. {@link Game#FEED_PACE} ties Funtime Foxy's speed to the monitor. He
 *      was the same speed either way, so looking was free and the one
 *      threat whose rule is about looking took 4 kills in a week.
 *   3. Ballora is faster than the player (pace 2.60). Moving is no longer
 *      an answer to her; stopping is.
 *
 * <b>What the sweep says now.</b> The ladder is FLEE 97%, HOLD 91%, PANIC
 * 0% over the week, and the killers are 12 Ballora, 11 Funtime Foxy and 3
 * Funtime Freddy -- three threats that all end nights, where there used to
 * be one. PANIC is last instead of first, which is the specific thing the
 * redesign was for: a game whose best strategy is to panic is a game whose
 * counters are decoration.
 *
 * <b>What is still open, and it is the honest part.</b> PRO -- the policy
 * that actually plays all three counters -- reads 0% on nights 3 to 5. The
 * stop is the right answer to Ballora and it is also exactly what Freddy is
 * built to punish, so a policy that uses it has to choose *where* to stop,
 * and this one does not: it stops wherever it happens to be, and Freddy
 * walks into the room while it waits. That is the next piece of work, and
 * it is a bot problem before it is a balance problem -- the redesign cannot
 * be judged until something in the suite can play it. The difficulty curve
 * is also generous on nights 1 to 3 (100% for HOLD), because the grace
 * table was set for an engine where the shock was the Ballora answer.
 */
public final class Bot {

    public enum Policy { IDLE, REACT, HOLD, FLEE, PANIC, PRO }

    /** How often the bot is allowed to change its mind, in seconds. */
    public static final double REACTION = 0.20;

    /**
     * How long a step next door counts as a warning.
     *
     * Not the same as the threat's own interval: a player who hears a step
     * and then waits to see what happens has already lost. This is the
     * window in which the bot treats the sound as "leave now".
     */
    public static final double HEARD_WINDOW = 1.6;

    /**
     * How long the bot trusts its last sighting of Funtime Freddy.
     *
     * A player does not forget where a seven-foot animatronic was standing
     * the moment they look away, but the memory does decay, and past this
     * the bot stops treating it as a direction to run in. Long enough to
     * act on, short enough that a stale reading cannot walk it into the
     * room Freddy has since moved to.
     */
    public static final double FREDDY_MEMORY = 5.0;

    public static Game run(int night, long seed, Policy policy) {
        return run(night, seed, policy, REACTION);
    }

    /** The same night, with the player's reaction time as a dial. */
    public static Game run(int night, long seed, Policy policy, double reaction) {
        Game g = new Game(night, seed);
        Brain b = new Brain();
        double dt = 1.0 / 60.0;
        double think = 0;
        // Hard stop, so a bug cannot spin forever.
        double limit = Game.HOUR_SECONDS * Game.NIGHT_HOURS + 5;
        while (g.status == Game.Status.PLAYING && g.time < limit) {
            think -= dt;
            if (think <= 0) {
                think = reaction;
                act(g, policy, b);
            }
            watch(g, b, dt);
            g.update(dt);
        }
        return g;
    }

    /**
     * The little bit of state a player has that the engine does not.
     *
     * A committed direction, and the last place Funtime Freddy was seen or
     * heard. A bot that re-decides from scratch every fifth of a second
     * spends the whole night turning around, and one that has no memory of
     * Freddy cannot run from him at all -- which is exactly how the first
     * version of this died.
     */
    static final class Brain {
        int dir = 1;
        /** The last room Funtime Freddy was known to be in, or null. */
        Room.Where freddy = null;
        /** Seconds since that was true. Large means "no idea". */
        double freddyAge = 999;
    }

    static void act(Game g, Policy policy, Brain b) {
        if (policy == Policy.IDLE) return;
        if (g.busy > 0) return;

        switch (policy) {
            case PANIC -> panic(g);
            case REACT -> react(g, b);
            case HOLD -> hold(g, b);
            case FLEE -> flee(g, b);
            case PRO -> pro(g, b);
            default -> { }
        }
    }

    // ------------------------------------------------------------- the rules

    /**
     * Deal with whatever is standing in the room.
     *
     * Returns true if there was an answer in the room. The three rules read
     * backwards are the whole game: Funtime Freddy follows you and nothing
     * distracts it, so the answer is the shock. Funtime Foxy follows the
     * camera, so the answer is to put the camera somewhere else. Ballora is
     * blind, so the book answer is silence -- and the sweep says the book
     * answer does not work, which is why this returns false for her and
     * lets the policy walk instead. See the note in the SOUND case.
     */
    static boolean counter(Game g, Brain b) {
        boolean any = false;
        boolean pursuit = false;
        boolean attention = false;
        for (Threat t : g.threats) {
            if (!t.inYourRoom(g)) continue;
            any = true;
            if (t.rule == Threat.Rule.PURSUIT) pursuit = true;
            if (t.rule == Threat.Rule.ATTENTION) attention = true;
        }
        if (!any) return false;

        // The three counters, in the order of how much they cost. Freddy
        // first, because the shock is the only answer he has -- and, since
        // 2026-10-01, the only answer the shock *is*: it removes him and
        // nothing else. See {@link Game#shock}.
        if (pursuit && g.shocks > 0) {
            g.shock();
            return true;
        }
        if (attention) {
            // Turn it around, and then get out of the room anyway. The
            // camera is the counter, but it is not a wall: Foxy is standing
            // in the doorway, and a player who stands there admiring their
            // own cleverness dies on the grace like anybody else.
            steer(g, b);
            return false;
        }
        // Freddy, with no charge left. There is nothing the room can answer
        // with, so this returns false and lets the policy fall through to
        // walking -- which is what a player does, and which is the only
        // thing that ever works on him.
        if (pursuit) return false;

        // Ballora alone. The book answer is to do nothing -- she is blind
        // and silence loses her -- and this returns false so the policy
        // falls through to walking, which is what HOLD and FLEE do and
        // what PRO deliberately does not. The note that used to live here
        // said the book answer could not work, because her patience was
        // 1.8 seconds and night 5's grace was also 1.8, so the stop was a
        // coin flip. That was a real finding and it is why her patience is
        // 1.2 now: a counter that only fits inside a generous night is not
        // a counter. PRO uses it; see {@link #pro} for what it costs.
        return false;
    }

    // ---------------------------------------------------------- the policies

    /** Answers what is in the room and nothing else. */
    static void react(Game g, Brain b) {
        counter(g, b);
    }

    /**
     * Answers what is in the room, and keeps walking.
     *
     * This is the competent player, and the whole of it is the second half:
     * never park. Everything in the building is slower than you are, so
     * distance is always available -- but only to somebody who is making
     * it, and a player who stands still is a player who has handed the
     * night to whoever arrives first.
     */
    static void hold(Game g, Brain b) {
        if (counter(g, b)) return;
        patrol(g, b);
    }

    /**
     * Answers the room, and runs from where Funtime Freddy was last known.
     *
     * The strategy the design describes, kept as a policy so it can be
     * measured instead of assumed. It is worse than HOLD, and the reason is
     * the game's own central bargain: every step is a noise, the noise is
     * Ballora's, and a player who runs from Freddy all night is being
     * followed by Ballora all night. Freddy is the thing that can end a
     * night; Ballora is the thing that does.
     */
    static void flee(Game g, Brain b) {
        if (counter(g, b)) return;

        // The bot does not get to read Freddy's room out of the engine;
        // see `watch`, which is the only thing that updates this, and which
        // can only see what a player can see.
        if (b.freddy != null && b.freddyAge < FREDDY_MEMORY) {
            int away = Integer.compare(Room.index(g.where), Room.index(b.freddy));
            if (away == 0) away = b.dir;
            // Never back into a dead end. A dead end is one door and a
            // wall, and one door is where anything corners you -- the first
            // build of this bot ran from Freddy until it ran out of
            // building, and then died in the corner it had chosen.
            Room.Where to = Room.at(Room.index(g.where) + away);
            if (to != null && deadEnd(to) && !deadEnd(g.where)) away = -away;
            if (g.step(away)) { b.dir = away; return; }
            if (g.step(-away)) { b.dir = -away; return; }
        }
        patrol(g, b);
    }

    /** Walk, without any opinion about where Funtime Freddy is. */
    static void patrol(Game g, Brain b) {
        int mid = Room.COUNT / 2;
        int here = Room.index(g.where);
        int inward = here == mid ? b.dir : (here < mid ? 1 : -1);
        if (tryStep(g, b, inward)) return;
        if (tryStep(g, b, -inward)) return;
        steer(g, b);
    }

    /**
     * The player who has read all three rules and plays them.
     *
     * <b>Added 2026-10-01, because the sweep did not contain one.</b> HOLD
     * was labelled "the competent player" and it is not: it answers Freddy
     * and Foxy and then walks forever, which is two of the three counters.
     * The third -- stop and let Ballora lose you -- was never played by any
     * policy in the set, so the reading "the game does not reward
     * competence" was taken from a policy that was not competent. This one
     * plays all three:
     *
     * <pre>
     *   Freddy in the room   shock him; he is the one with no other answer
     *   Foxy in the room     turn the feed, then keep walking
     *   Ballora in the room  stop, and put the monitor down while you wait
     *   nothing in the room  keep away from what you can perceive, and walk
     * </pre>
     *
     * The monitor stays down except when Foxy has to be turned, because the
     * feed is his fuel -- a player who parks it is a player being hunted.
     *
     * <b>It is a first attempt and it is not good yet.</b> It reads 0% on
     * nights 3 to 5 and dies to Funtime Freddy, because it stops for Ballora
     * without a plan for the pursuer who is closing while it waits, and it
     * runs out of shocks. That is a real finding rather than a bug in the
     * measurement: the stop is the right answer to Ballora and it is also
     * the thing Freddy is built to punish, so a policy that uses it has to
     * choose *where* to stop, and this one does not. It is in the suite so
     * the next pass has something to improve rather than something to
     * invent.
     */
    static void pro(Game g, Brain b) {
        boolean pursuit = false, attention = false, sound = false;
        for (Threat t : g.threats) {
            if (!t.inYourRoom(g)) continue;
            if (t.rule == Threat.Rule.PURSUIT) pursuit = true;
            if (t.rule == Threat.Rule.ATTENTION) attention = true;
            if (t.rule == Threat.Rule.SOUND) sound = true;
        }
        // Freddy: the shock is the only answer, and it clears the room.
        if (pursuit && g.shocks > 0) { g.shock(); return; }
        // Foxy: turn the feed, then keep moving -- the feed is not a wall.
        if (attention) steer(g, b);
        // Ballora: silence, and freeze Foxy while we wait her out.
        else if (sound) { g.monitorDown(); return; }

        // Keep away from anything we can perceive.
        Room.Where danger = nearestPerceived(g);
        if (danger != null && Room.distance(danger, g.where) <= 2) {
            int away = Integer.compare(Room.index(g.where), Room.index(danger));
            if (away == 0) away = b.dir;
            if (tryStep(g, b, away)) return;
            if (tryStep(g, b, -away)) return;
        }

        // The feed is Foxy's fuel: leave it down unless we need it.
        if (g.monitorOn && !attention) { g.monitorDown(); return; }

        int here = Room.index(g.where);
        int next = here + b.dir;
        if (next < 0 || next >= Room.COUNT) { b.dir = -b.dir; next = here + b.dir; }
        if (tryStep(g, b, b.dir)) return;
        b.dir = -b.dir;
        if (tryStep(g, b, b.dir)) return;
        steer(g, b);
    }

    /**
     * The nearest room the bot has a reason to believe holds something.
     *
     * The same two channels a player has for a room they are not standing
     * in -- the monitor, and a step heard next door -- and nothing else.
     */
    static Room.Where nearestPerceived(Game g) {
        Room.Where best = null;
        int bestD = 99;
        for (Threat t : g.threats) {
            boolean known = perceived(g, t.room)
                    || (g.heardAt == t.room && g.heardAge < HEARD_WINDOW);
            if (!known) continue;
            int d = Room.distance(t.room, g.where);
            if (d < bestD) { bestD = d; best = t.room; }
        }
        return best;
    }

    /** True at the two ends of the line, which are the only traps in it. */
    static boolean deadEnd(Room.Where w) {
        int i = Room.index(w);
        return i == 0 || i == Room.COUNT - 1;
    }

    /**
     * Step, unless the bot has a reason to think something is standing
     * there.
     *
     * Added 2026-10-01, because the competent policy was walking into
     * occupied rooms on purpose. It is the one place the player's
     * information is worth anything: the monitor and a step next door are
     * the only two channels for a room you are not in, and both of them are
     * available before you commit to a move rather than after.
     */
    static boolean tryStep(Game g, Brain b, int dir) {
        Room.Where dest = Room.at(Room.index(g.where) + dir);
        if (dest == null) return false;
        if (knownOccupied(g, dest)) return false;
        if (g.step(dir)) { b.dir = dir; return true; }
        return false;
    }

    /**
     * True when the bot has a reason to believe a threat is in this room.
     *
     * The two channels a player has for a room they are not standing in:
     * the monitor, if it is pointed there, and a step heard next door. Both
     * are the FNAF 4 rule -- the bot may read the engine only where a
     * player could have looked or listened -- and neither is a cheat: the
     * monitor is already pointed somewhere, and the step is already in the
     * cue stream.
     */
    static boolean knownOccupied(Game g, Room.Where room) {
        if (perceived(g, room) && g.standingIn(room) != null) return true;
        return g.heardAt == room && g.heardAge < HEARD_WINDOW;
    }

    /**
     * Point the camera so that Funtime Foxy goes away from us.
     *
     * This is the part of the game that took the longest to see. Foxy
     * follows the feed, so the camera is not just a window -- it is a
     * steering wheel, and pointing it at the far end of the building is
     * exactly the wrong thing to do, because the far end is on the other
     * side of you and Foxy will walk straight through your room to get
     * there. What you want is the room on the far side of <i>Foxy</i>, so
     * it turns around and walks away.
     */
    static void steer(Game g, Brain b) {
        Threat foxy = byKey(g, "foxy");
        if (foxy != null && (perceived(g, foxy.room) || foxy.inYourRoom(g))) {
            int away = Integer.compare(Room.index(foxy.room), Room.index(g.where));
            if (away == 0) away = 1;
            Room.Where beyond = Room.at(Room.index(foxy.room) + away);
            if (beyond != null && beyond != g.where && g.view() != beyond) {
                g.watch(beyond);
                return;
            }
        }
        Room.Where far = farFrom(g, g.where);
        if (far != null && g.view() != far) g.watch(far);
    }

    // ------------------------------------------------------------- the eyes

    /**
     * Update what the bot knows about Funtime Freddy, from the three
     * channels a player actually has.
     *
     * This is the FNAF 4 rule made explicit rather than left to discipline:
     * the bot may read a threat's room only when that room is one a player
     * could be looking at or listening to. It is the same room, the same
     * animatronic and the same engine field -- the difference is entirely
     * in whether the information was available, and that difference is the
     * difference between a difficulty table and a number.
     */
    static void watch(Game g, Brain b, double dt) {
        b.freddyAge += dt;
        Threat f = byKey(g, "freddy");
        if (f == null) return;
        if (f.room == g.where) { b.freddy = f.room; b.freddyAge = 0; return; }
        if (g.view() != null && f.room == g.view()) { b.freddy = f.room; b.freddyAge = 0; return; }
        if (f.key.equals(g.heardKey) && g.heardAt == f.room && g.heardAge < 0.5) {
            b.freddy = f.room;
            b.freddyAge = 0;
        }
    }

    /** True when the bot can see this room: it is in it, or on the camera. */
    static boolean perceived(Game g, Room.Where room) {
        return room == g.where || room == g.view();
    }

    /** A threat by its short key, or null. */
    static Threat byKey(Game g, String key) {
        for (Threat t : g.threats) if (t.key.equals(key)) return t;
        return null;
    }

    /** Shocks at everything and never stands still. */
    static void panic(Game g) {
        if (g.standingWith() != null) {
            if (g.shocks > 0) { g.shock(); return; }
        }
        int d = g.rng.nextBoolean() ? 1 : -1;
        if (!g.step(d)) g.step(-d);
    }

    /** The room furthest from a given one, never the one you are in. */
    static Room.Where farFrom(Game g, Room.Where from) {
        Room.Where best = null;
        int bestD = -1;
        for (Room.Where w : Room.ALL) {
            if (w == g.where) continue;
            int d = Room.distance(w, from);
            if (d > bestD) { best = w; bestD = d; }
        }
        return best;
    }

    // ------------------------------------------------------------- the sweep

    /** Survival rate over {@code runs} seeds. */
    public static double survival(int night, int runs, Policy policy) {
        return survival(night, runs, policy, REACTION);
    }

    /** Survival rate over {@code runs} seeds, at a given reaction time. */
    public static double survival(int night, int runs, Policy policy, double reaction) {
        int wins = 0;
        for (int i = 0; i < runs; i++) {
            if (run(night, 1000L + i * 7919L, policy, reaction).status == Game.Status.SURVIVED) wins++;
        }
        return wins / (double) runs;
    }

    /**
     * Who ends the night, over {@code runs} seeds.
     *
     * The number that found FNAF 5's open design problem, and the reason it
     * is in the suite rather than in a scratch file. Survival says how often
     * you die; this says who killed you, and the answer is that almost all
     * of them are Ballora. Funtime Freddy is the one the design is
     * built around -- he is the only one that can end a night, which is why
     * the shock exists and why the player is faster than everything -- and
     * he never lands a hit. A survival percentage cannot tell you that; a
     * count of killers can. See the note in {@link Bot}.
     *
     * Counts in cast order: Ballora, Funtime Foxy, Funtime Freddy, then
     * anything unrecognised.
     */
    public static int[] killers(int night, int runs, Policy policy) {
        int[] out = new int[4];
        for (int i = 0; i < runs; i++) {
            Game g = run(night, 1000L + i * 7919L, policy);
            if (g.status == Game.Status.SURVIVED) continue;
            String k = g.killer == null ? "" : g.killer;
            if (k.startsWith("Ballora")) out[0]++;
            else if (k.startsWith("Funtime Foxy")) out[1]++;
            else if (k.startsWith("Funtime Freddy")) out[2]++;
            else out[3]++;
        }
        return out;
    }

    /**
     * How slow a competent player is allowed to be, in seconds.
     *
     * The survival percentage is not FNAF 5's difficulty -- this is. The
     * percentage is close to a step function: a player who keeps moving
     * survives every night of the week, and a player who cannot get out of
     * the room they are in dies on every night of it. There is very little
     * in between, because the building is a line, the player is faster than
     * everything in it, and the only thing that can actually catch them is
     * being mid-move when something arrives.
     *
     * So the week's real dial is the margin between the grace and the time
     * it takes to leave: a move (1.5s) plus a reaction. This finds that
     * margin by bisection -- the slowest reaction at which HOLD still
     * survives every seed. A night with a flip of 1.2s is a night you can
     * walk out of half asleep. A night with a flip of 0.4s is a night that
     * asks for a reaction faster than most people have.
     *
     * <b>A flip of 0.00 means no reaction time is fast enough</b>, which is
     * what nights 4 and 5 now read: HOLD does not survive every seed at any
     * reaction, so there is no margin to find. That is the intended shape
     * at the end of the week -- the last nights are not about reacting
     * faster, they are about having decided where to be -- and it is worth
     * reading the number that way rather than as a broken measurement.
     */
    public static double flipReaction(int night, int runs) {
        double lo = 0.0;    // survives
        double hi = 2.0;    // does not
        for (int i = 0; i < 12; i++) {
            double mid = (lo + hi) / 2;
            if (survival(night, runs, Policy.HOLD, mid) >= 1.0) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    private Bot() {}
}
