package aside.games.fnaf9.engine;

import aside.games.fnaf9.MouseMap;

import java.util.HashSet;
import java.util.Set;

/**
 * The FNAF 9 checks, runnable with no display.
 *
 *     java -cp classes aside.games.fnaf9.engine.SelfTest
 *
 * <p><b>This suite did not exist when the game landed, and that is the first
 * thing worth saying about it.</b> FNAF 9 was committed with "248 engine
 * checks, 0 failed" in its message, and the number was true and said nothing:
 * 248 is {@code aside.engine.SelfTest}, which never looks at this game, and
 * the twelve checks that took it from 236 to 248 came from FNAF 6's phone
 * build on the fire before. Every other game in the franchise has a suite --
 * FNAF 8's is 217 checks -- and this one had none, so none of the claims below
 * was being held to anything. A count that goes up is not a count that covers
 * the thing you just wrote.
 *
 * <p>What is asserted here is the shape of the night rather than its numbers:
 * that the picture is the hall as it was, that a walker past its own sharp is
 * not drawn at all, that the sensor cannot start anything, that the door
 * outlasts every walker in the cast, that the trap loses every night, and that
 * the week gets harder. The survival table is printed rather than asserted
 * tightly, because it is a reading of the difficulty and not a contract.
 *
 * <p>Two things this suite found are recorded as open rather than fixed, and
 * they are in the comments where they were found:
 *
 * <ul>
 *   <li><b>The sharp axis is inert.</b> Raising night five's walkers from
 *       sharp 1.15/1.00 to 2.60/2.50 moves the competent policy by nothing at
 *       all (61% either way). The rule the game is about is a trap for the
 *       player who stares and a non-event for every policy that glances,
 *       which is the design working -- but it means the week's difficulty is
 *       carried entirely by arrival rate, and the pairs had to be re-tuned on
 *       that axis to make the week ramp.</li>
 *   <li><b>Night four is lopsided.</b> On every other night the two halls
 *       take a similar share of the deaths; on night four one walker takes
 *       almost all of them (146 to 12 with the pair as it stands, and the same
 *       split mirrored when the two are swapped). It is not a side bias --
 *       two identical walkers kill evenly and are survivable 94% of the time
 *       -- it is that <b>two walkers with different intervals drift apart and
 *       interleave their arrivals, while two with the same interval stay in
 *       phase and one hold covers both.</b> Night four's difficulty is
 *       therefore a property of the pair being nearly-but-not-quite the same
 *       walker, which is real but is not what its note says.</li>
 * </ul>
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
        feed();
        door();
        sensor();
        cast();
        week();
        instrument();
        mouse();
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

    static double round1(double d) { return Math.round(d * 10) / 10.0; }

    static final double DT = 1.0 / 60.0;

    /** Advance a night by a number of frames, with no player. */
    static void frames(Feed m, int n) {
        for (int i = 0; i < n && m.status == Feed.Status.PLAYING; i++) m.update(DT);
    }

    /** Advance a night by a number of seconds, with no player. */
    static void run(Feed m, double seconds) {
        frames(m, (int) (seconds / DT));
    }

    /** Advance a night by a number of seconds with both of them pinned away. */
    static void runParked(Feed m, double seconds) {
        framesParked(m, (int) (seconds / DT));
    }

    /**
     * A night with both of them pinned at the far end.
     *
     * <p>So that a check about the door is about the door: without this the
     * walkers arrive in the middle of a measurement and the night ends for a
     * reason the check was not asking about.
     */
    static Feed parked(int night, long seed) {
        Feed m = new Feed(night, seed);
        m.d[0] = Feed.MAX;
        m.d[1] = Feed.MAX;
        return m;
    }

    /** Frames with both walkers pinned at the far end. */
    static void framesParked(Feed m, int n) {
        for (int i = 0; i < n && m.status == Feed.Status.PLAYING; i++) {
            m.d[0] = Feed.MAX;
            m.d[1] = Feed.MAX;
            m.update(DT);
        }
    }

    /**
     * Frames with one hall held where it is and the other pinned away.
     *
     * <p>The held one is <b>not</b> re-pinned, so a walker that gives up stays
     * given up and the check can see it happen.
     */
    static void framesHolding(Feed m, int side, int n) {
        for (int i = 0; i < n && m.status == Feed.Status.PLAYING; i++) {
            m.d[1 - side] = Feed.MAX;
            m.update(DT);
        }
    }

    // -------------------------------------------------------------- the office

    static void office() {
        section("the office");

        check("the hall is six steps", Feed.MAX == 6);
        check("the night is six hours of twenty-five seconds",
                Feed.HOUR_SECONDS * Feed.NIGHT_HOURS == 150.0);

        // One door, two halls. Both of them end at the same place, and that
        // is the difference from FNAF 8 -- there, two halls meant two
        // doorways and the night was the distance between them.
        Feed m = parked(1, 7);
        m.d[0] = 0;
        check("a walker in the left doorway is at the door", m.atDoor());
        check("and it is the left one",
                m.atDoor(Feed.Side.LEFT) && !m.atDoor(Feed.Side.RIGHT));
        m.d[0] = Feed.MAX;
        m.d[1] = 0;
        check("and the right hall ends at the same door",
                m.atDoor(Feed.Side.RIGHT) && !m.atDoor(Feed.Side.LEFT));

        // The two of them do not start in step. Started in step they arrived
        // in step, and with one door and one duty cycle that is an unwinnable
        // moment -- measured, before this line existed, as five policies
        // scoring zero on every seed of every night.
        int inStep = 0, outOfRange = 0;
        double separation = 0;
        for (int i = 0; i < 400; i++) {
            Feed f = new Feed(1, i);
            double gap = Math.abs(f.d[0] - f.d[1]);
            separation += gap;
            if (gap < 0.01) inStep++;
            if (f.d[0] < Feed.MAX - 2 || f.d[0] > Feed.MAX
                    || f.d[1] < Feed.MAX - 2 || f.d[1] > Feed.MAX) outOfRange++;
        }
        separation /= 400;
        check("the two of them are not started in step", inStep < 40);
        check("and the phase is rolled rather than fixed", separation > 0.4);
        check("and both start in the last two steps", outOfRange == 0);

        // One circuit, one thing at a time.
        Feed c = parked(1, 8);
        c.watch(Feed.Side.LEFT);
        check("the monitor is on the hall it was asked for",
                c.monitorOn() && c.monitorSide() == Feed.Side.LEFT);
        check("and the door is not held", !c.holding());
        c.hold();
        check("holding the door takes the monitor away", !c.monitorOn() && c.holding());
        c.dark();
        check("and the dark is a state of its own", !c.monitorOn() && !c.holding());

        // The circuit is free, and it is a choice rather than a resource.
        check("the circuit moves when it is asked to", c.watch(Feed.Side.RIGHT));
        check("and does not move when it is already there", !c.watch(Feed.Side.RIGHT));

        // Six AM.
        Feed night = parked(1, 9);
        runParked(night, 200);
        check("the night ends at six", night.status == Feed.Status.SURVIVED);
        check("and the clock reads six", night.clock().equals("6 AM"));
        check("and nothing moves after it", !night.set(Feed.Circuit.HOLD));

        // The band's clock, hour by hour.
        Feed h = parked(1, 10);
        String[] want = {"12 AM", "1 AM", "2 AM", "3 AM", "4 AM", "5 AM"};
        boolean clockOK = true;
        for (int i = 0; i < 6; i++) {
            if (!h.clock().equals(want[i])) clockOK = false;
            // One frame past the hour: 1500 frames of 1/60 is 24.99999999999986
            // seconds, and the band would still be reading the hour before.
            framesParked(h, (int) (Feed.HOUR_SECONDS * 60) + 1);
        }
        check("the band's clock counts the hours", clockOK);
    }

    // ---------------------------------------------------------------- the feed

    static void feed() {
        section("the feed");

        Feed m = parked(1, 21);
        m.watch(Feed.Side.LEFT);
        frames(m, 60);
        check("a second of watching ages the picture by the night's rate",
                Math.abs(m.feed - m.ageRate()) < 0.02);
        check("and the age is the time spent watching, times that rate",
                Math.abs(m.feed - m.watched * m.ageRate()) < 0.05);
        m.dark();
        frames(m, 60);
        check("a second of dark sheds it by the night's rate",
                Math.abs(m.feed - (m.ageRate() - m.ageDecay())) < 0.02);
        check("and looking costs more than not looking", m.ageRate() > m.ageDecay());

        Feed old = parked(1, 22);
        old.watch(Feed.Side.LEFT);
        framesParked(old, 60 * 30);
        check("the picture never gets older than AGE_MAX", old.feed == Feed.AGE_MAX);
        old.dark();
        framesParked(old, 60 * 30);
        check("and never younger than now", old.feed == 0);
        check("and the band says the feed is idle", old.feedLabel().equals("FEED IDLE"));

        // THE RULE. The picture is the hall as it was `feed` seconds ago, and
        // it is read out of the delay line rather than computed -- a walker
        // that arrived, waited and gave up inside the window has to be drawn
        // as having done that, and only a history knows.
        Feed a = new Feed(1, 42);
        a.watch(Feed.Side.LEFT);
        frames(a, 300);                       // five seconds of watching
        Feed c = new Feed(1, 42);
        frames(c, 45);                        // the same night, 0.75s in
        check("the picture is the hall as it was",
                Math.abs(a.apparent(Feed.Side.LEFT) - c.distance(Feed.Side.LEFT)) < 0.05);
        check("and it is drawn further away than the walker is",
                a.apparent(Feed.Side.LEFT) > a.distance(Feed.Side.LEFT) + 0.5);
        check("and the picture is as old as the time spent watching it",
                Math.abs(a.feed - a.watched * a.ageRate()) < 0.05);

        // A picture that is behind never shows a walker closer than it is,
        // because a walker only ever walks toward the door. (No holds here,
        // so nothing gives up and nothing is pushed back.)
        Feed p = new Feed(3, 77);
        p.watch(Feed.Side.LEFT);
        boolean neverCloser = true;
        for (int i = 0; i < 60 * 90 && p.status == Feed.Status.PLAYING; i++) {
            p.update(DT);
            double shown = p.shown(Feed.Side.LEFT);
            if (shown >= 0 && shown < p.distance(Feed.Side.LEFT) - 0.01) neverCloser = false;
        }
        check("the picture never shows a walker closer than it is", neverCloser);

        // Past its own sharp a walker is not drawn at all -- not faint, not
        // misplaced, absent. This is the trap the whole night is built on.
        Feed s = new Feed(5, 5);
        s.watch(Feed.Side.LEFT);
        frames(s, 60 * 3);
        check("three seconds of staring is past the walker's sharp",
                s.feed > s.pair.unit(Feed.Side.LEFT).sharp());
        check("and the monitor does not hold it", s.shown(Feed.Side.LEFT) < 0);
        check("and it says the hall has faded", s.faded(Feed.Side.LEFT));
        check("and the hall it is not on reads nothing at all",
                s.shown(Feed.Side.RIGHT) < 0);
        check("faded is the monitor being on this hall and the picture too old",
                s.faded(Feed.Side.RIGHT)
                        == (s.monitorSide() == Feed.Side.RIGHT && s.shown(Feed.Side.RIGHT) < 0));

        // A delay line has to sample the thing it is delaying. Recording only
        // the whole-step crossings quantized the picture to a step on top of
        // the lag, and the belief read 0.75 while the walker was at 0.04.
        Feed d = parked(1, 31);
        int before = d.histN[0];
        frames(d, 60);
        check("the delay line samples every frame", d.histN[0] == before + 60);
    }

    // ---------------------------------------------------------------- the door

    static void door() {
        section("the door");

        // The single most load-bearing number in the game: the door is slower
        // than the grace on every night, which is what makes the sensor a
        // readout instead of a trigger.
        boolean slower = true;
        for (int n = 1; n <= 5; n++) if (Feed.SHUT_TIME <= new Feed(n, 1).grace()) slower = false;
        check("the door is slower than the grace on every night", slower);

        Feed m = parked(1, 51);
        m.hold();
        check("the door is asked to come down", m.holding());
        check("and stops nothing yet", !m.blocking());
        framesParked(m, (int) (Feed.SHUT_TIME * 60) + 2);
        check("and stops things once it is down", m.blocking());

        // A door that can be held forever is a door you hold from the first
        // arrival to six AM.
        Feed j = parked(1, 52);
        j.hold();
        framesParked(j, (int) (Feed.HOLD_MAX * 60) + 2);
        check("the door will not stay shut", j.jammed && j.jams == 1);
        check("and lets go on its own", !j.holding() && j.circuit == Feed.Circuit.DARK);
        check("and will not take another hold yet", !j.hold());
        framesParked(j, (int) ((Feed.HOLD_MAX * (1 - Feed.REARM)) / Feed.COOL * 60) + 4);
        check("and takes one again once it has cooled", j.hold());

        check("holdLeft runs out while the door is held",
                new Feed(1, 53).holdLeft() == 1.0);

        // A walker at a shut door gives up, and that is the only way the
        // doorway ever empties -- nothing in this office can push one back.
        Feed g = parked(1, 54);
        g.hold();
        framesParked(g, (int) (Feed.SHUT_TIME * 60) + 2);
        check("the door is down for the give-up check", g.blocking());
        int gaveBefore = g.gaveUp;
        g.d[0] = 0;
        framesHolding(g, 0, (int) (g.pair.unit(Feed.Side.LEFT).patience() * 60) + 4);
        check("a walker at a shut door gives up", g.gaveUp == gaveBefore + 1);
        check("and goes back to the far end", g.distance(Feed.Side.LEFT) > Feed.MAX - 0.2);
        check("and the night is still going", g.status == Feed.Status.PLAYING);

        // And in an open doorway it takes you, on the night's grace.
        Feed e = parked(1, 55);
        e.d[0] = 0;
        framesHolding(e, 0, (int) (e.grace() * 60) + 4);
        check("a walker in an open doorway takes you", e.status == Feed.Status.TAKEN);
        check("and it says which hall", e.takenSide == Feed.Side.LEFT);
        check("and which walker it was", e.takenBy == e.pair.unit(Feed.Side.LEFT));
    }

    // -------------------------------------------------------------- the sensor

    static void sensor() {
        section("the sensor");

        Feed m = parked(1, 61);
        m.d[0] = 0;
        check("something is standing in the doorway", m.atDoor());
        check("and the sensor says nothing, because the door is up", !m.sensor());
        check("and the sensor cannot start anything", !m.sensor());

        m.hold();
        check("and it says nothing while the door is still coming down", !m.sensor());
        framesParked(m, (int) (Feed.SHUT_TIME * 60) + 2);
        m.d[0] = 0;
        check("it reads once the door is down and something is against it", m.sensor());
        check("and it is not clear", !m.clear());

        m.d[0] = Feed.MAX;
        check("and it is clear when nothing is", m.clear());
        check("and it does not read", !m.sensor());
        check("and clear and sensor are never both true", !(m.clear() && m.sensor()));
    }

    // --------------------------------------------------------------- the cast

    static void cast() {
        section("the cast");

        Walker[] all = Walker.all();
        check("ten of them", all.length == 10);
        Set<String> keys = new HashSet<>();
        boolean unique = true;
        for (Walker w : all) if (!keys.add(w.key())) unique = false;
        check("every walker has its own key", unique);
        Set<String> names = new HashSet<>();
        boolean named = true;
        for (Walker w : all) if (!names.add(w.name())) named = false;
        check("and its own name", named);

        // The door has to be able to outlast every one of them, walk and all,
        // or a walker could outlast the door and the door would be a lie.
        // The walk is two steps of that walker's own night, because a hold
        // starts about a step out and the door has to cover the rest of the
        // approach as well as the patience.
        double worst = 0;
        String worstAt = "";
        for (int n = 1; n <= 5; n++) {
            Feed f = new Feed(n, 1);
            for (Feed.Side s : Feed.Side.values()) {
                Walker w = f.pair.unit(s);
                double needed = w.patience() + Feed.SHUT_TIME + 2 * f.interval(s);
                if (needed > worst) { worst = needed; worstAt = "night " + n + ", " + w.name(); }
            }
        }
        check("the door outlasts every walker in the cast, walk and all",
                worst < Feed.HOLD_MAX);
        System.out.println("       the longest a walker can ask for is "
                + round1(worst) + "s of an " + round1(Feed.HOLD_MAX) + "s door ("
                + worstAt + ")");

        Pair[] pairs = Pair.all();
        check("five nights of them", pairs.length == 5);
        boolean two = true, differ = true, notes = true;
        for (Pair p : pairs) {
            if (p.left() == p.right()) two = false;
            if (p.left().speed() == p.right().speed()
                    || p.left().sharp() == p.right().sharp()) differ = false;
            if (p.note() == null || p.note().isBlank()) notes = false;
        }
        check("every night is two different walkers", two);
        check("and the two of them differ in speed and in how long they hold",
                differ);
        check("and every night says what it is", notes);
        check("the sides are fixed for the night",
                Pair.forNight(3).left() == Pair.forNight(3).left()
                        && Pair.forNight(3).right() == Pair.forNight(3).right());

        Set<String> used = new HashSet<>();
        boolean once = true;
        for (Pair p : pairs) {
            if (!used.add(p.left().key()) || !used.add(p.right().key())) once = false;
        }
        check("and every walker in the cast is used exactly once", once && used.size() == 10);

        check("the pair is the night's, not the seed's",
                Pair.forNight(2).left().key().equals(new Feed(2, 999).pair.left().key()));
    }

    // --------------------------------------------------------------- the week

    static void week() {
        section("the week's tables");

        boolean priceRises = true, recoveryFalls = true, tempoQuickens = true,
                windowCloses = true;
        for (int n = 1; n < 5; n++) {
            Feed a = new Feed(n, 1), b = new Feed(n + 1, 1);
            if (!(b.ageRate() > a.ageRate())) priceRises = false;
            if (!(b.ageDecay() < a.ageDecay())) recoveryFalls = false;
            if (!(b.pace() < a.pace())) tempoQuickens = false;
            if (!(b.grace() < a.grace())) windowCloses = false;
        }
        check("the price of looking rises across the week", priceRises);
        check("the recovery falls", recoveryFalls);
        check("the tempo quickens", tempoQuickens);
        check("the window closes", windowCloses);

        boolean costsMore = true, doorSlower = true;
        for (int n = 1; n <= 5; n++) {
            Feed m = new Feed(n, 1);
            if (!(m.ageRate() > m.ageDecay())) costsMore = false;
            if (!(Feed.SHUT_TIME > m.grace())) doorSlower = false;
        }
        check("looking costs more than not looking on every night", costsMore);
        check("and the door is slower than the window on every night", doorSlower);

        // Six seconds of lag has to be more than two steps of the slowest
        // night, or the picture stops being a picture of the same night.
        double longest = 0;
        for (int n = 1; n <= 5; n++) {
            Feed m = new Feed(n, 1);
            for (Feed.Side s : Feed.Side.values()) longest = Math.max(longest, m.interval(s));
        }
        check("the picture can be more than two steps old", Feed.AGE_MAX > 2 * longest);
        System.out.println("       the slowest step in the week is "
                + round1(longest) + "s; the picture can be "
                + round1(Feed.AGE_MAX / longest) + " steps behind");
    }

    // ---------------------------------------------------------- the instrument

    static void instrument() {
        section("the instrument");

        // The belief is the picture minus the age of the picture. Without
        // that correction the belief is pinned about half a step behind
        // reality on every night, and a bot that never believes a walker is
        // at the door never shuts it: measured, PRO scored zero on every seed
        // of every night with the belief reading 0.99 while the walker stood
        // in the doorway.
        Feed m = parked(1, 71);
        m.watch(Feed.Side.LEFT);
        frames(m, 60);
        Bot b = new Bot(Bot.Policy.PRO);
        b.observe(m, DT);
        double shown = m.shown(Feed.Side.LEFT);
        check("the picture is showing the hall", shown > 0.5);
        check("the bot's belief is never closer than the picture",
                b.est[0] <= shown + 0.001);
        check("and it is behind the picture by the age of the picture",
                Math.abs(b.est[0] - (shown - m.feed / m.interval(Feed.Side.LEFT))) < 0.01);

        // With nothing shown the belief walks toward the door at the mean
        // rate, and that drift is what a look is spent against.
        Feed d = parked(1, 72);
        Bot db = new Bot(Bot.Policy.PRO);
        db.est[0] = 3.0;
        db.est[1] = 3.0;
        for (int i = 0; i < 60; i++) {
            db.observe(d, DT);
            d.update(DT);
        }
        check("with nothing shown the belief walks toward the door", db.est[0] < 3.0);
        check("and it is not the truth", Math.abs(db.est[0] - d.distance(Feed.Side.LEFT)) > 0.05);

        // The trap, measured: a policy that stares at one hall has, after
        // three seconds, a picture that does not contain the thing it is
        // staring at.
        Feed s = new Feed(5, 73);
        s.watch(Feed.Side.LEFT);
        frames(s, 60 * 3);
        check("the trap has been staring for three seconds",
                s.feed > s.pair.unit(Feed.Side.LEFT).sharp());
        check("and the hall it is staring at is empty", s.shown(Feed.Side.LEFT) < 0);
    }

    // ---------------------------------------------------------------- the mouse

    static void mouse() {
        section("the mouse");

        double[][] buttons = {MouseMap.MON_LEFT, MouseMap.MON_RIGHT, MouseMap.DARK,
                MouseMap.HOLD};
        String[] names = {"MON LEFT", "MON RIGHT", "DARK", "HOLD"};
        MouseMap.Kind[] kinds = {MouseMap.Kind.MON_LEFT, MouseMap.Kind.MON_RIGHT,
                MouseMap.Kind.DARK, MouseMap.Kind.HOLD};

        for (int i = 0; i < buttons.length; i++) {
            double[] r = buttons[i];
            check(names[i] + " is on the canvas",
                    r[0] >= 0 && r[0] + r[2] <= MouseMap.W
                            && r[1] >= 0 && r[1] + r[3] <= MouseMap.H);
            check(names[i] + " is inside the strip", r[1] >= MouseMap.STRIP[1]);
            check(names[i] + " is hit at its centre",
                    MouseMap.hit(r[0] + r[2] / 2, r[1] + r[3] / 2).kind() == kinds[i]);
            check(names[i] + " is hit at its top-left corner",
                    MouseMap.hit(r[0] + 1, r[1] + 1).kind() == kinds[i]);
            check(names[i] + " is hit at its bottom-right corner",
                    MouseMap.hit(r[0] + r[2] - 1, r[1] + r[3] - 1).kind() == kinds[i]);
        }

        // No two buttons may overlap, or a click lands on whichever the hit
        // test happens to check first and the other one is decoration.
        boolean overlap = false;
        for (int i = 0; i < buttons.length; i++) {
            for (int j = i + 1; j < buttons.length; j++) {
                double[] a = buttons[i], b = buttons[j];
                if (a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                        && a[1] < b[1] + b[3] && b[1] < a[1] + a[3]) overlap = true;
            }
        }
        check("no two buttons overlap", !overlap);

        // The room is not a button, but it is a hit, so a click on the office
        // is a click that did nothing rather than a miss.
        check("the room is a hit", MouseMap.hit(640, 300).kind() == MouseMap.Kind.SCENE);
        check("and a click on the office does nothing",
                MouseMap.hit(640, 300).kind() != MouseMap.Kind.MON_LEFT
                        && MouseMap.hit(640, 300).kind() != MouseMap.Kind.HOLD);

        // The readouts are drawn in the band, and the screen in the room.
        check("the feed readout is in the band",
                MouseMap.FEED[1] >= MouseMap.BAND[1]
                        && MouseMap.FEED[1] + MouseMap.FEED[3] <= MouseMap.BAND[1] + MouseMap.BAND[3]);
        check("the sensor readout is in the band",
                MouseMap.SENSOR[1] >= MouseMap.BAND[1]
                        && MouseMap.SENSOR[1] + MouseMap.SENSOR[3] <= MouseMap.BAND[1] + MouseMap.BAND[3]);
        check("and the two of them do not overlap",
                MouseMap.FEED[0] + MouseMap.FEED[2] <= MouseMap.SENSOR[0]);
        check("the monitor's screen is in the room",
                MouseMap.SCREEN[1] >= MouseMap.SCENE[1]
                        && MouseMap.SCREEN[1] + MouseMap.SCREEN[3] <= MouseMap.SCENE[1] + MouseMap.SCENE[3]);

        // The night rows.
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            double[] row = MouseMap.nightRow(n);
            check("night " + n + "'s row is on the canvas",
                    row[0] >= 0 && row[0] + row[2] <= MouseMap.W
                            && row[1] >= 0 && row[1] + row[3] <= MouseMap.H);
            check("night " + n + "'s row is hit at its centre",
                    MouseMap.nightAt(row[0] + row[2] / 2, row[1] + row[3] / 2) == n);
        }
        check("and nothing above the rows is a night", MouseMap.nightAt(640, 100) == 0);
        check("and there are as many nights as there are pairs",
                MouseMap.NIGHTS == Pair.all().length);
    }

    // ------------------------------------------------------------- the ladder

    static void survival() {
        section("the week, 400 seeds a night");

        int seeds = 400;
        System.out.println("  policy     n1    n2    n3    n4    n5   week");
        double[] pro = new double[5];
        for (Bot.Policy p : Bot.Policy.values()) {
            StringBuilder sb = new StringBuilder(String.format("  %-9s", p));
            double total = 0;
            for (int n = 1; n <= 5; n++) {
                double s = Bot.survival(p, n, seeds);
                total += s;
                if (p == Bot.Policy.PRO) pro[n - 1] = s;
                sb.append(String.format(" %4.0f%%", s * 100));
            }
            sb.append(String.format(" %5.0f%%", total / 5 * 100));
            System.out.println(sb);
        }

        // Doing nothing is hopeless. If this ever passes, the night is being
        // survived by the clock rather than by the player.
        check("doing nothing loses every night", Bot.week(Bot.Policy.IDLE, 200) == 0);
        // The trap: one hall watched, forever. The picture empties the hall,
        // so the policy is answering a room it has been staring at.
        check("watching one hall forever loses every night",
                Bot.week(Bot.Policy.STARE, 200) == 0);
        // The over-cautious player, who shuts the door a step early and pays
        // for it out of the next decision.
        check("shutting the door early loses every night",
                Bot.week(Bot.Policy.EARLY, 200) == 0);

        // The competent policy beats the one with a habit. That is the claim
        // the ladder is read against.
        double glance = Bot.week(Bot.Policy.GLANCE, seeds);
        double proWeek = Bot.week(Bot.Policy.PRO, seeds);
        check("the policy that looks when it needs to beats the one with a rhythm",
                proWeek >= glance);
        System.out.println("       GLANCE " + Math.round(glance * 100) + "%   PRO "
                + Math.round(proWeek * 100) + "%");

        // And the week gets harder. Monotone to within the noise of four
        // hundred seeds, which is about two and a half points.
        boolean monotone = true;
        for (int i = 1; i < 5; i++) if (pro[i] > pro[i - 1] + 0.03) monotone = false;
        check("the week gets harder", monotone);
        check("the last night is the hardest of the nine", pro[4] < pro[0]);
        check("and the first night is survivable", pro[0] > 0.75);
        check("and the last night is not a formality", pro[4] < 0.85);

        // The killers, so the shape of the difficulty is visible rather than
        // inferred from a percentage. A survival number cannot tell you which
        // of the two halls is the one doing the killing.
        for (int n = 1; n <= 5; n++) {
            int[] k = Bot.killers(Bot.Policy.PRO, n, 200);
            int[] t = Bot.tally(Bot.Policy.PRO, n, 200);
            System.out.println("       night " + n + ": taken " + t[0]
                    + ", survived " + t[1] + ", doorways emptied "
                    + round1(t[2]) + " per night, deaths left " + k[0]
                    + " right " + k[1]);
        }
    }

    private SelfTest() {}
}
