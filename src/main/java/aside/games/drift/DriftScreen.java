package aside.games.drift;

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
 * drift, drawn.
 *
 * Two columns and a list of sixteen rows: what the line said when you left it,
 * and what it says now. The eye is supposed to go across, not down -- the whole
 * game is one comparison repeated sixteen times -- so the two texts sit on the
 * same baseline and the row is the unit, not the column.
 *
 * The list scrolls. Twelve rows fit and there are seventeen (sixteen lines and
 * the report), which is the same arithmetic the library needed on the same day:
 * a list whose length is not known when the geometry is written has to move
 * rather than shrink. See aside.ui.LibraryLayout for the longer version of that
 * argument.
 */
public class DriftScreen extends UiScreen {

    static final Color BG = Color.web("#0A0A12");
    static final Color GOLD = Color.web("#F2C14E");
    static final Color INK = Color.web("#E8E8F0");
    static final Color DIM = Color.web("#9A9AAE");
    static final Color FAINT = Color.web("#6E6E86");
    static final Color PAPER = Color.rgb(30, 30, 46, 0.85);
    static final Color EDGE = Color.web("#2A2A3A");
    static final Color FALSE_C = Color.web("#E94560");
    static final Color GOOD = Color.web("#6FCF97");

    static final Font F_TITLE = Font.font("Georgia", 44);
    static final Font F_SCENE = Font.font("Georgia", 17);
    static final Font F_BIG = Font.font("Georgia", 30);
    static final Font F_SMALL = Font.font("Arial", 14);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_ROW = Font.font("Arial", 13);

    static final double M = 70;
    static final double LIST_TOP = 158;
    static final double LIST_BOTTOM = 640;
    static final double ROW_H = 44;
    static final double COL_THEN = 110;
    static final double COL_NOW = 682;
    static final double COL_W = 540;

    enum Phase { OPEN, READ, REPORT }

    final Drift drift;
    final Path save;
    Phase phase = Phase.OPEN;
    int sel = 0;
    int scroll = 0;
    String notice = "";
    double noticeTimer = 0;

    /** Index == LINES means the report row. */
    int reportRow() { return Drift.LINES; }
    int rows() { return Drift.LINES + 1; }
    int visible() { return (int) Math.floor((LIST_BOTTOM - LIST_TOP) / ROW_H) + 1; }

    public DriftScreen(UiManager ui, Drift drift, Path save) {
        super(ui);
        this.drift = drift;
        this.save = save;
        if (drift.reported) phase = Phase.REPORT;
        else if (drift.markedCount() > 0) phase = Phase.READ;
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
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.READ; sfx("choice_select"); }
        } else if (phase == Phase.READ) {
            read(c);
        } else {
            if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                ui.replace(new DriftScreen(ui, Drift.of(), save));
            }
        }
        e.consume();
    }

    void read(KeyCode c) {
        if (c == KeyCode.UP) { sel = (sel - 1 + rows()) % rows(); follow(); }
        else if (c == KeyCode.DOWN) { sel = (sel + 1) % rows(); follow(); }
        else if (c == KeyCode.SPACE) {
            if (sel == reportRow()) { warn(Drift.NOT_HERE); return; }
            drift.toggle(sel);
            sfx("choice_select");
            persist();
        } else if (c == KeyCode.ENTER) {
            if (sel != reportRow()) { warn(Drift.NOT_HERE); return; }
            drift.reported = true;
            persist();
            phase = Phase.REPORT;
            sfx("choice_select");
        }
    }

    /**
     * Move the window so the selection is in it. Minimal scroll, the same rule
     * the library uses: the list moves only when the selection would otherwise
     * leave the window, because a list that re-centres on every keypress moves
     * under the reader's eye.
     */
    void follow() {
        int shown = Math.min(rows(), visible());
        if (rows() <= shown) { scroll = 0; return; }
        int start = 0;
        if (sel >= shown) start = sel - shown + 1;
        if (start > rows() - shown) start = rows() - shown;
        scroll = Math.max(0, start);
    }

    void warn(String text) {
        notice = text;
        noticeTimer = 2.5;
    }

    void persist() {
        try { drift.save(save); } catch (Exception ignored) { }
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
        gc.fillText(Drift.WORDMARK, M, 72);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        String right = switch (phase) {
            case OPEN -> Drift.WHERE_THEN;
            case READ -> drift.markedCount() + " marked";
            case REPORT -> Drift.WHERE_REPORT;
        };
        gc.fillText(right, W - M - 160, 72);

        switch (phase) {
            case OPEN -> drawOpen();
            case READ -> drawRead();
            case REPORT -> drawReport();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(FALSE_C);
            gc.fillText(notice, M, H - 64);
        }
    }

    void drawOpen() {
        double y = 140;
        for (String p : Drift.OPENING) {
            for (String line : wrap(p, F_SCENE, 560)) {
                gc.setFont(F_SCENE);
                gc.setFill(INK);
                gc.fillText(line, M, y);
                y += 26;
            }
            y += 12;
        }

        panel(660, 122, 550, 292);
        double x = 690, ry = 154;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Drift.RULES_HEADING, x, ry);
        ry += 30;
        for (String[] rule : Drift.RULES) {
            gc.setFont(F_SMALL);
            gc.setFill(GOLD);
            gc.fillText(rule[0], x, ry);
            ry += 20;
            for (String line : wrap(rule[1], F_TINY, 490)) {
                gc.setFont(F_TINY);
                gc.setFill(DIM);
                gc.fillText(line, x, ry);
                ry += 17;
            }
            ry += 18;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Drift.START_LINE, M, H - 74);
        hint("M mute    [ quieter    ] louder");
    }

    void drawRead() {
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Drift.THEN_HEAD, COL_THEN, 132);
        gc.fillText(Drift.NOW_HEAD, COL_NOW, 132);

        int shown = Math.min(rows(), visible());
        int start = scroll;
        for (int k = 0; k < shown; k++) {
            int i = start + k;
            double y = LIST_TOP + k * ROW_H;
            boolean selected = i == sel;
            boolean isReport = i == reportRow();
            boolean marked = !isReport && drift.marked[i];

            // A hairline before the first line of each night, so the four
            // nights are visible without costing the row any height.
            if (!isReport && Drift.LOG.get(i).night() > 1
                    && Drift.LOG.get(i - 1).night() != Drift.LOG.get(i).night()) {
                gc.setStroke(EDGE);
                gc.setLineWidth(1);
                gc.strokeLine(COL_THEN, y - ROW_H + 6, W - M, y - ROW_H + 6);
            }

            if (selected) {
                gc.setFill(PAPER);
                gc.fillRoundRect(56, y - 26, W - 112, ROW_H - 6, 8, 8);
            }
            if (marked) {
                gc.setFill(GOLD);
                gc.fillOval(62, y - 13, 9, 9);
            }

            if (isReport) {
                gc.setFont(F_SMALL);
                gc.setFill(selected ? GOLD : DIM);
                gc.fillText((selected ? ">  " : "   ") + Drift.REPORT_ROW
                        + "   (" + drift.markedCount() + " marked)", COL_THEN, y);
                continue;
            }

            gc.setFont(F_TINY);
            gc.setFill(marked ? GOLD : FAINT);
            gc.fillText(String.valueOf(i + 1), 82, y);

            gc.setFont(F_ROW);
            gc.setFill(selected ? INK : DIM);
            gc.fillText(clip(drift.rows.get(i).then(), F_ROW, COL_W), COL_THEN, y);
            gc.setFill(selected ? INK : DIM);
            gc.fillText(clip(drift.rows.get(i).now(), F_ROW, COL_W), COL_NOW, y);
        }

        if (rows() > shown) {
            double top = LIST_TOP - 22, bottom = LIST_BOTTOM + 12, x = W - 42;
            gc.setFill(Color.rgb(60, 60, 78, 0.55));
            gc.fillRoundRect(x, top, 4, bottom - top, 2, 2);
            double frac = (double) shown / rows();
            double thumbH = Math.max(24, (bottom - top) * frac);
            double pos = (double) start / (rows() - shown);
            gc.setFill(GOLD);
            gc.fillRoundRect(x, top + ((bottom - top) - thumbH) * pos, 4, thumbH, 2, 2);
        }

        hint(Drift.HINT_READ);
    }

    void drawReport() {
        int hits = drift.hits(), falseAlarms = drift.falseAlarms(), missed = drift.missed();

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Drift.REPORT_HEAD, M, 128);

        gc.setFont(F_BIG);
        gc.setFill(hits == Drift.CHANGED ? GOOD : GOLD);
        gc.fillText(Drift.foundLine(hits), M, 176);

        gc.setFont(F_SMALL);
        gc.setFill(DIM);
        double y = 210;
        gc.fillText(Drift.markedLine(drift.markedCount()), M, y); y += 24;
        gc.setFill(falseAlarms == 0 ? GOOD : FALSE_C);
        gc.fillText(Drift.falseLine(falseAlarms), M, y); y += 24;
        gc.setFill(missed == 0 ? GOOD : FALSE_C);
        gc.fillText(Drift.missedLine(missed), M, y); y += 24;
        gc.setFill(drift.score() > 0 ? INK : FALSE_C);
        gc.fillText(Drift.worthLine(drift.score()), M, y);

        y += 46;
        for (String line : wrap(Drift.closing(drift.score()), F_SCENE, 520)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 24;
        }

        double rx = 660, ry = 128;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Drift.MISSED_HEAD, rx, ry);
        ry += 26;
        if (missed == 0) {
            gc.setFont(F_TINY);
            gc.setFill(GOOD);
            gc.fillText(Drift.NOTHING_MISSED, rx, ry);
            ry += 22;
        }
        for (Drift.Row row : drift.rows) {
            if (row.kind() != Drift.Kind.CHANGED || drift.marked[row.index()]) continue;
            ry = pair(rx, ry, row.index(), row.then(), row.now(), FALSE_C);
        }

        ry += 18;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Drift.FALSE_HEAD, rx, ry);
        ry += 26;
        if (falseAlarms == 0) {
            gc.setFont(F_TINY);
            gc.setFill(GOOD);
            gc.fillText(Drift.NOTHING_FALSE, rx, ry);
        }
        for (Drift.Row row : drift.rows) {
            if (row.kind() == Drift.Kind.CHANGED || !drift.marked[row.index()]) continue;
            ry = pair(rx, ry, row.index(), row.then(), row.now(), DIM);
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Drift.AGAIN, M, H - 74);
        hint(Drift.HINT_REPORT);
    }

    /** One missed or wasted line, as "12  then  ->  now". */
    double pair(double x, double y, int index, String then, String now, Color nowColor) {
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(String.valueOf(index + 1), x, y);
        gc.setFill(DIM);
        gc.fillText(clip(then, F_TINY, 500), x + 26, y);
        y += 18;
        gc.setFill(nowColor);
        gc.fillText(clip(now, F_TINY, 500), x + 26, y);
        return y + 26;
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
