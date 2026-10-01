package aside.games.fnaf9.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNAF 9 core loop: two halls, one door, one monitor, one light, and one
 * circuit between all of it.
 *
 * <p>FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors. FNAF 4 gave you a
 * body that can only be in one place. FNAF 5 gave you a building and took the
 * room you were standing in. FNAF 6 gave you one chair, one lamp and one
 * shock, and took away the idea that a look is free. FNAF 7 gave you two
 * doors, one bar and one light, and took away the idea that you are a stranger
 * to it. FNAF 8 gave you two doorways and one lamp, and took away the idea
 * that either of them wants you. FNAF 9 gives you <b>two halls, one door and
 * one monitor</b> -- and then takes away the idea that <i>the picture is
 * now.</i>
 *
 * <pre>
 *   THE RULE: the picture is as old as the time you have spent watching it.
 * </pre>
 *
 * <h2>The board</h2>
 *
 * <p>Two halls, {@code MAX} steps long, and they both end at <b>the same
 * door</b>. That is the shape of this office and it is the first thing that
 * separates it from FNAF 8: there, two halls meant two doorways and the whole
 * night was the distance between them. Here there is one doorway, and the two
 * halls are <b>two ways to arrive at it</b> -- so the question is never
 * <i>which door</i>, it is <i>which hall</i>, and there is only one answer to
 * give.
 *
 * <p>One walker in each hall. And one circuit, which is the only thing in the
 * office that is yours:
 *
 * <pre>
 *   DARK        nothing on. You see nothing, and the feed catches up.
 *   MON LEFT    the monitor, on the left hall. You see a hall, and it is old.
 *   MON RIGHT   the same, on the right hall.
 *   HOLD        the door, shut. You see nothing at all.
 * </pre>
 *
 * <p><b>One circuit, one thing at a time, and exactly one live channel --
 * and the live channel is not a camera.</b> That split is the whole shape of
 * this office. Every other game in the franchise gives you a live view of the
 * thing that is coming: FNAF 1 has a door light, FNAF 3 has a window, FNAF 6,
 * 7 and 8 each have a lamp. FNAF 9 has a monitor that is always behind, and
 * {@link #sensor()} -- <b>a contact sensor mounted on the door itself,
 * which is live, free, one bit, and only reads while the door is down.</b>
 *
 * <p>The mounting is the point. The sensor says <i>something is against the
 * door right now</i>, and it says nothing about anything else -- not which
 * hall, not how far, not how long it has been there -- and because it is on
 * the door it says nothing at all until the door is down. <b>So it cannot be
 * used to start anything.</b> A walker the sensor could announce is a walker
 * whose decision has already been made. What it is for is the other end: it is
 * the only way to know the doorway has <i>emptied</i>, which is the only way
 * to know when it is safe to let go of the door and go back to looking.
 *
 * <p>So the two channels divide cleanly and neither one is enough.
 * <b>The monitor tells you when to shut the door and cannot tell you when to
 * open it. The sensor tells you when to open it and cannot tell you when to
 * shut it.</b> A player who leans on the sensor is always late; a player who
 * leans on the monitor never knows when to let go.
 *
 * <p><b>Half of that is measured and half of it is not.</b> The first half is
 * solid: a policy that releases on the sensor alone dies immediately, because
 * the door finishes coming down before the walker it was shut for has arrived
 * and the doorway is genuinely empty at that moment. The second half is the
 * one the sweep does not support. A policy that holds until the sensor has
 * <i>seen</i> something and then gone clear -- the play this paragraph
 * describes -- scores <b>13% on the week</b> against the competent policy's
 * 61%, because the sensor says the doorway is empty and says nothing about
 * the hall behind it, and a release into a hall nobody has looked at is the
 * move that kills. What actually decides the night is the <i>belief</i>: the
 * walker's patience is a constant, so a player who has been keeping a hall in
 * their head knows when the doorway will empty without asking the door. That
 * is the open design item, and it is why the extended hold beats the competent
 * player -- see {@link aside.games.fnaf9.engine.Bot#SIEGE_AT}.
 *
 * <p>A live view of the doorway was here and it was removed, and the sweep is
 * why. With one, the night stops being a night: a player who keeps it on sees
 * every arrival the moment it happens, and a walker in a doorway is a walker
 * you have {@link #grace()} seconds to answer, which is plenty. Measured, that
 * policy scored <b>100% on all five nights</b> while the competent one scored
 * zero -- and a game whose best play is to ignore the thing it is about is not
 * a game about that thing.
 *
 * <h2>The feed, which is the whole game</h2>
 *
 * <p>{@link #feed} is the age of the picture, in seconds. It <b>grows while
 * the monitor is on</b> at {@link #ageRate()} and <b>falls while it is off</b>
 * at {@link #ageDecay()}. The monitor is a delay line, not a filter: what is
 * drawn is the hall as it was {@code feed} seconds ago, exactly. So the
 * consequences are not cosmetic, they are arithmetic:
 *
 * <ul>
 *   <li><b>Things in a stale picture appear further away than they are.</b>
 *       A walker two steps from the door, seen through a four-second-old
 *       picture on a night whose step is two seconds, is drawn four steps out.
 *       You will not shut the door, because the picture says you have
 *       time.</li>
 *   <li><b>Things in a stale picture appear not to move.</b> The apparent
 *       speed of everything on the screen is the real speed scaled by how far
 *       behind the picture is. At {@link #AGE_MAX} the picture is six seconds
 *       old and the hall is a photograph. <i>Staring at the monitor is the one
 *       thing that makes the monitor look safe.</i></li>
 *   <li><b>And past its own {@link Walker#sharp()}, a walker is not drawn at
 *       all.</b> Not faint, not misplaced -- absent. A player who has been
 *       watching for three seconds is looking at an empty hall, and an empty
 *       hall is the most dangerous thing this office can show.</li>
 * </ul>
 *
 * <p>That third one is the trap, and it is why the night cannot be played by
 * keeping the monitor up. The obvious play -- <i>watch the halls so nothing
 * can surprise you</i> -- is the play that empties the halls, and the night is
 * built so that the player who does it dies to a room they have been staring
 * at.
 *
 * <h2>The door, and why it will not stay shut</h2>
 *
 * <p>The door is the only thing in the office that actually stops anything,
 * and it is the only thing in the office with a <b>duty cycle</b>. Holding it
 * runs {@link #heat} up at one second per second; letting go runs it down at
 * {@link #COOL}; and at {@link #HOLD_MAX} the mechanism lets go on its own and
 * will not take another hold until it has cooled past {@link #REARM}. So the
 * door can be shut for {@link #HOLD_MAX} seconds and then it needs
 * {@code HOLD_MAX / COOL} -- about three and a half -- seconds open. (This
 * said "about three seconds and then about three seconds open" for as long as
 * the constants have said eight and 2.2. The numbers it describes are the
 * constants, and the constants are what the night is tuned against.)
 *
 * <p>That number is not decoration. Without it the night is trivial and the
 * first sweep said so in one line: <b>a door that can be held forever is a
 * door you hold from the first arrival to six AM</b>, and a night where the
 * correct play is to shut the door at 12:10 and open it at 5:50 is not a night
 * at all. The duty cycle is what makes the question <i>when</i> rather than
 * <i>whether</i> -- and <i>when</i> is the only question the feed can answer.
 *
 * <h2>The light, and why it is not a free answer</h2>
 *
 * <p>The office light shows <b>the doorway, live</b>. It is the only truthful
 * thing in the office. It is also narrow: it tells you a walker has arrived
 * and nothing else, and a walker that has arrived is a walker you have
 * {@link #grace()} seconds to answer. On night one that is comfortable. By
 * night five the grace is shorter than a person takes to move, and <b>a player
 * who only ever uses the light is a player who is always reacting to the last
 * possible moment.</b>
 *
 * <p>So the two channels are not a choice between good and bad. The monitor is
 * early and wrong; the light is late and right.
 *
 * <h2>Why it is pure logic</h2>
 *
 * <p>No UI dependency, so a week can be swept by a bot at 60fps with no
 * display -- which is how the tables below were set, rather than by playing
 * twenty nights by hand. The bot is not allowed to read {@link #d} or
 * {@link #d}; it reads what the monitor draws and what the sensor says,
 * which is exactly what a player has.
 */
public class Feed {

    // ---- Clock ----
    /** Seconds per in-game hour. Six of them is the night. */
    public static final double HOUR_SECONDS = 25.0;
    public static final int NIGHT_HOURS = 6;

    // ---- The board ----
    /**
     * Steps from the doorway. 0 is standing in it, {@code MAX} is the far end.
     *
     * <p>Six, and it is six rather than ten for the same reason FNAF 8's hall
     * is five: the player has to hold a position in their head and compare it
     * against a picture that is lying to them, and a longer hall is not a
     * harder night, it is a longer walk between two decisions.
     */
    public static final int MAX = 6;

    /**
     * How much a step's interval is allowed to wander, as a fraction.
     *
     * <p>Zero would make the night a timetable, and a timetable is a night the
     * monitor is not needed for: a player could compute every arrival once and
     * then close their eyes. At 0.5 a step takes anywhere from three quarters
     * to five quarters of its nominal time, so <b>a belief about a hall you
     * are not looking at is a belief that drifts</b> -- and the drift is what
     * the monitor is spent against.
     *
     * <p>It is also what makes the sweep honest. The bot walks its belief
     * forward at the <i>mean</i> rate, exactly as a person would, so the
     * number the ladder is read off is a number about a player who is
     * guessing, not about one who can see through the wall.
     */
    public static final double JITTER = 0.5;

    /**
     * The oldest the picture can get.
     *
     * <p>Six seconds, and the number is chosen against the night rather than
     * against a feeling: the slowest night's step is 2.55 seconds, so six
     * seconds of lag is <b>two and a bit steps</b> of error -- enough that a
     * walker at the door is drawn a third of the way down the hall, and not so
     * much that the picture stops being a picture of the same night.
     */
    public static final double AGE_MAX = 6.0;

    /**
     * Seconds the door takes to come down.
     *
     * <p>Longer than {@link #grace()} on every night of the week, and that
     * inequality is the single most load-bearing number in the game: it is
     * what makes the sensor a readout instead of a trigger. If the door could
     * come down inside the grace, then a player watching the sensor would
     * never be too late, and the monitor -- and the whole idea of a picture
     * that is behind -- would be decoration.
     */
    public static final double SHUT_TIME = 0.80;

    /**
     * How long the door will stay shut before the mechanism lets go.
     *
     * <p>Seconds of held time. It has to be longer than the longest
     * {@link Walker#patience()} in the cast <i>plus the walk the door is shut
     * for before the walker gets there</i> <i>plus</i> {@link #SHUT_TIME}, or
     * a walker could outlast the door and the door would be a lie; it has to
     * be short enough that the night is a series of decisions rather than one
     * decision. Eight seconds against a patience table that tops out at 2.0, a
     * door that takes a second to come down, and a hold that starts about a
     * step out -- which leaves the player a window of roughly two steps to
     * start a hold in rather than the tenth of a step the first numbers left,
     * and a tenth of a step is not a decision, it is a coincidence.
     *
     * <p>(This comment said six for as long as the constant has said eight.
     * The number it describes is the constant, and the constant is what the
     * night is tuned against -- the sweep reproduces the shipped ladder at
     * 8.0 and at nothing else.)
     *
     * <p>The margin is measured rather than asserted: the longest any walker
     * in the cast can ask for is 7.6s, on night one, where the step is the
     * slowest in the week and the patience is nearly the longest. That is
     * close enough to the ceiling that it is worth a check, and
     * {@code SelfTest} fails if it ever crosses.
     */
    public static final double HOLD_MAX = 8.0;

    /**
     * How fast the door mechanism sheds heat, in seconds of hold per second.
     *
     * <p>Above one, so the door is a duty cycle and not a resource you spend:
     * six seconds of hold costs under four seconds of open door, which is
     * about half of one walker's approach. That ratio is what makes <b>holding
     * the door at the wrong moment as expensive as not holding it at the right
     * one.</b>
     */
    public static final double COOL = 2.20;

    /**
     * The heat below which the door will take another hold.
     *
     * <p>A fraction of {@link #HOLD_MAX}, and it is a fraction rather than
     * zero on purpose: without the hysteresis the door would be re-armable on
     * the exact frame it let go, and a player could hold it in a stutter that
     * is neither shut nor open. The gap is what makes letting go a decision.
     */
    public static final double REARM = 0.35;

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

    /**
     * What the one circuit is doing.
     *
     * <p>Five states and one of them at a time, and every one of them is a
     * trade rather than a tool:
     *
     * <pre>
     *   DARK        nothing. The feed catches up at full speed.
     *   MON LEFT    a hall, wide and old.
     *   MON RIGHT   the same.
     *   HOLD        the door, shut, and you are blind.
     * </pre>
     *
     * <p>{@link #DARK} is a real state rather than the absence of one: it is
     * what you are in when you have turned everything off to let the feed
     * catch up, and it is the state the competent player spends most of the
     * night in.
     */
    public enum Circuit {
        DARK,
        MON_LEFT,
        MON_RIGHT,
        HOLD
    }

    public enum Status {
        /** Still playing, and the clock is still running. */
        PLAYING,
        /** Something walked in through the doorway. */
        TAKEN,
        /** Six AM, and nothing did. */
        SURVIVED
    }

    public final int night;
    public final Pair pair;
    public final Random rng;

    // ---- Clock ----
    public double time = 0;
    public int hour = 0;

    // ---- The board ----
    /**
     * Distance from the doorway, per hall, in steps. Indexed by {@link
     * Side#ordinal()}. 0 is standing in the doorway.
     *
     * <p><b>Continuous, not a step count, and that is a design decision the
     * sweep forced.</b> A walker that moves in whole steps is a walker whose
     * picture carries no information about <i>where in its step</i> it was
     * when the picture was taken -- so a walker drawn one step out might be
     * one step out or a hundredth of a step out, and the difference between
     * those two is the difference between having time to bring the door down
     * and not. Measured, that ambiguity is what made every policy on the
     * ladder unplayable: the belief was systematically optimistic by up to a
     * full step, and a full step is longer than the grace.
     *
     * <p>Moving smoothly removes the ambiguity entirely. The only error left
     * in a reading is the age of the picture, which is a number the band
     * prints, so the correction is arithmetic rather than luck.
     */
    public final double[] d = new double[]{MAX, MAX};
    /**
     * Seconds this hall has had something standing in an <b>open</b> doorway.
     *
     * <p>Separate from {@link #blocked}, and the separation is load-bearing
     * rather than tidy. The first version of this kept one clock, and it meant
     * a walker that had been standing at a shut door for two seconds was
     * <i>already past its grace</i> the instant the door opened -- so letting
     * go of a held door did not give you a reaction window, it killed you on
     * the same frame. Measured, that is what PRO was dying to on every seed:
     * the trace showed the door held, the walker waiting, and the night over
     * the frame the mechanism let go.
     *
     * <p>Both clocks <b>accumulate</b> while the walker is in the doorway and
     * are reset only when it leaves, and that is the second thing the sweep
     * taught. Resetting a clock whenever the door changed state looked like
     * the obvious reading of "the walker has to stand in an open doorway for
     * {@link #grace()} seconds" -- and it made <b>flickering the door a way to
     * win</b>: a policy that tapped the mechanism on and off never let either
     * clock run, so the walker neither entered nor gave up, and the night went
     * to six AM with two of them standing in the doorway. Measured, that
     * policy scored 100% on all five nights. A walker that has spent a total
     * of {@code grace} seconds in front of an open door is inside, however
     * many pieces that time arrived in.
     */
    public final double[] waited = new double[]{0, 0};
    /** Seconds this hall has had something standing at a <b>shut</b> door. */
    public final double[] blocked = new double[]{0, 0};

    // ---- The one circuit ----
    public Circuit circuit = Circuit.DARK;
    /**
     * The age of the picture, in seconds.
     *
     * <p>The whole game is in this number. It grows while the monitor is on
     * and falls while it is off, and what the monitor draws is the hall as it
     * was this many seconds ago.
     */
    public double feed = 0;
    /** Seconds until the door is actually down. Counts only while HOLD. */
    public double shutT = SHUT_TIME;
    /** Seconds of hold the door has left in it, from 0 to {@link #HOLD_MAX}. */
    public double heat = 0;
    /** True while the door has let go on its own and will not take a hold. */
    public boolean jammed = false;

    // ---- Outcome ----
    public Status status = Status.PLAYING;
    /** The second of the night it got in, or -1. */
    public int takenAt = -1;
    /** Which hall it came down, or null. */
    public Side takenSide = null;
    /** The walker that got in, or null. */
    public Walker takenBy = null;

    // ---- Counters, for the report ----
    /** Times a walker reached the doorway. */
    public int arrivals = 0;
    /** Times a walker gave up at a shut door and walked back. */
    public int gaveUp = 0;
    /** Steps taken toward the office. */
    public int steps = 0;
    /** Times the circuit moved. */
    public int switches = 0;
    /** Seconds the monitor was on. */
    public double watched = 0;
    /** Seconds the door was shut. */
    public double held = 0;
    /** Times the door let go on its own. */
    public int jams = 0;
    /**
     * The last hall that stepped.
     *
     * <p>Kept for the report and <b>deliberately not turned into a cue
     * name.</b> FNAF 7's whole audio channel was one distinction -- a step on
     * the left against a step on the right -- and this game has no directional
     * channel at all: a footfall that said which hall it came from would hand
     * the player the one thing the monitor exists to sell them. So both halls
     * emit the same cue, and the check that keeps it that way is in SelfTest.
     */
    public Side lastStepSide = null;

    // ---- The delay line ----
    // The monitor is a delay line, so the honest implementation is to keep
    // one. Each hall records a transition whenever its distance changes, and
    // the picture is read out of that list at `feed` seconds behind now.
    // Sampling every frame instead would work and would be 60 times the memory
    // for the same answer, because a walker only moves every couple of seconds.
    /**
     * How many samples a hall's delay line keeps.
     *
     * <p>Public because the phone build has to keep the same number: the
     * delay line <i>is</i> the game, and a phone whose buffer was a different
     * length would be a phone whose picture ages differently from the
     * desktop's. {@link aside.games.fnaf9.WebFeed} emits it into the page.
     */
    public static final int HIST = 1024;
    final double[][] histT = new double[2][HIST];
    final double[][] histD = new double[2][HIST];
    final int[] histN = new int[2];

    private final List<String> cues = new ArrayList<>();

    public Feed(int night, long seed) {
        this.night = night;
        this.rng = new Random(mix(seed));
        this.pair = Pair.forNight(night);
        // The two of them do not start in step. A night where both walkers
        // left the far end on the same frame is a night where both arrive on
        // the same frame, and with one door and one duty cycle that is a night
        // with an unwinnable moment in it -- measured, before this line
        // existed, as five policies scoring zero on every seed of every night.
        // The phase is rolled rather than fixed so the arrivals interleave
        // differently every time, which is what makes the door a decision
        // instead of a schedule.
        d[0] = MAX - 2.0 * rng.nextDouble();
        d[1] = MAX - 2.0 * rng.nextDouble();
        record(Side.LEFT);
        record(Side.RIGHT);
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
     * Seconds of age the picture gains per second the monitor is on.
     *
     * <p>This is the night's <b>price of looking</b>, and it is the dial the
     * whole week ramps on. The other candidate was {@link #ageDecay()}, and
     * the first sweep is why this one won: raising the rate makes every
     * individual look worse, which is a thing the player can feel and answer
     * by looking less, while lowering the decay only makes the <i>recovery</i>
     * worse, which a player experiences as the game being slow rather than the
     * game being hard.
     */
    public double ageRate() {
        double[] t = {0.85, 1.00, 1.15, 1.30, 1.45};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * Seconds of age the picture sheds per second the monitor is off.
     *
     * <p>It falls slower than it rises on every night of the week, and that
     * asymmetry is the game rather than a tuning choice: <b>if looking cost
     * the same as not looking, the monitor would be free and the night would
     * be a slideshow.</b> The duty cycle this sets is roughly a quarter --
     * look for a second, rest for three -- and the whole skill is spending
     * that quarter where the arrivals are.
     */
    public double ageDecay() {
        double[] t = {0.50, 0.45, 0.40, 0.35, 0.30};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * Seconds a walker takes to walk one step toward the office.
     *
     * <p>This is the night's <b>tempo</b>: how long a look buys you, and
     * therefore how many decisions fit in a night. A short step is a night
     * where a stale picture costs more, because the same lag is more steps.
     */
    public double pace() {
        double[] t = {2.55, 2.45, 2.35, 2.25, 2.15};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    /**
     * Seconds a walker can stand in an open doorway before it is inside.
     *
     * <p>This is the night's <b>reaction window</b>, and it is the number that
     * decides whether the light is a strategy or a habit. It has to stay above
     * a person's reaction time or the light is a lie; it has to come down over
     * the week or the light is an answer.
     */
    public double grace() {
        double[] t = {0.70, 0.65, 0.60, 0.55, 0.50};
        return t[Math.min(Math.max(night - 1, 0), t.length - 1)];
    }

    // ---- Reading the board ----

    /** True when the monitor is on at all. */
    public boolean monitorOn() {
        return circuit == Circuit.MON_LEFT || circuit == Circuit.MON_RIGHT;
    }

    /** The hall the monitor is showing, or null. */
    public Side monitorSide() {
        return switch (circuit) {
            case MON_LEFT -> Side.LEFT;
            case MON_RIGHT -> Side.RIGHT;
            default -> null;
        };
    }

    /** True when the mechanism has been asked to bring the door down. */
    public boolean holding() { return circuit == Circuit.HOLD; }

    /**
     * True when the door is actually down and stopping things.
     *
     * <p>Not the same as {@link #holding()}, and the gap between them is
     * {@link #SHUT_TIME}. A door on its way down is a door that is not there
     * yet, which is why the sensor cannot be used to start one.
     */
    public boolean blocking() { return circuit == Circuit.HOLD && shutT <= 0; }

    /**
     * The contact sensor: is anything standing against the door right now?
     *
     * <p>Live, free, one bit, and <b>it only reads while the door is down.</b>
     * That last part is the whole of its design and it took two sweeps to get
     * to. A sensor that reads all the time is a sensor that tells you a walker
     * has arrived -- which is a trigger, and a trigger is a live view of the
     * thing that is coming, and a live view of the thing that is coming is the
     * thing this game is about not having. Measured, a policy holding on the
     * sensor alone scored 100% on all five nights while the competent one
     * scored zero, twice.
     *
     * <p>Mounted on the door, it cannot do that. It reads pressure on the
     * <i>door</i>, so it says nothing at all until the door is down, and by
     * then the decision it could have helped with has already been made. What
     * it is for is the other end of the hold: <b>it is the only way to know
     * the doorway has emptied</b>, which is the only way to know when it is
     * safe to let go and go back to looking. Without it, letting go is a
     * guess, and a guess about the end of a hold is a guess about the start of
     * the next one.
     */
    public boolean sensor() { return blocking() && (d[0] <= 0 || d[1] <= 0); }

    /** True when the door is down and the sensor says nothing is against it. */
    public boolean clear() { return blocking() && d[0] > 0 && d[1] > 0; }

    /** How much hold the door has left, 0..1. */
    public double holdLeft() {
        return Math.max(0, 1 - heat / HOLD_MAX);
    }

    /**
     * What the monitor is drawing for a hall, or -1 for nothing at all.
     *
     * <p>Three ways to get nothing, and the player cannot tell them apart --
     * which is the point, and is the reason this returns one number rather
     * than a reason code:
     *
     * <ol>
     *   <li>the monitor is not on this hall,</li>
     *   <li>the picture is older than this walker's {@link Walker#sharp()},
     *       so the monitor does not hold it,</li>
     *   <li>or the walker is genuinely not in the picture.</li>
     * </ol>
     *
     * <p>An empty hall and an unwatchable one look exactly alike, and that is
     * the trap the whole night is built around.
     */
    public double shown(Side s) {
        if (monitorSide() != s) return -1;
        if (feed > pair.unit(s).sharp()) return -1;
        return apparent(s);
    }

    /** True when the monitor is on this hall but the picture is too old to hold it. */
    public boolean faded(Side s) {
        return monitorSide() == s && feed > pair.unit(s).sharp();
    }

    /**
     * Where the picture says a walker is: the distance it had {@code feed}
     * seconds ago.
     *
     * <p>Read out of the delay line rather than computed, because it is not a
     * formula -- a walker that arrived, waited and gave up inside the window
     * has to be drawn as having done that, and only a history knows.
     */
    public double apparent(Side s) {
        int k = s.ordinal();
        double target = time - feed;
        int i = histN[k] - 1;
        while (i > 0 && histT[k][i] > target) i--;
        return histD[k][i];
    }

    /** True when something is standing in the doorway right now. */
    public boolean atDoor() { return d[0] <= 0 || d[1] <= 0; }

    /** True when this hall is the one with something standing in the doorway. */
    public boolean atDoor(Side s) { return d[s.ordinal()] <= 0; }

    /** Steps from the doorway, 0..MAX. The truth, for the checks. */
    public double distance(Side s) { return d[s.ordinal()]; }

    /** The clock, as the band draws it. */
    public String clock() {
        return switch (Math.min(hour, 6)) {
            case 0 -> "12 AM"; case 1 -> "1 AM"; case 2 -> "2 AM";
            case 3 -> "3 AM"; case 4 -> "4 AM"; case 5 -> "5 AM";
            default -> "6 AM";
        };
    }

    /** The age of the picture, as the band draws it. */
    public String feedLabel() {
        if (!monitorOn()) return "FEED IDLE";
        return String.format("FEED %.1fs BEHIND", feed);
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

        // The feed. This is the only place the picture's age changes, and it
        // is the only thing in the office that is about you rather than about
        // them.
        if (monitorOn()) {
            watched += dt;
            feed = Math.min(AGE_MAX, feed + ageRate() * dt);
        } else {
            feed = Math.max(0, feed - ageDecay() * dt);
        }

        // The door mechanism. Held, it runs up; let go, it runs down; and at
        // the top it lets go on its own and will not take another hold until
        // it has cooled past the re-arm point.
        if (holding()) {
            held += dt;
            heat += dt;
            shutT = Math.max(0, shutT - dt);
            if (heat >= HOLD_MAX) {
                heat = HOLD_MAX;
                jammed = true;
                jams++;
                circuit = Circuit.DARK;
                cue("f9_jam");
            }
        } else {
            // Letting go drops the door instantly -- it is held by the
            // mechanism, not lowered by it -- so the only thing the door
            // costs is the time it takes to come down.
            shutT = SHUT_TIME;
            heat = Math.max(0, heat - COOL * dt);
            if (jammed && heat <= HOLD_MAX * REARM) jammed = false;
        }

        // The doorway. A walker that has stood at a shut door for its whole
        // patience loses interest -- and that is the only way the doorway ever
        // empties, because nothing in this office can push a walker back.
        for (Side s : Side.values()) {
            int i = s.ordinal();
            if (d[i] > 0) { waited[i] = 0; blocked[i] = 0; continue; }
            if (blocking()) {
                blocked[i] += dt;
                if (blocked[i] >= pair.unit(s).patience()) {
                    d[i] = MAX;
                    waited[i] = 0;
                    blocked[i] = 0;
                    gaveUp++;
                    record(s);
                    cue("f9_gives_up");
                }
                continue;
            }
            // The door is open and something is standing in the doorway.
            waited[i] += dt;
            if (waited[i] >= grace()) {
                status = Status.TAKEN;
                takenAt = (int) time;
                takenSide = s;
                takenBy = pair.unit(s);
                cue("f9_taken");
                return;
            }
        }

        // The two of them, walking.
        for (Side s : Side.values()) {
            int i = s.ordinal();
            if (d[i] <= 0) continue;
            double before = d[i];
            d[i] = Math.max(0, d[i] - dt * walkRate(s));
            if ((int) before != (int) d[i]) {
                steps++;
                lastStepSide = s;
                cue("f9_step");
            }
            if (d[i] <= 0 && before > 0) {
                arrivals++;
                cue("f9_door");
            }
            record(s);
        }
    }

    /**
     * Push one sample onto a hall's delay line.
     *
     * <p><b>Every frame, not every time the walker moves.</b> The first
     * version recorded only the moments a walker crossed a whole step, on the
     * reasoning that a delay line only needs the transitions -- and that made
     * the picture quantized to a step, which is exactly the ambiguity the
     * continuous motion was introduced to remove. Measured, the belief read
     * 0.75 while the walker was at 0.04: the picture was showing the position
     * at the last whole step before the read, which is up to a step stale on
     * top of the {@link #feed} seconds it is supposed to be stale by. A delay
     * line has to sample the thing it is delaying.
     */
    void record(Side s) {
        int k = s.ordinal();
        if (histN[k] == HIST) {
            // Full. Drop the oldest half rather than the oldest one: the
            // window we need is AGE_MAX seconds wide, so half a buffer is
            // several seconds more than enough.
            int keep = HIST / 2;
            System.arraycopy(histT[k], keep, histT[k], 0, keep);
            System.arraycopy(histD[k], keep, histD[k], 0, keep);
            histN[k] = keep;
        }
        histT[k][histN[k]] = time;
        histD[k][histN[k]] = d[k];
        histN[k]++;
    }

    /**
     * Seconds to this hall's next step.
     *
     * <p>Nothing in this office changes it. There is no lamp and no beam and
     * no counter -- <b>the only thing the player can do to a walker is decide
     * whether the door is shut when it arrives</b> -- so this is the night's
     * tempo divided by the walker's own speed and nothing else. FNAF 8's
     * interval had three multipliers on it and this one has one, and that is
     * not a simplification, it is the difference between the two games: in
     * FNAF 8 you spent the night moving them, and in FNAF 9 you spend it
     * trying to find out where they are.
     */
    public double interval(Side s) {
        return pace() / pair.unit(s).speed();
    }

    /**
     * The same, with the night's jitter on it.
     *
     * <p>Separate from {@link #interval} on purpose. {@code interval} is the
     * <i>mean</i>, and it is what the bot is allowed to know; this one draws,
     * and it is what the game actually does. If they were the same method the
     * bot's dead reckoning would consume the game's randomness and the sweep
     * would be measuring a bot that had quietly taken the wheel.
     */
    double nextInterval(Side s) {
        return interval(s) * (1.0 + JITTER * (rng.nextDouble() - 0.5));
    }

    /**
     * The same, with the night's jitter on it, for one frame of walking.
     *
     * <p>Continuous motion needs the jitter applied per frame rather than per
     * step, and it is applied to the <i>speed</i> so the walker still takes
     * about {@link #interval} seconds per step on average. The variance is
     * what the dead reckoning drifts against, exactly as it was when the
     * motion was stepped.
     */
    double walkRate(Side s) {
        return (1.0 + JITTER * (rng.nextDouble() - 0.5)) / interval(s);
    }

    // ---- Controls ----
    // Every one of these is a no-op once the night is over.

    /**
     * Put the one circuit somewhere.
     *
     * <p>Free and instant, and it has to be: the circuit is not a resource, it
     * is a <i>choice</i>, and the cost of the choice is what it takes away --
     * the feed while you watch, the halls while you hold, the halls while you
     * light. A player who switches is not spending anything except the seconds
     * the monitor is not running.
     *
     * @return true when the circuit moved
     */
    public boolean set(Circuit c) {
        if (status != Status.PLAYING) return false;
        if (c == Circuit.HOLD && jammed) return false;
        if (circuit == c) return false;
        circuit = c;
        switches++;
        cue("f9_switch");
        return true;
    }

    /** Shut the door, if the mechanism will take it. */
    public boolean hold() { return set(Circuit.HOLD); }

    /** Show a hall on the monitor. */
    public boolean watch(Side s) {
        return set(s == Side.LEFT ? Circuit.MON_LEFT : Circuit.MON_RIGHT);
    }

    /** Turn everything off. */
    public boolean dark() { return set(Circuit.DARK); }

}
