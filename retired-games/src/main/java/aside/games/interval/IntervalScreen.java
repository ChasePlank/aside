package aside.games.interval;

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
 * interval, drawn.
 *
 * Two columns and a wall. The record is on the right because it is the thing
 * you are allowed to look at, and the journal is on the left because it is the
 * thing you are making -- and the journal only has an entry for the days you
 * stood watch, so the left column fills in as a record of your own attention.
 * The days you slept are in it too, and they say "asleep", because a journal
 * that quietly omitted them would be a journal that flattered you.
 *
 * The list scrolls for the same reason the library does: twelve rows is more
 * than fits at a readable size, and a list whose length is known but whose
 * geometry is not has to move rather than shrink. See aside.ui.LibraryLayout
 * for the longer version of that argument.
 */
public class IntervalScreen extends UiScreen {

    static final Color BG = Color.web("#070B12");
    static final Color SEA = Color.web("#0D1520");
    static final Color GOLD = Color.web("#F2C14E");
    static final Color INK = Color.web("#E8E8F0");
    static final Color DIM = Color.web("#9A9AAE");
    static final Color FAINT = Color.web("#6E6E86");
    static final Color PAPER = Color.rgb(22, 28, 40, 0.85);
    static final Color EDGE = Color.web("#243040");
    static final Color BAD = Color.web("#E94560");
    static final Color GOOD = Color.web("#6FCF97");
    static final Color WATCH_C = Color.web("#7FB2E5");

    static final Font F_TITLE = Font.font("Georgia", 44);
    static final Font F_SCENE = Font.font("Georgia", 17);
    static final Font F_BIG = Font.font("Georgia", 26);
    static final Font F_SMALL = Font.font("Arial", 14);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_ROW = Font.font("Arial", 13);

    static final double M = 70;
    static final double LIST_TOP = 190;
    static final double ROW_H = 38;
    static final double LIST_BOTTOM = 620;
    static final double COL_L = 70;
    static final double COL_R = 660;
    static final double COL_W = 550;

    enum Phase { OPEN, PLAY, REPORT }

    final Interval interval;
    final Path save;
    Phase phase = Phase.OPEN;
    int scroll = 0;
    String notice = "";
    double noticeTimer = 0;

    public IntervalScreen(UiManager ui, Interval interval, Path save) {
        super(ui);
        this.interval = interval;
        this.save = save;
        if (interval.reported) phase = Phase.REPORT;
        else if (interval.decided > 0) phase = Phase.PLAY;
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
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.PLAY; sfx(); }
        } else if (phase == Phase.PLAY) {
            play(c);
        } else {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                ui.replace(new IntervalScreen(ui, Interval.of(), save));
            }
        }
        e.consume();
    }

    void play(KeyCode c) {
        int day = interval.decided;
        if (day >= Interval.DAYS) {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                interval.reported = true;
                persist();
                phase = Phase.REPORT;
                sfx();
            }
            return;
        }
        if (c == KeyCode.W) {
            if (interval.watchesUsed() >= Interval.WATCHES) { warn(Interval.NO_WATCHES_LEFT); return; }
            if (!interval.canWatch(day)) { warn(Interval.CANNOT_WATCH); return; }
            interval.watch(day);
            sfx();
            persist();
            follow();
        } else if (c == KeyCode.S) {
            interval.sleep(day);
            sfx();
            persist();
            follow();
        }
    }

    void follow() {
        int shown = visible();
        if (interval.decided > shown) scroll = interval.decided - shown;
        else scroll = 0;
    }

    void warn(String text) {
        notice = text;
        noticeTimer = 2.5;
    }

    void persist() {
        try { interval.save(save); } catch (Exception ignored) { }
    }

    static void sfx() {
        if (Audio.A != null) Audio.A.sfx("choice_select", "choice_select");
    }

    int visible() { return (int) Math.floor((LIST_BOTTOM - LIST_TOP) / ROW_H) + 1; }

    @Override
    public void tick(double dt) {
        if (noticeTimer > 0) noticeTimer -= dt;
        draw();
    }

    // ---------------------------------------------------------------- draw

    void draw() {
        gc.setFill(BG);
        gc.fillRect(0, 0, W, H);
        // A horizon, because the whole game is one.
        gc.setFill(SEA);
        gc.fillRect(0, 632, W, H - 632);
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeLine(0, 632, W, 632);

        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(Interval.WORDMARK, M, 72);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = switch (phase) {
            case OPEN -> Interval.WHERE;
            case PLAY -> "day " + Math.min(interval.decided + 1, Interval.DAYS) + " of " + Interval.DAYS;
            case REPORT -> Interval.WHERE_REPORT;
        };
        gc.fillText(right, W - M - 160, 72);

        switch (phase) {
            case OPEN -> drawOpen();
            case PLAY -> drawPlay();
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
        for (String p : Interval.OPENING) {
            for (String line : wrap(p, F_SCENE, 560)) {
                gc.setFont(F_SCENE);
                gc.setFill(INK);
                gc.fillText(line, M, y);
                y += 26;
            }
            y += 12;
        }

        panel(COL_R, 122, COL_W, 292);
        double x = COL_R + 30, ry = 154;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Interval.RULES_HEADING, x, ry);
        ry += 30;
        for (String[] rule : Interval.RULES) {
            if (!rule[0].isEmpty()) {
                gc.setFont(F_SMALL);
                gc.setFill(GOLD);
                gc.fillText(rule[0], x, ry);
                ry += 20;
            }
            for (String line : wrap(rule[1], F_TINY, 490)) {
                gc.setFont(F_TINY);
                gc.setFill(DIM);
                gc.fillText(line, x, ry);
                ry += 17;
            }
            ry += 14;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Interval.START_LINE, M, H - 74);
        hint("M mute    [ quieter    ] louder");
    }

    void drawPlay() {
        // The wall, on the right: the only thing you are allowed to know.
        panel(COL_R, 122, COL_W, 300);
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Interval.RECORD_HEAD, COL_R + 30, 154);
        double ry = 186;
        for (int i = 0; i < interval.record.size(); i++) {
            int out = interval.record.get(i);
            gc.setFont(F_ROW);
            gc.setFill(DIM);
            gc.fillText(Interval.NAMES.get(i), COL_R + 30, ry);
            gc.setFill(out == 0 ? FAINT : GOLD);
            gc.fillText(Interval.recordLine(out), COL_R + 250, ry);
            ry += 20;
        }

        // The journal, on the left: what you have made of the twelve days.
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Interval.JOURNAL_HEAD, COL_L, 154);

        int shown = Math.min(Interval.DAYS, visible());
        for (int k = 0; k < shown; k++) {
            int day = scroll + k;
            if (day >= Interval.DAYS) break;
            double y = LIST_TOP + k * ROW_H;
            boolean decided = day < interval.decided;
            boolean watched = interval.watched[day];
            boolean current = day == interval.decided;

            if (current) {
                gc.setFill(PAPER);
                gc.fillRoundRect(COL_L - 14, y - 24, COL_W + 28, ROW_H - 4, 8, 8);
            }

            gc.setFont(F_TINY);
            gc.setFill(decided ? (watched ? WATCH_C : FAINT) : (current ? GOLD : FAINT));
            gc.fillText(String.valueOf(day + 1), COL_L, y);

            gc.setFont(F_ROW);
            if (!decided) {
                gc.setFill(current ? GOLD : FAINT);
                gc.fillText(current ? "tonight" : "\u2014", COL_L + 26, y);
            } else if (watched) {
                gc.setFill(INK);
                gc.fillText(clip(Interval.LOG.get(day), F_ROW, COL_W - 40), COL_L + 26, y);
            } else {
                gc.setFill(FAINT);
                gc.fillText(Interval.JOURNAL_BLANK, COL_L + 26, y);
            }
        }

        if (Interval.DAYS > shown) {
            double top = LIST_TOP - 22, bottom = LIST_BOTTOM + 12, x = COL_L + COL_W + 6;
            gc.setFill(Color.rgb(60, 60, 78, 0.55));
            gc.fillRoundRect(x, top, 4, bottom - top, 2, 2);
            double frac = (double) shown / Interval.DAYS;
            double thumbH = Math.max(24, (bottom - top) * frac);
            double pos = (double) scroll / (Interval.DAYS - shown);
            gc.setFill(GOLD);
            gc.fillRoundRect(x, top + ((bottom - top) - thumbH) * pos, 4, thumbH, 2, 2);
        }

        // The choice.
        int day = interval.decided;
        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        if (day >= Interval.DAYS) {
            gc.fillText("The season is over. ENTER to read it back.", COL_L, H - 74);
        } else {
            boolean canWatch = interval.canWatch(day);
            gc.setFill(canWatch ? GOLD : FAINT);
            gc.fillText("[W] " + Interval.WATCH, COL_L, H - 74);
            gc.setFill(GOLD);
            gc.fillText("[S] " + Interval.SLEEP, COL_L + 150, H - 74);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(interval.watchesUsed() + " of " + Interval.WATCHES + " watches stood",
                    COL_L + 300, H - 74);
        }
        hint(Interval.HINT);
    }

    void drawReport() {
        Interval.End end = interval.end();
        int wasted = interval.wasted();

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Interval.REPORT_HEAD, M, 128);

        gc.setFont(F_BIG);
        gc.setFill(end == Interval.End.CAUGHT ? GOOD : (end == Interval.End.MISSED ? BAD : GOLD));
        double y = 176;
        for (String line : wrap(Interval.outcomeLine(end, interval.arrival), F_BIG, COL_W)) {
            gc.fillText(line, M, y);
            y += 32;
        }

        y += 10;
        gc.setFont(F_SMALL);
        gc.setFill(DIM);
        gc.fillText(Interval.watchedLine(interval.watchesUsed()), M, y); y += 24;
        gc.setFill(wasted == 0 ? GOOD : DIM);
        gc.fillText(Interval.wastedLine(end, wasted), M, y); y += 24;

        y += 22;
        for (String line : wrap(Interval.closing(end, wasted), F_SCENE, COL_W)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 24;
        }

        // The journal, read back.
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Interval.JOURNAL_HEAD, COL_R, 128);
        double ry = 158;
        for (int day = 0; day < Interval.DAYS; day++) {
            boolean watched = interval.watched[day];
            gc.setFont(F_TINY);
            gc.setFill(watched ? WATCH_C : FAINT);
            gc.fillText(String.valueOf(day + 1), COL_R, ry);
            gc.setFont(F_TINY);
            gc.setFill(watched ? DIM : FAINT);
            gc.fillText(watched ? clip(Interval.LOG.get(day), F_TINY, COL_W - 40)
                    : Interval.JOURNAL_BLANK, COL_R + 26, ry);
            ry += 19;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Interval.AGAIN, M, H - 74);
        hint(Interval.HINT_REPORT);
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

    /** Trim to fit rather than wrap: a row is one line, and a wrapped row is two. */
    static String clip(String text, Font font, double maxWidth) {
        Text probe = new Text();
        probe.setFont(font);
        probe.setText(text);
        if (probe.getLayoutBounds().getWidth() <= maxWidth) return text;
        String s = text;
        while (s.length() > 4 && probe.getLayoutBounds().getWidth() > maxWidth) {
            s = s.substring(0, s.length() - 2);
            probe.setText(s + "...");
        }
        return s + "...";
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
