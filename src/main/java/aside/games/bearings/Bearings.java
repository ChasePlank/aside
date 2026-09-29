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
        double penalty = Math.min(0.45, Math.abs(error()) / 250.0);
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
        if (r < 0.25) w = Weather.CLEAR;
        else if (r < 0.85) w = Weather.FAIR;
        else w = Weather.FOUL;

        // A voyage with no clear day is not a game, and a navigator who has
        // not been able to look for a week is being punished by the weather
        // rather than by their own choices. The sky opens on its own
        // schedule, and this is that schedule.
        if (w != Weather.CLEAR && day >= 4) {
            if (clearDays == 0) w = Weather.CLEAR;
            else if (day - lastClearDay >= 6) w = Weather.CLEAR;
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
        if (rand(2) < 0.10) {
            double d = newDrift(7);
            for (Clock c : clocks) c.drift = d;
            return;
        }
        if (rand(3) < 0.22) clocks.get(0).drift = newDrift(5);
        if (rand(4) < 0.22) clocks.get(1).drift = newDrift(6);
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
