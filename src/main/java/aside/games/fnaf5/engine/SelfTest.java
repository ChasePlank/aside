package aside.games.fnaf5.engine;

import aside.games.fnaf5.MouseMap;

/**
 * The FNAF 5 checks, runnable with no display.
 *
 *     java -cp classes aside.games.fnaf5.engine.SelfTest
 *
 * These are not "does it compile" checks. Each one is a claim the game
 * makes about itself, written down so it cannot quietly stop being true:
 * that the building is a line, that the camera cannot see the room you are
 * in, that moving is what Ballora hears, that the shock is the only thing
 * that answers Funtime Freddy, and that the week gets harder.
 *
 * The survival numbers are printed rather than asserted tightly, because
 * they are a *reading* of the difficulty, not a contract. What is asserted
 * is the shape: idle must die, and the week must get harder.
 */
public final class SelfTest {

    static int checks = 0;
    static int failed = 0;

    public static void main(String[] args) {
        // `--survival` runs only the sweep, which is the slow part and the
        // only part that changes when a difficulty number moves. Tuning
        // means running this a dozen times in a row.
        if (args.length > 0 && args[0].equals("--survival")) {
            survival();
            System.out.println();
            System.out.println(checks + " checks, " + failed + " failed");
            if (failed > 0) System.exit(1);
            return;
        }
        building();
        camera();
        sound();
        shock();
        rules();
        clock();
        mouse();
        survival();
        System.out.println();
        System.out.println(checks + " checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // --------------------------------------------------------- the building

    static void building() {
        section("the building");

        check("five rooms", Room.COUNT == 5);
        check("the shift starts in the middle", Room.START == Room.Where.CONTROL);
        check("the middle is the middle", Room.index(Room.START) == Room.COUNT / 2);

        for (Room.Where a : Room.ALL) {
            check("you are already in " + a.label, Room.distance(a, a) == 0);
            for (Room.Where b : Room.ALL) {
                check("distance is symmetric: " + a.tag + " / " + b.tag,
                        Room.distance(a, b) == Room.distance(b, a));
            }
        }
        check("the ends are four rooms apart",
                Room.distance(Room.ALL[0], Room.ALL[Room.COUNT - 1]) == 4);
        check("neighbours are adjacent", Room.adjacent(Room.Where.PARTS, Room.Where.BALLORA));
        check("the ends are not adjacent",
                !Room.adjacent(Room.Where.PARTS, Room.Where.AUDITORIUM));

        // stepToward is how everything in the building moves, so it has to
        // be right in both directions and at both ends.
        for (Room.Where a : Room.ALL) {
            for (Room.Where b : Room.ALL) {
                Room.Where next = Room.stepToward(a, b);
                if (a == b) {
                    check("already there: " + a.tag, next == a);
                } else {
                    check("one step from " + a.tag + " toward " + b.tag + " is closer",
                            Room.distance(next, b) == Room.distance(a, b) - 1);
                }
            }
        }

        // stepAway is what Ballora does when she gives up, and the case
        // that matters is the one where she is standing in your room --
        // a zero there is a no-op that looks like a working game.
        for (Room.Where a : Room.ALL) {
            Room.Where away = Room.stepAway(a, a);
            check("giving up in " + a.tag + " goes somewhere", away != a);
        }
        check("giving up at an end still moves",
                Room.stepAway(Room.ALL[0], Room.ALL[0]) != Room.ALL[0]);
    }

    // ----------------------------------------------------------- the camera

    static void camera() {
        section("the camera");

        Game g = new Game(1, 42);
        check("the camera starts somewhere else", g.camera != g.where);
        check("the monitor starts up", g.monitorOn);
        check("the view is the camera", g.view() == g.camera);

        // THE RULE. Everything else in the game is downstream of this one.
        check("you cannot watch the room you are in", !g.watch(g.where));
        check("and the camera did not move", g.camera != g.where);

        check("you can watch another room", g.watch(Room.Where.PARTS));
        check("and it moved", g.camera == Room.Where.PARTS);

        // Walking into the room you were watching kills the feed, because
        // the camera cannot see the room you are in -- and that is the rule
        // happening to you rather than a bug being papered over.
        Game w = new Game(1, 7);
        w.watch(Room.Where.CIRCUS);
        w.step(1);
        for (int i = 0; i < 200; i++) w.update(1.0 / 60.0);
        check("walking into the room on camera kills the feed",
                w.where == Room.Where.CIRCUS && w.camera == null);
        check("and the view is nothing", w.view() == null);

        Game d = new Game(1, 7);
        d.monitorDown();
        check("the monitor can be taken down", !d.monitorOn);
        check("and the view is nothing", d.view() == null);
        check("and it cannot be taken down twice", !d.monitorDown());
    }

    // ------------------------------------------------------------ the sound

    static void sound() {
        section("the sound");

        Game g = new Game(1, 42);
        check("the building starts quiet", g.soundAge > Game.SOUND_MEMORY);
        check("and nothing is next to you", g.heardAge > 1.0);

        g.step(1);
        check("walking makes a sound", g.soundAge == 0);
        check("and it was made where you are going", g.lastSound == Room.Where.CIRCUS);
        check("and it was counted", g.sounds == 1);

        // Ballora's target is the last sound, and only while she can still
        // remember it. Past that she has no target at all, which is what
        // makes standing still a defence rather than just a delay.
        Threat ballora = byKey(g, "ballora");
        check("Ballora follows the sound", ballora.target(g) == g.lastSound);
        Game old = new Game(1, 42);
        old.soundAge = Game.SOUND_MEMORY + 1;
        check("and loses it when the building goes quiet",
                byKey(old, "ballora").target(old) == null);

        // The counter, stated as a test: silence loses her, and both halves
        // of that test are load-bearing.
        Game b = new Game(1, 42);
        b.where = Room.Where.BALLORA;
        Threat bal = byKey(b, "ballora");
        bal.room = Room.Where.BALLORA;
        bal.hereFor = Game.BALLORA_PATIENCE + 0.1;
        b.soundAge = Game.BALLORA_PATIENCE + 0.1;
        bal.update(0.01, b);
        check("silence loses Ballora", bal.room != Room.Where.BALLORA);

        Game b2 = new Game(1, 42);
        b2.where = Room.Where.BALLORA;
        Threat bal2 = byKey(b2, "ballora");
        bal2.room = Room.Where.BALLORA;
        bal2.hereFor = Game.BALLORA_PATIENCE + 0.1;
        b2.soundAge = 0.0;                       // you just moved
        bal2.update(0.01, b2);
        check("but a noise keeps her", bal2.room == Room.Where.BALLORA);
    }

    // ------------------------------------------------------------ the shock

    static void shock() {
        section("the shock");

        Game g = new Game(1, 42);
        check("the night starts with charges", g.shocks == Game.shockAllowance(1));
        check("and it can be used", g.canShock());

        Threat freddy = byKey(g, "freddy");
        freddy.room = g.where;
        check("something is in the room", g.occupied());
        check("and the shock finds it", g.shock());
        check("and it is gone", freddy.room != g.where);
        check("and it went to the far end", freddy.room == g.farEnd());
        check("and it cost a charge", g.shocks == Game.shockAllowance(1) - 1);
        check("and it was not wasted", g.wastedShocks == 0);

        // Hands are busy.
        check("the shock cannot be used twice at once", !g.canShock());
        check("and the second press does nothing", !g.shock());

        // A shock at nothing costs the same as a shock at something, which
        // is the whole reason the count is a decision.
        Game w = new Game(1, 42);
        w.busy = 0;
        check("a shock at an empty room finds nothing", !w.shock());
        check("and is counted as wasted", w.wastedShocks == 1);
        check("and still costs a charge", w.shocks == Game.shockAllowance(1) - 1);

        // The charges run out and do not come back.
        Game e = new Game(1, 42);
        for (int i = 0; i < Game.shockAllowance(1) + 3; i++) { e.busy = 0; e.shock(); }
        check("the charges run out", e.shocks == 0);
        check("and then the shock is not available", !e.canShock());
        check("and the night is still playing", e.status == Game.Status.PLAYING);

        // Far end is whichever end is further from you.
        Game f = new Game(1, 42);
        f.where = Room.Where.PARTS;
        check("the far end from one end is the other",
                f.farEnd() == Room.ALL[Room.COUNT - 1]);
        f.where = Room.Where.AUDITORIUM;
        check("and the other way round", f.farEnd() == Room.ALL[0]);
    }

    // ------------------------------------------------------------ the rules

    static void rules() {
        section("the three rules");

        Game g = new Game(1, 42);
        Threat ballora = byKey(g, "ballora");
        Threat foxy = byKey(g, "foxy");
        Threat freddy = byKey(g, "freddy");

        check("Ballora follows sound", ballora.rule == Threat.Rule.SOUND);
        check("Funtime Foxy follows the camera", foxy.rule == Threat.Rule.ATTENTION);
        check("Funtime Freddy follows you", freddy.rule == Threat.Rule.PURSUIT);

        check("Freddy always knows where you are", freddy.target(g) == g.where);

        g.watch(Room.Where.PARTS);
        check("Foxy goes to what you are watching", foxy.target(g) == Room.Where.PARTS);
        foxy.room = Room.Where.PARTS;
        check("and once it is there it comes for you", foxy.target(g) == g.where);

        // The other half of Foxy's rule: with no feed there is nothing to
        // follow, so it stops where it is. That is a real defence and it
        // costs you the only eye you have.
        g.monitorDown();
        check("Foxy has no target with the monitor down", foxy.target(g) == null);

        // Foxy turns to follow the feed the moment the feed moves, or the
        // counter does not work: it would stand in your room for a whole
        // interval deciding, which is longer than the grace.
        Game t = new Game(1, 42);
        Threat tf = byKey(t, "foxy");
        tf.room = t.where;
        t.busy = 0;
        t.watch(Room.Where.PARTS);
        check("moving the camera makes Foxy move now",
                tf.timer >= t.interval(tf));
    }

    // ------------------------------------------------------------ the clock

    static void clock() {
        section("the clock");

        Game g = new Game(1, 42);
        check("the night starts at midnight", g.hour == 0);
        g.update(Game.HOUR_SECONDS * 3);
        check("three hours in it is 3 AM", g.hour == 3);
        check("and it is still playing", g.status == Game.Status.PLAYING);
        g.update(Game.HOUR_SECONDS * 3);
        check("six hours in the night is over", g.status == Game.Status.SURVIVED);
        check("and it says 6 AM", g.hour >= Game.NIGHT_HOURS);

        // A night is six hours of forty seconds.
        check("a night is " + (int) (Game.HOUR_SECONDS * Game.NIGHT_HOURS) + "s",
                Math.abs(Game.HOUR_SECONDS * Game.NIGHT_HOURS - 240.0) < 1e-9);
    }

    // ------------------------------------------------------------ the mouse

    static void mouse() {
        section("the mouse");

        check("there are five camera tiles", Room.COUNT == 5);
        for (int i = 0; i < Room.COUNT; i++) {
            double[] r = MouseMap.tile(i);
            check("tile " + i + " is inside the strip",
                    r[1] >= MouseMap.BAR[1] && r[1] + r[3] <= MouseMap.BAR[1] + MouseMap.BAR[3]);
            check("tile " + i + " is on the canvas",
                    r[0] >= 0 && r[0] + r[2] <= MouseMap.W);
            check("tile " + i + " answers for its own centre",
                    MouseMap.tileAt(r[0] + r[2] / 2, r[1] + r[3] / 2) == i);
        }
        for (int i = 0; i + 1 < Room.COUNT; i++) {
            double[] a = MouseMap.tile(i), b = MouseMap.tile(i + 1);
            check("tiles " + i + " and " + (i + 1) + " do not overlap",
                    a[0] + a[2] <= b[0]);
        }

        // The band and the strip win over the scene, because they are drawn
        // on top of it and a button that is visible but not clickable is
        // worse than no button.
        check("the shock button is a shock",
                MouseMap.hit(MouseMap.SHOCK[0] + 5, MouseMap.SHOCK[1] + 5).kind()
                        == MouseMap.Kind.SHOCK);
        check("the left move button moves left",
                MouseMap.hit(MouseMap.MOVE_L[0] + 5, MouseMap.MOVE_L[1] + 5).kind()
                        == MouseMap.Kind.MOVE_LEFT);
        check("the right move button moves right",
                MouseMap.hit(MouseMap.MOVE_R[0] + 5, MouseMap.MOVE_R[1] + 5).kind()
                        == MouseMap.Kind.MOVE_RIGHT);
        check("the middle of the feed is not a button",
                MouseMap.hit(MouseMap.W / 2, MouseMap.SCENE[1] + MouseMap.SCENE[3] / 2).kind()
                        == MouseMap.Kind.SCENE);
        check("a tile beats the strip it sits in",
                MouseMap.hit(MouseMap.tile(0)[0] + 5, MouseMap.tile(0)[1] + 5).kind()
                        == MouseMap.Kind.TILE);

        for (int n = 1; n <= MouseMap.NIGHTS; n++) {
            double[] r = MouseMap.nightRow(n);
            check("night row " + n + " answers for its own centre",
                    MouseMap.nightAt(r[0] + r[2] / 2, r[1] + r[3] / 2) == n);
        }
        check("nothing is night 0", MouseMap.nightAt(5, 5) == 0);
    }

    // ---------------------------------------------------------- the difficulty

    static void survival() {
        section("the week");

        int runs = 60;
        System.out.printf("    %-6s %7s %7s %7s %7s%n", "night", "IDLE", "REACT", "HOLD", "PANIC");
        double[] hold = new double[5];
        for (int n = 1; n <= 5; n++) {
            double idle = Bot.survival(n, runs, Bot.Policy.IDLE);
            double react = Bot.survival(n, runs, Bot.Policy.REACT);
            double h = Bot.survival(n, runs, Bot.Policy.HOLD);
            double panic = Bot.survival(n, runs, Bot.Policy.PANIC);
            hold[n - 1] = h;
            System.out.printf("    %-6d %6.0f%% %6.0f%% %6.0f%% %6.0f%%%n",
                    n, idle * 100, react * 100, h * 100, panic * 100);
        }

        // The shape, not the numbers. A player who does nothing must die,
        // and the week must get harder -- the second one is what "each gets
        // harder than the last" means as a test rather than as a promise.
        check("doing nothing dies on night 1",
                Bot.survival(1, runs, Bot.Policy.IDLE) == 0.0);
        check("doing nothing dies on night 5",
                Bot.survival(5, runs, Bot.Policy.IDLE) == 0.0);
        check("the week does not get easier", hold[4] <= hold[0] + 1e-9);

        // The charges are the resource, and the week takes them away.
        for (int n = 1; n < 5; n++) {
            check("night " + n + " allows at least as many shocks as night " + (n + 1),
                    Game.shockAllowance(n) >= Game.shockAllowance(n + 1));
        }
        check("the first night allows at least one shock", Game.shockAllowance(1) >= 1);

        // And the building gets faster.
        for (int n = 1; n < 5; n++) {
            Game a = new Game(n, 1), b = new Game(n + 1, 1);
            check("night " + (n + 1) + " is at least as fast as night " + n,
                    b.baseInterval() <= a.baseInterval() + 1e-9);
        }
    }

    // ---------------------------------------------------------------- plumbing

    static Threat byKey(Game g, String key) {
        for (Threat t : g.threats) if (t.key.equals(key)) return t;
        return null;
    }

    static void section(String name) {
        System.out.println();
        System.out.println("-- " + name + " " + "-".repeat(Math.max(0, 58 - name.length())));
    }

    static void check(String what, boolean ok) {
        checks++;
        if (!ok) {
            failed++;
            System.out.println("  FAIL  " + what);
        }
    }

    private SelfTest() {}
}
