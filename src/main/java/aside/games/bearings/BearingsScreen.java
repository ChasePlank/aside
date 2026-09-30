package aside.games.bearings;

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
 * bearings, drawn.
 *
 * Two columns, and the split is the whole design. The left is the day: what
 * the weather is doing, and the two numbers the two clocks are offering for
 * it. The right is the ship's book: where it thinks you are, and -- the only
 * number on the screen that actually decides the voyage -- how long it has
 * been since anything on that side was checked against something outside it.
 *
 * The player's eye is supposed to keep going back to the right column while
 * the left column keeps offering a day's run, because that is the actual
 * experience of the game: the book is always available, always confident, and
 * always exactly as old as the last time you looked up.
 *
 * Four beats:
 *
 *   OPEN    the premise and the rules, once
 *   DAY     a day, and the two clocks' readings of it
 *   SIGHT   what the sky said, and what it cost
 *   END     where you called land, and where you were
 *
 * The one thing this screen deliberately never does is say how far off the
 * book is. It cannot: the only instrument that knows is the sky, and the sky
 * is on a schedule of its own.
 */
public class BearingsScreen extends UiScreen {

    static final Font F_TITLE = Font.font("Georgia", 30);
    static final Font F_HEAD = Font.font("Georgia", 24);
    static final Font F_SCENE = Font.font("Georgia", 19);
    static final Font F_ITEM = Font.font("Georgia", 19);
    static final Font F_SMALL = Font.font("Arial", 13);
    static final Font F_TINY = Font.font("Arial", 12);
    static final Font F_MONO = Font.font("Consolas", 16);
    static final Font F_MONO_S = Font.font("Consolas", 13);

    static final double M = 84;
    static final double LEFT_W = 700;
    static final double RIGHT_X = 830;
    static final double RIGHT_W = 366;

    static final Color BG = Color.web("#070B14");
    static final Color GOLD = Color.web("#F2C14E");
    static final Color INK = Color.web("#E8E8EF");
    static final Color DIM = Color.web("#9A9AAE");
    static final Color FAINT = Color.web("#6E6E86");
    static final Color RED = Color.web("#E94560");
    static final Color GREEN = Color.web("#7FD1AE");
    static final Color BLUE = Color.web("#8FB8DE");

    enum Phase { OPEN, DAY, SIGHT, END }

    final Bearings b;
    final Path save;

    Phase phase = Phase.OPEN;
    String notice = "";
    double noticeTimer = 0;

    public BearingsScreen(UiManager ui, Bearings b, Path save) {
        super(ui);
        this.b = b;
        this.save = save;
        // A finished voyage has no day to open on. Without this the player
        // who reaches the island and comes back lands on an empty sea.
        if (b.finished) phase = Phase.END;
        // And a voyage already under way opens on the day, not on the
        // premise again. Sixteen days is long enough that being told the
        // rules every time you come back would be its own punishment.
        else if (!b.days.isEmpty()) phase = Phase.DAY;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void handleKey(KeyEvent e) {
        KeyCode c = e.getCode();
        if (c == KeyCode.ESCAPE) { ui.replace(new LibraryScreen(ui)); e.consume(); return; }

        switch (phase) {
            case OPEN -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) { phase = Phase.DAY; sfx("choice_select"); }
            }
            case DAY -> day(c);
            case SIGHT -> {
                if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    phase = b.finished ? Phase.END : Phase.DAY;
                    sfx("choice_move");
                }
            }
            case END -> {
                if (c == KeyCode.R) {
                    reset();
                    phase = Phase.DAY;
                    sfx("choice_select");
                } else if (c == KeyCode.ENTER || c == KeyCode.SPACE) {
                    ui.replace(new LibraryScreen(ui));
                }
            }
        }
        e.consume();
    }

    void day(KeyCode c) {
        if (b.finished) { phase = Phase.END; return; }
        switch (c) {
            case DIGIT1, NUMPAD1 -> pick(Bearings.Pick.A);
            case DIGIT2, NUMPAD2 -> pick(Bearings.Pick.B);
            case DIGIT3, NUMPAD3 -> {
                if (b.canSight()) pick(Bearings.Pick.SIGHT);
                else { notice = "There is nothing to look at through cloud."; noticeTimer = 4; sfx("door_close"); }
            }
            default -> { }
        }
    }

    void pick(Bearings.Pick p) {
        if (!b.choose(p)) return;
        persist();
        if (p == Bearings.Pick.SIGHT) {
            phase = Phase.SIGHT;
            sfx("door_close");
        } else {
            phase = b.finished ? Phase.END : Phase.DAY;
            sfx("choice_move");
        }
    }

    void reset() {
        Bearings fresh = Bearings.fresh();
        b.seed = fresh.seed;
        b.clocks.clear();
        b.clocks.addAll(fresh.clocks);
        b.days.clear();
        b.day = fresh.day;
        b.weather = fresh.weather;
        b.todayRun = fresh.todayRun;
        b.trueRun = 0;
        b.book = 0;
        b.sightings = 0;
        b.lastSightDay = -1;
        b.lastCorrected = 0;
        b.everLooked = false;
        b.finished = false;
        b.found = false;
        b.finalTrue = 0;
        b.ending = "";
        persist();
    }

    void persist() {
        try { b.save(save); }
        catch (Exception ex) { notice = "the book could not be written: " + ex.getMessage(); noticeTimer = 6; }
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
        gc.fillText("bearings", M, 76);

        gc.setFont(F_TINY);
        gc.setFill(FAINT);
        gc.fillText(Bearings.dayLabel(b.day, phase == Phase.SIGHT, b.finished),
                W - M - 130, 76);

        switch (phase) {
            case OPEN -> drawOpen();
            case DAY -> drawDay();
            case SIGHT -> drawSight();
            case END -> drawEnd();
        }

        if (noticeTimer > 0) {
            gc.setFont(F_SMALL);
            gc.setFill(RED);
            gc.fillText(notice, M, H - 66);
        }
    }

    // ---------------------------------------------------------------- open

    void drawOpen() {
        double y = 150;
        for (String p : Bearings.OPENING) {
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
        gc.fillText(Bearings.START_LINE, M, H - 76);
    }

    void drawRulesPanel() {
        double x = RIGHT_X, y = 150;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Bearings.RULES_HEADING, x, y);
        y += 26;

        for (String[] r : Bearings.RULES) {
            if (!r[0].isEmpty()) {
                gc.setFont(F_MONO);
                gc.setFill(GOLD);
                gc.fillText(r[0], x, y);
            }
            for (String line : wrap(r[1], F_SMALL, RIGHT_W - 46)) {
                gc.setFont(F_SMALL);
                gc.setFill(DIM);
                gc.fillText(line, x + 46, y);
                y += 18;
            }
            y += 14;
        }
    }

    // ---------------------------------------------------------------- day

    void drawDay() {
        double y = 150;
        gc.setFont(F_SCENE);
        gc.setFill(INK);
        gc.fillText(Bearings.weatherLine(b.weather), M, y);

        y = 206;
        gc.setFont(F_SMALL);
        gc.setFill(DIM);
        gc.fillText(Bearings.WHAT_EACH_CLOCK, M, y);

        y = 252;
        option(y, "1", Bearings.CLOCK_A, b.estimate(0), 0);
        y += 46;
        option(y, "2", Bearings.CLOCK_B, b.estimate(1), 1);

        y += 62;
        boolean open = b.canSight();
        gc.setFont(F_MONO);
        gc.setFill(open ? GOLD : FAINT);
        gc.fillText("3", M, y);
        gc.setFont(F_ITEM);
        gc.setFill(open ? INK : FAINT);
        gc.fillText(Bearings.TAKE_SIGHT, 120, y);
        gc.setFont(F_SMALL);
        gc.setFill(open ? GREEN : FAINT);
        gc.fillText(open ? Bearings.SKY_OPEN : Bearings.SKY_CLOSED, 340, y);

        y += 60;
        for (String line : wrap(b.dayPrompt(), F_SMALL, LEFT_W)) {
            gc.setFont(F_SMALL);
            gc.setFill(DIM);
            gc.fillText(line, M, y);
            y += 19;
        }

        y += 16;
        for (String line : wrap(b.dayProse(), F_SCENE, LEFT_W)) {
            gc.setFont(F_SCENE);
            gc.setFill(Color.web("#B9B9CC"));
            gc.fillText(line, M, y);
            y += 26;
        }

        drawBookPanel();
        hint(Bearings.DAY_HINT);
    }

    void option(double y, String key, String name, double reading, int i) {
        gc.setFont(F_MONO);
        gc.setFill(GOLD);
        gc.fillText(key, M, y);
        gc.setFont(F_ITEM);
        gc.setFill(INK);
        gc.fillText(name, 120, y);
        gc.setFont(F_MONO);
        gc.setFill(BLUE);
        gc.fillText(Math.round(reading) + Bearings.MILES, 300, y);
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(ratingLabel(i), 520, y);
    }

    // The words live in Bearings. These are the screen's names for them,
    // so the phone build and this screen cannot drift apart.

    String ratingLabel(int i) {
        Bearings.Clock c = b.clocks.get(i);
        return Bearings.RATED_AT.formatted(Bearings.rateLabel(c),
                Bearings.ratedWhenLabel(c, b.day));
    }

    void drawBookPanel() {
        double x = RIGHT_X, y = 150;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Bearings.BOOK_HEAD, x, y);
        y += 28;

        gc.setFont(F_MONO);
        gc.setFill(INK);
        gc.fillText(String.valueOf(Math.round(b.book)), x, y);
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Bearings.OF_MILES, x + 74, y);
        y += 16;

        // The bar is the book's confidence, which is not the same thing as
        // the book's accuracy. It fills at exactly the same rate either way.
        gc.setFill(Color.web("#1B2233"));
        gc.fillRect(x, y, RIGHT_W, 5);
        gc.setFill(GOLD);
        gc.fillRect(x, y, RIGHT_W * b.progress(), 5);
        y += 34;

        row(x, y, Bearings.ROW_LAST_LOOKED,
                b.everLooked ? Bearings.ageLabel(b.day - b.lastSightDay) : Bearings.NEVER);
        y += 22;
        if (b.everLooked) {
            double off = b.lastCorrected;
            row(x, y, Bearings.ROW_AND_FOUND, Bearings.foundLine(off));
            y += 22;
        }
        row(x, y, Bearings.ROW_LOOKS_USED, String.valueOf(b.sightings));
        y += 22;
        row(x, y, Bearings.ROW_DAYS_LEFT, String.valueOf(Math.max(0, Bearings.DAYS - b.day)));
        y += 40;

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Bearings.CLOCKS_HEAD, x, y);
        y += 26;
        for (int i = 0; i < 2; i++) {
            Bearings.Clock c = b.clocks.get(i);
            gc.setFont(F_MONO_S);
            gc.setFill(BLUE);
            gc.fillText(c.name, x, y);
            gc.setFont(F_MONO_S);
            gc.setFill(INK);
            gc.fillText(Bearings.rateLabel(c), x + 34, y);
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(Bearings.ratedWhenLabel(c, b.day), x + 92, y);
            y += 24;
        }

        y += 10;
        for (String line : wrap(Bearings.RATE_NOTE, F_SMALL, RIGHT_W)) {
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(line, x, y);
            y += 18;
        }
    }

    void row(double x, double y, String label, String value) {
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(label, x, y);
        gc.setFont(F_MONO_S);
        gc.setFill(INK);
        gc.fillText(value, x + 110, y);
    }

    // ---------------------------------------------------------------- sight

    void drawSight() {
        double y = 150;
        gc.setFont(F_HEAD);
        gc.setFill(GOLD);
        gc.fillText(Bearings.SIGHT_HEAD, M, y);
        y += 56;

        for (String line : wrap(Bearings.sightWas(b.lastCorrected), F_SCENE, LEFT_W)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 26;
        }
        y += 18;

        for (int i = 0; i < 2; i++) {
            Bearings.Clock c = b.clocks.get(i);
            gc.setFont(F_MONO);
            gc.setFill(BLUE);
            gc.fillText(c.name, M, y);
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(Bearings.SIGHT_RERATED.formatted(c.name, Bearings.rateLabel(c)),
                    M + 40, y);
            y += 32;
        }

        y += 12;
        for (String line : wrap(Bearings.SIGHT_PROSE, F_SCENE, LEFT_W)) {
            gc.setFont(F_SCENE);
            gc.setFill(Color.web("#B9B9CC"));
            gc.fillText(line, M, y);
            y += 26;
        }

        drawBookPanel();
        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Bearings.GO_ON, M, H - 76);
    }

    // ---------------------------------------------------------------- end

    void drawEnd() {
        double y = 128;
        gc.setFont(F_HEAD);
        gc.setFill(b.found ? GREEN : RED);
        gc.fillText(b.headline(), M, y);

        y = 184;
        for (String line : wrap(b.verdict(), F_SCENE, W - 2 * M)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 26;
        }

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(Bearings.VOYAGE_HEAD, M, 288);
        gc.setFont(F_TINY);
        gc.fillText(Bearings.VOYAGE_COLS, M, 310);
        drawVoyage(328);

        double cy = 494;
        for (String line : wrap(b.closing(), F_SCENE, W - 2 * M)) {
            if (line.isEmpty()) { cy += 12; continue; }   // a paragraph break is not a line
            gc.setFont(F_SCENE);
            gc.setFill(Color.web("#B9B9CC"));
            gc.fillText(line, M, cy);
            cy += 23;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText(Bearings.AGAIN, M, H - 76);
    }

    void drawVoyage(double top) {
        List<Bearings.Day> ds = b.days;
        int rows = (ds.size() + 1) / 2;
        double colW = 560;
        for (int i = 0; i < ds.size(); i++) {
            Bearings.Day d = ds.get(i);
            int col = i / rows, row = i % rows;
            double x = M + col * colW, y = top + row * 20;
            double off = d.bookAfter - d.trueAfter;

            gc.setFont(F_MONO_S);
            gc.setFill(FAINT);
            gc.fillText(String.valueOf(d.index + 1), x, y);

            gc.setFill(DIM);
            gc.fillText(Bearings.weatherName(d.weather), x + 44, y);

            String wrote = d.pick == Bearings.Pick.SIGHT ? Bearings.LOOKED
                    : String.valueOf(Math.round(d.recorded));
            gc.setFill(d.pick == Bearings.Pick.SIGHT ? GOLD : INK);
            gc.fillText(wrote, x + 116, y);

            gc.setFill(FAINT);
            gc.fillText(String.valueOf(Math.round(d.actual)), x + 196, y);

            gc.setFill(Math.abs(off) <= Bearings.TOLERANCE ? DIM : RED);
            gc.fillText((off >= 0 ? "+" : "") + Math.round(off), x + 286, y);
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
