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
        // After a sighting the day has already turned over, and the screen is
        // still showing the day the look was taken on.
        int shown = phase == Phase.SIGHT ? b.day : b.day + 1;
        String right = b.finished
                ? "the voyage is over"
                : "day " + Math.min(shown, Bearings.DAYS) + " of " + Bearings.DAYS;
        gc.fillText(right, W - M - 130, 76);

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
        for (String p : new String[]{
                "You are the navigator, and the island is " + (int) Bearings.NEEDED
                        + " miles east of you, and there is nothing between you and it "
                        + "but open water and two chronometers.",
                "Every day you write the day's run into the ship's book. The book is what you steer by. "
                        + "You cannot write down where you are -- only how far you believe you have run -- "
                        + "and the two clocks will not agree about it.",
                "Both were rated in port. A rate does not stay rated. It moves at night, and nothing says so.",
                "The only instrument on board that reports the truth is the sky. It has to be clear, and "
                        + "looking costs you most of the day."}) {
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
        gc.fillText("ENTER to begin.", M, H - 76);
    }

    void drawRulesPanel() {
        double x = RIGHT_X, y = 150;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("HOW IT GOES", x, y);
        y += 26;

        String[][] rules = {
            {"1 / 2", "Write the day's run into the book from Clock A or Clock B. You will steer by what you write."},
            {"3", "Heave to and take a sighting. Clear days only. Puts the book back on the sea and re-rates both clocks. Costs most of the day."},
            {"", "When the book reaches " + (int) Bearings.NEEDED + " you call for land. Whether you are there is not up to the book."},
            {"", "When the two clocks disagree, one of them has moved. When they agree, you have learned nothing."},
        };
        for (String[] r : rules) {
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
        gc.fillText(weatherLine(), M, y);

        y = 206;
        gc.setFont(F_SMALL);
        gc.setFill(DIM);
        gc.fillText("What each clock would have you write:", M, y);

        y = 252;
        option(y, "1", "Clock A", b.estimate(0), 0);
        y += 46;
        option(y, "2", "Clock B", b.estimate(1), 1);

        y += 62;
        boolean open = b.canSight();
        gc.setFont(F_MONO);
        gc.setFill(open ? GOLD : FAINT);
        gc.fillText("3", M, y);
        gc.setFont(F_ITEM);
        gc.setFill(open ? INK : FAINT);
        gc.fillText(open ? "Take a sighting" : "Take a sighting", 120, y);
        gc.setFont(F_SMALL);
        gc.setFill(open ? GREEN : FAINT);
        gc.fillText(open ? "the sky is open -- this is the only honest reading on board"
                        : "the sky is closed", 340, y);

        y += 60;
        for (String line : wrap(dayPrompt(), F_SMALL, LEFT_W)) {
            gc.setFont(F_SMALL);
            gc.setFill(DIM);
            gc.fillText(line, M, y);
            y += 19;
        }

        y += 16;
        for (String line : wrap(dayProse(), F_SCENE, LEFT_W)) {
            gc.setFont(F_SCENE);
            gc.setFill(Color.web("#B9B9CC"));
            gc.fillText(line, M, y);
            y += 26;
        }

        drawBookPanel();
        hint("1 / 2 to write the run, 3 to look. ESC for the library.");
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
        gc.fillText(Math.round(reading) + " miles", 300, y);
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText(ratingLabel(i), 520, y);
    }

    String ratingLabel(int i) {
        Bearings.Clock c = b.clocks.get(i);
        String rate = (c.rated > 0 ? "+" : "") + Math.round(c.rated);
        return "rated " + rate + ", " + ageLabel(c.ageOn(b.day));
    }

    static String ageLabel(int days) {
        if (days <= 0) return "today";
        if (days == 1) return "yesterday";
        return days + " days ago";
    }

    String weatherLine() {
        return switch (b.weather) {
            case FAIR -> "Fair wind, and the sea is working with you.";
            case CLEAR -> "Clear. Nothing between the glass and the sun.";
            case FOUL -> "Foul. Grey from rail to rail.";
        };
    }

    String dayProse() {
        String[] pool = switch (b.weather) {
            case FAIR -> new String[]{
                "The wind holds all day and the sea runs with you. Nobody on board has anything to say about the clocks.",
                "Good going, and the log line straight behind. The two brass faces sit side by side in their box and neither of them is talking.",
                "A steady day. You write the run in the book, and the book gets a little further from the sea.",
                "The ship works well. The clocks work. Nothing about the day tells you anything you did not already believe.",
            };
            case CLEAR -> new String[]{
                "Not a cloud. The horizon is a ruled line and the sun is where the almanac says it should be, which is the only honest thing on board.",
                "Clear from rail to rail. You could have the truth off the glass in an hour, and you would lose the afternoon's run doing it.",
                "The sky is open. Everything you have written in the book is checkable today, and none of it has been checked.",
            };
            case FOUL -> new String[]{
                "Grey from rail to rail and the sea coming over the bow. You make what you can and you write it down.",
                "No sun, no stars, no horizon. The book is the only world there is today.",
                "Bad weather. The clocks are in their box, and the box is the only thing on board that claims to know anything.",
            };
        };
        return pool[(int) Math.floor(b.rand(90) * pool.length) % pool.length];
    }

    String dayPrompt() {
        if (!b.everLooked) {
            return "You have not looked at anything outside this ship yet. Everything on the right is what you "
                    + "wrote down, and none of it has been checked.";
        }
        if (b.day - b.lastSightDay >= 5) {
            return "It has been " + ageLabel(b.day - b.lastSightDay) + " since anything on the right was checked "
                    + "against something that was not a clock.";
        }
        return "Write one of these into the book. The book is what you steer by, and it will not be checked today.";
    }

    void drawBookPanel() {
        double x = RIGHT_X, y = 150;
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("THE SHIP'S BOOK", x, y);
        y += 28;

        gc.setFont(F_MONO);
        gc.setFill(INK);
        gc.fillText(String.valueOf(Math.round(b.book)), x, y);
        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("of " + (int) Bearings.NEEDED + " miles", x + 74, y);
        y += 16;

        // The bar is the book's confidence, which is not the same thing as
        // the book's accuracy. It fills at exactly the same rate either way.
        gc.setFill(Color.web("#1B2233"));
        gc.fillRect(x, y, RIGHT_W, 5);
        gc.setFill(GOLD);
        gc.fillRect(x, y, RIGHT_W * b.progress(), 5);
        y += 34;

        row(x, y, "last looked", b.everLooked ? ageLabel(b.day - b.lastSightDay) : "never");
        y += 22;
        if (b.everLooked) {
            double off = b.lastCorrected;
            String dir = off > 0 ? "ahead of the sea" : "behind the sea";
            row(x, y, "and found", Math.round(Math.abs(off)) + " miles " + dir);
            y += 22;
        }
        row(x, y, "looks used", String.valueOf(b.sightings));
        y += 22;
        row(x, y, "days left", String.valueOf(Math.max(0, Bearings.DAYS - b.day)));
        y += 40;

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("THE CLOCKS", x, y);
        y += 26;
        for (int i = 0; i < 2; i++) {
            Bearings.Clock c = b.clocks.get(i);
            gc.setFont(F_MONO_S);
            gc.setFill(BLUE);
            gc.fillText(c.name, x, y);
            gc.setFont(F_MONO_S);
            gc.setFill(INK);
            gc.fillText((c.rated > 0 ? "+" : "") + Math.round(c.rated), x + 34, y);
            gc.setFont(F_SMALL);
            gc.setFill(FAINT);
            gc.fillText(ageLabel(c.ageOn(b.day)), x + 92, y);
            y += 24;
        }

        y += 10;
        for (String line : wrap("A rate is only as good as the day it was written. The clocks do not tell you when they have moved.",
                F_SMALL, RIGHT_W)) {
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
        gc.fillText("You have the sun.", M, y);
        y += 56;

        double off = b.lastCorrected;
        String was = off > 0
                ? "Your book was " + Math.round(Math.abs(off)) + " miles ahead of the sea."
                : (off < 0
                    ? "Your book was " + Math.round(Math.abs(off)) + " miles behind the sea."
                    : "Your book was exactly on the sea. It has happened once before, to somebody else.");

        for (String line : wrap(was, F_SCENE, LEFT_W)) {
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
            gc.fillText("is running " + (c.rated > 0 ? "+" : "") + Math.round(c.rated)
                    + " miles a day, and the book now says so.", M + 40, y);
            y += 32;
        }

        y += 12;
        for (String line : wrap(sightProse(), F_SCENE, LEFT_W)) {
            gc.setFont(F_SCENE);
            gc.setFill(Color.web("#B9B9CC"));
            gc.fillText(line, M, y);
            y += 26;
        }

        drawBookPanel();
        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText("ENTER to go on.", M, H - 76);
    }

    String sightProse() {
        return "The correction is written into the book and both clocks are re-rated, and you have spent most "
                + "of the day doing it. What you know now is true today. Nothing about it says anything about "
                + "tomorrow, and tonight the rates will move again.";
    }

    // ---------------------------------------------------------------- end

    void drawEnd() {
        double y = 128;
        gc.setFont(F_HEAD);
        gc.setFill(b.found ? GREEN : RED);
        gc.fillText(headline(), M, y);

        y = 184;
        for (String line : wrap(verdict(), F_SCENE, W - 2 * M)) {
            gc.setFont(F_SCENE);
            gc.setFill(INK);
            gc.fillText(line, M, y);
            y += 26;
        }

        gc.setFont(F_SMALL);
        gc.setFill(FAINT);
        gc.fillText("THE VOYAGE", M, 288);
        gc.setFont(F_TINY);
        gc.fillText("day     sky      wrote      had      off by", M, 310);
        drawVoyage(328);

        double cy = 494;
        for (String line : wrap(closing(), F_SCENE, W - 2 * M)) {
            if (line.isEmpty()) { cy += 12; continue; }   // a paragraph break is not a line
            gc.setFont(F_SCENE);
            gc.setFill(Color.web("#B9B9CC"));
            gc.fillText(line, M, cy);
            cy += 23;
        }

        gc.setFont(F_SMALL);
        gc.setFill(GOLD);
        gc.fillText("R to sail it again.  ENTER for the library.", M, H - 76);
    }

    String headline() {
        return switch (b.ending) {
            case "found" -> "You raise the island at dawn.";
            case "short" -> "You call for land, and there is no land.";
            case "past" -> "You call for land, and you are already past it.";
            default -> "The season turns before you get there.";
        };
    }

    String verdict() {
        long here = Math.round(b.finalTrue);
        long called = Math.round(b.book);
        long off = Math.round(Math.abs(b.miss()));
        return switch (b.ending) {
            case "found" -> "You called for land at " + called + " miles by the book, and you were at "
                    + here + ". The book was " + off + " miles out, and " + off + " miles is inside the "
                    + "error a landfall can absorb.";
            case "short" -> "You called for land at " + called + " miles by the book. You were at " + here
                    + " -- " + off + " miles short, with nothing on the horizon in any direction and no "
                    + "way to know which way to beat.";
            case "past" -> "You called for land at " + called + " miles by the book. You were at " + here
                    + " -- " + off + " miles beyond the island, which is now somewhere behind you in a "
                    + "great deal of water.";
            default -> "The book never reached " + (long) Bearings.NEEDED + ". The season turned, and you "
                    + "put the helm over and went back. You were at " + here + " miles, which is " + off
                    + " miles short of where you needed to be.";
        };
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

            String wrote = d.pick == Bearings.Pick.SIGHT ? "looked" : String.valueOf(Math.round(d.recorded));
            gc.setFill(d.pick == Bearings.Pick.SIGHT ? GOLD : INK);
            gc.fillText(wrote, x + 116, y);

            gc.setFill(FAINT);
            gc.fillText(String.valueOf(Math.round(d.actual)), x + 196, y);

            gc.setFill(Math.abs(off) <= Bearings.TOLERANCE ? DIM : RED);
            gc.fillText((off >= 0 ? "+" : "") + Math.round(off), x + 286, y);
        }
    }

    String closing() {
        int looks = b.sightings;
        String first = switch (Math.min(looks, 3)) {
            case 0 -> "You never looked. The book was a perfectly consistent account of a voyage that was "
                    + "happening somewhere else, and there was nothing in it that could have told you so.";
            case 1 -> "You looked once. One honest reading in sixteen days, and the whole voyage after it "
                    + "rested on a rate that was true on the afternoon you took it.";
            case 2 -> "You looked twice. Two afternoons of truth in sixteen days, and between them the book "
                    + "ran on its own, which is what a book does.";
            default -> "You looked " + looks + " times, and every one of them cost you the day you would "
                    + "otherwise have spent getting there.";
        };
        return first + "\n\n"
                + "Two clocks that agree have agreed about nothing. They share a box, a temperature and a "
                + "knock, and when the same cause moves both of them they will sit side by side in perfect "
                + "agreement and both be wrong by the same amount. The only instrument on board that cannot "
                + "share their mistakes is the sky, and the sky is only open on days you would rather be "
                + "sailing.";
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
