package aside.games.fnaf2.engine;

/**
 * One animatronic in FNAF 2.
 *
 * Movement is FNAF's dice roll: every `moveInterval` seconds, roll a d20
 * against `aiLevel`; success advances one node along `path`. When the
 * path runs out they are standing in their opening, and the opening's
 * answer decides what happens next.
 *
 * The difference from FNAF 1 is that nothing here can be blocked. There
 * are no doors. An animatronic in an opening is answered by the mask, or
 * by the hall light (Foxy), or by nothing at all (Balloon Boy) -- and
 * answering costs you the music box, because the mask takes the camera
 * and the lights with it.
 */
public class Animatronic {

    /** Which opening this one arrives at. */
    public enum Opening { HALL, VENT_L, VENT_R }

    /** What makes it go away. */
    public enum Answer { MASK, LIGHT, NONE }

    public final String name;
    /** Room ids walked in order. Running out of path == standing in the opening. */
    public final int[] path;
    public final Opening opening;
    public final Answer answer;
    /** Balloon Boy does not kill. He takes the lights and leaves. */
    public final boolean lethal;

    public int pathIndex = 0;
    public int aiLevel;
    public final double moveInterval;
    public final Game game;

    // ---- Staged arrival (Withered Foxy) ----
    /** True for the staged sprinter: he does not walk the path, he waits. */
    public boolean staged = false;
    public int stages = 0;              // 0 hidden, 1 peeking, 2 emerged, 3 in the hall
    /** Seconds left in the hall before he is through. */
    public double hallWindow = 0;
    /** Seconds of hall light accumulated on him this visit. */
    public double repelTimer = 0;
    /** True when he has just arrived in the hall -- the sound cue fires once. */
    public boolean arrivalAnnounced = false;

    // ---- Opening state ----
    public boolean resolved = false;
    public double officeTimer = 0;

    double moveTimer = 0;

    public Animatronic(String name, int[] path, Opening opening, Answer answer,
                       boolean lethal, int aiLevel, double moveInterval, Game game) {
        this.name = name;
        this.path = path;
        this.opening = opening;
        this.answer = answer;
        this.lethal = lethal;
        this.aiLevel = aiLevel;
        this.moveInterval = moveInterval;
        this.game = game;
    }

    public int currentRoom() {
        return pathIndex < path.length ? path[pathIndex] : Game.OFFICE;
    }

    public boolean atOpening() {
        return !staged && pathIndex >= path.length;
    }

    /** Send it back the way it came. FNAF animatronics retreat a few
     *  nodes, not all the way -- a repelled visitor is still nearby. */
    public void retreat() {
        pathIndex = Math.max(0, pathIndex - 2);
        resolved = false;
        officeTimer = 0;
    }

    public void update(double dt) {
        if (game.status != Game.Status.PLAYING) return;
        if (aiLevel <= 0) return;

        if (staged) {
            updateStaged(dt);
            return;
        }

        if (atOpening()) return;   // handled by Game's opening pass

        moveTimer += dt;
        if (moveTimer < moveInterval) return;
        moveTimer = 0;

        if (game.rng.nextInt(20) < aiLevel) {
            pathIndex++;
            if (atOpening()) {
                officeTimer = 0;
                resolved = false;
                // Announced, like Foxy's arrival. A visitor used to reach the
                // office in silence, and since the monitor hides the office
                // there was no way to know it had happened until the
                // jumpscare -- Chase's playtest note, verbatim: "I checked
                // lights, went to wind the music box, and got jumpscared. If
                // the timer between entering and killing is 5 seconds, then
                // they arent visible." The grace is what makes the arrival
                // survivable; the cue is what makes it fair.
                game.cue("at_door");
            }
        }
    }

    /**
     * Withered Foxy: waits in Kid's Cove, advances a stage on a roll, and
     * once he is out he is running. Being watched on his camera freezes
     * him; being ignored lets him move faster.
     *
     * When he reaches the hall he gets `FOXY_HALL_WINDOW` seconds. The
     * hall light accumulates against him -- a flash counts, because that
     * is how anyone actually uses a flashlight.
     */
    void updateStaged(double dt) {
        if (stages >= 3) {
            if (!arrivalAnnounced) {
                arrivalAnnounced = true;
                hallWindow = Game.FOXY_HALL_WINDOW;
                repelTimer = 0;
                game.cue("at_door");
            }
            if (game.hallLightOn) {
                repelTimer += dt;
                if (repelTimer >= Game.FOXY_REPEL_TIME) {
                    repelled();
                    return;
                }
            }
            hallWindow -= dt;
            if (hallWindow <= 0) {
                game.jumpscare(this);
            }
            return;
        }

        // Watched on his own camera: frozen.
        if (game.cameraUp && game.currentCam == Game.COVE_CAM) return;

        moveTimer += dt;
        if (moveTimer < moveInterval) return;
        moveTimer = 0;

        if (game.rng.nextInt(20) < aiLevel) {
            stages++;
            if (stages >= 3) {
                arrivalAnnounced = false;
                hallWindow = Game.FOXY_HALL_WINDOW;
                repelTimer = 0;
            }
        }
    }

    void repelled() {
        stages = 0;
        arrivalAnnounced = false;
        hallWindow = 0;
        repelTimer = 0;
        moveTimer = 0;
        game.cue("door_close");
    }
}
