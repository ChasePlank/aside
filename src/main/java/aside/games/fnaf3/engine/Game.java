package aside.games.fnaf3.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNAF 3 core loop.
 *
 * FNAF 1 rations power and lets you shut doors. FNAF 2 rations attention
 * and lets you hide behind a mask. FNAF 3 gives you <b>neither</b>. There
 * is one animatronic, he cannot be blocked, and the only thing you can do
 * to him is make a noise somewhere else.
 *
 * So the night is three clocks, and they all want the same two hands:
 *
 *   1. SPRINGTRAP walks a graph toward the office. The audio lure is the
 *      only way to move him, and it has a duration and a cooldown, so it
 *      buys distance rather than removing him.
 *   2. VENTILATION drains and must be rebooted. Rebooting takes seconds
 *      during which the system is down -- and while it is down, the
 *      phantoms come, and Springtrap moves faster.
 *   3. PHANTOMS cannot hurt you. They take a system on the way out. A
 *      dead camera, a dead lure, or dead air, chosen for you.
 *
 * And over all three, the office: the window and the vent are the only
 * places you can see him arrive, and the monitor is the only place you
 * can see him coming. Looking at one is not looking at the other. That
 * is the whole game, and it is the reason this one is harder than the
 * two before it.
 *
 * The engine is pure logic with no UI dependency, so the survival sim can
 * be played by a bot at 60fps with no display -- which is how the
 * difficulty was tuned rather than by playing twenty nights by hand.
 */
public class Game {

    // ---- Clock ----
    public static final double HOUR_SECONDS = 45.0;
    public static final int NIGHT_HOURS = 6;

    // ---- Systems ----
    public enum System { CAMERAS, AUDIO, VENTILATION }

    /** Seconds a reboot takes. The system is down for all of it. */
    public static final double REBOOT_TIME = 4.0;

    // ---- Ventilation ----
    public static final double VENT_MAX = 100.0;
    /** Full to empty in ~100 seconds on night 1 if never rebooted. */
    public static final double VENT_DRAIN = VENT_MAX / 100.0;
    /**
     * The air gets worse as the week goes on. This is the honest
     * difficulty lever, and it is the same one FNAF 2 uses for its music
     * box: it does not make Springtrap faster, it makes you spend more of
     * the night with your hands on a panel -- and a panel is time you are
     * not holding the lure.
     */
    public static final double[] DRAIN_MULT = {1.0, 1.3, 1.55, 1.75, 2.1};

    // ---- Audio lure ----
    /** How long a lure keeps playing. */
    public static final double LURE_DURATION = 5.0;
    /** How long before it can be played again. The real constraint. */
    public static final double LURE_COOLDOWN = 7.5;

    // ---- Springtrap ----
    public static final double MOVE_MAX = 5.4;
    public static final double MOVE_MIN = 3.0;
    public static final double GRACE_MAX = 3.5;
    public static final double GRACE_MIN = 2.6;
    /** How much faster he moves with the air off. */
    public static final double VENT_FAIL_SPEEDUP = 0.6;

    // ---- Phantoms ----
    public static final double PHANTOM_LIFE = 3.0;
    /** Per second, with the air on. */
    public static final double PHANTOM_RATE_CALM = 0.008;
    /** Per second, with the air off. */
    public static final double PHANTOM_RATE_FAILING = 0.12;
    public static final int PHANTOM_MAX = 2;

    public int night;

    public double time = 0;
    public int hour = 0;

    // ---- Player state ----
    public boolean cameraUp = false;
    public int currentCam = 1;

    // ---- Systems ----
    public final boolean[] online = {true, true, true};
    /** Which system is being rebooted, or -1. */
    public int rebooting = -1;
    public double rebootTimer = 0;

    // ---- Ventilation ----
    public double ventilation = VENT_MAX;

    // ---- Audio lure ----
    /** Room the lure is playing in, or 0 for silence. */
    public int lureRoom = 0;
    public double lureTimer = 0;
    public double lureCooldown = 0;

    // ---- Outcome ----
    public enum Status { PLAYING, JUMPSCARED, SURVIVED }
    public Status status = Status.PLAYING;

    // ---- The cast ----
    public final Springtrap springtrap;
    public final List<Phantom> phantoms = new ArrayList<>();
    /** The most recent phantom, so the screen can name what just appeared. */
    public Phantom lastPhantom;
    /** The last system a phantom took, so the screen can say what it cost. */
    public System lastTaken;
    /** How many have appeared this night. Read by the self-test. */
    public int phantomsSpawned;

    public final Random rng;

    private final List<String> cues = new ArrayList<>();

    /** The systems a phantom can take. Not the air: the air is what
     *  causes them, and a hallucination that switches off the thing
     *  making it is a loop rather than a cost. */
    public static final System[] PHANTOM_TAKES = { System.CAMERAS, System.AUDIO };

    public static final String[] PHANTOM_NAMES = {
        "Phantom Freddy", "Phantom Chica", "Phantom Foxy",
        "Phantom Mangle", "Phantom Puppet", "Phantom Balloon Boy",
    };
    public static final Phantom.Slot[] PHANTOM_SLOTS = {
        Phantom.Slot.WINDOW, Phantom.Slot.DESK, Phantom.Slot.VENT,
        Phantom.Slot.CORNER, Phantom.Slot.WINDOW, Phantom.Slot.DESK,
    };

    public Game(int night, long seed) {
        this.night = night;
        this.rng = new Random(seed);
        // He does not start at the door. The first move of the night is
        // always a walk, which is what makes the opening calm honest.
        this.springtrap = new Springtrap(5 + rng.nextInt(6));
    }

    // ---- Difficulty ----

    /**
     * AI level per night. FNAF 3 is one animatronic and no way to stop
     * him, so the ramp starts higher than FNAF 2's and ends at the top:
     * "each one harder than the last" is a promise about the franchise,
     * not a mood.
     */
    public static int aiLevel(int night) {
        int[] table = {4, 8, 12, 16, 20};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    /** Seconds between his moves. The air being off makes him quicker. */
    public double moveInterval() {
        double t = Math.min(1.0, aiLevel(night) / 20.0);
        double base = MOVE_MAX - t * (MOVE_MAX - MOVE_MIN);
        return ventilationOnline() ? base : base * VENT_FAIL_SPEEDUP;
    }

    /** How long he stands in the office before he is on you. */
    public double officeGrace() {
        double t = Math.min(1.0, aiLevel(night) / 20.0);
        return GRACE_MAX - t * (GRACE_MAX - GRACE_MIN);
    }

    /** Chance per move that he steps in from the last room. */
    public double enterChance() {
        double t = Math.min(1.0, aiLevel(night) / 20.0);
        return 0.30 + t * 0.45;
    }

    /** How fast the air goes on this night. */
    public double drainRate() {
        return VENT_DRAIN * DRAIN_MULT[Math.min(Math.max(night - 1, 0),
                DRAIN_MULT.length - 1)];
    }

    /** Phantoms per second. The air is the whole dial. */
    public double phantomRate() {
        return ventilationOnline() ? PHANTOM_RATE_CALM : PHANTOM_RATE_FAILING;
    }

    public boolean ventilationOnline() {
        return online[System.VENTILATION.ordinal()];
    }

    public boolean audioOnline() {
        return online[System.AUDIO.ordinal()];
    }

    public boolean camerasOnline() {
        return online[System.CAMERAS.ordinal()];
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

    // ---- Update ----

    public void update(double dt) {
        if (status != Status.PLAYING) return;

        // Clock
        time += dt;
        hour = (int) Math.floor(time / HOUR_SECONDS);
        if (hour >= NIGHT_HOURS) {
            status = Status.SURVIVED;
            cue("chime_6am");
            return;
        }

        // The lure. It runs down on its own and then has to cool off --
        // that cooldown is what stops "keep a noise playing in the arcade"
        // from being a strategy.
        if (lureRoom > 0) {
            lureTimer -= dt;
            if (lureTimer <= 0) {
                lureRoom = 0;
                lureTimer = 0;
            }
        }
        if (lureCooldown > 0) lureCooldown = Math.max(0, lureCooldown - dt);

        // Ventilation. It only drains while it is running; a system that
        // is already down cannot get worse, which is why the failure has
        // to be *fixed* rather than survived.
        if (ventilationOnline()) {
            ventilation -= drainRate() * dt;
            if (ventilation <= 0) {
                ventilation = 0;
                online[System.VENTILATION.ordinal()] = false;
                cue("power_down");
            }
        }

        // Reboots
        if (rebooting >= 0) {
            rebootTimer += dt;
            if (rebootTimer >= REBOOT_TIME) {
                System s = System.values()[rebooting];
                online[rebooting] = true;
                if (s == System.VENTILATION) ventilation = VENT_MAX;
                rebooting = -1;
                rebootTimer = 0;
                cue("power_up");
            }
        }

        springtrap.update(dt, this);
        if (status != Status.PLAYING) return;

        updatePhantoms(dt);
    }

    void updatePhantoms(double dt) {
        for (Phantom p : phantoms) {
            if (p.update(dt)) {
                online[p.takes.ordinal()] = false;
                lastTaken = p.takes;
                cue("static");
            }
        }
        phantoms.removeIf(p -> p.spent);

        if (phantoms.size() < PHANTOM_MAX && rng.nextDouble() < phantomRate() * dt) {
            spawnPhantom();
        }
    }

    /** Public so the screen's dev hooks can stage one for a render. */
    public void spawnPhantom() {
        int i = rng.nextInt(PHANTOM_NAMES.length);
        System takes = PHANTOM_TAKES[rng.nextInt(PHANTOM_TAKES.length)];
        Phantom p = new Phantom(PHANTOM_NAMES[i], PHANTOM_SLOTS[i], takes, PHANTOM_LIFE);
        phantoms.add(p);
        lastPhantom = p;
        phantomsSpawned++;
        cue("static");
    }

    void jumpscare(Springtrap s) {
        status = Status.JUMPSCARED;
        cue("scare_sprint");
    }

    // ---- Controls ----
    // Every one of these is a no-op once the night is over.

    public void toggleCamera() {
        if (status != Status.PLAYING) return;
        cameraUp = !cameraUp;
        cue(cameraUp ? "camera_up" : "camera_down");
    }

    public void setCam(int cam) {
        if (cam < 1 || cam > House.ROOMS) return;
        currentCam = cam;
        cue("static");
    }

    /**
     * Play the audio lure in a room.
     *
     * Refused while the audio is down, while it is being rebooted, and
     * while it is still cooling off. The last one is the design: the lure
     * is not a switch you leave on, it is a move you get to make about
     * once every nine seconds, and Springtrap walks four rooms in that
     * time on a bad night.
     */
    public boolean playLure(int room) {
        if (status != Status.PLAYING) return false;
        if (!audioOnline()) return false;
        // A reboot takes both hands. This is the cost that makes the air
        // matter: every second spent fixing a panel is a second the lure
        // is unavailable, and he does not stop walking while you fix it.
        if (rebooting >= 0) return false;
        if (lureCooldown > 0) return false;
        if (room < 1 || room > House.ROOMS) return false;
        lureRoom = room;
        lureTimer = LURE_DURATION;
        lureCooldown = LURE_COOLDOWN;
        cue("pot_clank");
        return true;
    }

    /** True when the lure can be played right now. */
    public boolean lureReady() {
        return status == Status.PLAYING && audioOnline()
                && rebooting < 0 && lureCooldown <= 0;
    }

    /**
     * Start rebooting a system. One at a time: the panels are on the wall
     * and you have two hands, which is the same reason you cannot watch
     * the office and the monitor at once.
     */
    public boolean startReboot(System s) {
        if (status != Status.PLAYING) return false;
        if (rebooting >= 0) return false;
        if (online[s.ordinal()]) return false;
        rebooting = s.ordinal();
        rebootTimer = 0;
        cue("light_click");
        return true;
    }

    /** True when this system could be rebooted right now. */
    public boolean canReboot(System s) {
        return status == Status.PLAYING && rebooting < 0 && !online[s.ordinal()];
    }
}
