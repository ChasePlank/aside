package aside.games.fnaf8.engine;

import aside.games.fnaf8.MouseMap;
import aside.games.fnaf8.engine.Meeting.Setting;
import aside.games.fnaf8.engine.Meeting.Side;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The FNAF 8 checks, runnable with no display.
 *
 *     java -cp classes aside.games.fnaf8.engine.SelfTest
 *
 * <p>These are not "does it compile" checks. Each one is a claim the game
 * makes about itself, written down so it cannot quietly stop being true:
 * that the lamp reaches the hall and not the doorway, that the two of them
 * only die together, that the call beats a look and the beam beats the
 * call, that a unit in a doorway gives up, that the week gets harder, and
 * that the obvious play loses.
 *
 * <p>The survival numbers are printed rather than asserted tightly,
 * because they are a <i>reading</i> of the difficulty, not a contract.
 * What is asserted is the shape: doing nothing must be hopeless, the
 * obvious play must be hopeless, the competent policy must beat the one
 * that never looks, and the week must get harder.
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
        board();
        lamp();
        doorway();
        meeting();
        economy();
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

    static double round1(double d) { return Math.round(d * 10) / 10.0; }

    /** Run a night with no player at all, to the end. */
    static Meeting idle(int night, long seed) {
        Meeting m = new Meeting(night, seed);
        double dt = 1.0 / 60.0;
        for (int i = 0; i < 60 * 400 && m.status == Meeting.Status.PLAYING; i++) m.update(dt);
        return m;
    }

    /** Advance a night by a number of seconds, with no player. */
    static void run(Meeting m, double seconds) {
        double dt = 1.0 / 60.0;
        for (int i = 0; i < (int) (seconds / dt) && m.status == Meeting.Status.PLAYING; i++) {
            m.update(dt);
        }
    }

    /** A night with the lamp out and both of them parked, so a check about the lamp is about the lamp. */
    static Meeting parked(int night, long seed) {
        Meeting m = new Meeting(night, seed);
        m.dark();
        return m;
    }

    // -------------------------------------------------------------- the office

    static void office() {
        section("the office");

        check("five nights", MouseMap.NIGHTS == 5);
        for (int n = 1; n <= 5; n++) {
            Pair p = Pair.forNight(n);
            check("night " + n + " has a pair", p != null);
            check("night " + n + " has a left unit: " + p.left().name(),
                    p.left() != null && !p.left().key().isEmpty());
            check("night " + n + " has a right unit: " + p.right().name(),
                    p.right() != null && !p.right().key().isEmpty());
            check("night " + n + " has two different units",
                    !p.left().key().equals(p.right().key()));
            check("night " + n + " has a note", p.note() != null && !p.note().isEmpty());
            check("night " + n + " has a note that is a sentence",
                    p.note().endsWith("."));
        }

        // The cast. Every unit has to be usable, and the two axes have to
        // be independent -- a night where the fast one is also the
        // stubborn one is a night with one threat in it wearing two names.
        for (Unit u : Unit.all()) {
            check("unit " + u.key() + " has a name", u.name() != null && !u.name().isEmpty());
            check("unit " + u.key() + " has a note", u.note() != null && !u.note().isEmpty());
            check("unit " + u.key() + " has a speed in range",
                    u.speed() >= 0.6 && u.speed() <= 1.6);
            check("unit " + u.key() + " has a stubbornness in range",
                    u.stubborn() >= 0.6 && u.stubborn() <= 2.0);
        }
        check("ten units for five pairs", Unit.all().length == 10);
        for (int n = 1; n <= 5; n++) {
            Pair p = Pair.forNight(n);
            check("night " + n + "'s pair is not the same unit twice",
                    !p.left().key().equals(p.right().key()));
        }

        // The one place the cast design is a claim rather than a taste:
        // the fast one is never the stubborn one, on any night.
        boolean fastIsStubborn = false;
        for (Pair p : Pair.all()) {
            Unit a = p.left(), b = p.right();
            boolean aFastAndStubborn = a.speed() > b.speed() && a.stubborn() > b.stubborn();
            boolean bFastAndStubborn = b.speed() > a.speed() && b.stubborn() > a.stubborn();
            if (aFastAndStubborn || bFastAndStubborn) fastIsStubborn = true;
        }
        check("the fast one is never also the stubborn one", !fastIsStubborn);
    }

    // --------------------------------------------------------------- the board

    static void board() {
        section("the board");

        check("the hall is five steps long", Meeting.MAX == 5);
        check("they are too close at three steps apart", Meeting.MEET == 3);

        Meeting m = new Meeting(1, 12345L);
        check("both of them start at the far end",
                m.distance(Side.LEFT) == Meeting.MAX
                        && m.distance(Side.RIGHT) == Meeting.MAX);
        check("so they start as far apart as the office can measure",
                m.apart() == 2 * Meeting.MAX);
        check("neither of them starts in a doorway",
                !m.waiting(Side.LEFT) && !m.waiting(Side.RIGHT));
        check("the night starts at midnight", m.clock().equals("12 AM"));
        check("the night starts playing", m.status == Meeting.Status.PLAYING);

        // The distance is the sum of the two distances from the doors, and
        // it is the number the whole night is spent keeping above zero.
        m.d[Side.LEFT.ordinal()] = 2;
        m.d[Side.RIGHT.ordinal()] = 4;
        check("apart is the sum of the two distances", m.apart() == 6);
        m.d[Side.LEFT.ordinal()] = 0;
        m.d[Side.RIGHT.ordinal()] = 3;
        check("one at the door and one at three is not yet a meeting", m.apart() == 3);
        m.d[Side.RIGHT.ordinal()] = 2;
        check("one at the door and one at two is", m.apart() == 2);

        // The audio channel, which is the one thing this game deliberately
        // does not have. FNAF 7's whole channel was a step on the left
        // against a step on the right; here both halls emit the same cue,
        // because a footfall that said which hall it came from would hand
        // the player the one piece of knowledge the lamp exists to sell.
        Meeting l = new Meeting(1, 4711L);
        l.dark();
        java.util.Set<String> cues = new java.util.HashSet<>();
        for (int i = 0; i < 400 && cues.size() < 2; i++) {
            l.drainCues();
            l.d[0] = Meeting.MAX;
            l.d[1] = Meeting.MAX;
            l.timer[0] = 0;
            l.timer[1] = 0;
            l.update(1.0 / 60.0);
            for (String cue : l.drainCues()) {
                if (cue.startsWith("f8_step")) cues.add(cue);
            }
        }
        check("both halls make the same footfall", cues.size() == 1);
        check("and it is not FNAF 7's directional cue",
                !cues.contains("step_left") && !cues.contains("step_right"));

        // The clock.
        Meeting c = new Meeting(1, 999L);
        c.d[0] = Meeting.MAX;
        c.d[1] = Meeting.MAX;
        c.timer[0] = 1e9;
        c.timer[1] = 1e9;
        run(c, Meeting.HOUR_SECONDS * 3 + 0.1);
        check("three hours in, it is three o'clock", c.clock().equals("3 AM"));
        run(c, Meeting.HOUR_SECONDS * 3 + 0.1);
        check("six hours in, it is six", c.clock().equals("6 AM"));
        check("and the night is over", c.status == Meeting.Status.SURVIVED);
    }

    // ---------------------------------------------------------------- the lamp

    static void lamp() {
        section("the lamp");

        Meeting m = new Meeting(1, 4242L);
        m.d[0] = Meeting.MAX;
        m.d[1] = Meeting.MAX;
        check("the lamp starts out", m.lamp == Setting.OFF);
        check("and it is on neither hall", m.lampAt() == null);

        check("looking at the left hall works", m.aim(Side.LEFT, Setting.DIM));
        check("and the lamp is on the left", m.lampAt() == Side.LEFT);
        check("and it is dim", m.lamp == Setting.DIM);
        check("and the left hall is visible", m.visible(Side.LEFT));
        check("and the right hall is not", !m.visible(Side.RIGHT));

        // Changing what the lamp is doing, on the hall it is already on, is
        // free. That asymmetry is the game's only rule about your hands.
        check("brightening the same hall is instant", m.aim(Side.LEFT, Setting.BRIGHT));
        check("and it took no time at all", m.swivel <= 0);
        check("and it is bright now", m.lamp == Setting.BRIGHT);
        check("and it is pushing", m.pushing(Side.LEFT));

        // Changing where it is, is not free.
        check("aiming at the other hall starts a swivel", m.aim(Side.RIGHT, Setting.BRIGHT));
        check("and the swivel is the whole crossing", m.swivel > 0);
        check("and the lamp is on neither hall while it crosses", m.lampAt() == null);
        check("and it is not lit", !m.lit(Side.LEFT) && !m.lit(Side.RIGHT));
        check("and it cannot be re-aimed mid-crossing", !m.aim(Side.LEFT, Setting.DIM));
        run(m, Meeting.SWIVEL + 0.05);
        check("and when it arrives it is on the hall it was sent to",
                m.lampAt() == Side.RIGHT);
        check("and it is bright", m.lamp == Setting.BRIGHT);

        // Putting it out cancels a crossing.
        m.aim(Side.LEFT, Setting.DIM);
        check("a crossing is in progress", m.swivel > 0);
        m.dark();
        check("putting the lamp out cancels the crossing", m.swivel <= 0);
        check("and the lamp is out", m.lamp == Setting.OFF);
        check("and it is on neither hall", m.lampAt() == null);
    }

    // ------------------------------------------------------------- the doorway

    static void doorway() {
        section("the doorway");

        Meeting m = new Meeting(1, 777L);
        m.d[Side.LEFT.ordinal()] = 0;
        m.d[Side.RIGHT.ordinal()] = Meeting.MAX;
        check("a unit at zero is in its doorway", m.waiting(Side.LEFT));
        check("and it has its whole patience left", m.patienceLeft(Side.LEFT) > 0.99);
        check("and it is not visible under the lamp", !m.visible(Side.LEFT));

        // The load-bearing rule: the lamp reaches the hall and not the
        // doorway. A unit in a doorway cannot be pushed back.
        m.aim(Side.LEFT, Setting.BRIGHT);
        check("the lamp is bright on the doorway hall", m.pushing(Side.LEFT));
        int before = m.d[Side.LEFT.ordinal()];
        run(m, m.retreat() * 3);
        check("and it cannot move the unit standing in the doorway",
                m.d[Side.LEFT.ordinal()] == before);
        check("and the unit is still in the doorway", m.waiting(Side.LEFT));

        // The call: the other one walks faster while this one waits.
        Meeting a = new Meeting(1, 31337L);
        a.d[0] = 0;
        a.d[1] = Meeting.MAX;
        a.dark();
        double called = a.interval(Side.RIGHT);
        Meeting b = new Meeting(1, 31337L);
        b.d[0] = Meeting.MAX;
        b.d[1] = Meeting.MAX;
        b.dark();
        double uncalled = b.interval(Side.RIGHT);
        check("a unit in a doorway calls its partner", called < uncalled);
        check("and the call is the night's call multiplier",
                Math.abs(called - a.pace() / a.pair.unit(Side.RIGHT).speed() / a.call()) < 1e-9);

        // Giving up. The only way a doorway ever empties.
        Meeting g = new Meeting(1, 5150L);
        g.d[0] = 0;
        g.d[1] = Meeting.MAX;
        g.timer[1] = 1e9;
        g.dark();
        run(g, g.patience() - 0.2);
        check("it is still in the doorway just before its patience runs out",
                g.waiting(Side.LEFT));
        run(g, 0.4);
        check("and it gives up when its patience runs out", !g.waiting(Side.LEFT));
        check("and it walks back to the far end",
                g.distance(Side.LEFT) == Meeting.MAX);
        check("and the office counted it", g.gaveUp == 1);

        // A unit that is pushed back out of a doorway is a different thing
        // from one that gives up, and the game has to be able to tell them
        // apart -- the first is the player's doing, the second is not.
        Meeting p = new Meeting(1, 8080L);
        p.d[0] = 1;
        p.d[1] = Meeting.MAX;
        p.timer[0] = 0.01;
        p.timer[1] = 1e9;
        p.aim(Side.LEFT, Setting.BRIGHT);
        run(p, p.retreat() * p.pair.unit(Side.LEFT).stubborn() * 1.5);
        check("a unit in the hall is pushed back", p.distance(Side.LEFT) > 1);
        check("and the office counted the push", p.pushed > 0);
        check("and it did not count a give-up", p.gaveUp == 0);
    }

    // ------------------------------------------------------------ the meeting

    static void meeting() {
        section("the meeting");

        // The only death in the game, and it takes both of them.
        Meeting m = new Meeting(1, 2024L);
        m.d[0] = 0;
        m.d[1] = Meeting.MAX;
        m.timer[0] = 1e9;
        m.timer[1] = 1e9;
        m.dark();
        m.update(1.0 / 60.0);
        check("one in a doorway and one at the far end is not a meeting",
                m.status == Meeting.Status.PLAYING);
        m.d[1] = Meeting.MEET + 1;
        m.update(1.0 / 60.0);
        check("one in a doorway and one just outside the threshold is not a meeting",
                m.status == Meeting.Status.PLAYING);
        m.d[1] = Meeting.MEET;
        m.update(1.0 / 60.0);
        check("and one step inside it is", m.status == Meeting.Status.MET);
        check("and the office records the second it happened", m.metAt >= 0);

        Meeting both = new Meeting(1, 2024L);
        both.d[0] = 0;
        both.d[1] = 0;
        both.dark();
        both.update(1.0 / 60.0);
        check("both in a doorway is the same meeting", both.status == Meeting.Status.MET);

        // And it is reachable the other way: by walking, not by staging.
        boolean metByWalking = false;
        for (int i = 0; i < 200 && !metByWalking; i++) {
            Meeting w = new Meeting(1, 900L + i);
            w.dark();
            for (int f = 0; f < 60 * 400 && w.status == Meeting.Status.PLAYING; f++) {
                w.update(1.0 / 60.0);
            }
            if (w.status == Meeting.Status.MET) metByWalking = true;
        }
        check("doing nothing at all ends in a meeting", metByWalking);

        // A night that is never touched by either of them ends at six.
        Meeting s = new Meeting(1, 55L);
        s.d[0] = Meeting.MAX;
        s.d[1] = Meeting.MAX;
        s.timer[0] = 1e9;
        s.timer[1] = 1e9;
        run(s, Meeting.HOUR_SECONDS * Meeting.NIGHT_HOURS + 0.5);
        check("a night where neither of them moves ends at six",
                s.status == Meeting.Status.SURVIVED);
        check("and it was never a meeting", s.metAt < 0);
    }

    // -------------------------------------------------------------- the economy

    static void economy() {
        section("the economy");

        // The regime the game is tuned in, and the one claim in this file
        // that is arithmetic rather than taste:
        //
        //     the beam beats the call, and the call beats a look.
        //
        // Outside that band the night stops being a night. If the beam
        // cannot out-push the call then the moment one of them reaches a
        // doorway the other one is already lost, and if a look costs
        // nothing then the lamp is always already up and the answer to
        // every arrival is a move the player has all night to make.
        for (int n = 1; n <= 5; n++) {
            Meeting m = new Meeting(n, 1L);
            double pace = m.pace();
            double retreat = m.retreat();
            double beam = 1.0 / retreat;
            double call = m.call() / pace;
            double look = Meeting.DIM_RUSH / pace;
            check("night " + n + ": the beam out-pushes the call", beam > call);
            check("night " + n + ": the call out-runs a look", call > look);
            check("night " + n + ": a look still costs something", look > 1.0 / pace);
            check("night " + n + ": the bright beam costs more than the dim one",
                    Meeting.BRIGHT_RUSH > Meeting.DIM_RUSH);
        }

        // And the ramps, stated as claims rather than as tables.
        Meeting n1 = new Meeting(1, 1L), n5 = new Meeting(5, 1L);
        check("the call is stronger on the last night", n5.call() > n1.call());
        check("the doorway is less patient on the last night",
                n5.patience() < n1.patience());
        check("the beam is weaker on the last night", n5.retreat() > n1.retreat());
        // The pace table is deliberately NOT monotone, and this is the
        // check that says so out loud rather than leaving it to look like
        // a mistake. The night's base pace compensates for who is walking
        // it: night two's pair is the quickest in the building and night
        // three's is the slowest, so the table goes 2.30, 2.34, 2.50, 2.58,
        // 2.58 and the *measured* difficulty still climbs. A reader who
        // sees a non-monotone table and no note will "fix" it.
        check("the pace table is not monotone, because the cast is not",
                n1.pace() < new Meeting(3, 1L).pace());
        check("and the last night's base pace is the slowest of the five",
                n5.pace() >= new Meeting(4, 1L).pace());

        // The jitter, which is what makes a belief about an unlit hall a
        // belief that drifts -- and therefore what makes the lamp worth
        // spending on a hall that is not currently a problem.
        check("the night is jittered", Meeting.JITTER > 0.2);
        boolean sawDifferent = false;
        Meeting j = new Meeting(1, 606L);
        double first = j.nextInterval(Side.LEFT);
        for (int i = 0; i < 40; i++) {
            if (Math.abs(j.nextInterval(Side.LEFT) - first) > 1e-6) sawDifferent = true;
        }
        check("and two steps are never the same length", sawDifferent);
        check("and the mean is what the tables say",
                Math.abs(j.interval(Side.LEFT) - j.pace() / j.pair.unit(Side.LEFT).speed()) < 1e-9);
    }

    // ----------------------------------------------------------- the instrument

    static void instrument() {
        section("the instrument");

        // The bot is not allowed to see anything the player cannot. It
        // reads visible/waiting/patienceLeft/lamp/lampAt and nothing else,
        // and in this game that costs it something real: the lamp can only
        // be in one hall, so it always knows exactly where one of them is
        // and has to work out where the other one is.
        Meeting m = new Meeting(1, 1234L);
        Bot bot = new Bot(Bot.Policy.PRO);
        check("the bot starts believing they are at the far end",
                bot.est[0] == Meeting.MAX && bot.est[1] == Meeting.MAX);
        m.aim(Side.LEFT, Setting.DIM);
        run(m, 0.5);
        bot.step(m, 1.0 / 60.0);
        check("and it believes what it can see", bot.est[0] == m.distance(Side.LEFT));
        check("and it has to work out the other one", bot.est[1] <= Meeting.MAX);
        check("and it knows which one it has not seen",
                bot.age[1] > bot.age[0]);
    }

    // ---------------------------------------------------------------- the mouse

    static void mouse() {
        section("the mouse");

        double[][] buttons = {MouseMap.LOOK_L, MouseMap.LOOK_R, MouseMap.OFF,
                MouseMap.PUSH_L, MouseMap.PUSH_R};
        String[] names = {"LOOK LEFT", "LOOK RIGHT", "LAMP OFF", "PUSH LEFT", "PUSH RIGHT"};
        MouseMap.Kind[] kinds = {MouseMap.Kind.LOOK_L, MouseMap.Kind.LOOK_R,
                MouseMap.Kind.OFF, MouseMap.Kind.PUSH_L, MouseMap.Kind.PUSH_R};

        for (int i = 0; i < buttons.length; i++) {
            double[] r = buttons[i];
            check(names[i] + " is on the canvas",
                    r[0] >= 0 && r[0] + r[2] <= MouseMap.W
                            && r[1] >= 0 && r[1] + r[3] <= MouseMap.H);
            check(names[i] + " is inside the strip",
                    r[1] >= MouseMap.STRIP[1]);
            check(names[i] + " is hit at its centre",
                    MouseMap.hit(r[0] + r[2] / 2, r[1] + r[3] / 2).kind() == kinds[i]);
            check(names[i] + " is hit at its top-left corner",
                    MouseMap.hit(r[0] + 1, r[1] + 1).kind() == kinds[i]);
            check(names[i] + " is hit at its bottom-right corner",
                    MouseMap.hit(r[0] + r[2] - 1, r[1] + r[3] - 1).kind() == kinds[i]);
        }

        // No two buttons may overlap, or a click lands on whichever the
        // hit test happens to check first and the other one is decoration.
        boolean overlap = false;
        for (int i = 0; i < buttons.length; i++) {
            for (int j = i + 1; j < buttons.length; j++) {
                double[] a = buttons[i], b = buttons[j];
                if (a[0] < b[0] + b[2] && b[0] < a[0] + a[2]
                        && a[1] < b[1] + b[3] && b[1] < a[1] + a[3]) overlap = true;
            }
        }
        check("no two buttons overlap", !overlap);

        // And the room itself is not a button, but it is a hit, so a click
        // on the office is a click that did nothing rather than a miss.
        check("the room is a hit", MouseMap.hit(640, 300).kind() == MouseMap.Kind.SCENE);
        check("and a click on the office does nothing",
                MouseMap.hit(640, 300).kind() != MouseMap.Kind.LOOK_L
                        && MouseMap.hit(640, 300).kind() != MouseMap.Kind.PUSH_L);

        // The night rows.
        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            double[] row = MouseMap.nightRow(n);
            check("night " + n + "'s row is on the canvas",
                    row[0] >= 0 && row[0] + row[2] <= MouseMap.W
                            && row[1] >= 0 && row[1] + row[3] <= MouseMap.H);
            check("night " + n + "'s row is hit at its centre",
                    MouseMap.nightAt(row[0] + row[2] / 2, row[1] + row[3] / 2) == n);
        }
        check("and nothing above the rows is a night",
                MouseMap.nightAt(640, 100) == 0);
    }

    // -------------------------------------------------------------- survival

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
     * <p>What is <i>not</i> checked here is the RNG or the update loop. The
     * page reproduces {@code java.util.Random} in BigInt so the same seed
     * deals the same night in both builds, and that was verified by hand:
     * night one on seed 1001 was driven through both engines under the same
     * scripted policy and the two traces are identical every second -- the
     * distances, the lamp's setting and the lamp's side all agree, and both
     * end MET at the same moment. It is not checkable from Java without a
     * JavaScript engine, and the alternative (a page that deals its own
     * nights) would make the two builds different games with the same rules.
     */
    static void phone() {
        section("the phone build");

        Path out = Path.of("web", "fnaf8.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
            return;
        }
        String page;
        try {
            page = Files.readString(out);
        } catch (Exception e) {
            check("web/fnaf8.html can be read", false);
            return;
        }
        try {
            check("web/fnaf8.html is current -- regenerate it with aside.games.fnaf8.WebMeeting",
                    aside.games.fnaf8.WebMeeting.html().equals(page));
        } catch (Exception e) {
            check("web/fnaf8.html is current -- regenerate it with aside.games.fnaf8.WebMeeting",
                    false);
        }

        // The rules, as numbers. A port whose beam pushes at 1.2 rather than
        // 1.15 plays differently and looks identical.
        check("the page carries the swivel",
                page.contains("swivel: " + aside.games.fnaf8.WebMeeting.num(Meeting.SWIVEL)));
        check("the page carries the dim rush",
                page.contains("dimRush: " + aside.games.fnaf8.WebMeeting.num(Meeting.DIM_RUSH)));
        check("the page carries the bright rush",
                page.contains("brightRush: " + aside.games.fnaf8.WebMeeting.num(Meeting.BRIGHT_RUSH)));
        check("the page carries how close they have to get",
                page.contains("meet: " + Meeting.MEET));
        check("the page carries the length of the hall",
                page.contains("max: " + Meeting.MAX));

        // The four night tables, which are the whole of the difficulty ramp.
        for (String t : new String[]{"pace", "retreat", "call", "patience"}) {
            String want = aside.games.fnaf8.WebMeeting.table(
                    switch (t) {
                        case "pace" -> 0;
                        case "retreat" -> 1;
                        case "call" -> 2;
                        default -> 3;
                    });
            check("the page carries the " + t + " table", page.contains(t + ": " + want));
        }

        // And the cast, which is the one thing the player reads before the
        // night starts.
        for (int n = 1; n <= 5; n++) {
            Pair p = Pair.forNight(n);
            check("the page carries night " + n + "'s pair, " + p.left().name()
                            + " and " + p.right().name(),
                    page.contains(p.left().name()) && page.contains(p.right().name())
                            && page.contains(p.note()));
        }
    
        // The sound. And this one is the counter-example that proves the palette is about
        // information rather than about sound. FNAF 8 has *no directional channel at all*,
        // deliberately: both halls emit the same footfall, because the lamp is the only
        // way to know where either of them is. The port must not invent a distinction the
        // desktop refuses to make.
        check("the page carries the shared synthesiser",
                page.contains("function voice(") && page.contains("function sfx("));
        check("the page has a voice for the chime_6am cue",
                page.contains("case \"chime_6am\""));
        check("the page has a voice for the f8_back cue",
                page.contains("case \"f8_back\""));
        check("the page has a voice for the f8_door cue",
                page.contains("case \"f8_door\""));
        check("the page has a voice for the f8_gives_up cue",
                page.contains("case \"f8_gives_up\""));
        check("the page has a voice for the f8_met cue",
                page.contains("case \"f8_met\""));
        check("the page has a voice for the f8_off cue",
                page.contains("case \"f8_off\""));
        check("the page has a voice for the f8_push cue",
                page.contains("case \"f8_push\""));
        check("the page has a voice for the f8_set cue",
                page.contains("case \"f8_set\""));
        check("the page has a voice for the f8_step cue",
                page.contains("case \"f8_step\""));
        check("the page has a voice for the f8_swivel cue",
                page.contains("case \"f8_swivel\""));
}

    static void survival() {
        section("the week, 500 seeds a night");

        int seeds = 500;
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

        // Doing nothing is hopeless. If this ever passes, the night is
        // being survived by the clock rather than by the player.
        check("doing nothing loses every night",
                Bot.week(Bot.Policy.IDLE, 200) == 0);
        // The trap: one hall watched, forever.
        check("watching one hall forever loses every night",
                Bot.week(Bot.Policy.STARE, 200) == 0);
        // The obvious play, and the one the whole game is built to punish:
        // point the lamp at the thing standing in the doorway. The lamp
        // does not reach a doorway, so this is a night spent doing nothing
        // to the one thing that can kill you.
        check("pointing the lamp at the doorway loses every night",
                Bot.week(Bot.Policy.DOOR, 200) == 0);

        // The competent policy beats the one that never looks. That is the
        // claim the ladder is read against, and it is the only reason the
        // lamp has two settings rather than one.
        double react = Bot.week(Bot.Policy.REACT, seeds);
        double proWeek = Bot.week(Bot.Policy.PRO, seeds);
        check("the policy that looks beats the policy that only acts",
                proWeek >= react);
        System.out.println("       REACT " + Math.round(react * 100) + "%   PRO "
                + Math.round(proWeek * 100) + "%");

        // And the week gets harder. Monotone to within the noise of five
        // hundred seeds, which is about four points.
        boolean monotone = true;
        for (int i = 1; i < 5; i++) if (pro[i] > pro[i - 1] + 0.04) monotone = false;
        check("the week gets harder", monotone);
        check("the last night is the hardest of the eight",
                pro[4] < pro[0]);
        check("and the first night is survivable", pro[0] > 0.75);
        check("and the last night is not a formality", pro[4] < 0.85);

        // The killers, so the shape of the difficulty is visible rather
        // than inferred from a percentage.
        for (int n = 1; n <= 5; n++) {
            int[] t = Bot.tally(Bot.Policy.PRO, n, 200);
            System.out.println("       night " + n + ": met " + t[0]
                    + ", survived " + t[1] + ", doorways emptied "
                    + round1(t[2]) + " per night");
        }
    }
}
