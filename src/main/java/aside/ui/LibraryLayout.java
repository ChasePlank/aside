package aside.ui;

/**
 * The library list's geometry, with no JavaFX in it.
 *
 * This exists because the library failed three times at a row count it had
 * not seen yet, and all three were arithmetic that nothing was checking.
 *
 *   1. A fixed 88px pitch put the newest game at y=830 on a 720 canvas, so
 *      adding a game silently hid it.
 *   2. At eleven rows the selection bar reached up into the row above, and
 *      the highlight of the newest game sat on the second-newest game's
 *      description.
 *   3. At thirteen rows -- FNAF 2 arriving -- the titles were drawn on top of
 *      the blurbs above them.
 *
 * The fix for all three was to derive the pitch and the type size from the
 * row count instead of fixing them. That works, and it runs out: at fifteen
 * rows the titles are already at their 15px floor and the blurb-to-title gap
 * is 0.7px. Sixteen rows does not fit at any type size worth reading.
 *
 * So the fourth answer is not a smaller type size. It is to stop pretending
 * the list has to fit. **The list scrolls.** The pitch has a floor, the
 * window shows what fits at that floor, and the row count stops being a
 * cliff -- which is what it has been for three failures running.
 *
 * What is left to check is a different invariant, and a stronger one: for
 * any number of rows and any selection, the selected row is inside the
 * window. That is checked exhaustively in aside.engine.SelfTest, for every
 * row count up to sixty and every selection in it, because it is the one
 * property a scrolling list can get wrong in a way that hides a game.
 *
 * The two spacing constraints are unchanged and still both checked:
 *
 *   1. The selection bar of one row must not reach the blurb of the row
 *      above it. That gap is pitch*(1 - BAR_FRACTION) - BLURB_OFFSET.
 *   2. The blurb of one row must not reach the TITLE of the row below it.
 *      That gap is pitch - BLURB_OFFSET - BLURB_DESCENT - titleAscent.
 *
 * The second is the tighter one, and it was never checked until the third
 * failure came through it.
 */
public final class LibraryLayout {

    public static final double CANVAS_W = 1280, CANVAS_H = 720;

    // --- the type, first, because everything below is derived from it ------

    /** Title font sizes. Georgia's ascent is about 0.77 of its size. */
    public static final double MAX_TITLE = 26;
    public static final double MIN_TITLE = 15;
    public static final double TITLE_ASCENT_RATIO = 0.77;
    /** Georgia's descender, as a fraction of its size. */
    public static final double TITLE_DESCENT_RATIO = 0.22;
    /** Arial 13's descender, in pixels. */
    public static final double BLURB_DESCENT = 4;
    /** Arial 15's ascent, in pixels. The volume notice is Arial 15. */
    public static final double NOTICE_ASCENT = 14;

    // --- the vertical frame -------------------------------------------------

    /** Baseline of the first row. */
    public static final double LIST_TOP = 178;
    /**
     * The footer, derived from the bottom of the canvas up.
     *
     * "The canvas is 720 tall" is not the same statement as "nothing is drawn
     * below 648": the volume notice is drawn at CANVAS_H - 60 and the hint at
     * CANVAS_H - 28, and the notice is the highest thing in the footer. Its
     * ascent is what the list has to clear, so the footer's top is the
     * notice's baseline minus its ascent rather than a round number chosen to
     * look about right.
     */
    public static final double HINT_BASELINE = CANVAS_H - 28;
    public static final double NOTICE_BASELINE = CANVAS_H - 60;
    public static final double FOOTER_TOP = NOTICE_BASELINE - NOTICE_ASCENT;
    /** Nothing above this may be overlapped by the first row's bar. */
    public static final double HEADER_BOTTOM = 140;

    // --- the row ------------------------------------------------------------

    /** A row's blurb baseline sits this far below its title. */
    public static final double BLURB_OFFSET = 17;
    /** The least room to leave between a blurb and the title below it. */
    public static final double ROW_GAP_MIN = 5;
    /** The bar's top edge sits this fraction of a pitch above its row's baseline. */
    public static final double BAR_FRACTION = 0.45;
    /** The bar is this much shorter than a pitch. */
    public static final double BAR_INSET = 4;

    /** Never pitch rows further apart than this, however few there are. */
    public static final double MAX_PITCH = 88;
    /**
     * Never pitch them closer than this. This is the floor that makes the
     * list scroll: below it the rows stop fitting and the window starts
     * moving instead of the type shrinking.
     *
     * 56 is chosen so the title can stay at its full 26px -- the tightest
     * pitch that still clears the blurb-to-title constraint with room to
     * spare is 46 -- and so eight rows fit between LIST_TOP and LIST_BOTTOM.
     */
    public static final double MIN_PITCH = 56;

    /**
     * The last row's baseline.
     *
     * The margin below it is the deepest a *blurb* can descend, not a title.
     * That is the change the scroll forced: while the list had to fit, the
     * last row was always the Quit row, and the Quit row has no blurb -- so a
     * margin sized for a title was enough. Once the list scrolls, any row can
     * be the last visible one, and every game has a blurb. The render showed
     * it immediately: the ninth row's blurb was drawn through the volume
     * notice.
     *
     * The old margin was `FOOTER_TOP - 5`, with a comment saying 5 and not 3
     * because the deepest a minimum-size title descends is 3.3px. That was
     * true while the titles shrank to 15px to make the list fit. With the
     * pitch floored the titles are 26px, the descent is 5.7px, and 5 is
     * smaller than the thing it is a margin for -- which is exactly what the
     * old comment said not to do. Both margins are derived now.
     */
    public static final double LIST_BOTTOM = FOOTER_TOP - BLURB_OFFSET - BLURB_DESCENT;

    /** The scrollbar's track, and the width of its thumb. */
    public static final double BAR_TRACK_X = CANVAS_W - 46;
    public static final double BAR_TRACK_TOP = LIST_TOP - 22;
    public static final double BAR_TRACK_BOTTOM = LIST_BOTTOM + 18;
    public static final double BAR_TRACK_W = 4;

    /**
     * Row pitch for a given number of rows.
     *
     * Derived, never fixed -- but now bounded below as well as above. Few
     * rows still spread out; many rows stop closing up and start scrolling.
     */
    public static double pitch(int rows) {
        if (rows <= 1) return MAX_PITCH;
        double fit = (LIST_BOTTOM - LIST_TOP) / (rows - 1);
        return Math.max(MIN_PITCH, Math.min(MAX_PITCH, fit));
    }

    /**
     * How many rows the window shows at once.
     *
     * The most that fit at MIN_PITCH, which is also the most that fit at any
     * pitch, since the pitch never goes below it.
     */
    public static int visibleRows() {
        return (int) Math.floor((LIST_BOTTOM - LIST_TOP) / MIN_PITCH) + 1;
    }

    /**
     * Which row the window starts on, for a given selection.
     *
     * Minimal scroll, not centred: the window moves only when the selection
     * would otherwise leave it. Centring looks better in a screenshot and
     * moves the whole list under the player's eye on every keypress, which
     * is the wrong thing for a list you are reading down.
     *
     * The invariant this has to hold -- and the one the suite checks for
     * every row count up to sixty and every selection in it -- is
     * start <= selected < start + shown, with the window inside the list.
     */
    public static int windowStart(int selected, int rows) {
        int shown = Math.min(rows, visibleRows());
        if (rows <= shown || shown <= 0) return 0;
        int start = 0;
        if (selected >= shown) start = selected - shown + 1;
        if (start > rows - shown) start = rows - shown;
        if (start < 0) start = 0;
        return start;
    }

    /** Baseline of row i, in a window showing `shown` rows. */
    public static double rowY(int i, int shown) {
        return LIST_TOP + i * pitch(shown);
    }

    /** Top edge of the selection bar drawn on row i. */
    public static double barTop(int i, int shown) {
        return rowY(i, shown) - pitch(shown) * BAR_FRACTION;
    }

    /** Bottom edge of the selection bar drawn on row i. */
    public static double barBottom(int i, int shown) {
        return barTop(i, shown) + pitch(shown) - BAR_INSET;
    }

    /** Baseline of row i's blurb. */
    public static double blurbY(int i, int shown) {
        return rowY(i, shown) + BLURB_OFFSET;
    }

    /** How much room is left between one row's bar and the blurb above it. */
    public static double clearance(int rows) {
        return pitch(rows) * (1 - BAR_FRACTION) - BLURB_OFFSET;
    }

    /**
     * Title size for a given row count.
     *
     * Derived, like the pitch, and for the same reason: a fixed size is a
     * size that is right until the row count changes, and then it is wrong
     * in a way nothing reports. With the pitch floored, this now settles at
     * MAX_TITLE for any list long enough to scroll, which is the point of
     * the floor.
     */
    public static double titleSize(int rows) {
        double room = pitch(rows) - BLURB_OFFSET - BLURB_DESCENT - ROW_GAP_MIN;
        return Math.max(MIN_TITLE, Math.min(MAX_TITLE, room / TITLE_ASCENT_RATIO));
    }

    /** How far the last row's title descends below its baseline. */
    public static double titleDescent(int rows) {
        return titleSize(rows) * TITLE_DESCENT_RATIO;
    }

    /** How much room is left between one row's blurb and the title below it. */
    public static double rowGap(int rows) {
        return pitch(rows) - BLURB_OFFSET - BLURB_DESCENT
                - titleSize(rows) * TITLE_ASCENT_RATIO;
    }

    /**
     * The most rows this geometry holds *without scrolling*.
     *
     * Kept because it is the number a reader wants -- "nine fit at once" --
     * and because it is the row count at which the window starts moving.
     * It is no longer a limit on how many games the library can hold.
     */
    public static int maxRows() {
        return visibleRows();
    }

    private LibraryLayout() {}
}
