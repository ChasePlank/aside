package aside.games.lesson;

import aside.ui.Audio;
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
 * lesson, drawn.
 *
 * Three columns of information and one of them is a person. The board is on the
 * left because it is the thing you are choosing from; tonight is on the right
 * because it is the thing you are choosing for; and the new keeper is under it
 * because what they believe is the consequence of the other two and should be
 * read after them.
 *
 * The keeper's panel is the one that matters. It says what they think, and
 * underneath it, in the same panel, it lists every rule still standing -- so a
 * player who has spent a night teaching a corner they had already taught can
 * see that they have, without having to work it out. The game is about choosing
 * the demonstration; it is not about guessing what the student made of it.
 */
public class LessonScreen extends UiScreen {

    static final Color BG = Color.web("#0A0906");
    static final Color IRON = Color.web("#12110D");
    static final Color GOLD = Color.web("#E8B44A");
    static final Color INK = Color.web("#EDE9E0");
    static final Color DIM = Color.web("#A29C8E");
    static final Color FAINT = Color.web("#6E6A60");
    static final Color PAPER = Color.rgb(24, 22, 17, 0.88);
    static final Color EDGE = Color.web("#2C2A22");
    static final Color BAD = Color.web("#D9534F");
    static final Color GOOD = Color.web("#7FBF7F");
    static final Color SHOWN = Color.web("#7FB2E5");

    static final Font F_TITLE = Font.font("Georgia", 44);
    static final Font F_SCENE = Font.font("Georgia", 17);
    static final Font F_BIG = Font.font("Georgia", 25);
    static final Font F_SMALL = Font.font("Arial", 14);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_ROW = Font.font("Consolas", 14);
    static final Font F_MONO = Font.font("Consolas", 13);

    static final double M = 70;
    static final double COL_L = 70;
    static final double COL_LW = 620;
    static final double COL_R = 730;
    static final double COL_RW = 480;
    static final double BOARD_TOP = 186;
    static final double BOARD_PITCH = 33;

    enum Phase { OPEN, TEACH, REPORT }

    final Lesson lesson;
    final Path save;
    Phase phase = Phase.OPEN;
    int cursor = 0;
    String notice = "";
    double noticeTimer = 0;

    public LessonScreen(UiManager ui, Lesson lesson, Path save) {
        super(ui);
        this.lesson = lesson;
        this.save = save;
        if (lesson.handedOver) phase = Phase.REPORT;
        else if (!lesson.shown.isEmpty()) phase = Phase.TEACH;
    }

    @Override
    public void enter() {
        Audio a = Audio.A;
        if (a != null) a.stopAll();
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (phase == Phase.OPEN) {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.TEACH; sfx(); }
        } else if (phase == Phase.TEACH) {
            teach(c);
        } else {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                ui.replace(new LessonScreen(ui, Lesson.of(), save));
            }
        }
        e.consume();
    }

    void teach(KeyCode c) {
        if (lesson.ready()) {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                lesson.handedOver = true;
                persist();
                phase = Phase.REPORT;
                sfx();
            }
            return;
        }
        if (c == KeyCode.UP) { cursor = (cursor + lesson.board.size() - 1) % lesson.board.size(); }
        else if (c == KeyCode.DOWN) { cursor = (cursor + 1) % lesson.board.size(); }
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
            if (lesson.shown.contains(cursor)) { warn(Lesson.ALREADY_SHOWN); return; }
            if (!lesson.canShow(cursor)) { warn(Lesson.NO_NIGHTS); return; }
            lesson.show(cursor);
            sfx();
            persist();
            advanceCursor();
        }
    }

    /** Move the cursor off a state that has been shown, so ENTER is never a no-op. */
    void advanceCursor() {
        for (int i = 1; i <= lesson.board.size(); i++) {
            int j = (cursor + i) % lesson.board.size();
            if (!lesson.shown.contains(j)) { cursor = j; return; }
        }
    }

    void warn(String text) {
        notice = text;
        noticeTimer = 2.5;
    }

    void persist() {
        try { lesson.save(save); } catch (Exception ignored) { }
    }

    static void sfx() {
        if (Audio.A != null) Audio.A.sfx("choice_select", "choice_select");
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
        gc.setFill(IRON);
        gc.fillRect(0, 640, W, H - 640);
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(0, 640, W, 640);

        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(Lesson.WORDMARK, M, 72);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = switch (phase) {
            case OPEN -> Lesson.WHERE;
            case TEACH -> Lesson.NIGHT + " " + Math.min(lesson.shown.size() + 1, Lesson.SHOWS)
                    + " " + Lesson.OF + " " + Lesson.SHOWS;
            case REPORT -> Lesson.WHERE_REPORT;
        };
        gc.fillText(right, W - M - 150, 72);

        switch (phase) {
            case OPEN -> drawOpen();
            case TEACH -> drawTeach();
            case REPORT -> drawReport();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(BAD);
            gc.fillText(notice, M, H - 64);
        }
    }

    void drawOpen() {
        double y = 140;
        for (String p : Lesson.OPENING) {
            for (String line : wrap(p, F_SCENE, 560)) {
                gc.setFont(F_SCENE);
                gc.setFill(INK);
                gc.fillText(line, M, y);
                y += 26;
            }
            y += 12;
        }

        panel(COL_R, 122, COL_RW, 300);
        double x = COL_R + 30, ry = 154;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Lesson.RULES_HEADING, x, ry);
        ry += 30;
        for (String[] rule : Lesson.RULES) {
            if (!rule[0].isEmpty()) {
                gc.setFont(F_SMALL);
                gc.setFill(GOLD);
                gc.fillText(rule[0], x, ry);
                ry += 20;
            }
            for (String line : wrap(rule[1], F_TINY, 420)) {
                gc.setFont(F_TINY);
                gc.setFill(DIM);
                gc.fillText(line, x, ry);
                ry += 17;
            }
            ry += 14;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Lesson.START_LINE, M, H - 74);
        hint("M mute    [ quieter    ] louder");
    }

    void drawTeach() {
        // The board: what you are choosing from.
        panel(COL_L, 110, COL_LW, 490);
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Lesson.BOARD_HEAD, COL_L + 30, 142);
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText("up: " + Lesson.PRESSURE + " " + Lesson.PRESSURE_UP + "+   "
                + Lesson.HEAT + " " + Lesson.HEAT_UP + "+", COL_L + 200, 142);

        for (int i = 0; i < lesson.board.size(); i++) {
            double y = BOARD_TOP + i * BOARD_PITCH;
            boolean shown = lesson.shown.contains(i);
            boolean here = i == cursor && !lesson.ready();

            if (here) {
                gc.setFill(PAPER);
                gc.fillRoundRect(COL_L + 18, y - 22, COL_LW - 36, BOARD_PITCH - 5, 8, 8);
            }
            gc.setFont(F_MONO);
            gc.setFill(shown ? SHOWN : (here ? GOLD : FAINT));
            gc.fillText(String.format("%02d", i + 1), COL_L + 34, y);

            Lesson.Reading rd = lesson.board.get(i);
            gc.setFont(F_ROW);
            gc.setFill(shown ? DIM : INK);
            gc.fillText(Lesson.PRESSURE, COL_L + 80, y);
            gc.setFill(shown ? DIM : INK);
            gc.fillText(String.valueOf(rd.pressure()), COL_L + 190, y);
            gc.fillText(Lesson.HEAT, COL_L + 250, y);
            gc.fillText(String.valueOf(rd.heat()), COL_L + 330, y);

            if (shown) {
                gc.setFont(F_TINY);
                gc.setFill(SHOWN);
                gc.fillText(Lesson.SHOWN, COL_L + 420, y);
            }
        }

        // Tonight: what you are choosing for.
        panel(COL_R, 110, COL_RW, 168);
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Lesson.TONIGHT_HEAD, COL_R + 30, 142);
        for (int i = 0; i < lesson.shift.size(); i++) {
            Lesson.Reading rd = lesson.shift.get(i);
            double y = 180 + i * 30;
            gc.setFont(F_ROW);
            gc.setFill(INK);
            gc.fillText(Lesson.readingLine(rd), COL_R + 30, y);
        }

        // The new keeper: the consequence.
        panel(COL_R, 298, COL_RW, 302);
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Lesson.KEEPER_HEAD, COL_R + 30, 330);

        List<Lesson.Rule> s = new ArrayList<>(lesson.standing());
        String said = lesson.shown.isEmpty() ? Lesson.WATCHING
                : (lesson.lastRedundant ? Lesson.NOTHING_NEW : Lesson.belief(s));
        double y = 366;
        for (String line : wrap(said, F_SCENE, COL_RW - 60)) {
            gc.setFont(F_SCENE);
            gc.setFill(lesson.lastRedundant ? DIM : INK);
            gc.fillText(line, COL_R + 30, y);
            y += 24;
        }

        y += 10;
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Lesson.confidence(s.size()), COL_R + 30, y);
        y += 22;
        for (int i = 0; i < s.size() && i < 4; i++) {
            gc.setFont(F_TINY);
            gc.setFill(DIM);
            gc.fillText("\u2014 when " + Lesson.whenOf(s.get(i)), COL_R + 30, y);
            y += 18;
        }
        if (s.size() > 4) {
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText("and " + Lesson.word(s.size() - 4) + " more", COL_R + 30, y);
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        if (lesson.ready()) {
            gc.fillText(Lesson.HAND_OVER, COL_L, H - 74);
        } else {
            gc.fillText("[ENTER] " + Lesson.SHOW, COL_L, H - 74);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(lesson.nightsLeft() + " " + Lesson.NIGHTS_LEFT, COL_L + 300, H - 74);
        }
        hint(Lesson.HINT);
    }

    void drawReport() {
        boolean safe = lesson.safe();
        boolean certain = lesson.certain();
        List<Lesson.Quadrant> corners = safe ? lesson.unsure() : lesson.wrongCorners();

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Lesson.REPORT_HEAD, COL_L, 128);

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Lesson.SHIFT_HEAD, COL_L, 162);
        double y = 196;
        for (Lesson.Reading rd : lesson.shift) {
            boolean acts = lesson.actsOn(rd);
            boolean right = acts == Lesson.opens(lesson.rule, rd);
            gc.setFont(F_ROW);
            gc.setFill(INK);
            gc.fillText(Lesson.actionLine(rd, acts), COL_L, y);
            gc.setFont(F_TINY);
            gc.setFill(right ? GOOD : BAD);
            gc.fillText(right ? Lesson.RIGHT : Lesson.WRONG, COL_L + 430, y);
            if (!right) {
                gc.setFill(FAINT);
                gc.fillText(Lesson.shouldLine(Lesson.opens(lesson.rule, rd)), COL_L + 490, y);
            }
            y += 30;
        }

        y += 20;
        gc.setFont(F_BIG);
        gc.setFill(safe ? GOOD : BAD);
        for (String line : wrap(Lesson.verdict(safe, certain, corners), F_BIG, COL_LW)) {
            gc.fillText(line, COL_L, y);
            y += 30;
        }

        y += 14;
        for (String line : wrap(Lesson.closing(safe, certain, lesson.lastRedundant),
                F_SCENE, COL_LW)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, COL_L, y);
            y += 24;
        }

        // What they believed, and the board, read back.
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Lesson.BELIEF_HEAD, COL_R, 128);
        double ry = 160;
        List<Lesson.Rule> s = new ArrayList<>(lesson.standing());
        String said = lesson.shown.isEmpty() ? Lesson.NOTHING_SHOWN : Lesson.belief(s);
        for (String line : wrap(said, F_SMALL, COL_RW)) {
            gc.setFont(F_SMALL);
            gc.setFill(DIM);
            gc.fillText(line, COL_R, ry);
            ry += 20;
        }
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Lesson.confidence(s.size()), COL_R, ry + 4);

        ry += 40;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Lesson.BOARD_HEAD, COL_R, ry);
        ry += 26;
        for (int i = 0; i < lesson.board.size(); i++) {
            Lesson.Reading rd = lesson.board.get(i);
            boolean shown = lesson.shown.contains(i);
            gc.setFont(F_MONO);
            gc.setFill(shown ? SHOWN : FAINT);
            gc.fillText(String.format("%02d", i + 1), COL_R, ry);
            gc.setFont(F_MONO);
            gc.setFill(shown ? DIM : FAINT);
            gc.fillText(Lesson.readingLine(rd), COL_R + 40, ry);
            if (shown) {
                gc.setFont(F_TINY);
                gc.setFill(SHOWN);
                gc.fillText(Lesson.SHOWN, COL_R + 300, ry);
            }
            ry += 19;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Lesson.AGAIN, COL_L, H - 74);
        hint(Lesson.HINT_REPORT);
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
        gc.setFill(Color.rgb(150, 145, 135, 0.6));
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
