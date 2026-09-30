package aside.games.redaction;

import aside.ui.LibraryScreen;
import aside.ui.UiManager;
import aside.ui.UiScreen;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.util.List;

/**
 * redaction, drawn.
 *
 * The whole screen is one column of sixteen lines, and the column is the
 * design. The board reads the file top to bottom, so the player's only lever
 * is where the bars go in a list that is read in order -- and a list read in
 * order is a list where position is the whole of the strategy. Two columns
 * would have made the file easier to fit and impossible to reason about.
 *
 * A withheld line is drawn as a BAR, not as dimmed text. The bar is the width
 * of the line it covers, because that is what a redaction is: not a hole, a
 * shape where something was. It also means the screen cannot be read for the
 * thing the player is deciding about, which is right -- the player is supposed
 * to be deciding, not re-reading.
 *
 * Three beats:
 *
 *   OPEN    the premise and the rule, once
 *   FILE    sixteen lines, a bar or not a bar, and one send
 *   REPORT  what the board read, and the three lines it wrote down
 *
 * The screen never tells the player whether a bar was worth it. It cannot: the
 * board's reading is not known until the file is sent, and the whole subject
 * of the game is that a bar costs something whether or not it works.
 */
public class RedactionScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 24);
    static final Font F_OPEN = Font.font("Georgia", 17);
    static final Font F_LINE = Font.font("Arial", 14);
    static final Font F_LINE_SEL = Font.font("Arial", 14);
    static final Font F_MONO = Font.font("Consolas", 13);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);

    static final Color BG = Color.web("#0A0A0C");
    static final Color PAPER = Color.web("#131317");
    static final Color GOLD = Color.web("#F2C14E");
    static final Color INK = Color.web("#E9E9EF");
    static final Color DIM = Color.web("#A2A2B0");
    static final Color FAINT = Color.web("#6B6B7C");
    static final Color RED = Color.web("#C4553F");
    static final Color GREEN = Color.web("#7FA88A");
    static final Color BAR = Color.web("#3C3C4A");
    static final Color BAR_SEL = Color.web("#4E4E60");
    static final Color SEL = Color.web("#1C1C24");

    static final double M = 72;
    static final double ROW_TOP = 152;
    static final double ROW_STEP = 33;
    static final double NUM_X = 100;
    static final double TEXT_X = 118;
    static final double MARK_X = W - M;

    enum Phase { OPEN, FILE, REPORT }

    final Redaction r;
    final Path save;

    Phase phase = Phase.OPEN;
    int row = 0;
    String notice = "";
    double noticeTimer = 0;

    public RedactionScreen(UiManager ui, Redaction r, Path save) {
        super(ui);
        this.r = r;
        this.save = save;
        if (r.sent) phase = Phase.REPORT;
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode k = e.getCode();
        if (k == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        if (phase == Phase.OPEN) {
            if (k == KeyCode.ENTER || k == KeyCode.SPACE) phase = Phase.FILE;
        } else if (phase == Phase.FILE) {
            if (k == KeyCode.UP) row = (row - 1 + Redaction.LINES) % Redaction.LINES;
            else if (k == KeyCode.DOWN) row = (row + 1) % Redaction.LINES;
            else if (k == KeyCode.W) set(row, true);
            else if (k == KeyCode.R) set(row, false);
            else if (k == KeyCode.ENTER || k == KeyCode.SPACE) send();
            else {
                // KeyCode names, not characters: the engine's own snapshot mode
                // delivers synthetic KeyEvents and getText() is empty on them.
                // See the note in reference/aside-engine.md -- this is the same
                // trap FNAF 2's camera selector fell into.
                int d = digit(k);
                if (d >= 1 && d <= 9) row = d - 1;
            }
        } else {
            if (k == KeyCode.ENTER || k == KeyCode.SPACE) ui.replace(new LibraryScreen(ui));
            else if (k == KeyCode.R) again();
        }
        e.consume();
    }

    /** 1..9 for DIGIT1..DIGIT9, and 0 for anything else. */
    static int digit(KeyCode c) {
        for (int d = 1; d <= 9; d++) {
            if (c == KeyCode.valueOf("DIGIT" + d)) return d;
        }
        return 0;
    }

    void set(int i, boolean withheld) {
        r.lines.get(i).withheld = withheld;
        notice = "";
        try { r.save(save); } catch (Exception ignored) { }
    }

    void send() {
        r.sent = true;
        try { r.save(save); } catch (Exception ignored) { }
        phase = Phase.REPORT;
    }

    void again() {
        Redaction fresh = Redaction.of();
        try { fresh.save(save); } catch (Exception ignored) { }
        ui.replace(new RedactionScreen(ui, fresh, save));
    }

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        draw();
    }

    // ------------------------------------------------------------------ draw

    void draw() {
        gc.setFill(BG);
        gc.fillRect(0, 0, W, H);
        if (phase == Phase.OPEN) drawOpen();
        else if (phase == Phase.FILE) drawFile();
        else drawReport();
    }

    void header(String where) {
        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(Redaction.WORDMARK, M, 62);
        gc.setFill(FAINT);
        gc.setFont(F_SMALL);
        gc.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
        gc.fillText(where, W - M, 60);
        gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
    }

    void drawOpen() {
        header(Redaction.WHERE_OPEN);
        double y = 108;
        gc.setFont(F_OPEN);
        gc.setFill(INK);
        for (String p : Redaction.OPENING) {
            for (String line : wrap(p, F_OPEN, W - 2 * M - 20)) {
                gc.fillText(line, M, y);
                y += 25;
            }
            y += 8;
        }

        y += 6;
        gc.setFill(FAINT);
        gc.setFont(F_SMALL);
        gc.fillText(Redaction.RULES_HEADING, M, y);
        y += 24;
        for (String[] rule : Redaction.RULES) {
            gc.setFill(GOLD);
            gc.setFont(F_MONO);
            gc.fillText(rule[0], M, y);
            gc.setFill(DIM);
            gc.setFont(F_SMALL);
            for (String line : wrap(rule[1], F_SMALL, W - 2 * M - 90)) {
                gc.fillText(line, M + 84, y);
                y += 19;
            }
            y += 5;
        }

        gc.setFill(GOLD);
        gc.setFont(F_SMALL);
        gc.fillText(Redaction.START_LINE, M, H - 40);
    }

    void drawFile() {
        header(Redaction.WHERE_FILE);

        int held = r.withheld();
        Redaction.Reading reading = r.reading();
        gc.setFont(F_SMALL);
        gc.setFill(held == 0 ? FAINT : DIM);
        gc.fillText(Redaction.heldLine(held), M, 112);
        gc.setFill(held == 0 ? FAINT : GOLD);
        gc.fillText(held == 0 ? "" : Redaction.digsLine(reading.recovered.size()), M + 240, 112);

        for (int i = 0; i < Redaction.LINES; i++) {
            Redaction.Line l = r.lines.get(i);
            double y = ROW_TOP + i * ROW_STEP;
            boolean sel = i == row;

            if (sel) {
                gc.setFill(SEL);
                gc.fillRoundRect(M - 12, y - 19, W - 2 * M + 24, ROW_STEP - 4, 8, 8);
            }
            gc.setFill(sel ? GOLD : FAINT);
            gc.setFont(F_MONO);
            gc.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
            gc.fillText(String.valueOf(l.number), NUM_X, y);
            gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);

            double tw = textWidth(l.text, F_LINE);
            if (l.withheld) {
                // The bar is the width of the line it covers. That is what a
                // redaction is, and it is also the only thing the board will
                // have to go on.
                gc.setFill(sel ? BAR_SEL : BAR);
                gc.fillRoundRect(TEXT_X, y - 13, tw, 18, 3, 3);
            } else {
                gc.setFill(sel ? INK : DIM);
                gc.setFont(F_LINE);
                gc.fillText(l.text, TEXT_X, y);
            }

            gc.setFont(F_TINY);
            gc.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
            gc.setFill(l.withheld ? RED : FAINT);
            gc.fillText(l.withheld ? Redaction.WITHHELD_MARK : Redaction.RELEASED_MARK, MARK_X, y);
            gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
        }

        gc.setFill(FAINT);
        gc.setFont(F_TINY);
        gc.fillText("up/down select    W withhold    R release    ENTER send the file",
                M, H - 40);
        gc.setFill(GOLD);
        gc.fillText(Redaction.SEND_WARNING, M, H - 20);
    }

    void drawReport() {
        Redaction.Finding f = r.finding();
        header(Redaction.WHERE_REPORT);

        gc.setFill(f.nameOut ? RED : (f.failsOut >= 3 ? GOLD : GREEN));
        gc.setFont(F_HEAD);
        gc.fillText(Redaction.headline(f), M, 108);

        gc.setFont(F_OPEN);
        gc.setFill(INK);
        gc.fillText(Redaction.personLine(f.nameOut), M, 142);
        gc.fillText(Redaction.recordLine(f.failsOut), M, 168);
        gc.fillText(Redaction.withholdingLine(f.withheld), M, 194);

        double y = 234;
        gc.setFill(FAINT);
        gc.setFont(F_SMALL);
        gc.fillText(Redaction.WHAT_WAS_READ, M, y);
        y += 22;
        gc.setFont(F_LINE);
        if (f.recovered.isEmpty()) {
            gc.setFill(FAINT);
            gc.fillText(Redaction.NOTHING_READ, M, y);
            y += 22;
        } else {
            for (Redaction.Line l : f.recovered) {
                gc.setFill(GOLD);
                gc.setFont(F_MONO);
                gc.setTextAlign(javafx.scene.text.TextAlignment.RIGHT);
                gc.fillText(String.valueOf(l.number), NUM_X, y);
                gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
                gc.setFill(DIM);
                gc.setFont(F_LINE);
                gc.fillText(l.text, TEXT_X, y);
                y += 21;
            }
            y += 6;
        }

        gc.setFill(FAINT);
        gc.setFont(F_SMALL);
        for (String line : wrap(Redaction.closing(f), F_SMALL, W - 2 * M)) {
            gc.fillText(line, M, y);
            y += 19;
        }
        y += 14;

        gc.setFont(F_OPEN);
        gc.setFill(INK);
        for (String line : wrap(Redaction.verdict(f), F_OPEN, W - 2 * M)) {
            gc.fillText(line, M, y);
            y += 24;
        }
        y += 12;
        gc.setFill(GOLD);
        gc.setFont(F_SMALL);
        for (String line : wrap(Redaction.STANDING, F_SMALL, W - 2 * M)) {
            gc.fillText(line, M, y);
            y += 19;
        }

        gc.setFill(FAINT);
        gc.setFont(F_TINY);
        gc.fillText("ENTER for the library    R to take the file again", M, H - 40);
    }

    // ----------------------------------------------------------------- text

    /** Measure a string in a font. Needed for the bar, which is text-width. */
    static double textWidth(String s, Font f) {
        Text t = new Text(s);
        t.setFont(f);
        return t.getLayoutBounds().getWidth();
    }

    /** Greedy wrap. The engine has no text layout, so screens do their own. */
    static java.util.List<String> wrap(String s, Font f, double maxWidth) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (textWidth(probe, f) > maxWidth && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    /** Unused, but kept so the shape of the screen matches the others. */
    List<Redaction.Line> lines() { return r.lines; }
}
