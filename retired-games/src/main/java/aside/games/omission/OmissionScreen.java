package aside.games.omission;

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
 * omission, drawn.
 *
 * The list is on the left because it is the thing you are choosing from, and
 * the bag is on the right because it is the thing you are choosing for. Each
 * row carries the thing itself, the line that says whether you ever said it,
 * and what it was worth -- and the whole game is in the fact that the third
 * column and the second one disagree about which things are in danger.
 *
 * The report is the same list read back, with the assumption written underneath
 * every thing you lost. That is the one screen in the game that could not be
 * drawn any other way: the point is not that you lost something, it is that
 * what you have instead is specific, plausible, and wrong.
 */
public class OmissionScreen extends UiScreen {

    static final Color BG = Color.web("#0B0A08");
    static final Color IRON = Color.web("#131210");
    static final Color GOLD = Color.web("#E8B44A");
    static final Color INK = Color.web("#EDE9E0");
    static final Color DIM = Color.web("#A29C8E");
    static final Color FAINT = Color.web("#6E6A60");
    static final Color PAPER = Color.rgb(26, 24, 19, 0.9);
    static final Color EDGE = Color.web("#2E2B23");
    static final Color BAD = Color.web("#D9534F");
    static final Color GOOD = Color.web("#7FBF7F");
    static final Color SILENT = Color.web("#7FB2E5");

    static final Font F_TITLE = Font.font("Georgia", 44);
    static final Font F_SCENE = Font.font("Georgia", 17);
    static final Font F_BIG = Font.font("Georgia", 24);
    static final Font F_ROW = Font.font("Georgia", 15);
    static final Font F_ROW_DIM = Font.font("Georgia", 15);
    static final Font F_SMALL = Font.font("Arial", 14);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_MONO = Font.font("Consolas", 13);

    static final double M = 60;
    static final double COL_L = 60;
    static final double COL_LW = 760;
    static final double COL_R = 850;
    static final double COL_RW = 370;
    static final double ROW_TOP = 182;
    static final double ROW_PITCH = 36;

    enum Phase { OPEN, CHOOSE, REPORT }

    final Omission omission;
    final Path save;
    Phase phase = Phase.OPEN;
    int cursor = 0;
    String notice = "";
    double noticeTimer = 0;

    public OmissionScreen(UiManager ui, Omission omission, Path save) {
        super(ui);
        this.omission = omission;
        this.save = save;
        if (omission.left) phase = Phase.REPORT;
        else if (!omission.kept.isEmpty()) phase = Phase.CHOOSE;
    }

    @Override
    public void enter() {
        Audio a = Audio.A;
        if (a != null) a.stopAll();
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }
        if (phase == Phase.OPEN) {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.CHOOSE; sfx(); }
        } else if (phase == Phase.CHOOSE) {
            choose(c);
        } else {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                ui.replace(new OmissionScreen(ui, Omission.of(), save));
            }
        }
        e.consume();
    }

    /**
     * In or out of the bag, and then out of the house.
     *
     * Leaving is its own key rather than ENTER on an empty row, because ENTER
     * is already the key that puts a thing in the bag and a player who has just
     * filled the fifth slot should not discover that the same key now ends the
     * night.
     */
    void choose(KeyCode c) {
        int n = omission.list.size();
        if (c == KeyCode.UP) { cursor = (cursor + n - 1) % n; }
        else if (c == KeyCode.DOWN) { cursor = (cursor + 1) % n; }
        else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
            if (omission.kept.contains(cursor)) {
                omission.drop(cursor);
                sfx();
                persist();
            } else if (omission.canKeep(cursor)) {
                omission.keep(cursor);
                sfx();
                persist();
            } else {
                warn(Omission.FULL);
            }
        } else if (c == KeyCode.L) {
            if (omission.ready()) {
                omission.leave();
                persist();
                phase = Phase.REPORT;
                sfx();
            } else {
                warn(Omission.NOT_YET);
            }
        }
    }

    void warn(String text) {
        notice = text;
        noticeTimer = 2.5;
    }

    void persist() {
        try { omission.save(save); } catch (Exception ignored) { }
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
        gc.fillText(Omission.WORDMARK, M, 72);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = switch (phase) {
            case OPEN -> Omission.WHERE;
            case CHOOSE -> Omission.WHERE_LIST;
            case REPORT -> Omission.WHERE_REPORT;
        };
        gc.fillText(right, W - M - 120, 72);

        switch (phase) {
            case OPEN -> drawOpen();
            case CHOOSE -> drawChoose();
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
        for (String p : Omission.OPENING) {
            for (String line : wrap(p, F_SCENE, 620)) {
                gc.setFont(F_SCENE);
                gc.setFill(INK);
                gc.fillText(line, M, y);
                y += 26;
            }
            y += 12;
        }

        panel(COL_R, 122, COL_RW, 320);
        double x = COL_R + 26, ry = 152;
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        for (String line : wrap(Omission.RULES_HEADING, F_TINY, COL_RW - 52)) {
            gc.fillText(line, x, ry);
            ry += 16;
        }
        ry += 14;
        for (String[] rule : Omission.RULES) {
            if (!rule[0].isEmpty()) {
                gc.setFont(F_SMALL);
                gc.setFill(GOLD);
                gc.fillText(rule[0], x, ry);
                ry += 20;
            }
            for (String line : wrap(rule[1], F_TINY, COL_RW - 52)) {
                gc.setFont(F_TINY);
                gc.setFill(DIM);
                gc.fillText(line, x, ry);
                ry += 17;
            }
            ry += 14;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Omission.START_LINE, M, H - 74);
        hint("M mute    [ quieter    ] louder");
    }

    void drawChoose() {
        panel(COL_L, 110, COL_LW, 512);
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Omission.LIST_HEAD, COL_L + 26, 140);
        gc.fillText(Omission.WORTH_HEAD, COL_L + COL_LW - 26 - textWidth(Omission.WORTH_HEAD, F_TINY), 140);

        for (int i = 0; i < omission.list.size(); i++) {
            Omission.Detail d = omission.list.get(i);
            double y = ROW_TOP + i * ROW_PITCH;
            boolean inBag = omission.kept.contains(i);
            boolean here = i == cursor;

            if (here) {
                gc.setFill(PAPER);
                gc.fillRoundRect(COL_L + 14, y - 22, COL_LW - 28, ROW_PITCH - 4, 8, 8);
            }

            gc.setFont(F_ROW);
            gc.setFill(inBag ? GOLD : (here ? INK : DIM));
            gc.fillText(d.text(), COL_L + 30, y);

            gc.setFont(F_TINY);
            gc.setFill(d.told() ? FAINT : SILENT);
            gc.fillText(d.said(), COL_L + 30, y + 16);

            String w = Omission.worth(d.matters());
            gc.setFont(F_TINY);
            gc.setFill(inBag ? GOLD : FAINT);
            gc.fillText(w, COL_L + COL_LW - 26 - textWidth(w, F_TINY), y);

            if (inBag) {
                gc.setFont(F_TINY);
                gc.setFill(GOLD);
                gc.fillText(Omission.KEPT_TAG, COL_L + COL_LW - 26 - textWidth(Omission.KEPT_TAG, F_TINY), y + 16);
            }
        }

        // The bag.
        panel(COL_R, 110, COL_RW, 512);
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Omission.BAG_HEAD, COL_R + 26, 140);

        double ry = 176;
        if (omission.kept.isEmpty()) {
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Omission.NOTHING_YET, COL_R + 26, ry);
        } else {
            for (int i : omission.kept) {
                Omission.Detail d = omission.list.get(i);
                gc.setFont(F_MONO);
                gc.setFill(GOLD);
                gc.fillText("\u2022", COL_R + 26, ry);
                for (String line : wrap(d.text(), F_SMALL, COL_RW - 70)) {
                    gc.setFont(F_SMALL);
                    gc.setFill(INK);
                    gc.fillText(line, COL_R + 46, ry);
                    ry += 18;
                }
                ry += 10;
            }
        }

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(omission.slotsLeft() + " " + Omission.SLOTS_LEFT, COL_R + 26, 592);

        gc.setFont(F_SMALL);
        if (omission.ready()) {
            gc.setFill(GOLD);
            gc.fillText("[L] " + Omission.LEAVE_KEY, COL_L, H - 74);
        } else {
            gc.setFill(GOLD);
            gc.fillText("[ENTER] " + (omission.kept.contains(cursor) ? Omission.TAKE_BACK : Omission.TAKE),
                    COL_L, H - 74);
        }
        hint(Omission.HINT);
    }

    void drawReport() {
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Omission.REPORT_HEAD, COL_L + 26, 140);

        for (int i = 0; i < omission.list.size(); i++) {
            Omission.Detail d = omission.list.get(i);
            double y = ROW_TOP + i * ROW_PITCH;
            boolean ok = omission.right(i);
            boolean inBag = omission.kept.contains(i);

            gc.setFont(F_ROW_DIM);
            gc.setFill(ok ? DIM : INK);
            gc.fillText(d.text(), COL_L + 26, y);

            gc.setFont(F_TINY);
            if (!ok) {
                gc.setFill(BAD);
                gc.fillText(Omission.YOU_BELIEVE + " " + d.assumed(), COL_L + 26, y + 16);
            } else if (inBag) {
                gc.setFill(GOLD);
                gc.fillText(Omission.KEPT_TAG, COL_L + 26, y + 16);
            } else {
                gc.setFill(GOOD);
                gc.fillText(Omission.CAME_BACK_TAG, COL_L + 26, y + 16);
            }

            gc.setFont(F_TINY);
            gc.setFill(ok ? GOOD : BAD);
            String mark = ok ? Omission.RIGHT : Omission.WRONG;
            gc.fillText(mark, COL_L + COL_LW - 26 - textWidth(mark, F_TINY), y);
        }

        int score = omission.score(), best = omission.best();
        int count = omission.rightCount(), total = omission.total();

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Omission.SCORE_HEAD, COL_R, 140);

        gc.setFont(F_BIG);
        gc.setFill(score >= best ? GOOD : INK);
        gc.fillText(score + " / " + total, COL_R, 178);

        double ry = 206;
        for (String line : wrap(Omission.scoreLine(count, omission.list.size(), score, total),
                F_SMALL, COL_RW)) {
            gc.setFont(F_SMALL);
            gc.setFill(DIM);
            gc.fillText(line, COL_R, ry);
            ry += 19;
        }
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Omission.bestLine(best), COL_R, ry + 6);
        ry += 40;

        gc.setFont(F_BIG);
        gc.setFill(score >= best ? GOOD : BAD);
        for (String line : wrap(Omission.verdict(score, best, omission.heaviestWasted(),
                omission.heaviestLost()), F_BIG, COL_RW)) {
            gc.fillText(line, COL_R, ry);
            ry += 28;
        }

        ry += 12;
        for (String line : wrap(Omission.closing(score, best, omission.silentCount(),
                omission.wastedSlots()), F_SCENE, COL_RW)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, COL_R, ry);
            ry += 24;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Omission.AGAIN, COL_L, H - 74);
        hint(Omission.HINT_REPORT);
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

    static double textWidth(String s, Font f) {
        Text probe = new Text(s);
        probe.setFont(f);
        return probe.getLayoutBounds().getWidth();
    }

    /** Word wrap using real font metrics -- a Canvas has no measureText. */
    static List<String> wrap(String text, Font font, double maxWidth) {
        List<String> out = new ArrayList<>();
        Text probe = new Text();
        probe.setFont(font);
        for (String para : text.split("\\n", -1)) {
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
