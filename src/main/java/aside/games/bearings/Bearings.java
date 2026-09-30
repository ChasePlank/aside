package aside.games.bearings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * bearings -- the model.
 *
 * Two chronometers, sixteen days, one island, and no way to check anything
 * except by spending a whole day looking at the sky.
 *
 * The rule the game is built on: a clock's reading is the true run plus its
 * drift, and the drift changes at night without telling you. So the ship's
 * book is only ever as good as the last time you looked, and *how long ago
 * that was* is the number the whole voyage turns on.
 *
 * Nothing here is drawn. The separation is the point: the rule can be tested
 * without a screen, which is what SelfTest does.
 */
public final class Bearings {

    // ---- the voyage -------------------------------------------------------

    public static final int DAYS = 16;
    public static final double NEEDED = 820;
    public static final double TOLERANCE = 55;

    /**
     * What a look costs, as a fraction of the day's run.
     *
     * Not the whole day. You heave to, take the sun, and get some of the
     * afternoon back -- but a clear sky is also a light-air day, so the day
     * you can see is the day you would most have wanted to run. The two
     * costs together are what stop "look whenever you can" from working.
     */
    public static final double SIGHT_COST = 0.75;

    /**
     * The rule's other knobs, named.
     *
     * These were literals inside the methods that use them, which was fine
     * while there was one build. There are two now, and the phone build has to
     * be given the same numbers -- so they are constants, they are emitted into
     * the phone build, and a change to one of them without regenerating fails
     * a check instead of quietly making two different games. The template used
     * to hold its own copies of all nine of these.
     */
    public static final double CLEAR_BELOW = 0.25;
    public static final double FAIR_BELOW = 0.85;
    public static final int SKY_OPENS_FROM_DAY = 4;
    public static final int SKY_OPENS_AFTER = 6;
    public static final double SHARED_CAUSE_BELOW = 0.10;
    public static final double A_MOVES_BELOW = 0.22;
    public static final double B_MOVES_BELOW = 0.22;
    public static final double LOST_PENALTY_CAP = 0.45;
    public static final double LOST_PENALTY_DIVISOR = 250.0;

    public enum Weather { FAIR, CLEAR, FOUL }
    public enum Pick { A, B, SIGHT }

    public static double run(Weather w) {
        return switch (w) {
            case FAIR -> 68;
            case CLEAR -> 50;
            case FOUL -> 32;
        };
    }

    public static String weatherName(Weather w) {
        return switch (w) {
            case FAIR -> "fair";
            case CLEAR -> "clear";
            case FOUL -> "foul";
        };
    }

    // ---- the clocks -------------------------------------------------------

    /**
     * A chronometer.
     *
     * {@code drift} is the truth and never leaves this class. {@code rated}
     * is what the ship's book says the rate is, and it is only ever as good
     * as the day it was written.
     */
    public static final class Clock {
        public final String name;
        public double drift;
        public double rated;
        public int ratedOn = -1;        // -1 means rated in port, before day one

        Clock(String name) { this.name = name; }

        /** Days since this clock was last rated, counting the day in progress. */
        public int ageOn(int day) { return ratedOn < 0 ? day + 1 : day - ratedOn; }
    }

    /** One day, kept so the end screen can show the whole voyage at once. */
    public static final class Day {
        public int index;
        public Weather weather;
        public Pick pick;
        public double recorded;         // what went into the book
        public double actual;           // what the sea actually gave
        public double bookAfter;
        public double trueAfter;
        public double clockA;           // A's reading of the day's run
        public double clockB;
    }

    // ---- state ------------------------------------------------------------

    public long seed;
    public final List<Clock> clocks = new ArrayList<>();
    public final List<Day> days = new ArrayList<>();

    public int day;                     // index of the day in progress
    public Weather weather;
    public double todayRun;             // true distance the day will give
    public double trueRun;              // where the ship actually is
    public double book;                 // where the ship's book says it is

    public int sightings;
    public int lastSightDay = -1;
    public double lastCorrected;        // the error found at the last sighting
    public boolean everLooked;

    public boolean finished;
    public boolean found;
    public double finalTrue;
    public String ending = "";

    int clearDays;
    int lastClearDay = -1;

    // ---- construction -----------------------------------------------------

    public Bearings() { this(System.nanoTime()); }

    public Bearings(long seed) {
        this.seed = seed;
        clocks.add(new Clock("A"));
        clocks.add(new Clock("B"));
        // Both chronometers are rated before the ship leaves port. That is
        // the only free look the player ever gets, and it is exactly why the
        // first days of the voyage feel safe.
        clocks.get(0).drift = newDrift(11);
        clocks.get(1).drift = newDrift(12);
        for (Clock c : clocks) c.rated = c.drift;
        startDay();
    }

    // ---- the day ----------------------------------------------------------

    void startDay() {
        weather = rollWeather();
        // Being lost costs you way, not just accuracy: a navigator steering
        // for a landfall that is not where the book says it is makes less
        // ground than one who knows. This is the only place the error bites
        // before the last day, and it is what makes looking early worth
        // anything at all.
        double penalty = Math.min(LOST_PENALTY_CAP, Math.abs(error()) / LOST_PENALTY_DIVISOR);
        todayRun = run(weather) * (1 - penalty);
    }

    public double error() { return book - trueRun; }

    /** What the given clock says the day's run was. */
    public double reading(int i) { return todayRun + clocks.get(i).drift; }

    /** What the player would write, correcting by that clock's rating. */
    public double estimate(int i) { return reading(i) - clocks.get(i).rated; }

    public boolean canSight() { return !finished && weather == Weather.CLEAR; }

    public double progress() { return Math.min(1.0, book / NEEDED); }

    public double miss() { return finalTrue - NEEDED; }

    /**
     * Play the day. Returns false if that choice is not available.
     *
     * A sighting is not a reading. It is the only thing in the game that
     * reports the truth, and it costs the whole day: no way is made at all.
     */
    public boolean choose(Pick p) {
        if (finished) return false;
        if (p == Pick.SIGHT && !canSight()) return false;

        Day d = new Day();
        d.index = day;
        d.weather = weather;
        d.pick = p;
        d.clockA = reading(0);
        d.clockB = reading(1);

        if (p == Pick.SIGHT) {
            lastCorrected = error();
            lastSightDay = day;
            everLooked = true;
            sightings++;
            double kept = todayRun * (1 - SIGHT_COST);
            trueRun += kept;
            book = trueRun;                 // the error is gone, not hidden
            for (Clock c : clocks) { c.rated = c.drift; c.ratedOn = day; }
            d.recorded = kept;
            d.actual = kept;
        } else {
            Clock c = clocks.get(p == Pick.A ? 0 : 1);
            double recorded = todayRun + c.drift - c.rated;
            trueRun += todayRun;
            book += recorded;
            d.recorded = recorded;
            d.actual = todayRun;
        }

        d.bookAfter = book;
        d.trueAfter = trueRun;
        days.add(d);

        night();
        settle();
        return true;
    }

    void night() {
        day++;
        if (day < DAYS) rollDrift();
    }

    void settle() {
        if (book >= NEEDED) { finish(true); return; }
        if (day >= DAYS) { finish(false); return; }
        startDay();
    }

    void finish(boolean calledLand) {
        finished = true;
        finalTrue = trueRun;
        if (calledLand) {
            double m = finalTrue - NEEDED;
            found = Math.abs(m) <= TOLERANCE;
            ending = found ? "found" : (m < 0 ? "short" : "past");
        } else {
            found = false;
            ending = "season";
        }
    }

    // ---- the weather ------------------------------------------------------

    Weather rollWeather() {
        double r = rand(1);
        Weather w;
        if (r < CLEAR_BELOW) w = Weather.CLEAR;
        else if (r < FAIR_BELOW) w = Weather.FAIR;
        else w = Weather.FOUL;

        // A voyage with no clear day is not a game, and a navigator who has
        // not been able to look for a week is being punished by the weather
        // rather than by their own choices. The sky opens on its own
        // schedule, and this is that schedule.
        if (w != Weather.CLEAR && day >= SKY_OPENS_FROM_DAY) {
            if (clearDays == 0) w = Weather.CLEAR;
            else if (day - lastClearDay >= SKY_OPENS_AFTER) w = Weather.CLEAR;
        }
        if (w == Weather.CLEAR) { clearDays++; lastClearDay = day; }
        return w;
    }

    // ---- the drift --------------------------------------------------------

    static final double[] DRIFT_VALUE = { 0, 7, -7, 18, -18 };
    static final double[] DRIFT_WEIGHT = { 0.32, 0.20, 0.20, 0.14, 0.14 };

    double newDrift(int salt) {
        double r = rand(salt), acc = 0;
        for (int i = 0; i < DRIFT_VALUE.length; i++) {
            acc += DRIFT_WEIGHT[i];
            if (r < acc) return DRIFT_VALUE[i];
        }
        return 0;
    }

    /**
     * What happens at night, and the reason the game is not solvable.
     *
     * A shared cause fires first: the same heat, the same knock, the same
     * bad night in a wooden box. When it does, both clocks take the same new
     * rate -- so they agree, and their agreement is the one thing a
     * navigator cannot use.
     */
    void rollDrift() {
        if (rand(2) < SHARED_CAUSE_BELOW) {
            double d = newDrift(7);
            for (Clock c : clocks) c.drift = d;
            return;
        }
        if (rand(3) < A_MOVES_BELOW) clocks.get(0).drift = newDrift(5);
        if (rand(4) < B_MOVES_BELOW) clocks.get(1).drift = newDrift(6);
    }

    // ---- determinism ------------------------------------------------------

    /**
     * A counter-based draw, not a stream.
     *
     * Every random number is a pure function of (seed, day, salt), which
     * means a voyage can be replayed exactly from its seed and a save file
     * does not have to carry an RNG position.
     */
    double rand(int salt) {
        long h = seed + 0x9E3779B97F4A7C15L * (day + 1) + 0xBF58476D1CE4E5B9L * salt;
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27); h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }

    // ---- the words --------------------------------------------------------
    //
    // Every fixed line the game says lives here, not in the screen.
    //
    // Not tidiness: the phone build reads these strings, and a second copy
    // of the prose in a template is a second copy of the game quietly
    // disagreeing with the first. The screen draws them; the generator
    // emits them; neither owns them.

    public static final String WORDMARK = "bearings";
    public static final String WHERE = "the eastern passage";
    /** %s is the day number, %s the number of days. */
    public static final String DAY_OF = "day %s of %s";
    public static final String VOYAGE_OVER = "the voyage is over";

    public static final String[] OPENING = {
        "You are the navigator, and the island is " + (int) NEEDED
                + " miles east of you, and there is nothing between you and it "
                + "but open water and two chronometers.",
        "Every day you write the day's run into the ship's book. The book is what you steer by. "
                + "You cannot write down where you are -- only how far you believe you have run -- "
                + "and the two clocks will not agree about it.",
        "Both were rated in port. A rate does not stay rated. It moves at night, and nothing says so.",
        "The only instrument on board that reports the truth is the sky. It has to be clear, and "
                + "looking costs you most of the day.",
    };

    public static final String RULES_HEADING = "HOW IT GOES";

    public static final String[][] RULES = {
        {"1 / 2", "Write the day's run into the book from Clock A or Clock B. You will steer by what you write."},
        {"3", "Heave to and take a sighting. Clear days only. Puts the book back on the sea and re-rates both clocks. Costs most of the day."},
        {"", "When the book reaches " + (int) NEEDED + " you call for land. Whether you are there is not up to the book."},
        {"", "When the two clocks disagree, one of them has moved. When they agree, you have learned nothing."},
    };

    public static final String START_LINE = "ENTER to begin.";
    public static final String GO_ON = "ENTER to go on.";
    public static final String AGAIN = "R to sail it again.  ENTER for the library.";
    // The phone has buttons, not keys, so it needs the verb without the key.
    // Stripping "ENTER to " off the desktop line would have left the end
    // screen offering "for the library" on a button that sails again.
    public static final String BTN_BEGIN = "Begin";
    public static final String BTN_GO_ON = "Go on";
    public static final String BTN_AGAIN = "Sail it again";
    public static final String DAY_HINT = "1 / 2 to write the run, 3 to look. ESC for the library.";

    public static final String WEATHER_FAIR = "Fair wind, and the sea is working with you.";
    public static final String WEATHER_CLEAR = "Clear. Nothing between the glass and the sun.";
    public static final String WEATHER_FOUL = "Foul. Grey from rail to rail.";

    public static final String[] DAY_FAIR = {
        "The wind holds all day and the sea runs with you. Nobody on board has anything to say about the clocks.",
        "Good going, and the log line straight behind. The two brass faces sit side by side in their box and neither of them is talking.",
        "A steady day. You write the run in the book, and the book gets a little further from the sea.",
        "The ship works well. The clocks work. Nothing about the day tells you anything you did not already believe.",
    };
    public static final String[] DAY_CLEAR = {
        "Not a cloud. The horizon is a ruled line and the sun is where the almanac says it should be, which is the only honest thing on board.",
        "Clear from rail to rail. You could have the truth off the glass in an hour, and you would lose the afternoon's run doing it.",
        "The sky is open. Everything you have written in the book is checkable today, and none of it has been checked.",
    };
    public static final String[] DAY_FOUL = {
        "Grey from rail to rail and the sea coming over the bow. You make what you can and you write it down.",
        "No sun, no stars, no horizon. The book is the only world there is today.",
        "Bad weather. The clocks are in their box, and the box is the only thing on board that claims to know anything.",
    };

    public static final String PROMPT_NEVER =
        "You have not looked at anything outside this ship yet. Everything on the right is what you "
        + "wrote down, and none of it has been checked.";
    /** %s is how long ago the last sighting was. */
    public static final String PROMPT_LONG =
        "It has been %s since anything on the right was checked against something that was not a clock.";
    public static final String PROMPT_DEFAULT =
        "Write one of these into the book. The book is what you steer by, and it will not be checked today.";

    public static final String WHAT_EACH_CLOCK = "What each clock would have you write:";
    public static final String CLOCK_A = "Clock A";
    public static final String CLOCK_B = "Clock B";
    public static final String TAKE_SIGHT = "Take a sighting";
    public static final String SKY_OPEN = "the sky is open -- this is the only honest reading on board";
    public static final String SKY_CLOSED = "the sky is closed";
    public static final String MILES = " miles";

    public static final String BOOK_HEAD = "THE SHIP'S BOOK";
    public static final String CLOCKS_HEAD = "THE CLOCKS";
    public static final String OF_MILES = "of " + (int) NEEDED + " miles";
    public static final String ROW_LAST_LOOKED = "last looked";
    public static final String ROW_AND_FOUND = "and found";
    public static final String ROW_LOOKS_USED = "looks used";
    public static final String ROW_DAYS_LEFT = "days left";
    public static final String NEVER = "never";
    public static final String AHEAD = "ahead of the sea";
    public static final String BEHIND = "behind the sea";
    /** The bare direction words, for the sighting line. */
    public static final String SIGHT_AHEAD = "ahead of";
    public static final String SIGHT_BEHIND = "behind";
    public static final String RATE_NOTE =
        "A rate is only as good as the day it was written. The clocks do not tell you when they have moved.";
    /** %s is the rate, e.g. "+7". */
    public static final String RATED_AT = "rated %s, %s";
    public static final String IN_PORT = "in port";

    public static final String SIGHT_HEAD = "You have the sun.";
    /** %s is the distance, %s the direction. */
    public static final String SIGHT_WAS =
        "Your book was %s miles %s the sea.";
    public static final String SIGHT_EXACT =
        "Your book was exactly on the sea. It has happened once before, to somebody else.";

    /**
     * The book panel's version of the same afternoon.
     *
     * SIGHT_EXACT is a sentence and this is a value in a key/value row, so
     * they are two strings -- but they are the same fact, and the panel used
     * to report it as a direction.
     */
    public static final String FOUND_EXACT = "exactly on the sea";
    /** %s is the clock name, %s the rate. */
    public static final String SIGHT_RERATED =
        "%s is running %s miles a day, and the book now says so.";
    public static final String SIGHT_PROSE =
        "The correction is written into the book and both clocks are re-rated, and you have spent most "
        + "of the day doing it. What you know now is true today. Nothing about it says anything about "
        + "tomorrow, and tonight the rates will move again.";

    public static final String VOYAGE_HEAD = "THE VOYAGE";
    public static final String VOYAGE_COLS = "day     sky      wrote      had      off by";
    public static final String LOOKED = "looked";

    public static final String HEAD_FOUND = "You raise the island at dawn.";
    public static final String HEAD_SHORT = "You call for land, and there is no land.";
    public static final String HEAD_PAST = "You call for land, and you are already past it.";
    public static final String HEAD_SEASON = "The season turns before you get there.";

    /** %s called, %s here, %s off. */
    public static final String VERDICT_FOUND =
        "You called for land at %s miles by the book, and you were at %s. The book was %s miles out, "
        + "and %s miles is inside the error a landfall can absorb.";
    /** %s called, %s here, %s off. */
    public static final String VERDICT_SHORT =
        "You called for land at %s miles by the book. You were at %s -- %s miles short, with nothing "
        + "on the horizon in any direction and no way to know which way to beat.";
    /** %s called, %s here, %s off. */
    public static final String VERDICT_PAST =
        "You called for land at %s miles by the book. You were at %s -- %s miles beyond the island, "
        + "which is now somewhere behind you in a great deal of water.";
    /** %s needed, %s here, %s off. */
    public static final String VERDICT_SEASON =
        "The book never reached %s. The season turned, and you put the helm over and went back. You "
        + "were at %s miles, which is %s miles short of where you needed to be.";

    public static final String CLOSING_NONE =
        "You never looked. The book was a perfectly consistent account of a voyage that was "
        + "happening somewhere else, and there was nothing in it that could have told you so.";
    public static final String CLOSING_ONE =
        "You looked once. One honest reading in sixteen days, and the whole voyage after it "
        + "rested on a rate that was true on the afternoon you took it.";
    public static final String CLOSING_TWO =
        "You looked twice. Two afternoons of truth in sixteen days, and between them the book "
        + "ran on its own, which is what a book does.";
    /** %s is the number of looks. */
    public static final String CLOSING_MANY =
        "You looked %s times, and every one of them cost you the day you would otherwise have "
        + "spent getting there.";
    public static final String CLOSING_SHARED =
        "Two clocks that agree have agreed about nothing. They share a box, a temperature and a "
        + "knock, and when the same cause moves both of them they will sit side by side in perfect "
        + "agreement and both be wrong by the same amount. The only instrument on board that cannot "
        + "share their mistakes is the sky, and the sky is only open on days you would rather be "
        + "sailing.";

    // ---- the lines that depend on a number, as functions of it ------------

    /**
     * The day counter.
     *
     * After a sighting the day has already turned over and the screen is
     * still showing the day the look was taken on, so the two screens do
     * not compute it the same way -- which is exactly why it lives here
     * rather than being written twice.
     */
    public static String dayLabel(int day, boolean onSightScreen, boolean finished) {
        if (finished) return VOYAGE_OVER;
        int shown = onSightScreen ? day : day + 1;
        return DAY_OF.formatted(Math.min(shown, DAYS), DAYS);
    }

    /**
     * What the book panel says the last sighting found.
     *
     * A correction of exactly zero has no direction, and "0 miles behind
     * the sea" directly under "your book was exactly on the sea" reads as a
     * mistake -- which it was.
     *
     * The rounding is deliberate and it is not decoration: this game produces
     * errors of 1e-13 whenever the book and the sea land on the same double,
     * and a bare `off == 0` test would print "0 miles behind the sea" for
     * exactly the case the line exists to handle. The sight screen already has
     * a sentence for that afternoon; the panel uses it.
     */
    public static String foundLine(double off) {
        long m = Math.round(Math.abs(off));
        if (m == 0) return FOUND_EXACT;
        return m + MILES + " " + (off > 0 ? AHEAD : BEHIND);
    }

    public static String ageLabel(int days) {
        if (days <= 0) return "today";
        if (days == 1) return "yesterday";
        return days + " days ago";
    }

    /** A rate with its sign, because the sign is the whole point of it. */
    public static String rateLabel(Clock c) {
        return (c.rated > 0 ? "+" : "") + Math.round(c.rated);
    }

    /**
     * When a clock was last rated. A rating made before the ship sailed is
     * not "yesterday" -- it is the one the voyage is spending.
     */
    public static String ratedWhenLabel(Clock c, int day) {
        return c.ratedOn < 0 ? IN_PORT : ageLabel(c.ageOn(day));
    }

    public static String weatherLine(Weather w) {
        return switch (w) {
            case FAIR -> WEATHER_FAIR;
            case CLEAR -> WEATHER_CLEAR;
            case FOUL -> WEATHER_FOUL;
        };
    }

    /** The day's colour, chosen from the same counter-based draw the
     *  simulation uses, so a seed replays the same words. */
    public String dayProse() {
        String[] pool = switch (weather) {
            case FAIR -> DAY_FAIR;
            case CLEAR -> DAY_CLEAR;
            case FOUL -> DAY_FOUL;
        };
        return pool[(int) Math.floor(rand(90) * pool.length) % pool.length];
    }

    public String dayPrompt() {
        if (!everLooked) return PROMPT_NEVER;
        if (day - lastSightDay >= 5) {
            return PROMPT_LONG.formatted(ageLabel(day - lastSightDay));
        }
        return PROMPT_DEFAULT;
    }

    public String headline() {
        return switch (ending) {
            case "found" -> HEAD_FOUND;
            case "short" -> HEAD_SHORT;
            case "past" -> HEAD_PAST;
            default -> HEAD_SEASON;
        };
    }

    public String verdict() {
        long here = Math.round(finalTrue);
        long called = Math.round(book);
        long off = Math.round(Math.abs(miss()));
        return switch (ending) {
            case "found" -> VERDICT_FOUND.formatted(called, here, off, off);
            case "short" -> VERDICT_SHORT.formatted(called, here, off);
            case "past" -> VERDICT_PAST.formatted(called, here, off);
            default -> VERDICT_SEASON.formatted((long) NEEDED, here, off);
        };
    }

    public String closing() {
        int looks = sightings;
        String first = switch (Math.min(looks, 3)) {
            case 0 -> CLOSING_NONE;
            case 1 -> CLOSING_ONE;
            case 2 -> CLOSING_TWO;
            default -> CLOSING_MANY.formatted(looks);
        };
        return first + "\n\n" + CLOSING_SHARED;
    }

    /** What the sighting screen says the book was, given the correction. */
    public static String sightWas(double off) {
        if (off == 0) return SIGHT_EXACT;
        return SIGHT_WAS.formatted(Math.round(Math.abs(off)),
                off > 0 ? SIGHT_AHEAD : SIGHT_BEHIND);
    }

    // ---- storage ----------------------------------------------------------

    public String serialize() {
        StringBuilder b = new StringBuilder();
        b.append("bearings 1\n");
        b.append("seed ").append(seed).append('\n');
        b.append("day ").append(day).append('\n');
        b.append("weather ").append(weather).append('\n');
        b.append("todayRun ").append(todayRun).append('\n');
        b.append("trueRun ").append(trueRun).append('\n');
        b.append("book ").append(book).append('\n');
        b.append("sightings ").append(sightings).append('\n');
        b.append("lastSightDay ").append(lastSightDay).append('\n');
        b.append("lastCorrected ").append(lastCorrected).append('\n');
        b.append("everLooked ").append(everLooked ? 1 : 0).append('\n');
        b.append("finished ").append(finished ? 1 : 0).append('\n');
        b.append("found ").append(found ? 1 : 0).append('\n');
        b.append("finalTrue ").append(finalTrue).append('\n');
        b.append("ending ").append(ending.isEmpty() ? "-" : ending).append('\n');
        b.append("clearDays ").append(clearDays).append('\n');
        b.append("lastClearDay ").append(lastClearDay).append('\n');
        for (Clock c : clocks) {
            b.append("clock ").append(c.name).append(' ').append(c.drift).append(' ')
             .append(c.rated).append(' ').append(c.ratedOn).append('\n');
        }
        for (Day d : days) {
            b.append("rec ").append(d.index).append(' ').append(d.weather).append(' ')
             .append(d.pick).append(' ').append(d.recorded).append(' ').append(d.actual)
             .append(' ').append(d.bookAfter).append(' ').append(d.trueAfter).append(' ')
             .append(d.clockA).append(' ').append(d.clockB).append('\n');
        }
        return b.toString();
    }

    public static Bearings deserialize(String data) throws IOException {
        long seed = 0;
        for (String line : data.split("\n")) {
            if (line.startsWith("seed ")) { seed = Long.parseLong(line.substring(5).trim()); break; }
        }
        Bearings b = new Bearings(seed);
        for (String line : data.split("\n")) {
            String[] f = line.trim().split(" ");
            if (f.length == 0 || f[0].isEmpty()) continue;
            switch (f[0]) {
                case "day" -> b.day = Integer.parseInt(f[1]);
                case "weather" -> b.weather = Weather.valueOf(f[1]);
                case "todayRun" -> b.todayRun = Double.parseDouble(f[1]);
                case "trueRun" -> b.trueRun = Double.parseDouble(f[1]);
                case "book" -> b.book = Double.parseDouble(f[1]);
                case "sightings" -> b.sightings = Integer.parseInt(f[1]);
                case "lastSightDay" -> b.lastSightDay = Integer.parseInt(f[1]);
                case "lastCorrected" -> b.lastCorrected = Double.parseDouble(f[1]);
                case "everLooked" -> b.everLooked = f[1].equals("1");
                case "finished" -> b.finished = f[1].equals("1");
                case "found" -> b.found = f[1].equals("1");
                case "finalTrue" -> b.finalTrue = Double.parseDouble(f[1]);
                case "ending" -> b.ending = f[1].equals("-") ? "" : f[1];
                case "clearDays" -> b.clearDays = Integer.parseInt(f[1]);
                case "lastClearDay" -> b.lastClearDay = Integer.parseInt(f[1]);
                case "clock" -> {
                    int i = f[1].equals("A") ? 0 : 1;
                    Clock c = b.clocks.get(i);
                    c.drift = Double.parseDouble(f[2]);
                    c.rated = Double.parseDouble(f[3]);
                    c.ratedOn = Integer.parseInt(f[4]);
                }
                case "rec" -> {
                    Day d = new Day();
                    d.index = Integer.parseInt(f[1]);
                    d.weather = Weather.valueOf(f[2]);
                    d.pick = Pick.valueOf(f[3]);
                    d.recorded = Double.parseDouble(f[4]);
                    d.actual = Double.parseDouble(f[5]);
                    d.bookAfter = Double.parseDouble(f[6]);
                    d.trueAfter = Double.parseDouble(f[7]);
                    d.clockA = Double.parseDouble(f[8]);
                    d.clockB = Double.parseDouble(f[9]);
                    b.days.add(d);
                }
                default -> { }
            }
        }
        return b;
    }

    public void save(Path file) throws IOException {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, serialize(), StandardCharsets.UTF_8);
    }

    public static Bearings load(Path file) throws IOException {
        if (!Files.exists(file)) return new Bearings();
        return deserialize(Files.readString(file, StandardCharsets.UTF_8));
    }

    public static Bearings fresh() { return new Bearings(); }
}
