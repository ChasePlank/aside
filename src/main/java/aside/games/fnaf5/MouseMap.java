package aside.games.fnaf5;

import aside.games.fnaf5.engine.Room;

/**
 * Every rectangle the FNAF 5 screen draws, and the hit test that answers
 * for them.
 *
 * Pure geometry with no JavaFX in it, so the self-test can check every
 * region without a display. A button that is clickable somewhere it is not
 * drawn is the classic mouse bug, and the only way to catch it is to have
 * one copy of the rectangle that both the drawing and the hit test read.
 *
 * The layout is a monitor and a strip:
 *
 * <pre>
 *   +--------------------------------------------+  y=0
 *   |  NIGHT 3          12 AM          [SHOCK 4] |  the band
 *   +--------------------------------------------+  y=70
 *   |                                            |
 *   |  the camera feed, 1280x526                 |  never the room
 *   |  (clicking here is nothing)                |  you are standing in
 *   +--------------------------------------------+  y=596
 *   | [&lt; MOVE] [0][1][2][3][4] [MOVE &gt;]          |  the strip
 *   +--------------------------------------------+  y=720
 * </pre>
 *
 * The strip is the one concession the game makes to being a game. It says
 * where you are and which room the camera is on, and nothing else -- it
 * does not say where anything else is, because that is what the monitor
 * and the listening are for. The five tiles are camera selectors, not
 * destinations: you cannot click a room to walk to it, because you can
 * only walk one room at a time and only into a room you can see.
 */
public final class MouseMap {

    public static final double W = 1280, H = 720;

    /** The top band: night, clock, shocks. */
    public static final double[] BAND = {0, 0, W, 70};

    /** The camera feed. Clicking here does nothing at all. */
    public static final double[] SCENE = {0, 70, W, 526};

    /** The strip along the bottom. */
    public static final double[] BAR = {0, 596, W, 124};

    /** The shock button, in the band on the right. */
    public static final double[] SHOCK = {1046, 16, 194, 38};

    /** The two walk buttons, at the ends of the strip. */
    public static final double[] MOVE_L = {24, 630, 116, 52};
    public static final double[] MOVE_R = {1140, 630, 116, 52};

    /** One camera tile per room, in {@link Room#ALL} order. */
    public static final double TILE_W = 184, TILE_H = 56, TILE_GAP = 8, TILE_Y = 628;
    public static final double TILE_X =
            (W - (Room.COUNT * TILE_W + (Room.COUNT - 1) * TILE_GAP)) / 2;

    public static final int NIGHTS = 5;

    /** The night rows on the night-select screen. */
    public static final double NIGHT_ROW_H = 56, NIGHT_TOP = 250, NIGHT_X = 440,
            NIGHT_W = 400;

    public enum Kind { NONE, SCENE, TILE, MOVE_LEFT, MOVE_RIGHT, SHOCK, NIGHT }

    /** What a point is over. {@code index} is a room or a night number. */
    public record Hit(Kind kind, int index) {
        public static final Hit NONE = new Hit(Kind.NONE, 0);
    }

    static boolean in(double[] r, double x, double y) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    /** The rectangle for camera tile {@code i} (0-based). */
    public static double[] tile(int i) {
        return new double[]{TILE_X + i * (TILE_W + TILE_GAP), TILE_Y, TILE_W, TILE_H};
    }

    /** The rectangle for a room's tile. */
    public static double[] tile(Room.Where w) {
        return tile(Room.index(w));
    }

    /** Which tile a point is over, or -1. */
    public static int tileAt(double x, double y) {
        for (int i = 0; i < Room.COUNT; i++) {
            if (in(tile(i), x, y)) return i;
        }
        return -1;
    }

    /** The row rectangle for night {@code n} (1..5). */
    public static double[] nightRow(int n) {
        return new double[]{NIGHT_X, NIGHT_TOP + (n - 1) * NIGHT_ROW_H,
                NIGHT_W, NIGHT_ROW_H - 8};
    }

    /** Which night a point is over, or 0. */
    public static int nightAt(double x, double y) {
        for (int i = 1; i <= NIGHTS; i++) if (in(nightRow(i), x, y)) return i;
        return 0;
    }

    /**
     * The hit test for the room.
     *
     * The band and the strip both win over the scene, because they are
     * drawn on top of it and a button that is visible but not clickable is
     * worse than no button.
     */
    public static Hit hit(double x, double y) {
        if (in(SHOCK, x, y)) return new Hit(Kind.SHOCK, 0);
        if (in(MOVE_L, x, y)) return new Hit(Kind.MOVE_LEFT, 0);
        if (in(MOVE_R, x, y)) return new Hit(Kind.MOVE_RIGHT, 0);
        int t = tileAt(x, y);
        if (t >= 0) return new Hit(Kind.TILE, t);
        if (in(SCENE, x, y)) return new Hit(Kind.SCENE, 0);
        return Hit.NONE;
    }

    private MouseMap() {}
}
