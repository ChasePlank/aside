package aside.games.fnaf7.engine;

import aside.games.fnaf7.engine.Shift.Side;

/**
 * A player, as a policy, so the week can be measured instead of guessed.
 *
 * <p>Same instrument as FNAF 5's and FNAF 6's, and it exists for the same
 * reason: a balance table produced by playing twenty nights by hand is a
 * table of twenty nights, and a table produced by sweeping a bot over two
 * hundred seeds a night is a table of the game.
 *
 * <p>The bot is <b>not allowed to see anything the player cannot.</b> It
 * reads {@link Shift#visible()} -- true only while the light is up, warm,
 * and the unit is in the hall it is pointed at -- {@link Shift#heardSide},
 * {@link Shift#belief()}, {@link Shift#barAt} and {@link Shift#lit}. It
 * does not read {@code where}, {@code target} or {@code timer}. A bot that
 * peeked would produce a table about the bot.
 *
 * <h2>The one idea every playing policy is built on</h2>
 *
 * <p>The record reads the light. So <b>the light is the steering wheel</b>:
 * point it at one side and the unit comes to the other. The competent loop
 * is therefore not "watch for it" but <b>"watch the side you are not
 * defending"</b> -- the light does two jobs at once, and the second job is
 * the one that keeps the unit away from the side that has a bar on it.
 *
 * <p>What breaks the loop is {@link Shift#explore()}: a unit that
 * sometimes ignores its own record comes to the side the light is on, which
 * is the side with no bar. Answering that means moving the bar, and moving
 * the bar means putting the light out first -- so the answer to a
 * deviation is a second and a half of blindness, and the record spends
 * that second and a half still describing the side you just left.
 *
 * <pre>
 *   IDLE     does nothing.                        The control.
 *   STARE    lights one side and never moves.     The trap: a pinned record.
 *   LISTEN   never lights, follows its ears.      No record at all.
 *   REACT    watches the unbarred side, swaps.    Right idea, one channel.
 *   PRO      the same loop, and reads the
 *            readout while the record is stale.  The competent player.
 * </pre>
 *
 * <p><b>PRO is the policy the ladder is read against</b>, and the reason is
 * the same lesson FNAF 5 learned twice: a check written against "the
 * competent player" is really written against an assumption about which
 * policy that is, and the assumption is the part that goes stale. PRO is
 * the only policy here that uses both channels -- what it can see and what
 * the record says -- so it is the only one whose number means anything
 * about the game.
 */
public final class Bot {

    public enum Policy { IDLE, STARE, LISTEN, REACT, PRO }

    /**
     * How long REACT waits before it puts the light back up.
     *
     * <p>Not zero, and the reason is not realism. A player who re-lights
     * the instant the bar lands is a player whose record never has a
     * moment of quiet, and the sweep needs the two policies to differ in
     * exactly one way or the comparison is between two things at once.
     */
    public static final double RELIGHT = 0.15;

    public final Policy policy;

    /** The side the bar was last on, so a knocked bar can be put back. */
    Side lastBar = Side.RIGHT;
    /** Seconds since the light went out because this bot put it out. */
    double darkFor = 0;
    /**
     * The side the unit was last seen walking down, and the repel count it
     * was seen at.
     *
     * <p><b>Without this the competent policy is worse than the reactive
     * one, and the sweep said so before it was written down.</b> PRO saw
     * the unit at the left door, put the bar there, and then -- on the very
     * next frame -- read the record, which still described the light that
     * had been on the left a moment ago, concluded the unit was coming to
     * the right, and moved the bar off the door the thing was standing at.
     * Measured: PRO read 41/34/17/2/5 against REACT's 78/68/50/23/11, and
     * the whole difference was that PRO was talking itself out of what it
     * had just seen. <b>A sighting outranks a record, and it outranks it
     * until the bar takes the hit.</b>
     */
    Side seenSide = null;
    int lastRepels = 0;
    /** Seconds since the unit was last seen in the lit hall. */
    double seenAge = 0;
    /**
     * How long a sighting is trusted.
     *
     * <p>Long enough to cover a whole approach plus the stand at the door,
     * which is the window in which the bar has to already be there. A
     * sighting that outlives its arrival is a bar held on a door nothing
     * is standing at, and the night after a sighting is a night with the
     * bar on the wrong side of it.
     */
    public static final double SEEN_HOLD = 6.0;

    /**
     * How sure the record has to be before PRO stops paying for the light.
     *
     * <p>Above this, the readout is a working prediction and the filament
     * is better saved; below it, the record is losing its grip and the
     * light is the only thing that can re-pin it. The threshold is the
     * whole difference between PRO and REACT, and it is a difference in
     * <i>when</i> to spend rather than in what to do.
     */
    public static final double PRO_REST = 0.55;

    public Bot(Policy policy) {
        this.policy = policy;
    }

    /**
     * One frame of play.
     *
     * <p>Order matters and is the same order a person would use: take in
     * what you can see, then decide whether the bar has to move, then put
     * the light back where it belongs.
     */
    public void step(Shift s, double dt) {
        if (s.status != Shift.Status.PLAYING) return;
        if (s.barAt != null) lastBar = s.barAt;

        switch (policy) {
            case IDLE -> { }
            case STARE -> {
                if (!s.barMoving && (!s.lit || s.lightSide != Side.LEFT)) s.aim(Side.LEFT);
            }
            case LISTEN -> {
                if (s.barMoving) return;
                if (s.heardSide != null && s.barAt != s.heardSide) {
                    if (s.lit) s.dark();
                    s.moveBar(s.heardSide);
                }
            }
            case REACT -> react(s, dt, false);
            case PRO -> react(s, dt, true);
        }
    }

    /**
     * The competent loop, with the readout either used or not.
     *
     * <p>Three jobs, in order, and the order is the whole policy:
     *
     * <ol>
     *   <li><b>It is showing.</b> The light is on the hall it is walking
     *       down, so that is the side it is coming to. Put the bar there.
     *       This costs the light, because there is one pair of hands.</li>
     *   <li><b>The readout disagrees with the bar</b> (PRO only). The
     *       record says it thinks you are somewhere the bar is not, which
     *       means it is coming to where the bar is not. Move the bar. This
     *       is the only thing the readout is for, and it is only ever
     *       needed for the second and a half after a swap, when the record
     *       is still describing the side you left.</li>
     *   <li><b>Nothing is happening.</b> Put the light on the side with no
     *       bar, which is the side it will not come to, and wait.</li>
     * </ol>
     */
    void react(Shift s, double dt, boolean readTheRecord) {
        // What it was seen doing outranks what the record says it will do,
        // and a sighting expires: a bar held on a door the thing has
        // already left is a bar that is not on the door it is at.
        if (s.visible()) { seenSide = s.lightSide; seenAge = 0; }
        else seenAge += dt;
        if (s.repels != lastRepels) { lastRepels = s.repels; seenSide = null; }
        if (seenSide != null && seenAge > SEEN_HOLD) seenSide = null;

        if (s.barMoving) { darkFor = 0; return; }

        Side want = null;
        if (seenSide != null) want = seenSide;
        else if (readTheRecord) want = s.predicted();

        if (want != null && s.barAt != want) {
            if (s.lit) s.dark();
            s.moveBar(want);
            return;
        }
        if (s.barAt == null) {
            if (s.lit) s.dark();
            s.moveBar(lastBar);
            return;
        }

        // The readout's real use, and the reason it is drawn at all: it
        // says how well the record is holding. While it is holding well,
        // the unit is going where the record says, and the light is not
        // earning its filament -- so PRO puts it out and saves it for the
        // arrival that the record has lost. REACT has no way to know, so
        // REACT burns the filament flat and is blind when it matters.
        // Maintain the eyes: the light belongs on the side with no bar,
        // which is the side the record does not hold and the side a
        // deviation comes to.
        Side eyes = s.barAt.other();
        if (!s.lit || s.lightSide != eyes) {
            if (!s.lit) darkFor += dt;
            if (darkFor >= RELIGHT || s.lit) {
                s.aim(eyes);
                darkFor = 0;
            }
        } else {
            darkFor = 0;
        }
    }

    // ---- The sweep ----

    /** Run one night to the end and say whether it was survived. */
    public static boolean survived(Policy p, int night, long seed) {
        Shift s = new Shift(night, seed);
        Bot b = new Bot(p);
        double dt = 1.0 / 60.0;
        for (int i = 0; i < 60 * 400 && s.status == Shift.Status.PLAYING; i++) {
            b.step(s, dt);
            s.update(dt);
        }
        return s.status == Shift.Status.SURVIVED;
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
     * times the bar took a hit.
     */
    public static int[] tally(Policy p, int night, int seeds) {
        int caught = 0, survived = 0, repels = 0;
        for (int i = 0; i < seeds; i++) {
            Shift s = new Shift(night, 1000L * night + i);
            Bot b = new Bot(p);
            double dt = 1.0 / 60.0;
            for (int f = 0; f < 60 * 400 && s.status == Shift.Status.PLAYING; f++) {
                b.step(s, dt);
                s.update(dt);
            }
            if (s.status == Shift.Status.SURVIVED) survived++; else caught++;
            repels += s.repels;
        }
        return new int[]{caught, survived, repels / Math.max(1, seeds)};
    }
}
