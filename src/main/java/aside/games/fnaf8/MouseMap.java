package aside.games.fnaf8;

/**
 * Every rectangle the FNAF 8 screen draws, and the hit test that answers
 * for them.
 *
 * <p>Pure geometry with no JavaFX in it, so the self-test can check every
 * region without a display. A button that is clickable somewhere it is not
 * drawn is the classic mouse bug, and the only way to catch it is to have
 * one copy of the rectangle that both the drawing and the hit test read.
 *
 * <p>The layout is a room, a band and a strip:
 *
 * <pre>
 *   +--------------------------------------------------+  y=0
 *   |  NIGHT 3   12 AM   [ how far apart they are ]    |  the band
 *   +--------------------------------------------------+  y=70
 *   |                                                  |
 *   |  the office: two doorways, 1280x526              |  the room
 *   |  (clicking here is nothing)                      |  y=596
 *   +--------------------------------------------------+  y=596
 *   | [LOOK L][LOOK R][ OFF ][PUSH L][PUSH R]          |  the strip
 *   +--------------------------------------------------+  y=720
 * </pre>
 *
 * <p>Five buttons, and the five of them are the whole control surface:
 * <b>look</b> at a hall (cheap, shows you where it is), <b>push</b> at a
 * hall (expensive, moves it back, and the other one gains on you for it),
 * and <b>off</b>. FNAF 7 had four buttons and the difficulty was that two
 * of them could not be used at once. FNAF 8 has five and the difficulty is
 * that <i>four of them are the same lamp.</i>
 */
public final class MouseMap {

    public static final double W = 1280, H = 720;

    /** The top band: night, clock, and how far apart they are. */
    public static final double[] BAND = {0, 0, W, 70};

    /** The room. Clicking here does nothing at all. */
    public static final double[] SCENE = {0, 70, W, 526};

    /** The strip along the bottom. */
    public static final double[] STRIP = {0, 596, W, 124};

    /** The five buttons, left to right, all the same size. */
    static final double BTN_W = 200, BTN_H = 58, BTN_Y = 626, BTN_GAP = 20;

    /** Look at the left hall. Cheap: the other one gains a little. */
    public static final double[] LOOK_L = btn(0);
    /** Look at the right hall. */
    public static final double[] LOOK_R = btn(1);
    /** Lamp out. Both of them walk. */
    public static final double[] OFF = btn(2);
    /** Push the left hall back. Expensive: the other one gains a lot. */
    public static final double[] PUSH_L = btn(3);
    /** Push the right hall back. */
    public static final double[] PUSH_R = btn(4);

    /** The distance readout, in the band. */
    public static final double[] APART = {700, 8, 556, 54};

    public static final int NIGHTS = 5;

    /** The night rows on the night-select screen. */
    public static final double NIGHT_ROW_H = 56, NIGHT_TOP = 250, NIGHT_X = 440,
            NIGHT_W = 400;

    static double[] btn(int i) {
        double total = 5 * BTN_W + 4 * BTN_GAP;
        double x = (W - total) / 2 + i * (BTN_W + BTN_GAP);
        return new double[]{x, BTN_Y, BTN_W, BTN_H};
    }

    public enum Kind { NONE, SCENE, LOOK_L, LOOK_R, OFF, PUSH_L, PUSH_R, NIGHT }

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
     * <p>The strip wins over the scene, because it is drawn on top of it
     * and a button that is visible but not clickable is worse than no
     * button.
     */
    public static Hit hit(double x, double y) {
        if (in(LOOK_L, x, y)) return new Hit(Kind.LOOK_L, 0);
        if (in(LOOK_R, x, y)) return new Hit(Kind.LOOK_R, 0);
        if (in(OFF, x, y)) return new Hit(Kind.OFF, 0);
        if (in(PUSH_L, x, y)) return new Hit(Kind.PUSH_L, 0);
        if (in(PUSH_R, x, y)) return new Hit(Kind.PUSH_R, 0);
        if (in(SCENE, x, y)) return new Hit(Kind.SCENE, 0);
        return Hit.NONE;
    }

    private MouseMap() {}
}
