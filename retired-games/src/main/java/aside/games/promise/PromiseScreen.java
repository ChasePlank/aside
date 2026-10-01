package aside.games.promise;

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
 * promise, drawn.
 *
 * Three screens and a report. The ask screen is one person at a time, because
 * that is how it happens -- nobody hands you a list of eight and asks you to
 * allocate; they come one at a time, and each one is easy to say yes to.
 *
 * The night screen is where the season turns. It shows the water first, then
 * what is due, then the arithmetic, and if the arithmetic does not fit it puts
 * a cursor on the promises and makes the player choose which one to break. That
 * choice is the only moment in the game that feels like a decision, and the
 * whole point is that it was made earlier -- on the night the player said yes.
 *
 * The panel on the right of the ask screen is the game's conscience: it shows
 * what is already promised and what night each one lands on, so a player who
 * looks can see the fourth night filling up while they are still on the third.
 * It is not hidden. It is just easy not to read.
 */
public class PromiseScreen extends UiScreen {

    static final Color BG = Color.web("#0B0C10");
    static final Color GOLD = Color.web("#E8B44A");
    static final Color INK = Color.web("#E9E9F2");
    static final Color DIM = Color.web("#9C9CB0");
    static final Color FAINT = Color.web("#6B6B82");
    static final Color PAPER = Color.rgb(26, 27, 38, 0.9);
    static final Color EDGE = Color.web("#2C2D3E");
    static final Color GOOD = Color.web("#6FCF97");
    static final Color BAD = Color.web("#E05A6B");
    static final Color WATER = Color.web("#6FA8CF");

    static final Font F_TITLE = Font.font("Georgia", 44);
    static final Font F_SUB = Font.font("Georgia", 20);
    static final Font F_WHO = Font.font("Georgia", 30);
    static final Font F_SCENE = Font.font("Georgia", 18);
    static final Font F_BIG = Font.font("Georgia", 30);
    static final Font F_SMALL = Font.font("Arial", 14);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_ROW = Font.font("Arial", 13);

    static final double M = 70;
    static final double PANEL_X = 760;
    static final double PANEL_W = 450;

    enum Phase { OPEN, ASK, NIGHT, REPORT }

    final Promise p;
    final Path save;
    Phase phase = Phase.OPEN;
    int sel = 0;
    String notice = "";
    double noticeTimer = 0;

    public PromiseScreen(UiManager ui, Promise p, Path save) {
        super(ui);
        this.p = p;
        this.save = save;
        if (p.reported) phase = Phase.REPORT;
        else if (p.night > 1 || p.askAt > 0) phase = p.asking() ? Phase.ASK : Phase.NIGHT;
    }

    @Override
    public void enter() {
        Audio a = Audio.A;
        if (a != null) a.stopAll();
    }

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        switch (phase) {
            case OPEN -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    phase = p.asking() ? Phase.ASK : Phase.NIGHT;
                    sfx("choice_select");
                }
            }
            case ASK -> ask(c);
            case NIGHT -> night(c);
            case REPORT -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) ui.pop();
                else if (c == KeyCode.R) again();
            }
        }
        e.consume();
    }

    void ask(KeyCode c) {
        if (c == KeyCode.Y) { p.answer(true); sfx("choice_select"); after(); }
        else if (c == KeyCode.N) { p.answer(false); sfx("text_blip"); after(); }
    }

    void after() {
        persist();
        phase = p.asking() ? Phase.ASK : Phase.NIGHT;
        sel = 0;
    }

    void night(KeyCode c) {
        List<Integer> due = p.dueTonight();
        if (p.over() > 0) {
            if (due.isEmpty()) return;
            if (c == KeyCode.UP || c == KeyCode.W) { sel = (sel + due.size() - 1) % due.size(); sfx("text_blip"); }
            else if (c == KeyCode.DOWN || c == KeyCode.S) { sel = (sel + 1) % due.size(); sfx("text_blip"); }
            else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                int i = due.get(Math.min(sel, due.size() - 1));
                p.breakIt(i);
                sfx("choice_select");
                notice = Promise.REQUESTS.get(i).who() + " was told yes, and not carried.";
                noticeTimer = 3.0;
                sel = 0;
                persist();
            }
            return;
        }
        if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
            p.finishNight();
            sfx("choice_select");
            persist();
            if (p.reported) phase = Phase.REPORT;
            else { phase = p.asking() ? Phase.ASK : Phase.NIGHT; sel = 0; }
        }
    }

    void again() {
        Promise fresh = new Promise();
        try { fresh.save(save); } catch (Exception ignored) { }
        ui.replace(new PromiseScreen(ui, fresh, save));
    }

    void persist() {
        try { p.save(save); } catch (Exception ignored) { }
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

        switch (phase) {
            case OPEN -> drawOpen();
            case ASK -> drawAsk();
            case NIGHT -> drawNight();
            case REPORT -> drawReport();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(BAD);
            gc.fillText(notice, M, H - 58);
        }
    }

    void drawOpen() {
        gc.setFill(GOLD);
        gc.setFont(F_TITLE);
        gc.fillText(Promise.OPEN_TITLE, M, 118);

        gc.setFont(F_SUB);
        gc.setFill(INK);
        for (String line : wrap(Promise.OPEN_SUB, F_SUB, 1080)) {
            gc.fillText(line, M, 156);
            break;
        }

        double y = 214;
        for (String para : Promise.OPEN_SITUATION) {
            for (String line : wrap(para, F_SCENE, 540)) {
                gc.setFont(F_SCENE);
                gc.setFill(DIM);
                gc.fillText(line, M, y);
                y += 25;
            }
            y += 16;
        }

        double rx = 700, ry = 214;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Promise.RULES_HEADING, rx, ry);
        ry += 30;
        for (String[] rule : Promise.RULES) {
            gc.setFont(F_SMALL);
            gc.setFill(GOLD);
            gc.fillText(rule[0], rx, ry);
            ry += 20;
            for (String line : wrap(rule[1], F_TINY, 500)) {
                gc.setFont(F_TINY);
                gc.setFill(DIM);
                gc.fillText(line, rx, ry);
                ry += 17;
            }
            ry += 18;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Promise.START_LINE, M, H - 74);
        hint("M mute    [ quieter    ] louder");
    }

    void drawAsk() {
        Promise.Ask a = p.current();

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Promise.nightLabel(p.night), M, 58);

        gc.setFont(F_WHO);
        gc.setFill(GOLD);
        gc.fillText(a.who(), M, 104);

        double y = 146;
        for (String line : wrap(a.what(), F_SCENE, 600)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 26;
        }

        y += 12;
        gc.setFont(F_SMALL);
        gc.setFill(WATER);
        gc.fillText(Promise.dueLine(a), M, y);

        y += 46;
        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Promise.SAY_YES, M, y);
        gc.setFill(DIM);
        gc.fillText(Promise.SAY_NO, M + 190, y);

        drawPromisePanel();
        hint(Promise.HINT_ASK);
    }

    /** What is already promised, and what night each one lands on. */
    void drawPromisePanel() {
        List<Integer> standing = new ArrayList<>();
        standing.addAll(p.dueTonight());
        standing.addAll(p.outstanding());

        // The panel is as tall as what is in it. A fixed box reads as a screen
        // with something missing from it on the first night, when the answer is
        // honestly "nothing yet".
        double panelH = Math.max(150, 74 + standing.size() * 26);

        gc.setFill(PAPER);
        gc.fillRoundRect(PANEL_X, 120, PANEL_W, panelH, 10, 10);
        gc.setStroke(EDGE);
        gc.setLineWidth(1);
        gc.strokeRoundRect(PANEL_X, 120, PANEL_W, panelH, 10, 10);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Promise.ASKS_HEAD, PANEL_X + 22, 152);

        double y = 186;
        if (standing.isEmpty()) {
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Promise.NOTHING_PROMISED, PANEL_X + 22, y);
        }
        for (int i : standing) {
            Promise.Ask a = Promise.REQUESTS.get(i);
            boolean tonight = a.due() == p.night;
            gc.setFont(F_ROW);
            gc.setFill(tonight ? GOLD : INK);
            gc.fillText(a.who(), PANEL_X + 22, y);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(Promise.costWord(a.cost()) + "  \u00b7  " + Promise.ordinal(a.due()), PANEL_X + 190, y);
            if (tonight) {
                gc.setFill(GOLD);
                gc.fillText("tonight", PANEL_X + 360, y);
            }
            y += 26;
        }
    }

    void drawNight() {
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Promise.nightLabel(p.night), M, 58);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Promise.WATER_HEAD, M, 96);

        double y = 128;
        for (String line : wrap(Promise.WATER[p.night].what(), F_SCENE, 620)) {
            gc.setFont(F_SCENE);
            gc.setFill(WATER);
            gc.fillText(line, M, y);
            y += 25;
        }
        gc.setFont(F_SMALL);
        gc.setFill(WATER);
        gc.fillText(Promise.costWord(Promise.WATER[p.night].cost()), M, y + 4);

        y += 52;
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Promise.DUE_HEAD, M, y);
        y += 30;

        List<Integer> due = p.dueTonight();
        boolean over = p.over() > 0;
        if (due.isEmpty()) {
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Promise.NOTHING_DUE, M, y);
            y += 30;
        }
        for (int k = 0; k < due.size(); k++) {
            int i = due.get(k);
            Promise.Ask a = Promise.REQUESTS.get(i);
            boolean on = over && k == Math.min(sel, due.size() - 1);
            gc.setFont(F_ROW);
            gc.setFill(on ? GOLD : INK);
            gc.fillText(on ? ">" : " ", M, y);
            gc.fillText(a.who(), M + 20, y);
            gc.setFill(DIM);
            gc.fillText(Promise.costWord(a.cost()), M + 220, y);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            // The panel starts at 760, so the row's prose has to stop short of
            // it rather than run under it.
            gc.fillText(clip(a.what(), F_TINY, PANEL_X - (M + 340) - 20), M + 340, y);
            y += 26;
        }

        y += 20;
        gc.setFont(F_BIG);
        gc.setFill(over ? BAD : GOOD);
        gc.fillText(Promise.loadLine(p.load()), M, y);
        gc.setFont(F_SMALL);
        gc.setFill(over ? BAD : GOOD);
        gc.fillText(over ? Promise.overLine(p.over()) : Promise.FITS_LINE, M + 240, y - 4);

        y += 44;
        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(over ? Promise.BREAK_KEY : Promise.GO_ON, M, y);

        drawPromisePanel();
        hint(over ? Promise.HINT_BREAK : Promise.HINT_NIGHT);
    }

    void drawReport() {
        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Promise.REPORT_HEAD, M, 58);

        gc.setFont(F_BIG);
        gc.setFill(p.score() >= Promise.MAX ? GOOD : GOLD);
        gc.fillText(Promise.worthLine(p.score()), M, 100);

        gc.setFont(F_SMALL);
        gc.setFill(p.breaks() == 0 ? GOOD : BAD);
        gc.fillText(Promise.breaksLine(p.breaks()), M, 128);

        gc.setFont(F_SMALL);
        gc.setFill(DIM);
        gc.fillText(Promise.KEPT_HEAD + " " + p.kept()
                + "    " + Promise.BROKEN_HEAD + " " + p.breaks()
                + "    " + Promise.DECLINED_HEAD + " " + p.declined(), M, 154);

        double y = 194;
        for (int i = 0; i < Promise.ASKS; i++) {
            Promise.Ask a = Promise.REQUESTS.get(i);
            boolean broken = p.broken[i];
            int ans = p.answer[i];
            gc.setFont(F_ROW);
            gc.setFill(FAINT);
            gc.fillText(Promise.ordinal(a.due()), M, y);
            gc.setFill(INK);
            gc.fillText(a.who(), M + 60, y);
            gc.setFill(DIM);
            gc.fillText(Promise.costWord(a.cost()), M + 220, y);
            gc.setFill(ans == 1 ? (broken ? BAD : GOOD) : FAINT);
            gc.fillText(Promise.rowWord(ans, broken), M + 340, y);
            gc.setFont(F_TINY);
            gc.setFill(FAINT);
            gc.fillText(clip(a.what(), F_TINY, 620), M + 430, y);
            y += 25;
        }

        y += 16;
        String close = (p.kept() == 0 && p.breaks() == 0)
                ? Promise.CLOSING_EMPTY
                : Promise.closing(p.score());
        for (String line : wrap(close, F_SCENE, 900)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 25;
        }

        hint(Promise.HINT_REPORT);
    }

    // ------------------------------------------------------------ helpers

    void hint(String text) {
        gc.setFont(F_SMALL);
        gc.setFill(Color.rgb(150, 150, 165, 0.6));
        gc.fillText(text, M, H - 36);
    }

    /** Trim to fit rather than wrap: a report row is one line. */
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
