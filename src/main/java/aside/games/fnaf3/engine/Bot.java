package aside.games.fnaf3.engine;

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
 * reacts on a fixed delay. That makes it an upper bound: if the bot
 * cannot survive a night, nobody can, and if the bot survives every time,
 * the night is free. The interesting number is in between, and it is the
 * one the difficulty table is chosen against.
 *
 * Three policies, because one number is not a reading:
 *
 *   IDLE    does nothing at all. Must die on every night.
 *   NO_LURE reboots and watches, but never plays the lure. Must be worse
 *           than COMPETENT on every night, or the lure is decoration.
 *   COMPETENT reboots, watches, and lures. The reference player.
 */
public final class Bot {

    public enum Policy { IDLE, NO_LURE, COMPETENT }

    /** How often the bot is allowed to change its mind, in seconds. */
    public static final double REACTION = 0.35;

    /** Where a competent player sends him: the far end of the building. */
    public static final int FAR_ROOM = 10;

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

        int d = g.springtrap.distanceToOffice();

        // 1. Anything down gets rebooted, in the order that keeps you
        //    alive: the lure if he is close enough to need it, then the
        //    air (which is what makes the phantoms), then the lure, then
        //    the cameras.
        if (g.rebooting < 0) {
            // Do not start a panel while the lure is available and he is
            // close: the reboot locks the lure out for four seconds, and
            // four seconds is most of a walk.
            boolean urgent = g.springtrap.atOffice || d <= 1;
            if (!g.audioOnline() && (g.springtrap.atOffice || d <= 2)) {
                g.startReboot(Game.System.AUDIO);
            } else if (!g.ventilationOnline() && !(urgent && g.lureReady())) {
                g.startReboot(Game.System.VENTILATION);
            } else if (!g.audioOnline() && !(urgent && g.lureReady())) {
                g.startReboot(Game.System.AUDIO);
            } else if (!g.camerasOnline() && !(urgent && g.lureReady())) {
                g.startReboot(Game.System.CAMERAS);
            }
        }

        if (policy != Policy.COMPETENT) return;

        // 2. Send the noise to the far end whenever it is available and he
        //    is anywhere near. This is the whole skill of the game: the
        //    lure is worth the distance between him and where you send
        //    him, and the far end is the most distance there is.
        if (d <= 3 || g.springtrap.atOffice) {
            if (g.lureReady()) g.playLure(FAR_ROOM);
        }
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
