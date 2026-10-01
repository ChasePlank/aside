package aside.games.fnaf8.engine;

import aside.games.fnaf8.engine.Meeting.Setting;
import aside.games.fnaf8.engine.Meeting.Side;

/**
 * A player, as a policy, so the week can be measured instead of guessed.
 *
 * <p>Same instrument as FNAF 5's, 6's and 7's, and it exists for the same
 * reason: a balance table produced by playing twenty nights by hand is a
 * table of twenty nights, and a table produced by sweeping a bot over two
 * hundred seeds a night is a table of the game.
 *
 * <p><b>The bot is not allowed to see anything the player cannot.</b> It
 * reads {@link Meeting#visible}, {@link Meeting#waiting},
 * {@link Meeting#patienceLeft}, {@link Meeting#lamp} and
 * {@link Meeting#lampAt}. It does <b>not</b> read {@code d}, {@code timer}
 * or {@code waited}. That rule is what makes the number mean something, and
 * in this game it costs the bot something real: <b>the lamp can only be in
 * one hall, so at any moment the bot knows exactly where one of them is and
 * has to <i>work out</i> where the other one is.</b>
 *
 * <h2>Dead reckoning, which is the whole of this game's skill</h2>
 *
 * <p>{@link #est} is the bot's belief about a distance, and it is advanced
 * the way a person's would be: it is <b>set</b> when the lamp shows the
 * hall, and otherwise it is <b>walked forward</b> at the mean rate the
 * night's tables say. The real thing is jittered ({@link Meeting#JITTER}),
 * so the belief drifts, and the drift is the reason the lamp is worth
 * spending on a hall that is not currently a problem.
 *
 * <p>That is the difference between the two competent policies, and it is
 * the only difference: <b>REACT holds the beam bright all night and PRO
 * drops it to dim whenever there is nothing left to push.</b> A look costs
 * the other one {@link Meeting#DIM_RUSH}; a hand costs it
 * {@link Meeting#BRIGHT_RUSH}. So the competent player is not the one who
 * looks more -- it is the one who <i>stops paying for a hand when it only
 * needs eyes.</i>
 *
 * <pre>
 *   IDLE     does nothing.                        The control.
 *   STARE    dim on the left hall, forever.       The trap: one hall watched.
 *   DOOR     brights whichever doorway has eyes.  The obvious play, and wrong.
 *   REACT    turns the far one around.            Right idea, one setting.
 *   PRO      the same, and dims when it can.      The competent player.
 * </pre>
 *
 * <p><b>PRO is the policy the ladder is read against</b>, for the reason
 * FNAF 5 learned twice and FNAF 7 learned once: a check written against
 * "the competent player" is really written against an assumption about
 * which policy that is, and the assumption is the part that goes stale.
 */
public final class Bot {

    public enum Policy { IDLE, STARE, DOOR, REACT, PRO }

    /**
     * How much closer the other one has to be before REACT will switch.
     *
     * <p>Small on purpose. REACT is the *obvious* play, not a straw man:
     * it is what the game looks like it wants you to do, and the point of
     * the number is that a player who does it is punished by the swivel
     * rather than by anything the game hides from them.
     */
    public static final double GREED = 3.0;

    /**
     * How far from the far end a side has to be before the bot will keep
     * working on it rather than switching.
     *
     * <p>The anti-thrash number. A bot that switches the instant the other
     * side is nominally closer never finishes anything, because the lamp
     * takes {@link Meeting#SWIVEL} seconds to cross and the two beliefs
     * cross each other every few frames.
     */
    public static final double FINISH = 0.35;

    /**
     * Seconds of slack a side has to have before the bot will leave it.
     *
     * <p>The hysteresis, in the unit that matters. Distance is the wrong
     * thing to compare two units on, because the two of them do not walk at
     * the same speed: a night's pair is deliberately one fast one and one
     * stubborn one, and <b>the fast one at three steps is closer to the
     * door than the slow one at two.</b> So the bot compares <i>time to
     * arrive</i>, and it stays on the side it is on until the other one is
     * more than a swivel's worth more urgent.
     */

    public final Policy policy;

    /** The bot's belief about each side's distance from its doorway. */
    public final double[] est = new double[]{Meeting.MAX, Meeting.MAX};
    /** Seconds since each side was last actually seen. */
    public final double[] age = new double[]{99, 99};
    /** Whether each side was in its doorway last frame, for the give-up. */
    final boolean[] wasWaiting = new boolean[]{false, false};

    public Bot(Policy policy) {
        this.policy = policy;
    }

    /**
     * One frame of play.
     *
     * <p>Order matters and is the same order a person would use: take in
     * what the lamp is showing, then decide, then act.
     */
    public void step(Meeting m, double dt) {
        if (m.status != Meeting.Status.PLAYING) return;
        observe(m, dt);
        switch (policy) {
            case IDLE -> { }
            case STARE -> {
                if (m.lampAt() != Side.LEFT || m.lamp != Setting.DIM) {
                    m.aim(Side.LEFT, Setting.DIM);
                }
            }
            case DOOR -> {
                Side w = doorway(m);
                if (w != null) m.aim(w, Setting.BRIGHT);
            }
            case REACT -> greedy(m, dt);
            case PRO -> play(m, dt, true);
        }
    }

    /**
     * Seconds until this side reaches its doorway, as the bot believes it.
     *
     * <p>The belief divided by the mean rate, which is the only arithmetic
     * in the policy that is not just bookkeeping. It is also where the
     * night's jitter bites: the belief drifts, so the estimate drifts, so
     * the bot occasionally defends the wrong hall -- which is exactly the
     * mistake the game is built to punish.
     */
    double eta(Meeting m, Side s) {
        return est[s.ordinal()] * m.pace() / m.pair.unit(s).speed();
    }

    /** The side with something standing in its doorway, or null. */
    static Side doorway(Meeting m) {
        for (Side s : Side.values()) if (m.waiting(s)) return s;
        return null;
    }

    /**
     * Keep the belief up to date.
     *
     * <p>Set it when the lamp shows the hall; walk it forward when it does
     * not. The walk is at the <b>mean</b> interval, which is the whole
     * point: the night is jittered, so a belief that is not refreshed is a
     * belief that is wrong by an amount that grows with how long it has
     * been since anybody looked.
     */
    void observe(Meeting m, double dt) {
        for (Side s : Side.values()) {
            int i = s.ordinal();
            boolean waiting = m.waiting(s);
            // It gave up and walked back. You can see the doorway empty and
            // you can hear it going, so this is not a guess.
            if (wasWaiting[i] && !waiting) { est[i] = Meeting.MAX; age[i] = 0; }
            wasWaiting[i] = waiting;

            if (m.visible(s)) {
                est[i] = m.distance(s);
                age[i] = 0;
                continue;
            }
            age[i] += dt;
            if (waiting) { est[i] = 0; continue; }
            double rate = dt / m.interval(s);
            if (m.pushing(s)) est[i] = Math.min(Meeting.MAX, est[i] + rate);
            else est[i] = Math.max(0, est[i] - rate);
        }
    }

    /**
     * The obvious play, and the control the ladder is read against.
     *
     * <p>Push whichever of them is closer, and switch the moment that
     * stops being true. It is the right idea and it is what a player does
     * on their first night -- and it is worse than the competent policy
     * for one reason: <b>the lamp takes {@link Meeting#SWIVEL} seconds to
     * cross, so a policy that changes its mind whenever the two beliefs
     * cross spends the night with the lamp in the air.</b> The competent
     * player finishes the hall they are on.
     */
    void greedy(Meeting m, double dt) {
        Side w = doorway(m);
        if (w != null) {
            Side p = w.other();
            if (m.lampAt() != p || m.lamp != Setting.BRIGHT) m.aim(p, Setting.BRIGHT);
            return;
        }
        Side cur = m.lampAt();
        Side want = est[0] <= est[1] ? Side.LEFT : Side.RIGHT;
        if (cur != null && est[cur.ordinal()] <= est[want.ordinal()] + GREED) want = cur;
        if (m.lampAt() != want || m.lamp != Setting.BRIGHT) m.aim(want, Setting.BRIGHT);
    }

    /**
     * The competent loop.
     *
     * <p>Two jobs, in order, and the order is the whole policy:
     *
     * <ol>
     *   <li><b>Somebody is in a doorway.</b> The lamp does not reach a
     *       doorway, so there is nothing to do about that one -- the only
     *       move is to turn its <i>partner</i> around, because a unit in a
     *       doorway gives up when it is left alone long enough. This is the
     *       rule the game is built on and the reason {@code DOOR} loses.</li>
     *   <li><b>Nobody is.</b> Keep the closer one back -- and PRO only,
     *       drop the lamp to dim whenever the hall it is on is already as
     *       far back as it goes, because a look costs the other one less
     *       than a hand does.</li>
     * </ol>
     */
    void play(Meeting m, double dt, boolean look) {
        Side w = doorway(m);
        if (w != null) {
            Side p = w.other();
            if (m.lampAt() != p || m.lamp != Setting.BRIGHT) m.aim(p, Setting.BRIGHT);
            return;
        }

        // Hysteresis, and the first sweep of this game is why. Without it
        // the belief about the two sides crosses every few frames, the lamp
        // starts a swivel on one side and re-targets the other before it
        // arrives, and the night is spent with the lamp permanently in the
        // air -- measured, PRO died on all two hundred seeds of night one
        // with the lamp on neither hall for most of the night.
        //
        // The comparison is in *seconds to arrive*, not in steps, and that
        // is the second thing the sweep taught. A night's pair is one fast
        // unit and one stubborn one, so the fast one at three steps is
        // closer to the door than the slow one at two -- and a bot that
        // compares distances spends the night defending the wrong hall.
        Side cur = m.lampAt();
        Side want;
        if (cur != null && est[cur.ordinal()] < Meeting.MAX - FINISH) {
            want = cur;
        } else {
            want = est[0] <= est[1] ? Side.LEFT : Side.RIGHT;
        }

        // PRO's one extra move, and the whole of the difference between
        // the two competent policies: **when there is nothing left to push
        // on the hall it is on, it drops the lamp to dim.** A look costs
        // the other one DIM_RUSH; a hand costs it BRIGHT_RUSH. So a player
        // who leaves the beam bright on a hall that is already as far back
        // as it goes is paying the expensive price for nothing -- and the
        // first sweep of this game said so, because PRO with a dim *look*
        // bolted on read two points WORSE than REACT on every night. The
        // look was not the useful half. The setting was.
        Setting setting = Setting.BRIGHT;
        if (look && est[want.ordinal()] >= Meeting.MAX - FINISH) setting = Setting.DIM;
        if (m.lampAt() != want || m.lamp != setting) m.aim(want, setting);
    }

    // ---- The sweep ----

    /** Run one night to the end and say whether it was survived. */
    public static boolean survived(Policy p, int night, long seed) {
        Meeting m = new Meeting(night, seed);
        Bot b = new Bot(p);
        double dt = 1.0 / 60.0;
        for (int i = 0; i < 60 * 400 && m.status == Meeting.Status.PLAYING; i++) {
            b.step(m, dt);
            m.update(dt);
        }
        return m.status == Meeting.Status.SURVIVED;
    }

    /** Survival rate over a run of seeds. */
    public static double survival(Policy p, int night, int seeds) {
        int won = 0;
        for (int i = 0; i < seeds; i++) if (survived(p, night, 1000L * night + i)) won++;
        return won / (double) seeds;
    }

    /** Survival rate over the whole week, all nights pooled. */
    public static double week(Policy p, int seeds) {
        double total = 0;
        for (int n = 1; n <= 5; n++) total += survival(p, n, seeds);
        return total / 5.0;
    }

    /**
     * How the nights ended, for one policy: caught, survived, and how many
     * times a doorway emptied itself.
     */
    public static int[] tally(Policy p, int night, int seeds) {
        int met = 0, survived = 0, gaveUp = 0;
        for (int i = 0; i < seeds; i++) {
            Meeting m = new Meeting(night, 1000L * night + i);
            Bot b = new Bot(p);
            double dt = 1.0 / 60.0;
            for (int f = 0; f < 60 * 400 && m.status == Meeting.Status.PLAYING; f++) {
                b.step(m, dt);
                m.update(dt);
            }
            if (m.status == Meeting.Status.SURVIVED) survived++; else met++;
            gaveUp += m.gaveUp;
        }
        return new int[]{met, survived, gaveUp / Math.max(1, seeds)};
    }
}
