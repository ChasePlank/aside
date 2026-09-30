package aside.ui;

/**
 * The library list's geometry, with no JavaFX in it.
 *
 * This exists because the library has now failed twice at a row count it had
 * not seen yet. Once with a fixed 88px pitch, which put the newest game at
 * y=830 on a 720 canvas, so adding a game silently hid it. Once at eleven
 * rows, where the selection bar reached up into the row above and the
 * highlight of the newest game sat on top of the second-newest game's
 * description.
 *
 * Both were arithmetic. Both were invisible in the code. Both were found by
 * rendering a frame and looking at it, which is a fine way to find something
 * and a terrible way to be told about it. Pulling the arithmetic out of the
 * screen means it can be checked without a window, so the next row count does
 * not have to be discovered the same way. See aside.engine.SelfTest.
 *
 * There are TWO binding constraints, not one, and checking only the first
 * is how the third failure happened.
 *
 *   1. The selection bar of one row must not reach the blurb of the row
 *      above it. That gap is pitch*(1 - BAR_FRACTION) - BLURB_OFFSET.
 *   2. The blurb of one row must not reach the TITLE of the row below it.
 *      That gap is pitch - BLURB_OFFSET - BLURB_DESCENT - titleAscent.
 *
 * The second is the tighter one, and it was never checked. At twelve rows
 * the first constraint reported 5.5px of room while the second was already
 * at 1px, and at thirteen -- FNAF 2 arriving -- the titles were drawn on
 * top of the blurbs above them. Found by rendering the library, which is
 * the third time this file has been written because of a frame.
 *
 * The fix is the same principle as the pitch: do not fix the type size
 * either. titleSize(rows) shrinks the titles as the rows go up, so the
 * second constraint holds by construction rather than by luck, and
 * maxRows() is where it stops holding.
 */
public final class LibraryLayout {

    public static final double CANVAS_W = 1280, CANVAS_H = 720;

    /** Baseline of the first row, and of the last one. */
    public static final double LIST_TOP = 178;
    /**
     * The last row's baseline. Derived from the footer rather than from the
     * canvas edge: the volume notice is drawn at CANVAS_H - 60 and the hint
     * at CANVAS_H - 28, and "the canvas is 720 tall" is not the same
     * statement as "nothing is drawn below 648".
     */
    public static final double FOOTER_TOP = CANVAS_H - 72;
    /** 5, not 3: the deepest a minimum-size title descends is 3.3px, and a
     *  margin that is smaller than the thing it is a margin for is not one. */
    public static final double LIST_BOTTOM = FOOTER_TOP - 5;

    /** Never pitch rows further apart than this, however few there are. */
    public static final double MAX_PITCH = 88;

    /** The bar's top edge sits this fraction of a pitch above its row's baseline. */
    public static final double BAR_FRACTION = 0.45;
    /** The bar is this much shorter than a pitch. */
    public static final double BAR_INSET = 4;
    /** A row's blurb baseline sits this far below its title. */
    public static final double BLURB_OFFSET = 17;
    /** Nothing above this may be overlapped by the first row's bar. */
    public static final double HEADER_BOTTOM = 140;

    /** Title font sizes. Georgia's ascent is about 0.77 of its size. */
    public static final double MAX_TITLE = 26;
    public static final double MIN_TITLE = 15;
    public static final double TITLE_ASCENT_RATIO = 0.77;
    /** Arial 13's descender, in pixels. */
    public static final double BLURB_DESCENT = 4;
    /** The least room to leave between a blurb and the title below it. */
    public static final double ROW_GAP_MIN = 5;

    /** Row pitch for a given number of rows. Derived, never fixed. */
    public static double pitch(int rows) {
        return Math.min(MAX_PITCH, (LIST_BOTTOM - LIST_TOP) / Math.max(1, rows - 1));
    }

    /** Baseline of row i. */
    public static double rowY(int i, int rows) {
        return LIST_TOP + i * pitch(rows);
    }

    /** Top edge of the selection bar drawn on row i. */
    public static double barTop(int i, int rows) {
        return rowY(i, rows) - pitch(rows) * BAR_FRACTION;
    }

    /** Bottom edge of the selection bar drawn on row i. */
    public static double barBottom(int i, int rows) {
        return barTop(i, rows) + pitch(rows) - BAR_INSET;
    }

    /** Baseline of row i's blurb. */
    public static double blurbY(int i, int rows) {
        return rowY(i, rows) + BLURB_OFFSET;
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
     * in a way nothing reports.
     */
    public static double titleSize(int rows) {
        double room = pitch(rows) - BLURB_OFFSET - BLURB_DESCENT - ROW_GAP_MIN;
        return Math.max(MIN_TITLE, Math.min(MAX_TITLE, room / TITLE_ASCENT_RATIO));
    }

    /** How far the last row's title descends below its baseline. */
    public static double titleDescent(int rows) {
        return titleSize(rows) * 0.22;
    }

    /** How much room is left between one row's blurb and the title below it. */
    public static double rowGap(int rows) {
        return pitch(rows) - BLURB_OFFSET - BLURB_DESCENT
                - titleSize(rows) * TITLE_ASCENT_RATIO;
    }

    /**
     * The most rows this geometry holds.
     *
     * The binding constraint is the blurb-to-title gap, not the bar: the bar
     * has 0.55 of a pitch to play with and the title has a whole pitch minus
     * the blurb's offset, so the title runs out first.
     */
    public static int maxRows() {
        int n = 2;
        while (n < 500 && clearance(n) > 0 && rowGap(n) > 0) n++;
        return n - 1;
    }

    private LibraryLayout() {}
}
