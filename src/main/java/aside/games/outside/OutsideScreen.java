package aside.games.outside;

import aside.ui.Audio;
import aside.ui.LibraryScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * outside, drawn.
 *
 * Two columns, and the split is the whole design. The left is the ridge:
 * what is happening, and the four things you could say about it. The right
 * is the dispatcher's room: everything it has ever been told, each line
 * labelled with the day it arrived, in the order it arrived.
 *
 * The player's eye is supposed to keep going back to the right column while
 * the left column keeps offering more, because that is the actual experience
 * of the game -- the thing you are about to say is going to sit under
 * everything you have already said, forever.
 *
 * Four beats:
 *
 *   OPEN      the premise, once
 *   REPORT    a day, and the four things you can see
 *   ROUTING   what it did with what it had, and which day it learned it
 *   END       the week, the score, and where every belief came from
 *
 * The one thing this screen deliberately never does is tell you which of
 * today's four things will still be true on the seventh day. It cannot: what
 * is durable is decided by the weather, and the weather comes later.
 */
public class OutsideScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 24);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_FACT = Font.font("Georgia", 18);
    static final Font F_ITEM = Font.font("Georgia", 19);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_MONO = Font.font("Consolas", 15);
    static final Font F_MONO_S = Font.font("Consolas", 13);

    static final double M = 84;
    static final double LEFT_W = 700;
    static final double RIGHT_X = 830;
    static final double RIGHT_W = 366;

    static final Color GOLD = Color.web("#F2C14E");
    static final Color INK = Color.web("#E8E8EF");
    static final Color DIM = Color.web("#9A9AAE");
    static final Color FAINT = Color.web("#6E6E86");
    static final Color RED = Color.web("#E94560");
    static final Color GREEN = Color.web("#7FD1AE");
    static final Color BLUE = Color.web("#8FB8DE");

    enum Phase { OPEN, REPORT, ROUTING, END }

    final Outside outside;
    final Path save;

    Phase phase = Phase.OPEN;
    int index = -1;                 // -1 means "report nothing"
    Outside.Fact filed;             // what was reported on the day just filed
    String notice = "";
    double noticeTimer = 0;

    public OutsideScreen(UiManager ui, Outside outside, Path save) {
        super(ui);
        this.outside = outside;
        this.save = save;
        // A finished week has no day to open on. Without this the player who
        // reaches the end and comes back lands on an empty ridge.
        if (outside.finished) phase = Phase.END;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case OPEN -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.REPORT; index = -1; }
            }
            case REPORT -> report(c);
            case ROUTING -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    outside.next();
                    phase = outside.finished ? Phase.END : Phase.REPORT;
                    index = -1;
                    filed = null;
                    persist();
                }
            }
            case END -> {
                if (c == KeyCode.R) {
                    outside.heard.clear();
                    outside.day = 0;
                    outside.correct = 0;
                    outside.ranDays = 0;
                    outside.finished = false;
                    outside.last = null;
                    phase = Phase.REPORT;
                    index = -1;
                    filed = null;
                    persist();
                } else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    ui.replace(new LibraryScreen(ui));
                }
            }
        }
        e.consume();
    }

    void report(KeyCode c) {
        Outside.Day d = outside.currentDay();
        if (d == null) { phase = Phase.END; return; }

        int n = digit(c);
        if (n == 0) { index = -1; sfx("choice_move"); return; }
        if (n >= 1 && n <= d.facts.size()) { index = n - 1; sfx("choice_move"); return; }

        if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
            filed = index < 0 ? null : d.facts.get(index);
            outside.report(filed);
            outside.file();
            phase = Phase.ROUTING;
            sfx(filed == null ? "door_close" : "choice_select");
            persist();
        }
    }

    static int digit(KeyCode c) {
        return switch (c) {
            case DIGIT0, NUMPAD0 -> 0;
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            default -> -1;
        };
    }

    void persist() {
        try { outside.save(save); }
        catch (Exception ex) { notice = "the ridge log could not be written: " + ex.getMessage(); noticeTimer = 6; }
    }

    static void sfx(String name) {
        if (Audio.A != null) Audio.A.sfx(name, "choice_select");
    }

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        draw();
    }

    // ---------------------------------------------------------------- draw

    void draw() {
        gc.setFill(Color.web("#0A0A11"));
        gc.fillRect(0, 0, W, H);

        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText("outside", M, 76);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = Outside.where(outside.day, outside.finished);
        gc.fillText(right, W - M - 110, 76);

        switch (phase) {
            case OPEN -> drawOpen();
            case REPORT -> drawReport();
            case ROUTING -> drawRouting();
            case END -> drawEnd();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(RED);
            gc.fillText(notice, M, H - 66);
        }
    }

    // ---------------------------------------------------------------- open

    void drawOpen() {
        double y = 150;

        gc.setFont(F_MONO);
        gc.setFill(FAINT);
        gc.fillText(Outside.THE_RIDGE, M, y);
        y += 40;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(200, 200, 215, 0.78));
        for (String p : Outside.OPENING) {
            for (String line : wrap(p, F_SCENE, LEFT_W)) {
                gc.fillText(line, M, y);
                y += 28;
            }
            y += 18;
        }

        drawRulesPanel();
        hint("ENTER  go up to the ridge     ESC  leave");
    }

    /** The one thing the player has to understand before the first day. */
    void drawRulesPanel() {
        double x = RIGHT_X, y = 150;
        gc.setFill(Color.rgb(20, 20, 32, 0.85));
        gc.fillRoundRect(x - 26, y - 44, RIGHT_W + 52, 292, 12, 12);

        gc.setFont(F_MONO);
        gc.setFill(GOLD);
        gc.fillText(Outside.RULES_HEADING, x, y);
        y += 34;

        gc.setFont(F_SMALL);
        for (String r : Outside.RULES) {
            gc.setFill(Color.rgb(190, 190, 205, 0.8));
            for (String line : wrap(r, F_SMALL, RIGHT_W)) {
                gc.fillText(line, x, y);
                y += 20;
            }
            y += 14;
        }
    }

    // -------------------------------------------------------------- report

    void drawReport() {
        Outside.Day d = outside.currentDay();
        if (d == null) { phase = Phase.END; return; }

        double y = 138;
        gc.setFont(F_MONO);
        gc.setFill(FAINT);
        gc.fillText(d.heading.toUpperCase(), M, y);
        y += 34;

        gc.setFont(F_HEAD);
        gc.setFill(INK);
        gc.fillText(d.title, M, y);
        y += 34;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(200, 200, 215, 0.72));
        for (String line : wrap(d.scene, F_SCENE, LEFT_W)) {
            gc.fillText(line, M, y);
            y += 28;
        }

        y += 24;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Outside.WHAT_YOU_CAN_SEE, M, y);
        y += 32;

        for (int i = 0; i < d.facts.size(); i++) {
            Outside.Fact x = d.facts.get(i);
            boolean sel = i == index;
            gc.setFont(F_FACT);
            gc.setFill(sel ? GOLD : Color.web("#B9B9C6"));
            double ly = y;
            for (String line : wrap(x.words, F_FACT, LEFT_W - 60)) {
                gc.fillText((sel && ly == y ? "\u25B6  " : "   ") + (i + 1) + ".  " + line, M + 6, ly);
                ly += 26;
            }
            y = ly + 12;
        }

        gc.setFont(F_FACT);
        boolean none = index < 0;
        gc.setFill(none ? GOLD : DIM);
        gc.fillText((none ? "\u25B6  " : "   ") + "0.  " + Outside.SAY_NOTHING, M + 6, y + 8);

        drawHoldsPanel();
        hint("1-4  choose a report     0  say nothing     ENTER  file it     ESC  leave");
    }

    /**
     * The right column: the whole of what the dispatcher is holding.
     *
     * Every line carries the day it arrived, because the day is the only
     * thing that decides which of them wins. The first line under each
     * heading is the one it will act on, and it is drawn as such.
     */
    void drawHoldsPanel() {
        double x = RIGHT_X, y = 138;
        gc.setFill(Color.rgb(20, 20, 32, 0.85));
        gc.fillRoundRect(x - 26, y - 44, RIGHT_W + 52, 500, 12, 12);

        gc.setFont(F_MONO);
        gc.setFill(GOLD);
        gc.fillText(Outside.WHAT_IT_HOLDS, x, y);
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(outside.heard.size() + " of " + (Outside.DAYS.size() * 4), x + RIGHT_W - 70, y);
        y += 30;

        for (Outside.Concern c : Outside.Concern.values()) {
            gc.setFont(F_MONO_S);
            gc.setFill(BLUE);
            gc.fillText(c.label, x, y);
            y += 22;

            boolean any = false;
            for (Outside.Fact h : outside.heard) {
                if (h.concern != c) continue;
                boolean first = !any;
                any = true;
                gc.setFont(F_SMALL);
                gc.setFill(first ? INK : Color.rgb(150, 150, 170, 0.7));
                double ly = y;
                for (String line : wrap(valueLabel(h.value), F_SMALL, RIGHT_W - 62)) {
                    gc.fillText(line, x + 16, ly);
                    ly += 19;
                }
                gc.setFont(F_TINY);
                gc.setFill(first ? GOLD : FAINT);
                gc.fillText(Outside.dayLabel(h.day), x + RIGHT_W - 52, y);
                y = ly + 4;
            }

            if (!any) {
                gc.setFont(F_SMALL);
                gc.setFill(Color.rgb(110, 110, 134, 0.6));
                gc.fillText(Outside.NOTHING, x + 16, y);
                y += 22;
            }
            y += 12;
        }

        gc.setFont(F_TINY);
        gc.setFill(Color.rgb(150, 150, 170, 0.7));
        for (String line : wrap(Outside.FIRST_LINE_NOTE, F_TINY, RIGHT_W)) {
            gc.fillText(line, x, y);
            y += 17;
        }
    }

    // ------------------------------------------------------------- routing

    void drawRouting() {
        Outside.Routing r = outside.last;
        if (r == null) { phase = Phase.REPORT; return; }

        double y = 138;
        gc.setFont(F_MONO);
        gc.setFill(FAINT);
        gc.fillText(Outside.THE_DISPATCHER, M, y);
        y += 34;

        gc.setFont(F_HEAD);
        gc.setFill(r.ran ? INK : RED);
        gc.fillText(r.ran ? Outside.TRUCK_WENT : Outside.TRUCK_DID_NOT_GO, M, y);
        y += 40;

        for (Outside.Decision dec : r.decisions) {
            gc.setFont(F_MONO_S);
            gc.setFill(BLUE);
            gc.fillText(dec.concern.label, M, y);
            y += 24;

            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Outside.THE_WORLD, M + 16, y);
            gc.setFill(Color.rgb(190, 190, 205, 0.85));
            gc.fillText(Outside.valueLabel(dec.truth), M + 130, y);

            gc.setFill(FAINT);
            gc.fillText(Outside.IT_DID, M + 430, y);
            gc.setFill(dec.did == null ? RED : (dec.right() ? GREEN : RED));
            gc.fillText(dec.did == null ? Outside.NOTHING : Outside.valueLabel(dec.did), M + 510, y);

            gc.setFont(F_TINY);
            gc.setFill(dec.fromDay == 0 ? FAINT : GOLD);
            gc.fillText(dec.fromDay == 0 ? Outside.NO_REPORT : Outside.fromDay(dec.fromDay), M + 900, y);

            y += 34;
        }

        // The day's tally. The colours already say which calls were wrong,
        // but the week is scored as a sum, and a player who cannot see the
        // sum cannot tell whether they are doing well.
        y += 4;
        gc.setFont(F_SMALL);
        gc.setFill(!r.ran ? RED : (r.correct() == 3 ? GREEN : DIM));
        gc.fillText(r.ran ? Outside.rightToday(r.correct()) : Outside.NOTHING_ROUTED, M, y);
        y += 30;

        // On the seventh day the report you just made is still sitting there,
        // unheard. Naming that out loud is the whole ending, so it gets its
        // own paragraph rather than a footnote.
        if (outside.day == Outside.DAYS.size() - 1) {
            gc.setFont(F_SMALL);
            gc.setFill(Color.rgb(150, 150, 170, 0.85));
            String said = filed == null
                    ? Outside.SAID_NOTHING_TODAY
                    : Outside.saidToday(filed.words);
            for (String line : wrap(said, F_SMALL, LEFT_W)) {
                gc.fillText(line, M, y);
                y += 20;
            }
            boolean ignored = filed != null && outside.lastReportIgnored(filed);
            gc.setFill(ignored ? RED : DIM);
            String verdict = filed == null
                    ? Outside.ACTED_ON_WHAT_IT_HAD
                    : (ignored
                        ? Outside.actedOnEarlier(outside.earliest(filed.concern).day)
                        : Outside.reachedIt(filed.concern));
            for (String line : wrap(verdict, F_SMALL, LEFT_W)) {
                gc.fillText(line, M, y);
                y += 20;
            }
        }

        hint("ENTER  the next day     ESC  leave");
    }

    // ----------------------------------------------------------------- end

    void drawEnd() {
        double y = 128;

        gc.setFont(F_HEAD);
        gc.setFill(INK);
        gc.fillText(Outside.WEEK_OVER, M, y);
        y += 38;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(200, 200, 215, 0.8));
        gc.fillText(Outside.scoreLine(outside.correct, outside.ranDays), M, y);
        y += 32;

        gc.setFill(DIM);
        for (String line : wrap(Outside.closing(outside.correct, outside.heard.size()),
                F_SCENE, LEFT_W)) {
            gc.fillText(line, M, y);
            y += 26;
        }

        // Where every belief came from. This is the only screen in the game
        // that shows the whole of it at once, and it is the point of the game.
        y += 26;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Outside.BELIEVED_HEADING, M, y);
        y += 28;

        for (Outside.Concern c : Outside.Concern.values()) {
            Outside.Fact e = outside.earliest(c);
            gc.setFont(F_MONO_S);
            gc.setFill(BLUE);
            gc.fillText(c.label, M, y);
            gc.setFont(F_SMALL);
            gc.setFill(e == null ? RED : Color.rgb(190, 190, 205, 0.85));
            gc.fillText(e == null ? Outside.NOTHING : Outside.valueLabel(e.value), M + 150, y);
            gc.setFont(F_TINY);
            gc.setFill(e == null ? FAINT : GOLD);
            gc.fillText(e == null ? Outside.NEVER_SAID : Outside.dayLabel(e.day), M + 470, y);
            y += 26;
        }

        y += 22;
        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        for (String line : wrap(Outside.NO_CLOCK_LINE, F_SMALL, LEFT_W)) {
            gc.fillText(line, M, y);
            y += 20;
        }

        drawRecordPanel();
        hint("ENTER  back to the library     R  start again     ESC  leave");
    }

    /** The right column at the end: everything said, in the order it landed. */
    void drawRecordPanel() {
        double x = RIGHT_X, y = 128;
        gc.setFill(Color.rgb(20, 20, 32, 0.85));
        gc.fillRoundRect(x - 26, y - 44, RIGHT_W + 52, 500, 12, 12);

        gc.setFont(F_MONO);
        gc.setFill(GOLD);
        gc.fillText(Outside.THE_RIDGE_LOG, x, y);
        y += 30;

        if (outside.heard.isEmpty()) {
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Outside.NOTHING_WAS_EVER_SAID, x, y);
            return;
        }

        for (Outside.Fact h : outside.heard) {
            gc.setFont(F_TINY);
            gc.setFill(GOLD);
            gc.fillText(Outside.dayLabel(h.day), x, y);
            gc.setFont(F_SMALL);
            gc.setFill(Color.rgb(180, 180, 196, 0.85));
            double ly = y;
            for (String line : wrap(h.words, F_SMALL, RIGHT_W - 56)) {
                gc.fillText(line, x + 56, ly);
                ly += 19;
            }
            y = ly + 12;
        }

        y += 8;
        gc.setFont(F_TINY);
        gc.setFill(Color.rgb(150, 150, 170, 0.7));
        for (String line : wrap(Outside.visibleLine(outside.heard.size()), F_TINY, RIGHT_W)) {
            gc.fillText(line, x, y);
            y += 17;
        }
    }

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 170, 0.6));
        gc.fillText(text, M, H - 40);
    }

    // ------------------------------------------------------------ helpers

    static String valueLabel(String value) { return Outside.valueLabel(value); }

    /** Word wrap using real font metrics -- a Canvas has no measureText. */
    static List<String> wrap(String text, Font font, double maxWidth) {
        List<String> out = new ArrayList<>();
        Text probe = new Text();
        probe.setFont(font);
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { out.add(""); continue; }
            StringBuilder line = new StringBuilder();
            for (String w : para.split(" ")) {
                String candidate = line.isEmpty() ? w : line + " " + w;
                probe.setText(candidate);
                if (probe.getLayoutBounds().getWidth() > maxWidth && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (!line.isEmpty()) out.add(line.toString());
        }
        return out;
    }
}
