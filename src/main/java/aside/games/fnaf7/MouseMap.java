package aside.games.fnaf7;

/**
 * Every rectangle the FNAF 7 screen draws, and the hit test that answers
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
 *   |  NIGHT 3   12 AM   [ filament ]  [ IT EXPECTS ]  |  the band
 *   +--------------------------------------------------+  y=70
 *   |                                                  |
 *   |  the office: two doorways, 1280x526              |  the room
 *   |  (clicking here is nothing)                      |
 *   +--------------------------------------------------+  y=596
 *   | [ LIGHT L ] [ LIGHT R ]     [ BAR L ] [ BAR R ]  |  the strip
 *   +--------------------------------------------------+  y=720
 * </pre>
 *
 * <p>There are exactly four buttons, because there are exactly four
 * things you can do: point the light at one side or the other, and put the
 * bar on one door or the other. That is not a simplification of the game;
 * it is the game. FNAF 5 had five rooms, a monitor, a shock and a walk.
 * FNAF 6 had a lamp and a shock. FNAF 7 has two hands, and the whole
 * difficulty is that they cannot do both jobs at once.
 */
public final class MouseMap {

    public static final double W = 1280, H = 720;

    /** The top band: night, clock, filament, readout. */
    public static final double[] BAND = {0, 0, W, 70};

    /** The room. Clicking here does nothing at all. */
    public static final double[] SCENE = {0, 70, W, 526};

    /** The strip along the bottom. */
    public static final double[] STRIP = {0, 596, W, 124};

    /** Point the light at the left hall. */
    public static final double[] LIGHT_L = {24, 626, 210, 58};

    /** Point the light at the right hall. */
    public static final double[] LIGHT_R = {248, 626, 210, 58};

    /** Put the bar on the left door. */
    public static final double[] BAR_L = {822, 626, 210, 58};

    /** Put the bar on the right door. */
    public static final double[] BAR_R = {1046, 626, 210, 58};

    /** The filament meter, in the band. */
    public static final double[] FILAMENT = {330, 22, 260, 26};

    /** The readout, in the band on the right. */
    public static final double[] READOUT = {700, 8, 556, 54};

    public static final int NIGHTS = 5;

    /** The night rows on the night-select screen. */
    public static final double NIGHT_ROW_H = 56, NIGHT_TOP = 250, NIGHT_X = 440,
            NIGHT_W = 400;

    public enum Kind { NONE, SCENE, LIGHT_L, LIGHT_R, BAR_L, BAR_R, NIGHT }

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
        if (in(LIGHT_L, x, y)) return new Hit(Kind.LIGHT_L, 0);
        if (in(LIGHT_R, x, y)) return new Hit(Kind.LIGHT_R, 0);
        if (in(BAR_L, x, y)) return new Hit(Kind.BAR_L, 0);
        if (in(BAR_R, x, y)) return new Hit(Kind.BAR_R, 0);
        if (in(SCENE, x, y)) return new Hit(Kind.SCENE, 0);
        return Hit.NONE;
    }

    private MouseMap() {}
}
