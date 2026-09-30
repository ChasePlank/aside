package aside.games.fnaf2;

/**
 * Where a click lands, and what it means.
 *
 * Pure geometry, no game state and no JavaFX, for two reasons:
 *
 *  - SelfTest can check every region without a display, which is the only
 *    way to be sure a button is clickable where it is drawn.
 *  - The numbers a click is tested against are the same numbers the screen
 *    draws with, so a button cannot be drawn in one place and clickable in
 *    another. That is the whole class of bug this file exists to prevent.
 *
 * Coordinates are CANVAS pixels -- the fixed 1280x720 the screen draws in
 * (see UiScreen). The office art is 1600x768 fitted by width, so the
 * openings are given in source pixels and converted here exactly as the
 * renderer converts them.
 */
public final class MouseMap {

    private MouseMap() {}

    public static final int W = 1280, H = 720;

    static final double OS = W / 1600.0;
    static final double OW = 1600 * OS;
    static final double OH = 768 * OS;
    static final double OX = 0;
    static final double OY = (H - OH) / 2;

    /** Opening rectangles, in source pixels. */
    public static final double[] HALL_SRC   = {545, 175, 510, 465};
    public static final double[] VENT_L_SRC = {40, 415, 185, 275};
    public static final double[] VENT_R_SRC = {1375, 415, 185, 275};

    /**
     * The music box camera. Mirrors {@code Game.MUSIC_BOX_CAM} -- this file
     * cannot import the game without dragging JavaFX into the headless
     * self-test, so the two copies are held together by an assertion
     * instead. See SelfTest.
     */
    public static final int MUSIC_BOX_CAM = 11;

    /**
     * Buttons, in canvas pixels.
     *
     * Both are live in BOTH views, at the same place, because the two things
     * a player reaches for while the monitor is up are the two things that
     * take it away: the mask, and the monitor toggle itself. The toggle is
     * one button wearing the label for what it will do next.
     *
     * That is why the music box panel sits high (see GameScreen): the bottom
     * strip of the screen belongs to the buttons in both views, so a click
     * never means two things depending on which screen is up.
     */
    public static final double[] MASK_BTN    = {W / 2 - 170, H - 74, 150, 48};
    public static final double[] MONITOR_BTN = {W / 2 + 20,  H - 74, 150, 48};

    /**
     * The music box panel on CAM 11, and the bar inside it.
     *
     * Defined here rather than in the screen so the layout can be checked:
     * the panel has to stop short of the button strip, and the bar has to be
     * a comfortable target, and both of those are assertions rather than
     * things to notice in a screenshot.
     */
    public static final double MUSIC_BOX_BAR_W = 520;
    public static final double MUSIC_BOX_BAR_H = 34;
    public static final double MUSIC_BOX_BAR_X = (W - MUSIC_BOX_BAR_W) / 2;
    public static final double MUSIC_BOX_BAR_Y = H - 214;
    /** The panel's background, as drawn. */
    public static final double[] MUSIC_BOX_PANEL = {
            MUSIC_BOX_BAR_X - 24, MUSIC_BOX_BAR_Y - 54,
            MUSIC_BOX_BAR_W + 48, MUSIC_BOX_BAR_H + 96 };
    /** The bar itself, with a little margin: 34px is a thin thing to ask a
     *  hand to hit. */
    public static final double[] WIND_BTN = {
            MUSIC_BOX_BAR_X, MUSIC_BOX_BAR_Y - 8,
            MUSIC_BOX_BAR_W, MUSIC_BOX_BAR_H + 16 };

    /**
     * The camera strip: one row per camera, down the right edge.
     *
     * It starts below the clock (drawn top-right at y 48 and 76) and stops
     * above the control hint (baseline y 694), so the eleven rows fit
     * between two things that were already there.
     */
    public static final double CAM_STRIP_X = W - 150;
    public static final double CAM_STRIP_W = 130;
    public static final double CAM_STRIP_Y = 96;
    public static final double CAM_ROW_H = 48;
    public static final double CAM_ROW_GAP = 5;

    public enum Kind { NONE, HALL, VENT_L, VENT_R, MASK, MONITOR, WIND, CAM }

    public record Hit(Kind kind, int cam) {
        public static final Hit NONE = new Hit(Kind.NONE, 0);
        public static Hit cam(int n) { return new Hit(Kind.CAM, n); }
    }

    /** A source-pixel rectangle, in canvas pixels. */
    public static double[] rect(double[] src) {
        return new double[]{ OX + src[0] * OS, OY + src[1] * OS, src[2] * OS, src[3] * OS };
    }

    /** The row for camera {@code cam} (1..11). */
    public static double[] camRow(int cam) {
        return new double[]{ CAM_STRIP_X,
                             CAM_STRIP_Y + (cam - 1) * (CAM_ROW_H + CAM_ROW_GAP),
                             CAM_STRIP_W, CAM_ROW_H };
    }

    /**
     * The night-select rows. Five of them, and the highlight is drawn from
     * the same rectangle a click is tested against, so the row that lights
     * up is the row that starts.
     */
    public static final int NIGHTS = 5;
    public static final double NIGHT_ROW_W = 400;
    public static final double NIGHT_ROW_H = 46;
    public static final double NIGHT_ROW_X = (W - NIGHT_ROW_W) / 2;
    public static final double NIGHT_ROW_Y = 206;
    public static final double NIGHT_ROW_STEP = 52;

    public static double[] nightRow(int i) {
        return new double[]{ NIGHT_ROW_X, NIGHT_ROW_Y + i * NIGHT_ROW_STEP,
                             NIGHT_ROW_W, NIGHT_ROW_H };
    }

    /** Which night a point is on, or -1 for none of them. */
    public static int nightAt(double x, double y) {
        for (int i = 0; i < NIGHTS; i++) {
            if (inside(nightRow(i), x, y)) return i;
        }
        return -1;
    }

    static boolean inside(double[] r, double x, double y) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    /** Centre of a rectangle -- what a test clicks when it wants "this one". */
    public static double[] centre(double[] r) {
        return new double[]{ r[0] + r[2] / 2, r[1] + r[3] / 2 };
    }

    /**
     * What a click at (x, y) means.
     *
     * While the mask is on, every click lowers it. The mask covers the room,
     * so there is nothing behind it to aim at, and the only thing a player
     * wearing it can want is out.
     *
     * The office is not clickable with the monitor up: the camera view is
     * over it, and a click that reached through would be a click at
     * something the player cannot see.
     */
    public static Hit hit(double x, double y, boolean cameraUp, boolean maskOn, int cam) {
        if (maskOn) return new Hit(Kind.MASK, 0);

        if (cameraUp) {
            // The two ways out of the monitor, before anything drawn on it.
            if (inside(MASK_BTN, x, y)) return new Hit(Kind.MASK, 0);
            if (inside(MONITOR_BTN, x, y)) return new Hit(Kind.MONITOR, 0);
            if (cam == MUSIC_BOX_CAM && inside(WIND_BTN, x, y)) return new Hit(Kind.WIND, 0);
            for (int c = 1; c <= MUSIC_BOX_CAM; c++) {
                if (inside(camRow(c), x, y)) return Hit.cam(c);
            }
            return Hit.NONE;
        }

        // Buttons before openings: they are the smaller, nearer thing, and
        // nothing should be able to shadow them.
        if (inside(MASK_BTN, x, y)) return new Hit(Kind.MASK, 0);
        if (inside(MONITOR_BTN, x, y)) return new Hit(Kind.MONITOR, 0);
        if (inside(rect(HALL_SRC), x, y)) return new Hit(Kind.HALL, 0);
        if (inside(rect(VENT_L_SRC), x, y)) return new Hit(Kind.VENT_L, 0);
        if (inside(rect(VENT_R_SRC), x, y)) return new Hit(Kind.VENT_R, 0);
        return Hit.NONE;
    }
}
