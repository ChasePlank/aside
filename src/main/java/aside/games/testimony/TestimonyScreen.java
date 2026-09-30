package aside.games.testimony;

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
 * Testimony, drawn.
 *
 * The layout is doing one job: keeping the player's own account in front of
 * them while they keep answering. The left column is the interview. The
 * right column is what they remember. The right column is written in the
 * same flat declarative voice whether the line is true or not, because that
 * is exactly how a memory feels from the inside, and the whole game is the
 * gap between that feeling and the evening.
 *
 * Five beats:
 *
 *   WITNESS     the evening, once, and the warning that it is once
 *   INTERVIEW   a question, three answers, and the account growing beside it
 *   CONFIDENCE  how sure, which the game records and never uses to help you
 *   ACCOUNT     what you would say happened, read back as settled fact
 *   VERDICT     that, next to what happened, and the tally that matters
 *
 * The screen never marks an answer during the interview. It cannot: the
 * marking is the ending, and an ending that arrives early is not an ending.
 */
public class TestimonyScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 32);
    static final Font F_HEAD = Font.font("Georgia", 24);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_PROMPT = Font.font("Georgia", 23);
    static final Font F_OPTION = Font.font("Georgia", 20);
    static final Font F_LINE = Font.font("Georgia", 17);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_MONO = Font.font("Consolas", 14);

    static final double M = 84;
    static final double LEFT_W = 640;
    static final double RIGHT_X = 800;
    static final double RIGHT_W = 400;

    static final Color INK = Color.web("#E8E8EF");
    static final Color STEEL = Color.web("#8FB8DE");
    static final Color DIM = Color.web("#9A9AAE");
    static final Color FAINT = Color.web("#6E6E86");
    static final Color RED = Color.web("#E94560");
    static final Color GREEN = Color.web("#7FD1AE");
    static final Color AMBER = Color.web("#F2C14E");

    enum Phase { WITNESS, INTERVIEW, CONFIDENCE, ACCOUNT, VERDICT }

    final Testimony t;
    final Path save;

    Phase phase = Phase.WITNESS;
    String pending = null;        // the option chosen, awaiting a confidence
    String notice = "";
    double noticeTimer = 0;

    public TestimonyScreen(UiManager ui, Testimony t, Path save) {
        super(ui);
        this.t = t;
        this.save = save;
        // A testimony that is already complete has no evening left to show.
        // Without this the returning player lands on a witness screen for a
        // scene they have already been interviewed about.
        if (t.done()) phase = Phase.VERDICT;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case WITNESS -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) phase = Phase.INTERVIEW;
            }
            case INTERVIEW -> {
                if (c == KeyCode.DIGIT1 || c == KeyCode.NUMPAD1) choose(0);
                else if (c == KeyCode.DIGIT2 || c == KeyCode.NUMPAD2) choose(1);
                else if (c == KeyCode.DIGIT3 || c == KeyCode.NUMPAD3) choose(2);
            }
            case CONFIDENCE -> {
                if (c == KeyCode.S) commit(true);
                else if (c == KeyCode.N) commit(false);
            }
            case ACCOUNT -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) phase = Phase.VERDICT;
            }
            case VERDICT -> {
                if (c == KeyCode.R) restart();
                else if (c == KeyCode.ENTER || c == KeyCode.SPACE) ui.replace(new LibraryScreen(ui));
            }
        }
        e.consume();
    }

    void choose(int slot) {
        Testimony.Question q = t.current();
        if (q == null || slot >= q.options().size()) return;
        pending = q.options().get(slot).id();
        phase = Phase.CONFIDENCE;
    }

    void commit(boolean confident) {
        if (pending == null) return;
        if (!t.answer(pending, confident)) {
            notice = "that answer was not on the list";
            noticeTimer = 3;
            phase = Phase.INTERVIEW;
            pending = null;
            return;
        }
        pending = null;
        phase = t.done() ? Phase.ACCOUNT : Phase.INTERVIEW;
        persist();
    }

    void restart() {
        t.memory.clear();
        t.sure.clear();
        t.order.clear();
        t.finished = false;
        phase = Phase.WITNESS;
        pending = null;
        persist();
    }

    void persist() {
        try { t.save(save); } catch (Exception ignored) { }
    }

    // ----------------------------------------------------------------- tick

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        gc.setFill(Color.web("#0A0A12"));
        gc.fillRect(0, 0, W, H);

        switch (phase) {
            case WITNESS -> drawWitness();
            case INTERVIEW, CONFIDENCE -> drawInterview();
            case ACCOUNT -> drawAccount();
            case VERDICT -> drawVerdict();
        }
    }

    // -------------------------------------------------------------- witness

    void drawWitness() {
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("TESTIMONY", M, 66);

        gc.setFont(F_TITLE);
        gc.setFill(STEEL);
        gc.fillText(Testimony.SCENE_TITLE, M, 108);

        double y = 160;
        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(214, 214, 226, 0.92));
        for (String para : Testimony.SCENE.split("\n")) {
            if (para.isBlank()) { y += 16; continue; }
            for (String line : wrap(para, F_SCENE, W - M * 2 - 120)) {
                gc.fillText(line, M, y);
                y += 29;
            }
        }

        y += 18;
        gc.setFont(F_LINE);
        gc.setFill(AMBER);
        for (String line : wrap(Testimony.SCENE_NOTE, F_LINE, W - M * 2)) {
            gc.fillText(line, M, y);
            y += 24;
        }

        hint("ENTER  \u2014  and then it is gone");
    }

    // ------------------------------------------------------------ interview

    void drawInterview() {
        Testimony.Question q = t.current();
        if (q == null) return;

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("TESTIMONY", M, 66);
        gc.fillText("QUESTION " + (t.index() + 1) + " OF " + Testimony.QUESTIONS.size(), M, 86);

        double y = 140;
        gc.setFont(F_PROMPT);
        gc.setFill(INK);
        for (String line : wrap(t.promptFor(q), F_PROMPT, LEFT_W)) {
            gc.fillText(line, M, y);
            y += 32;
        }
        y += 26;

        for (int i = 0; i < q.options().size(); i++) {
            boolean chosen = pending != null && pending.equals(q.options().get(i).id());
            gc.setFont(F_MONO);
            gc.setFill(chosen ? STEEL : FAINT);
            gc.fillText(String.valueOf(i + 1), M, y);

            gc.setFont(F_OPTION);
            gc.setFill(chosen ? STEEL : Color.rgb(206, 206, 220, 0.9));
            for (String line : wrap(q.options().get(i).text(), F_OPTION, LEFT_W - 40)) {
                gc.fillText(line, M + 34, y);
                y += 28;
            }
            y += 20;
        }

        drawMemoryPanel();

        if (phase == Phase.CONFIDENCE) drawConfidenceBox();

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(RED);
            gc.fillText(notice, M, H - 66);
        }

        hint(phase == Phase.CONFIDENCE
                ? "S  sure        N  not sure"
                : "1  2  3  answer        ESC  leave");
    }

    /**
     * The right column: the account so far.
     *
     * Deliberately not labelled "answers". It is labelled what it is -- what
     * the player remembers -- and it is drawn without a single mark of
     * approval, because the game has none to give until the end.
     */
    void drawMemoryPanel() {
        double x = RIGHT_X;
        double y = 138;
        gc.setFill(Color.rgb(18, 20, 30, 0.85));
        gc.fillRoundRect(x - 26, y - 44, RIGHT_W + 52, 470, 12, 12);

        gc.setFont(F_MONO);
        gc.setFill(STEEL);
        gc.fillText(Testimony.MEMORY_HEAD, x, y);
        y += 26;

        List<String> sofar = t.accountSoFar();
        if (sofar.isEmpty()) {
            gc.setFont(F_TINY);
            gc.setFill(Color.rgb(110, 110, 134, 0.7));
            gc.fillText("\u00b7  " + Testimony.MEMORY_EMPTY, x, y + 14);
            return;
        }

        gc.setFont(F_MONO);
        for (String line : sofar) {
            gc.setFill(Color.rgb(150, 150, 170, 0.55));
            gc.fillText("\u00b7", x, y + 12);
            gc.setFont(F_LINE);
            gc.setFill(Color.rgb(222, 222, 234, 0.95));
            for (String w : wrap(line, F_LINE, RIGHT_W - 22)) {
                gc.fillText(w, x + 16, y + 12);
                y += 24;
            }
            gc.setFont(F_MONO);
            y += 14;
        }
    }

    void drawConfidenceBox() {
        double bw = 560, bh = 150;
        double bx = (W - bw) / 2, by = (H - bh) / 2 - 20;
        gc.setFill(Color.rgb(10, 10, 18, 0.93));
        gc.fillRoundRect(bx, by, bw, bh, 14, 14);
        gc.setStroke(Color.rgb(143, 184, 222, 0.5));
        gc.setLineWidth(1.4);
        gc.strokeRoundRect(bx, by, bw, bh, 14, 14);

        gc.setFont(F_HEAD);
        gc.setFill(INK);
        gc.fillText(Testimony.CONFIDENCE_HEAD, bx + 40, by + 58);

        gc.setFont(F_SMALL);
        gc.setFill(DIM);
        gc.fillText(Testimony.CONFIDENCE_NOTE, bx + 40, by + 84);

        gc.setFont(F_MONO);
        gc.setFill(GREEN);
        gc.fillText("S", bx + 40, by + 122);
        gc.setFont(F_LINE);
        gc.setFill(INK);
        gc.fillText("sure", bx + 62, by + 122);

        gc.setFont(F_MONO);
        gc.setFill(AMBER);
        gc.fillText("N", bx + 190, by + 122);
        gc.setFont(F_LINE);
        gc.setFill(INK);
        gc.fillText("not sure", bx + 212, by + 122);
    }

    // -------------------------------------------------------------- account

    void drawAccount() {
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("TESTIMONY", M, 66);

        gc.setFont(F_HEAD);
        gc.setFill(STEEL);
        gc.fillText(Testimony.ACCOUNT_HEAD, M, 108);

        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(200, 200, 215, 0.78));
        gc.fillText(Testimony.ACCOUNT_NOTE, M, 140);

        double y = 190;
        gc.setFont(F_LINE);
        gc.setFill(Color.rgb(226, 226, 238, 0.95));
        for (String line : t.account()) {
            for (String w : wrap(line, F_LINE, W - M * 2 - 40)) {
                gc.fillText(w, M + 24, y);
                y += 26;
            }
            y += 8;
        }

        hint("ENTER  \u2014  read what happened");
    }

    // -------------------------------------------------------------- verdict

    void drawVerdict() {
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("TESTIMONY", M, 60);

        gc.setFont(F_HEAD);
        gc.setFill(STEEL);
        gc.fillText(Testimony.VERDICT_HEAD, M, 96);

        double top = 140;
        double colW = 520;
        double rx = M + colW + 70;

        gc.setFont(F_SMALL);
        gc.setFill(STEEL);
        gc.fillText(Testimony.VERDICT_SAID, M, top);
        gc.setFill(Color.rgb(150, 150, 170, 0.85));
        gc.fillText(Testimony.VERDICT_HAPPENED, rx, top);

        double y = top + 30;
        for (String qid : t.order) {
            Testimony.Question q = Testimony.byId(qid);
            if (q == null) continue;
            boolean right = t.correct(qid);
            boolean wasSure = Boolean.TRUE.equals(t.sure.get(qid));
            String mine = q.phrase(t.memory.get(qid));
            String real = q.phrase(q.truth());

            double ly = y;
            gc.setFont(F_LINE);
            gc.setFill(right ? Color.rgb(190, 210, 200, 0.95) : Color.rgb(233, 160, 175, 0.98));
            for (String w : wrap(mine, F_LINE, colW)) { gc.fillText(w, M, ly); ly += 23; }

            double ry = y;
            gc.setFill(Color.rgb(200, 200, 215, 0.9));
            for (String w : wrap(real, F_LINE, colW)) { gc.fillText(w, rx, ry); ry += 23; }

            // The certainty mark sits in the gutter, where it can be read as
            // a column of its own: which of your wrong lines you were sure of.
            gc.setFont(F_MONO);
            if (!right) {
                gc.setFill(wasSure ? RED : AMBER);
                gc.fillText(wasSure ? "!!" : "?", M + colW + 16, y);
            }

            y = Math.max(ly, ry) + 10;
        }

        y += 12;
        gc.setFont(F_LINE);
        gc.setFill(DIM);
        gc.fillText(t.verdictTrue(), M, y);
        y += 26;
        gc.setFill(t.wrongSure() > 0 ? Color.rgb(233, 160, 175, 0.95) : Color.rgb(190, 210, 200, 0.95));
        gc.fillText(t.verdictTally(), M, y);

        y += 34;
        gc.setFont(F_SCENE);
        gc.setFill(Color.rgb(214, 214, 226, 0.92));
        for (String line : wrap(t.closing(), F_SCENE, W - M * 2)) {
            gc.fillText(line, M, y);
            y += 27;
        }

        hint("ENTER  back to the library     R  start again     ESC  leave");
    }

    // -------------------------------------------------------------- helpers

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 170, 0.6));
        gc.fillText(text, M, H - 40);
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
