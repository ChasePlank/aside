package aside.games.fnaf9.engine;

import aside.games.fnaf9.engine.Feed.Side;

/**
 * A player, as a policy, so the week can be measured instead of guessed.
 *
 * <p>Same instrument as FNAF 5's, 6's, 7's and 8's, and it exists for the same
 * reason: a balance table produced by playing twenty nights by hand is a table
 * of twenty nights, and a table produced by sweeping a bot over two hundred
 * seeds a night is a table of the game.
 *
 * <p><b>The bot is not allowed to see anything the player cannot.</b> It reads
 * {@link Feed#shown}, {@link Feed#atDoor}, {@link Feed#feed},
 * {@link Feed#holdLeft} and {@link Feed#circuit}. It does <b>not</b> read
 * {@code d}, {@code timer} or {@code waited}. That rule is what makes the
 * number mean something, and in this game it costs the bot the thing the whole
 * night is about: <b>the monitor it is reading is a picture of the past, and
 * the only way it can ask how far in the past is to read the age off the band,
 * which is exactly what a player does.</b>
 *
 * <h2>Dead reckoning, which is the whole of this game's skill</h2>
 *
 * <p>{@link #est} is the bot's belief about a distance, and it is advanced the
 * way a person's would be: it is <b>set</b> when the monitor draws the hall or
 * the light shows the doorway, and otherwise it is <b>walked forward</b> at
 * the mean rate the night's tables say. The real thing is jittered
 * ({@link Feed#JITTER}), so the belief drifts, and the drift is the reason a
 * look is worth spending on a hall that is not currently a problem.
 *
 * <p>The difference between the policies is <b>where the circuit is and when
 * it moves</b>, and that is the only thing this game has to get right:
 *
 * <pre>
 *   IDLE     does nothing.                          The control.
 *   STARE    the monitor, on the left hall, forever. The trap: the picture
 *                                                    empties the hall.
 *   EARLY    shuts the door the moment anything is    The over-cautious
 *            within two steps.                        player.
 *   GLANCE   a fixed rhythm: look, rest, look.       A player with a habit.
 *   PRO      look when an arrival is due, shut when  The competent player.
 *            it is, and be dark the rest of the time.
 *   SIEGE    PRO, but the hold is sized to cover    The player who holds
 *            the OTHER hall as well whenever that   through both of them.
 *            hall is within {@link #SIEGE_AT}.
 *   SENSE    PRO, but the door is let go the moment The player who trusts
 *            the contact sensor says the doorway is  the door's own sensor.
 *            empty, instead of when the belief says
 *            the walker must have lost interest.
 * </pre>
 *
 * <p><b>SIEGE is on the ladder because it beats PRO, and that is an open
 * design item rather than a compliment.</b> Measured at 200 seeds a night it
 * reads 89/99/99/99/66 against PRO's 86/74/62/48/35 -- three nights out of
 * five at 99%, and a week of 90% against 61%. The reason is in the death
 * trace: <b>every death PRO takes happens while the door is down, within
 * 0.8s of a release</b> (measured, 418 deaths over 200 seeds a night, all of
 * them in {@link Feed.Circuit#HOLD} and all of them inside a second of
 * letting go). PRO lets go the moment the doorway empties, the other walker
 * arrives inside the door's {@link Feed#SHUT_TIME}, and the door is still on
 * its way down. SIEGE does not have that failure because it does not let go
 * until both halls are clear -- so the night rewards <i>holding longer</i>,
 * and the door's duty cycle is not tight enough to stop it. See
 * {@link #SIEGE_AT} for what has been tried against it.
 *
 * <p><b>PRO is the policy the ladder is read against</b>, for the reason FNAF
 * 5 learned twice and FNAF 7 and 8 learned once each: a check written against
 * "the competent player" is really written against an assumption about which
 * policy that is, and the assumption is the part that goes stale.
 */
public final class Bot {

    public enum Policy { IDLE, STARE, EARLY, GLANCE, PRO, SIEGE, SENSE }

    /**
     * How much warning the bot needs before it will shut the door, in seconds.
     *
     * <p>Set by the door's travel rather than by taste: the door takes
     * {@link Feed#SHUT_TIME} to come down and stops nothing until it is down,
     * so the hold has to start that far ahead of the soonest the walker could
     * arrive, plus a margin for the belief being wrong. It cannot start much
     * further ahead either, because the mechanism will only stay shut for
     * {@link Feed#HOLD_MAX} seconds and a hold that starts a full step out
     * spends two and a half of those on an empty doorway. <b>Shutting the door
     * early is the same mistake as shutting it late, and it is the one the
     * duty cycle exists to create.</b>
     */
    public static final double SHUT_LEAD = Feed.SHUT_TIME + 0.45;

    /**
     * How close the belief has to be before the bot will spend a look on it.
     *
     * <p>The whole economy in one number. Watching a hall that is four steps
     * out is watching a hall that will still be there in ten seconds, and the
     * feed it costs is feed that will not be there when it matters.
     */
    public static final double WATCH_AT = 2.60;

    /**
     * The oldest the feed is allowed to get while the bot is looking.
     *
     * <p>Past this the bot goes dark and lets the picture catch up, which is
     * the behaviour the whole game is asking for. It is a hard rule rather
     * than a preference because the alternative -- look until you have seen
     * what you want -- is the trap, and a bot that fell into it would make the
     * ladder read as though the game were unwinnable.
     */
    public static final double LOOK_MAX = 0.55;

    /** Extra seconds the bot keeps the door shut past the walker's patience. */
    public static final double HOLD_SLACK = 0.30;

    /** Seconds GLANCE watches for. */
    public static final double GLANCE_ON = 0.50;
    /** Seconds GLANCE rests for. */
    public static final double GLANCE_OFF = 1.00;

    public final Policy policy;

    /** The bot's belief about each hall's distance from the doorway. */
    public final double[] est = new double[]{Feed.MAX, Feed.MAX};
    /** Seconds since each hall was last actually read. */
    public final double[] age = new double[]{99, 99};
    /** True while the bot is holding the door shut. */
    public boolean holding = false;
    /** Which hall the current hold was started for. */
    public Side holdSide = Side.LEFT;
    /** Seconds the current hold has lasted. */
    public double holdFor = 0;
    /** True once the contact sensor has read something against the door. */
    public boolean sawOccupied = false;
    /**
     * Seconds the current hold has to last.
     *
     * <p>Worked out when the hold starts, and it is <b>the walker's walk plus
     * the walker's patience</b> rather than just the patience. The first
     * version of this held for {@code patience} from the moment it pressed,
     * which is wrong by however far away the walker was when it pressed -- and
     * since it presses about a step out, that is two and a half seconds of the
     * hold spent on an empty doorway and two and a half seconds of the
     * walker's patience left unserved. Every policy on the ladder died on
     * every seed of every night with it, and the sweep said so in one line:
     * five policies, five zeros.
     */
    public double holdNeed = 0;
    /** Seconds into the current look, for GLANCE's rhythm. */
    public double lookFor = 0;
    /** Which hall GLANCE is on, for its alternation. */
    public Side glanceSide = Side.LEFT;
    /**
     * True when this policy sizes its hold for both halls.
     *
     * <p>Set from the policy rather than passed in, so there is one place that
     * knows which policies are the extended hold.
     */
    public final boolean extendHold;

    /**
     * True when this policy lets go on the contact sensor rather than on the
     * belief. Set from the policy, like {@link #extendHold}.
     */
    public final boolean sensorRelease;

    public Bot(Policy policy) {
        this.policy = policy;
        this.extendHold = policy == Policy.SIEGE;
        this.sensorRelease = policy == Policy.SENSE;
    }

    /**
     * One frame of play.
     *
     * <p>Order matters and is the same order a person would use: take in what
     * the office is showing, then decide, then act.
     */
    public void step(Feed m, double dt) {
        if (m.status != Feed.Status.PLAYING) return;
        observe(m, dt);
        switch (policy) {
            case IDLE -> { }
            case STARE -> stare(m);
            case EARLY -> early(m, dt);
            case GLANCE -> glance(m, dt);
            case PRO, SIEGE, SENSE -> play(m, dt);
        }
    }

    /**
     * Keep the belief up to date.
     *
     * <p>Three ways to learn something, and they are not equal. The monitor
     * <b>sets</b> the belief to whatever the picture says, which is a fact
     * about {@code feed} seconds ago. The light <b>proves</b> the doorway is
     * occupied or empty, which is a fact about now and is worth more than the
     * picture -- so it is applied after the dead reckoning rather than instead
     * of it. And when neither is available the belief is walked forward at the
     * mean rate, which is where the drift comes from.
     */
    void observe(Feed m, double dt) {
        for (Side s : Side.values()) {
            int i = s.ordinal();
            double shown = m.shown(s);
            if (shown >= 0) {
                // The picture is `feed` seconds old, so the walker is further
                // along than it is drawn. Correcting for that is the one piece
                // of arithmetic this game asks a player to do, and the band
                // prints the number it needs -- which is why the band prints
                // it. Without the correction the belief is pinned about half a
                // step behind reality on every night, and a bot that never
                // believes a walker is at the door never shuts it: measured,
                // PRO scored zero on every seed of every night with the belief
                // reading 0.99 while the walker stood in the doorway.
                est[i] = Math.max(0, shown - m.feed / m.interval(s));
                age[i] = 0;
            } else {
                age[i] += dt;
                est[i] = Math.max(0, est[i] - dt / m.interval(s));
            }
        }
    }

    /** The hall the bot believes is closest to the doorway. */
    Side urgent() {
        return est[0] <= est[1] ? Side.LEFT : Side.RIGHT;
    }

    /**
     * The soonest this hall's walker could possibly be at the door, in
     * seconds.
     *
     * <p>Which is just the belief times the mean interval, and it can be that
     * simple only because the walkers move continuously. While they moved in
     * whole steps this number had to be the <i>pessimistic</i> end of a range
     * -- a walker drawn one step out might be one step out or a hundredth of
     * one -- and taking the pessimistic end made the bot shut the door on
     * every frame, because the pessimistic end of "one step out" is "at the
     * door". Measured, that scored worse than doing nothing. Smooth motion
     * removes the range: the belief is the belief, and the only error left in
     * it is the age of the picture, which {@link #observe} has already
     * subtracted.
     */
    double soonest(Feed m, Side s) {
        return Math.max(0, est[s.ordinal()] * m.interval(s));
    }

    /**
     * How long a hold has to last, given where the walker was believed to be.
     *
     * <p>The walk, plus the patience, plus a margin, capped by what the
     * mechanism will actually give. The walk is the belief times the mean
     * interval, which is the same dead reckoning the rest of the policy runs
     * on -- so a bot that shuts the door on a stale belief shuts it for the
     * wrong length of time, exactly as a person would.
     */
    double holdNeeded(Feed m, Side s) {
        double need = est[s.ordinal()] * m.interval(s) + Feed.SHUT_TIME
                + m.pair.unit(s).patience() + HOLD_SLACK;
        if (extendHold) {
            Side o = s.other();
            if (est[o.ordinal()] <= SIEGE_AT) {
                need = Math.max(need, est[o.ordinal()] * m.interval(o)
                        + Feed.SHUT_TIME + m.pair.unit(o).patience() + HOLD_SLACK);
            }
        }
        return Math.min(Feed.HOLD_MAX - 0.15, need);
    }

    /**
     * How close the other hall has to be before SIEGE holds through it too.
     *
     * <p>2.5 steps, and it is a reading rather than a preference: at 2.0 the
     * extension almost never fires and SIEGE reads like PRO (73% week), at
     * 3.0 it fires too late to help and night one collapses (74% week, night
     * one at 1%), and 2.5 is the band where it works (90% week). Measured at
     * 200 seeds a night, 2.5 reads 89/99/99/99/66.
     *
     * <p><b>What has been tried against it, all measured, none of it enough:</b>
     *
     * <ul>
     *   <li><b>{@link Feed#REARM} is inert.</b> 0.35, 0.20, 0.10 and 0.00 give
     *       byte-identical ladders for every policy, because it only gates a
     *       re-hold after a jam and no policy on the ladder jams.</li>
     *   <li><b>{@link Feed#COOL} cannot separate them.</b> 2.2 -> PRO 60% /
     *       SIEGE 90%; 1.8 -> 55% / 79%; 1.5 -> 51% / 74%; 1.2 -> 26% / 36%.
     *       The gap narrows and never closes, and the week stops ramping first
     *       (night five is the first to go, 33% -> 8% at 1.8).</li>
     *   <li><b>Jittering the walker's patience changes nothing.</b> Drawing a
     *       fresh patience per visit at +/-10%, 20%, 30%, 40% and 50% leaves
     *       SIEGE between 87% and 92% and PRO between 59% and 64%. It cannot
     *       help: SIEGE's hold is already pinned at the mechanism's ceiling
     *       ({@link Feed#HOLD_MAX} less the 0.15 margin), so there is no
     *       length for the jitter to take away.</li>
     *   <li><b>Charging the door for what is leaning on it is far too strong.</b>
     *       Extra heat while a walker is against a shut door, at 0.5 per
     *       second, takes PRO from 60% to 11% and SIEGE from 90% to 23%. It
     *       punishes the recovery -- which is the thing PRO needs -- rather
     *       than the length of the hold.</li>
     *   <li><b>Charging the hold superlinearly is the right shape and needs a
     *       re-balance.</b> Heat at {@code 1 + k * held / HOLD_MAX} per second
     *       at k = 0.5 brings the two together (PRO 49%, SIEGE 55%) and at
     *       k = 1.0 puts both on the floor (14% / 16%). But it punishes long
     *       <i>patience</i>, which is night one's whole cast, so the ramp
     *       inverts: PRO reads 49/76/62/43/14. That is a re-tune of the
     *       patience table and a re-sweep, not a constant.</li>
     *   <li><b>Shortening the door's travel makes the game trivial rather than
     *       closing the gap.</b> {@link Feed#SHUT_TIME} 0.80 -> PRO 61% /
     *       SIEGE 91%; 0.70 -> 86% / 95%; 0.55 -> 95% / 98%; 0.40 -> 100% /
     *       99%. PRO climbs and SIEGE stays on top of it, and the week stops
     *       ramping on the way (PRO's night five goes 35% -> 78% -> 84% ->
     *       100%), so the travel is what makes the night a night and it cannot
     *       be spent on this. Seventh lever tried, seventh that does not close
     *       it -- and the only one whose failure mode is losing the game
     *       rather than losing the ramp, which is why {@code SelfTest} now
     *       asserts that no policy wins the week.</li>
     *   <li><b>Charging the door for the time it spends on an EMPTY doorway --
     *       the one cost that depends on the walker's state rather than on a
     *       rate -- is a cliff as well.</b> Heat at {@code 1 + k} per second
     *       while nothing is against the door and {@code 1} while something
     *       is, so that the hold SIEGE buys its win with is the hold it pays
     *       for: k = 0.5 -> PRO 12% / SIEGE 21%; k = 1.0 -> 0% / 0%; k = 1.5
     *       -> 0% / 0%. It is the eighth lever and the eighth that does not
     *       close the gap, and it is the most interesting of the eight,
     *       because it is the shape the diagnosis below says the fix has to
     *       have -- <i>a cost whose value depends on the walker's state rather
     *       than on how much of it the player applies</i> -- and it still
     *       comes back a threshold. <b>A state-dependent cost is not enough on
     *       its own. The player still has no lever that changes the walkers,
     *       and without one there is nothing for a gradient to be made
     *       of.</b></li>
     * </ul>
     *
     * <p>So the honest state of this game is: <b>the night rewards holding
     * longer than the competent policy holds, and no single constant fixes
     * it.</b> The suite asserts the relationship rather than pretending it is
     * not there, so that a future fire that does fix it is told it has.
     *
     * <p><b>And the shape of that failure is worth naming, because a second
     * game was built on it five times and lost every time.</b> Every one of
     * the eight levers above is a cost that only binds when <i>both</i> halls
     * are busy -- the door is expensive exactly when it is covering two
     * arrivals -- and a cost of that shape is a <b>threshold rather than a
     * gradient</b>: it either sustains the joint hold or it does not, and
     * there is no middle.
     *
     * <p>The reason there is no middle is worth stating carefully, because the
     * obvious version of it is wrong. It is <i>not</i> that the player has no
     * lever -- three of the five tenth-game builds gave them one and failed
     * anyway. <b>It is that the lever is discrete and the walker's state is
     * poor.</b> A lever with two settings is a threshold however you point it:
     * the third build's lever (a door a walker will not come to) was a
     * <i>rate</i>, so more of it was always better and a fixed cadence played
     * it; the fourth and fifth flipped the lever's sign with the walker's
     * state, which is the shape this paragraph originally asked for, and they
     * were still binary -- the competent policy read 100% and every other
     * policy read 0%, because "in the hall" and "at the door" is not enough
     * state for a decision to have a middle. <b>What this office has that they
     * did not is a lever with a continuum in it: the feed is a number the
     * player spends a little or a lot of, and that is why this game has a
     * difficulty table at all.</b>
     *
     * <p>So the rule for a tenth game is sharper than "ask what the player can
     * do about it": <b>ask what the player can do about it, in how many
     * gradations, and whether the thing they are doing it to has enough states
     * to be wrong in.</b> See [[reference/fnaf10.md]] in memory for all eight
     * builds and their measurements.
     *
     * <p><b>And the eighth attempt found the thing that makes a timing lever
     * work, which is worth having here because it is the first positive result
     * out of eight.</b> Every earlier design was playable by a fixed cadence,
     * because the lever's effect was an <i>integral over time</i> -- a door
     * that stops a walker, a dimmer that wears one down -- and a cadence gets
     * an average right. The eighth made the lever <b>sampled</b>: the walker
     * steps on its own clock and reads the brightness <i>at the instant of the
     * step</i>, advancing {@code (1 - brightness)} of a step and nothing else.
     * Nothing accumulates between steps, so a second of full light between two
     * of them is worth exactly nothing.
     *
     * <p>Measured: <b>the rhythm dies.</b> RHYTHM -- full and nothing on a
     * cadence -- reads 1% over the week against the competent policy's 80%,
     * where in every previous design the habit beat or matched the competent
     * player. That is the first time a cadence has lost, and it says the
     * sampling is the load-bearing part.
     *
     * <p><b>It is still not a game, and the reason is specific.</b> The
     * competent strategy -- spend light only on the steps -- is <i>strictly
     * dominant</i>, because light spent that way costs almost nothing: the
     * filament is on for about a fifth of the night, so it never runs out, and
     * every other policy reads 0%. The window width is a real dial (margins of
     * 0.05, 0.20, 0.60 and 1.20 seconds read 77%, 80%, 60% and 62% over the
     * week) but the <i>strategies</i> are 0 or 80, which is a cliff rather than
     * a ladder. <b>So the next attempt needs a cost that a perfect step-timer
     * cannot avoid</b> -- the sampling is solved, the economy is not.
     *
     * <p><b>The ninth attempt tried to supply that cost by giving the dimmer
     * inertia, and it is half a result.</b> The brightness now moves toward
     * the setting at a fixed rate rather than jumping to it, so the light
     * cannot be flicked on for the instant of a step: a rise takes
     * {@code 1 / TRAVEL} seconds, a fall takes the same, and the filament
     * burns for all of it. That is a real cost -- the competent policy's week
     * falls from 80% to 60% at a quarter-second rise and to 23% at a
     * six-tenths-second one -- and the stride sweep shows a window where the
     * policies finally <i>separate</i> (at a stride of 0.25: competent 60%,
     * LOCK 19%, RHYTHM 4%).
     *
     * <p><b>But it is not a ladder, because the player still has nothing to
     * ration.</b> Every step is worth the same, so blocking every step the
     * filament allows is still strictly the best play, and the only question
     * is whether the budget lasts. Making the walker <i>impatient</i> -- its
     * next step growing with how long it was held -- was tried and makes the
     * game harder without creating a choice: the competent policy falls and
     * every other policy falls to zero, which is a cliff with a lower top.
     * <b>What a tenth game needs is not a cost but a reason for some steps to
     * matter more than others.</b>
     *
     * <p><b>And one latent bug worth recording, because it only bites when the
     * lever is not instant.</b> The order guard was
     * {@code if (same setting && !acted) return false;} -- which is inert while
     * the brightness jumps to the setting, and fatal the moment it does not: a
     * policy that re-orders the same setting every frame resets {@code acted}
     * every frame, the dimmer is never allowed to move, and <b>every policy
     * reads 0%</b>. It cost a sweep to find. Any future design with a lever
     * that takes time to move needs the guard to be
     * {@code if (same setting) return false;}
     *
     * <p><b>And SENSE is on the ladder because the office's own description
     * of itself does not survive being played.</b> {@link Feed}'s javadoc says
     * the sensor "is the only way to know the doorway has emptied, which is
     * the only way to know when it is safe to let go of the door and go back
     * to looking". A policy that does exactly that -- hold until the sensor
     * has read something against the door and then gone clear -- reads
     * <b>92/80/0/0/0, a week of 34%</b> against PRO's 86/74/62/48/35. It
     * <i>beats</i> PRO on the two nights whose walkers hold together on a bad
     * picture, and it <b>loses every seed of nights three, four and five</b>.
     * That is the whole finding in one row: the sensor says the doorway is
     * empty and says nothing about the hall behind it, so on a night where
     * the two halls come apart a release into the hall nobody has looked at
     * is the move that kills, and it kills every time. What actually decides
     * the release is the belief, and the belief can decide it because the
     * walker's patience is a constant. <b>That is the same defect as the
     * extended hold seen from the other end: the sensor is decorative, and
     * the fix is to make the walker's departure something a player cannot
     * compute.</b>
     */
    public static final double SIEGE_AT = 2.50;

    /** Shut the door, and work out how long for. */
    void beginHold(Feed m, Side s) {
        m.hold();
        holding = true;
        sawOccupied = false;
        holdSide = s;
        holdFor = 0;
        holdNeed = holdNeeded(m, s);
    }

    /**
     * Keep the door shut, or let go when the walker must have lost interest.
     *
     * <p>It also lets go the moment the mechanism is about to give out, and
     * that is not a nicety: a jam drops the circuit to {@link
     * Feed.Circuit#DARK} on its own, so a bot that held past {@link
     * Feed#HOLD_MAX} would be a bot that had its door opened by the building
     * rather than by its own decision. Letting go first means the policy is
     * always the thing that chose.
     */
    boolean keepHolding(Feed m, double dt) {
        if (!holding) return false;
        holdFor += dt;
        int i = holdSide.ordinal();

        // The sensor, and the whole reason it exists: the doorway is empty and
        // the door is down, so there is nothing left to hold it for. This is
        // the one thing in the night the bot knows for certain and knows now.
        //
        // It is not enough on its own, and the first version of this released
        // on the sensor alone -- which meant letting go the moment the door
        // finished coming down, because the walker it was shut for had not
        // arrived yet and the doorway was genuinely empty. Measured, that is
        // what killed PRO on night one: hold at 28.0, door down and doorway
        // empty at 29.0, release, walker arrives at 30.2 into an open door.
        // <b>The sensor says the doorway is empty. It does not say nothing is
        // coming.</b> So the hold has to run its length as well.
        // SENSE: the office says the sensor is the only way to know the
        // doorway has emptied, so this policy waits for it to say so. It has
        // to have SEEN something first -- otherwise the door is let go the
        // moment it finishes coming down, because the walker it was shut for
        // has not arrived yet -- and the hold's own length is the timeout for
        // a press that turns out to have been wrong.
        if (sensorRelease) {
            if (m.sensor()) sawOccupied = true;
            if (sawOccupied && m.clear()) {
                holding = false;
                m.watch(holdSide);
                est[i] = Feed.MAX;
                age[i] = 99;
                return true;
            }
        }

        if (!sensorRelease && m.clear() && holdFor >= holdNeed) {
            holding = false;
            // Let go <b>and look</b>, rather than letting go into the dark.
            // The sensor proves the doorway is empty; it says nothing about
            // the hall behind it, and the first version of this released to
            // DARK and died to a walker that was one step out at the moment it
            // let go. Letting go is not the end of a decision, it is the start
            // of the next one.
            m.watch(holdSide);
            est[i] = Feed.MAX;
            age[i] = 99;
            return true;
        }

        // The mechanism is about to give out. Let go first, so the policy is
        // always the thing that chose -- and mark the belief unknown, because
        // whether the walker is still there is now a genuine question.
        if (m.holdLeft() <= 0.02) {
            holding = false;
            m.dark();
            age[i] = 99;
            return true;
        }
        return true;
    }

    /**
     * The trap, and the control the ladder is read against.
     *
     * <p>Keep the monitor on the left hall, forever, and answer what the
     * picture shows. It is what the game looks like it wants you to do -- the
     * monitor is the only thing that can see a hall, so watch one -- and it is
     * wrong for the reason the whole night exists: <b>the picture it is
     * answering is not of now.</b> After three seconds the walkers are not
     * drawn on it at all, so the bot spends the night looking at an empty hall
     * and dies to a room it has been staring at.
     */
    void stare(Feed m) {
        if (holding) return;
        double shown = m.shown(Side.LEFT);
        if (shown >= 0 && shown <= 1) {
            beginHold(m, Side.LEFT);
            return;
        }
        m.watch(Side.LEFT);
    }

    /**
     * The over-cautious player: shut the door the moment anything is within
     * two steps.
     *
     * <p>This is the play that <i>feels</i> safest and it is the one the duty
     * cycle exists to punish. {@link #holdNeeded} sizes the hold by where the
     * walker is, so shutting the door two steps out asks the mechanism for
     * about <b>7.9 of its {@link Feed#HOLD_MAX} seconds</b> -- and the walker
     * only needs four and a half of them, so three are spent with the door
     * down on an empty doorway. The door then runs hot, and the arrival after
     * it is the one that gets in. <b>The mistake is not being too slow. It is
     * being too early, and paying for it out of the next decision.</b>
     *
     * <p><b>It scores zero on all five nights, and that is the answer rather
     * than a bug.</b> The previous fire left it open as "meant to be a
     * plausible bad policy, not a non-starter", and the suspicion was that its
     * rhythm left the belief stale -- the same failure as {@link #stare}. Both
     * halves of that were tested and neither is the cause. Given a competent
     * looking loop (watch the staler hall, go dark past {@link #LOOK_MAX}) and
     * a hold corrected for the door's travel, an early press is <i>still</i>
     * fatal on every night: the press itself is the mistake, and it is fatal
     * for the reason the mechanism exists. So EARLY is not a rung below PRO,
     * it is the second trap beside STARE -- <b>STARE wastes the feed, EARLY
     * wastes the heat, and they are the same economy spent from opposite
     * ends.</b> The suite asserts it loses every night, which is the honest
     * claim; the ladder's rung is GLANCE.
     */
    void early(Feed m, double dt) {
        if (keepHolding(m, dt)) return;
        Side u = urgent();
        if (est[u.ordinal()] <= EARLY_AT) {
            beginHold(m, u);
            return;
        }
        lookFor += dt;
        if (lookFor < GLANCE_ON) {
            m.watch(u);
            return;
        }
        if (lookFor < GLANCE_ON + GLANCE_OFF) {
            m.dark();
            return;
        }
        lookFor = 0;
    }

    /** How close the over-cautious player shuts the door at. */
    public static final double EARLY_AT = 2.0;

    /**
     * A player with a habit: look, rest, look.
     *
     * <p>A fixed rhythm rather than a decision, and it is on the ladder
     * because it is what most people actually do -- they work out a cadence
     * that keeps the picture readable and then stop thinking about it. It
     * beats STARE by a mile and loses to PRO by the amount the rhythm is wrong
     * at the moments that matter.
     */
    void glance(Feed m, double dt) {
        if (keepHolding(m, dt)) return;
        Side u = urgent();
        if (soonest(m, u) <= SHUT_LEAD) {
            beginHold(m, u);
            return;
        }
        lookFor += dt;
        if (lookFor < GLANCE_ON) {
            m.watch(glanceSide);
            return;
        }
        if (lookFor < GLANCE_ON + GLANCE_OFF) {
            m.dark();
            return;
        }
        lookFor = 0;
        glanceSide = glanceSide.other();
    }

    /**
     * The competent loop.
     *
     * <p>Four jobs, in order, and the order is the whole policy:
     *
     * <ol>
     *   <li><b>The door is shut.</b> Keep it shut until the walker's patience
     *       is up, because a shut door is a door you cannot see through and
     *       letting go early is the only way shutting it can kill you.</li>
     *   <li><b>Something is at the door.</b> Shut it. This is the move the
     *       whole night is spent earning, and it is worth spending the circuit
     *       on because the door is the only thing in the office that actually
     *       stops anything.</li>
     *   <li><b>Something is coming.</b> Watch its hall -- but only while the
     *       feed is young enough to be worth reading. This is the difference
     *       between PRO and every policy below it.</li>
     *   <li><b>Nothing is close and nothing is coming.</b> Be dark, and let
     *       the picture catch up. This is the state the competent player
     *       spends most of the night in, and it is the one no other policy on
     *       the ladder ever reaches.</li>
     * </ol>
     */
    void play(Feed m, double dt) {
        if (keepHolding(m, dt)) return;

        Side u = urgent();
        int i = u.ordinal();

        // Shut the door only on a belief that is actually current. A stale
        // belief that says "at the door" is a belief that has been decaying
        // toward the door for the whole time nobody looked, and acting on it
        // is how a policy ends up holding a door for a walker that is still at
        // the far end of the hall -- measured, that is exactly what the first
        // version of this did, and it spent the night with the mechanism
        // pinned at the top of its range and the walkers arriving into a door
        // that could no longer shut.
        if (soonest(m, u) <= SHUT_LEAD && age[i] < FRESH) {
            beginHold(m, u);
            return;
        }

        // Something to find out: either the belief is too old to act on, or
        // something is close enough that knowing exactly where it is matters.
        // The hall worth spending the look on is not always the urgent one --
        // the other hall's belief is the one nobody has refreshed, and it is
        // the one that will be urgent next.
        Side w = u;
        Side o = u.other();
        if (age[o.ordinal()] > age[i] && est[o.ordinal()] <= WATCH_AT) w = o;
        int wi = w.ordinal();
        if (age[wi] > FRESH || est[wi] <= WATCH_AT) {
            if (m.feed < LOOK_MAX) {
                m.watch(w);
                return;
            }
            // The picture is as old as it is allowed to get. Let it catch up
            // rather than read a hall through a photograph.
        }

        m.dark();
    }

    /**
     * How old a belief has to be before the bot will not act on it.
     *
     * <p>Half a second, which is about a fifth of a step. It is small because
     * the thing the belief is used for -- deciding whether to shut the door --
     * is a decision with a duty cycle attached to it, and a wrong one costs
     * the next arrival rather than this one.
     */
    public static final double FRESH = 0.50;

    // ---- The sweep ----

    /** Run one night to the end and say whether it was survived. */
    public static boolean survived(Policy p, int night, long seed) {
        Feed m = new Feed(night, seed);
        Bot b = new Bot(p);
        double dt = 1.0 / 60.0;
        for (int i = 0; i < 60 * 400 && m.status == Feed.Status.PLAYING; i++) {
            b.step(m, dt);
            m.update(dt);
        }
        return m.status == Feed.Status.SURVIVED;
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
     * How the nights ended, for one policy: taken, survived, and how many
     * times a shut door emptied itself.
     */
    public static int[] tally(Policy p, int night, int seeds) {
        int taken = 0, survived = 0, gaveUp = 0;
        for (int i = 0; i < seeds; i++) {
            Feed m = new Feed(night, 1000L * night + i);
            Bot b = new Bot(p);
            double dt = 1.0 / 60.0;
            for (int f = 0; f < 60 * 400 && m.status == Feed.Status.PLAYING; f++) {
                b.step(m, dt);
                m.update(dt);
            }
            if (m.status == Feed.Status.SURVIVED) survived++; else taken++;
            gaveUp += m.gaveUp;
        }
        return new int[]{taken, survived, gaveUp / Math.max(1, seeds)};
    }

    /**
     * Which hall killed a policy most, over a run of seeds.
     *
     * <p>A survival percentage cannot tell you why, and it cannot tell you
     * which of your two halls is the one doing the killing. Print the
     * breakdown.
     */
    public static int[] killers(Policy p, int night, int seeds) {
        int[] out = new int[2];
        for (int i = 0; i < seeds; i++) {
            Feed m = new Feed(night, 1000L * night + i);
            Bot b = new Bot(p);
            double dt = 1.0 / 60.0;
            for (int f = 0; f < 60 * 400 && m.status == Feed.Status.PLAYING; f++) {
                b.step(m, dt);
                m.update(dt);
            }
            if (m.status == Feed.Status.TAKEN && m.takenSide != null) {
                out[m.takenSide.ordinal()]++;
            }
        }
        return out;
    }
}
