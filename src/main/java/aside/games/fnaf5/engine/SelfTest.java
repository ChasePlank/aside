package aside.games.fnaf5.engine;

import aside.games.fnaf5.MouseMap;

import java.nio.file.Files;
import java.nio.file.Path;

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
 * <b>It has since had to change which policy it reads, twice, and both
 * times for the same reason: the check was pointed at whatever the suite
 * believed the competent player was.</b> HOLD was that belief, then HOLD
 * was measured and found to play two of the three counters, and then the
 * 2026-10-01 pass made never stopping fatal -- so HOLD is flat at the
 * bottom of the table by design and a week that gets harder cannot show up
 * in its column. The ladder is asserted against PRO now. <b>When a check
 * is written against a policy, it is really written against an assumption
 * about that policy</b>, and the assumption is the part that goes stale.
 *
 * <b>2026-10-01, later: three of these checks are new and they are the
 * redesign written as assertions.</b> The shock removes the pursuer and
 * nothing else; the feed is Funtime Foxy's fuel, so he is faster with the
 * monitor up than with it down and the other two do not care; and Ballora's
 * counter fits inside the grace on every night, which it did not before.
 * Each one is a rule the game now rests on, and each one is here so that a
 * later tuning pass cannot quietly undo it -- which is exactly what
 * happened to the "week gets harder" check the first time.
 *
 * <p><b>2026-10-02: mutation-tested, and every constant is caught.</b>
 * Breaking one of the thirteen constants this game is made of and running the
 * suite (tools/mutate.sh):
 *
 * <pre>
 *   BALLORA_PATIENCE 0.8 -> 2.0      caught, 14 checks fail
 *   SHOCK_TIME       0.55 -> 2.00    caught, 13 checks fail
 *   BALLORA_GRACE    1.40 -> 3.00    caught,  6 checks fail
 *   STILL_PACE       2.5 -> 1.0      caught,  6 checks fail
 *   MOVE_TIME        1.50 -> 0.20    caught,  6 checks fail
 *   FEED_PACE        2.0 -> 1.0      caught,  4 checks fail
 *   BALLORA_COOLDOWN 10.0 -> 0.5     caught,  2 checks fail
 *   STILL_WINDOW     3.0 -> 30.0     caught,  2 checks fail
 *   SOUND_MEMORY     6.0 -> 60.0     caught,  2 checks fail
 *   INTERVAL_JITTER  0.5 -> 0.0      caught,  2 checks fail
 * </pre>
 *
 * <p>That is the answer to the question this file's own comment raises -- a
 * later tuning pass cannot quietly undo a rule, because it cannot change any
 * of these numbers without something failing. The three constants not listed
 * (HOUR_SECONDS, SHOCK_FLASH, CUE_EVERY) are the clock and two presentation
 * timings, and are not expected to move the ladder.
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
        phone();
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

        // BALLORA'S COUNTER HAS TO FIT INSIDE HER OWN CLOCK, on every night
        // of the week. It did not before 2026-10-01: her patience was 1.8
        // and night 5's grace was also 1.8, so the book answer to her was a
        // coin flip on the last night and the sweep's competent policy died
        // to her on every seed of it. Then it fitted inside the night's
        // grace and still did not work, because the night's grace is not
        // the clock she is on any more -- see the next check.
        for (int n = 1; n <= 5; n++) {
            Game gn = new Game(n, 1);
            check("silence loses Ballora before her clock runs out on night " + n,
                    Game.BALLORA_PATIENCE < gn.graceFor(byKey(gn, "ballora")));
        }

        // THE RULE THE WHOLE 2026-10-01 PASS TURNS ON: her clock is shorter
        // than a step, so walking out of her room is not an answer to her.
        // Every other threat is answered by walking -- Freddy is slower than
        // the player and Foxy follows a feed that costs nothing to move --
        // and while Ballora was too, one strategy satisfied all three
        // demands and the design's contradiction was never felt. Measured
        // with the rule in place: HOLD, the policy that never stops, goes
        // from 90% over the week to 5%.
        check("Ballora's clock is shorter than a step",
                Game.BALLORA_GRACE < Game.MOVE_TIME);
        for (int n = 1; n <= 5; n++) {
            Game gn = new Game(n, 1);
            check("and it is her clock on night " + n + ", not the night's",
                    gn.graceFor(byKey(gn, "ballora")) == Game.BALLORA_GRACE);
            check("while the other two are still on the night's grace on night " + n,
                    gn.graceFor(byKey(gn, "freddy")) == gn.grace());
        }

        // AND SILENCE HAS TO BUY SOMETHING. A stop that only postpones the
        // next stop is a stop the player makes out of fear; this is the
        // stretch of deafness after she gives up, and it is what makes the
        // counter profitable rather than merely necessary. Measured, 8, 10
        // and 14 are indistinguishable -- what matters is that it is not
        // zero, so that is what is asserted.
        check("giving up buys time: Ballora is deaf for a while",
                Game.BALLORA_COOLDOWN > 0);

        // The grace has to stay above the worst trip, or the two counters
        // cannot both be used on the same night. This is why the grace
        // table came back up in this pass: at 1.8 on night 5 the competent
        // policy reads 0%, because the player owes stillness to one threat
        // and a shock to another and the night does not contain both.
        for (int n = 1; n <= 5; n++) {
            Game gn = new Game(n, 1);
            check("night " + n + " has room for a move and a shock",
                    gn.grace() >= Game.worstTrip());
        }

        // The week's ramp lives in Freddy's pace now, so it has to actually
        // ramp. He is the only thing in the building that punishes standing
        // still, and standing still is what Ballora demands -- so how fast
        // he walks is how expensive her counter is, which is the whole
        // difficulty of a night.
        for (int n = 1; n < 5; n++) {
            Game a = new Game(n, 1), b = new Game(n + 1, 1);
            check("Freddy is at least as fast on night " + (n + 1)
                            + " as on night " + n,
                    b.freddyPace() >= a.freddyPace() - 1e-9);
        }
        Game first = new Game(1, 1), last = new Game(5, 1);
        check("and the last night's Freddy is faster than the first night's",
                last.freddyPace() > first.freddyPace());

        // STILLNESS HAS A PRICE, AND IT IS THE PURSUER'S. Every other cost
        // in the building is charged to a player who is *doing* something:
        // a step is a noise and the noise is Ballora's, and the feed is
        // Funtime Foxy's fuel. A player who never moves pays neither, and
        // the sweep said so -- REACT, the policy that answers the room and
        // never takes a step, read 92% on the first night and 20% over the
        // week, second only to the policy that plays all three counters.
        // See Game.STILL_WINDOW.
        //
        // The window has two bounds and both are rules rather than numbers:
        // it has to be longer than a step, or a player who is walking is
        // charged for the gaps between their own footsteps; and longer than
        // a stop for Ballora, or the counter the whole pass is built on is
        // taxed by the fix meant to tax the turtle.
        check("standing still is charged for, but not while you are walking",
                Game.STILL_WINDOW > Game.MOVE_TIME);
        check("and not while you are stopping for Ballora",
                Game.STILL_WINDOW > Game.BALLORA_PATIENCE);
        check("and the price is real: the pursuer closes faster",
                Game.STILL_PACE > 1.0);

        Game st = new Game(1, 42);
        Threat stFreddy = byKey(st, "freddy");
        Threat stBallora = byKey(st, "ballora");
        Threat stFoxy = byKey(st, "foxy");
        double walking = st.interval(stFreddy);
        st.stillTime = Game.STILL_WINDOW;
        double standing = st.interval(stFreddy);
        check("the pursuer closes on a player who has stopped moving",
                standing < walking);
        check("and it is worth exactly STILL_PACE",
                Math.abs(walking / Game.STILL_PACE - standing) < 1e-9);

        // And stillness does not move the other two. Ballora is blind and
        // Foxy follows the feed; neither of them can tell whether you have
        // taken a step, and only one of the three threats is allowed to
        // charge you for not taking one.
        Game so = new Game(1, 42);
        Threat soBallora = byKey(so, "ballora");
        Threat soFoxy = byKey(so, "foxy");
        double balStill = so.interval(soBallora);
        double foxyStill = so.interval(soFoxy);
        so.stillTime = Game.STILL_WINDOW * 3;
        check("standing still does not move Ballora", so.interval(soBallora) == balStill);
        check("and it does not move Funtime Foxy", so.interval(soFoxy) == foxyStill);
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
     * night is made of is asserted to be the engine's own value, and the three
     * rules that make the three threats different problems are asserted to be
     * the engine's own rules.
     *
     * <p>What is <i>not</i> checked here is the RNG or the update loop. The
     * page reproduces {@code java.util.Random} in BigInt -- including
     * {@code nextInt}, which is a rejection sampler rather than a modulo, and
     * the die in this game is a d20 against the night's AI level -- and that
     * was verified by driving both engines under the same scripted policy:
     * night one on seed 1001 produces identical traces every five seconds (the
     * room you are in, all three threats' rooms, and the shocks left) and both
     * end JUMPSCARED to the same killer at the same moment. It is not
     * checkable from Java without a JavaScript engine, and the alternative (a
     * page that deals its own nights) would make the two builds different
     * games with the same rules.
     */
    static void phone() {
        section("the phone build");

        Path out = Path.of("web", "fnaf5.html");
        if (!Files.exists(out)) {
            System.out.println("       (no " + out + " from here -- run from the repository root)");
            return;
        }
        String page;
        try {
            page = Files.readString(out);
        } catch (Exception e) {
            check("web/fnaf5.html can be read", false);
            return;
        }
        try {
            check("web/fnaf5.html is current -- regenerate it with aside.games.fnaf5.WebRental",
                    aside.games.fnaf5.WebRental.html().equals(page));
        } catch (Exception e) {
            check("web/fnaf5.html is current -- regenerate it with aside.games.fnaf5.WebRental",
                    false);
        }

        // The rules, as numbers. A port whose feed pace is 1.8 rather than 2.0
        // plays differently and looks identical.
        check("the page carries the move",
                page.contains("moveTime: " + aside.games.fnaf5.WebRental.num(Game.MOVE_TIME)));
        check("the page carries the shock",
                page.contains("shockTime: " + aside.games.fnaf5.WebRental.num(Game.SHOCK_TIME)));
        check("the page carries how long the building remembers a sound",
                page.contains("soundMemory: " + aside.games.fnaf5.WebRental.num(Game.SOUND_MEMORY)));
        check("the page carries Ballora's patience",
                page.contains("balloraPatience: " + aside.games.fnaf5.WebRental.num(Game.BALLORA_PATIENCE)));
        check("the page carries Ballora's clock",
                page.contains("balloraGrace: " + aside.games.fnaf5.WebRental.num(Game.BALLORA_GRACE)));
        check("the page carries how long silence buys",
                page.contains("balloraCooldown: " + aside.games.fnaf5.WebRental.num(Game.BALLORA_COOLDOWN)));
        check("the page carries the feed pace",
                page.contains("feedPace: " + aside.games.fnaf5.WebRental.num(Game.FEED_PACE)));
        check("the page carries the stillness window",
                page.contains("stillWindow: " + aside.games.fnaf5.WebRental.num(Game.STILL_WINDOW)));
        check("the page carries the stillness pace",
                page.contains("stillPace: " + aside.games.fnaf5.WebRental.num(Game.STILL_PACE)));
        check("the page carries the interval jitter",
                page.contains("intervalJitter: " + aside.games.fnaf5.WebRental.num(Game.INTERVAL_JITTER)));

        // The tables, which are the whole of the difficulty ramp.
        for (String t : new String[]{"baseInterval", "freddyPace", "grace", "shockAllowance", "aiLevel"}) {
            StringBuilder want = new StringBuilder(t + ": [");
            for (int n = 1; n <= 5; n++) {
                Game g = new Game(n, 1);
                if (n > 1) want.append(", ");
                want.append(switch (t) {
                    case "baseInterval" -> aside.games.fnaf5.WebRental.num(g.baseInterval());
                    case "freddyPace" -> aside.games.fnaf5.WebRental.num(g.freddyPace());
                    case "grace" -> aside.games.fnaf5.WebRental.num(g.grace());
                    case "shockAllowance" -> String.valueOf(Game.shockAllowance(n));
                    default -> String.valueOf(Game.aiLevel(n));
                });
            }
            want.append("]");
            check("the page carries the " + t + " table", page.contains(want));
        }

        // Every room has a label, keyed by the name the engine uses. The
        // page looks a room up by name, so an array here would index by a
        // string and every label would come back undefined -- which is what
        // happened, and which no check on the numbers could have caught.
        boolean tags = true;
        for (int i = 0; i < Room.COUNT; i++) {
            tags &= page.contains(Room.ALL[i].name() + ": \"" + Room.ALL[i].tag + "\"");
        }
        check("the page labels every room by the name the engine uses", tags);

        // The art. The desktop draws five room photographs and three "it is
        // in the room with you" frames; the phone carries its own downscaled
        // copies, built by tools/fnaf5-phone-art.py and inlined as data URIs,
        // because the build has to be one file. Every room has to have one,
        // keyed by the name the engine uses -- the page looks a room up by
        // name, and an array here would index by a string and put the player
        // in the wrong photograph while looking perfectly fine.
        for (int i = 0; i < Room.COUNT; i++) {
            check("the page carries the art for " + Room.ALL[i].name(),
                    page.contains("\"room:" + Room.ALL[i].name() + "\":\"data:image/webp;base64,"));
        }
        for (String key : new String[]{"ballora", "foxy", "freddy"}) {
            check("the page carries the frame for " + key + " in the room with you",
                    page.contains("\"here:" + key + "\":\"data:image/webp;base64,"));
            check("the page carries the scare frame for " + key,
                    page.contains("\"scare:" + key + "\":\"data:image/webp;base64,"));
        }

        // And the three rules, which are the whole difference between the
        // three threats.
        Game g = new Game(1, 1);
        for (Threat t : g.threats) {
            check("the page carries " + t.name + " following " + t.rule,
                    page.contains("name:\"" + t.name + "\", key:\"" + t.key
                            + "\", rule:\"" + t.rule.name() + "\", home:\""
                            + t.home.name() + "\", pace:"
                            + aside.games.fnaf5.WebRental.num(t.pace)));
        }
    
        // The sound. Ballora is blind and follows sound, so the cue is not atmosphere here -- it is
        // the only channel that says something is next door, and the engine records what
        // happened when it was missing: "the bot walked straight into the room she had
        // just left, on every seed, because the only channel that says 'something is next
        // door' was never fired."
        check("the page carries the shared synthesiser",
                page.contains("function voice(") && page.contains("function sfx("));
        check("the page has a voice for the camera_down cue",
                page.contains("case \"camera_down\""));
        check("the page has a voice for the camera_up cue",
                page.contains("case \"camera_up\""));
        check("the page has a voice for the chime_6am cue",
                page.contains("case \"chime_6am\""));
        check("the page has a voice for the footstep cue",
                page.contains("case \"footstep\""));
        check("the page has a voice for the scare_sprint cue",
                page.contains("case \"scare_sprint\""));
        check("the page has a voice for the shock cue",
                page.contains("case \"shock\""));
        check("the page has a voice for the static cue",
                page.contains("case \"static\""));
        check("the page has a voice for the here_* cues",
                page.contains("startsWith(\"here_\")"));
        check("the page has a voice for the step_* cues",
                page.contains("startsWith(\"step_\")"));
        check("the page has a voice for the lost_* cues",
                page.contains("startsWith(\"lost_\")"));
}

    static void survival() {
        section("the week");

        // 200 rather than 60, and the reason is the monotonicity check: the
        // first two nights of the week are three hundredths of a pace
        // apart, so at 60 seeds the sample noise is wider than the ramp it
        // is being asked to detect. The suite is a few seconds slower and
        // the reading is a reading.
        int runs = 200;
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

        // THE LADDER, and it is a different ladder from the one this block
        // was written against. It used to assert that HOLD -- "answer the
        // room and keep walking" -- beat REACT and PANIC, because HOLD was
        // believed to be the competent player. It is not one: it plays two
        // of the three counters and never stops, and since Ballora's clock
        // became shorter than a step, never stopping is fatal. So the old
        // check would now be asserting the wrong thing in the wrong
        // direction.
        //
        // The ladder is asserted against PRO -- the policy that actually
        // plays all three counters -- and it is the reading the whole
        // 2026-10-01 pass exists to produce: the competent policy is the
        // best one, and every policy that skips a counter is behind it.
        // Measured over 200 seeds a night after the stillness rule landed:
        // PRO 62%, FLEE 11%, HOLD 6%, PANIC 1%, REACT 0%, IDLE 0%.
        double reactMean = mean(react);
        double holdMean = mean(hold);
        double proMean = mean(pro);
        check("playing all three counters beats never moving: PRO beats REACT",
                proMean > reactMean);
        check("and beats never stopping: PRO beats HOLD", proMean > holdMean);
        check("and beats never stopping in the other direction: PRO beats FLEE",
                proMean > mean(flee));

        // AND NEVER MOVING IS WORSE THAN WALKING. This is the other half of
        // the ladder, and it was upside down until 2026-10-01: REACT read
        // 20% over the week against HOLD's 5% and FLEE's 10%, because never
        // moving meant never making a sound, so Ballora never found it and
        // its whole night was a race between Funtime Freddy's arrival rate
        // and five charges -- a race the charges won on the first night.
        // The price of stillness is the pursuer's now (see
        // Game.STILL_WINDOW), and this is the reading that says so: the
        // policy that never takes a step is behind both of the policies
        // that do.
        check("a policy that never moves is worse than one that walks: REACT behind HOLD",
                reactMean < holdMean);
        check("and behind the one that runs: REACT behind FLEE",
                reactMean < mean(flee));

        // THE WEEK GETS HARDER -- the thing Chase asked the franchise for,
        // and the check that could not fail before 2026-10-01. It used to
        // compare HOLD's array, and HOLD scored 0% on every night, so the
        // comparison was 0 <= 0 and it passed on main for as long as the
        // game existed. It is a real comparison now, and it is a real
        // reading, because the engine now has a die roll *and* a jittered
        // interval: without either, the night is a script and the table is
        // a coin flip on the parameters. See the note in Threat#update.
        //
        // <b>It is measured against PRO now rather than HOLD, and that is
        // the second time this check has had to change which policy it
        // reads.</b> HOLD is flat at the bottom of the table by design --
        // it dies to Ballora on every night of the week, which is the
        // redesign working -- so a week that gets harder cannot show up in
        // its column. The week's difficulty is the competent player's, and
        // the tolerance is 0.05 because the reading is a 200-seed sample
        // and the first two nights are three hundredths of a pace apart.
        for (int n = 1; n < 5; n++) {
            check("night " + (n + 1) + " is not easier than night " + n,
                    pro[n - 1] >= pro[n] - 0.05);
        }

        // THE SHOCK IS NOT A UNIVERSAL ANSWER, and this is the check the
        // redesign was for. Before 2026-10-01 the shock cleared the room of
        // all three threats, so the policy that spammed it did not have to
        // know which counter belonged to which threat -- and it won:
        // PANIC 86%, FLEE 85%, HOLD 79%. The shock removes the pursuer
        // only now, so panicking wastes charges on Ballora and Foxy and has
        // none left when Freddy is the one in the room. A game whose best
        // strategy is to panic is a game whose counters are decoration.
        check("panicking is worse than playing the counters: PRO beats PANIC",
                proMean > mean(panic));

        // THE FINDING THAT WAS OPEN, AND IS NOT ANY MORE. This block used to
        // print the spread across HOLD, FLEE and PANIC as evidence that the
        // moving policies converged -- seven points between them, with the
        // competent one last, because in a line the player is faster than
        // everything in it and the direction of a step barely mattered.
        // That was true while walking answered all three threats. It is not
        // true now: the policies that never stop are the policies that die,
        // and the spread between them is the size of the redesign.
        double best = Math.max(holdMean, Math.max(mean(flee), mean(panic)));
        double worst = Math.min(holdMean, Math.min(mean(flee), mean(panic)));
        System.out.printf("    the policies that skip a counter: HOLD %.0f%%, FLEE %.0f%%,"
                        + " PANIC %.0f%% (spread %.0f points)%n",
                holdMean * 100, mean(flee) * 100, mean(panic) * 100,
                (best - worst) * 100);

        // PRO -- the policy that plays all three counters -- is printed
        // beside the ladder it is now at the top of. It reads 61% over the
        // week against HOLD's 5%, and its deaths are Ballora's: the stop is
        // the answer to her, it is what Freddy is built to punish, and the
        // whole economy of the night is the price of that trade. See
        // Bot#pro for the three rules of its own that had to change with
        // the game.
        System.out.printf("    the policy that plays all three counters: PRO %.0f%%"
                        + " (n1 %.0f%%, n5 %.0f%%)%n",
                mean(pro) * 100, pro[0] * 100, pro[4] * 100);

        // AND THE DIRECTION OF A STEP, which is the last piece of the open
        // finding and the one that was never measured. The note in Game#step
        // claimed for a year that movement direction barely matters, because
        // the player is faster than everything in the building -- and the
        // suite contradicted it in prose without ever testing it. TOWARD is
        // PRO with one thing changed: when it moves, it walks toward the
        // nearest thing it can perceive instead of away. Everything else --
        // the shock, the feed, the stop -- is identical, so the gap between
        // the two columns is the value of the direction alone.
        double[] toward = new double[5];
        for (int n = 1; n <= 5; n++) toward[n - 1] = Bot.survival(n, runs, Bot.Policy.TOWARD);
        double towardMean = mean(toward);
        System.out.printf("    walking toward what you can perceive: TOWARD %.0f%%"
                        + " (n1 %.0f%%, n5 %.0f%%)%n",
                towardMean * 100, toward[0] * 100, toward[4] * 100);
        check("walking away from what you can perceive beats walking toward it",
                proMean - towardMean > 0.15);

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

        // THE FEED AND THE FEET -- the instrument that found the 2026-10-01
        // finding, kept in the suite so the next pass does not have to
        // rebuild it. The design says looking costs and standing still is
        // how you lose Ballora; these two numbers say whether either is
        // true. They are printed and not asserted, because they are a
        // reading of the game rather than a contract -- but they are the
        // reading that matters. HOLD, the best simple policy, never puts
        // the feed down and never stands still; PRO, which plays all three
        // counters, does both and reads worse. See the note in Bot.
        System.out.printf("    the feed and the feet (HOLD / PRO):"
                        + " monitor up %.0f%% / %.0f%%, standing still %.0f%% / %.0f%%%n",
                Bot.monitorDuty(3, runs, Bot.Policy.HOLD) * 100,
                Bot.monitorDuty(3, runs, Bot.Policy.PRO) * 100,
                Bot.stillness(3, runs, Bot.Policy.HOLD) * 100,
                Bot.stillness(3, runs, Bot.Policy.PRO) * 100);

        System.out.print("    what ends the night (PRO):");
        for (int n = 1; n <= 5; n++) {
            int[] k = Bot.killers(n, runs, Bot.Policy.PRO);
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
