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
 * Four policies, because one number is not a reading:
 *
 *   IDLE    does nothing at all. Must die on every night.
 *   REACT   answers what is in the room with it, and nothing else. This is
 *           the honest first-night player, and it is what the grace table
 *           is set against.
 *   HOLD    does that too, and also leaves when it hears something arrive
 *           next door. Strictly better than REACT if the design is right,
 *           because pre-empting is what the warning is for.
 *   PANIC   shocks the moment anything is in the room and never stops
 *           moving. Must be much worse than HOLD, or the shock has no cost
 *           and Ballora has no teeth.
 */
public final class Bot {

    public enum Policy { IDLE, REACT, HOLD, PANIC }

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

    public static Game run(int night, long seed, Policy policy) {
        Game g = new Game(night, seed);
        Brain b = new Brain();
        double dt = 1.0 / 60.0;
        double think = 0;
        // Hard stop, so a bug cannot spin forever.
        double limit = Game.HOUR_SECONDS * Game.NIGHT_HOURS + 5;
        while (g.status == Game.Status.PLAYING && g.time < limit) {
            think -= dt;
            if (think <= 0) {
                think = REACTION;
                act(g, policy, b);
            }
            g.update(dt);
        }
        return g;
    }

    /**
     * The little bit of state a player has that the engine does not.
     *
     * A committed direction, mostly. A player picks a way and goes; a bot
     * that re-decides from scratch every fifth of a second spends the whole
     * night turning around.
     */
    static final class Brain {
        int dir = 1;
        /**
         * Which side Funtime Freddy was last heard on.
         *
         * Freddy is the only thing that can end the night, and it is also
         * the only thing the player has to keep running from -- the player
         * is faster than it, so the way you survive Freddy is distance, and
         * distance only exists if you keep making it. Forgetting which side
         * it was on is how the bot ended up standing in the middle of the
         * building while Freddy walked in from the left.
         */
        int freddySide = 0;
    }

    static void act(Game g, Policy policy, Brain b) {
        if (policy == Policy.IDLE) return;
        if (g.busy > 0) return;

        switch (policy) {
            case PANIC -> panic(g);
            case REACT -> react(g, b);
            case HOLD -> hold(g, b);
            default -> { }
        }
    }

    // ------------------------------------------------------------- the rules

    /**
     * Deal with whatever is standing in the room.
     *
     * Returns true if there was anything to deal with. The three answers
     * are the three rules read backwards, and they are the whole game:
     * Ballora is blind, so silence loses her and the answer is to do
     * nothing. Funtime Foxy follows the camera, so the answer is to put the
     * camera somewhere else. Funtime Freddy follows you and nothing
     * distracts it, so the answer is the shock -- and that is why there are
     * two or three charges a night and no way to earn more.
     *
     * You cannot walk out. The engine refuses that, because the thing is
     * standing in the doorway.
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
        // first, because it is the only one that can end the night and
        // because it is the only one the other two cannot wait out.
        if (pursuit) {
            if (g.shocks > 0) g.shock();
            return true;
        }
        if (attention) {
            steer(g);
            return true;
        }
        // Ballora alone. The book answer is to do nothing -- she is blind
        // and silence loses her -- and the book answer is wrong here,
        // because standing still is what lets Funtime Freddy close the
        // distance, and Freddy is the only one of the three that can end
        // the night. So walk: it costs a noise, and the noise is hers, but
        // the alternative costs the whole room.
        if (g.step(b.dir)) return true;
        if (g.step(-b.dir)) return true;
        return true;
    }

    // ---------------------------------------------------------- the policies

    /** Answers what is in the room and nothing else. */
    static void react(Game g, Brain b) {
        counter(g, b);
    }

    /** Answers what is in the room, and leaves when it hears something coming. */
    static void hold(Game g, Brain b) {
        if (counter(g, b)) return;

        // Funtime Freddy is the only thing in the building that can end the
        // night, so it is the only thing worth running from. Everything
        // else is a cost: Ballora is what moving costs, Foxy is what
        // looking costs.
        if (g.heardAge < HEARD_WINDOW && g.heardAt != null) {
            int away = Integer.compare(Room.index(g.where), Room.index(g.heardAt));
            if ("freddy".equals(g.heardKey)) b.freddySide = away;
            if (away != 0) {
                Room.Where to = Room.at(Room.index(g.where) + away);
                // Never back into a dead end. That is how the first build
                // of this bot died every single time: it ran from Freddy
                // until it ran out of building, and then Freddy walked in
                // and it had one door and a wall.
                if (to != null && !deadEnd(to)) {
                    b.dir = away;
                    if (g.step(away)) return;
                }
                if (g.step(-away)) return;
            }
        }

        // Keep moving. Freddy is slower than we are, so the only way it
        // ever reaches us is if we stop -- and standing still is what
        // Ballora wants anyway, so there is nothing to gain by it. Head
        // for the middle: a dead end is one door and a wall, and one door
        // is where Freddy corners you.
        int mid = Room.COUNT / 2;
        int here = Room.index(g.where);
        // Keep running from Freddy. It is slower than we are, so distance
        // is the whole defence -- and distance only exists if we keep
        // making it.
        if (b.freddySide != 0 && g.step(b.freddySide)) return;

        int inward = here == mid ? b.dir : (here < mid ? 1 : -1);
        if (g.step(inward)) return;
        if (g.step(-inward)) return;
        // Nothing worked. Move anyway: a player who is out of ideas still
        // has to be somewhere, and standing still is the one thing that
        // lets Funtime Freddy finish.
        int d = g.rng.nextBoolean() ? 1 : -1;
        if (g.step(d)) return;
        g.step(-d);

        // Nothing to run from. Stand still -- every step is a noise, and
        // the noise is Ballora's whole information channel -- and keep the
        // camera on the far end, which is where Funtime Foxy gets sent and
        // therefore the one place it cannot be standing in the door we need.
        steer(g);
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
    static void steer(Game g) {
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

    /** True when the bot can see this room: it is in it, or on the camera. */
    static boolean perceived(Game g, Room.Where room) {
        return room == g.where || room == g.view();
    }

    /** True at the two ends of the line, which are the only traps in it. */
    static boolean deadEnd(Room.Where w) {
        int i = Room.index(w);
        return i == 0 || i == Room.COUNT - 1;
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
        int wins = 0;
        for (int i = 0; i < runs; i++) {
            if (run(night, 1000L + i * 7919L, policy).status == Game.Status.SURVIVED) wins++;
        }
        return wins / (double) runs;
    }

    private Bot() {}
}
