package aside.games.fnaf7.engine;

import aside.games.fnaf7.MouseMap;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The FNAF 7 checks, runnable with no display.
 *
 *     java -cp classes aside.games.fnaf7.engine.SelfTest
 *
 * <p>These are not "does it compile" checks. Each one is a claim the game
 * makes about itself, written down so it cannot quietly stop being true:
 * that the record reads the light and not the bar, that the unit comes to
 * the side the record does not hold, that the bar cannot move while the
 * lamp is on, that the filament cuts out, that a hall is the only thing
 * the lamp shows, and that the week gets harder.
 *
 * <p>The survival numbers are printed rather than asserted tightly,
 * because they are a <i>reading</i> of the difficulty, not a contract.
 * What is asserted is the shape: doing nothing must be hopeless, the
 * policy that plays the record must beat the policy that only reacts, and
 * the week must get harder.
 */
public final class SelfTest {

    static int checks = 0;
    static int failed = 0;

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--survival")) {
            survival();
            System.out.println();
            System.out.println(checks + " checks, " + failed + " failed");
            if (failed > 0) System.exit(1);
            return;
        }
        office();
        record();
        rule();
        hands();
        filament();
        lamp();
        instrument();
        mouse();
        phone();
        survival();
        System.out.println();
        System.out.println(checks + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------- helpers

    static void section(String name) {
        System.out.println();
        System.out.println("--- " + name + " ---");
    }

    static void check(String name, boolean ok) {
        checks++;
        if (ok) System.out.println("  ok   " + name);
        else { failed++; System.out.println("  FAIL " + name); }
    }

    /** Run a night with no player at all, to the end. */
    static Shift idle(int night, long seed) {
        Shift s = new Shift(night, seed);
        double dt = 1.0 / 60.0;
        for (int i = 0; i < 60 * 400 && s.status == Shift.Status.PLAYING; i++) s.update(dt);
        return s;
    }

    /** Advance a shift by a number of seconds, with no player. */
    static void run(Shift s, double seconds) {
        double dt = 1.0 / 60.0;
        for (int i = 0; i < (int) (seconds / dt) && s.status == Shift.Status.PLAYING; i++) {
            s.update(dt);
        }
    }

    /**
     * A shift with the unit parked, so a check about the record is a check
     * about the record.
     *
     * <p>Without this the decay check measures how long the player
     * survives, because a night that ends at forty seconds has a record
     * that stopped decaying at forty seconds.
     */
    static Shift quiet(int night, long seed) {
        Shift s = new Shift(night, seed);
        s.where = Shift.Where.AWAY;
        s.timer = 1e9;
        return s;
    }

    /** Find a seed on a night where the unit is in a particular place. */
    static Shift findArrival(int night, int tries) {
        for (int i = 0; i < tries; i++) {
            Shift s = new Shift(night, 7919L * i + night);
            run(s, s.away() + 0.05);
            if (s.where.inHall()) return s;
        }
        throw new IllegalStateException("no arrival on night " + night);
    }

    // -------------------------------------------------------------- the office

    static void office() {
        section("the office");

        check("five nights", MouseMap.NIGHTS == 5);
        for (int n = 1; n <= 5; n++) {
            Unit u = Unit.forNight(n);
            check("night " + n + " has a unit: " + u.name(), u != null);
            check("night " + n + " has a key: " + u.key(), !u.key().isEmpty());
            check("night " + n + " has a note", !u.note().isEmpty());
            check("night " + n + " has a tell in range", u.tell() >= 0 && u.tell() <= 1);
        }

        // The week is the order of how quiet they are. That is the second
        // ramp, and it is the one that closes the audio channel: a unit you
        // can hear is a unit you can answer without spending a look, and a
        // look is the only thing keeping the record sharp.
        for (int n = 1; n < 5; n++) {
            check("night " + n + " is louder than night " + (n + 1),
                    Unit.forNight(n).tell() > Unit.forNight(n + 1).tell());
        }
        check("the first unit is loud enough to be worth listening for",
                Unit.forNight(1).tell() >= 0.6);
        check("the last unit is quiet enough that listening is not a plan",
                Unit.forNight(5).tell() <= 0.2);

        // The two doors and the one bar. The whole night is this sentence.
        check("there are two sides", Shift.Side.values().length == 2);
        check("a side's other is the other side",
                Shift.Side.LEFT.other() == Shift.Side.RIGHT
                        && Shift.Side.RIGHT.other() == Shift.Side.LEFT);
        check("a hall is a hall", Shift.Where.HALL_LEFT.inHall()
                && Shift.Where.HALL_RIGHT.inHall());
        check("a doorway is not a hall", !Shift.Where.DOOR_LEFT.inHall()
                && !Shift.Where.DOOR_RIGHT.inHall());
        check("being away has no side", Shift.Where.AWAY.side() == null);
    }

    // -------------------------------------------------------------- the record

    static void record() {
        section("the record");

        // It reads the light and nothing else. The bar is the one thing
        // about the player it cannot see, which is what makes the bar a
        // defence rather than a tell.
        Shift s = new Shift(1, 11L);
        s.barAt = Shift.Side.LEFT;
        run(s, 6.0);
        check("a bar left alone teaches it nothing",
                s.attendL < 0.001 && s.attendR < 0.001);
        check("and it has no opinion", s.belief() == null);
        check("and it will guess", s.predicted() == null);

        // A look is what it reads.
        Shift t = new Shift(1, 12L);
        t.aim(Shift.Side.LEFT);
        run(t, 4.0);
        check("a look on the left teaches it the left", t.belief() == Shift.Side.LEFT);
        check("so it comes to the right", t.predicted() == Shift.Side.RIGHT);
        check("and it is sure", t.confidence() > 0.5);

        // And it forgets. The decay is what makes the readout a quantity
        // the player cannot compute by looking at the office.
        Shift q = quiet(1, 14L);
        q.aim(Shift.Side.LEFT);
        run(q, Shift.LIGHT_MAX);
        double before = Math.abs(q.attendL - q.attendR);
        check("a held look leaves a mark", before > 1.0);
        q.dark();
        run(q, q.memory() * 3);
        double after = Math.abs(q.attendL - q.attendR);
        check("a record left alone bleeds away ("
                        + Math.round(100 * after / before) + "% left)",
                after < before * 0.25);
        check("and eventually it has no opinion again", q.belief() == null);

        // The threshold is a threshold, not a hair trigger.
        Shift u = new Shift(1, 13L);
        u.aim(Shift.Side.RIGHT);
        run(u, 0.05);
        check("a glance is not an opinion",
                Math.abs(u.attendL - u.attendR) < Shift.SURE);

        // The week's memory shrinks: a unit that tracks your hands is a
        // unit whose record is about the present, and the present is the
        // one thing the player cannot hide.
        for (int n = 1; n < 5; n++) {
            Shift a = new Shift(n, 1L), b = new Shift(n + 1, 1L);
            check("night " + (n + 1) + " remembers less than night " + n,
                    b.memory() < a.memory());
        }
    }

    // ---------------------------------------------------------------- the rule

    static void rule() {
        section("the rule");

        // THE RULE, stated as arithmetic: with the record holding one side,
        // it comes to the other. Measured over four hundred draws on night
        // one, where the deviation chance is lowest.
        Shift s = new Shift(1, 21L);
        s.attendL = 10;
        s.attendR = 0;
        int right = 0, left = 0;
        for (int i = 0; i < 4000; i++) {
            if (s.choose() == Shift.Side.RIGHT) right++; else left++;
        }
        double follows = right / 4000.0;
        check("with the record on the left it comes to the right ("
                + Math.round(follows * 100) + "%)", follows > 0.85);
        // Half of the deviations land on the same side by chance, so the
        // share that follows the record is one minus half the deviation
        // chance -- which is the number the ladder is read against.
        check("and the deviation chance is the rest of it",
                Math.abs(follows - (1 - s.explore() / 2)) < 0.02);

        // And the other way, so the check is about the rule and not about
        // one side being special.
        Shift t = new Shift(1, 22L);
        t.attendR = 10;
        t.attendL = 0;
        int lefts = 0;
        for (int i = 0; i < 4000; i++) if (t.choose() == Shift.Side.LEFT) lefts++;
        check("with the record on the right it comes to the left ("
                + Math.round(100.0 * lefts / 4000) + "%)", lefts / 4000.0 > 0.85);

        // With no record at all it is a coin flip, which is what makes the
        // first move of every night a coin flip.
        Shift u = new Shift(1, 23L);
        int l = 0;
        for (int i = 0; i < 4000; i++) if (u.choose() == Shift.Side.LEFT) l++;
        check("with nothing on you it is a coin flip ("
                + Math.round(100.0 * l / 4000) + "%)",
                Math.abs(l / 4000.0 - 0.5) < 0.05);

        // The week's deviation chance grows, and it is the ramp.
        for (int n = 1; n < 5; n++) {
            Shift a = new Shift(n, 1L), b = new Shift(n + 1, 1L);
            check("night " + (n + 1) + " deviates more than night " + n,
                    b.explore() > a.explore());
        }

        // The readout is the opposite of the answer, and the check is that
        // the two are never the same side.
        Shift v = new Shift(1, 24L);
        v.aim(Shift.Side.LEFT);
        run(v, 3.0);
        check("the readout names the side it is NOT coming to",
                v.belief() != null && v.predicted() == v.belief().other());
    }

    // --------------------------------------------------------------- the hands

    static void hands() {
        section("the hands");

        // One pair of hands. The two rules are the same rule.
        Shift s = new Shift(1, 31L);
        s.barAt = Shift.Side.RIGHT;
        s.aim(Shift.Side.LEFT);
        check("the bar will not move while the lamp is on",
                !s.moveBar(Shift.Side.LEFT));
        check("and the bar is still where it was", s.barAt == Shift.Side.RIGHT);

        s.dark();
        check("with the lamp out it will", s.moveBar(Shift.Side.LEFT));
        check("and the light will not come up while it is in the air",
                !s.aim(Shift.Side.RIGHT));

        // While it is in the air it is on neither door. That window is the
        // price of changing your mind.
        check("a bar in the air is not on the left", !s.barred(Shift.Side.LEFT));
        check("a bar in the air is not on the right", !s.barred(Shift.Side.RIGHT));
        run(s, Shift.BAR_MOVE + 0.05);
        check("and then it is on the door it was sent to", s.barred(Shift.Side.LEFT));

        // Sending it where it already is is a no-op rather than a move.
        int moves = s.moves;
        check("sending the bar where it already is does nothing",
                !s.moveBar(Shift.Side.LEFT) && s.moves == moves);

        // A door that turns the unit back knocks the bar loose.
        Shift t = findArrival(1, 400);
        t.barAt = t.target;
        t.lit = false;
        run(t, t.approach() + Shift.STRIKE + 0.1);
        check("a barred door turns it back", t.repels == 1);
        check("and the bar comes loose", t.barAt == null);
        check("and it goes away to try again", t.where == Shift.Where.AWAY);
        check("and the night is still going", t.status == Shift.Status.PLAYING);

        // An open door does not.
        Shift u = findArrival(1, 400);
        u.barAt = u.target.other();
        u.lit = false;
        run(u, u.approach() + Shift.STRIKE + 0.1);
        check("an open door ends the night", u.status == Shift.Status.CAUGHT);
        check("and it names what came through", u.killer != null);
        check("and it says when", u.caughtAt >= 0);
    }

    // ------------------------------------------------------------ the filament

    static void filament() {
        section("the filament");

        Shift s = new Shift(1, 41L);
        s.aim(Shift.Side.LEFT);
        run(s, Shift.LIGHT_MAX * 0.9);
        check("the filament is burning", s.heat > 0.7 && !s.blown);
        check("and the lamp is still on", s.lit);

        run(s, Shift.LIGHT_MAX * 0.2);
        check("held past its limit it cuts out", s.blown);
        check("and the lamp is out", !s.lit);
        check("and it will not strike again yet", !s.aim(Shift.Side.LEFT));

        // It comes back, but only after it has cooled most of the way.
        run(s, Shift.LIGHT_COOL * 0.3);
        check("a warm filament still will not strike", s.blown);
        run(s, Shift.LIGHT_COOL);
        check("a cooled one will", !s.blown);
        check("and the lamp comes back", s.aim(Shift.Side.LEFT));

        // The budget is the night. A lamp that can be held is a lamp that
        // solves the night, which is what the first sweep said.
        Shift t = new Shift(1, 42L);
        t.aim(Shift.Side.LEFT);
        run(t, 30.0);
        double litFraction = t.litFor > 0 ? 1 : 0;
        check("a held lamp is not lit all the time", t.blown || !t.lit);
        check("and the duty cycle is under two thirds",
                Shift.LIGHT_MAX / (Shift.LIGHT_MAX
                        + Shift.LIGHT_COOL * (1 - Shift.LIGHT_RESET)) < 0.66);
    }

    // ---------------------------------------------------------------- the lamp

    static void lamp() {
        section("the lamp");

        Shift s = findArrival(1, 400);
        s.lightSide = s.target;
        s.lit = true;
        s.litFor = 0;
        check("a lamp that has not warmed shows nothing", !s.visible());

        s.litFor = Shift.LIGHT_WARM + 0.01;
        check("a warm lamp on its hall shows it", s.visible());

        s.lightSide = s.target.other();
        check("a lamp on the other hall shows nothing", !s.visible());

        // Halls only. A lamp that showed a doorway would answer the
        // question after the answer was useless.
        Shift t = findArrival(1, 400);
        t.where = t.target == Shift.Side.LEFT
                ? Shift.Where.DOOR_LEFT : Shift.Where.DOOR_RIGHT;
        t.lightSide = t.target;
        t.lit = true;
        t.litFor = Shift.LIGHT_WARM + 0.01;
        check("a lamp does not show a doorway", !t.visible());

        Shift u = findArrival(1, 400);
        u.where = Shift.Where.AWAY;
        u.lightSide = Shift.Side.LEFT;
        u.lit = true;
        u.litFor = Shift.LIGHT_WARM + 0.01;
        check("a lamp does not show an empty building", !u.visible());
    }

    // ------------------------------------------------------------ the instrument

    static void instrument() {
        section("the instrument");

        // The seed mixer. FNAF 6 shipped without it and the sweep read the
        // first draw of java.util.Random as a fact about the game: nights
        // 1 to 3 produced zero hostile units out of two hundred and nights
        // 4 and 5 produced 104 and 191, and the table looked like a clean
        // story about difficulty. It was a fact about Random.
        //
        // The check is not "is the share right" -- it is "is the share the
        // same on every night", because a correlation with the seed is
        // exactly a share that depends on which block of seeds you drew.
        double[] share = new double[5];
        for (int n = 1; n <= 5; n++) {
            int right = 0;
            for (int i = 0; i < 2000; i++) {
                Shift s = new Shift(n, 1000L * n + i);
                s.attendL = 10; s.attendR = 0;
                if (s.choose() == Shift.Side.RIGHT) right++;
            }
            share[n - 1] = right / 2000.0;
        }
        System.out.printf("       share that follows the record, by night: "
                + "%.3f %.3f %.3f %.3f %.3f%n",
                share[0], share[1], share[2], share[3], share[4]);
        // Each night against its own table, not against the others: the
        // nights are *supposed* to differ, so a spread across nights is the
        // ramp and not a bug. What a correlated seed would look like is a
        // night that misses its own number by more than sampling noise.
        for (int n = 1; n <= 5; n++) {
            double want = 1 - new Shift(n, 1L).explore() / 2;
            check("night " + n + "'s seeds are not correlated with its table ("
                            + String.format("%.3f", share[n - 1]) + " vs "
                            + String.format("%.3f", want) + ")",
                    Math.abs(share[n - 1] - want) < 0.02);
        }

        // The tables are monotone where they are supposed to be.
        for (int n = 1; n < 5; n++) {
            Shift a = new Shift(n, 1L), b = new Shift(n + 1, 1L);
            check("night " + (n + 1) + " is faster than night " + n,
                    b.away() < a.away() && b.approach() < a.approach());
        }
        check("the reaction window is positive on night one",
                new Shift(1, 1L).approach() - Shift.LIGHT_WARM - Shift.BAR_MOVE > 0);
        check("and it is under two seconds on night five",
                new Shift(5, 1L).approach() - Shift.LIGHT_WARM - Shift.BAR_MOVE < 2.0);
    }

    // --------------------------------------------------------------- the mouse

    static void mouse() {
        section("the mouse");

        double[][] regions = {MouseMap.LIGHT_L, MouseMap.LIGHT_R,
                MouseMap.BAR_L, MouseMap.BAR_R};
        MouseMap.Kind[] kinds = {MouseMap.Kind.LIGHT_L, MouseMap.Kind.LIGHT_R,
                MouseMap.Kind.BAR_L, MouseMap.Kind.BAR_R};
        for (int i = 0; i < regions.length; i++) {
            double[] r = regions[i];
            MouseMap.Hit h = MouseMap.hit(r[0] + r[2] / 2, r[1] + r[3] / 2);
            check("the centre of " + kinds[i] + " hits it", h.kind() == kinds[i]);
        }

        // Every button is inside the strip, and no two overlap. A button
        // that is drawn over another one is a button that cannot be
        // clicked, and it is invisible in a screenshot.
        for (double[] r : regions) {
            check("a button is inside the strip",
                    r[1] >= MouseMap.STRIP[1]
                            && r[1] + r[3] <= MouseMap.STRIP[1] + MouseMap.STRIP[3]);
        }
        for (int i = 0; i < regions.length; i++) {
            for (int j = i + 1; j < regions.length; j++) {
                check("buttons " + i + " and " + j + " do not overlap",
                        !overlap(regions[i], regions[j]));
            }
        }
        check("the room is not a button",
                MouseMap.hit(MouseMap.W / 2, 300).kind() == MouseMap.Kind.SCENE);
        check("the band is not a button",
                MouseMap.hit(MouseMap.W / 2, 20).kind() == MouseMap.Kind.NONE);

        // The readout and the filament are drawn in the band and must not
        // be hit regions: they are read, not pressed.
        check("the readout is not clickable",
                MouseMap.hit(MouseMap.READOUT[0] + 10, MouseMap.READOUT[1] + 10)
                        .kind() == MouseMap.Kind.NONE);

        for (int n = 1; n <= 5; n++) {
            double[] r = MouseMap.nightRow(n);
            check("night " + n + "'s row is where the hit test says",
                    MouseMap.nightAt(r[0] + r[2] / 2, r[1] + r[3] / 2) == n);
        }
        check("a point below the last row is no night",
                MouseMap.nightAt(600, 700) == 0);
    }

    static boolean overlap(double[] a, double[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    // ------------------------------------------------------------- the week

    /**
     * The phone build.
     *
     * <p>Two different things, and they fail for different reasons.
     *
     * <p><b>Is it current?</b> A generated file that has gone stale is worse
     * than no file: it is a second copy of the game quietly disagreeing with
     * the first. So the page is regenerated and compared rather than
     * spot-checked -- the same check every other ported game carries.
     *
     * <p><b>Is it the same game?</b> A staleness check proves the file matches
     * its generator and says nothing about whether the generator is right. The
     * failure that would otherwise be silent is a rule that lives in the page
     * as a literal and has stopped matching the engine -- so every number the
     * night is made of is asserted to be the engine's own value, and the four
     * night tables are asserted to be the engine's own tables.
     *
     * <p>What is <i>not</i> checked here is the RNG. The page reproduces
     * {@code java.util.Random} in BigInt so the same seed deals the same night
     * in both builds, and that was verified by hand against the desktop for
     * three seeds -- it is not checkable from Java without a JavaScript
     * engine, and the alternative (a page that deals its own nights) would
     * make the two builds different games with the same rules.
     */
    static void phone() {
        section("the phone build");

        Path out = Path.of("web", "fnaf7.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
            return;
        }
        String page;
        try {
            page = Files.readString(out);
        } catch (Exception e) {
            check("web/fnaf7.html can be read", false);
            return;
        }
        try {
            check("web/fnaf7.html is current -- regenerate it with aside.games.fnaf7.WebShift",
                    aside.games.fnaf7.WebShift.html().equals(page));
        } catch (Exception e) {
            check("web/fnaf7.html is current -- regenerate it with aside.games.fnaf7.WebShift",
                    false);
        }

        // The rules, as numbers. A port whose warm-up is 0.4 rather than 0.45
        // is a port that plays differently and looks identical.
        check("the page carries the light's warm-up",
                page.contains("lightWarm: " + aside.games.fnaf7.WebShift.num(Shift.LIGHT_WARM)));
        check("the page carries the filament's budget",
                page.contains("lightMax: " + aside.games.fnaf7.WebShift.num(Shift.LIGHT_MAX)));
        check("the page carries the filament's cooling",
                page.contains("lightCool: " + aside.games.fnaf7.WebShift.num(Shift.LIGHT_COOL)));
        check("the page carries the bar's crossing time",
                page.contains("barMove: " + aside.games.fnaf7.WebShift.num(Shift.BAR_MOVE)));
        check("the page carries how long it stands at the door",
                page.contains("strike: " + aside.games.fnaf7.WebShift.num(Shift.STRIKE)));
        check("the page carries the record's threshold",
                page.contains("sure: " + aside.games.fnaf7.WebShift.num(Shift.SURE)));

        // The four night tables, which are the whole of the difficulty ramp.
        for (String t : new String[]{"away", "approach", "explore", "memory"}) {
            String want = aside.games.fnaf7.WebShift.table(
                    switch (t) {
                        case "away" -> 0;
                        case "approach" -> 1;
                        case "explore" -> 2;
                        default -> 3;
                    });
            check("the page carries the " + t + " table", page.contains(t + ": " + want));
        }

        // And the cast, which is the one thing the player reads before the
        // night starts.
        for (int n = 1; n <= 5; n++) {
            Unit u = Unit.forNight(n);
            check("the page carries night " + n + "'s unit, " + u.name(),
                    page.contains(u.name()) && page.contains(u.note()));
        }
    
        // The sound. The ears are the one free channel in this game, and the engine says what they
        // are worth: "an arrival you can place is an arrival you can answer without
        // spending a look -- and a look is the only thing keeping the record sharp." A
        // silent port hands the player the light and takes away the reason the light is a
        // decision.
        check("the page carries the shared synthesiser",
                page.contains("function voice(") && page.contains("function sfx("));
        check("the page has a voice for the at_door cue",
                page.contains("case \"at_door\""));
        check("the page has a voice for the bar_move cue",
                page.contains("case \"bar_move\""));
        check("the page has a voice for the bar_set cue",
                page.contains("case \"bar_set\""));
        check("the page has a voice for the caught cue",
                page.contains("case \"caught\""));
        check("the page has a voice for the chime_6am cue",
                page.contains("case \"chime_6am\""));
        check("the page has a voice for the light_blown cue",
                page.contains("case \"light_blown\""));
        check("the page has a voice for the light_off cue",
                page.contains("case \"light_off\""));
        check("the page has a voice for the light_on cue",
                page.contains("case \"light_on\""));
        check("the page has a voice for the light_ready cue",
                page.contains("case \"light_ready\""));
        check("the page has a voice for the repel cue",
                page.contains("case \"repel\""));
        check("the page has a voice for the step_left* cues",
                page.contains("startsWith(\"step_left\")"));
        check("the page has a voice for the step_right* cues",
                page.contains("startsWith(\"step_right\")"));
}

    static void survival() {
        section("the week");
        int seeds = Integer.parseInt(System.getProperty("seeds", "300"));

        System.out.printf("  %-9s %5s %5s %5s %5s %5s %6s%n",
                "policy", "n1", "n2", "n3", "n4", "n5", "week");
        double[][] table = new double[Bot.Policy.values().length][5];
        for (Bot.Policy p : Bot.Policy.values()) {
            for (int n = 1; n <= 5; n++) table[p.ordinal()][n - 1] = Bot.survival(p, n, seeds);
            double[] row = table[p.ordinal()];
            System.out.printf("  %-9s %4.0f%% %4.0f%% %4.0f%% %4.0f%% %4.0f%% %5.0f%%%n",
                    p, row[0] * 100, row[1] * 100, row[2] * 100,
                    row[3] * 100, row[4] * 100, Bot.week(p, seeds) * 100);
        }
        for (int n = 1; n <= 5; n++) {
            int[] k = Bot.tally(Bot.Policy.PRO, n, seeds);
            System.out.printf("  PRO n%d  caught %3d   survived %3d   repels/seed %d%n",
                    n, k[0], k[1], k[2]);
        }

        double idle = Bot.week(Bot.Policy.IDLE, seeds);
        check("doing nothing is hopeless (" + Math.round(idle * 100) + "%)", idle < 0.05);
        check("and so is watching one side forever",
                Bot.week(Bot.Policy.STARE, seeds) < 0.05);
        check("and so is listening without looking",
                Bot.week(Bot.Policy.LISTEN, seeds) < 0.10);

        // THE LADDER. The policy that plays the record must beat the policy
        // that only reacts to what it can see, and both must beat doing
        // nothing.
        double pro = Bot.week(Bot.Policy.PRO, seeds);
        double react = Bot.week(Bot.Policy.REACT, seeds);
        check("playing the record beats reacting to the lamp",
                pro > react);
        check("reacting to the lamp beats doing nothing", react > idle + 0.30);
        check("the competent policy is not free (" + Math.round(pro * 100) + "%)",
                pro < 0.90);

        // The week gets harder, and it is asserted against PRO -- the
        // policy that actually plays the game. FNAF 5 learned this twice:
        // a check pointed at "the competent player" is really pointed at
        // an assumption about which policy that is.
        double[] row = table[Bot.Policy.PRO.ordinal()];
        for (int n = 0; n < 4; n++) {
            check("the week does not get easier: night " + (n + 1) + " -> " + (n + 2)
                            + " (" + Math.round(row[n] * 100) + "% -> "
                            + Math.round(row[n + 1] * 100) + "%)",
                    row[n + 1] <= row[n] + 0.06);
        }
        check("and the last night is harder than the first",
                row[4] < row[0] - 0.25);
    }
}
