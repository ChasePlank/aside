package aside.games.fnaf9;

/**
 * Every rectangle the FNAF 9 screen draws, and the hit test that answers for
 * them.
 *
 * <p>Pure geometry with no JavaFX in it, so the self-test can check every
 * region without a display. A button that is clickable somewhere it is not
 * drawn is the classic mouse bug, and the only way to catch it is to have one
 * copy of the rectangle that both the drawing and the hit test read.
 *
 * <p>The layout is a band, a room and a strip:
 *
 * <pre>
 *   +--------------------------------------------------+  y=0
 *   |  NIGHT 3   12 AM  [ FEED 2.4s BEHIND ] [ DOOR ]  |  the band
 *   +--------------------------------------------------+  y=70
 *   |                                                  |
 *   |  the office: one monitor, 1280x526               |  the room
 *   |  (clicking here is nothing)                      |  y=596
 *   +--------------------------------------------------+  y=596
 *   | [MON LEFT][MON RIGHT][ DARK ][  HOLD  ]          |  the strip
 *   +--------------------------------------------------+  y=720
 * </pre>
 *
 * <p>Four buttons, and the four of them are the whole control surface --
 * because there are only four things the one circuit can do. FNAF 7 had four
 * buttons and the difficulty was that two of them could not be used at once.
 * FNAF 8 had five and the difficulty was that four of them were the same lamp.
 * FNAF 9 has four and the difficulty is that <b>three of them are eyes and the
 * fourth one blinds you.</b>
 */
public final class MouseMap {

    public static final double W = 1280, H = 720;

    /** The top band: night, clock, feed age and the door sensor. */
    public static final double[] BAND = {0, 0, W, 70};

    /** The room. Clicking here does nothing at all. */
    public static final double[] SCENE = {0, 70, W, 526};

    /** The strip along the bottom. */
    public static final double[] STRIP = {0, 596, W, 124};

    /** The four buttons, left to right, all the same size. */
    static final double BTN_W = 240, BTN_H = 58, BTN_Y = 626, BTN_GAP = 24;

    /** Show the left hall on the monitor. The picture it shows is old. */
    public static final double[] MON_LEFT = btn(0);
    /** Show the right hall on the monitor. */
    public static final double[] MON_RIGHT = btn(1);
    /** Everything off. The feed catches up at full speed. */
    public static final double[] DARK = btn(2);
    /** Bring the door down. It takes a second, and it blinds you. */
    public static final double[] HOLD = btn(3);

    /** The feed-age readout, in the band. */
    public static final double[] FEED = {560, 8, 380, 54};
    /** The door sensor, in the band. */
    public static final double[] SENSOR = {960, 8, 296, 54};

    /** The monitor's screen, in the room. */
    public static final double[] SCREEN = {170, 110, 940, 440};

    public static final int NIGHTS = 5;

    /** The night rows on the night-select screen. */
    public static final double NIGHT_ROW_H = 56, NIGHT_TOP = 250, NIGHT_X = 440,
            NIGHT_W = 400;

    static double[] btn(int i) {
        double total = 4 * BTN_W + 3 * BTN_GAP;
        double x = (W - total) / 2 + i * (BTN_W + BTN_GAP);
        return new double[]{x, BTN_Y, BTN_W, BTN_H};
    }

    public enum Kind { NONE, SCENE, MON_LEFT, MON_RIGHT, DARK, HOLD, NIGHT }

    /** What a point is over. {@code index} is a night number, or 0. */
    public record Hit(Kind kind, int index) {
        public static final Hit NONE = new Hit(Kind.NONE, 0);
    }

    public static boolean in(double[] r, double x, double y) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
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
     * <p>The strip wins over the scene, because it is drawn on top of it and a
     * button that is visible but not clickable is worse than no button.
     */
    public static Hit hit(double x, double y) {
        if (in(MON_LEFT, x, y)) return new Hit(Kind.MON_LEFT, 0);
        if (in(MON_RIGHT, x, y)) return new Hit(Kind.MON_RIGHT, 0);
        if (in(DARK, x, y)) return new Hit(Kind.DARK, 0);
        if (in(HOLD, x, y)) return new Hit(Kind.HOLD, 0);
        if (in(SCENE, x, y)) return new Hit(Kind.SCENE, 0);
        return Hit.NONE;
    }

    private MouseMap() {}
}
