package aside.games.fnaf4.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNAF 4 core loop.
 *
 * FNAF 1 gave you doors and a power meter. FNAF 2 gave you a mask and a
 * music box. FNAF 3 gave you a speaker and took the doors away. FNAF 4
 * takes away the desk: <b>the room has four sides and you have one body</b>,
 * so the resource is not power and not attention, it is <i>where you are</i>.
 *
 * Four things are walking toward four places. Each one counts down in
 * moves, and the countdown is audible -- a step when it is one move out, a
 * breath when it is standing there. You can only push back what is in
 * front of you, and the flashlight that pushes it back is the same
 * flashlight that tells the room where you are.
 *
 * So the night is three costs and they are all the same cost:
 *
 *   1. THE TRIP. The bed is the hub, so the doors are one hop away and
 *      each other is two. A wrong guess is a hop you did not have.
 *   2. THE FLASH. You can push something back at one move out, which is
 *      much safer than waiting for it to arrive -- and every flash is
 *      noise, and noise is what brings Fredbear.
 *   3. FREDBEAR. He does not walk. He is simply somewhere, and he has to
 *      be found, and finding him means flashing at places that are empty.
 *
 * The difficulty table is built so that early nights can be survived by
 * reacting and late nights cannot: at night 5 the grace is shorter than
 * the worst-case trip, so a player who only ever answers the breath will
 * eventually be two hops away with nothing to do. Pushing things back
 * early is the only way to hold the room, and pushing things back early
 * is what fills the room with noise.
 *
 * The engine is pure logic with no UI dependency, so the survival sim can
 * be played by a bot at 60fps with no display -- which is how the week was
 * tuned rather than by playing twenty nights by hand.
 */
public class Game {

    // ---- Clock ----
    public static final double HOUR_SECONDS = 40.0;
    public static final int NIGHT_HOURS = 6;

    // ---- The body ----
    /** One hop: bed to a door, or a door back to the bed. */
    public static final double HOP_TIME = 1.15;
    /** How long the light takes, and how long your hands are busy. */
    public static final double FLASH_TIME = 0.40;
    /** How long a station stays lit after a flash. */
    public static final double LIT_TIME = 1.00;

    // ---- The threats ----
    /** Moves from the far end of the room to standing in front of you. */
    public static final int DIST_MAX = 4;
    /**
     * Where the light sends something it catches one move out.
     *
     * Not all the way back. See {@link Threat#push()} -- the difference
     * between this and {@link #DIST_MAX} is the price of pre-empting, and
     * it is what stops "flash everything early" from being the answer.
     */
    public static final int PUSH_TO = 3;
    /** How often a waiting threat announces itself again. */
    public static final double BREATH_EVERY = 1.5;

    // ---- Noise ----
    public static final double NOISE_MAX = 100.0;
    /** What one flash costs. */
    public static final double NOISE_PER_FLASH = 18.0;
    public static final double NOISE_DECAY = 6.0;
    /** How much more often Fredbear comes at full noise. */
    public static final double NOISE_WEIGHT = 1.9;

    // ---- Fredbear ----
    /** How long he waits to be found. Long: he is an errand, not an attack. */
    public static final double FREDBEAR_GRACE = 9.0;
    /** How long the room is quiet after he leaves. */
    public static final double FREDBEAR_COOLDOWN = 22.0;

    public int night;

    public double time = 0;
    public int hour = 0;

    // ---- Where you are ----
    public Room.Where where = Room.HUB;
    /** Where you are going, or null. */
    public Room.Where heading = null;
    /** Seconds of hands-busy left: a move or a flash, never both. */
    public double busy = 0;
    /** How long the move you are in the middle of takes, for the bar. */
    public double busyTotal = 0;
    /** Seconds of light left at the station you are at. */
    public double lit = 0;

    // ---- Noise ----
    public double noise = 0;
    /** Flashes made this night. Read by the self-test and the report. */
    public int flashes = 0;
    /** Flashes that found nothing. The number the player should want low. */
    public int wastedFlashes = 0;

    // ---- Fredbear ----
    /** Where he is standing, or null. */
    public Room.Where fredbearAt = null;
    public double fredbearHere = 0;
    public double fredbearCooldown = 0;
    /** How many times he has come this night. */
    public int fredbearVisits = 0;

    // ---- Outcome ----
    public enum Status { PLAYING, JUMPSCARED, SURVIVED }

    public Status status = Status.PLAYING;
    /** Who got you, for the jumpscare. */
    public String killer = null;

    // ---- The cast ----
    public final List<Threat> threats = new ArrayList<>();
    /** The most recent arrival, so the screen can flash the right edge. */
    public Threat lastArrival;
    /** How many have arrived this night. Read by the self-test. */
    public int arrivals;

    public final Random rng;

    private final List<String> cues = new ArrayList<>();

    public Game(int night, long seed) {
        this.night = night;
        this.rng = new Random(seed);

        // Four things, four places, one each. The pace spread staggers the
        // arrivals; without it all four move on the same beat and the room
        // reads as a metronome.
        threats.add(new Threat("Nightmare Bonnie", "bonnie", Room.Where.LEFT, 0.95,
                start()));
        threats.add(new Threat("Nightmare Chica", "chica", Room.Where.RIGHT, 1.05,
                start()));
        threats.add(new Threat("Nightmare Foxy", "foxy", Room.Where.CLOSET, 0.90,
                start()));
        threats.add(new Threat("Nightmare Freddy", "freddy", Room.Where.BED, 1.00,
                start()));
    }

    /** Nobody starts at the door. The opening calm is not a lie. */
    private int start() {
        return DIST_MAX - rng.nextInt(2);
    }

    // ---- Difficulty ----

    /**
     * AI level per night.
     *
     * Chase's line for the franchise was "each gets harder than the last",
     * so this starts higher than FNAF 3's and ends at the top. FNAF 4 is
     * the one that takes the desk away, so it also has the steepest ramp.
     */
    static int aiLevel(int night) {
        int[] table = {3, 6, 10, 14, 18};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /** Seconds between moves for a threat of pace 1.0. */
    public double baseInterval() {
        double[] table = {6.0, 5.3, 4.7, 4.2, 3.8};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    public double interval(Threat t) {
        return baseInterval() / t.pace;
    }

    /**
     * How long a threat stands there before it is on you.
     *
     * This is the dial the whole week turns on. The worst-case trip is a
     * spoke to a spoke -- two hops and a flash, 2.7 seconds -- so a grace
     * above that can be answered from anywhere and a grace below it cannot.
     * Night 1 is comfortable, night 5 is not, and the difference is what
     * forces the player to stop reacting and start pre-empting.
     */
    public double grace() {
        double[] table = {4.2, 3.8, 3.3, 2.9, 2.5};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /** The worst-case trip: spoke to spoke, then the light. */
    public static double worstTrip() {
        return 2 * HOP_TIME + FLASH_TIME;
    }

    /**
     * Fredbear's arrival rate per second, before noise.
     *
     * Zero on night 1: he is the escalation, and a first night that already
     * has him in it has nowhere left to go.
     */
    public double fredbearRate() {
        double[] table = {0.0, 0.0040, 0.0070, 0.0110, 0.0160};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /**
     * And with noise.
     *
     * The multiplier runs from 0.1 at a quiet room to 2.0 at a loud one, so
     * a player who only ever flashes at what they can hear sees him a fifth
     * as often as a player who sweeps the room. That is the whole cost of
     * the flashlight, and it is deliberately a rate rather than a meter
     * that trips: a punishment you can watch coming is a decision, and a
     * punishment that arrives on a threshold is a trap.
     */
    public double fredbearChance() {
        return fredbearRate() * (0.1 + NOISE_WEIGHT * (noise / NOISE_MAX));
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

    /** Called by a threat the moment it arrives. */
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

        // The room goes quiet on its own. Slowly enough that a burst of
        // flashes is still there a few seconds later, which is the point.
        noise = Math.max(0, noise - NOISE_DECAY * dt);

        if (busy > 0) {
            busy -= dt;
            if (busy <= 0) {
                busy = 0;
                if (heading != null) {
                    where = heading;
                    heading = null;
                }
            }
        }
        if (lit > 0) lit = Math.max(0, lit - dt);

        for (Threat t : threats) {
            t.update(dt, this);
            if (status != Status.PLAYING) return;
        }

        updateFredbear(dt);
    }

    void updateFredbear(double dt) {
        if (fredbearAt != null) {
            fredbearHere += dt;
            if (fredbearHere >= FREDBEAR_GRACE) jumpscare("Nightmare Fredbear");
            return;
        }
        if (fredbearCooldown > 0) {
            fredbearCooldown = Math.max(0, fredbearCooldown - dt);
            return;
        }
        double p = fredbearChance();
        if (p > 0 && rng.nextDouble() < p * dt) spawnFredbear();
    }

    /**
     * Put Fredbear somewhere.
     *
     * Never where the player already is. He is the one threat you cannot
     * answer by standing still, and a Fredbear who appears in front of you
     * is a Fredbear who costs nothing.
     */
    public void spawnFredbear() {
        List<Room.Where> options = new ArrayList<>();
        for (Room.Where w : Room.ALL) if (w != where) options.add(w);
        fredbearAt = options.get(rng.nextInt(options.size()));
        fredbearHere = 0;
        fredbearVisits++;
        cue("fredbear_laugh");
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

    /**
     * Walk to a place.
     *
     * Refused while your hands are busy, which covers both the flash you
     * are in the middle of and the move you are in the middle of. There is
     * no queue: a second press during a move is not a second move.
     */
    public boolean moveTo(Room.Where dest) {
        if (status != Status.PLAYING) return false;
        if (busy > 0) return false;
        if (dest == where) return false;
        int hops = Room.hops(where, dest);
        if (hops == 0) return false;
        heading = dest;
        busy = hops * HOP_TIME;
        busyTotal = busy;
        lit = 0;                    // you are not looking at that any more
        cue("footstep");
        return true;
    }

    /**
     * Put the light on what is in front of you.
     *
     * It reaches one move out as well as standing-here, and that is the
     * single most important number in the game. Answering a breath is
     * always safe and always late; pushing something back while it is
     * still one move away is what keeps the room from filling up, and it
     * costs a flash you did not have to spend.
     *
     * Returns true if it found anything, which is what the screen uses to
     * decide whether the frame was a hit or a waste.
     */
    public boolean flash() {
        if (status != Status.PLAYING) return false;
        if (busy > 0) return false;

        busy = FLASH_TIME;
        busyTotal = FLASH_TIME;
        lit = LIT_TIME;
        noise = Math.min(NOISE_MAX, noise + NOISE_PER_FLASH);
        flashes++;
        cue("light_click");

        boolean found = false;
        for (Threat t : threats) {
            if (t.home != where) continue;
            if (t.distance == 0) { t.repel(); found = true; }
            else if (t.distance == 1) { t.push(); found = true; }
        }
        if (fredbearAt == where) {
            fredbearAt = null;
            fredbearHere = 0;
            fredbearCooldown = FREDBEAR_COOLDOWN;
            cue("door_close");
            found = true;
        }
        if (!found) wastedFlashes++;
        return found;
    }

    /** True when the light could be used right now. */
    public boolean canFlash() {
        return status == Status.PLAYING && busy <= 0;
    }

    /** True when the station you are at has something in it. */
    public boolean occupied() {
        if (fredbearAt == where) return true;
        for (Threat t : threats) if (t.home == where && t.distance == 0) return true;
        return false;
    }

    /** True when something is one move out from the station you are at. */
    public boolean approaching() {
        for (Threat t : threats) if (t.home == where && t.distance == 1) return true;
        return false;
    }

    /** True when the station you are at has anything within reach of the light. */
    public boolean reachable() {
        if (fredbearAt == where) return true;
        for (Threat t : threats) if (t.home == where && t.distance <= 1) return true;
        return false;
    }

    /** The threat standing at a station, or null. */
    public Threat standingAt(Room.Where w) {
        for (Threat t : threats) if (t.home == w && t.distance == 0) return t;
        return null;
    }
}
