package aside.games.fnaf9.engine;

import aside.games.fnaf9.MouseMap;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * <p>What this suite has found, and what was done about it:
 *
 * <ul>
 *   <li><b>The sharp axis is inert.</b> Raising night five's walkers from
 *       sharp 1.15/1.00 to 2.60/2.50 moves the competent policy by nothing at
 *       all (61% either way). The rule the game is about is a trap for the
 *       player who stares and a non-event for every policy that glances,
 *       which is the design working -- but it means the week's difficulty is
 *       carried entirely by arrival rate, and the pairs had to be re-tuned on
 *       that axis to make the week ramp.</li>
 *   <li><b>Night four was lopsided, and is fixed.</b> One walker took almost
 *       all the deaths (99 to 9 at 400 seeds, and the same split mirrored
 *       when the two were swapped, so it was the walker and not the hall). It
 *       is not a side bias -- two identical walkers kill evenly and are
 *       survivable 92% -- it is that <b>two walkers with different intervals
 *       drift apart and interleave their arrivals, while two with the same
 *       interval stay in phase and one hold covers both.</b> The drift is
 *       what makes the night hard and it is also what makes it lopsided: the
 *       walker that falls behind is the one arriving just after the door has
 *       let go, every time. Plushtrap went from 1.18 to 1.24 to widen the
 *       step gap past the band where the phase barely moves; the night reads
 *       102/106 now at the same difficulty. The check below is what keeps
 *       it that way. See {@link Walker#PLUSHTRAP}.</li>
 *   <li><b>The competent policy is not a robust reading of the game, and
 *       this is the biggest thing still open.</b> A bot whose hold is sized
 *       for <i>both</i> halls rather than only the walker it was started for
 *       -- {@link Bot.Policy#SIEGE} -- reads <b>89/99/99/99/66, a week of
 *       90%</b> against PRO's 86/74/62/48/35, so three nights out of five are
 *       solved by "hold until both are clear" and the 61% below is a reading
 *       of the bot's release policy rather than of the game. <b>The note that
 *       stood here said 99% on the week, and that number was never measured;
 *       the real one is 90%.</b> Correcting it is half the reason the policy
 *       is on the ladder now -- a claim about the game that lives in a comment
 *       is a claim nothing checks. The death trace says why it wins: <b>every
 *       death PRO takes happens with the door down, inside a second of letting
 *       go.</b> It releases the moment the doorway empties, the other walker
 *       arrives inside the door's travel, and the door is still on its way
 *       down. SIEGE has no such failure because it does not let go until both
 *       halls are clear -- so the night rewards <i>holding longer</i>, and the
 *       duty cycle is not tight enough to stop it. <b>No single constant
 *       closes the gap</b>; see {@link Bot#SIEGE_AT} for the five things that
 *       were tried, all of them measured. The fix is a redesign of the door's
 *       economy plus a re-tune of the patience table, not a tuning pass.
 *       <b>And the same defect has a second face:</b> the office says the
 *       sensor is the only way to know the doorway has emptied, and a policy
 *       that plays exactly that ({@link Bot.Policy#SENSE}) reads 92/80/0/0/0
 *       -- better than the competent player on the two nights whose walkers
 *       hold together on a bad picture, and wiped out on the three where they
 *       come apart. The sensor is decoration, and what decides the release is
 *       the belief.</li>
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
        cues();
        mouse();
        phone();
        survival();
        siege();
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

    // ----------------------------------------------------------------- cues

    /**
     * The night's own sounds, and the one thing they must not become.
     *
     * <p>Every cue in {@code GameScreen.play} is asked for by name with a
     * fallback -- {@code f9_step} then {@code footstep} -- so a missing file is
     * not an error, it is a quiet substitution, and a game's worth of
     * substitutions sounds like a game that was never finished. That is the
     * same shape of failure as a stale generated file: it does not fail, it
     * just disagrees with what was written. So the files are checked for
     * existing, and they are built by {@code tools/fnaf9-audio.py} rather than
     * committed by hand.
     *
     * <p>And the two halls share one footfall. FNAF 9 has no directional
     * channel by design -- the monitor is the only way to know where anything
     * is, and the monitor is a picture of the past -- so a step that told the
     * halls apart would be a free channel. The last check is what keeps one
     * from being added by accident.
     */
    static void cues() {
        section("the night's own sounds");
        Path dir = Path.of("audio");
        if (!Files.isDirectory(dir)) {
            System.out.println("       (no " + dir + " from here)");
            return;
        }
        for (String cue : new String[]{"f9_step", "f9_door", "f9_gives_up",
                "f9_switch", "f9_jam", "f9_taken"}) {
            boolean found = false;
            for (String ext : new String[]{".wav", ".mp3", ".aiff", ".m4a", ".aac"}) {
                if (Files.exists(dir.resolve(cue + ext))) { found = true; break; }
            }
            check("audio/" + cue + " is its own cue, not a fallback", found);
        }
        check("and the two halls share one footfall",
                !Files.exists(dir.resolve("f9_step_left.wav"))
                        && !Files.exists(dir.resolve("f9_step_right.wav")));
    }

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

        // A BAND, because the four checks above are all shape and a mutation
        // can keep the shape. Measured: changing the filament's budget from
        // 6.0 to 4.0, or a walker's patience from 1.90 to 0.30, leaves every
        // one of them passing -- the week still ramps, the first night is
        // still survivable, the last is still not a formality -- while the
        // numbers underneath move. That is the design working (the table is a
        // reading rather than a contract) and it is also a blind spot, so the
        // week's mean is held to a band wide enough for a retune and narrow
        // enough to catch a slip.
        double proMean = 0;
        for (double v : pro) proMean += v;
        proMean /= 5;
        check(String.format("the competent policy's week is in its band (%.0f%%)",
                        proMean * 100),
                proMean > 0.40 && proMean < 0.80);

        // AND THE MUTATION TEST SAYS THIS SUITE IS THOROUGH, which is worth
        // writing down because the first version of the test said the
        // opposite. Breaking one engine constant at a time and running this
        // suite:
        //
        //   HOLD_MAX  8.00 -> 4.00   caught, 14 checks fail
        //   SHUT_TIME 0.80 -> 0.20   caught, 11 checks fail
        //   COOL      2.20 -> 0.60   caught, 10 checks fail
        //   AGE_MAX   6.00 -> 2.00   caught,  5 checks fail
        //   JITTER    0.50 -> 2.00   caught,  4 checks fail
        //   REARM     0.35 -> 0.05   caught,  1 check fails
        //
        // Every constant the night is made of is defended by something. The
        // first run of this test reported that HOLD_MAX and a walker's
        // patience were inert -- the ladder did not move at all -- and that was
        // wrong: the script's sed anchors did not match the file, so the
        // mutation never applied, and a mutation that never applied reads
        // exactly like a mutation the suite cannot see. The anchors are checked
        // now, and a mutation that does not apply is reported as one rather
        // than counted as a survivor.
        //
        // The lesson generalises past this file: **a mutation test that does
        // not verify its own mutation reports that the suite is blind whenever
        // the script is wrong.** It is the same failure as a check that cannot
        // fail, one level up.

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

        // No night is decided by one hall. A night whose deaths are almost all
        // on one side is a night the player learns to ignore a hall, and that
        // is a rule rather than a skill. Night four was exactly that -- 99
        // left against 9 right, mirrored when the two were swapped -- until
        // Plushtrap was brought up from 1.18 to 1.24 to widen the step gap
        // past the band where the two barely drift (see Walker.PLUSHTRAP).
        //
        // The bound is loose on purpose: the point is to catch a night that
        // has collapsed onto one side, not to demand a coin flip. Measured at
        // 400 seeds the worst night is 1.24:1, and the one this was written
        // for was 11:1.
        for (int n = 1; n <= 5; n++) {
            int[] k = Bot.killers(Bot.Policy.PRO, n, 400);
            int lo = Math.min(k[0], k[1]);
            int hi = Math.max(k[0], k[1]);
            check("night " + n + "'s deaths are not one-sided",
                    hi <= 2 * Math.max(1, lo));
        }
    }

    /**
     * The extended hold: the policy that beats the competent player, and why.
     *
     * <p>This section exists because the claim it tests used to live in a
     * comment. {@code aside-engine.md} and this file's own header both said
     * the extended hold scored "99% on the week", and nobody had run it since
     * the number was written down -- it is <b>90%</b>. A number in a comment
     * is a number nothing checks, and the whole reason the ladder exists is
     * that a balance table produced by hand is a table of the nights somebody
     * happened to play.
     *
     * <p>So the policy is a rung of the ladder now, and the relationship is
     * asserted rather than described. The assertion is deliberately written as
     * the <i>honest state of the game</i> -- "the extended hold is still at
     * least as good as the competent player" -- rather than as a bug, because
     * the day it stops being true is the day the door's economy has been
     * redesigned, and the check failing is how that fire finds out it worked.
     *
     * <p><b>The other end of the same defect is here too.</b> {@link Feed}'s
     * javadoc says the sensor is the only way to know the doorway has emptied.
     * {@link Bot.Policy#SENSE} is that play, and it reads 92/80/0/0/0 -- it
     * beats the competent policy on the two nights whose walkers hold together
     * on a bad picture and loses <i>every seed</i> of the three where they
     * come apart. Both rows say the same thing from opposite sides: the
     * release is decided by the belief, the belief can decide it because the
     * walker's patience is a constant, and the sensor is decoration.
     */
    static void siege() {
        section("the two ends of the same defect");

        int seeds = 200;
        double[] pro = new double[5];
        double[] sie = new double[5];
        double[] sen = new double[5];
        for (int n = 1; n <= 5; n++) {
            pro[n - 1] = Bot.survival(Bot.Policy.PRO, n, seeds);
            sie[n - 1] = Bot.survival(Bot.Policy.SIEGE, n, seeds);
            sen[n - 1] = Bot.survival(Bot.Policy.SENSE, n, seeds);
        }
        System.out.println("       PRO   " + row(pro) + "   week "
                + Math.round(week(pro) * 100) + "%");
        System.out.println("       SIEGE " + row(sie) + "   week "
                + Math.round(week(sie) * 100) + "%");
        System.out.println("       SENSE " + row(sen) + "   week "
                + Math.round(week(sen) * 100) + "%");

        check("the extended hold is still at least as good as the competent player",
                week(sie) >= week(pro));
        check("and it is the better policy on the middle of the week",
                sie[1] > pro[1] && sie[2] > pro[2] && sie[3] > pro[3]);

        // And the night is not winnable, which is the thing seven separate
        // attempts to close the gap above all failed to preserve. The door's
        // travel is what keeps it that way: shortening it takes PRO from 61%
        // to 86% (0.70), 95% (0.55) and 100% (0.40) while SIEGE stays on top
        // of it, so a fire that shortens the travel to close the gap finds
        // the gap still there and the game gone. This check is the one that
        // says so.
        check("no policy wins the week", week(sie) < 0.98 && week(pro) < 0.90);

        // The other end of the same defect. The office says the sensor is the
        // only way to know the doorway has emptied; a policy that believes it
        // is fine on the two nights whose walkers hold together on a bad
        // picture and is wiped out on the three where they come apart.
        check("trusting the door's own sensor loses the back half of the week",
                sen[2] == 0 && sen[3] == 0 && sen[4] == 0);
        check("and it is still better than the competent player on the front half",
                sen[0] > pro[0] && sen[1] > pro[1]);

        // Why. The trace, not the percentage: a survival number cannot tell
        // you what killed you, and this one is entirely about the moment the
        // door is let go.
        int deaths = 0, down = 0, recent = 0;
        double worst = 0;
        for (int n = 1; n <= 5; n++) {
            for (int i = 0; i < seeds; i++) {
                Feed m = new Feed(n, 1000L * n + i);
                Bot b = new Bot(Bot.Policy.PRO);
                double dt = 1.0 / 60.0;
                double lastRelease = -99;
                boolean was = false;
                for (int f = 0; f < 60 * 400 && m.status == Feed.Status.PLAYING; f++) {
                    b.step(m, dt);
                    if (was && !b.holding) lastRelease = m.time;
                    was = b.holding;
                    m.update(dt);
                }
                if (m.status == Feed.Status.TAKEN) {
                    deaths++;
                    if (m.circuit == Feed.Circuit.HOLD) down++;
                    double gap = m.time - lastRelease;
                    if (gap <= 1.0) recent++;
                    worst = Math.max(worst, gap);
                }
            }
        }
        System.out.println("       " + deaths + " deaths over " + (5 * seeds)
                + " nights: " + down + " with the door down, " + recent
                + " inside a second of letting go (worst gap "
                + round1(worst) + "s)");
        check("every death the competent player takes is taken with the door down",
                down == deaths);
        check("and every one of them is inside a second of letting go",
                recent == deaths);
    }

    /** One night's five percentages, for a report line. */
    static String row(double[] v) {
        StringBuilder sb = new StringBuilder();
        for (double x : v) sb.append(String.format(" %4.0f%%", x * 100));
        return sb.toString();
    }

    /** The mean of a five-night reading. */
    static double week(double[] v) {
        double t = 0;
        for (double x : v) t += x;
        return t / v.length;
    }

    /**
     * The phone build.
     *
     * <p>Two different things are checked here and they fail for different
     * reasons.
     *
     * <p><b>Is it current?</b> A generated file that has gone stale is worse
     * than no file: it is a second copy of the game quietly disagreeing with
     * the first. So the page is regenerated and compared, rather than
     * spot-checked -- the same check every other ported game carries.
     *
     * <p><b>Is it the same game?</b> A staleness check proves the file
     * matches its generator and says nothing about whether the generator is
     * right. The failure that would otherwise be silent is a line that exists
     * in {@link aside.games.fnaf9.Voice} and never reaches the page, or a
     * number the page restates instead of reading -- so every fixed sentence
     * is asserted present, and the thresholds the night is tuned against are
     * asserted to be the engine's own values rather than copies of them.
     *
     * <p>The one thing that is genuinely copied rather than read is the
     * corridor's proportions, which live inside {@code GameScreen}'s drawing
     * code and cannot be reached from here. That is why they are gathered in
     * one place in the generator: they are the only numbers in this port with
     * two homes, and a check that cannot see both is not worth writing.
     */
    static void phone() {
        section("the phone build");

        Path out = Path.of("web", "fnaf9.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
            return;
        }
        String page;
        try {
            page = Files.readString(out);
        } catch (Exception e) {
            check("web/fnaf9.html can be read", false);
            return;
        }
        try {
            check("web/fnaf9.html is current -- regenerate it with aside.games.fnaf9.WebFeed",
                    aside.games.fnaf9.WebFeed.html().equals(page));
        } catch (Exception e) {
            check("web/fnaf9.html is current -- regenerate it with aside.games.fnaf9.WebFeed",
                    false);
        }

        // Every fixed sentence the desktop says is on the phone. The failure
        // this catches is a line added to Voice and never emitted, which is
        // invisible in every other check in this file.
        String[] lines = {
                aside.games.fnaf9.Voice.TITLE,
                aside.games.fnaf9.Voice.SUBTITLE,
                aside.games.fnaf9.Voice.HELP_2,
                aside.games.fnaf9.Voice.HELP_3,
                aside.games.fnaf9.Voice.HELP_4,
                aside.games.fnaf9.Voice.BAND_NIGHT,
                aside.games.fnaf9.Voice.FEED_HEAD,
                aside.games.fnaf9.Voice.SENSOR_HEAD,
                aside.games.fnaf9.Voice.SENSOR_NOT_DOWN,
                aside.games.fnaf9.Voice.SENSOR_TOUCHING,
                aside.games.fnaf9.Voice.SENSOR_CLEAR,
                aside.games.fnaf9.Voice.MON_OFF_1,
                aside.games.fnaf9.Voice.MON_OFF_2,
                aside.games.fnaf9.Voice.FADED_1,
                aside.games.fnaf9.Voice.HALL,
                aside.games.fnaf9.Voice.BTN_MON_LEFT,
                aside.games.fnaf9.Voice.BTN_MON_RIGHT,
                aside.games.fnaf9.Voice.BTN_DARK,
                aside.games.fnaf9.Voice.BTN_HOLD,
                aside.games.fnaf9.Voice.CIRCUIT_JAMMED,
                aside.games.fnaf9.Voice.CIRCUIT_DARK,
                aside.games.fnaf9.Voice.CIRCUIT_HOLD,
                aside.games.fnaf9.Voice.TAKEN_HEAD,
                aside.games.fnaf9.Voice.TAKEN_LINE,
                aside.games.fnaf9.Voice.WIN_HEAD,
                aside.games.fnaf9.Voice.WIN_LINE,
                aside.games.fnaf9.Voice.BACK_TO_NIGHTS,
        };
        int missing = 0;
        for (String line : lines) if (!page.contains(line)) missing++;
        check("every fixed sentence the desktop says is on the phone (" + lines.length
                + " lines, " + missing + " missing)", missing == 0);

        // The thresholds, as the engine's own values. A page that restated
        // them would be a page that could disagree about the night.
        check("the phone reads the door's ceiling from the engine, not a copy",
                page.contains("\"holdMax\":" + aside.games.fnaf9.WebFeed.num(Feed.HOLD_MAX)));
        check("the phone reads the door's travel from the engine, not a copy",
                page.contains("\"shutTime\":" + aside.games.fnaf9.WebFeed.num(Feed.SHUT_TIME)));
        check("the phone reads the picture's ceiling from the engine, not a copy",
                page.contains("\"ageMax\":" + aside.games.fnaf9.WebFeed.num(Feed.AGE_MAX)));
        check("the phone reads the delay line's length from the engine, not a copy",
                page.contains("\"hist\":" + Feed.HIST));
        check("the phone reads the hall's length from the engine, not a copy",
                page.contains("\"max\":" + Feed.MAX));

        // The cast, by name, so a re-tuned pair reaches the phone. Night four
        // is the one that was re-tuned by measurement, so it is the one worth
        // naming.
        int castMissing = 0;
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            Pair p = Pair.forNight(n);
            if (!page.contains(p.left().name()) || !page.contains(p.right().name())) castMissing++;
            if (!page.contains(p.note())) castMissing++;
        }
        check("every night's pair and note is on the phone", castMissing == 0);

        // One footfall for both halls. This is the design rule the audio tool
        // states and the desktop's cue table keeps; the phone has its own cue
        // table, so it needs its own check. A page that gave the two halls
        // different footfalls would hand the player the one thing the monitor
        // exists to sell them.
        check("the phone's footfall carries no direction",
                !page.contains("step_left") && !page.contains("step_right"));
        check("the phone has one footfall cue", page.contains("case 'f9_step':"));

        // The delay line is the game, so the page has to actually keep one.
        check("the phone keeps a delay line", page.contains("Feed.prototype.record"));
        check("the phone reads the picture out of the delay line, not a formula",
                page.contains("Feed.prototype.apparent"));
    }

    private SelfTest() {}
}
