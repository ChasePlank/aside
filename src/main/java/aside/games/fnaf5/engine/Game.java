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
 * which one to be wrong about. The controlled shock answers all three at
 * once -- it clears whatever is in the room with you and throws it to the
 * far end of the building -- and that is exactly why there are only a
 * handful of them and no way to earn more.
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
    /** Silence, since she arrived, that loses her. */
    public static final double BALLORA_PATIENCE = 1.8;
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
        threats.add(new Threat("Ballora", "ballora", Threat.Rule.SOUND,
                Room.Where.BALLORA, 1.00, Room.Where.BALLORA));
        threats.add(new Threat("Funtime Foxy", "foxy", Threat.Rule.ATTENTION,
                Room.Where.AUDITORIUM, 0.95, Room.Where.AUDITORIUM));
        threats.add(new Threat("Funtime Freddy", "freddy", Threat.Rule.PURSUIT,
                Room.Where.PARTS, 1.05, Room.Where.PARTS));
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

    public double interval(Threat t) {
        return baseInterval() / t.pace;
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
     * <b>Retuned 2026-10-01, and the sweep is why.</b> The table used to be
     * {3.0, 2.8, 2.6, 2.4, 2.2} and the week did not get harder: HOLD
     * survived 93/95/100/98/100, with the *last* night the easiest one.
     * Most of that was the missing jitter (see {@link #INTERVAL_JITTER}),
     * but not all of it. Once the night was a distribution, the interval
     * turned out to be the wrong dial to ramp: a threat that moves faster
     * also *leaves* your room faster, so a shorter interval can make a
     * night easier rather than harder. Grace has no such counter-effect --
     * it is exactly the time you have to answer -- so the ramp lives here
     * now, and the week reads 95/94/91/82/49.
     */
    public double grace() {
        double[] table = {3.0, 2.7, 2.4, 2.1, 1.8};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /** The worst case: a move, then the shock. */
    public static double worstTrip() {
        return MOVE_TIME + SHOCK_TIME;
    }

    /**
     * Controlled shocks per night.
     *
     * Never more than three, and the last two nights have two. The shock is
     * the only answer to all three threats at once, so its scarcity is what
     * makes the three of them a squeeze rather than three separate games.
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
     * Blind, limited, and the only thing in the game that answers all three
     * threats at once. It clears whatever is standing in the room with you
     * and throws it to whichever end of the building is further away, and
     * it makes a noise doing it -- so a player who shocks their way out of
     * Ballora has just called her back.
     *
     * Returns true if it found anything, which is what the screen uses to
     * decide whether the frame was a hit or a waste.
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
