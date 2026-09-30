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
 * The binding constraint is not the canvas -- the pitch is derived from the
 * row count, so the last row always lands on LIST_BOTTOM. It is the gap
 * between the selection bar of one row and the blurb of the row above it.
 * That gap is pitch*(1 - BAR_FRACTION) - BLURB_OFFSET, so as rows go up and
 * pitch goes down, it shrinks, and at some row count it goes negative.
 * maxRows() is where that happens. It is 15 today.
 */
public final class LibraryLayout {

    public static final double CANVAS_W = 1280, CANVAS_H = 720;

    /** Baseline of the first row, and of the last one. */
    public static final double LIST_TOP = 196;
    public static final double LIST_BOTTOM = CANVAS_H - 62;

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

    /** The most rows this geometry holds before the bar reaches the row above. */
    public static int maxRows() {
        int n = 2;
        while (n < 500 && clearance(n) > 0) n++;
        return n - 1;
    }

    private LibraryLayout() {}
}
