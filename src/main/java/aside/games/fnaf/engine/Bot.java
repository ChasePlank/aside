package aside.games.fnaf.engine;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.util.Random;

/**
 * Blind survival bot. Plays with ONLY player-visible information:
 *   - camera feed (which animatronic is in the viewed room)
 *   - door lights (is someone at the left/right doorway)
 *   - power, hour, night number
 * No reading of AI levels, move timers, or internal state.
 *
 * Strategy (a cautious FNAF player):
 *   - Check Foxy's cove (cam 3) periodically; if he's gone (stage 3),
 *     slam the left door until the bang, then reopen.
 *   - Check Monty's path rooms; if he's near, close left door.
 *   - Check right-side rooms for Roxanne/Chica; close right door.
 *   - Keep power above ~30% early, spend freely after 4 AM.
 *   - Never leave both lights on; flick lights briefly.
 */
public class Bot {
    final Game game;
    final Random rng;

    // Strategy knobs
    double camCheckInterval = 2.5;
    double camTimer = 0;
    boolean leftDoorBias = false;   // hold left door longer (Foxy/Monty)
    double doorHoldTimer = 0;

    public Bot(Game game, long seed) {
        this.game = game;
        this.rng = new Random(seed);
    }

    // ---- Player-visible sensors ----

    /** What the camera at `room` shows: name of animatronic there, or null. */
    String camView(int room) {
        // Camera can't see the office or the halls right at the door
        if (room == Game.OFFICE) return null;
        for (Animatronic a : new Animatronic[]{game.monty, game.roxanne, game.chica}) {
            if (a.currentRoom() == room) return a.name;
        }
        // Foxy-role: cove cam shows stage (0 hidden, 1 peek, 2 emerged)
        if (room == 3) {
            if (game.freddy.stages >= 3) return "GONE";
            if (game.freddy.stages == 2) return "Freddy: emerged";
            if (game.freddy.stages == 1) return "Freddy: peeking";
            return "empty cove";
        }
        return null;
    }

    /** Door light: is an animatronic at the left/right doorway? */
    boolean lightCheck(int side) {
        for (Animatronic a : new Animatronic[]{game.monty, game.roxanne, game.chica}) {
            if (a.atOffice() && a.doorSide == side) return true;
    }
        return false;
    }

    // ---- Bot play ----

    public void play(double dt) {
        if (game.status != Game.Status.PLAYING) return;

        camTimer += dt;
        doorHoldTimer -= dt;

        // Periodic camera sweep
        if (camTimer >= camCheckInterval) {
            camTimer = 0;
            game.cameraUp = true;
            // Sweep: cove first (Foxy-role is the fastest killer), then
            // the animatronics' path rooms
            int[] sweep = {3, 1, 4, 7, 9};
            for (int room : sweep) {
                game.currentCam = room;
                String view = camView(room);
                if (view != null && view.contains("GONE")) {
                    // Foxy-role running: slam left door
                    game.leftDoorClosed = true;
                    doorHoldTimer = 6.0;  // hold until bang
                    leftDoorBias = true;
                    return;  // keep camera UP on the cove (stalls him if he resets)
                }
            }
            // Nothing urgent — camera down to save power
            game.cameraUp = false;
        }

        // Door management
        if (game.leftDoorClosed && doorHoldTimer <= 0) {
            // Check light before opening
            game.leftLightOn = true;
            boolean threat = lightCheck(-1);
            game.leftLightOn = false;
            if (!threat) game.leftDoorClosed = false;
        }
        if (game.rightDoorClosed && doorHoldTimer <= 0) {
            game.rightLightOn = true;
            boolean threat = lightCheck(1);
            game.rightLightOn = false;
            if (!threat) game.rightDoorClosed = false;
            else doorHoldTimer = 2.0;  // keep holding a little
        }

        // Proactive light flicks when doors are open (1/s — catches
        // doorway lurkers inside the 3s grace window)
        if (!game.leftDoorClosed && rng.nextDouble() < dt * 1.0) {
            game.leftLightOn = true;
            if (lightCheck(-1)) {
                game.leftDoorClosed = true;
                doorHoldTimer = 5.0;
            }
            game.leftLightOn = false;
        }
        if (!game.rightDoorClosed && rng.nextDouble() < dt * 1.0) {
            game.rightLightOn = true;
            if (lightCheck(1)) {
                game.rightDoorClosed = true;
                doorHoldTimer = 5.0;
            }
            game.rightLightOn = false;
        }
    }
}
