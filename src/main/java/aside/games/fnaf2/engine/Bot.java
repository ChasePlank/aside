package aside.games.fnaf2.engine;

import java.util.Random;

/**
 * Blind survival bot. Plays with ONLY player-visible information:
 *
 *   - the lights (who is standing in the hall, left vent, right vent)
 *   - the camera feed (who is in the viewed room, and Foxy's cove stage)
 *   - the music box level, the hour, the night number
 *
 * No reading of AI levels, move timers, or internal state. That
 * restriction is the whole point: a bot that reads `pathIndex` directly
 * would survive a game no person could, and would tell us nothing.
 *
 * Strategy -- a competent FNAF 2 player:
 *
 *   - The music box is the clock. Wind it when it drops, in short bursts,
 *     because every second spent winding is a second not watching an
 *     opening.
 *   - Sweep the three openings with their lights between winds. The hall
 *     first: two of the three hall threats are the fast ones.
 *   - Mask the moment anyone is standing in an opening, and drop it as
 *     soon as they are gone -- the mask costs the camera and the lights.
 *   - Flash the hall light rather than holding it: a flash repels Foxy
 *     (the repel timer accumulates) and costs nothing.
 */
public class Bot {

    // Phases of the rotation.
    static final int WIND = 0;
    static final int CHECK_HALL = 1;
    static final int CHECK_VENT_L = 2;
    static final int CHECK_VENT_R = 3;
    static final int MASK = 4;

    final Game game;
    final Random rng;

    // ---- Strategy knobs (tuned by sweeping, not by playing) ----
    /** Start winding once the box drops below this. */
    public double windBelow = 62.0;
    /** Wind until the box is back above this. */
    public double windUntil = 96.0;
    /** Longest single winding burst. A long burst is a long blind spell. */
    public double windBurst = 1.0;
    /** How long each opening light stays on. */
    public double lightHold = 0.45;
    /** How long the mask stays up once a threat is seen. */
    public double maskHold = 1.1;

    int phase = WIND;
    double phaseT = 0;
    boolean sawThreat = false;

    public Bot(Game game, long seed) {
        this.game = game;
        this.rng = new Random(seed);
    }

    // ---- Player-visible sensors ----

    /** Who is standing in the hall, as the hall light shows it. */
    public Animatronic hallOccupant() {
        if (!game.hallLightOn) return null;
        for (Animatronic a : game.visitors()) {
            if (a.atOpening() && a.opening == Animatronic.Opening.HALL) return a;
        }
        return null;
    }

    /** Who is standing in a vent, as that vent's light shows it. */
    public Animatronic ventOccupant(Animatronic.Opening side) {
        boolean lit = side == Animatronic.Opening.VENT_L
                ? game.ventLLightOn : game.ventRLightOn;
        if (!lit) return null;
        for (Animatronic a : game.visitors()) {
            if (a.atOpening() && a.opening == side) return a;
        }
        return null;
    }

    /** Foxy is in the hall. He is not a mask problem -- he is a light problem. */
    public boolean foxyInHall() {
        return game.witheredFoxy.stages >= 3;
    }

    // ---- Play ----

    public void play(double dt) {
        if (game.status != Game.Status.PLAYING) return;

        phaseT += dt;

        switch (phase) {
            case WIND -> {
                game.maskOn = false;
                game.setLight(Game.NONE);
                game.cameraUp = true;
                game.currentCam = Game.MUSIC_BOX_CAM;
                game.setWinding(true);
                if (game.musicBox >= windUntil || phaseT >= windBurst) {
                    game.setWinding(false);
                    game.cameraUp = false;
                    next(CHECK_HALL);
                }
            }
            case CHECK_HALL -> {
                game.cameraUp = false;
                game.setLight(Game.HALL);
                // Foxy is repelled by the light itself; anyone else in the
                // hall needs the mask.
                if (hallOccupant() != null) sawThreat = true;
                if (phaseT >= lightHold) {
                    game.setLight(Game.NONE);
                    if (sawThreat) next(MASK); else next(CHECK_VENT_L);
                }
            }
            case CHECK_VENT_L -> {
                game.setLight(Game.VENT_L);
                if (ventOccupant(Animatronic.Opening.VENT_L) != null) sawThreat = true;
                if (phaseT >= lightHold) {
                    game.setLight(Game.NONE);
                    if (sawThreat) next(MASK); else next(CHECK_VENT_R);
                }
            }
            case CHECK_VENT_R -> {
                game.setLight(Game.VENT_R);
                if (ventOccupant(Animatronic.Opening.VENT_R) != null) sawThreat = true;
                if (phaseT >= lightHold) {
                    game.setLight(Game.NONE);
                    if (sawThreat) next(MASK); else next(decide());
                }
            }
            case MASK -> {
                game.maskOn = true;
                game.cameraUp = false;
                game.setWinding(false);
                if (phaseT >= maskHold) {
                    game.maskOn = false;
                    next(decide());
                }
            }
            default -> next(WIND);
        }
    }

    void next(int p) {
        phase = p;
        phaseT = 0;
        sawThreat = false;
    }

    /** Where the rotation goes when nothing is wrong: wind if the box is
     *  low, otherwise keep sweeping. */
    int decide() {
        return game.musicBox < windBelow ? WIND : CHECK_HALL;
    }
}
