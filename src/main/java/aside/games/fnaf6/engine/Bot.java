package aside.games.fnaf6.engine;

/**
 * A player, as a policy, so the week can be measured instead of guessed.
 *
 * <p>Same instrument as FNAF 5's, and it exists for the same reason: a
 * balance table produced by playing twenty nights by hand is a table of
 * twenty nights, and a table produced by sweeping a bot over two hundred
 * seeds a night is a table of the game.
 *
 * <p>The bot is <b>not allowed to see anything the player cannot.</b> It
 * reads {@link Salvage#seen()} -- null unless the lamp is on and warm --
 * {@link Salvage#drags}, {@link Salvage#dragAge},
 * {@link Salvage#agitation} and {@link Salvage#shocks}. It does not read
 * {@code hostile} or {@code pose}. A bot that peeked would produce a table
 * about the bot.
 *
 * <h2>The one idea every playing policy is built on</h2>
 *
 * <p>A drag is free information: the unit moved, so the pose is one higher
 * than you thought. <b>Count the drags and you know the pose without ever
 * lighting the lamp.</b> That is the whole audio channel, and it is why
 * the lamp is a last resort rather than the main instrument.
 *
 * <p>What breaks the count is {@link Unit#silent}: a silent advance is an
 * advance nobody counted, so the count drifts <i>low</i>, and a low count
 * is a shock that arrives after the thing is already standing up. The only
 * correction is to look -- which is what the unit is waiting for. So the
 * night is not "can you hear it", it is <b>"how often do you have to
 * check your arithmetic, and can you afford it".</b>
 *
 * <pre>
 *   IDLE     never looks, never shocks.          The control.
 *   LAMP_ON  leaves the lamp on.                 Dies of agitation, always.
 *   PANIC    shocks at five seconds.             Wins a hostile night by luck.
 *   LISTEN   counts drags, checks every 8s.      Right idea, no restraint.
 *   PATROL   never counts, looks every 16s.      Ignores the audio channel.
 *   PRO      counts drags, checks every 18s,
 *            and stops when the lamp is dear.    The competent player.
 * </pre>
 *
 * <p><b>PRO is the policy the ladder is read against</b>, and the reason
 * is the same lesson FNAF 5 learned twice: a check written against "the
 * competent player" is really written against an assumption about which
 * policy that is, and the assumption is the part that goes stale. PRO is
 * the only policy here that uses both channels and rations the lamp, so it
 * is the only one whose number means anything about the game.
 */
public final class Bot {

    public enum Policy { IDLE, LAMP_ON, PANIC, LISTEN, PATROL, PRO }

    /** How long one deliberate look lasts, once started. */
    public static final double LOOK = 1.20;
    /**
     * How long LISTEN holds the lamp.
     *
     * <p>A person who looks because they <i>heard something</i> stares. A
     * person who looks because it is time to check glances. That is the
     * whole difference between the two policies and it is the reason
     * LISTEN is not simply PRO minus a hedge: it spends nearly twice the
     * lamp per look, so the same night that PRO plays on a budget is a
     * night LISTEN runs out on.
     */
    public static final double LOOK_STARE = 1.60;

    /** How long after a drag LISTEN will still look at it. */
    public static final double LISTEN_WINDOW = 0.35;
    /** How often PATROL looks. It never counts at all. */
    public static final double PATROL_EVERY = 24.0;
    /** How often PRO hedges with a look it did not count its way into. */
    public static final double PRO_EVERY = 18.0;
    /** How often PRO hedges once agitation is high. */
    public static final double PRO_SLOW = 26.0;
    /**
     * How often PRO looks once it has checked twice and nothing moved.
     *
     * <p><b>This is the competent player's actual skill, and without it
     * the ladder is upside down.</b> A dead unit and a hostile one look
     * identical for the first ten seconds, so the only way to tell them
     * apart is to keep looking -- and looking is what kills you. What a
     * person does about that is not clever: they check twice, see nothing,
     * and stop checking so often. Measured before this rule existed, PRO
     * died of agitation on <i>every</i> night five seed including the half
     * with nothing in the chair, because a fixed clock spends the same
     * lamp on a night that did not need it. The rule is the whole reason
     * a quiet night is survivable at all.
     */
    public static final double PRO_IDLE = 40.0;
    /** Consecutive looks at the same pose before PRO calls it dead. */
    public static final int SAME_BEFORE_IDLE = 2;
    /** Agitation above which PRO stretches its schedule. */
    public static final double RESTRAINT = 0.60;
    /** Agitation above which PRO stops looking altogether. */
    public static final double HOLD_OFF = 0.92;

    public final Policy policy;

    /** The last value of {@link Salvage#drags} this bot saw. */
    int lastDrags = 0;
    /** Drags counted since the last correction. */
    int count = 0;
    /** Seconds the current look has been running. */
    double lookFor = 0;
    /** Seconds since the last look ended. */
    double sinceLook = 0;
    /**
     * The count value the last count-triggered look was taken at.
     *
     * <p><b>Without this the bot burns the whole lamp in four seconds, and
     * the sweep is what caught it.</b> A count-triggered look fires when
     * the count reaches {@code SHOCK_MIN - 1} and stays there -- the count
     * only changes when something is heard -- so a rule gated on "has it
     * been a second since I looked" fires again the moment the look ends,
     * and again, and again. Measured on night 1: nine looks in fifty-four
     * seconds, agitation 1.00, and the unit came off the chair without
     * ever advancing to it. <b>A trigger that is a level, not an edge,
     * fires forever.</b>
     */
    int countLooked = -1;
    /** The pose the last look revealed, or -1. */
    int lastSeen = -1;
    /** How many looks in a row have shown that same pose. */
    int sameSeen = 0;
    /** True while the lamp is on because this bot turned it on. */
    boolean looking = false;

    public Bot(Policy policy) {
        this.policy = policy;
    }

    /**
     * One frame of play.
     *
     * <p>Order matters and is the same order a person would use: take in
     * what you heard, spend the shock if it is due, then decide whether to
     * look.
     */
    public void step(Salvage s, double dt) {
        if (s.status != Salvage.Status.PLAYING) return;

        // 1. What you heard. A drag is an advance you did not have to pay
        //    for, and it is the only free information in the building.
        if (s.drags > lastDrags) {
            count += s.drags - lastDrags;
            lastDrags = s.drags;
        }

        // 2. The shock. It only reaches a unit that is up, so it is only
        //    ever worth spending on one you can see is up. A count is not
        //    a sighting: the count drifts low on a silent step, and a
        //    shock spent on a count is a shock spent on a guess.
        if (s.revealed() && s.pose >= Salvage.SHOCK_MIN) {
            s.shock();
            return;
        }

        switch (policy) {
            case IDLE -> { }
            case LAMP_ON -> { if (!s.lit) s.toggleLamp(); }
            case PANIC -> { if (s.time > 5.0 && s.shocks > 0) s.shock(); }
            case LISTEN, PATROL, PRO -> drive(s, dt);
        }
    }

    /**
     * The look schedule.
     *
     * <p>Two things can start a look, and the difference between the
     * policies is which of them they listen to:
     *
     * <ul>
     *   <li><b>the count is one short.</b> You think the pose is
     *       {@code SHOCK_MIN - 1}, so the next drag puts it in the window
     *       and the next drag is the one you must not miss. This is the
     *       check that matters, and it is why a counting player spends
     *       almost no lamp on a loud unit.</li>
     *   <li><b>the clock.</b> You have not looked in a while, so your
     *       count may have drifted on a silent step. This is the check
     *       that costs, and it is the price of a quiet unit.</li>
     * </ul>
     */
    void drive(Salvage s, double dt) {
        if (s.lit) {
            lookFor += dt;
            double hold = policy == Policy.LISTEN ? LOOK_STARE : LOOK;
            if (lookFor >= hold) {
                s.toggleLamp();
                lookFor = 0;
                sinceLook = 0;
                looking = false;
            }
            return;
        }

        sinceLook += dt;
        lookFor = 0;
        looking = false;

        boolean start;
        switch (policy) {
            // LISTEN has heard that a drag is information and has not
            // worked out that the lamp is what the thing in the chair is
            // waiting for. It looks at whatever it just heard, every time,
            // with no clock and no rationing. On a loud unit that is
            // nearly as good as PRO; on a quiet one it is looking at
            // nothing, and it has no second channel to fall back on.
            case LISTEN -> start = s.dragAge <= LISTEN_WINDOW && sinceLook >= 1.0;

            // PATROL has a plan and no ears. It looks on a clock, which
            // means it never spends a look on a drag it could have had for
            // free -- and never catches a window that opened between two
            // ticks of its own clock.
            case PATROL -> start = sinceLook >= PATROL_EVERY;

            // PRO does both, and rations. It looks when its count says the
            // window is next, and it hedges on a clock because a silent
            // step is a step nobody counted. It stretches the clock when
            // the lamp has cost a lot and stops when the next look could
            // be the one that wakes the thing up.
            default -> {
                if (s.agitation >= HOLD_OFF) { start = false; break; }
                // 1. A drag is the one moment worth spending a look on: the
                //    pose has just changed, and if it changed into the
                //    window this is the look that ends the night. This is
                //    the same rule LISTEN plays, and it is the reason the
                //    audio channel is worth having.
                boolean dragDue = s.dragAge <= LISTEN_WINDOW && sinceLook >= 1.0;

                // 2. And a hedge, for the nights the audio channel is a
                //    liar. It only fires when the count is still short of
                //    the window AND it has been quiet for most of a look
                //    interval -- which is exactly the state a person calls
                //    "I have not heard it move in a while, and it is
                //    either dead or it is moving without me". A loud unit
                //    never reaches this branch, because its drags keep the
                //    count climbing and the room noisy.
                double every = sameSeen >= SAME_BEFORE_IDLE ? PRO_IDLE
                        : s.agitation > RESTRAINT ? PRO_SLOW : PRO_EVERY;
                boolean hedgeDue = count < Salvage.SHOCK_MIN - 1
                        && s.dragAge >= every * 0.6
                        && sinceLook >= every;
                start = dragDue || hedgeDue;
            }
        }

        if (start) {
            s.toggleLamp();
            lookFor = 0;
            looking = true;
        }
    }

    /**
     * Called by the driver when the lamp has been on long enough to see.
     *
     * <p>Not used by {@link #step} -- the correction happens in
     * {@link #correct}, which the sweep calls after every frame so the
     * count is fixed the moment the pose is legible.
     */
    void correct(Salvage s) {
        if (!s.revealed()) return;
        // The lamp is the ground truth. Whatever you counted, this is what
        // it is, and the count restarts from what you can see.
        count = s.pose;
        // And it is the only evidence there is about whether anything is
        // in the chair at all. Two looks at the same pose is a person's
        // whole reason for relaxing.
        if (s.pose == lastSeen) sameSeen++;
        else { sameSeen = 0; lastSeen = s.pose; }
    }

    // ---- The sweep ----

    /** What one run of one policy on one night came to. */
    public record Run(Salvage.Status status, boolean hostile, double agitation,
                      int looks, int noises, int drags, int silentSteps) {}

    /** Run one policy on one night with one seed, to the end of the night. */
    public static Run play(Policy policy, int night, long seed) {
        Salvage s = new Salvage(night, seed);
        Bot b = new Bot(policy);
        double dt = 1.0 / 60.0;
        // A hard stop well past the night, so a policy that somehow never
        // ends cannot hang the sweep.
        for (int i = 0; i < 60 * 400 && s.status == Salvage.Status.PLAYING; i++) {
            s.update(dt);
            b.correct(s);
            b.step(s, dt);
        }
        return new Run(s.status, s.hostile, s.agitation, s.looks,
                s.noises, s.drags, s.silentSteps);
    }

    /**
     * Survival rate for a policy on a night, over {@code seeds} runs.
     *
     * <p>"Survived" is not the only good outcome:
     * {@link Salvage.Status#DESTROYED} is a salvage carried out correctly,
     * and both end the night with the player alive. Both are counted here,
     * because the question the sweep asks is whether the player got to
     * six.
     */
    public static double survival(Policy policy, int night, int seeds) {
        int ok = 0;
        for (int i = 0; i < seeds; i++) {
            Run r = play(policy, night, 1000L * night + i);
            if (r.status() == Salvage.Status.SURVIVED
                    || r.status() == Salvage.Status.DESTROYED) ok++;
        }
        return (double) ok / seeds;
    }

    /** Survival over the whole week. */
    public static double week(Policy policy, int seeds) {
        double sum = 0;
        for (int n = 1; n <= 5; n++) sum += survival(policy, n, seeds);
        return sum / 5.0;
    }

    /** How a policy's nights ended on a night: {lunged, survived, destroyed}. */
    public static int[] killers(Policy policy, int night, int seeds) {
        int lunged = 0, survived = 0, destroyed = 0;
        for (int i = 0; i < seeds; i++) {
            Run r = play(policy, night, 1000L * night + i);
            switch (r.status()) {
                case LUNGED -> lunged++;
                case SURVIVED -> survived++;
                case DESTROYED -> destroyed++;
                default -> { }
            }
        }
        return new int[]{lunged, survived, destroyed};
    }
}
