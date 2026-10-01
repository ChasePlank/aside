package aside.games.vigil;

import aside.ui.Audio;
import aside.ui.LibraryScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * vigil, drawn.
 *
 * Two columns, and the split is the design. The left is the house: the day's
 * one line, and the five things with what each of them is right now. The right
 * is the arithmetic: how much of you there is today, what the selected thing
 * would become if you kept it, and what it would become if you changed it.
 *
 * The right column is the one the player has to look at, and it is the one
 * that says the thing the game is about. Keep costs one and leaves the thing
 * exactly as it was, which is the promise. Change costs two, leaves a mark,
 * and cannot be undone. There is no third option, and there is not enough
 * effort for the first one.
 *
 * Four beats:
 *
 *   OPEN     the premise, the arithmetic, and the one rule that matters
 *   DAY      the house, the effort, and the choice
 *   REPORT   the room they walk into, assembled from what is left of it
 *   ARRIVAL  what they do about it
 *
 * The one thing this screen deliberately never does is tell the player whether
 * they did well. There is no good outcome here. There is only which house they
 * are standing in when the door opens.
 */
public class VigilScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 24);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_ITEM = Font.font("Georgia", 19);
    static final Font F_DAY = Font.font("Georgia", FontPosture.ITALIC, 17);
    static final Font F_ARRIVAL = Font.font("Georgia", 18);
    static final Font F_CLOSE = Font.font("Georgia", FontPosture.ITALIC, 19);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_BODY = Font.font("Arial", 14);
    static final Font F_MONO = Font.font("Consolas", 14);
    static final Font F_MONO_S = Font.font("Consolas", 13);

    static final double M = 84;
    static final double LEFT_W = 606;
    static final double RIGHT_X = 760;
    static final double RIGHT_W = 456;
    static final double PAD = 28;

    static final Color BG = Color.web("#0B0A0C");
    static final Color PAPER = Color.web("#141317");
    static final Color EDGE = Color.web("#2A2730");
    static final Color GOLD = Color.web("#D9A441");
    static final Color INK = Color.web("#EDE7DE");
    static final Color DIM = Color.web("#A79C8E");
    static final Color FAINT = Color.web("#6E655A");
    static final Color GREEN = Color.web("#7FA88A");
    static final Color RED = Color.web("#C4553F");
    static final Color COLD = Color.web("#5A6472");

    enum Phase { OPEN, DAY, REPORT, ARRIVAL }

    /** Change phase, and drop anything the last phase was saying. A notice
     *  about a refused keep has no business on the page where they come back. */
    void to(Phase p) { phase = p; notice = ""; noticeTimer = 0; }

    final Vigil v;
    final Path save;

    Phase phase = Phase.OPEN;
    int selected = 0;
    String notice = "";
    double noticeTimer = 0;

    public VigilScreen(UiManager ui, Vigil v, Path save) {
        super(ui);
        this.v = v;
        this.save = save;
        // A finished vigil has no day to open on. Without this the player who
        // comes back after the door opens lands on day 12 with nothing to do.
        if (v.finished) phase = Phase.REPORT;
        else if (v.day > 1 || v.spentKeeping > 0 || v.spentChanging > 0) phase = Phase.DAY;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case OPEN -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) { to(Phase.DAY); sfx("choice_select"); }
            }
            case DAY -> day(c);
            case REPORT -> {
                if (c == KeyCode.R) { reset(); to(Phase.DAY); sfx("choice_select"); }
                else if (c == KeyCode.ENTER || c == KeyCode.SPACE) { to(Phase.ARRIVAL); sfx("door_open"); }
            }
            case ARRIVAL -> {
                if (c == KeyCode.R) { reset(); to(Phase.DAY); sfx("choice_select"); }
                else if (c == KeyCode.ENTER || c == KeyCode.SPACE) ui.replace(new LibraryScreen(ui));
            }
        }
        e.consume();
    }

    void day(KeyCode c) {
        int pick = switch (c) {
            case DIGIT1, NUMPAD1 -> 0;
            case DIGIT2, NUMPAD2 -> 1;
            case DIGIT3, NUMPAD3 -> 2;
            case DIGIT4, NUMPAD4 -> 3;
            case DIGIT5, NUMPAD5 -> 4;
            default -> -1;
        };
        if (pick >= 0) {
            if (pick < v.things.size()) { selected = pick; sfx("choice_move"); }
            return;
        }

        if (c == KeyCode.K) {
            if (v.keep(selected)) { persist(); sfx("choice_select"); }
            else { warn(v.thing(selected) != null && v.thing(selected).gone
                    ? "There is nothing there to keep."
                    : "You do not have a unit left today."); }
            return;
        }
        if (c == KeyCode.C) {
            if (v.change(selected)) { persist(); sfx("door_close"); }
            else {
                Vigil.Thing t = v.thing(selected);
                if (t != null && !t.gone && t.mine()) warn("That one is already yours. It cannot be changed twice.");
                else warn("A change costs two units, and you do not have two.");
            }
            return;
        }
        if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
            int left = v.unspent();
            v.endDay();
            persist();
            if (v.finished) { to(Phase.REPORT); sfx("door_open"); }
            else if (left > 0) { warn("You ended the day with " + left + (left == 1 ? " unit" : " units") + " unspent."); sfx("choice_move"); }
            else sfx("choice_move");
        }
    }

    void warn(String text) {
        notice = text;
        noticeTimer = 4;
        sfx("door_close");
    }

    void reset() {
        Vigil fresh = Vigil.of();
        v.things.clear();
        v.things.addAll(fresh.things);
        v.day = fresh.day;
        v.effort = fresh.effort;
        v.keptToday = fresh.keptToday;
        v.finished = false;
        v.spentKeeping = 0;
        v.spentChanging = 0;
        selected = 0;
        persist();
    }

    void persist() {
        try { v.save(save); }
        catch (Exception ex) { notice = "the day could not be written down: " + ex.getMessage(); noticeTimer = 6; }
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
        gc.setFill(BG);
        gc.fillRect(0, 0, W, H);

        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText("vigil", M, 76);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = switch (phase) {
            case OPEN -> "twelve days";
            case DAY -> "day " + v.day + " of " + Vigil.DAYS;
            case REPORT -> "day " + Vigil.DAYS + " of " + Vigil.DAYS;
            case ARRIVAL -> "the door";
        };
        gc.fillText(right, W - M - width(right, F_TINY), 76);

        switch (phase) {
            case OPEN -> drawOpen();
            case DAY -> drawDay();
            case REPORT -> drawReport();
            case ARRIVAL -> drawArrival();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(RED);
            gc.fillText(notice, M, H - 66);
        }
    }

    // ---------------------------------------------------------------- open

    void drawOpen() {
        double y = 138;
        String[] paras = Vigil.openParagraphs();
        for (String p : paras) {
            for (String line : wrap(p, F_SCENE, LEFT_W)) {
                gc.setFont(F_SCENE);
                gc.setFill(INK);
                gc.fillText(line, M, y);
                y += 26;
            }
            y += 14;
        }

        gc.setFont(F_CLOSE);
        gc.setFill(GOLD);
        gc.fillText(Vigil.PROMISE, M, y + 14);

        drawRulesPanel();

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText("ENTER to begin the first day.", M, H - 40);
    }

    void drawRulesPanel() {
        panel(RIGHT_X, 120, RIGHT_W, 528);
        double x = RIGHT_X + PAD, y = 156;
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText("HOW IT GOES", x, y);
        y += 30;

        String[][] rules = Vigil.rules();
        for (String[] r : rules) {
            if (!r[0].isEmpty()) {
                gc.setFont(F_MONO_S);
                gc.setFill(GOLD);
                gc.fillText(r[0], x, y);
            }
            for (String line : wrap(r[1], F_SMALL, RIGHT_W - 2 * PAD - 66)) {
                gc.setFont(F_SMALL);
                gc.setFill(DIM);
                gc.fillText(line, x + 66, y);
                y += 18;
            }
            y += 14;
        }

        y += 8;
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(x, y, RIGHT_X + RIGHT_W - PAD, y);
        y += 32;

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("THE ARITHMETIC", x, y);
        y += 26;
        String[] sums = Vigil.sums();
        for (int i = 0; i < sums.length; i++) {
            gc.setFont(F_MONO_S);
            gc.setFill(i == 3 ? GOLD : DIM);
            gc.fillText(sums[i], x, y);
            y += 22;
        }
    }

    // ----------------------------------------------------------------- day

    void drawDay() {
        double y = 118;
        for (String line : wrap(Vigil.dayLine(v.day), F_DAY, LEFT_W)) {
            gc.setFont(F_DAY);
            gc.setFill(Color.web("#B9AFA2"));
            gc.fillText(line, M, y);
            y += 24;
        }

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText("THE HOUSE", M, 186);

        double top = 224, pitch = 84;
        for (int i = 0; i < v.things.size(); i++) {
            Vigil.Thing t = v.things.get(i);
            double ny = top + i * pitch;
            boolean sel = i == selected;

            if (sel) {
                gc.setFill(Color.rgb(30, 28, 36, 0.85));
                gc.fillRoundRect(M - 14, ny - 24, LEFT_W + 28, pitch - 10, 8, 8);
                gc.setStroke(Color.web("#3A3546"));
                gc.setLineWidth(1);
                gc.strokeRoundRect(M - 14, ny - 24, LEFT_W + 28, pitch - 10, 8, 8);
            }

            gc.setFont(F_MONO_S);
            gc.setFill(kept(i) ? GOLD : FAINT);
            gc.fillText(String.valueOf(i + 1), M, ny);

            gc.setFont(F_ITEM);
            gc.setFill(t.gone ? FAINT : (sel ? INK : Color.web("#D8D2C8")));
            gc.fillText(t.name, M + 22, ny);

            gc.setFont(F_TINY);
            gc.setFill(stateColor(t));
            gc.fillText(t.stateWord(), M + 158, ny);

            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(t.where, M + 330, ny);

            condition(M + 470, ny - 11, t);

            double ly = ny + 22;
            for (String line : wrap(t.line(), F_SMALL, LEFT_W - 20)) {
                gc.setFont(F_SMALL);
                gc.setFill(t.gone ? Color.web("#7A6E64") : Color.web("#B4ABA0"));
                gc.fillText(line, M + 22, ly);
                ly += 18;
            }
        }

        drawDayPanel();
        hint("1 - 5 select    K keep    C change    ENTER end the day    ESC for the library");
    }

    boolean kept(int i) { return i < v.keptToday.length && v.keptToday[i]; }

    static Color stateColor(Vigil.Thing t) {
        return switch (t.state()) {
            case AS_LEFT -> GREEN;
            case CHANGED -> GOLD;
            case LOST -> RED;
        };
    }

    void condition(double x, double top, Vigil.Thing t) {
        Color on = stateColor(t);
        for (int i = 0; i < Vigil.FULL; i++) {
            boolean lit = i < t.condition;
            gc.setFill(lit ? on : Color.web("#26232C"));
            gc.fillRect(x + i * 16, top, 12, 12);
        }
    }

    void drawDayPanel() {
        panel(RIGHT_X, 108, RIGHT_W, 540);
        double x = RIGHT_X + PAD;
        double y = 148;

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText("EFFORT TODAY", x, y);

        int total = Vigil.unitsFor(v.day);
        for (int i = 0; i < total; i++) {
            boolean lit = i < v.effort;
            gc.setFill(lit ? GOLD : Color.web("#26232C"));
            gc.fillRect(x + i * 20, y + 14, 16, 16);
        }
        gc.setFont(F_MONO_S);
        gc.setFill(v.effort > 0 ? GOLD : FAINT);
        gc.fillText(v.effort + " of " + total + " left", x + total * 20 + 14, y + 27);

        divider(x, y + 52);

        Vigil.Thing t = v.thing(selected);
        y = 232;
        if (t != null) {
            gc.setFont(F_ITEM);
            gc.setFill(t.gone ? FAINT : INK);
            gc.fillText(t.name, x, y);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(t.where, x + 158, y);
            gc.setFont(F_TINY);
            gc.setFill(stateColor(t));
            gc.fillText(t.stateWord(), x, y + 20);

            y += 58;
            gc.setFont(F_TINY);
            gc.setFill(GOLD);
            gc.fillText("KEEP  \u00b7  1 unit", x, y);
            y = preview(t.keepPreview(), x, y + 22, t.gone);

            y += 22;
            gc.setFont(F_TINY);
            gc.setFill(GOLD);
            gc.fillText("CHANGE  \u00b7  2 units", x, y);
            y = preview(t.changePreview(), x, y + 22, t.gone && t.replaced);
        }

        divider(x, 552);
        gc.setFont(F_MONO_S);
        gc.setFill(DIM);
        gc.fillText(v.tally(), x, 582);
        gc.setFont(F_CLOSE);
        gc.setFill(Color.web("#8C8175"));
        gc.fillText(Vigil.PROMISE, x, 616);
    }

    /** Preview text, with the honest answer when there is nothing to preview. */
    double preview(String text, double x, double y, boolean nothing) {
        if (text == null) {
            gc.setFont(F_BODY);
            gc.setFill(FAINT);
            gc.fillText(nothing ? "There is nothing there." : "That one is already yours.", x, y);
            return y + 20;
        }
        for (String line : wrap(text, F_BODY, RIGHT_W - 2 * PAD)) {
            gc.setFont(F_BODY);
            gc.setFill(Color.web("#B4ABA0"));
            gc.fillText(line, x, y);
            y += 20;
        }
        return y;
    }

    void divider(double x, double y) {
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(x, y, RIGHT_X + RIGHT_W - PAD, y);
    }

    // -------------------------------------------------------------- report

    void drawReport() {
        double y = 132;
        gc.setFont(F_HEAD);
        gc.setFill(v.mine() == 0 ? GREEN : GOLD);
        gc.fillText(v.headline(), M, y);

        y = 180;
        for (String line : wrap(v.verdict(), F_SCENE, W - 2 * M)) {
            if (line.isEmpty()) { y += 14; continue; }
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 26;
        }

        y += 18;
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText("THE HOUSE THEY WALK INTO", M, y);
        y += 12;
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(M, y, W - M, y);
        y += 26;

        for (Vigil.Thing t : v.things) {
            gc.setFont(F_ITEM);
            gc.setFill(t.gone ? FAINT : INK);
            gc.fillText(t.name, M, y);
            gc.setFont(F_TINY);
            gc.setFill(stateColor(t));
            gc.fillText(t.stateWord(), M + 150, y);
            gc.setFont(F_SMALL);
            gc.setFill(t.gone ? Color.web("#7A6E64") : Color.web("#B4ABA0"));
            gc.fillText(t.line(), M + 330, y);
            y += 40;
        }

        hint("R to keep it all again from the first day.    ENTER \u2014 they come back.");
    }

    void drawArrival() {
        double y = 150;
        for (String line : wrap(v.arrival(), F_ARRIVAL, W - 2 * M)) {
            if (line.isEmpty()) { y += 16; continue; }
            gc.setFont(F_ARRIVAL);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 28;
        }

        y = Math.max(y + 34, 430);
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(M, y, W - M, y);
        y += 50;

        for (String line : wrap(v.closing(), F_CLOSE, W - 2 * M)) {
            gc.setFont(F_CLOSE);
            gc.setFill(GOLD);
            gc.fillText(line, M, y);
            y += 28;
        }

        gc.setFont(F_MONO_S);
        gc.setFill(FAINT);
        gc.fillText(v.tally(), M, H - 120);

        hint("R to keep it all again from the first day.    ENTER for the library.");
    }

    // ------------------------------------------------------------ helpers

    void panel(double x, double y, double w, double h) {
        gc.setFill(PAPER);
        gc.fillRoundRect(x, y, w, h, 10, 10);
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, w, h, 10, 10);
    }

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 140, 125, 0.6));
        gc.fillText(text, M, H - 40);
    }

    /** Real font metrics -- a Canvas has no measureText. */
    static double width(String s, Font font) {
        Text probe = new Text(s);
        probe.setFont(font);
        return probe.getLayoutBounds().getWidth();
    }

    /** Word wrap using real font metrics. */
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
