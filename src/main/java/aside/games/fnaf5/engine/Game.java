package aside.games.fnaf5.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNAF 5 core loop.
 *
 * FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 took the
 * desk and gave you a body that can only be in one place. FNAF 5 gives you
 * <b>a building</b> -- and then takes away the one thing every game before
 * it assumed, which is that you can see where you are standing.
 *
 * <pre>
 *   THE RULE: the camera cannot see the room you are in.
 * </pre>
 *
 * That single line is the game. You have five rooms, a monitor, and three
 * things walking around in the dark, and the monitor is only ever pointed
 * somewhere else. So every decision to move is a decision made on a picture
 * of where you are going, taken from another room, and already a second old
 * by the time you get there. The information and the safety are the same
 * resource and you cannot hold both.
 *
 * The three things walking around do not differ in speed. They differ in
 * <i>what they follow</i>, and the three answers contradict each other:
 *
 *   1. FUNTIME FREDDY follows you. Standing still is what kills you.
 *   2. BALLORA follows sound. Moving is what kills you.
 *   3. FUNTIME FOXY follows the camera. Looking is what kills you.
 *
 * A player cannot satisfy all three at once, so the night is spent choosing
 * which one to be wrong about. <b>Each of the three has exactly one
 * answer, and the controlled shock is one of them</b> -- it removes Funtime
 * Freddy, and only him, because he is the one with no other answer. Ballora
 * has to be waited out in silence; Funtime Foxy has to be turned by moving
 * the feed. There are three to five shocks a night and no way to earn more.
 *
 * <h2>2026-10-01: the three answers become three answers</h2>
 *
 * The paragraph above described the design from the beginning, and the
 * sweep said the design was not what the game did. Over a week of sixty
 * seeds a night, HOLD's 64 deaths were 59 Ballora, 4 Funtime Foxy and 1
 * Funtime Freddy -- and the three policies that keep moving finished within
 * seven points of each other, with PANIC (shock everything, walk at
 * random) ahead of the competent one. Two things were wrong, and both were
 * rules rather than numbers:
 *
 *   1. THE SHOCK WAS A UNIVERSAL ANSWER. It cleared the room of all three,
 *      so a player never had to learn which counter belonged to which
 *      threat -- and the policy that spammed it won. It now removes the
 *      pursuer only. See {@link #shock}.
 *
 *   2. LOOKING WAS FREE. Funtime Foxy followed the feed at the same speed
 *      whether the feed was up or down, so the one threat whose rule is
 *      about looking never landed a hit. The feed is now his fuel: he is
 *      frozen with the monitor down and twice as fast with it up. See
 *      {@link #FEED_PACE}.
 *
 * And one number had to move with them: Ballora was slower than the player,
 * so "keep walking" beat her and the stop -- her actual counter -- was
 * never needed. Measured after the three changes: the killers are 12
 * Ballora, 11 Funtime Foxy and 3 Funtime Freddy, and the ladder is FLEE
 * 97%, HOLD 91%, PANIC 0%.
 *
 * <h2>2026-10-01, the third pass: the stop becomes the answer</h2>
 *
 * The redesign above fixed the *legibility* of the three counters and left
 * the ladder upside down, and the fire after it measured why: <b>walking
 * answered all three threats at once.</b> Freddy is slower than the player
 * so you outrun him; Foxy follows the feed and the feed is free to move;
 * and Ballora followed your sound, so she was always one step behind, and
 * a grace longer than a step meant walking out of her room always worked.
 * The three policies that never stop finished within seven points of each
 * other and the competent one was not the best one.
 *
 * Four things changed, and the first is the one the whole pass turns on:
 *
 *   1. <b>Ballora has her own clock, and it is shorter than a step.</b>
 *      See {@link #BALLORA_GRACE}. Walking out of her room is no longer an
 *      answer to her, so the stop is, and the stop is the one thing in the
 *      building that costs time. HOLD -- the policy that never stops --
 *      goes from 90% over the week to <b>5%</b>.
 *   2. <b>Silence buys something.</b> When she gives up she is deaf for
 *      {@link #BALLORA_COOLDOWN} seconds, so the stop is *profitable* and
 *      not merely affordable. Without it the stop bought nothing and the
 *      sweep said so.
 *   3. <b>She is a periodic demand rather than a constant one.</b> Her
 *      pace drops from 2.60 to 1.60: at 2.60 she arrived more often than
 *      the night could pay for her.
 *   4. <b>Freddy's pace ramps across the week</b> ({@link #freddyPace}),
 *      because with the stop mandatory the week's difficulty is exactly
 *      how expensive stillness is -- and stillness is what Freddy
 *      punishes. The grace table goes back up to compensate: it has to
 *      stay above the worst trip (a move and a shock, 2.05s) or the
 *      counters stop being usable at the end of the week.
 *
 * Measured over 600 seeds a night: IDLE 0%, REACT 20%, HOLD 5%, FLEE 10%,
 * PANIC 0%, and the policy that plays all three counters at <b>61%</b>,
 * monotonic across the week (86/76/64/50/31). The ladder is right for the
 * first time in the game's life, and the killers are Ballora's: HOLD dies
 * to her on every night of the week.
 *
 * <h2>2026-10-01, the fourth pass: stillness gets a price</h2>
 *
 * The pass above left one thing wrong, and it was the second place in the
 * ladder rather than the first. <b>REACT -- the policy that answers the
 * room and never takes a step -- read 20% over the week, behind only the
 * policy that plays all three counters.</b> Its whole night was a race
 * between Funtime Freddy's arrival rate and five charges, and on the first
 * night the charges won: 92%. The reason is that every cost in the
 * building was charged to a player who is <i>doing</i> something -- a step
 * is a noise and the noise is Ballora's, and the feed is Funtime Foxy's
 * fuel -- so a player who never moves paid neither and had opted out of
 * two of the three demands.
 *
 * The fix is a rule and it belongs to the pursuer, because he is the only
 * threat left with anything to charge: <b>past {@link #STILL_WINDOW}
 * seconds without a step, Funtime Freddy closes at {@link #STILL_PACE}.</b>
 * He follows you, and a player who never moves is a player he does not
 * have to follow -- he already knows where they are. Measured over 200
 * seeds a night: REACT 20% -> <b>0%</b> over the week and 92% -> 2% on the
 * first night, with the competent policy unchanged at 62%. The ladder now
 * reads PRO 62%, FLEE 11%, HOLD 6%, PANIC 1%, REACT 0%, IDLE 0% -- the
 * policy that never moves is behind both of the policies that do.
 *
 * The engine is pure logic with no UI dependency, so the week can be
 * swept by a bot at 60fps with no display. That is how the difficulty
 * table was set, rather than by playing twenty nights by hand.
 */
public class Game {

    // ---- Clock ----
    public static final double HOUR_SECONDS = 40.0;
    public static final int NIGHT_HOURS = 6;

    // ---- The body ----
    /** One room. There is no hub, so every move costs the same. */
    public static final double MOVE_TIME = 1.50;
    /** How long the shock takes, and how long your hands are busy. */
    public static final double SHOCK_TIME = 0.55;
    /** How long the shock is drawn for. */
    public static final double SHOCK_FLASH = 0.85;

    // ---- Sound ----
    /**
     * How long a sound stays in the building.
     *
     * This is the whole of Ballora's memory: past it she has no target and
     * stops moving, which is what makes standing still a defence rather
     * than just a delay.
     */
    public static final double SOUND_MEMORY = 6.0;
    /**
     * Silence, since she arrived, that loses her.
     *
     * The book answer to Ballora is to stop making noise: her patience runs
     * from the moment she arrives, and the building has to be quiet for the
     * same stretch, so the player owes her `patience` seconds of standing
     * still *after* the step they were already making lands.
     *
     * <b>1.8 was a coin flip and 1.2 was still too slow; it is 0.8 now.</b>
     * At 1.8 it was exactly night 5's grace, so the answer to her was a
     * coin flip on the last night. At 1.2 it fitted inside the grace and
     * the counter worked -- but only just, and the sweep said so: with
     * {@link #BALLORA_GRACE} in place the competent policy still died to
     * her, because 1.2 of stillness plus the step you were already making
     * is longer than the clock you have to answer inside. The counter has
     * to fit inside *her* clock with room to spare, not inside the night's
     * grace: measured, 0.8 against a 1.40 clock is the difference between
     * a competent policy at 43% and one at 61%.
     */
    public static final double BALLORA_PATIENCE = 0.8;
    /**
     * Ballora's own clock: how long she stands in your room before she is
     * on you.
     *
     * <pre>
     *   THE RULE: her clock is shorter than a step, so walking out of her
     *             room is not an answer to her. Stopping is.
     * </pre>
     *
     * <b>This is the change the whole 2026-10-01 balance pass turns on, and
     * it is a rule rather than a number.</b> Every other threat is answered
     * by walking: Funtime Freddy is slower than the player, and Funtime
     * Foxy follows a feed that costs nothing to move. Ballora was answered
     * by walking too, and that was the whole problem -- the design says the
     * three demands contradict, and the sweep said one strategy satisfied
     * all three, and it was the one the player does by default. A grace
     * longer than {@link #MOVE_TIME} is a grace in which leaving her room
     * always works, so the stop was never needed and the competent policy
     * was not the best one.
     *
     * <b>Why the value does not matter much, and why it is flat.</b> The
     * death is decided by whether you were *mid-move* when she arrived,
     * which is binary: any clock below a step turns it into the same game,
     * and any clock above one turns it back into the old game. Measured:
     * 1.45, 1.42 and 1.40 over the week are identical to three decimal
     * places, and 1.46 is a different game. So there is no ramp here --
     * a threshold cannot be ramped -- and the week's ramp lives in
     * {@link #grace} and {@link #freddyPace} instead.
     *
     * 1.40 against a 1.50 step leaves 0.10s of margin, which is enough
     * that the threshold is not being straddled by the frame rate, and
     * short enough that the margin is not a place to hide.
     */
    public static final double BALLORA_GRACE = 1.40;
    /**
     * Seconds Ballora is deaf after she gives up.
     *
     * <b>This is what makes the stop profitable rather than merely
     * affordable.</b> With her own clock in place the stop became
     * *necessary* -- HOLD, the policy that never stops, drops from 90% to
     * 5% -- and the competent policy still read 47%, because a stop that
     * buys nothing is a stop the player is only making out of fear. She
     * gives up when the building has been quiet long enough; this is the
     * stretch after that in which she has no target at all, so silence
     * buys a window in which you can move freely.
     *
     * Ten seconds is a little over two rooms of walking. It is long enough
     * to be worth paying 0.8 seconds of stillness for and short enough
     * that she is never off the board for a whole night. Measured: 8, 10
     * and 14 are indistinguishable over the week, so this is a threshold
     * too -- what matters is that it is not zero.
     */
    public static final double BALLORA_COOLDOWN = 10.0;
    /** How often something in your room announces itself again. */
    public static final double CUE_EVERY = 1.6;

    public int night;

    public double time = 0;
    public int hour = 0;

    // ---- Where you are ----
    public Room.Where where = Room.START;
    /** Where you are walking to, or null. */
    public Room.Where heading = null;
    /** Seconds of hands-busy left: a move or a shock, never both. */
    public double busy = 0;
    /** How long the thing you are in the middle of takes, for the bar. */
    public double busyTotal = 0;
    /** How long the shock flash has left to draw. */
    public double shockT = 0;

    // ---- What you can see ----
    /** The room on the monitor. Never the room you are in. */
    public Room.Where camera = Room.Where.AUDITORIUM;
    /** Whether the monitor is up at all. */
    public boolean monitorOn = true;

    // ---- Sound ----
    /** Where the last sound was made. */
    public Room.Where lastSound = Room.START;
    /** Seconds since it was made. */
    public double soundAge = 999;
    /** How many sounds have been made this night. Read by the report. */
    public int sounds = 0;

    // ---- What you can hear ----
    /**
     * The last thing that stepped into a room next to you.
     *
     * This is the whole warning system, and it is the reason the game is
     * survivable at all. You cannot see the room you are in, so the only
     * notice you get that something is about to be in it is the sound of
     * it arriving next door. It is deliberately transient -- a step, not a
     * presence -- so a player who is not listening at the right moment
     * does not get a second chance.
     */
    public String heardKey = null;
    public Room.Where heardAt = null;
    /** Seconds since that step. Large means nothing is next to you. */
    public double heardAge = 999;

    // ---- The shock ----
    public int shocks = 3;
    public int shocksUsed = 0;
    /** Shocks that found nothing. The number the player should want low. */
    public int wastedShocks = 0;

    // ---- Outcome ----
    public enum Status { PLAYING, JUMPSCARED, SURVIVED }

    public Status status = Status.PLAYING;
    /** Who got you, for the jumpscare. */
    public String killer = null;

    // ---- The cast ----
    public final List<Threat> threats = new ArrayList<>();
    /** The most recent arrival, so the screen can flash the right edge. */
    public Threat lastArrival;
    /** How many times something has walked into the room you were in. */
    public int arrivals;
    /** How many rooms you have walked through. Read by the report. */
    public int moves;
    /**
     * Seconds since you last finished a move.
     *
     * The one number in the building that is charged to a player for
     * <i>not</i> doing something. Every other price is paid by moving -- a
     * step is a noise and the noise is Ballora's, and the feed is Funtime
     * Foxy's fuel -- so a player who never moves pays neither. This is what
     * Funtime Freddy charges them. See {@link #STILL_WINDOW}.
     */
    public double stillTime;
    /** How many times you put the monitor on a different room. */
    public int cameraSwitches;

    public final Random rng;

    private final List<String> cues = new ArrayList<>();

    public Game(int night, long seed) {
        this.night = night;
        this.rng = new Random(seed);
        this.shocks = shockAllowance(night);

        // One each, and they start where they belong. The pace spread
        // staggers their moves; without it all three step on the same beat
        // and the building reads as a metronome rather than as a place.
        //
        // BALLORA'S PACE IS 1.60, and it is slower than the player on
        // purpose. She is not a chase -- {@link #BALLORA_GRACE} is what
        // makes her dangerous, and it makes her dangerous whether or not
        // she can catch you. What her pace decides is how *often* she is a
        // demand: at 2.60 she moved every 1.15 to 1.9 seconds, so she
        // arrived more often than a night can pay for her and the competent
        // policy read 47% no matter what the economy did. At 1.60 she is a
        // periodic demand -- roughly one stop every three to five seconds
        // -- which is a rhythm a player can plan around. Measured over 600
        // seeds a night: 1.4, 1.6 and 1.8 finish within three points of
        // each other, so this is a shape rather than a knife edge.
        threats.add(new Threat("Ballora", "ballora", Threat.Rule.SOUND,
                Room.Where.BALLORA, 1.60, Room.Where.BALLORA));
        threats.add(new Threat("Funtime Foxy", "foxy", Threat.Rule.ATTENTION,
                Room.Where.AUDITORIUM, 0.95, Room.Where.AUDITORIUM));
        threats.add(new Threat("Funtime Freddy", "freddy", Threat.Rule.PURSUIT,
                Room.Where.PARTS, freddyPace(), Room.Where.PARTS));
    }

    // ---- Difficulty ----

    /**
     * AI level per night -- the chance in twenty that a threat moves.
     *
     * Chase's line for the franchise was "each gets harder than the last",
     * so this starts above FNAF 4's and ends at the top. FNAF 5 is the one
     * that takes away the room you are standing in, so it also has the
     * steepest ramp of the five.
     *
     * <b>This was defined and never read until 2026-09-30.</b> The threats
     * moved on their interval every time, with no roll, so the building was
     * a metronome and the seed did nothing; see the note in
     * `Threat.update`. It is read now, and it is what makes a night a
     * distribution instead of a script.
     *
     * <b>And the top of the scale is not "always moves".</b> Night 5 sits
     * at 20, which is the franchise's top and which means every roll
     * succeeds -- so at the top of the week the die stops being a die. That
     * is what {@link #INTERVAL_JITTER} is for: the interval carries the
     * randomness the roll has run out of, so the last night is the fastest
     * *and* still unpredictable. A deterministic threat in a building the
     * player can walk is a threat the player can learn, and FNAF 5 is the
     * first game in the franchise where that is true.
     */
    static int aiLevel(int night) {
        int[] table = {4, 7, 11, 15, 20};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /**
     * How much a threat's wait varies around its interval, as a fraction.
     *
     * A threat waits `interval * (1 + JITTER * (rand - 0.5))`, so 0.5 gives
     * a wait between three quarters and five quarters of the interval and
     * leaves the mean where the table put it.
     *
     * <b>This exists because the die roll runs out at the top of the
     * week.</b> `aiLevel` is a chance in twenty, so night 5 is 20 and every
     * roll succeeds: with no jitter, night 5 was a single scripted night
     * and all sixty seeds in the sweep produced the identical result. The
     * symptom was a difficulty table that read 0% or 100% and nothing in
     * between, and a week that got *easier* at the end -- night 5 came back
     * at 100% while night 1 came back at 93%. It was not a balance problem.
     * A night with no randomness in it cannot be measured, and it cannot be
     * tuned either. See {@link Threat#update}.
     */
    public static final double INTERVAL_JITTER = 0.5;

    /** Seconds between moves for a threat of pace 1.0. */
    public double baseInterval() {
        double[] table = {5.0, 4.4, 3.8, 3.4, 3.0};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /**
     * How fast Funtime Freddy walks, per night.
     *
     * <b>This is where the week's ramp lives now, and it is the last of the
     * four changes in the 2026-10-01 pass.</b> Once Ballora's clock made
     * the stop mandatory, the difficulty of a night stopped being "how
     * fast is anything" and became "how expensive is standing still" --
     * and the only thing in the building that punishes standing still is
     * the pursuer. So he is the dial: 0.85 on the first night and 1.15 on
     * the last, which is a 35% increase in how often he arrives.
     *
     * <b>It is a table rather than a constant because the other two dials
     * cannot carry a ramp.</b> {@link #BALLORA_GRACE} is a threshold -- a
     * clock either is or is not shorter than a step, and measured, 1.45
     * and 1.40 are the same game while 1.46 is a different one. The
     * interval is the wrong dial for the reason the previous pass found: a
     * threat that moves faster also *leaves your room* faster. Freddy's
     * pace has no such counter-effect -- he is following you, so arriving
     * sooner is arriving sooner -- and it is the one number the whole
     * squeeze is denominated in.
     *
     * <b>And the grace table had to go back up to meet it.</b> The worst
     * case is a move out of the room you are in and a shock, 2.05 seconds,
     * so a grace below that cannot be answered from anywhere. The previous
     * pass ramped grace down to 1.8, which was right for an engine where
     * the shock was Ballora's answer and wrong for this one: measured with
     * the ramp in Freddy's pace, night 5's competent policy reads 31% at
     * grace 2.2 and 0% at grace 1.8. See {@link #grace}.
     */
    public double freddyPace() {
        double[] table = {0.85, 0.88, 0.92, 1.00, 1.15};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /**
     * How much faster Funtime Foxy walks while the monitor is up.
     *
     * <b>The feed is his fuel, and that is the second half of the redesign.</b>
     * He follows the camera, so the camera is what moves him: with the
     * monitor down he is frozen (his target is null), and with it up he
     * walks at this multiple of his own pace. Before this he was the same
     * speed either way, which meant looking was free and the one threat
     * whose whole rule is about looking never landed a hit -- four kills
     * over a week of sixty seeds a night, against Ballora's fifty-nine.
     *
     * Now the monitor is a cost and not just a window. The design always
     * claimed it was ("the camera is the only way to know which room is
     * safe to walk into"); this is the number that makes the claim true.
     * It is deliberately a multiple of his pace rather than a fixed
     * interval, so the week's ramp still reaches him.
     */
    public static final double FEED_PACE = 2.0;

    /**
     * How long you can stand still before Funtime Freddy starts closing
     * faster.
     *
     * <pre>
     *   THE RULE: he follows you, and a player who never moves is a player
     *             he does not have to follow -- he already knows where you
     *             are.
     * </pre>
     *
     * <b>This is what stillness costs when the player has chosen it, and
     * it was the one price the building did not charge.</b> Every other
     * cost in FNAF 5 is paid by a player who is <i>doing</i> something: a
     * step is a noise and the noise is Ballora's, and the feed is Funtime
     * Foxy's fuel. A player who never moves pays neither -- and the sweep
     * said so. REACT, the policy that answers the room and never takes a
     * step, read <b>92% on the first night</b> and 20% over the week,
     * second only to the policy that plays all three counters, because
     * never moving means never making a sound, so Ballora never finds it
     * and the whole night is a race between Funtime Freddy's arrival rate
     * and five charges. On the first night the charges win.
     *
     * <b>Why the fix is a rule rather than a number.</b> The design says
     * the three demands contradict: walk and Ballora comes, stop and
     * Freddy comes, look and Foxy comes. The contradiction is only felt by
     * a player who is moving. A player who never moves has opted out of
     * two of the three, and the only threat left to charge them is the
     * pursuer -- who is also the one threat with no counter but the shock.
     * So the pursuer is where the price of stillness has to live, and the
     * price is that he stops having to find you: past this many seconds
     * without a step, he closes at {@link #STILL_PACE}.
     *
     * <b>Three seconds, and both bounds on it are measured.</b> It has to
     * be longer than a step (1.50s) or a player who is walking is charged
     * for the gaps between their own footsteps, and it has to be longer
     * than a stop for Ballora (her patience is 0.8s, so the competent
     * policy's stops are around a second) or the counter the whole
     * 2026-10-01 pass is built on would be taxed by the fix meant to tax
     * the turtle. Measured over 200 seeds a night: the window is not the
     * dial -- 3.0 and 5.0 read the same -- and the competent policy is
     * unchanged at 62%.
     */
    public static final double STILL_WINDOW = 3.0;

    /**
     * How much faster the pursuer closes once you have stood still past
     * {@link #STILL_WINDOW}.
     *
     * A multiple of his own pace rather than a fixed interval, so the
     * week's ramp still reaches him. Measured over 200 seeds a night with
     * the window at 3.0: 2.0, 2.5 and 3.0 take REACT's week from 20% to
     * 2%, 0.4% and 0%, and leave the competent policy at 62% throughout --
     * the fix is a threshold like {@link #BALLORA_GRACE} and not a knife
     * edge, so the value is chosen to be clearly past the threshold rather
     * than tuned against a curve.
     */
    public static final double STILL_PACE = 2.5;

    public double interval(Threat t) {
        double base = baseInterval() / t.pace;
        if (t.rule == Threat.Rule.ATTENTION && monitorOn) base /= FEED_PACE;
        // The pursuer closes faster on a player who has stopped moving.
        // See STILL_WINDOW: he is the only threat that can charge for
        // stillness, because the other two are paid for by moving.
        if (t.rule == Threat.Rule.PURSUIT && stillTime >= STILL_WINDOW) {
            base /= STILL_PACE;
        }
        return base;
    }

    /**
     * How long something stands in your room before it is on you.
     *
     * The dial the whole week turns on. The worst case is a move out of the
     * room you are in and a shock, 2.05 seconds, so a grace above that can
     * be answered from anywhere and a grace below it cannot. Night 1 is
     * comfortable, night 5 is not, and the difference is what forces the
     * player to stop reacting and start deciding where to be.
     *
     * <b>Retuned 2026-10-01, twice, and the second time is the interesting
     * one.</b> The table used to be {3.0, 2.8, 2.6, 2.4, 2.2} and the week
     * did not get harder: HOLD survived 93/95/100/98/100, with the *last*
     * night the easiest one. Most of that was the missing jitter (see
     * {@link #INTERVAL_JITTER}), but not all of it. Once the night was a
     * distribution, the interval turned out to be the wrong dial to ramp:
     * a threat that moves faster also *leaves* your room faster, so a
     * shorter interval can make a night easier rather than harder. Grace
     * has no such counter-effect -- it is exactly the time you have to
     * answer -- so the ramp moved here, down to {3.0, 2.7, 2.4, 2.1, 1.8}.
     *
     * <b>Then it had to come back up, and the reason is a rule that
     * changed underneath it.</b> 1.8 is below the worst trip -- a move and
     * a shock, 2.05 seconds -- and that was fine while the shock was the
     * answer to Ballora, because a player who was already standing still
     * never had to make the trip. It stopped being fine when
     * {@link #BALLORA_GRACE} made the stop mandatory: now the player owes
     * stillness to one threat and a shock to another, and a night whose
     * grace cannot contain both is a night with no answer in it. Measured
     * with the ramp in {@link #freddyPace} where it belongs, night 5's
     * competent policy reads 31% at 2.2 and 0% at 1.8.
     *
     * <b>The ramp is gentler than it looks, and that is deliberate.</b>
     * The week's difficulty is carried by Freddy's pace now, so this table
     * only has to stay above the worst trip and come down slowly enough
     * that the last night is still a night rather than a coin flip.
     */
    public double grace() {
        double[] table = {3.0, 2.8, 2.6, 2.4, 2.2};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /**
     * The clock that applies to a given threat.
     *
     * One threat in the building is not on the night's clock, and it is the
     * one whose counter is *time* rather than a button. See
     * {@link #BALLORA_GRACE}: her clock is shorter than a step, so walking
     * out of her room is not an answer to her and standing still is.
     * Everything else is answered by moving, so everything else gets the
     * night's grace.
     */
    public double graceFor(Threat t) {
        if (t != null && t.rule == Threat.Rule.SOUND) return BALLORA_GRACE;
        return grace();
    }

    /** The worst case: a move, then the shock. */
    public static double worstTrip() {
        return MOVE_TIME + SHOCK_TIME;
    }

    /**
     * Controlled shocks per night.
     *
     * Never more than five, and the last night has three. The shock is the
     * only answer Funtime Freddy has, so its scarcity is what makes the
     * pursuer a clock rather than a nuisance -- and, since the shock stopped
     * clearing the room of all three (see {@link #shock}), it is also what
     * stops a player from spending one on Ballora and expecting it to work.
     */
    static int shockAllowance(int night) {
        int[] table = {5, 5, 4, 4, 3};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    // ---- Sound ----

    public void cue(String name) {
        cues.add(name);
    }

    public List<String> drainCues() {
        if (cues.isEmpty()) return List.of();
        List<String> out = List.copyOf(cues);
        cues.clear();
        return out;
    }

    /**
     * Something made a noise, here.
     *
     * Every sound in the building goes through this, which is what keeps
     * Ballora honest: a footstep, a shock and a body hitting the floor are
     * the same event to her, because she cannot see and has no other way
     * to know the building has anyone in it.
     */
    void makeSound(Room.Where at) {
        lastSound = at;
        soundAge = 0;
        sounds++;
    }

    /** Called by a threat the moment it steps into a room next to you. */
    void heardNextDoor(Threat t) {
        heardKey = t.key;
        heardAt = t.room;
        heardAge = 0;
    }

    /** Called by a threat the moment it walks into your room. */
    void arrived(Threat t) {
        lastArrival = t;
        arrivals++;
    }

    // ---- Update ----

    public void update(double dt) {
        if (status != Status.PLAYING) return;

        time += dt;
        hour = (int) Math.floor(time / HOUR_SECONDS);
        if (hour >= NIGHT_HOURS) {
            status = Status.SURVIVED;
            cue("chime_6am");
            return;
        }

        // The building forgets a sound on its own. Slowly enough that a
        // move is still audible to Ballora a few seconds later, which is
        // the point: she is the price of the last thing you did, not of
        // the thing you are doing.
        soundAge += dt;
        heardAge += dt;
        // Stillness is the one thing in the building that is charged for
        // not doing anything. See STILL_WINDOW.
        stillTime += dt;

        if (busy > 0) {
            busy -= dt;
            if (busy <= 0) {
                busy = 0;
                if (heading != null) {
                    arrive();
                }
            }
        }
        if (shockT > 0) shockT = Math.max(0, shockT - dt);

        for (Threat t : threats) {
            t.update(dt, this);
            if (status != Status.PLAYING) return;
        }
    }

    /**
     * You are standing in the room you were walking to.
     *
     * The monitor dies here if it was pointed at where you have just
     * arrived, and that is not a bug being papered over -- it is the rule
     * of the game happening to you. You walked into the picture, so there
     * is no picture any more.
     */
    void arrive() {
        where = heading;
        heading = null;
        moves++;
        stillTime = 0;
        if (camera == where) {
            camera = null;
            cue("static");
        }
    }

    void jumpscare(Threat t) {
        jumpscare(t.name);
    }

    void jumpscare(String who) {
        if (status != Status.PLAYING) return;
        status = Status.JUMPSCARED;
        killer = who;
        cue("scare_sprint");
    }

    // ---- Controls ----
    // Every one of these is a no-op once the night is over.

    public boolean moving() {
        return heading != null;
    }

    /** True when the shock could be used right now. */
    public boolean canShock() {
        return status == Status.PLAYING && busy <= 0 && shocks > 0;
    }

    /**
     * Walk one room left or right.
     *
     * Refused while your hands are busy, which covers both the shock you
     * are in the middle of and the move you are in the middle of. There is
     * no queue: a second press during a move is not a second move.
     *
     * <b>Not refused for the room you are walking into, and that is a
     * deliberate design decision rather than an oversight.</b> The rule
     * this method used to describe -- that the things in the building are
     * walls, so a player pinned between two occupied doors has nowhere to
     * be -- was written down and never implemented, and when it was
     * implemented and swept it turned out to be unplayable. With a
     * pursuer in a line of five rooms and no way past him, the player is
     * cornered on a fixed cycle and the whole night becomes a countdown of
     * charges: measured at Freddy paces from 0.25 to 1.05, every policy
     * died on every night. A line plus a pursuer plus walls has no
     * counterplay, so the walls are not there.
     *
     * <p><b>Re-verified 2026-10-01, against a working instrument.</b> The
     * measurement above was taken before the engine had a die roll and
     * before the bot could see what a player sees, so it was worth
     * doubting. It holds: with the jitter, the fixed bot and the retuned
     * grace, the guard still takes every policy to 0% from night 3 on --
     * 93/32/0/0/0 for HOLD, and PANIC falls with it, which is the tell
     * that it is the geometry and not the policy. The walls stay out.
     *
     * <p><b>OPEN, and deliberately not fixed (2026-10-01):</b> what the
     * missing refusal costs is that movement direction barely matters. The
     * player is faster than everything in the building, so the only threat
     * that cares where you are is Funtime Freddy -- and he is the one that
     * never kills anybody. The sweep says so plainly: HOLD, FLEE and PANIC
     * finish the week within seven points of each other, and the
     * competent policy is not the best one. The rule and the difficulty
     * table have to move together, and that is a redesign rather than a
     * tuning job; see the note in {@link Bot}.
     *
     * <p>The instrument that found this was itself broken when the note
     * above was first written. HOLD and REACT died to Funtime Freddy on
     * every night while PANIC survived, because HOLD parked in the middle
     * of the building -- and, underneath that, because the engine had no
     * die roll in it at all, so every night was the same night. Both are
     * fixed; see {@link Bot} and {@link Threat#update}. The table in
     * {@link SelfTest} is now a reading of the game rather than of the bot.
     */
    public boolean step(int direction) {
        if (status != Status.PLAYING) return false;
        if (busy > 0) return false;
        Room.Where dest = Room.at(Room.index(where) + direction);
        if (dest == null) return false;
        heading = dest;
        busy = MOVE_TIME;
        busyTotal = MOVE_TIME;
        // Your feet are what Ballora hears, and they are heard at the room
        // you are walking into, because that is where you will be.
        makeSound(dest);
        cue("footstep");
        return true;
    }

    /**
     * Put the monitor on a room.
     *
     * Refused for the room you are standing in, which is the rule of the
     * game stated as a guard rather than as a comment. Free, and it has to
     * be: the cost of looking is not time, it is that Funtime Foxy follows
     * the feed, so the price of watching a room is that you have told
     * something where to go.
     */
    public boolean watch(Room.Where room) {
        if (status != Status.PLAYING) return false;
        if (room == null || room == where) return false;
        if (monitorOn && camera == room) return false;
        monitorOn = true;
        camera = room;
        cameraSwitches++;
        cue("camera_up");
        // Funtime Foxy turns to follow the feed the moment the feed moves.
        // Without this the counter does not work: the thing is standing in
        // your room, you look somewhere else, and it stands there for
        // another full interval deciding -- which is longer than the grace,
        // so the answer to Foxy would be "look away and die anyway". A
        // counter that does not visibly counter is not a counter.
        for (Threat t : threats) {
            if (t.rule == Threat.Rule.ATTENTION) t.timer = interval(t);
        }
        return true;
    }

    /**
     * Take the monitor down.
     *
     * The other half of Foxy's rule. With no feed there is nothing for it
     * to follow, so it stops where it is -- which is a real defence and
     * costs you the only eye you have.
     */
    public boolean monitorDown() {
        if (status != Status.PLAYING) return false;
        if (!monitorOn) return false;
        monitorOn = false;
        cue("camera_down");
        return true;
    }

    /**
     * The controlled shock.
     *
     * Blind, limited, and <b>the answer to exactly one of the three</b>.
     *
     * <pre>
     *   THE RULE: the shock removes Funtime Freddy. It does not remove
     *             Ballora, and it does not remove Funtime Foxy.
     * </pre>
     *
     * It throws the pursuer to whichever end of the building is further
     * away, and it makes a noise doing it. Both halves of that matter. It
     * is the only thing that answers Freddy, because he follows you and
     * cannot be distracted; and the noise is a reason for Ballora to stay,
     * because she is blind and a shock in your room is the loudest thing
     * that has happened all night. Foxy does not care: he follows the feed,
     * and a shock is not a feed.
     *
     * <b>This is the redesign, and the sweep is why.</b> The shock used to
     * clear the room of all three, and the reading that came back was that
     * the universal answer beat every specific one: PANIC -- shock whatever
     * is in the room and walk at random -- survived 86% of the week, ahead
     * of FLEE at 85% and the competent HOLD at 79%. A game whose best
     * strategy is to panic is a game whose counters are decoration. Making
     * the shock the pursuer's answer makes the other two counters
     * mandatory: Ballora has to be waited out and Foxy has to be turned,
     * and the shock is what you spend when Freddy is the one in the room.
     * Measured after: FLEE 97%, HOLD 91%, PANIC 0%.
     *
     * Returns true if it found the pursuer, which is what the screen uses to
     * decide whether the frame was a hit or a waste. Shocking a room that
     * holds only Ballora is a waste, and the counter says so -- which is the
     * game telling the player the rule.
     */
    public boolean shock() {
        if (!canShock()) return false;

        shocks--;
        shocksUsed++;
        busy = SHOCK_TIME;
        busyTotal = SHOCK_TIME;
        shockT = SHOCK_FLASH;
        makeSound(where);
        cue("shock");

        boolean found = false;
        for (Threat t : threats) {
            if (t.room != where) continue;
            // Only the pursuer. See the note above: the shock is one
            // answer, not three, and the other two threats have counters
            // that cost the player something to use.
            if (t.rule != Threat.Rule.PURSUIT) continue;
            found = true;
            t.banished(farEnd());
        }
        if (!found) wastedShocks++;
        return found;
    }

    /** Whichever end of the line is further from where you are standing. */
    Room.Where farEnd() {
        Room.Where west = Room.ALL[0];
        Room.Where east = Room.ALL[Room.COUNT - 1];
        return Room.distance(where, west) >= Room.distance(where, east) ? west : east;
    }

    // ---- What the screen asks ----

    /** True when something is standing in the room you are standing in. */
    public boolean occupied() {
        for (Threat t : threats) if (t.inYourRoom(this)) return true;
        return false;
    }

    /** The thing standing in your room, or null. */
    public Threat standingWith() {
        for (Threat t : threats) if (t.inYourRoom(this)) return t;
        return null;
    }

    /** The thing in a room, or null. Used by the camera feed. */
    public Threat standingIn(Room.Where w) {
        for (Threat t : threats) if (t.room == w) return t;
        return null;
    }

    /** Everything in a room, in cast order. */
    public List<Threat> allIn(Room.Where w) {
        List<Threat> out = new ArrayList<>();
        for (Threat t : threats) if (t.room == w) out.add(t);
        return out;
    }

    /** True when something is one room away, which is as close as sound gets. */
    public boolean nextDoor() {
        for (Threat t : threats) if (t.nextDoor(this)) return true;
        return false;
    }

    /** The room the monitor is showing, or null when it is down. */
    public Room.Where view() {
        return monitorOn ? camera : null;
    }
}
