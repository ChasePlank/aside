package aside.games.ledger;

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
 * Ledger, drawn.
 *
 * Two columns, and the split is the whole design. The left is the hotel:
 * what is happening, and everything you could write down about it. The
 * right is the ledger: five lines, and nothing else. The player's eye is
 * supposed to keep going back to the right column while the left column
 * keeps offering more, because that is the actual experience of the game.
 *
 * Four beats:
 *
 *   NIGHT      a night, and the things in it you could record
 *   RECKONING  a question from somebody who was not there
 *   VERDICT    what the answer was, and whether you still had it
 *   END        what you kept, and everything you did not
 *
 * The one thing this screen deliberately never does is tell you which
 * details matter. It cannot: the game does not know either, because what
 * matters is decided by the question, and the question comes later.
 */
public class LedgerScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 24);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_DETAIL = Font.font("Georgia", 18);
    static final Font F_ITEM = Font.font("Georgia", 19);
    static final Font F_QUESTION = Font.font("Georgia", 23);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_MONO = Font.font("Consolas", 15);

    static final double M = 84;                 // outer margin
    static final double LEFT_W = 660;           // hotel column
    static final double RIGHT_X = 800;          // ledger column
    static final double RIGHT_W = 400;

    static final Color GOLD = Color.web("#F2C14E");
    static final Color INK = Color.web("#E8E8EF");
    static final Color DIM = Color.web("#9A9AAE");
    static final Color FAINT = Color.web("#6E6E86");
    static final Color RED = Color.web("#E94560");
    static final Color GREEN = Color.web("#7FD1AE");

    enum Phase { NIGHT, RECKONING, VERDICT, END }

    final Ledger ledger;
    final Path save;

    Phase phase = Phase.NIGHT;
    int index = 0;
    boolean lastCorrect;
    String lastAnswer;
    String notice = "";
    double noticeTimer = 0;

    public LedgerScreen(UiManager ui, Ledger ledger, Path save) {
        super(ui);
        this.ledger = ledger;
        this.save = save;
        // A ledger that is already closed has no night to open on. Without
        // this the player who finishes the game and comes back lands on an
        // empty screen -- found by rendering the end state and reopening it.
        if (ledger.finished) phase = Phase.END;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case NIGHT -> night(c);
            case RECKONING -> reckoning(c);
            case VERDICT -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    ledger.advanceNight();
                    phase = ledger.finished ? Phase.END : Phase.NIGHT;
                    index = 0;
                    persist();
                }
            }
            case END -> {
                if (c == KeyCode.R) {
                    ledger.lines.clear();
                    ledger.seen.clear();
                    ledger.night = 0;
                    ledger.reck = 0;
                    ledger.correct = 0;
                    ledger.finished = false;
                    phase = Phase.NIGHT;
                    index = 0;
                    persist();
                } else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    ui.replace(new LibraryScreen(ui));
                }
            }
        }
        e.consume();
    }

    void night(KeyCode c) {
        Ledger.Night n = ledger.currentNight();
        if (n == null) { phase = Phase.END; return; }
        int d = digit(c);
        if (d >= 1 && d <= n.details.size()) {
            ledger.toggle(n.details.get(d - 1).id);
            sfx("choice_move");
            persist();
            return;
        }
        if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
            ledger.closeNight();
            persist();
            if (ledger.reckoningDue()) {
                phase = Phase.RECKONING;
                index = 0;
            } else {
                ledger.advanceNight();
                phase = ledger.finished ? Phase.END : Phase.NIGHT;
                index = 0;
            }
            sfx("choice_select");
        }
    }

    void reckoning(KeyCode c) {
        int rows = ledger.lines.size() + 1;      // + "I don't know"
        if (c == KeyCode.UP) { index = (index - 1 + rows) % rows; sfx("choice_move"); }
        else if (c == KeyCode.DOWN) { index = (index + 1) % rows; sfx("choice_move"); }
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) { answerAt(index); }
        else {
            int d = digit(c);
            if (d == 0) answerAt(ledger.lines.size());
            else if (d >= 1 && d <= ledger.lines.size()) answerAt(d - 1);
        }
    }

    void answerAt(int i) {
        String line = i >= ledger.lines.size() ? null : ledger.lines.get(i);
        lastAnswer = line;
        lastCorrect = ledger.answer(line);
        phase = Phase.VERDICT;
        sfx(lastCorrect ? "choice_select" : "door_close");
        persist();
    }

    static int digit(KeyCode c) {
        return switch (c) {
            case DIGIT0, NUMPAD0 -> 0;
            case DIGIT1, NUMPAD1 -> 1;
            case DIGIT2, NUMPAD2 -> 2;
            case DIGIT3, NUMPAD3 -> 3;
            case DIGIT4, NUMPAD4 -> 4;
            case DIGIT5, NUMPAD5 -> 5;
            case DIGIT6, NUMPAD6 -> 6;
            default -> -1;
        };
    }

    void persist() {
        try { ledger.save(save); }
        catch (Exception ex) { notice = "the ledger could not be written down: " + ex.getMessage(); noticeTimer = 6; }
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
        gc.fillText("ledger", M, 76);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = ledger.finished
                ? "closed"
                : "night " + Math.min(ledger.night + 1, Ledger.NIGHTS.size()) + " of " + Ledger.NIGHTS.size();
        gc.fillText(right, W - M - 90, 76);

        switch (phase) {
            case NIGHT -> drawNight();
            case RECKONING -> drawReckoning();
            case VERDICT -> drawVerdict();
            case END -> drawEnd();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(RED);
            gc.fillText(notice, M, H - 66);
        }
    }

    // -------------------------------------------------------------- night

    void drawNight() {
        Ledger.Night n = ledger.currentNight();
        if (n == null) return;

        double y = 138;
        gc.setFont(F_MONO);
        gc.setFill(FAINT);
        gc.fillText(n.heading, M, y);
        y += 34;

        gc.setFont(F_HEAD);
        gc.setFill(INK);
        gc.fillText(n.title, M, y);
        y += 34;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(200, 200, 215, 0.72));
        for (String line : wrap(n.scene, F_SCENE, LEFT_W)) {
            gc.fillText(line, M, y);
            y += 28;
        }

        y += 26;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("YOU COULD WRITE DOWN", M, y);
        y += 30;

        for (int i = 0; i < n.details.size(); i++) {
            Ledger.Detail det = n.details.get(i);
            boolean held = ledger.holds(det.id);
            gc.setFont(F_DETAIL);
            gc.setFill(held ? GOLD : Color.web("#B9B9C6"));
            gc.fillText((held ? "  \u2713  " : "  \u00B7  ") + (i + 1) + ".  " + det.text, M, y);
            y += 30;
        }

        drawLedgerPanel();
        hint("1-4  write down or cross out     ENTER  end the night     ESC  leave");
    }

    // ---------------------------------------------------------- reckoning

    void drawReckoning() {
        Ledger.Reckoning r = ledger.reckoning();
        if (r == null) { phase = Phase.END; return; }

        double y = 150;
        gc.setFont(F_MONO);
        gc.setFill(FAINT);
        gc.fillText("THE INSPECTOR", M, y);
        y += 40;

        gc.setFont(F_QUESTION);
        gc.setFill(INK);
        for (String line : wrap(r.question, F_QUESTION, LEFT_W)) {
            gc.fillText(line, M, y);
            y += 34;
        }

        y += 30;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("ANSWER FROM THE LEDGER", M, y);
        y += 32;

        for (int i = 0; i < ledger.lines.size(); i++) {
            boolean sel = i == index;
            gc.setFont(F_ITEM);
            gc.setFill(sel ? GOLD : Color.web("#B9B9C6"));
            double ly = y;
            for (String line : wrap(ledger.lines.get(i).equals("") ? "" : Ledger.words(ledger.lines.get(i)),
                    F_ITEM, W - M * 2 - 70)) {
                gc.fillText((sel && ly == y ? "\u25B6  " : "   ") + (i + 1) + ".  " + line, M + 6, ly);
                ly += 28;
            }
            y = ly + 14;
        }

        boolean sel = index == ledger.lines.size();
        gc.setFont(F_ITEM);
        gc.setFill(sel ? GOLD : DIM);
        gc.fillText((sel ? "\u25B6  " : "   ") + "0.  " + Ledger.I_DO_NOT_KNOW, M + 6, y + 6);

        // The right column is where the ledger lives on every other beat.
        // Here the ledger IS the answer list, so the column holds the one
        // thing the player needs to understand about the question instead.
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 170, 0.75));
        double ny = 170;
        for (String line : wrap(Ledger.INSPECTOR_NOTE, F_SMALL, RIGHT_W)) {
            gc.fillText(line, RIGHT_X, ny);
            ny += 22;
        }

        hint("\u2191\u2193 choose     ENTER answer     ESC leave");
    }

    // ------------------------------------------------------------- verdict

    void drawVerdict() {
        Ledger.Reckoning r = ledger.reckoning() != null ? Ledger.RECKONINGS.get(ledger.reck - 1) : null;

        double y = 170;
        gc.setFont(F_HEAD);
        gc.setFill(lastCorrect ? GREEN : RED);
        gc.fillText(lastCorrect ? Ledger.HAD_IT : Ledger.DID_NOT_HAVE_IT, M, y);
        y += 44;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(200, 200, 215, 0.72));
        if (!lastCorrect) {
            String what = lastAnswer == null
                    ? Ledger.SAID_NOTHING
                    : Ledger.answered(lastAnswer);
            for (String line : wrap(what, F_SCENE, LEFT_W)) {
                gc.fillText(line, M, y);
                y += 28;
            }
            y += 14;
        }

        if (r != null) {
            for (String line : wrap(r.explanation, F_SCENE, LEFT_W)) {
                gc.fillText(line, M, y);
                y += 28;
            }
        }

        drawLedgerPanel();
        hint("ENTER  the next night     ESC  leave");
    }

    // ----------------------------------------------------------------- end

    void drawEnd() {
        double y = 128;
        gc.setFont(F_HEAD);
        gc.setFill(INK);
        gc.fillText(Ledger.CLOSES, M, y);
        y += 38;

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(200, 200, 215, 0.8));
        gc.fillText(Ledger.answeredCount(ledger.correct), M, y);
        y += 32;

        String closing = Ledger.endNote(ledger.correct);
        gc.setFill(DIM);
        for (String line : wrap(closing, F_SCENE, W - M * 2)) {
            gc.fillText(line, M, y);
            y += 26;
        }

        // Kept on the left, lost on the right. The two lists are the whole
        // game, so they get to be looked at next to each other.
        double top = y + 40;
        double lostX = M + 500;
        double lostW = W - M - lostX;

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText("STILL IN THE LEDGER", M, top);

        double ly = top + 28;
        gc.setFont(F_DETAIL);
        if (ledger.lines.isEmpty()) {
            gc.setFill(FAINT);
            gc.fillText("\u00B7  " + Ledger.NOTHING, M, ly);
        }
        for (String id : ledger.lines) {
            gc.setFill(GOLD);
            for (String line : wrap("\u2713  " + Ledger.words(id), F_DETAIL, 460)) {
                gc.fillText(line, M, ly);
                ly += 24;
            }
        }

        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 170, 0.8));
        gc.fillText("EVERYTHING YOU LET GO", lostX, top);

        double ry = top + 26;
        gc.setFont(F_TINY);
        gc.setFill(Color.rgb(140, 140, 160, 0.75));
        for (String id : ledger.lost()) {
            for (String line : wrap("\u00B7  " + Ledger.words(id), F_TINY, lostW)) {
                gc.fillText(line, lostX, ry);
                ry += 18;
            }
        }

        hint("ENTER  back to the library     R  start again     ESC  leave");
    }

    // ------------------------------------------------------------- ledger

    /**
     * The right column: the whole of what the clerk is carrying.
     *
     * Empty slots are drawn, not implied, because the size of the ledger is
     * the only thing the player is actually playing against. When it is
     * full, the line that would be pushed out next is named at the bottom,
     * so the cost is never a surprise -- only the regret is.
     */
    void drawLedgerPanel() {
        double x = RIGHT_X;
        double y = 138;

        gc.setFill(Color.rgb(20, 20, 32, 0.85));
        gc.fillRoundRect(x - 26, y - 44, RIGHT_W + 52, 470, 12, 12);

        gc.setFont(F_MONO);
        gc.setFill(GOLD);
        gc.fillText("THE LEDGER", x, y);
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(ledger.lines.size() + " of " + Ledger.CAPACITY, x + RIGHT_W - 40, y);
        y += 24;

        for (int i = 0; i < Ledger.CAPACITY; i++) {
            boolean filled = i < ledger.lines.size();
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(String.valueOf(i + 1), x, y + 14);

            if (filled) {
                String id = ledger.lines.get(i);
                boolean oldest = i == 0;
                gc.setFont(F_DETAIL);
                gc.setFill(oldest && ledger.lines.size() >= Ledger.CAPACITY ? Color.web("#D9A0A0") : INK);
                double ly = y + 14;
                for (String line : wrap(Ledger.words(id), F_DETAIL, RIGHT_W - 24)) {
                    gc.fillText(line, x + 22, ly);
                    ly += 24;
                }
                y = ly + 8;
            } else {
                gc.setFont(F_TINY);
                gc.setFill(Color.rgb(110, 110, 134, 0.5));
                gc.fillText("\u2014", x + 22, y + 14);
                y += 32;
            }
        }

        String out = ledger.nextOut();
        if (out != null) {
            y += 10;
            gc.setFont(F_TINY);
            gc.setFill(Color.rgb(217, 160, 160, 0.85));
            for (String line : wrap(Ledger.costWarning(out), F_TINY, RIGHT_W)) {
                gc.fillText(line, x, y);
                y += 17;
            }
        }
    }

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 170, 0.6));
        gc.fillText(text, M, H - 40);
    }

    // ------------------------------------------------------------ helpers

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
