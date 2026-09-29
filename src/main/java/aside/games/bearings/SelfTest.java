package aside.games.bearings;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless checks for bearings.
 *
 * The point of these is not coverage. It is that the clocks' rule IS the
 * game, so it has to be true independently of anything drawn. If "a reading
 * is the true run plus a drift you were not told about" were only true on
 * screen, the game would be a picture of a game.
 *
 * Run: java -cp classes aside.games.bearings.SelfTest
 */
public final class SelfTest {

    static int checks = 0, failed = 0;

    static void ok(boolean cond, String what) {
        checks++;
        if (!cond) { failed++; System.out.println("FAIL  " + what); }
    }

    static void eq(Object a, Object b, String what) {
        checks++;
        boolean same = a == null ? b == null : a.equals(b);
        if (!same) { failed++; System.out.println("FAIL  " + what + "  (got " + a + ", want " + b + ")"); }
    }

    static void near(double a, double b, double tol, String what) {
        checks++;
        if (Math.abs(a - b) > tol) {
            failed++;
            System.out.println("FAIL  " + what + "  (got " + a + ", want " + b + " +/- " + tol + ")");
        }
    }

    public static void main(String[] args) throws Exception {
        content();
        theReading();
        theStaleness();
        theSighting();
        agreementIsNotEvidence();
        theDisagreement();
        theSkyOpens();
        determinism();
        storage();
        landfall();
        theSeason();
        balance();
        System.out.println((checks - failed) + "/" + checks + " checks passed"
                + (failed == 0 ? "" : "  --  " + failed + " FAILED"));
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ content

    static void content() {
        ok(Bearings.DAYS >= 12, "the voyage is long enough to have a middle");
        ok(Bearings.TOLERANCE > 0 && Bearings.TOLERANCE < Bearings.NEEDED / 10,
                "the landfall tolerance is a fraction of the passage");
        ok(Bearings.run(Bearings.Weather.FAIR) > Bearings.run(Bearings.Weather.CLEAR),
                "a clear sky is light air: looking costs you the best day");
        ok(Bearings.run(Bearings.Weather.CLEAR) > Bearings.run(Bearings.Weather.FOUL),
                "foul weather is the slowest of the three");

        double sum = 0;
        for (double w : Bearings.DRIFT_WEIGHT) sum += w;
        near(sum, 1.0, 1e-9, "the drift table is a distribution");
        ok(Bearings.DRIFT_VALUE.length == Bearings.DRIFT_WEIGHT.length, "drift table is square");
        ok(Bearings.DRIFT_WEIGHT[0] > 0.25, "most of the time a clock is where you left it");
        boolean bothSigns = false;
        for (double v : Bearings.DRIFT_VALUE) if (v > 0) bothSigns = true;
        ok(bothSigns, "drift has a direction, and it is not always the same one");
    }

    // ------------------------------------------------------- the rule

    static void theReading() {
        Bearings b = new Bearings(7);
        b.clocks.get(0).drift = 5;
        b.clocks.get(0).rated = 5;
        b.clocks.get(1).drift = -2;
        b.clocks.get(1).rated = -2;
        b.weather = Bearings.Weather.FAIR;
        b.todayRun = Bearings.run(Bearings.Weather.FAIR);

        near(b.reading(0), b.todayRun + 5, 1e-9, "A reads the run plus its drift");
        near(b.reading(1), b.todayRun - 2, 1e-9, "B reads the run plus its drift");
        near(b.estimate(0), b.todayRun, 1e-9, "a freshly rated clock estimates the truth");
        near(b.estimate(1), b.todayRun, 1e-9, "and so does the other one");

        // Now make A lie without telling the book.
        b.clocks.get(0).drift = 11;
        near(b.estimate(0), b.todayRun + 6, 1e-9, "a clock that moved since its rating is wrong by the difference");
        near(b.estimate(1), b.todayRun, 1e-9, "the clock that did not move is still right");
    }

    static void theStaleness() {
        Bearings b = new Bearings(11);
        b.clocks.get(0).drift = 0; b.clocks.get(0).rated = 0; b.clocks.get(0).ratedOn = 0;
        b.clocks.get(1).drift = 0; b.clocks.get(1).rated = 0; b.clocks.get(1).ratedOn = 0;
        b.weather = Bearings.Weather.FAIR;

        double before = b.error();
        b.choose(Bearings.Pick.A);
        near(b.error() - before, 0, 1e-9, "recording from a clock that has not moved adds no error");

        // The clock moves overnight, and nothing says so.
        b.clocks.get(0).drift = 8;
        double mid = b.error();
        b.choose(Bearings.Pick.A);
        near(b.error() - mid, 8, 1e-9, "and once it has moved, every day you trust it adds the whole drift");

        // The other clock never moved, so it is still the honest one.
        double now = b.error();
        b.choose(Bearings.Pick.B);
        near(b.error() - now, 0, 1e-9, "the clock that has not moved stays honest");

        eq(b.clocks.get(0).ageOn(b.day), b.day, "an unrated clock ages from the day it was rated");
    }

    static void theSighting() {
        Bearings b = new Bearings(23);
        // Keep A wrong from the start, so the walk to a clear day is
        // carrying a real error by the time the sky opens.
        int guard = 0;
        while (!b.finished && guard++ < 40) {
            // Stop on a clear day that is already carrying an error. Day one
            // can itself be clear, and a sighting on an unblemished book
            // would prove nothing.
            if (b.canSight() && Math.abs(b.error()) > 0) break;
            b.clocks.get(0).drift = 18;
            b.clocks.get(0).rated = 0;
            b.choose(Bearings.Pick.A);
        }
        ok(b.canSight(), "a clear day arrives");

        b.clocks.get(0).drift = 18;
        b.clocks.get(1).drift = -7;
        double day = b.day;
        double trueBefore = b.trueRun;
        double bookBefore = b.book;
        double kept = b.todayRun * (1 - Bearings.SIGHT_COST);
        ok(Math.abs(b.error()) > 0, "the book has drifted off the sea");

        ok(b.choose(Bearings.Pick.SIGHT), "a sighting can be taken on a clear day");
        near(b.error(), 0, 1e-9, "a sighting puts the book back on the sea");
        near(b.trueRun, trueBefore + kept, 1e-9, "and costs most of the day's run doing it");
        ok(kept < b.todayRun, "a look is not free");
        ok(Math.abs(bookBefore - b.book) > 0, "the book moved even though the ship barely did");
        eq(b.clocks.get(0).rated, 18.0, "A is re-rated to what it actually is");
        eq(b.clocks.get(1).rated, -7.0, "B is re-rated too");
        eq(b.clocks.get(0).ratedOn, (int) day, "and both are marked as rated today");
        eq(b.sightings, 1, "one look used");
        near(b.lastCorrected, bookBefore - trueBefore, 1e-9, "the book remembers how far off it was");

        // On a day the sky is closed, the key does nothing.
        Bearings c = new Bearings(31);
        int g2 = 0;
        while (c.weather == Bearings.Weather.CLEAR && !c.finished && g2++ < 40) c.choose(Bearings.Pick.A);
        if (c.weather != Bearings.Weather.CLEAR) {
            ok(!c.canSight(), "there is nothing to look at through cloud");
            ok(!c.choose(Bearings.Pick.SIGHT), "and the sighting is refused");
            eq(c.sightings, 0, "a refused sighting is not counted");
        }
    }

    /**
     * The one inference the game offers.
     *
     * When exactly one clock has moved since it was rated, how far the two
     * clocks now disagree IS that clock's rate -- and the rate times the days
     * since the rating is the error you are carrying. This is what a player
     * who is paying attention can actually work out, and it is why the two
     * readings are on the screen side by side.
     */
    static void theDisagreement() {
        Bearings b = new Bearings(53);
        b.weather = Bearings.Weather.FAIR;
        b.todayRun = Bearings.run(Bearings.Weather.FAIR);
        b.clocks.get(0).drift = 18; b.clocks.get(0).rated = 0;
        b.clocks.get(1).drift = 0;  b.clocks.get(1).rated = 0;
        near(b.estimate(0) - b.estimate(1), 18, 1e-9,
                "when only one clock has moved, the disagreement is that clock's rate");
        near(b.reading(0) - b.reading(1), 18, 1e-9, "and it shows in the readings themselves");

        // Three days of trusting the moved clock is three times the rate.
        b.clocks.get(0).ratedOn = 0;
        b.day = 3;
        near(b.clocks.get(0).ageOn(b.day) * (b.estimate(0) - b.estimate(1)), 54, 1e-9,
                "the rate times the days since the rating is the error being carried");

        // And when both moved the same way, the disagreement is gone and the
        // error is not.
        b.clocks.get(1).drift = 18;
        near(b.estimate(0) - b.estimate(1), 0, 1e-9, "a shared cause leaves the clocks agreeing");
        near(b.estimate(0) - b.todayRun, 18, 1e-9, "and both of them wrong by the same amount");
    }

    /**
     * The thesis, as a test.
     *
     * When a shared cause moves both clocks the same way, they agree -- and
     * the agreement carries no information at all. This is the state the
     * whole game is built to put the player in.
     */
    static void agreementIsNotEvidence() {
        Bearings b = new Bearings(41);
        b.weather = Bearings.Weather.FAIR;
        b.todayRun = Bearings.run(Bearings.Weather.FAIR);
        b.clocks.get(0).drift = 8; b.clocks.get(0).rated = 0;
        b.clocks.get(1).drift = 8; b.clocks.get(1).rated = 0;

        near(b.reading(0), b.reading(1), 1e-9, "the two clocks agree exactly");
        near(b.estimate(0), b.estimate(1), 1e-9, "and so do the two estimates");

        double truth = b.todayRun;
        near(b.estimate(0) - truth, 8, 1e-9,
                "two clocks agreeing is still two clocks: the agreement is 8 miles wrong");

        // And the two-clock case is not better than the one-clock case.
        Bearings one = new Bearings(41);
        one.weather = Bearings.Weather.FAIR;
        one.todayRun = Bearings.run(Bearings.Weather.FAIR);
        one.clocks.get(0).drift = 8; one.clocks.get(0).rated = 0;
        near(one.estimate(0) - truth, b.estimate(0) - truth, 1e-9,
                "a second clock that shares the first one's cause adds nothing");
    }

    /** The sky has to open, or the game is decided by the weather. */
    static void theSkyOpens() {
        int shortVoyages = 0;
        for (int s = 0; s < 300; s++) {
            Bearings b = new Bearings(5000 + s);
            int guard = 0;
            while (!b.finished && guard++ < 60) b.choose(Bearings.Pick.A);
            if (b.days.size() < 5) { shortVoyages++; continue; }
            boolean anyClear = false;
            for (Bearings.Day d : b.days) if (d.weather == Bearings.Weather.CLEAR) anyClear = true;
            ok(anyClear, "seed " + (5000 + s) + ": a voyage that ran past day four had a clear day");
            int gap = 0, worst = 0;
            for (Bearings.Day d : b.days) {
                if (d.weather == Bearings.Weather.CLEAR) gap = 0; else gap++;
                if (d.index >= 4 && gap > worst) worst = gap;
            }
            ok(worst <= 6, "seed " + (5000 + s) + ": never more than six days without a look (was " + worst + ")");
        }
        ok(shortVoyages < 300, "some voyages run long enough to be checked");
    }

    static void determinism() {
        Bearings a = new Bearings(99);
        Bearings b = new Bearings(99);
        int guard = 0;
        while (!a.finished && guard++ < 60) a.choose(Bearings.Pick.A);
        guard = 0;
        while (!b.finished && guard++ < 60) b.choose(Bearings.Pick.A);
        eq(a.serialize(), b.serialize(), "the same seed and the same choices give the same voyage");

        Bearings c = new Bearings(100);
        guard = 0;
        while (!c.finished && guard++ < 60) c.choose(Bearings.Pick.A);
        ok(!c.serialize().equals(a.serialize()), "and a different seed gives a different one");
    }

    static void storage() throws Exception {
        Bearings b = new Bearings(1234);
        int guard = 0;
        while (!b.finished && guard++ < 60) {
            if (b.canSight() && b.progress() > 0.5) b.choose(Bearings.Pick.SIGHT);
            else b.choose(b.day % 2 == 0 ? Bearings.Pick.A : Bearings.Pick.B);
        }
        String text = b.serialize();
        Bearings back = Bearings.deserialize(text);
        eq(back.serialize(), text, "a voyage survives a round trip through text");
        eq(back.days.size(), b.days.size(), "the day records come back");
        eq(back.found, b.found, "and so does the outcome");
        eq(back.clocks.get(0).drift, b.clocks.get(0).drift, "and the hidden drift");

        Path p = Files.createTempFile("bearings", ".state");
        b.save(p);
        Bearings fromDisk = Bearings.load(p);
        eq(fromDisk.serialize(), text, "and through a file");
        Files.deleteIfExists(p);
    }

    static void landfall() {
        Bearings b = new Bearings(5);
        b.trueRun = Bearings.NEEDED + Bearings.TOLERANCE;
        b.book = Bearings.NEEDED;
        b.settle();
        ok(b.finished && b.found, "a book that calls land exactly at the edge still finds it");
        eq(b.ending, "found", "and says so");

        Bearings c = new Bearings(5);
        c.trueRun = Bearings.NEEDED + Bearings.TOLERANCE + 1;
        c.book = Bearings.NEEDED;
        c.settle();
        ok(c.finished && !c.found, "one mile past the edge does not");
        eq(c.ending, "past", "and says which way");

        Bearings d = new Bearings(5);
        d.trueRun = Bearings.NEEDED - Bearings.TOLERANCE - 1;
        d.book = Bearings.NEEDED;
        d.settle();
        ok(d.finished && !d.found, "and short of it does not either");
        eq(d.ending, "short", "and says which way");
        ok(d.miss() < 0, "the miss is signed: negative is short of the island");

        // Calling land early is allowed and is its own mistake.
        Bearings e = new Bearings(5);
        e.trueRun = 400;
        e.book = Bearings.NEEDED;
        e.settle();
        ok(e.finished && !e.found, "calling land while still far out is a miss, not a rule violation");
    }

    static void theSeason() {
        Bearings b = new Bearings(8);
        b.day = Bearings.DAYS;
        b.book = Bearings.NEEDED - 1;
        b.settle();
        ok(b.finished && !b.found, "running out of days is a failure");
        eq(b.ending, "season", "and it is its own ending");
        ok(b.miss() < 0, "you were short of the island when the season turned");
    }

    // ------------------------------------------------------- balance

    interface Strategy { Bearings.Pick pick(Bearings b); String name(); }

    /** The clock the book trusts most: the one rated most recently. */
    static Bearings.Pick fresher(Bearings b) {
        int a = b.clocks.get(0).ageOn(b.day), c = b.clocks.get(1).ageOn(b.day);
        if (a != c) return a < c ? Bearings.Pick.A : Bearings.Pick.B;
        // Equally stale, so the ratings are equally worthless. Fall back on
        // the two estimates and take the one nearer their mean, which is the
        // best a navigator can do with nothing but agreement.
        double ea = b.estimate(0), eb = b.estimate(1), mid = (ea + eb) / 2;
        return Math.abs(ea - mid) <= Math.abs(eb - mid) ? Bearings.Pick.A : Bearings.Pick.B;
    }

    static int[] play(Strategy s, int count) {
        int wins = 0, called = 0, looks = 0;
        for (int i = 0; i < count; i++) {
            Bearings b = new Bearings(20_000 + i * 37L);
            int guard = 0;
            while (!b.finished && guard++ < 80) {
                Bearings.Pick p = s.pick(b);
                if (!b.choose(p)) b.choose(Bearings.Pick.A);
            }
            if (b.found) wins++;
            if (!b.ending.equals("season")) called++;
            looks += b.sightings;
        }
        return new int[]{ wins, called, looks };
    }

    static void balance() {
        int n = 800;
        Strategy never = new Strategy() {
            public String name() { return "never look (always A)"; }
            public Bearings.Pick pick(Bearings b) { return Bearings.Pick.A; }
        };
        Strategy neverB = new Strategy() {
            public String name() { return "never look (always B)"; }
            public Bearings.Pick pick(Bearings b) { return Bearings.Pick.B; }
        };
        Strategy everyClear = new Strategy() {
            public String name() { return "look every clear day"; }
            public Bearings.Pick pick(Bearings b) {
                return b.canSight() ? Bearings.Pick.SIGHT : fresher(b);
            }
        };
        Strategy good = new Strategy() {
            public String name() { return "look when they disagree, or a week old"; }
            public Bearings.Pick pick(Bearings b) {
                boolean apart = Math.abs(b.estimate(0) - b.estimate(1)) >= 22;
                boolean old = b.clocks.get(0).ageOn(b.day) >= 7 || b.clocks.get(1).ageOn(b.day) >= 7;
                if (b.canSight() && (apart || old)) return Bearings.Pick.SIGHT;
                return fresher(b);
            }
        };

        Strategy[] all = { never, neverB, everyClear, good };
        int[] wins = new int[all.length];
        System.out.println("  strategy                                found   called   avg looks");
        for (int i = 0; i < all.length; i++) {
            int[] r = play(all[i], n);
            wins[i] = r[0];
            System.out.printf("  %-38s %4d%%   %4d%%   %.2f%n",
                    all[i].name(), r[0] * 100 / n, r[1] * 100 / n, r[2] / (double) n);
        }

        ok(wins[0] * 100 / n < 50, "never looking loses more often than it wins");
        ok(wins[1] * 100 / n < 50, "and it is not a matter of which clock you picked");
        ok(wins[2] * 100 / n < 50, "looking every chance you get is worse still: it costs you the island");
        ok(wins[3] * 100 / n > 40, "there is a middle that works");
        ok(wins[3] > wins[0], "and it beats never looking");
        ok(wins[3] < n, "and nothing wins every time");
    }
}
