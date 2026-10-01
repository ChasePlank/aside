package aside.games.fnaf6.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNAF 6 core loop: the salvage bay.
 *
 * <p>FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 took the
 * desk and gave you a body that can only be in one place. FNAF 5 gave you a
 * building and took the room you were standing in. FNAF 6 gives you
 * <b>one chair, one lamp, and one shock</b> -- and then takes away the
 * thing every game before it assumed, which is that a look is free.
 *
 * <pre>
 *   THE RULE: the lamp is the only way to see it, and the lamp is what it
 *             is waiting for.
 * </pre>
 *
 * <p>There is one unit in the room. It is either <b>hostile</b> -- it will
 * get up -- or it is <b>dead</b>, and there is no way to tell them apart
 * except by watching, and watching is the one thing that makes either of
 * them move. The lamp is not a window. It is a stimulus with a bulb in it.
 *
 * <h2>The night</h2>
 *
 * <ol>
 *   <li>A <b>hostile</b> unit advances one pose every {@link #advance()}
 *       seconds in the dark. A dead one never advances. It reaches
 *       {@link #POSE_MAX} and it is on you.</li>
 *   <li>While the lamp is <b>lit</b> nothing advances -- but
 *       {@link #agitation} climbs at {@link #litRate()} per second, and at
 *       {@link #AGITATION_MAX} the unit comes off the chair whatever it
 *       was. <b>Light wakes it.</b></li>
 *   <li>In the dark, agitation bleeds off at {@link #COOL_RATE}.</li>
 *   <li>The lamp has to <b>warm</b> for {@link #LAMP_WARM} seconds before
 *       the pose is legible. A quick flash shows you nothing and costs you
 *       the same as a long look.</li>
 *   <li>The building <b>creaks</b> on its own, and a unit that advances
 *       makes a <b>drag</b>. They are the same cue, deliberately: you hear
 *       that something happened and you cannot hear what. The light is the
 *       only channel that says which, and the light is the thing you are
 *       rationing.</li>
 *   <li><b>One shock.</b> It reaches a unit that is at {@link #SHOCK_MIN}
 *       or higher -- close enough for the discharge to cross the desk. On
 *       a hostile unit that is the end of the night and the salvage is
 *       done. Below that it arcs into the chair and is gone, and a hostile
 *       unit that is still standing will get up.</li>
 * </ol>
 *
 * <h2>Why the night is two different nights</h2>
 *
 * <p>A <b>dead</b> unit never advances, so the correct play against it is
 * to stop looking and wait for six. A <b>hostile</b> unit advances, so the
 * correct play against it is to keep looking until you catch it in the
 * window and then spend the shock. <b>Those are opposite instructions and
 * the player does not know which night they are in.</b> That is the game:
 * not a puzzle with an answer, but a budget you have to spend before you
 * know what it was for.
 *
 * <p>The unit's own {@link Unit#silent} is what makes the budget hard to
 * spend well. A loud unit tells you when to look. A quiet one does not, so
 * the only defence is to look on a schedule -- and a schedule spends the
 * same lamp on a night that did not need it.
 *
 * <p>The engine is pure logic with no UI dependency, so a week can be
 * swept by a bot at 60fps with no display. That is how the difficulty
 * table was set, rather than by playing twenty nights by hand.
 */
public class Salvage {

    // ---- Clock ----
    /** Seconds per in-game hour. Six of them is the night. */
    public static final double HOUR_SECONDS = 25.0;
    public static final int NIGHT_HOURS = 6;

    // ---- The lamp ----
    /**
     * Seconds the lamp has to be on before the pose is legible.
     *
     * <p>A flash is not a look. Without this, the optimal play is to tap
     * the lamp for a single frame every few seconds, which costs almost
     * nothing and turns the whole agitation budget into a formality. With
     * it, a look has a floor price, and the floor is the unit of the
     * night.
     */
    public static final double LAMP_WARM = 0.40;

    /** Agitation at which the unit comes off the chair. */
    public static final double AGITATION_MAX = 1.0;

    /**
     * Agitation bled off per second in the dark.
     *
     * <p>Deliberately much slower than any {@link #litRate()}. If cooling
     * were competitive with lighting, agitation would be a <i>duty
     * cycle</i> -- light a third of the time forever -- and the night
     * would have no budget in it at all, only a rhythm. At this rate the
     * night is a fixed number of looks and the question is where to spend
     * them.
     */
    public static final double COOL_RATE = 0.014;

    // ---- The unit ----
    /** The pose at which it is on you. Reaching it ends the night. */
    public static final int POSE_MAX = 5;
    /**
     * The lowest pose the shock reaches.
     *
     * <p>Below this the discharge arcs into the chair. That is what makes
     * the shock a <i>decision</i> rather than a button: you have to let
     * something get up before you are allowed to destroy it, and letting
     * it get up is the thing that can kill you.
     *
     * <p><b>Four, not three, and the window is the game.</b> At three the
     * window is two advances wide -- {@code (POSE_MAX - SHOCK_MIN) *
     * advance()} -- which on the first night is thirty-two seconds, and a
     * player looking on any sane schedule cannot miss a thirty-two second
     * window. Measured at three: every policy that looked at all read
     * 100% on nights one to three, and the week had no ramp in it. At four
     * the window is exactly one advance, so the question stops being
     * "will I look at some point" and becomes "will I look <i>now</i>".
     */
    public static final int SHOCK_MIN = 4;

    /** The chance a unit is hostile at all, before the night's tables. */
    public static final double HOSTILE_CHANCE = 0.5;

    /**
     * How much a wait is allowed to vary around {@link #advance()}.
     *
     * <p><b>Without this the night is a clockwork, and the sweep said so in
     * a way that was impossible to miss: a policy that looks on a fixed
     * clock either always lands inside the window or never does.</b>
     * Measured with a regular advance, PATROL read 100% on three nights
     * and 50% on the other two, and the numbers did not move when its
     * clock was changed by a second -- because the window opens at the
     * same instant on every seed, so a fixed clock is either aligned with
     * it or not, and there is nothing in between to average.
     *
     * <p>This is the same failure FNAF 5 shipped with, one level down: a
     * deterministic threat in a game the player is supposed to learn is a
     * threat the player can solve with a stopwatch. The jitter keeps the
     * mean where the table put it and makes the window <i>move</i>, which
     * is what makes a look that answers a sound worth more than a look
     * that answers a clock.
     */
    public static final double INTERVAL_JITTER = 0.5;

    /** The poses, in the order it climbs them. */
    public enum Pose {
        SLUMPED("slumped"), STIRRING("stirring"), LEANING("leaning"),
        RISEN("risen"), STANDING("standing"), LUNGING("lunging");

        /** What the screen calls it when the lamp is on it. */
        public final String label;

        Pose(String label) { this.label = label; }

        public static Pose of(int i) {
            return values()[Math.min(Math.max(i, 0), values().length - 1)];
        }
    }

    public enum Status {
        /** Still in the chair, and the clock is still running. */
        PLAYING,
        /** It got up and reached you. */
        LUNGED,
        /** The shock landed on it while it was up. The salvage is done. */
        DESTROYED,
        /** Six AM, and it never got up. */
        SURVIVED
    }

    public final int night;
    /**
     * The thing in the chair.
     *
     * <p>Not final, and the only reason is the screen's dev hook: a
     * snapshot of night four with night one's unit in the chair is how the
     * art gets checked without playing four nights to get there.
     */
    public Unit unit;
    public final Random rng;

    // ---- Clock ----
    public double time = 0;
    public int hour = 0;

    // ---- The thing in the chair ----
    /** Whether it will get up at all. Hidden from the player. */
    public boolean hostile;
    public int pose = 0;
    public double agitation = 0;

    // ---- The lamp ----
    public boolean lit = false;
    /** Seconds the lamp has been on continuously. */
    public double litFor = 0;

    // ---- The shock ----
    public int shocks = 1;
    public boolean shocked = false;
    /** True when the shock was spent below {@link #SHOCK_MIN}. */
    public boolean wasted = false;

    // ---- Sound ----
    /**
     * Seconds since the last <b>drag</b>: the unit moving.
     *
     * <p>A drag and a creak are different sounds, and that is the design.
     * The drag is the free channel -- it tells you the pose is one higher
     * than you thought, and a player who counts them can play a loud unit
     * without spending the lamp at all. The creak is the building, and it
     * is there so that the room is never silent and a careless player has
     * something to mistake for the thing they are waiting for.
     *
     * <p>What makes the night hard is not that you cannot tell them apart.
     * It is that a unit is only <i>sometimes</i> loud: see
     * {@link Unit#silent}. A silent advance is an advance you did not
     * count, so your count drifts, and the only way to correct it is the
     * lamp -- which is the one thing the unit is waiting for.
     */
    public double dragAge = 999;
    /** Seconds since the last creak from the building. */
    public double creakAge = 999;
    /** How many noises the building has made. Read by the report. */
    public int noises = 0;
    /** How many of those were the unit. Read by the report. */
    public int drags = 0;
    /** How many advances were silent. Read by the report. */
    public int silentSteps = 0;

    // ---- Timers ----
    public double advanceTimer = 0;
    public double creakTimer = 0;
    /** Seconds since it last announced itself from the chair. */
    public double sinceCue = 0;

    // ---- Outcome ----
    public Status status = Status.PLAYING;
    public String killer = null;

    /** How many times the lamp was switched on. Read by the report. */
    public int looks = 0;

    private final List<String> cues = new ArrayList<>();

    public Salvage(int night, long seed) {
        this.night = night;
        this.rng = new Random(mix(seed));
        this.unit = Unit.forNight(night);
        this.hostile = rng.nextDouble() < HOSTILE_CHANCE;
        this.advanceTimer = drawAdvance();
        this.creakTimer = drawCreak();
    }

    /**
     * Scramble a seed before it reaches {@link Random}.
     *
     * <p><b>This is not decoration, and it was found by the sweep.</b>
     * {@code java.util.Random} is a 48-bit LCG, and the first draw from a
     * sequence of nearby seeds is strongly correlated with the seed. The
     * sweep seeds itself {@code 1000 * night + i}, so nights 1 to 3 drew
     * {@code nextDouble()} values that were all on the same side of
     * {@link #HOSTILE_CHANCE}: measured, nights 1, 2 and 3 produced
     * <b>zero</b> hostile units out of 200 seeds and nights 4 and 5
     * produced 104 and 191. The table that came back said the early nights
     * were free and the late ones were lethal, and it was a fact about
     * {@code Random}, not about the game.
     *
     * <p>A splitmix64 finalizer is the standard fix and costs nothing. The
     * general lesson is the one the FNAF 5 sweep learned twice: <b>when a
     * measurement looks like a clean story, suspect the instrument.</b>
     */
    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Dev hook: put a different unit in the chair. */
    public void forceUnit(Unit u) {
        if (u != null) this.unit = u;
    }

    // ---- The night's tables ----
    //
    // Every one of these is a method rather than a constant so the sweep
    // can be run against a variant without editing the file, and so a
    // later balance pass has one place to look. The shape of the week is
    // in the comments beside each table.

    /**
     * Seconds between a hostile unit's advances.
     *
     * <p>This is the night's <b>patience</b>: how long you have before the
     * thing in the chair is standing at the desk. It is also, and more
     * importantly, how narrow the shock window is -- the window is
     * {@code (POSE_MAX - SHOCK_MIN) * advance()}, two advances, and it
     * shrinks with the table.
     */
    public double advance() {
        double[] t = {12.0, 11.0, 10.0, 9.0, 8.0};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * Agitation gained per second while the lamp is on.
     *
     * <p>This is the night's <b>budget</b>: how many looks you can afford.
     * The week's ramp lives here more than anywhere else, because a night
     * that is generous with the lamp is a night where the answer is
     * always "look again".
     */
    public double litRate() {
        double[] t = {0.260, 0.280, 0.300, 0.330, 0.360};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * Creaks per second from the building.
     *
     * <p>The confound. A creak and a drag are the same cue, so this is the
     * rate at which the audio channel lies to you -- and the reason a
     * player cannot simply look every time they hear something.
     */
    public double creakRate() {
        double[] t = {0.030, 0.035, 0.040, 0.045, 0.050};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /** The chance one of this unit's advances makes no sound. */
    public double silent() {
        return unit.silent();
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

    /** The building settling. Nothing behind it, and it sounds like a drag. */
    void makeCreak() {
        creakAge = 0;
        noises++;
        cue(rng.nextBoolean() ? "creak_a" : "creak_b");
    }

    /** The unit moving. This is the free channel, and it is worth counting. */
    void makeDrag() {
        dragAge = 0;
        noises++;
        drags++;
        cue("drag");
    }

    /**
     * One wait, drawn around {@link #advance()}.
     *
     * <p>Drawn once per wait rather than per frame, for the reason FNAF 5
     * wrote down: a per-frame draw biases every move early, because the
     * effective wait becomes the smallest draw seen so far.
     */
    double drawAdvance() {
        return advance() * (1.0 + INTERVAL_JITTER * (rng.nextDouble() - 0.5));
    }

    /** An exponential draw, so creaks are not a metronome. */
    double drawCreak() {
        double r = Math.max(1e-6, rng.nextDouble());
        return -Math.log(r) / creakRate();
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

        dragAge += dt;
        creakAge += dt;
        sinceCue += dt;

        // The lamp. Agitation is the price of looking, and it is charged
        // whether or not the look told you anything.
        if (lit) {
            litFor += dt;
            agitation += litRate() * dt;
            if (agitation >= AGITATION_MAX) {
                agitation = AGITATION_MAX;
                pose = POSE_MAX;
                lunge();
                return;
            }
        } else {
            litFor = 0;
            agitation = Math.max(0, agitation - COOL_RATE * dt);
        }

        // The building. A creak is a noise with nothing behind it, and it
        // is the reason a noise is evidence rather than proof.
        creakTimer -= dt;
        if (creakTimer <= 0) {
            creakTimer = drawCreak();
            makeCreak();
        }

        // The thing in the chair. A dead unit never advances -- that is
        // the entire difference between the two nights, and it is hidden.
        if (hostile) {
            advanceTimer -= dt;
            if (advanceTimer <= 0) {
                advanceTimer = drawAdvance();
                pose++;
                if (rng.nextDouble() < silent()) {
                    silentSteps++;
                } else {
                    makeDrag();
                }
                if (pose >= POSE_MAX) {
                    lunge();
                    return;
                }
            }
        }

    }

    void lunge() {
        if (status != Status.PLAYING) return;
        status = Status.LUNGED;
        killer = unit.name();
        cue("lunge");
    }

    // ---- Controls ----
    // Every one of these is a no-op once the night is over.

    /** True when the pose is legible: the lamp is on and has warmed. */
    public boolean revealed() {
        return lit && litFor >= LAMP_WARM;
    }

    /** The pose, or null when the lamp is off or still warming. */
    public Pose seen() {
        return revealed() ? Pose.of(pose) : null;
    }

    /** Turn the lamp on or off. Returns the new state. */
    public boolean toggleLamp() {
        if (status != Status.PLAYING) return lit;
        lit = !lit;
        if (lit) {
            litFor = 0;
            looks++;
            cue("lamp_on");
        } else {
            cue("lamp_off");
        }
        return lit;
    }

    /**
     * The controlled shock.
     *
     * <p>One discharge, and it only reaches a unit that is at
     * {@link #SHOCK_MIN} or higher. Below that it arcs into the chair: the
     * shock is spent, nothing is destroyed, and if the thing in the chair
     * was going to get up it still is.
     *
     * @return true when the discharge landed on a unit that was up
     */
    public boolean shock() {
        if (status != Status.PLAYING) return false;
        if (shocks <= 0) return false;
        shocks--;
        shocked = true;
        cue("shock");
        if (pose >= SHOCK_MIN) {
            status = Status.DESTROYED;
            killer = unit.name();
            cue("shock_hit");
            return true;
        }
        wasted = true;
        cue("shock_miss");
        return false;
    }

    // ---- What the screen asks ----

    /** True when the thing in the chair is up and the night is not over. */
    public boolean up() {
        return pose >= SHOCK_MIN;
    }

    /** True when the shock would land right now. */
    public boolean canShock() {
        return status == Status.PLAYING && shocks > 0;
    }

    /** The clock, as the band draws it. */
    public String clock() {
        return switch (Math.min(hour, 6)) {
            case 0 -> "12 AM"; case 1 -> "1 AM"; case 2 -> "2 AM";
            case 3 -> "3 AM"; case 4 -> "4 AM"; case 5 -> "5 AM";
            default -> "6 AM";
        };
    }
}
