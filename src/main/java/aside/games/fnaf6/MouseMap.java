package aside.games.fnaf6;

/**
 * Every rectangle the FNAF 6 screen draws, and the hit test that answers
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
 *   |  NIGHT 3     12 AM     [ agitation ]   [ SHOCK ] |  the band
 *   +--------------------------------------------------+  y=70
 *   |                                                  |
 *   |  the salvage room, 1280x526                      |  the chair, and
 *   |  (clicking here is nothing)                      |  the dark
 *   +--------------------------------------------------+  y=596
 *   | [ LAMP ]   what the lamp is showing              |  the strip
 *   +--------------------------------------------------+  y=720
 * </pre>
 *
 * <p>There are exactly two buttons, because there are exactly two things
 * you can do. That is not a simplification of the game; it is the game.
 * FNAF 5 had five rooms, a monitor, a shock and a walk. FNAF 6 has a lamp
 * and a shock, and the whole difficulty is that you cannot afford to use
 * either of them as often as you would like.
 */
public final class MouseMap {

    public static final double W = 1280, H = 720;

    /** The top band: night, clock, agitation, shock. */
    public static final double[] BAND = {0, 0, W, 70};

    /** The room. Clicking here does nothing at all. */
    public static final double[] SCENE = {0, 70, W, 526};

    /** The strip along the bottom. */
    public static final double[] BAR = {0, 596, W, 124};

    /** The lamp switch, at the left of the strip. */
    public static final double[] LAMP = {24, 626, 210, 58};

    /** The shock button, in the band on the right. */
    public static final double[] SHOCK = {1046, 16, 194, 38};

    /** The agitation meter, in the band in the middle-right. */
    public static final double[] METER = {820, 22, 190, 26};

    // ---- The thing in the chair, as the screen draws it ------------------
    //
    // There is one picture per unit and no picture per pose: the pose is
    // drawn, by scaling the unit up and raising it out of the chair. These
    // four numbers are that drawing, and they live here rather than in
    // GameScreen because the phone build draws the same figure and has to
    // draw it the same way. A unit that rose differently on a phone would
    // be a unit whose pose the player reads differently, and the pose is
    // the only thing on the screen they have to read at a glance.

    /** How tall a unit is at the top of the climb, in scene pixels. */
    public static final double UNIT_H = 470;
    /** The scale it is drawn at while it is still slumped. */
    public static final double UNIT_SCALE_MIN = 0.45;
    /** The tilt it is drawn at while it is still slumped, in degrees. */
    public static final double UNIT_TILT = -8.0;
    /**
     * How far below the scene's floor line the unit's feet sit.
     *
     * <p>Below the line on purpose, so the desk covers them and what the
     * player sees is a figure rising rather than a figure standing on the
     * table.
     */
    public static final double UNIT_BOTTOM = 26;

    public static final int NIGHTS = 5;

    /** The night rows on the night-select screen. */
    public static final double NIGHT_ROW_H = 56, NIGHT_TOP = 250, NIGHT_X = 440,
            NIGHT_W = 400;

    public enum Kind { NONE, SCENE, LAMP, SHOCK, NIGHT }

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
     * <p>The band and the strip both win over the scene, because they are
     * drawn on top of it and a button that is visible but not clickable is
     * worse than no button.
     */
    public static Hit hit(double x, double y) {
        if (in(SHOCK, x, y)) return new Hit(Kind.SHOCK, 0);
        if (in(LAMP, x, y)) return new Hit(Kind.LAMP, 0);
        if (in(SCENE, x, y)) return new Hit(Kind.SCENE, 0);
        return Hit.NONE;
    }

    private MouseMap() {}
}
