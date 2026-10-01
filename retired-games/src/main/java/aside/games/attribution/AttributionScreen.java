package aside.games.attribution;

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
 * attribution, drawn.
 *
 * Two columns, and the split is the design again, but for a different reason
 * than Inventory's. The left is the round: four lines of copy, each with the
 * byline that carries it, and the answer if you paid for it. The right is the
 * desk: the calls you have left, and what your calls have told you about each
 * stringer so far.
 *
 * The right column is the player's own record and the only thing they know.
 * It is deliberately thin. Two calls on Halloran and the panel says "2 called,
 * 1 true", which is what a liar files four times out of five and also what an
 * honest stringer files once in ten. The panel does not resolve that, because
 * the night does not either.
 *
 * Three beats:
 *
 *   OPEN    the premise and the one rule, once
 *   NIGHT   four lines, a purse of calls, and a decision per line
 *   REPORT  the edition, the desk's true numbers, and what you never called
 *
 * The one thing this screen never does during the night is tell you how you
 * are doing. It cannot: you only learn what you paid to learn.
 */
public class AttributionScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 26);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_LINE = Font.font("Georgia", 19);
    static final Font F_BYLINE = Font.font("Georgia", FontPosture.ITALIC, 16);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_MONO = Font.font("Consolas", 15);
    static final Font F_MONO_S = Font.font("Consolas", 13);

    static final double M = 76;
    static final double LEFT_W = 700;
    static final double RIGHT_X = 812;
    static final double RIGHT_W = 392;

    static final Color BG = Color.web("#0A0A0C");
    static final Color PAPER = Color.web("#131317");
    static final Color EDGE = Color.web("#26262E");
    static final Color GOLD = Color.web("#D8B45A");
    static final Color INK = Color.web("#E9E9EF");
    static final Color DIM = Color.web("#A2A2B0");
    static final Color FAINT = Color.web("#6B6B7C");
    static final Color TRUE_C = Color.web("#7FA88A");
    static final Color FALSE_C = Color.web("#C4553F");
    static final Color SEL = Color.web("#1C1C24");

    enum Phase { OPEN, NIGHT, REPORT }

    final Attribution desk;
    final Path save;

    Phase phase = Phase.OPEN;
    int sel = 0;
    String notice = "";
    double noticeTimer = 0;

    public AttributionScreen(UiManager ui, Attribution desk, Path save) {
        super(ui);
        this.desk = desk;
        this.save = save;
        // A filed night opens on the edition, not on an empty desk.
        if (desk.finished()) phase = Phase.REPORT;
        else if (desk.callsMade() > 0 || anyFiled()) phase = Phase.NIGHT;
    }

    boolean anyFiled() {
        for (Attribution.Item it : desk.items) if (it.filed()) return true;
        return false;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case OPEN -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.NIGHT; sel = 0; sfx("choice_select"); }
            }
            case NIGHT -> night(c);
            case REPORT -> {
                if (c == KeyCode.R) {
                    Attribution fresh = Attribution.of();
                    desk.items.clear();
                    desk.items.addAll(fresh.items);
                    desk.stringers.clear();
                    desk.stringers.addAll(fresh.stringers);
                    desk.callsLeft = Attribution.CALLS;
                    persist();
                    sel = 0;
                    phase = Phase.NIGHT;
                    sfx("choice_select");
                } else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    ui.replace(new LibraryScreen(ui));
                }
            }
        }
        e.consume();
    }

    void night(KeyCode c) {
        if (desk.finished()) { phase = Phase.REPORT; return; }
        List<Attribution.Item> round = desk.roundItems();
        if (round.isEmpty()) { phase = Phase.REPORT; return; }

        int slot = switch (c) {
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            default -> 0;
        };
        if (slot > 0) {
            if (slot <= round.size()) { sel = slot - 1; sfx("choice_move"); }
            return;
        }
        if (sel >= round.size()) sel = 0;
        Attribution.Item it = round.get(sel);

        if (c == KeyCode.C) {
            if (it.calledIt()) { warn(Attribution.ALREADY_CALLED); return; }
            if (desk.callsLeft <= 0) { warn(Attribution.NO_CALLS); return; }
            desk.call(it);
            sfx(it.calledTrue() ? "choice_select" : "door_close");
            persist();
            return;
        }
        if (c == KeyCode.R || c == KeyCode.S) {
            if (it.filed()) { warn(Attribution.ALREADY_FILED); return; }
            desk.file(it, c == KeyCode.R);
            sfx("choice_move");
            persist();
            if (desk.finished()) {
                phase = Phase.REPORT;
                sfx("door_close");
            } else if (desk.round() != it.round) {
                // the round turned over
                sel = 0;
                ui.toast(Attribution.ROUND_FILED);
            } else {
                // Move to the next line still to decide. Without this, filing
                // by keyboard means pressing the same key twice on the same
                // line and being told it is already filed -- which is what the
                // first render of this screen did, twenty-three times.
                sel = nextUnfiled(desk.roundItems());
            }
        }
    }

    /** The first line of the round still to be decided, or 0 if there is none. */
    static int nextUnfiled(List<Attribution.Item> round) {
        for (int i = 0; i < round.size(); i++) if (!round.get(i).filed()) return i;
        return 0;
    }

    void warn(String text) {
        notice = text;
        noticeTimer = 3.5;
        sfx("door_close");
    }

    void persist() {
        try { desk.save(save); }
        catch (Exception ex) { notice = "the edition could not be written down: " + ex.getMessage(); noticeTimer = 6; }
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
        gc.fillText(Attribution.WORDMARK, M, 72);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = switch (phase) {
            case OPEN -> Attribution.WHERE_OPEN;
            case NIGHT -> Attribution.roundWhere(desk.round());
            case REPORT -> Attribution.WHERE_REPORT;
        };
        gc.fillText(right, W - M - 200, 72);

        switch (phase) {
            case OPEN -> drawOpen();
            case NIGHT -> drawNight();
            case REPORT -> drawReport();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(FALSE_C);
            gc.fillText(notice, M, H - 64);
        }
    }

    // ---------------------------------------------------------------- open

    void drawOpen() {
        double y = 140;
        for (String p : Attribution.OPENING) {
            for (String line : wrap(p, F_SCENE, LEFT_W)) {
                gc.setFont(F_SCENE);
                gc.setFill(INK);
                gc.fillText(line, M, y);
                y += 26;
            }
            y += 12;
        }

        drawRulesPanel();

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Attribution.START_LINE, M, H - 74);
    }

    void drawRulesPanel() {
        panel(RIGHT_X - 18, 122, RIGHT_W + 36, 470);
        double x = RIGHT_X, y = 152;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Attribution.RULES_HEADING, x, y);
        y += 28;
        for (String[] r : Attribution.RULES) {
            if (!r[0].isEmpty()) {
                gc.setFont(F_MONO_S);
                gc.setFill(GOLD);
                gc.fillText(r[0], x, y);
            }
            for (String line : wrap(r[1], F_SMALL, RIGHT_W - 76)) {
                gc.setFont(F_SMALL);
                gc.setFill(DIM);
                gc.fillText(line, x + 76, y);
                y += 18;
            }
            y += 12;
        }

        // Who is on the desk. The names are not the game -- which of them is
        // worth anything is -- but a player should know who filed tonight.
        y += 16;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Attribution.WHO_FILES_HERE, x, y);
        y += 26;
        for (Attribution.Stringer s : desk.stringers) {
            gc.setFont(F_LINE);
            gc.setFill(INK);
            gc.fillText(s.name, x, y);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(s.beat, x + 130, y);
            y += 24;
        }
    }

    // --------------------------------------------------------------- night

    void drawNight() {
        List<Attribution.Item> round = desk.roundItems();
        if (round.isEmpty()) { drawReport(); return; }
        if (sel >= round.size()) sel = 0;

        double y = 150;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Attribution.WHERE_NIGHT, M, y);
        y += 34;

        for (int i = 0; i < round.size(); i++) {
            Attribution.Item it = round.get(i);
            boolean on = i == sel;
            double boxH = 104;
            if (on) {
                gc.setFill(SEL);
                gc.fillRoundRect(M - 16, y - 30, LEFT_W + 32, boxH, 8, 8);
            }
            gc.setFont(F_MONO);
            gc.setFill(on ? GOLD : FAINT);
            gc.fillText(String.valueOf(i + 1), M, y);

            gc.setFont(F_LINE);
            gc.setFill(it.filed() ? DIM : INK);
            List<String> lines = wrap(it.text, F_LINE, LEFT_W - 150);
            double ty = y;
            for (String line : lines) {
                gc.fillText(line, M + 40, ty);
                ty += 25;
            }

            Attribution.Stringer s = desk.stringers.get(it.stringer);
            gc.setFont(F_BYLINE);
            gc.setFill(FAINT);
            gc.fillText("\u2014 " + s.name + ", " + s.beat, M + 40, ty + 2);

            // The verdict on the line, if it was paid for.
            String mark = "";
            Color mc = FAINT;
            if (it.calledTrue()) { mark = Attribution.WAS_TRUE; mc = TRUE_C; }
            else if (it.calledFalse()) { mark = Attribution.WAS_FALSE; mc = FALSE_C; }
            else if (it.filed()) { mark = Attribution.NO_CALL; }
            if (!mark.isEmpty()) {
                gc.setFont(F_MONO_S);
                gc.setFill(mc);
                gc.fillText(mark, M + LEFT_W - 60, y);
            }

            // What you did with it.
            if (it.filed()) {
                gc.setFont(F_MONO_S);
                gc.setFill(it.ran() ? TRUE_C : FALSE_C);
                gc.fillText(it.ran() ? Attribution.RUN : Attribution.SPIKE, M + LEFT_W - 60, y + 20);
            }

            y += boxH;
        }

        drawDeskPanel();
        hint("1 - 4 select.  C call.  R run.  S spike.  ESC for the library.");
    }

    void drawDeskPanel() {
        panel(RIGHT_X - 18, 122, RIGHT_W + 36, 310);
        double x = RIGHT_X, y = 152;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Attribution.THE_DESK, x, y);
        y += 30;

        gc.setFont(F_MONO);
        gc.setFill(desk.callsLeft > 0 ? GOLD : FAINT);
        gc.fillText(Attribution.callsWhere(desk.callsLeft), x, y);
        y += 24;
        gc.setFont(F_MONO_S);
        gc.setFill(DIM);
        gc.fillText(Attribution.RUN + "  " + desk.ran(), x, y);
        gc.fillText(Attribution.SPIKE + "  " + desk.spiked(), x + 130, y);
        y += 34;

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Attribution.WHO_YOU_CALLED, x, y);
        y += 26;

        if (desk.callsMade() == 0) {
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Attribution.NOBODY_CALLED, x, y);
            return;
        }

        for (int i = 0; i < desk.stringers.size(); i++) {
            Attribution.Stringer s = desk.stringers.get(i);
            int n = desk.calledBy(i);
            gc.setFont(F_LINE);
            gc.setFill(n > 0 ? INK : FAINT);
            gc.fillText(s.name, x, y);
            gc.setFont(F_MONO_S);
            if (n == 0) {
                gc.setFill(FAINT);
                gc.fillText(Attribution.NOT_CALLED, x + 150, y);
            } else {
                gc.setFill(DIM);
                gc.fillText(n + " " + Attribution.CALLED, x + 150, y);
                gc.setFill(desk.calledTrueBy(i) > 0 ? TRUE_C : FAINT);
                gc.fillText(desk.calledTrueBy(i) + " " + Attribution.WAS_TRUE, x + 250, y);
            }
            y += 26;
        }
    }

    // -------------------------------------------------------------- report

    void drawReport() {
        double y = 118;
        gc.setFont(F_HEAD);
        gc.setFill(desk.accuracy() >= 0.75 ? TRUE_C : GOLD);
        gc.fillText(desk.headline(), M, y);

        y = 164;
        for (String line : wrap(desk.verdict(), F_SCENE, W - 2 * M)) {
            if (line.isEmpty()) { y += 10; continue; }
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 26;
        }

        y += 18;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Attribution.THE_EDITION, M, y);
        y += 10;
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(M, y, W - M, y);
        y += 26;

        // The desk, as it actually was. This is the only place the game says
        // what a byline was worth, and it says it after the edition is out.
        gc.setFont(F_MONO_S);
        gc.setFill(FAINT);
        gc.fillText("BYLINE", M, y);
        gc.fillText(Attribution.FILED, M + 250, y);
        gc.fillText(Attribution.TRUE_OF, M + 380, y);
        gc.fillText(Attribution.CALLED, M + 520, y);
        y += 22;
        for (int i = 0; i < desk.stringers.size(); i++) {
            Attribution.Stringer s = desk.stringers.get(i);
            int called = desk.calledBy(i);
            gc.setFont(F_SMALL);
            gc.setFill(called > 0 ? INK : DIM);
            gc.fillText(s.name, M, y);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(s.beat, M + 96, y);
            gc.setFont(F_MONO_S);
            gc.setFill(DIM);
            gc.fillText(String.valueOf(desk.filedBy(i)), M + 250, y);
            gc.setFill(desk.trueBy(i) * 2 >= desk.filedBy(i) ? TRUE_C : FALSE_C);
            gc.fillText(desk.trueBy(i) + " of " + desk.filedBy(i), M + 380, y);
            gc.setFill(called > 0 ? DIM : FALSE_C);
            gc.fillText(called > 0 ? String.valueOf(called) : Attribution.NOT_CALLED, M + 520, y);
            y += 24;
        }

        y += 16;
        gc.setFont(F_SCENE);
        gc.setFill(Color.web("#C9C9D4"));
        for (String line : wrap(desk.callsLine(), F_SCENE, W - 2 * M)) {
            gc.fillText(line, M, y);
            y += 26;
        }

        y += 14;
        gc.setFont(F_SCENE);
        gc.setFill(Color.web("#B0B0BE"));
        for (String line : wrap(desk.closing(), F_SCENE, W - 2 * M)) {
            gc.fillText(line, M, y);
            y += 26;
        }

        y += 14;
        gc.setFont(F_SCENE);
        gc.setFill(GOLD);
        for (String line : wrap(Attribution.STANDING, F_SCENE, W - 2 * M)) {
            gc.fillText(line, M, y);
            y += 26;
        }

        hint("R for another night. ENTER for the library.");
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
        gc.setFill(Color.rgb(150, 150, 165, 0.6));
        gc.fillText(text, M, H - 36);
    }

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
