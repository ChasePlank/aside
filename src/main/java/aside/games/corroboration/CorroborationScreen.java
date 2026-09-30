package aside.games.corroboration;

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
 * corroboration, drawn.
 *
 * Two columns, one per observer, and the columns are the whole design. What
 * each of them says about the fourteenth sits directly above what they said
 * when you asked them about the eleventh, so the comparison the game is made
 * of is a thing the eye does without being told to.
 *
 * The screen never says whether a check was worth anything. It cannot: a
 * clean check on a person who is stuck looks exactly like a clean check on a
 * person who is straight, and the difference is the game. All it shows is
 * what was said and what the log says, and the player does the rest.
 *
 * Four beats:
 *
 *   OPEN   the premise and the rule, once
 *   ASK    the two accounts, and the questions
 *   FILE   four lines, each a value or a blank
 *   END    what was written, what happened, and which of the two was known
 */
public class CorroborationScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 22);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_OPEN = Font.font("Georgia", 18);
    static final Font F_ITEM = Font.font("Georgia", 19);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_MONO = Font.font("Consolas", 17);
    static final Font F_MONO_S = Font.font("Consolas", 14);

    static final double M = 84;
    static final double COL = 600;

    static final Color BG = Color.web("#0A0A12");
    static final Color GOLD = Color.web("#F2C14E");
    static final Color INK = Color.web("#E8E8EF");
    static final Color DIM = Color.web("#9A9AAE");
    static final Color FAINT = Color.web("#6E6E86");
    static final Color RED = Color.web("#E94560");
    static final Color GREEN = Color.web("#7FD1AE");
    static final Color EDGE = Color.web("#22222E");

    enum Phase { OPEN, ASK, FILE, END }

    final Corroboration c;
    final Path save;

    Phase phase = Phase.OPEN;
    int row = 0;          // 0..7, observer * N + axis
    int fileRow = 0;
    String notice = "";
    double noticeTimer = 0;

    public CorroborationScreen(UiManager ui, Corroboration c, Path save) {
        super(ui);
        this.c = c;
        this.save = save;
        if (c.filedDone) phase = Phase.END;
        else if (c.left < Corroboration.BUDGET) phase = Phase.ASK;
    }

    static int obs(int row) { return row / Corroboration.N; }
    static int axis(int row) { return row % Corroboration.N; }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode k = e.getCode();
        if (k == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case OPEN -> {
                if (k == KeyCode.ENTER || k == KeyCode.SPACE) { phase = Phase.ASK; sfx("choice_select"); }
            }
            case ASK -> ask(k);
            case FILE -> file(k);
            case END -> {
                if (k == KeyCode.R) { reset(); }
                else if (k == KeyCode.ENTER || k == KeyCode.SPACE) ui.replace(new LibraryScreen(ui));
            }
        }
        e.consume();
    }

    void ask(KeyCode k) {
        switch (k) {
            case UP -> { row = (row + 7) % 8; sfx("choice_move"); }
            case DOWN -> { row = (row + 1) % 8; sfx("choice_move"); }
            case LEFT, RIGHT -> { row = (row + 4) % 8; sfx("choice_move"); }
            case ENTER, SPACE -> {
                if (!c.canCheck(obs(row), axis(row))) { say("no questions left"); return; }
                boolean already = c.asked[obs(row)][axis(row)];
                c.check(obs(row), axis(row));
                if (!already) sfx("light_click");
                persist();
                if (c.left == 0) say("that was the last question");
            }
            case F -> { phase = Phase.FILE; sfx("choice_select"); }
            default -> { }
        }
    }

    void file(KeyCode k) {
        int a = fileRow;
        switch (k) {
            case UP -> { fileRow = (fileRow + Corroboration.N - 1) % Corroboration.N; sfx("choice_move"); }
            case DOWN -> { fileRow = (fileRow + 1) % Corroboration.N; sfx("choice_move"); }
            case LEFT -> { c.filed[a] = prev(c.filed[a]); sfx("choice_move"); persist(); }
            case RIGHT -> { c.filed[a] = next(c.filed[a]); sfx("choice_move"); persist(); }
            case ENTER, SPACE -> {
                if (fileRow == Corroboration.N - 1) {
                    c.filedDone = true;
                    persist();
                    phase = Phase.END;
                    sfx("door_close");
                } else {
                    fileRow++;
                    sfx("choice_move");
                }
            }
            case A -> { phase = Phase.ASK; sfx("choice_select"); }
            default -> { }
        }
    }

    /** -- then the three values, then back to --. */
    static int next(int v) { return v >= Corroboration.VALUES - 1 ? Corroboration.BLANK : v + 1; }
    static int prev(int v) { return v <= Corroboration.BLANK ? Corroboration.VALUES - 1 : v - 1; }

    void reset() {
        try { java.nio.file.Files.deleteIfExists(save); } catch (Exception ignored) { }
        ui.replace(new CorroborationScreen(ui, Corroboration.newNight((int) (System.nanoTime() & 0x7fffffff)), save));
    }

    void persist() {
        try { c.save(save); } catch (Exception ignored) { }
    }

    void say(String s) { notice = s; noticeTimer = 2.4; }

    static void sfx(String name) { if (Audio.A != null) Audio.A.sfx(name, "choice_select"); }

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        draw();
    }

    // --------------------------------------------------------------- drawing

    void draw() {
        gc.setFill(BG);
        gc.fillRect(0, 0, W, H);
        switch (phase) {
            case OPEN -> drawOpen();
            case ASK -> drawAsk();
            case FILE -> drawFile();
            case END -> drawEnd();
        }
        if (noticeTimer > 0) {
            gc.setFill(FAINT);
            gc.setFont(F_SMALL);
            gc.fillText(notice, M, H - 26);
        }
    }

    void drawOpen() {
        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(Corroboration.OPEN_HEAD, M, 96);
        gc.setFill(FAINT);
        gc.setFont(F_TINY);
        gc.fillText("THE LOG, CLOSED FOR THE NIGHT", M, 120);

        double y = 146;
        gc.setFont(F_OPEN);
        for (String line : Corroboration.OPEN) {
            if (!line.isBlank()) { gc.setFill(DIM); gc.fillText(line, M, y); }
            y += 21;
        }
        gc.setFill(GOLD);
        gc.setFont(F_SMALL);
        gc.fillText("enter to begin", M, H - 46);
    }

    void drawAsk() {
        header("THE FOURTEENTH", c.left + (c.left == 1 ? " QUESTION LEFT" : " QUESTIONS LEFT"));

        for (int o = 0; o < 2; o++) {
            double x = M + o * COL;
            gc.setFill(GOLD);
            gc.setFont(F_HEAD);
            gc.fillText(Corroboration.OBSERVERS[o].toUpperCase(), x, 186);

            for (int a = 0; a < Corroboration.N; a++) {
                int r = o * Corroboration.N + a;
                double y = 236 + a * 66;
                boolean sel = r == row;

                if (sel) {
                    gc.setFill(Color.web("#14141C"));
                    gc.fillRoundRect(x - 12, y - 26, COL - 40, 60, 10, 10);
                    gc.setStroke(GOLD);
                    gc.setLineWidth(1);
                    gc.strokeRoundRect(x - 12, y - 26, COL - 40, 60, 10, 10);
                }

                gc.setFill(sel ? INK : DIM);
                gc.setFont(F_ITEM);
                gc.fillText(Corroboration.axisName(a), x, y);

                gc.setFont(F_MONO_S);
                gc.setFill(FAINT);
                gc.fillText("says", x + 220, y);
                gc.setFill(INK);
                gc.fillText(Corroboration.valueName(a, c.claim(o, a)), x + 268, y);

                gc.setFill(FAINT);
                gc.fillText("check", x + 220, y + 22);
                if (!c.asked[o][a]) {
                    gc.setFill(FAINT);
                    gc.fillText("--", x + 268, y + 22);
                } else if (c.caught(o, a)) {
                    gc.setFill(RED);
                    gc.fillText("caught", x + 268, y + 22);
                } else {
                    gc.setFill(GREEN);
                    gc.fillText("clean", x + 268, y + 22);
                }
            }
        }

        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(M, 546, W - M, 546);

        gc.setFill(FAINT);
        gc.setFont(F_TINY);
        gc.fillText("THE ELEVENTH, AS IT STANDS IN THE LOG", M, 578);
        gc.setFill(DIM);
        gc.setFont(F_MONO);
        gc.fillText(c.logLine(), M, 604);
        gc.setFill(FAINT);
        gc.setFont(F_TINY);
        gc.fillText("(the hour, the bearing, the height, the motion)", M, 626);

        gc.setFill(GOLD);
        gc.setFont(F_SMALL);
        gc.fillText(Corroboration.ASK_HINT, M, H - 46);
        gc.setFill(FAINT);
        gc.fillText("f to write the entry", W - M - 150, H - 46);
    }

    void drawFile() {
        header("THE ENTRY", "THE FOURTEENTH");
        gc.setFill(DIM);
        gc.setFont(F_SCENE);
        gc.fillText("Whatever you write becomes what happened.", M, 190);

        for (int a = 0; a < Corroboration.N; a++) {
            double y = 260 + a * 74;
            boolean sel = a == fileRow;
            if (sel) {
                gc.setFill(Color.web("#14141C"));
                gc.fillRoundRect(M - 14, y - 30, 720, 58, 10, 10);
                gc.setStroke(GOLD);
                gc.setLineWidth(1);
                gc.strokeRoundRect(M - 14, y - 30, 720, 58, 10, 10);
            }
            gc.setFill(sel ? INK : DIM);
            gc.setFont(F_ITEM);
            gc.fillText(Corroboration.axisName(a), M, y);

            double bx = M + 260;
            for (int v = Corroboration.BLANK; v < Corroboration.VALUES; v++) {
                double bw = v == Corroboration.BLANK ? 62 : 108;
                boolean on = c.filed[a] == v;
                gc.setFill(on ? GOLD : Color.web("#14141C"));
                gc.fillRoundRect(bx, y - 22, bw, 32, 8, 8);
                gc.setFill(on ? BG : FAINT);
                gc.setFont(F_MONO_S);
                gc.fillText(Corroboration.valueName(a, v), bx + 14, y);
                bx += bw + 12;
            }
        }

        gc.setFill(GOLD);
        gc.setFont(F_SMALL);
        gc.fillText(Corroboration.FILE_HINT, M, H - 46);
        gc.setFill(FAINT);
        gc.fillText("a to go back to the questions", W - M - 220, H - 46);
    }

    void drawEnd() {
        header("THE LOG, CLOSED", "THE FOURTEENTH");

        for (int a = 0; a < Corroboration.N; a++) {
            double y = 214 + a * 52;
            Corroboration.Verdict v = c.verdict(a);

            gc.setFill(DIM);
            gc.setFont(F_ITEM);
            gc.fillText(Corroboration.axisName(a), M, y);

            gc.setFont(F_MONO_S);
            gc.setFill(FAINT);
            gc.fillText("wrote", M + 200, y);
            gc.setFill(v == Corroboration.Verdict.WRONG ? RED : INK);
            gc.fillText(Corroboration.valueName(a, c.filed[a]), M + 256, y);

            gc.setFill(FAINT);
            gc.fillText("was", M + 400, y);
            gc.setFill(INK);
            gc.fillText(Corroboration.valueName(a, c.truth[a]), M + 444, y);

            gc.setFill(verdictColor(v));
            gc.setFont(F_SMALL);
            gc.fillText(verdictWord(v), M + 600, y);
        }

        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(M, 452, W - M, 452);

        double y = 490;
        gc.setFont(F_SCENE);
        for (String line : wrap(c.closing(), F_SCENE, W - 2 * M)) {
            gc.setFill(DIM);
            gc.fillText(line, M, y);
            y += 27;
        }

        gc.setFill(GOLD);
        gc.setFont(F_SMALL);
        gc.fillText(Corroboration.END_HINT, M, H - 46);
    }

    static Color verdictColor(Corroboration.Verdict v) {
        return switch (v) {
            case KNOWN -> GREEN;
            case LUCKY -> GOLD;
            case WRONG -> RED;
            case BLANK -> DIM;
            case WITHHELD -> RED;
        };
    }

    static String verdictWord(Corroboration.Verdict v) {
        return switch (v) {
            case KNOWN -> "known";
            case LUCKY -> "right, and unchecked";
            case WRONG -> "wrong";
            case BLANK -> "not established";
            case WITHHELD -> "known, and not written";
        };
    }

    void header(String left, String right) {
        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(left, M, 92);
        gc.setFill(FAINT);
        gc.setFont(F_TINY);
        gc.fillText(right, M, 116);
    }

    // ---------------------------------------------------------------- text

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
