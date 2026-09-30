package aside.games.fnaf4.engine;

/**
 * A blind player, for tuning.
 *
 * A game with a random outcome cannot be balanced by playing it. Twenty
 * nights by hand tell you nothing about a 40%-vs-28% difference, and they
 * tell you nothing at all about night 5 if you cannot get past night 3.
 * So the loop gets a player that can be run ten thousand times with no
 * display, and the difficulty is set by sweeping it.
 *
 * The bot is deliberately *not* a good player. It has perfect information
 * -- it reads the engine's own state rather than the screen -- and it
 * reacts on a fixed delay. That makes it an upper bound: if the bot cannot
 * survive a night, nobody can, and if the bot survives every time, the
 * night is free. The interesting number is in between.
 *
 * Four policies, because one number is not a reading:
 *
 *   IDLE    does nothing at all. Must die on every night.
 *   REACT   only ever answers a breath -- it goes to what is already
 *           standing in a doorway. This is the honest first-night player,
 *           and it is the one the grace table is set against.
 *   HOLD    pushes things back one move out as well, and returns to the
 *           bed when the room is quiet. Strictly better than REACT if the
 *           design is right, because that is what the extra reach is for.
 *   SWEEP   flashes wherever it happens to be standing, constantly. Must
 *           be much worse than HOLD, or the flashlight has no cost.
 */
public final class Bot {

    public enum Policy { IDLE, REACT, HOLD, SWEEP }

    /** How often the bot is allowed to change its mind, in seconds. */
    public static final double REACTION = 0.20;

    public static Game run(int night, long seed, Policy policy) {
        Game g = new Game(night, seed);
        double dt = 1.0 / 60.0;
        double think = 0;
        // Hard stop, so a bug cannot spin forever.
        double limit = Game.HOUR_SECONDS * Game.NIGHT_HOURS + 5;
        while (g.status == Game.Status.PLAYING && g.time < limit) {
            think -= dt;
            if (think <= 0) {
                think = REACTION;
                act(g, policy);
            }
            g.update(dt);
        }
        return g;
    }

    static void act(Game g, Policy policy) {
        if (policy == Policy.IDLE) return;
        if (g.busy > 0) return;

        if (policy == Policy.SWEEP) {
            g.flash();
            return;
        }

        Threat target = pick(g, policy);
        double targetTtd = target == null ? Double.MAX_VALUE : timeToDeath(g, target);

        // Fredbear is a hard deadline with a long fuse. He is worth the
        // trip whenever he is the soonest thing to end the night, and not
        // before -- walking away from a door that is about to open in order
        // to look for something with eight seconds left on it is how a
        // player loses two threats at once.
        double fbTtd = g.fredbearAt == null ? Double.MAX_VALUE
                : Game.FREDBEAR_GRACE - g.fredbearHere;

        if (fbTtd < targetTtd) {
            if (g.where == g.fredbearAt) g.flash();
            else g.moveTo(g.fredbearAt);
            return;
        }
        if (target != null) {
            if (g.where == target.home) g.flash();
            else g.moveTo(target.home);
            return;
        }
        // Nothing is close. The bed is one hop from every door, so it is
        // where a player who is not needed anywhere should be.
        if (g.where != Room.HUB) g.moveTo(Room.HUB);
    }

    /**
     * The thing to deal with next.
     *
     * Ordered by time-to-death, and then filtered by whether it is
     * reachable at all -- a threat two spokes away with half a second left
     * is already lost, and running at it means losing the one behind you
     * as well. That filter is the whole skill of the game, so the bot has
     * to have it or the survival numbers are meaningless.
     */
    static Threat pick(Game g, Policy policy) {
        Threat best = null;
        double bestTtd = Double.MAX_VALUE;
        boolean bestReachable = false;

        for (Threat t : g.threats) {
            // The bot only knows what a player knows. A threat two or more
            // moves out makes no sound, so a player has no idea it is
            // there, and a bot that reads `distance` directly would camp
            // the door it is coming to and never lose. That is not a
            // difficulty reading, it is a cheat -- and it took a
            // diagnostic run to notice, because the survival numbers
            // looked like a hard game rather than an impossible one.
            if (t.distance > 1) continue;
            if (policy == Policy.REACT && t.distance > 0) continue;
            double ttd = timeToDeath(g, t);
            double travel = Room.hops(g.where, t.home) * Game.HOP_TIME
                    + Game.FLASH_TIME;
            boolean reachable = ttd > travel;
            // A reachable threat always beats an unreachable one; among
            // equals, the sooner one wins.
            if (best != null && bestReachable && !reachable) continue;
            if (best != null && bestReachable == reachable && ttd >= bestTtd) continue;
            best = t;
            bestTtd = ttd;
            bestReachable = reachable;
        }

        if (best == null && g.fredbearAt != null) return null;
        return best;
    }

    /** Seconds until this threat ends the night, if nothing is done. */
    static double timeToDeath(Game g, Threat t) {
        if (t.distance == 0) return g.grace() - t.hereFor;
        double interval = g.interval(t);
        return (t.distance - 1) * interval + (interval - t.timer) + g.grace();
    }

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
