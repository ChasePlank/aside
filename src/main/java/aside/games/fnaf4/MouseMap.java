package aside.games.fnaf4;

import aside.games.fnaf4.engine.Room;

/**
 * Every rectangle the FNAF 4 screen draws, and the hit test that answers
 * for them.
 *
 * Pure geometry with no JavaFX in it, so the self-test can check every
 * region without a display. That is not tidiness: a button that is
 * clickable somewhere it is not drawn is the classic mouse bug, and the
 * only way to catch it is to have one copy of the rectangle that both the
 * drawing and the hit test read.
 *
 * The layout is a scene and a strip:
 *
 * <pre>
 *   +--------------------------------------------+  y=0
 *   |  the room, 1280x646                        |
 *   |  (clicking anywhere in here is the light)  |
 *   +--------------------------------------------+  y=646
 *   |  [ the bed ][ left ][ right ][ closet ]    |  y=656  the strip
 *   +--------------------------------------------+  y=720
 * </pre>
 *
 * The strip is the one concession the game makes to being a game. FNAF 4
 * proper has no HUD at all, and this one has four words and a marker,
 * because the thing the player has to hold in their head is <i>where they
 * are</i> and a room you cannot see is a room you cannot be sure of. The
 * strip says where you are and nothing else -- it does not say where
 * anything else is, which is what the light and the listening are for.
 */
public final class MouseMap {

    public static final double W = 1280, H = 720;

    /** The room. Clicking here is the flashlight. */
    public static final double[] SCENE = {0, 0, W, 646};

    /** The strip along the bottom. */
    public static final double[] BAR = {0, 646, W, 74};

    /** One button per station, in {@link Room#ALL} order. */
    public static final double BTN_W = 190, BTN_H = 54, BTN_GAP = 16, BTN_Y = 656;
    public static final double BTN_X =
            (W - (Room.ALL.length * BTN_W + (Room.ALL.length - 1) * BTN_GAP)) / 2;

    /** The noise meter, in the top left under the night number. */
    public static final double[] NOISE = {40, 58, 220, 8};

    public static final int NIGHTS = 5;

    /** The night rows on the night-select screen. */
    public static final double NIGHT_ROW_H = 56, NIGHT_TOP = 250, NIGHT_X = 440,
            NIGHT_W = 400;

    public enum Kind { NONE, SCENE, STATION, NIGHT }

    /** What a point is over. {@code index} is a station or night number. */
    public record Hit(Kind kind, int index) {
        public static final Hit NONE = new Hit(Kind.NONE, 0);
    }

    static boolean in(double[] r, double x, double y) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    /** The rectangle for station {@code i} (0-based). */
    public static double[] station(int i) {
        return new double[]{BTN_X + i * (BTN_W + BTN_GAP), BTN_Y, BTN_W, BTN_H};
    }

    /** The rectangle for a station. */
    public static double[] station(Room.Where w) {
        return station(w.ordinal());
    }

    /** Which station a point is over, or -1. */
    public static int stationAt(double x, double y) {
        for (int i = 0; i < Room.ALL.length; i++) {
            if (in(station(i), x, y)) return i;
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
     * The strip wins over the scene, because the strip is drawn on top of
     * it and a button that is visible but not clickable is worse than no
     * button. Everything else in the room is the light.
     */
    public static Hit hit(double x, double y) {
        int s = stationAt(x, y);
        if (s >= 0) return new Hit(Kind.STATION, s);
        if (in(SCENE, x, y)) return new Hit(Kind.SCENE, 0);
        return Hit.NONE;
    }

    private MouseMap() {}
}
