package aside.games.fnaf.engine;

import aside.ui.Audio;
import aside.ui.UiManager;
import aside.ui.UiScreen;

import java.util.Random;

/**
 * One animatronic. FNAF movement: every `moveInterval` seconds, roll
 * d20 against AI level; success -> advance one path node. At the office
 * node, the door check decides (Game handles the kill/retreat logic).
 */
public class Animatronic {
    public final String name;
    public final int[] path;
    public int pathIndex = 0;
    public int aiLevel;
    public final double moveInterval;
    public final Game game;

    public int doorSide = 0;       // -1 left, +1 right
    public boolean isFoxy = false;  // Foxy-role staged sprinter

    // Foxy-role staging
    public int stages = 0;
    public double sprintTimer = 0;
    public boolean sprintResolved = false;

    // Office-entry state
    public boolean officeEntryResolved = false;
    public double officeTimer = 0;

    public boolean stalled = false;  // camera-shy stall (Monty)

    double moveTimer = 0;

    public Animatronic(String name, int startRoom, int[] path, int aiLevel,
                       double moveInterval, Game game) {
        this.name = name;
        this.path = path;
        this.aiLevel = aiLevel;
        this.moveInterval = moveInterval;
        this.game = game;
    }

    public int currentRoom() {
        return path[pathIndex];
    }

    public boolean atOffice() {
        return pathIndex == path.length - 1;
    }

    public void retreat() {
        // After being blocked, go back a few rooms (FNAF animatronics
        // retreat to an earlier node, not all the way)
        pathIndex = Math.max(0, pathIndex - 2);
        officeEntryResolved = false;
        officeTimer = 0;
    }

    public void update(double dt) {
        if (game.status != Game.Status.PLAYING) return;
        if (aiLevel <= 0) return;
        if (stalled) return;

        moveTimer += dt;
        if (moveTimer < moveInterval) return;
        moveTimer = 0;

        if (isFoxy) {
            // Foxy-role: roll-gated STAGE advance (peek -> emerge -> run).
            // Camera on his cove stalls him; camera OFF for long lets him
            // advance faster (classic Foxy anti-idle).
            if (game.cameraUp && game.currentCam == 3) {
                return;  // watched — frozen
            }
            if (game.rng.nextInt(20) < aiLevel) {
                stages++;
                if (stages >= 3) {
                    sprintTimer = 0;
                    sprintResolved = false;
                }
            }
            return;
        }

        // Standard movement roll (d20 vs AI level)
        if (game.rng.nextInt(20) < aiLevel) {
            if (pathIndex < path.length - 1) {
                pathIndex++;
                officeEntryResolved = false;
                officeTimer = 0;
            }
        }
    }
}
