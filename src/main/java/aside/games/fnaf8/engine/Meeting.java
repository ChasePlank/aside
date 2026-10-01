package aside.games.fnaf8.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNAF 8 core loop: two doorways, one lamp, and two things that are not
 * coming for you.
 *
 * <p>FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 gave you
 * a body that can only be in one place. FNAF 5 gave you a building and took
 * the room you were standing in. FNAF 6 gave you one chair, one lamp and one
 * shock, and took away the idea that a look is free. FNAF 7 gave you two
 * doors, one bar and one light, and took away the idea that you are a
 * stranger to it. FNAF 8 gives you <b>two doorways and one lamp</b> -- and
 * then takes away the thing all seven of them assumed, which is that
 * <i>you are what it is coming for.</i>
 *
 * <pre>
 *   THE RULE: they are not coming for you. They are coming for each other.
 *             You are where they meet.
 * </pre>
 *
 * <h2>The board</h2>
 *
 * <p>Two units walk up two halls toward two doorways. Each has a distance
 * {@code d} in {@code 0..MAX} steps, where <b>0 is standing in the
 * doorway</b>. They do not attack you. They do not want you. Each one is
 * walking toward the office because the other one is, and <b>the night ends
 * the moment both of them are standing in a doorway at the same time.</b>
 * That is the only death in the game.
 *
 * <h2>The lamp, and the one rule that makes it a game</h2>
 *
 * <p>One lamp, on a swivel, and it does two jobs: it shows you a hall, and
 * it pushes back whatever is in it. It reaches <b>the hall</b>. It does not
 * reach <b>the doorway</b>, and that is the load-bearing rule of the whole
 * night:
 *
 * <pre>
 *   You cannot get rid of the one standing in your door.
 *   You can only push its partner away, and wait for it to lose interest.
 * </pre>
 *
 * <p>So the obvious play -- point the lamp at the thing in the doorway --
 * is a trap, and the play that works is the one the title is about: <b>turn
 * around the one that is still coming, and the one at the door gives up on
 * its own.</b> A unit stands in a doorway for {@link #patience()} seconds
 * and then walks back down the hall. If its partner arrives before that,
 * they meet.
 *
 * <h2>What the lamp costs</h2>
 *
 * <p>The lamp is on one side or the other and there is one of it, so <b>the
 * side you are not looking at is the side that is moving.</b> Dim is a
 * look: it shows the hall and the unit in it, and the other one gains
 * {@link #DIM_RUSH}. Bright is a hand: it shows the hall <i>and</i> pushes
 * the unit back a step every {@link #retreat()}, and the other one gains
 * {@link #BRIGHT_RUSH} instead. And moving the lamp from one hall to the
 * other takes {@link #SWIVEL} seconds, during which it is on neither.
 *
 * <p>Those three numbers are the whole economy. {@code retreat < pace/CALL
 * < pace/DIM_RUSH} is the regime the game is tuned in, and it says: <b>the
 * beam beats the call, and the call beats a look.</b> Outside that band the
 * night stops being a night -- if the beam cannot out-push the call, then
 * the moment one of them reaches a doorway the other one is already lost,
 * and if a look costs nothing then the lamp is always already up.
 *
 * <h2>Why it is pure logic</h2>
 *
 * <p>No UI dependency, so a week can be swept by a bot at 60fps with no
 * display. That is how the difficulty table was set, rather than by playing
 * twenty nights by hand -- and the first sweep of this game is what moved
 * {@link #CALL} from a constant to a table, because a constant made night
 * one and night five the same night with different clocks.
 */
public class Meeting {

    // ---- Clock ----
    /** Seconds per in-game hour. Six of them is the night. */
    public static final double HOUR_SECONDS = 25.0;
    public static final int NIGHT_HOURS = 6;

    // ---- The board ----
    /**
     * Steps from the doorway. 0 is standing in it, {@code MAX} is the far
     * end of the hall.
     *
     * <p>Five, and it is five rather than ten because the player has to
     * hold both numbers in their head and compare them against a lamp that
     * can only be in one place. A longer hall is not a harder night, it is
     * a longer walk between the two decisions.
     */
    public static final int MAX = 5;

    /**
     * How close the two of them have to get before they see each other.
     *
     * <p>Measured as the <b>sum of the two distances from the doors</b>,
     * because the two of them are in different halls and never occupy the
     * same space until a doorway -- so "how close are they" is really "how
     * far into the building are they, together."
     */
    public static final int MEET = 3;

    /**
     * The furthest step the beam can push from.
     *
     * <p>A unit at the far end of its hall is out of the lamp's reach: you
     * can see it and you cannot move it. That is not a limitation of the
     * lamp, it is the reason the night has a shape -- <b>a unit you can
     * park at the far end is a unit that is not a problem, and a night
     * where one of the two is never a problem is a night with one unit in
     * it.</b> The first sweep of this game said so in the only way that
     * could not be argued with: with the whole hall in reach, every policy
     * that did anything survived every seed of every night.
     */


    // ---- The lamp ----
    /**
     * Seconds the lamp takes to cross from one hall to the other.
     *
     * <p>While it is crossing it is on neither, so this is the price of
     * changing your mind and it is the reason the night cannot be played by
     * alternating. A lamp that could be in both places is a lamp that
     * answers both questions, and the answer to both questions is not a
     * game.
     */
    public static double SWIVEL = 1.10;

    // ---- Speed ----
    /**
     * The unlit one's speed multiplier while the lamp is <b>dim</b> on the
     * other side.
     *
     * <p>Dim is a look, and a look is nearly free: the other one is in the
     * dark and knows it, and walks a little faster for it. Nearly free, and
     * not free -- that gap is what stops the night being a slideshow.
     */
    public static double DIM_RUSH = 1.15;

    /**
     * The unlit one's speed multiplier while the lamp is <b>bright</b> on
     * the other side.
     *
     * <p>Bright is a hand, and a hand is loud. This is the number that
     * makes the rescue a decision rather than a reflex: pushing one of them
     * back is the most expensive thing you can do to the other one.
     */
    public static double BRIGHT_RUSH = 1.42;

    /**
     * How much a step's interval is allowed to wander, as a fraction.
     *
     * <p>Zero would make the night a timetable: every unit would arrive on
     * a schedule the player could compute once and then never think about
     * again, and the lamp would stop being eyes and start being a hand. At
     * 0.5 a step takes anywhere from three quarters to five quarters of its
     * nominal time, so <b>a belief about an unlit hall is a belief that
     * drifts</b> -- and the drift is what the lamp is spent against.
     *
     * <p>It is also what makes the sweep honest. The bot walks its belief
     * forward at the <i>mean</i> rate, exactly as a person would, so the
     * number the ladder is read off is a number about a player who is
     * guessing, not about one who can see through the wall.
     */
    public static final double JITTER = 0.5;

    /** The two sides of the office. */
    public enum Side {
        LEFT("left", "LEFT"), RIGHT("right", "RIGHT");

        /** As a sentence uses it. */
        public final String quiet;
        /** As the readout shouts it. */
        public final String shout;

        Side(String quiet, String shout) {
            this.quiet = quiet;
            this.shout = shout;
        }

        public Side other() { return this == LEFT ? RIGHT : LEFT; }
    }

    /** What the lamp is doing. */
    public enum Setting {
        /** On neither hall. Both of them walk. */
        OFF,
        /** Showing a hall. The other one gains {@link #DIM_RUSH}. */
        DIM,
        /** Showing a hall and pushing it back. The other gains {@link #BRIGHT_RUSH}. */
        BRIGHT
    }

    public enum Status {
        /** Still playing, and the clock is still running. */
        PLAYING,
        /** Both of them stood in a doorway at the same time. */
        MET,
        /** Six AM, and they never found each other. */
        SURVIVED
    }

    public final int night;
    public final Pair pair;
    public final Random rng;

    // ---- Clock ----
    public double time = 0;
    public int hour = 0;

    // ---- The board ----
    /** Steps from the doorway, per side. Indexed by {@link Side#ordinal()}. */
    public final int[] d = new int[]{MAX, MAX};
    /** Seconds to this side's next step. */
    public final double[] timer = new double[]{0, 0};
    /** Seconds this side has been standing in its doorway, or 0. */
    public final double[] waited = new double[]{0, 0};

    // ---- The lamp ----
    public Side lampSide = Side.LEFT;
    public Setting lamp = Setting.OFF;
    /** Seconds left in a swivel. While > 0 the lamp is on neither hall. */
    public double swivel = 0;
    /** Where the lamp is going, while it is going there. */
    public Side swivelTo = null;
    /**
     * What the lamp was asked to do when it arrives.
     *
     * <p>Kept rather than re-asked, because a swivel is a physical move and
     * the player who started it has already spent the time. The first
     * version of this dropped the setting on the floor: the lamp crossed,
     * arrived, and came on at nothing, so a player who aimed once and
     * waited got a lamp that was pointed at a hall and switched off. The
     * sweep never saw it because the bot re-aims every frame, which is
     * exactly the shape of bug a bot cannot find.
     */
    public Setting swivelSetting = Setting.DIM;

    // ---- Outcome ----
    public Status status = Status.PLAYING;
    /** The second of the night they met, or -1. */
    public int metAt = -1;
    /** The side the second one arrived on, or null. */
    public Side metSide = null;

    // ---- Counters, for the report ----
    /** Times a unit reached a doorway. */
    public int arrivals = 0;
    /** Times a unit gave up and walked back down the hall. */
    public int gaveUp = 0;
    /** Steps taken while being pushed back. */
    public int pushed = 0;
    /** Steps taken toward the office. */
    public int steps = 0;
    /** Times the lamp crossed from one hall to the other. */
    public int switches = 0;
    /**
     * The last side that stepped, and which way.
     *
     * <p>Kept for the report and for the jumpscare's framing, and
     * <b>deliberately not turned into a cue name.</b> FNAF 7's whole audio
     * channel was one distinction -- a step on the left against a step on
     * the right -- and this game has no directional channel at all: the
     * lamp is the only way to know where either of them is, and a footfall
     * that said which hall it came from would hand the player that
     * knowledge for free. So both halls emit the same cue, and the check
     * that keeps it that way is in SelfTest.
     */
    public Side lastStepSide = null;
    public boolean lastStepBack = false;

    private final List<String> cues = new ArrayList<>();

    public Meeting(int night, long seed) {
        this.night = night;
        this.rng = new Random(mix(seed));
        this.pair = Pair.forNight(night);
        timer[0] = nextInterval(Side.LEFT);
        timer[1] = nextInterval(Side.RIGHT);
    }

    /**
     * Scramble a seed before it reaches {@link Random}.
     *
     * <p>{@code java.util.Random} is a 48-bit LCG and the first draw from a
     * sequence of nearby seeds is strongly correlated with the seed. FNAF 6
     * shipped that bug and its sweep found it, and every game since has
     * carried the fix. A splitmix64 finalizer costs nothing.
     */
    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // ---- The night's tables ----
    // Every one of these is a method rather than a constant so the sweep can
    // be run against a variant without editing the file, and so a later
    // balance pass has one place to look.

    /**
     * Seconds a unit takes to walk one step toward the office.
     *
     * <p>This is the night's <b>tempo</b>: how long a look buys you, and
     * therefore how many decisions fit in a night. A short step is a night
     * where one bad habit costs more.
     */
    public double pace() {
        double[] t = {2.30, 2.34, 2.50, 2.58, 2.58};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * Seconds the bright lamp takes to push a unit one step back.
     *
     * <p>This is the night's <b>leverage</b>, and it is the number the
     * whole economy hangs off. It has to be shorter than {@code pace/CALL}
     * or the beam cannot rescue a doorway, and it has to be longer than
     * {@code pace/(DIM_RUSH*2)} or the player can hold both of them off
     * with one hand and the night is a chore.
     */
    public double retreat() {
        double[] t = {1.15, 1.15, 1.18, 1.20, 1.22};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * The other one's speed multiplier while this one stands in a doorway.
     *
     * <p>A unit in a doorway is not waiting for you. It is <b>calling</b>,
     * and the call is what turns the night's one mistake into the night's
     * only mistake: the moment one of them arrives, the other one stops
     * being a problem you have time for.
     *
     * <p>A table rather than a constant, and the first sweep is why. As a
     * constant, night one and night five were the same night with different
     * clocks -- the ramp lived entirely in {@code pace()}, and {@code pace}
     * moves both sides at once, so it moved the difficulty and the rescue
     * together and cancelled itself out. The call is the dial that only
     * moves the rescue.
     */
    public double call() {
        double[] t = {1.45, 1.48, 1.51, 1.54, 1.57};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * Seconds a unit stands in a doorway before it gives up and walks back.
     *
     * <p>This is the night's <b>patience</b>, and it is the clock the whole
     * rescue runs against. It is also the answer to the obvious exploit:
     * without it, a player could park one of them in a doorway forever and
     * spend the night on the other one.
     */
    public double patience() {
        double[] t = {5.4, 5.2, 5.0, 4.8, 4.6};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    // ---- Reading the board ----

    /** True when the lamp is on this hall right now. */
    public boolean lit(Side s) {
        return lamp != Setting.OFF && swivel <= 0 && lampSide == s;
    }

    /** True when the lamp is on this hall and pushing. */
    public boolean pushing(Side s) {
        return lamp == Setting.BRIGHT && swivel <= 0 && lampSide == s;
    }

    /**
     * True when the lamp is showing this hall.
     *
     * <p>Halls only, and the doorway is not one. A lamp that also showed the
     * doorway would be a lamp that answers the question after the answer is
     * useless -- the thing in the doorway is already the loudest fact in the
     * office, and it is drawn as a pair of eyes in the dark rather than as a
     * figure under the beam, because that is what it is.
     */
    public boolean visible(Side s) {
        return lit(s) && d[s.ordinal()] > 0;
    }

    /** True when this side has something standing in its doorway. */
    public boolean waiting(Side s) {
        return d[s.ordinal()] == 0;
    }

    /** Steps from the doorway, 0..MAX. */
    public int distance(Side s) { return d[s.ordinal()]; }

    /** Seconds this side has been standing in its doorway. */
    public double waited(Side s) { return waited[s.ordinal()]; }

    /** How much of its patience a waiting unit has left, 0..1. */
    public double patienceLeft(Side s) {
        if (!waiting(s)) return 1;
        return Math.max(0, 1 - waited[s.ordinal()] / patience());
    }

    /** The clock, as the band draws it. */
    public String clock() {
        return switch (Math.min(hour, 6)) {
            case 0 -> "12 AM"; case 1 -> "1 AM"; case 2 -> "2 AM";
            case 3 -> "3 AM"; case 4 -> "4 AM"; case 5 -> "5 AM";
            default -> "6 AM";
        };
    }

    // ---- Sound ----

    public void cue(String name) { cues.add(name); }

    public List<String> drainCues() {
        if (cues.isEmpty()) return List.of();
        List<String> out = List.copyOf(cues);
        cues.clear();
        return out;
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

        // The lamp, crossing.
        if (swivel > 0) {
            swivel -= dt;
            if (swivel <= 0) {
                swivel = 0;
                lampSide = swivelTo;
                swivelTo = null;
                lamp = swivelSetting;
                cue("f8_set");
            }
        }

        // The doorways. A unit that has stood in one for its whole patience
        // loses interest -- and that is the only way a doorway ever empties,
        // because the lamp does not reach it.
        for (Side s : Side.values()) {
            int i = s.ordinal();
            if (d[i] != 0) { waited[i] = 0; continue; }
            waited[i] += dt;
            if (waited[i] >= patience()) {
                d[i] = MAX;
                waited[i] = 0;
                timer[i] = nextInterval(s);
                gaveUp++;
                cue("f8_gives_up");
                cue("f8_back");
            }
        }

        // The two of them.
        for (Side s : Side.values()) {
            int i = s.ordinal();
            timer[i] -= dt;
            if (timer[i] > 0) continue;
            step(s);
            timer[i] = nextInterval(s);
        }

        // The only death in the game.
        if (apart() <= MEET) {
            status = Status.MET;
            metAt = (int) time;
            metSide = lastStepSide;
            cue("f8_met");
        }
    }

    /** One step for one unit, in whichever direction it is going. */
    void step(Side s) {
        int i = s.ordinal();
        if (pushing(s)) {
            // The beam reaches the hall and not the doorway. A unit
            // standing in a doorway is in the frame, and the frame is the
            // one place in this office the lamp does not get to.
            if (d[i] > 0 && d[i] < MAX) {
                d[i]++;
                pushed++;
                lastStepSide = s;
                lastStepBack = true;
                cue("f8_push");
            }
            return;
        }
        if (d[i] > 0) {
            d[i]--;
            steps++;
            lastStepSide = s;
            lastStepBack = false;
            cue("f8_step");
            if (d[i] == 0) {
                arrivals++;
                cue("f8_door");
            }
        }
    }

    /**
     * Seconds to this side's next step, given everything else.
     *
     * <p>The one place the whole economy lives. Three things can be true of
     * a unit at once and they do not stack: <b>the beam beats the call, and
     * the call beats a look.</b> Taking the largest effect rather than the
     * product is deliberate -- a product would let two mild multipliers
     * outrun a beam that is supposed to beat both of them, and the regime
     * the game is tuned in would stop being a regime.
     */
    public double interval(Side s) {
        if (pushing(s)) return retreat() * pair.unit(s).stubborn();
        double mult = 1.0;
        if (lit(s.other())) {
            mult = Math.max(mult, lamp == Setting.BRIGHT ? BRIGHT_RUSH : DIM_RUSH);
        }
        if (waiting(s.other())) mult = Math.max(mult, call());
        return pace() / pair.unit(s).speed() / mult;
    }

    /**
     * The same, with the night's jitter on it.
     *
     * <p>Separate from {@link #interval} on purpose. {@code interval} is the
     * <i>mean</i>, and it is what the bot is allowed to know; this one
     * draws, and it is what the game actually does. If they were the same
     * method the bot's dead reckoning would consume the game's randomness
     * and the sweep would be measuring a bot that had quietly taken the
     * wheel.
     */
    double nextInterval(Side s) {
        return interval(s) * (1.0 + JITTER * (rng.nextDouble() - 0.5));
    }

    // ---- Controls ----
    // Every one of these is a no-op once the night is over.

    /**
     * Point the lamp at a hall, at a setting.
     *
     * <p>Aiming at the hall it is already on is instant -- the setting
     * changes and nothing else does. Aiming at the other hall starts a
     * swivel, and the lamp is dark for the whole of it. That asymmetry is
     * the game's only rule about your hands: <b>you can change what the
     * lamp is doing for free, and you cannot change where it is.</b>
     *
     * @return true when the lamp did something
     */
    public boolean aim(Side s, Setting setting) {
        if (status != Status.PLAYING) return false;
        if (setting == Setting.OFF) return dark();
        if (swivel > 0) return false;
        if (lampSide == s) {
            if (lamp == setting) return false;
            lamp = setting;
            cue(setting == Setting.BRIGHT ? "f8_bright" : "f8_dim");
            return true;
        }
        swivel = SWIVEL;
        swivelTo = s;
        swivelSetting = setting;
        lamp = Setting.OFF;
        switches++;
        cue("f8_swivel");
        return true;
    }

    /** Put the lamp out. Cancels a swivel in progress. */
    public boolean dark() {
        if (status != Status.PLAYING) return false;
        boolean was = lamp != Setting.OFF || swivel > 0;
        lamp = Setting.OFF;
        swivel = 0;
        swivelTo = null;
        if (was) cue("f8_off");
        return was;
    }

    /**
     * The side the lamp is on, or null while it is crossing.
     *
     * <p>Drawn as the readout, and it is the only place the state of the
     * lamp is written down. Everything else in the office is a fact about
     * the units; this is the one fact about you.
     */
    public Side lampAt() {
        return swivel > 0 ? null : (lamp == Setting.OFF ? null : lampSide);
    }

    /**
     * How close they are to each other, as the office measures it.
     *
     * <p>Not a distance -- the two of them are in different halls and never
     * occupy the same space until the doorway. It is <b>the sum of the two
     * distances from the doors</b>, and it is the number the whole night is
     * spent keeping above zero: at 0 they are both in a doorway, and a
     * doorway is the one place in this building the two of them can be.
     */
    public int apart() { return d[0] + d[1]; }
}
