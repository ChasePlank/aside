package aside.ui;

import aside.game.Game;
import aside.game.Games;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * The library: everything the engine can start.
 *
 * The list is built from Games.all(), so adding a game is one entry in
 * one registry and it appears here with no other change.
 *
 * It scrolls. Until 2026-09-30 it shrank instead: the pitch and the type
 * size were derived from the row count, which worked right up to fifteen
 * rows and then had nowhere left to go. Sixteen games would not have fit at
 * any size worth reading. Now the pitch has a floor, the window shows nine
 * rows, and the count of games is not a limit on the library any more.
 * See LibraryLayout for the geometry and aside.engine.SelfTest for the
 * invariant that replaced the row limit.
 */
public class LibraryScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 52);
    static final Font F_SUB = Font.font("Arial", 15);
    /** The row title font. Sized per frame from the row count -- see
     *  LibraryLayout.titleSize. A fixed size is right until the row count
     *  changes, and then it draws titles on top of the blurbs above them. */
    static Font itemFont(int rows) {
        return Font.font("Georgia", LibraryLayout.titleSize(rows));
    }
    static final Font F_BLURB = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);

    final List<Game> games = new ArrayList<>(Games.all());
    int index = 0;
    /** The first row the window shows. Kept in step with `index` by
     *  LibraryLayout.windowStart, never by hand. */
    int scroll = 0;
    String notice = "";
    double noticeTimer = 0;

    /** Index == games.size() means the Quit row. */
    int quitRow() { return games.size(); }
    int rows() { return games.size() + 1; }
    /** How many rows the window shows: all of them, or as many as fit. */
    int shown() { return Math.min(rows(), LibraryLayout.visibleRows()); }

    public LibraryScreen(UiManager ui) {
        super(ui);
    }

    /**
     * Arriving at the shelf silences whatever the last game was doing.
     *
     * Nothing owned this job before: a game's ambience just kept running, so
     * leaving FNAF left its fan humming over the library (playtest, Sept 29).
     * Every route back here goes through replace(new LibraryScreen(...)), so
     * this one hook covers all of them.
     */
    @Override
    public void enter() {
        Audio a = Audio.A;
        if (a != null) a.stopAll();
        notice = "M mute    [ quieter    ] louder    - works in every game";
        noticeTimer = 5.0;
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.UP) { index = (index - 1 + rows()) % rows(); follow(); }
        else if (c == KeyCode.DOWN) { index = (index + 1) % rows(); follow(); }
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) select();
        e.consume();
    }

    /** Move the window so the selection is in it. The one place `scroll` moves. */
    void follow() {
        scroll = LibraryLayout.windowStart(index, rows());
    }

    void select() {
        if (index == quitRow()) { javafx.application.Platform.exit(); return; }
        Game g = games.get(index);
        try {
            ui.replace(g.create(ui));
        } catch (Exception ex) {
            notice = "could not start " + g.title() + ": " + ex.getMessage();
            noticeTimer = 4.0;
        }
    }

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        draw();
    }

    void draw() {
        gc.setFill(Color.web("#0A0A12"));
        gc.fillRect(0, 0, W, H);

        var cover = Assets.A == null ? null : Assets.A.background("title");
        if (cover != null) {
            gc.setGlobalAlpha(0.22);
            drawCover(cover);
            gc.setGlobalAlpha(1);
            gc.setFill(Color.rgb(8, 8, 14, 0.55));
            gc.fillRect(0, 0, W, H);
        }

        gc.setFill(Color.web("#F2C14E"));
        gc.setFont(F_TITLE);
        gc.fillText("Aside", 70, 100);
        gc.setFill(Color.web("#7A7A90"));
        gc.setFont(F_SUB);
        gc.fillText("a small engine, and the games built on it", 72, 128);

        // The pitch is derived from how many rows the window shows, and the
        // bar geometry is derived from the pitch. Both live in LibraryLayout,
        // which has no JavaFX in it, so the arithmetic that broke three times
        // at a row count nobody had tried yet can be checked without a
        // window. See aside.engine.SelfTest.
        int rows = rows();
        int shown = shown();
        int start = scroll;
        double step = LibraryLayout.pitch(shown);
        for (int k = 0; k < shown; k++) {
            int i = start + k;
            boolean sel = i == index;
            boolean isQuit = i == quitRow();
            String title = isQuit ? "Quit" : games.get(i).title();
            String blurb = isQuit ? "" : games.get(i).blurb();
            double y = LibraryLayout.rowY(k, shown);

            if (sel) {
                gc.setFill(Color.rgb(30, 30, 46, 0.85));
                gc.fillRoundRect(60, LibraryLayout.barTop(k, shown), W - 120,
                        step - LibraryLayout.BAR_INSET, 10, 10);
            }
            gc.setFill(sel ? Color.web("#F2C14E") : Color.web("#B9B9C6"));
            gc.setFont(itemFont(shown));
            gc.fillText((sel ? ">  " : "   ") + title, 90, y);
            if (!blurb.isEmpty()) {
                gc.setFill(Color.web("#6E6E86"));
                gc.setFont(F_BLURB);
                gc.fillText(blurb, 122, LibraryLayout.blurbY(k, shown));
            }
        }

        // A list that has more above or below it says so, in the one place
        // that cannot be mistaken for a game: the right-hand edge, where a
        // scrollbar is. Drawn only when there is something to scroll to, so
        // a short library looks exactly as it always did.
        if (rows > shown) {
            double top = LibraryLayout.BAR_TRACK_TOP;
            double bottom = LibraryLayout.BAR_TRACK_BOTTOM;
            double x = LibraryLayout.BAR_TRACK_X;
            gc.setFill(Color.rgb(60, 60, 78, 0.55));
            gc.fillRoundRect(x, top, LibraryLayout.BAR_TRACK_W, bottom - top, 2, 2);
            double frac = (double) shown / rows;
            double thumbH = Math.max(24, (bottom - top) * frac);
            double travel = (bottom - top) - thumbH;
            double pos = rows > shown ? (double) start / (rows - shown) : 0;
            gc.setFill(Color.web("#F2C14E"));
            gc.fillRoundRect(x, top + travel * pos, LibraryLayout.BAR_TRACK_W, thumbH, 2, 2);
        }

        if (noticeTimer > 0) {
            gc.setFill(Color.web("#E94560"));
            gc.setFont(F_SUB);
            gc.fillText(notice, 72, H - 60);
        }
        gc.setFill(Color.web("#6E6E86"));
        gc.setFont(F_TINY);
        String where = rows > shown
                ? "    " + (start + 1) + "-" + (start + shown) + " of " + rows
                : "";
        gc.fillText("up/down select    ENTER start" + where, 72, H - 28);
    }
}
