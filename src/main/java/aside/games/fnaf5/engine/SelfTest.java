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
 * is the shape: idle must die, moving must beat standing still, and the
 * week must get harder.
 *
 * That last one is new as of 2026-10-01, and it is worth knowing why it
 * was not real before. It compared HOLD's survival across the week, and
 * HOLD scored 0% on every night, so it was comparing an array of zeros to
 * itself. The game's own bot doc said HOLD was the good policy. A check
 * that cannot fail is worse than no check, because it reads as verified.
 *
 * <b>2026-10-01, later: three of these checks are new and they are the
 * redesign written as assertions.</b> The shock removes the pursuer and
 * nothing else; the feed is Funtime Foxy's fuel, so he is faster with the
 * monitor up than with it down and the other two do not care; and Ballora's
 * counter fits inside the grace on every night, which it did not before.
 * Each one is a rule the game now rests on, and each one is here so that a
 * later tuning pass cannot quietly undo it -- which is exactly what
 * happened to the "week gets harder" check the first time.
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
        cues();
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

        // THE RULE, and it is the redesign: the shock removes the pursuer
        // and nothing else. Ballora is blind and follows sound, so a shock
        // in her room is a reason for her to stay; Foxy follows the feed
        // and does not care. Before 2026-10-01 the shock cleared all three,
        // and the sweep said the universal answer beat every specific one.
        Game b = new Game(1, 42);
        byKey(b, "ballora").room = b.where;
        check("Ballora is in the room", b.occupied());
        check("and the shock does not remove her", !b.shock());
        check("and she is still there", byKey(b, "ballora").room == b.where);
        check("and the shock reads as wasted", b.wastedShocks == 1);

        Game x = new Game(1, 42);
        byKey(x, "foxy").room = x.where;
        check("Foxy is in the room", x.occupied());
        check("and the shock does not remove him", !x.shock());
        check("and he is still there", byKey(x, "foxy").room == x.where);

        // The pursuer is still the one it answers, even standing beside
        // something it cannot touch.
        Game m = new Game(1, 42);
        byKey(m, "ballora").room = m.where;
        byKey(m, "freddy").room = m.where;
        check("the shock finds the pursuer", m.shock());
        check("and removes him", byKey(m, "freddy").room != m.where);
        check("and leaves Ballora where she was", byKey(m, "ballora").room == m.where);

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

        // THE FEED IS HIS FUEL. He follows the camera, so the camera is
        // what moves him: twice as fast with the monitor up, frozen with it
        // down. Before 2026-10-01 he was the same speed either way, which
        // made looking free and left the one threat whose whole rule is
        // about looking with four kills in a week. See Game.FEED_PACE.
        Game fp = new Game(1, 42);
        Threat fpFoxy = byKey(fp, "foxy");
        double up = fp.interval(fpFoxy);
        fp.monitorDown();
        double down = fp.interval(fpFoxy);
        check("Foxy is faster with the feed up than with it down", up < down);
        check("and the feed is worth exactly FEED_PACE",
                Math.abs(up * Game.FEED_PACE - down) < 1e-9);

        // And the feed does not touch the other two. Ballora is blind and
        // Freddy is following you; neither of them has ever seen a camera.
        Game fo = new Game(1, 42);
        Threat foBallora = byKey(fo, "ballora");
        Threat foFreddy = byKey(fo, "freddy");
        double balUp = fo.interval(foBallora);
        double freUp = fo.interval(foFreddy);
        fo.monitorDown();
        check("the feed does not move Ballora", fo.interval(foBallora) == balUp);
        check("and it does not move Freddy", fo.interval(foFreddy) == freUp);

        // BALLORA'S COUNTER HAS TO FIT INSIDE THE GRACE, on every night of
        // the week. It did not before 2026-10-01: her patience was 1.8 and
        // night 5's grace was also 1.8, so the book answer to her was a
        // coin flip on the last night and the sweep's competent policy died
        // to her on every seed of it.
        for (int n = 1; n <= 5; n++) {
            Game gn = new Game(n, 1);
            check("silence loses Ballora before the grace on night " + n,
                    Game.BALLORA_PATIENCE < gn.grace());
        }

        // And she is faster than the player once the week has started,
        // which is what makes the stop a decision rather than a formality:
        // at pace 1.00 she moved every 3.0 seconds on night 5 while the
        // player moved every 1.5, so walking away was always an answer and
        // silence never had to be. Nights 1 and 2 are the ramp -- she is
        // still slower than the player there, and that is deliberate,
        // because a first night that already demands the stop is not a
        // first night.
        for (int n = 3; n <= 5; n++) {
            Game gn = new Game(n, 1);
            check("Ballora is faster than the player on night " + n,
                    gn.interval(byKey(gn, "ballora")) < Game.MOVE_TIME);
        }
        Game n1 = new Game(1, 1);
        check("and the first night is still a night you can walk out of",
                n1.interval(byKey(n1, "ballora")) > Game.MOVE_TIME);
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
        System.out.printf("    %-6s %7s %7s %7s %7s %7s %7s%n",
                "night", "IDLE", "REACT", "HOLD", "FLEE", "PANIC", "PRO");
        double[] hold = new double[5];
        double[] react = new double[5];
        double[] flee = new double[5];
        double[] panic = new double[5];
        double[] pro = new double[5];
        for (int n = 1; n <= 5; n++) {
            double idle = Bot.survival(n, runs, Bot.Policy.IDLE);
            react[n - 1] = Bot.survival(n, runs, Bot.Policy.REACT);
            hold[n - 1] = Bot.survival(n, runs, Bot.Policy.HOLD);
            flee[n - 1] = Bot.survival(n, runs, Bot.Policy.FLEE);
            panic[n - 1] = Bot.survival(n, runs, Bot.Policy.PANIC);
            pro[n - 1] = Bot.survival(n, runs, Bot.Policy.PRO);
            System.out.printf("    %-6d %6.0f%% %6.0f%% %6.0f%% %6.0f%% %6.0f%% %6.0f%%%n",
                    n, idle * 100, react[n - 1] * 100, hold[n - 1] * 100,
                    flee[n - 1] * 100, panic[n - 1] * 100, pro[n - 1] * 100);
        }

        // The shape, not the numbers. A player who does nothing must die on
        // every night of the week, not just the two ends of it.
        for (int n = 1; n <= 5; n++) {
            check("doing nothing dies on night " + n,
                    Bot.survival(n, runs, Bot.Policy.IDLE) == 0.0);
        }

        // The ladder the design wants: answering the room beats doing
        // nothing, and moving beats standing still and answering. The
        // second one is the whole skill of the game and it is the check
        // that the first sweep failed -- see the note in Bot.
        double reactMean = mean(react);
        double holdMean = mean(hold);
        check("walking beats parking: HOLD beats REACT over the week",
                holdMean > reactMean);

        // THE WEEK GETS HARDER -- the thing Chase asked the franchise for,
        // and the check that could not fail before 2026-10-01. It used to
        // compare HOLD's array, and HOLD scored 0% on every night, so the
        // comparison was 0 <= 0 and it passed on main for as long as the
        // game existed. It is a real comparison now, and it is a real
        // reading, because the engine now has a die roll *and* a jittered
        // interval: without either, the night is a script and the table is
        // a coin flip on the parameters. See the note in Threat#update.
        for (int n = 1; n < 5; n++) {
            check("night " + (n + 1) + " is not easier than night " + n,
                    hold[n - 1] >= hold[n] - 0.02);
        }

        // THE SHOCK IS NOT A UNIVERSAL ANSWER, and this is the check the
        // redesign was for. Before 2026-10-01 the shock cleared the room of
        // all three threats, so the policy that spammed it did not have to
        // know which counter belonged to which threat -- and it won:
        // PANIC 86%, FLEE 85%, HOLD 79%. The shock removes the pursuer
        // only now, so panicking wastes charges on Ballora and Foxy and has
        // none left when Freddy is the one in the room. A game whose best
        // strategy is to panic is a game whose counters are decoration.
        check("panicking is worse than playing the counters: HOLD beats PANIC",
                holdMean > mean(panic));

        // The finding that is still open, and it is now a *bot* finding
        // rather than a balance one: the moving policies converge. Running
        // from Funtime Freddy is not worse than patrolling, and moving at
        // random is not worse either, because in a line the player is
        // faster than everything in it and the direction of a step barely
        // matters. Printed rather than asserted, because a fix should be
        // allowed to break it.
        double best = Math.max(holdMean, Math.max(mean(flee), mean(panic)));
        double worst = Math.min(holdMean, Math.min(mean(flee), mean(panic)));
        System.out.printf("    the moving policies: HOLD %.0f%%, FLEE %.0f%%, PANIC %.0f%%"
                        + " (spread %.0f points)%n",
                holdMean * 100, mean(flee) * 100, mean(panic) * 100,
                (best - worst) * 100);

        // PRO -- the policy that plays all three counters -- is printed and
        // not asserted, because it is a first attempt and it is not good
        // yet. It reads 0% on the last three nights and dies to Funtime
        // Freddy: the stop is the right answer to Ballora and it is also
        // what Freddy is built to punish, so a policy that uses it has to
        // choose where to stop and this one does not. See Bot#pro. The
        // redesign cannot be judged until something in the suite can play
        // it, and that is the next piece of work.
        System.out.printf("    the policy that plays all three counters: PRO %.0f%%"
                        + " (n1 %.0f%%, n5 %.0f%%)%n",
                mean(pro) * 100, pro[0] * 100, pro[4] * 100);

        // The building has to be able to surprise you. Before the die was
        // added to Threat.update, every seed produced the same night: the
        // sweep returned 0% or 100% and nothing in between, because there
        // was nothing random in the game at all. Two seeds, two nights.
        java.util.Set<Integer> shapes = new java.util.HashSet<>();
        for (int i = 0; i < 24; i++) {
            shapes.add(Bot.run(3, 1000L + i * 7919L, Bot.Policy.HOLD).arrivals);
        }
        check("the same night is not the same night twice", shapes.size() > 1);

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

        // WHO KILLS YOU. Survival says how often; this says who, and it is
        // the number that found the open design problem. Printed rather
        // than asserted, because a fix should be allowed to change it.
        System.out.print("    what ends the night (HOLD):");
        for (int n = 1; n <= 5; n++) {
            int[] k = Bot.killers(n, runs, Bot.Policy.HOLD);
            System.out.printf(" n%d[ballora %d, foxy %d, freddy %d]", n, k[0], k[1], k[2]);
        }
        System.out.println();

        // The dial the week actually turns on. Printed rather than
        // asserted: the flip is a measurement, and a measurement that is
        // asserted stops being one.
        System.out.print("    the reaction margin (seconds):");
        for (int n = 1; n <= 5; n++) {
            System.out.printf(" n%d=%.2f", n, Bot.flipReaction(n, 24));
        }
        System.out.println();
    }

    static double mean(double[] xs) {
        double t = 0;
        for (double x : xs) t += x;
        return t / xs.length;
    }

    // ---------------------------------------------------------------- plumbing

    /**
     * Every cue the engine can emit has a file, and the three of them are
     * not the same file.
     *
     * This is the check that was missing, and the game shipped without it.
     * `GameScreen.play` falls back per family -- `here_*` to `at_door`,
     * `step_*` to `footstep`, `lost_*` to `door_close` -- which is right,
     * because a cue that resolves to nothing is worse than a cue that
     * resolves to the wrong file. But the fallbacks are a safety net, and
     * with no files behind them all three of these characters sounded
     * identical. In a game whose three threats differ only in *what they
     * follow*, and whose only channel for saying which one just walked in
     * is the sound, that is not a rough edge -- it is the information the
     * game is made of, missing.
     *
     * The cue names are derived from the threats rather than written down,
     * so adding a fourth one fails here until it has a voice.
     *
     * Paths are relative to the working directory, like the engine's own
     * `web/aside.html` check. Run it from the repository root.
     */
    static void cues() {
        section("the voices");

        java.io.File dir = new java.io.File("audio");
        if (!dir.isDirectory()) {
            check("audio/ is readable from here (run from the repository root)", false);
            return;
        }

        Game g = new Game(1, 1);
        java.util.List<String> heres = new java.util.ArrayList<>();
        for (Threat t : g.threats) {
            String here = "here_" + t.key;
            String step = "step_" + t.key;
            check(here + " has a file", hasCue(dir, here));
            check(step + " has a file", hasCue(dir, step));
            heres.add(here);
            // Only a threat that can lose you needs a cue for losing you,
            // and only one of the three can: Ballora is the one that gives
            // up when the building has been quiet long enough.
            if (t.rule == Threat.Rule.SOUND) {
                check("lost_" + t.key + " has a file (it is the one that gives up)",
                        hasCue(dir, "lost_" + t.key));
            }
        }
        check("the shock has a file", hasCue(dir, "shock"));

        // And the point of the whole section: three names, three files.
        check("the three of them do not share one voice",
                heres.stream().distinct().count() == g.threats.size());
        for (int i = 0; i < heres.size(); i++) {
            for (int j = i + 1; j < heres.size(); j++) {
                check(heres.get(i) + " and " + heres.get(j) + " are different sounds",
                        !sameFile(dir, heres.get(i), heres.get(j)));
            }
        }
    }

    /** Does `audio/<name>.<ext>` exist for any extension the loader reads? */
    static boolean hasCue(java.io.File dir, String name) {
        for (String ext : new String[]{".wav", ".mp3", ".aiff", ".aif", ".m4a", ".aac"}) {
            if (new java.io.File(dir, name + ext).isFile()) return true;
        }
        return false;
    }

    /** The two names resolve to the same bytes on disk. */
    static boolean sameFile(java.io.File dir, String a, String b) {
        java.io.File fa = find(dir, a), fb = find(dir, b);
        if (fa == null || fb == null) return false;
        try {
            return java.util.Arrays.equals(
                    java.nio.file.Files.readAllBytes(fa.toPath()),
                    java.nio.file.Files.readAllBytes(fb.toPath()));
        } catch (Exception e) {
            return false;
        }
    }

    static java.io.File find(java.io.File dir, String name) {
        for (String ext : new String[]{".wav", ".mp3", ".aiff", ".aif", ".m4a", ".aac"}) {
            java.io.File f = new java.io.File(dir, name + ext);
            if (f.isFile()) return f;
        }
        return null;
    }

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
