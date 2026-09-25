package aside.games.fnaf.engine;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.util.*;

/**
 * FNAF 1 core loop, Glamrock cast edition.
 *
 * The player sits in the office. Four animatronics roam the building.
 * Each has a movement opportunity every few seconds (FNAF's dice-roll
 * movement): roll succeeds -> advance along their path toward the
 * office. At the door, they wait; if the door isn't closed in time,
 * they enter and kill (jumpscare) after a grace window.
 *
 * Player tools:
 *  - Left/right DOORS (block entry, drain power while closed)
 *  - Left/right LIGHTS (reveal the doorway, drain power while on)
 *  - CAMERA (view rooms; drains power while up; Monty is camera-shy:
 *    viewing his room stalls his movement)
 *  - Power: starts 100%. Passive drain + per-tool drain. 0% = blackout.
 *
 * Blackout: doors open, lights dead, cameras dead. Monty (the Freddy
 * role) plays his music box; when it stops, he attacks after a random
 * delay. Survive to 6 AM.
 *
 * Night = 6 in-game hours, each hour ~45 real seconds (FNAF pacing).
 * Difficulty scales per night via animatronic AI levels.
 */
public class Game {
    // ---- Clock ----
    public static final double HOUR_SECONDS = 45.0;
    public static final int NIGHT_HOURS = 6;

    // ---- Power ----
    public static final double POWER_START = 100.0;
    // Passive drain: 1 unit per ~9.6 seconds at one "usage bar" (FNAF
    // baseline). Each active tool adds a usage bar.
    public static final double POWER_DRAIN_PER_BAR = 1.0 / 9.6;

    public int night = 1;

    public double time = 0;          // seconds into the night
    public int hour = 0;             // 12 AM .. 5 AM
    public double power = POWER_START;
    public int usageBars = 1;        // 1 passive + 1 per active tool

    // ---- Player state ----
    public boolean leftDoorClosed = false;
    public boolean rightDoorClosed = false;
    public boolean leftLightOn = false;
    public boolean rightLightOn = false;
    public boolean cameraUp = false;
    public int currentCam = 1;       // camera room id

    // ---- Outcome ----
    public enum Status { PLAYING, JUMPSCARED, POWER_OUT, SURVIVED }
    public Status status = Status.PLAYING;

    // ---- The cast (Glamrock roles) ----
    public final Animatronic monty;    // Bonnie role: left door, camera-shy
    public final Animatronic roxanne;  // Chica role: right door
    public final Animatronic chica;    // Freddy role: right door, waits in dark
    public final Animatronic freddy;   // Foxy role: sprints from his cove

    // Freddy-role: the power-out attacker (classic Freddy music box)
    public double blackoutTimer = 0;
    public boolean blackoutMusicPlaying = true;
    public double blackoutAttackDelay = 0;   // set when music stops
    public double blackoutAttackTimer = 0;

    // Camera stall: viewing Monty's room stalls his movement
    public double camStallTimer = 0;

    // Jumpscare source (which animatronic got you)
    public Animatronic jumpscareBy = null;

    // ---- Building map ----
    // Simplified FNAF 1 layout. Rooms 1..10, office = 0.
    //   1 Show Stage        2 Backstage        3 Pirate Cove (Monty)
    //   4 West Hall         5 W. Hall Corner   6 Supply Closet
    //   7 Restrooms         8 Kitchen          9 E. Hall Corner
    //  10 E. Hall
    // Paths: each animatronic walks a fixed sequence of rooms toward
    // the office. (FNAF animatronics teleport between path nodes on a
    // successful roll — they don't physically walk the map.)
    public static final int OFFICE = 0;

    public final Random rng;

    public Game(int night, long seed) {
        this(night, seed, null);
    }

    /** Custom-night variant: explicit AI levels, one per animatronic
     *  (Monty, Roxanne, Chica, Freddy), each 0-20. */
    public Game(int night, long seed, int[] customLevels) {
        this.night = night;
        this.rng = new Random(seed);

        int[] lv = customLevels != null
                ? customLevels
                : new int[]{aiLevel(night, 0), aiLevel(night, 1),
                            aiLevel(night, 2), aiLevel(night, 3)};

        monty = new Animatronic("Monty", 1, new int[]{1, 2, 4, 5, OFFICE},
                lv[0], 4.97, this);
        // Monty (Bonnie role) reaches the LEFT door
        monty.doorSide = -1;

        roxanne = new Animatronic("Roxanne", 1, new int[]{1, 7, 8, 9, OFFICE},
                lv[1], 4.98, this);
        roxanne.doorSide = 1;

        chica = new Animatronic("Chica", 1, new int[]{1, 7, 8, 9, OFFICE},
                lv[2], 3.02, this);
        chica.doorSide = 1;

        // Foxy role: waits in Pirate Cove, stages advance on rolls,
        // then SPRINTS down the West Hall to the left door.
        freddy = new Animatronic("Freddy", 3, new int[]{3, 4, OFFICE},
                lv[3], 5.01, this);
        freddy.doorSide = -1;
        freddy.isFoxy = true;
        freddy.stages = 0;   // 0 hidden, 1 peeking, 2 emerged, 3 gone (running)
    }

    /** AI level per night per animatronic (FNAF 1 table, tuned so
     *  night 1 has visible movement — the real game has Bonnie at 1,
     *  Chica at 1, Foxy at 1. Level 0 = literally never moves, which
     *  reads as "broken" on night 1. Minimum 1 for everyone. */
    static int aiLevel(int night, int idx) {
        // rows: night 1-6 (index 5 = custom), cols: animatronic idx
        // cols: Monty, Roxanne, Chica, Freddy(Foxy)
        int[][] table = {
            {1, 1, 2, 1},    // night 1 — visible but gentle
            {3, 3, 4, 3},    // night 2
            {5, 5, 6, 5},    // night 3
            {7, 7, 8, 7},    // night 4
            {9, 9, 10, 9},   // night 5
            {13, 13, 14, 13} // night 6 / custom 20-20-20-20 territory
        };
        int row = Math.min(night - 1, table.length - 1);
        return table[row][idx];
    }

    // ---- Update ----
    public void update(double dt) {
        // POWER_OUT runs its own sequence (clock keeps ticking — the
        // classic "survive blackout till 6AM" is a real win path).
        if (status == Status.POWER_OUT) {
            time += dt;
            hour = (int) Math.floor(time / HOUR_SECONDS);
            if (hour >= NIGHT_HOURS) {
                status = Status.SURVIVED;
                return;
            }
            blackoutTimer += dt;
            if (blackoutMusicPlaying) {
                if (blackoutTimer > 5.0 + rng.nextDouble() * 5.0) {
                    blackoutMusicPlaying = false;
                    // Attack delay: a 5AM blackout is roughly a coin
                    // flip (delay often exceeds the remaining 45s);
                    // an early power-out is near-certain death —
                    // power management matters (FNAF feel).
                    blackoutAttackDelay = 10.0 + rng.nextDouble() * 70.0;
                    blackoutAttackTimer = 0;
                }
            } else {
                blackoutAttackTimer += dt;
                if (blackoutAttackTimer >= blackoutAttackDelay) {
                    jumpscare(chica);  // Chica is the Freddy-role power-out attacker
                }
            }
            return;
        }
        if (status != Status.PLAYING) return;

        // Clock
        time += dt;
        int newHour = (int) Math.floor(time / HOUR_SECONDS);
        if (newHour != hour) {
            hour = newHour;
        }
        if (hour >= NIGHT_HOURS) {
            status = Status.SURVIVED;
            return;
   //         return;
        }

        // Power drain
        usageBars = 1
                + (leftDoorClosed ? 1 : 0)
                + (rightDoorClosed ? 1 : 0)
                + (leftLightOn ? 1 : 0)
                + (rightLightOn ? 1 : 0)
                + (cameraUp ? 1 : 0);
        power -= usageBars * POWER_DRAIN_PER_BAR * dt;
        if (power <= 0) {
            power = 0;
            enterBlackout();
        }

        // Camera stall on Monty
        if (cameraUp && currentCam == monty.path[monty.pathIndex] && !monty.isFoxy) {
            // viewing Monty's current room stalls him (Bonnie-role camera-shy)
            camStallTimer = 1.0;  // re-checked every frame while viewed
        }
        boolean montyStalled = cameraUp && currentCam == monty.path[monty.pathIndex];
        monty.stalled = montyStalled;

        // Animatronic movement
        monty.update(dt);
        roxanne.update(dt);
        chica.update(dt);
        freddy.update(dt);

        // Foxy-role sprint attack: once freddy.stages == 3, he runs.
        // If the left door is closed when he arrives, he's repelled
        // (and bangs — drains a chunk of power, classic Foxy).
        if (freddy.stages >= 3 && !freddy.sprintResolved) {
            freddy.sprintTimer += dt;
            if (freddy.sprintTimer >= 2.5) {  // reactable sprint — a player checking the cove every couple seconds can slam the door in time
                // arrives at the door after ~1s run
                if (leftDoorClosed) {
                    freddy.sprintResolved = true;
                    freddy.stages = 0;
                    freddy.sprintTimer = 0;
                    freddy.pathIndex = 0;
                    // power penalty for the bang (Foxy drains 1%+ on repel)
                    power -= (1 + night);
                } else {
                    jumpscare(freddy);
                }
            }
        }

        // At-door kill check: animatronic at OFFICE with door open
        for (Animatronic a : new Animatronic[]{monty, roxanne, chica}) {
            if (a.atOffice() && !a.officeEntryResolved) {
                a.officeTimer += dt;
                // Grace window: door still closable for a short moment
                if (a.doorSide < 0 && leftDoorClosed) {
                    a.officeEntryResolved = true;
                    a.retreat();
                } else if (a.doorSide > 0 && rightDoorClosed) {
                    a.officeEntryResolved = true;
                    a.retreat();
                } else if (a.officeTimer > 3.0) {
                    // door was open the whole grace window — kill
                    jumpscare(a);
                }
            }
        }
    }

    void enterBlackout() {
        if (status != Status.PLAYING) return;
        status = Status.POWER_OUT;
        leftDoorClosed = false;
        rightDoorClosed = false;
        leftLightOn = false;
        rightLightOn = false;
        cameraUp = false;
        blackoutTimer = 0;
        blackoutMusicPlaying = true;
    }

    void jumpscare(Animatronic a) {
        status = Status.JUMPSCARED;
        jumpscareBy = a;
    }

    /** Toggle helpers (called by UI / bot). */
    public void toggleLeftDoor() {
        if (status == Status.PLAYING && power > 0) leftDoorClosed = !leftDoorClosed;
    }
    public void toggleRightDoor() {
        if (status == Status.PLAYING && power > 0) rightDoorClosed = !rightDoorClosed;
    }
    public void toggleLeftLight() {
        if (status == Status.PLAYING && power > 0) leftLightOn = !leftLightOn;
    }
    public void toggleRightLight() {
        if (status == Status.PLAYING && power > 0) rightLightOn = !rightLightOn;
    }
    public void toggleCamera() {
        if (status == Status.PLAYING && power > 0) cameraUp = !cameraUp;
    }
    public void setCam(int cam) {
        currentCam = cam;
    }
}
