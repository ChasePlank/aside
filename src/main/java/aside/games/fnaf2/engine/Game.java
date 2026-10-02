package aside.games.fnaf2.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FNAF 2 core loop.
 *
 * FNAF 1 blocks with doors and rations power. FNAF 2 <b>cannot block
 * anything</b> -- it hides behind a mask and rations attention. Every
 * defence is a choice about what you are not watching.
 *
 * The office has three openings and no doors:
 *
 * <pre>
 *   [ LEFT VENT ]   [   HALL   ]   [ RIGHT VENT ]
 * </pre>
 *
 *   - The HALL light is also the flashlight. It repels Withered Foxy.
 *   - The two VENT lights reveal, but reveal nothing the mask answers.
 *   - The FREDDY MASK fools most of them. It blocks the camera and every
 *     light while worn, so hiding costs you the music box.
 *   - The MUSIC BOX (CAM 11) drains in ~55s and winds to full in ~5s.
 *     If it empties, the Puppet comes and cannot be stopped. This is the
 *     real clock.
 *
 * There is deliberately no power meter. FNAF 2 does not ration the
 * flashlight; the tension is the music box and the mask.
 *
 * The engine is pure logic with no UI dependency, so the survival sim
 * can be played by a bot at 60fps with no display.
 */
public class Game {

    // ---- Clock ----
    public static final double HOUR_SECONDS = 45.0;
    public static final int NIGHT_HOURS = 6;

    // ---- Music box ----
    public static final double MUSIC_BOX_MAX = 100.0;
    /** Empty in ~55 seconds if never wound. This is the real clock. */
    public static final double MUSIC_BOX_DRAIN = MUSIC_BOX_MAX / 55.0;
    /** Full in ~5 seconds of holding W on CAM 11. */
    public static final double MUSIC_BOX_WIND = MUSIC_BOX_MAX / 5.0;
    /**
     * The box drains faster as the week goes on -- the same move the real
     * game makes. It is the honest difficulty lever, because it does not
     * make any single threat harder to answer; it makes the player spend
     * more of the night with the camera up, which is time not spent
     * watching an opening.
     */
    public static final double[] DRAIN_MULT = {1.0, 1.2, 1.4, 1.6, 1.8, 2.0};
    /** Once it empties the Puppet is coming and nothing stops it. */
    public static final double PUPPET_GRACE = 6.0;

    // ---- Foxy ----
    /** How long he stands in the hall before he is through. */
    public static final double FOXY_HALL_WINDOW = 5.0;
    /** Seconds of hall light that repel him. Accumulates, so a flash counts. */
    public static final double FOXY_REPEL_TIME = 0.25;

    // ---- Openings ----
    /**
     * How long an unanswered visitor stands in an opening before acting.
     * A more aggressive animatronic gives you less time, the same way a
     * higher AI level means more movement rolls -- it is one difficulty
     * knob, not two.
     */
    public static final double OPENING_GRACE_MAX = 3.0;
    public static final double OPENING_GRACE_MIN = 2.0;

    // ---- Cameras ----
    public static final int OFFICE = 0;
    public static final int COVE_CAM = 3;       // Kid's Cove -- Foxy's stage tell
    public static final int MUSIC_BOX_CAM = 11; // Prize Corner -- the music box
    public static final int CAM_COUNT = 11;

    public int night = 1;

    public double time = 0;
    public int hour = 0;

    // ---- Player state ----
    public boolean hallLightOn = false;
    public boolean ventLLightOn = false;
    public boolean ventRLightOn = false;
    public boolean maskOn = false;
    public boolean cameraUp = false;
    public int currentCam = 1;

    /** Balloon Boy takes these for the rest of the night. */
    public boolean lightsDisabled = false;
    /** Set while the player holds W on the music box camera. */
    public boolean winding = false;

    public double musicBox = MUSIC_BOX_MAX;
    /** Counts up once the box has emptied. Nothing resets it. */
    public double puppetTimer = 0;
    public boolean puppetComing = false;

    // ---- Outcome ----
    public enum Status { PLAYING, JUMPSCARED, SURVIVED }
    public Status status = Status.PLAYING;
    public Animatronic jumpscareBy = null;

    // ---- The cast ----
    public final Animatronic toyFreddy;      // hall, mask
    public final Animatronic witheredFoxy;   // hall, light
    public final Animatronic toyBonnie;      // right vent, mask
    public final Animatronic toyChica;       // right vent, mask
    public final Animatronic mangle;         // right vent, mask
    public final Animatronic witheredBonnie; // left vent, mask
    public final Animatronic balloonBoy;     // left vent, mask -- but not lethal
    /** Not a dice roll. A consequence. */
    public final Animatronic puppet;

    public final Random rng;

    /** Sound cues raised this frame. The screen drains them; the engine
     *  itself has no audio dependency. */
    private final List<String> cues = new ArrayList<>();

    public Game(int night, long seed) {
        this.night = night;
        this.rng = new Random(seed);

        int lv = aiLevel(night);

        // Paths walk rooms toward the office. The last node before the
        // opening is the vent/hall mouth.
        toyFreddy = new Animatronic("Toy Freddy", new int[]{1, 2, 4},
                Animatronic.Opening.HALL, Animatronic.Answer.MASK, true,
                lv, 5.02, this);
        toyBonnie = new Animatronic("Toy Bonnie", new int[]{1, 2, 5, 7},
                Animatronic.Opening.VENT_R, Animatronic.Answer.MASK, true,
                lv, 4.96, this);
        toyChica = new Animatronic("Toy Chica", new int[]{1, 2, 6, 7},
                Animatronic.Opening.VENT_R, Animatronic.Answer.MASK, true,
                lv, 4.99, this);
        mangle = new Animatronic("Mangle", new int[]{1, 2, 5, 7},
                Animatronic.Opening.VENT_R, Animatronic.Answer.MASK, true,
                lv, 5.04, this);
        witheredBonnie = new Animatronic("Withered Bonnie", new int[]{1, 2, 9, 10},
                Animatronic.Opening.VENT_L, Animatronic.Answer.MASK, true,
                lv, 5.01, this);
        balloonBoy = new Animatronic("Balloon Boy", new int[]{1, 2, 9, 10},
                Animatronic.Opening.VENT_L, Animatronic.Answer.MASK, false,
                lv, 4.94, this);

        witheredFoxy = new Animatronic("Withered Foxy", new int[]{COVE_CAM},
                Animatronic.Opening.HALL, Animatronic.Answer.LIGHT, true,
                lv, 5.03, this);
        witheredFoxy.staged = true;

        puppet = new Animatronic("The Puppet", new int[]{MUSIC_BOX_CAM},
                Animatronic.Opening.HALL, Animatronic.Answer.NONE, true,
                20, 1.0, this);
    }

    /** How long an unanswered visitor waits on this night. */
    public double openingGrace() {
        double t = Math.min(1.0, aiLevel(night) / 20.0);
        return OPENING_GRACE_MAX - t * (OPENING_GRACE_MAX - OPENING_GRACE_MIN);
    }

    /** How fast the box empties on this night. */
    public double drainRate() {
        return MUSIC_BOX_DRAIN * DRAIN_MULT[Math.min(Math.max(night - 1, 0),
                DRAIN_MULT.length - 1)];
    }

    /**
     * AI level per night. FNAF 2 is busier than FNAF 1 -- there are more
     * of them and you cannot shut any of them out -- so the ramp is
     * gentler at the start and steeper at the end.
     */
    public static int aiLevel(int night) {
        int[] table = {2, 6, 10, 14, 17, 20};
        return table[Math.min(Math.max(night - 1, 0), table.length - 1)];
    }

    // ---- Sound ----

    /** Raise a cue for this frame. */
    public void cue(String name) {
        cues.add(name);
    }

    /** Take the cues raised since the last call. */
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

        // The music box. Always draining; wound only while the player
        // holds W on its camera, which the mask makes impossible.
        if (winding && cameraUp && currentCam == MUSIC_BOX_CAM && !maskOn) {
            musicBox = Math.min(MUSIC_BOX_MAX, musicBox + MUSIC_BOX_WIND * dt);
        } else {
            musicBox -= drainRate() * dt;
        }
        if (musicBox <= 0) {
            musicBox = 0;
            if (!puppetComing) {
                puppetComing = true;
                puppetTimer = 0;
                cue("music_box");
            }
        }
        if (puppetComing) {
            puppetTimer += dt;
            if (puppetTimer >= PUPPET_GRACE) {
                jumpscare(puppet);
                return;
            }
        }

        // Movement
        toyFreddy.update(dt);
        toyBonnie.update(dt);
        toyChica.update(dt);
        mangle.update(dt);
        witheredBonnie.update(dt);
        balloonBoy.update(dt);
        witheredFoxy.update(dt);

        // Opening pass.
        //
        // ORDER MATTERS, and this is the bug the first bot run found.
        // Defensive state changes (the mask going up) must be evaluated
        // BEFORE state-clearing effects (Balloon Boy taking the lights).
        // With the two the other way round, BB reached the office, killed
        // the lights, and *then* the mask was consulted -- so a player
        // already wearing the mask still lost the lights, and with the
        // lights gone Withered Foxy became unrepellable for the rest of
        // the night. One ordering mistake, cascading. Survival went from
        // 6/60 to 59/60 when it was fixed.
        answerOpenings(dt);
    }

    /**
     * Walk every opening and decide what the player's current state does
     * about whoever is standing there.
     */
    void answerOpenings(double dt) {
        for (Animatronic a : visitors()) {
            if (!a.atOpening() || a.resolved) continue;

            // 1. The mask answers first. It is the defensive state change.
            if (a.answer == Animatronic.Answer.MASK && maskOn) {
                a.resolved = true;
                a.retreat();
                cue("door_close");
                continue;
            }

            // 2. Then anything the visitor does to the office.
            a.officeTimer += dt;
            if (a.officeTimer < openingGrace()) continue;

            if (a.answer == Animatronic.Answer.NONE || !a.lethal) {
                // Balloon Boy: he walks in, laughs, and takes the lights
                // for the rest of the night. A slow death, not a fast one.
                if (!lightsDisabled) {
                    lightsDisabled = true;
                    setLight(NONE);
                    cue("power_down");
                }
                a.resolved = true;
                a.retreat();
                continue;
            }

            jumpscare(a);
            return;
        }
    }

    /** Everyone who can reach an opening. */
    public Animatronic[] visitors() {
        return new Animatronic[]{toyFreddy, toyBonnie, toyChica, mangle,
                witheredBonnie, balloonBoy};
    }

    /**
     * Is anybody standing at the office right now?
     *
     * This is the monitor's warning, and it is deliberately coarse: it says
     * that someone is there, not which opening. Lowering the monitor to look
     * is the decision the game is about, so the warning must not make that
     * decision for the player.
     *
     * It covers Foxy in the hall as well as the six who walk a path, because
     * the failure it exists to prevent -- being on the monitor when something
     * arrives -- is the same failure for all seven.
     */
    public boolean someoneAtTheOffice() {
        if (witheredFoxy.stages >= 3) return true;
        for (Animatronic a : visitors()) {
            if (a.atOpening() && !a.resolved) return true;
        }
        return false;
    }

    /** Everyone, for the camera sweep. */
    public Animatronic[] cast() {
        return new Animatronic[]{toyFreddy, witheredFoxy, toyBonnie, toyChica,
                mangle, witheredBonnie, balloonBoy};
    }

    void jumpscare(Animatronic a) {
        status = Status.JUMPSCARED;
        jumpscareBy = a;
        cue("scare_" + a.name.toLowerCase().replace(" ", ""));
    }

    // ---- Controls ----
    // Every one of these is a no-op once the night is over, and the
    // lights are no-ops while the mask is up or once BB has taken them.

    public void toggleHallLight() {
        if (lightsBlocked()) return;
        setLight(hallLightOn ? NONE : HALL);
        cue("light_click");
    }

    public void toggleVentLLight() {
        if (lightsBlocked()) return;
        setLight(ventLLightOn ? NONE : VENT_L);
        cue("light_click");
    }

    public void toggleVentRLight() {
        if (lightsBlocked()) return;
        setLight(ventRLightOn ? NONE : VENT_R);
        cue("light_click");
    }

    boolean lightsBlocked() {
        return status != Status.PLAYING || lightsDisabled || maskOn;
    }

    /**
     * Only one light at a time.
     *
     * This is not a limitation, it is the game: you have one flashlight
     * and three openings, so looking at one is choosing not to look at
     * the other two. It also matches the art -- the office is a whole
     * view per light state, and there is no view with two lights on.
     */
    public void setLight(int which) {
        hallLightOn = which == HALL;
        ventLLightOn = which == VENT_L;
        ventRLightOn = which == VENT_R;
    }

    public static final int NONE = 0, HALL = 1, VENT_L = 2, VENT_R = 3;

    /**
     * The mask. Blocks the camera and every light while worn -- so the
     * moment you hide is the moment you stop watching the music box.
     */
    public void toggleMask() {
        if (status != Status.PLAYING) return;
        maskOn = !maskOn;
        if (maskOn) {
            cameraUp = false;
            setLight(NONE);
            winding = false;
        }
        cue("camera_down");
    }

    public void toggleCamera() {
        if (status != Status.PLAYING || maskOn) return;
        cameraUp = !cameraUp;
        // You cannot wind a box you are not looking at. Without this, a
        // winding flag set by a click survives the monitor going down and
        // resumes by itself the next time CAM 11 comes up -- a state the
        // keyboard never produced, because releasing W always cleared it.
        if (!cameraUp) winding = false;
        cue(cameraUp ? "camera_up" : "camera_down");
    }

    public void setCam(int cam) {
        if (cam < 1 || cam > CAM_COUNT) return;
        currentCam = cam;
        cue("static");
    }

    public void setWinding(boolean on) {
        winding = on;
    }
}
