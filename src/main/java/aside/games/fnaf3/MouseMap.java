package aside.games.fnaf3;

/**
 * Every rectangle the FNAF 3 screen draws, and the hit test that answers
 * for them.
 *
 * Pure geometry with no JavaFX in it, so the self-test can check every
 * region without a display. That is not tidiness: a button that is
 * clickable somewhere it is not drawn is the classic mouse bug, and the
 * only way to catch it is to have one copy of the rectangle that both the
 * drawing and the hit test read.
 *
 * The layout is three bands, because the office art is 1400x537 against a
 * 1280x720 canvas and fitting it leaves 114px of black at the top and
 * bottom. Those bars are not waste -- they are where the panels and the
 * clock go, which is why the office can be shown whole instead of cropped.
 *
 * <pre>
 *   +--------------------------------------------+  y=0    top band: clock
 *   |  office art, fitted to width               |  y=114
 *   |  [window]                     [vent]       |
 *   +--------------------------------------------+  y=605
 *   |  AUDIO   VENTILATION   CAMERAS   MONITOR   |  y=605  bottom band
 *   +--------------------------------------------+  y=720
 * </pre>
 */
public final class MouseMap {

    public static final double W = 1280, H = 720;

    /** The office art, fitted to the canvas width and centred. */
    public static final double OW = 1280, OH = 490.9, OX = 0, OY = 114.5;

    /** Where Springtrap is drawn when he is at the window (left). */
    public static final double[] WINDOW = {40, 150, 390, 425};
    /** And when he is in the vent (right). */
    public static final double[] VENT = {950, 150, 300, 425};

    /** The bottom band: three maintenance panels and the monitor toggle. */
    public static final double[] PANEL_AUDIO = {60, 622, 280, 76};
    public static final double[] PANEL_VENT = {370, 622, 280, 76};
    public static final double[] PANEL_CAM = {680, 622, 280, 76};
    public static final double[] PANEL_MONITOR = {1010, 622, 210, 76};

    /** The camera feed, fitted inside the office band. */
    public static final double FEED_W = 623, FEED_H = 490.9;
    public static final double FEED_X = (W - FEED_W) / 2, FEED_Y = OY;

    /** The camera strip down the right of the monitor view. */
    public static final double[] STRIP = {1000, 130, 250, 460};
    public static final int NIGHTS = 5;

    /**
     * The lure button, live only while the monitor is up.
     *
     * It sits exactly where the AUDIO panel sits, because they are the
     * same control seen from two sides: on the office wall it is a panel
     * you fix, and on the monitor it is a button you press. Giving it a
     * fourth slot would have made the bottom band four panels wide and
     * left the audio state unreadable from the monitor, which is the one
     * view where you actually need to know whether the lure is available.
     */
    public static final double[] LURE_BTN = {60, 622, 280, 76};

    /** The night rows on the night-select screen. */
    public static final double NIGHT_ROW_H = 56, NIGHT_TOP = 250, NIGHT_X = 440,
            NIGHT_W = 400;

    public enum Kind { NONE, MONITOR, AUDIO, VENT, CAM, CAM_ROW, LURE, NIGHT }

    /** What a point is over. {@code index} is the camera or night number. */
    public record Hit(Kind kind, int index) {
        public static final Hit NONE = new Hit(Kind.NONE, 0);
    }

    static boolean in(double[] r, double x, double y) {
        return x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3];
    }

    /** The row rectangle for camera {@code cam} (1..10). */
    public static double[] camRow(int cam) {
        double h = (STRIP[3] - 20) / 10.0;
        return new double[]{STRIP[0] + 10, STRIP[1] + 10 + (cam - 1) * h,
                STRIP[2] - 20, h};
    }

    /** Which camera a point is over, or 0. */
    public static int camAt(double x, double y) {
        for (int i = 1; i <= 10; i++) if (in(camRow(i), x, y)) return i;
        return 0;
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
     * The hit test for the office screen.
     *
     * The monitor toggle is live in both views -- it is the one control
     * that has to work from wherever you are, because it is the control
     * that takes you somewhere else. The camera strip and the lure only
     * exist while the monitor is up, and the panels only while it is down,
     * because the panels are on the office wall.
     */
    public static Hit hit(double x, double y, boolean cameraUp) {
        if (in(PANEL_MONITOR, x, y)) return new Hit(Kind.MONITOR, 0);
        if (cameraUp) {
            int cam = camAt(x, y);
            if (cam > 0) return new Hit(Kind.CAM_ROW, cam);
            if (in(LURE_BTN, x, y)) return new Hit(Kind.LURE, 0);
            return Hit.NONE;
        }
        if (in(PANEL_AUDIO, x, y)) return new Hit(Kind.AUDIO, 0);
        if (in(PANEL_VENT, x, y)) return new Hit(Kind.VENT, 0);
        if (in(PANEL_CAM, x, y)) return new Hit(Kind.CAM, 0);
        return Hit.NONE;
    }

    private MouseMap() {}
}
