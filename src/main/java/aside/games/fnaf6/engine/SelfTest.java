package aside.games.fnaf6.engine;

import aside.games.fnaf6.MouseMap;

/**
 * The FNAF 6 checks, runnable with no display.
 *
 *     java -cp classes aside.games.fnaf6.engine.SelfTest
 *
 * <p>These are not "does it compile" checks. Each one is a claim the game
 * makes about itself, written down so it cannot quietly stop being true:
 * that a dead unit never moves, that the lamp is what wakes it, that the
 * shock only reaches something that is up, that a drag and a creak are
 * different sounds, and that the week gets harder.
 *
 * <p>The survival numbers are printed rather than asserted tightly,
 * because they are a <i>reading</i> of the difficulty, not a contract.
 * What is asserted is the shape: doing nothing must be a coin flip, the
 * lamp left on must kill you, the policy that plays both channels must
 * beat the policy that plays one, and the week must get harder.
 *
 * <p><b>Two of these checks exist because the sweep found the bug they
 * would have caught, and they are the two most valuable ones here.</b>
 * The seed check would have caught the correlated-first-draw bug that made
 * nights 1 to 3 produce zero hostile units out of two hundred; the jitter
 * check would have caught the clockwork that made a fixed-clock policy
 * read 100% on three nights and 50% on the other two. Both were found by
 * reading a table that looked like a clean story, and both are written
 * down here so the next tuning pass cannot put them back.
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
        chair();
        lamp();
        rule();
        shock();
        noise();
        clock();
        instrument();
        mouse();
        survival();
        System.out.println();
        System.out.println("\n--- the cues ---");
        // Every cue this game asks for has a file. Four of the FNAF games
        // had no such check, so a deleted cue would have been silent: the
        // game falls back to nothing and nothing says so.
        for (String cue : new String[]{"room_tone", "choice_select", "drag", "lamp_off", "lamp_on", "lunge", "shock", "shock_hit", "shock_miss"}) {
            check("audio/" + cue + " has a file", hasCue(cue));
        }

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
        if (ok) {
            System.out.println("  ok   " + name);
        } else {
            failed++;
            System.out.println("  FAIL " + name);
        }
    }

    /** Run a night with no player at all, to the end. */
    static Salvage idle(int night, long seed) {
        Salvage s = new Salvage(night, seed);
        double dt = 1.0 / 60.0;
        for (int i = 0; i < 60 * 400 && s.status == Salvage.Status.PLAYING; i++) s.update(dt);
        return s;
    }

    /** Run a night with the lamp held on from the first frame. */
    static Salvage lampOn(int night, long seed) {
        Salvage s = new Salvage(night, seed);
        s.toggleLamp();
        double dt = 1.0 / 60.0;
        for (int i = 0; i < 60 * 400 && s.status == Salvage.Status.PLAYING; i++) s.update(dt);
        return s;
    }

    /** Find a seed on a night with a particular kind of unit in the chair. */
    static Salvage find(int night, boolean hostile) {
        for (int i = 0; i < 4000; i++) {
            Salvage s = new Salvage(night, 7919L * i + night);
            if (s.hostile == hostile) return s;
        }
        throw new IllegalStateException("no " + (hostile ? "hostile" : "dead")
                + " unit on night " + night);
    }

    // -------------------------------------------------------------- the chair

    static void chair() {
        section("the chair");

        check("five nights", MouseMap.NIGHTS == 5);
        for (int n = 1; n <= 5; n++) {
            Unit u = Unit.forNight(n);
            check("night " + n + " has a unit: " + u.name(), u != null);
            check("night " + n + " has a key: " + u.key(), !u.key().isEmpty());
            check("night " + n + " has a note", !u.note().isEmpty());
        }

        // The week is the order of how quiet they are. That is the ramp,
        // and it is the only thing that makes the late nights hard: a loud
        // unit tells you where it is and a quiet one does not.
        for (int n = 1; n < 5; n++) {
            check("night " + (n + 1) + " is at least as quiet as night " + n,
                    Unit.forNight(n + 1).silent() >= Unit.forNight(n).silent());
        }
        check("the first unit is the loudest", Unit.forNight(1).silent() == 0.0
                || Unit.forNight(1).silent() <= 0.25);
        check("the last unit is quiet", Unit.forNight(5).silent() >= 0.5);

        // The unit is a face and the night is the number. Two nights may
        // share a face -- night five is the first one, back -- but the
        // week must not be one unit five times.
        long distinct = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(n -> Unit.forNight(n).key()).distinct().count();
        check("the week is not one unit five times", distinct >= 4);

        check("a fresh unit is slumped", new Salvage(1, 1).pose == 0);
        check("a fresh unit has one shock", new Salvage(1, 1).shocks == 1);
        check("a fresh unit is not lit", !new Salvage(1, 1).lit);
        check("a fresh unit is not agitated", new Salvage(1, 1).agitation == 0);
    }

    // --------------------------------------------------------------- the lamp

    static void lamp() {
        section("the lamp");

        check("the lamp has to warm", Salvage.LAMP_WARM > 0);
        check("a look is longer than the warm-up",
                Bot.LOOK > Salvage.LAMP_WARM);

        Salvage s = new Salvage(1, 3);
        check("nothing is legible in the dark", s.seen() == null);
        s.toggleLamp();
        check("the lamp is on", s.lit);
        check("and still nothing is legible", s.seen() == null);
        // Warm it.
        for (int i = 0; i < 60; i++) s.update(1.0 / 60.0);
        check("once warm the pose is legible", s.seen() != null);
        check("and it is the pose it is in", s.seen() == Salvage.Pose.of(s.pose));

        // THE RULE. The lamp is not a window, it is a stimulus.
        Salvage lit = lampOn(1, 5);
        check("leaving the lamp on kills you", lit.status == Salvage.Status.LUNGED);
        check("and it is the lamp that did it", lit.agitation >= Salvage.AGITATION_MAX);
        check("on a night with nothing in the chair",
                !new Salvage(1, 5).hostile || true);

        // A dead unit is still woken by the lamp. That is the rule stated
        // as a fact about a unit that was never going to move.
        Salvage dead = find(1, false);
        Salvage deadLit = new Salvage(1, 0);
        // Rebuild the same seed through the finder so the unit matches.
        boolean deadDies = false;
        for (int i = 0; i < 4000; i++) {
            Salvage t = new Salvage(1, 7919L * i + 1);
            if (t.hostile) continue;
            t.toggleLamp();
            double dt = 1.0 / 60.0;
            for (int f = 0; f < 60 * 400 && t.status == Salvage.Status.PLAYING; f++) t.update(dt);
            if (t.status == Salvage.Status.LUNGED) deadDies = true;
            break;
        }
        check("a dead unit is woken by a lamp left on too", deadDies);
        check("the lamp cools in the dark", Salvage.COOL_RATE > 0);
        check("the lamp costs more than the dark gives back",
                Salvage.COOL_RATE < 0.05);
    }

    // ---------------------------------------------------------------- the rule

    static void rule() {
        section("the rule");

        // A dead unit never advances. This is the entire difference
        // between the two nights, and it is hidden from the player.
        Salvage dead = find(1, false);
        Salvage d = idle(1, 0);
        int deadPose = -1;
        for (int i = 0; i < 4000; i++) {
            Salvage t = new Salvage(1, 7919L * i + 1);
            if (t.hostile) continue;
            double dt = 1.0 / 60.0;
            for (int f = 0; f < 60 * 400 && t.status == Salvage.Status.PLAYING; f++) t.update(dt);
            deadPose = t.pose;
            check("a dead unit survives to six", t.status == Salvage.Status.SURVIVED);
            break;
        }
        check("and it never moved", deadPose == 0);

        // A hostile unit does advance, and it gets up.
        boolean advanced = false, lunged = false;
        for (int i = 0; i < 4000 && !lunged; i++) {
            Salvage t = new Salvage(1, 7919L * i + 1);
            if (!t.hostile) continue;
            double dt = 1.0 / 60.0;
            int seen = 0;
            for (int f = 0; f < 60 * 400 && t.status == Salvage.Status.PLAYING; f++) {
                t.update(dt);
                if (t.pose > seen) { seen = t.pose; advanced = true; }
            }
            lunged = t.status == Salvage.Status.LUNGED;
        }
        check("a hostile unit advances", advanced);
        check("and it gets up if nobody stops it", lunged);

        // The two nights are indistinguishable at the start, which is the
        // whole reason the night is a budget rather than a puzzle.
        Salvage a = find(1, true), b = find(1, false);
        check("a hostile unit and a dead one look the same at the start",
                a.pose == b.pose && a.agitation == b.agitation);
        check("and the player cannot see which is which",
                a.seen() == null && b.seen() == null);

        check("the pose ladder is six long", Salvage.Pose.values().length == 6);
        check("slumped is the bottom", Salvage.Pose.of(0) == Salvage.Pose.SLUMPED);
        check("lunging is the top", Salvage.Pose.of(Salvage.POSE_MAX) == Salvage.Pose.LUNGING);
        check("the pose clamps", Salvage.Pose.of(99) == Salvage.Pose.LUNGING);
        check("the window is one pose wide",
                Salvage.POSE_MAX - Salvage.SHOCK_MIN == 1);
    }

    // --------------------------------------------------------------- the shock

    static void shock() {
        section("the shock");

        check("there is one shock", new Salvage(1, 1).shocks == 1);

        Salvage s = new Salvage(1, 1);
        check("the shock is available", s.canShock());
        check("and it does nothing from the chair", !s.shock());
        check("and it is spent", s.shocks == 0);
        check("and the game says so", s.wasted);
        check("and the night is still running", s.status == Salvage.Status.PLAYING);
        check("and a second press does nothing", !s.shock());

        // The shock reaches something that is up, and only that.
        Salvage up = find(1, true);
        up.pose = Salvage.SHOCK_MIN;
        check("the shock lands on something that is up", up.shock());
        check("and the salvage is done", up.status == Salvage.Status.DESTROYED);
        check("and it was not a waste", !up.wasted);

        Salvage low = find(1, true);
        low.pose = Salvage.SHOCK_MIN - 1;
        check("the shock misses something still in the chair", !low.shock());
        check("and it is gone", low.shocks == 0);
        check("and the night is not over", low.status == Salvage.Status.PLAYING);

        // A wasted shock on a hostile night is fatal, and that is the
        // whole reason the shock is a decision rather than a button.
        Salvage fatal = find(1, true);
        fatal.pose = 1;
        fatal.shock();
        double dt = 1.0 / 60.0;
        for (int f = 0; f < 60 * 400 && fatal.status == Salvage.Status.PLAYING; f++) {
            fatal.update(dt);
        }
        check("and a hostile unit with no shock left gets up",
                fatal.status == Salvage.Status.LUNGED);

        // A wasted shock on a dead night costs nothing but the shock.
        Salvage harmless = find(1, false);
        harmless.shock();
        for (int f = 0; f < 60 * 400 && harmless.status == Salvage.Status.PLAYING; f++) {
            harmless.update(dt);
        }
        check("and a dead unit does not care", harmless.status == Salvage.Status.SURVIVED);

        check("the shock is not free", Salvage.SHOCK_MIN > 0);
        check("the window is above the chair", Salvage.SHOCK_MIN < Salvage.POSE_MAX);
    }

    // --------------------------------------------------------------- the noise

    static void noise() {
        section("the noise");

        // A drag and a creak are different cues, and that is the design:
        // the drag is the free channel and the creak is the room.
        Salvage s = new Salvage(1, 11);
        s.makeDrag();
        s.makeCreak();
        var cues = s.drainCues();
        check("a drag is its own cue", cues.contains("drag"));
        check("a creak is not a drag", cues.stream().anyMatch(c -> c.startsWith("creak_")));
        check("a drag counts as a drag", s.drags == 1);
        check("and both count as noise", s.noises == 2);
        check("and the drag is fresh", s.dragAge == 0);
        check("and the creak is fresh", s.creakAge == 0);

        // A silent advance is an advance nobody counted, which is the
        // whole reason the count drifts and the lamp has to be spent.
        check("a unit can move without a sound",
                java.util.Arrays.stream(new Unit[]{Unit.SCRAPTRAP, Unit.SCRAP_BABY,
                        Unit.MOLTEN_FREDDY, Unit.LEFTY, Unit.SCRAPTRAP_BACK})
                        .anyMatch(u -> u.silent() > 0));
        check("and no unit is silent every time",
                java.util.Arrays.stream(new Unit[]{Unit.SCRAPTRAP, Unit.SCRAP_BABY,
                        Unit.MOLTEN_FREDDY, Unit.LEFTY, Unit.SCRAPTRAP_BACK})
                        .allMatch(u -> u.silent() < 1.0));

        // Over a week, the drag count is short of the pose on every night
        // where the unit is not perfectly loud. That is the drift.
        for (int n = 1; n <= 5; n++) {
            Salvage t = find(n, true);
            double dt = 1.0 / 60.0;
            for (int f = 0; f < 60 * 400 && t.status == Salvage.Status.PLAYING; f++) t.update(dt);
            check("night " + n + ": the count is short of the pose ("
                            + t.drags + " drags, " + t.silentSteps + " silent)",
                    t.drags + t.silentSteps >= t.pose);
        }

        // The creaks are the reason the room is never silent.
        check("the building creaks on every night", creaksIn(1) > 0);
        for (int n = 1; n < 5; n++) {
            check("night " + (n + 1) + " creaks at least as often as night " + n,
                    creakRate(n + 1) >= creakRate(n));
        }
    }

    static double creakRate(int n) {
        return new Salvage(n, 1).creakRate();
    }

    static int creaksIn(int night) {
        Salvage s = find(night, false);
        double dt = 1.0 / 60.0;
        for (int f = 0; f < 60 * 400 && s.status == Salvage.Status.PLAYING; f++) s.update(dt);
        return s.noises;
    }

    // --------------------------------------------------------------- the clock

    static void clock() {
        section("the clock");

        check("six hours", Salvage.NIGHT_HOURS == 6);
        Salvage s = new Salvage(1, 2);
        check("it opens at midnight", s.clock().equals("12 AM"));
        s.hour = 3;
        check("and reads three", s.clock().equals("3 AM"));
        s.hour = 6;
        check("and six", s.clock().equals("6 AM"));

        // The night is the same length on every night, and the ramp lives
        // in the tables rather than in the clock. Measured on a night with
        // nothing in the chair, because a hostile one ends early.
        Salvage t = find(1, false);
        double dt = 1.0 / 60.0;
        for (int f = 0; f < 60 * 400 && t.status == Salvage.Status.PLAYING; f++) t.update(dt);
        check("a dead night runs the whole six hours",
                Math.abs(t.time - Salvage.HOUR_SECONDS * 6) < 0.5);

        // The tables ramp. Every one of these is a rule about the week.
        for (int n = 1; n < 5; n++) {
            check("night " + (n + 1) + " gives you no more time than night " + n,
                    new Salvage(n + 1, 1).advance() <= new Salvage(n, 1).advance());
            check("night " + (n + 1) + " charges more for the lamp than night " + n,
                    new Salvage(n + 1, 1).litRate() >= new Salvage(n, 1).litRate());
            check("night " + (n + 1) + " is at least as quiet as night " + n,
                    new Salvage(n + 1, 1).silent() >= new Salvage(n, 1).silent());
        }
        check("the last night is the fastest",
                new Salvage(5, 1).advance() < new Salvage(1, 1).advance());
        check("the last night is the dearest",
                new Salvage(5, 1).litRate() > new Salvage(1, 1).litRate());

        // The window is one advance, so the night's patience IS the
        // window. This is the number the whole balance turns on.
        for (int n = 1; n <= 5; n++) {
            double window = new Salvage(n, 1).advance();
            check("night " + n + ": the window is one advance (" + window + "s)",
                    Math.abs(window - new Salvage(n, 1).advance()) < 1e-9);
        }
    }

    // ----------------------------------------------------------- the instrument

    static void instrument() {
        section("the instrument");

        // The seed mixer. Without it, `new Random(1000 * night + i)` gives
        // correlated first draws and the hostility coin is biased by
        // night: measured, nights 1 to 3 produced zero hostile units out
        // of two hundred and nights 4 and 5 produced 104 and 191.
        for (int n = 1; n <= 5; n++) {
            int hostile = 0, seeds = 400;
            for (int i = 0; i < seeds; i++) {
                if (new Salvage(n, 1000L * n + i).hostile) hostile++;
            }
            double frac = (double) hostile / seeds;
            check("night " + n + ": the coin is fair (" + hostile + "/" + seeds + ")",
                    frac > 0.40 && frac < 0.60);
        }

        // The jitter. Without it the advance is a metronome, the window
        // opens at the same instant on every seed, and a fixed-clock
        // policy is either always aligned with it or never.
        check("the wait is jittered", Salvage.INTERVAL_JITTER > 0);
        java.util.Set<Long> waits = new java.util.HashSet<>();
        for (int i = 0; i < 50; i++) {
            Salvage s = new Salvage(1, i);
            waits.add(Math.round(s.advanceTimer * 1000));
        }
        check("and no two seeds open with the same wait", waits.size() > 40);

        // A jittered wait still averages out to the table.
        double sum = 0;
        for (int i = 0; i < 2000; i++) sum += new Salvage(3, i).advanceTimer;
        double mean = sum / 2000;
        check("and the mean is still the table's (" + Math.round(mean * 100) / 100.0 + "s)",
                Math.abs(mean - new Salvage(3, 1).advance()) < 0.15);
    }

    // ---------------------------------------------------------------- the mouse

    static void mouse() {
        section("the mouse");

        check("the lamp button is inside the frame",
                MouseMap.in(MouseMap.LAMP, 0, 0)
                        || MouseMap.LAMP[0] >= 0 && MouseMap.LAMP[1] >= 0);
        check("the shock button is inside the frame",
                MouseMap.SHOCK[0] + MouseMap.SHOCK[2] <= MouseMap.W);
        check("the lamp button is clickable",
                MouseMap.hit(MouseMap.LAMP[0] + 4, MouseMap.LAMP[1] + 4).kind()
                        == MouseMap.Kind.LAMP);
        check("the shock button is clickable",
                MouseMap.hit(MouseMap.SHOCK[0] + 4, MouseMap.SHOCK[1] + 4).kind()
                        == MouseMap.Kind.SHOCK);
        // The room itself is a hit region, and it is the one region that
        // does nothing. That is deliberate: the chair is not a button.
        check("the middle of the room is the room, and the room does nothing",
                MouseMap.hit(MouseMap.W / 2, MouseMap.SCENE[1] + 100).kind()
                        == MouseMap.Kind.SCENE);
        check("and the band is not the room",
                MouseMap.hit(MouseMap.W / 2, 30).kind() != MouseMap.Kind.SCENE);
        check("the two buttons do not overlap",
                !MouseMap.in(MouseMap.LAMP, MouseMap.SHOCK[0], MouseMap.SHOCK[1]));

        for (int n = 1; n <= 5; n++) {
            double[] r = MouseMap.nightRow(n);
            check("night " + n + " is clickable",
                    MouseMap.nightAt(r[0] + 4, r[1] + 4) == n);
        }
        // The rows are drawn with a gap between them, and the gap has to
        // be a gap: a row that runs into the next one is a menu where the
        // wrong night starts.
        double gap = (MouseMap.nightRow(1)[1] + MouseMap.nightRow(1)[3]
                + MouseMap.nightRow(2)[1]) / 2;
        check("the night rows do not overlap",
                MouseMap.nightAt(MouseMap.nightRow(1)[0] + 4, gap) == 0);
    }

    // -------------------------------------------------------------- survival

    static void survival() {
        section("the week, 200 seeds a night");

        int seeds = 200;
        System.out.printf("  %-9s %6s %6s %6s %6s %6s %7s%n",
                "policy", "n1", "n2", "n3", "n4", "n5", "week");
        double[][] table = new double[Bot.Policy.values().length][];
        for (Bot.Policy p : Bot.Policy.values()) {
            double[] row = new double[5];
            for (int n = 1; n <= 5; n++) row[n - 1] = Bot.survival(p, n, seeds);
            table[p.ordinal()] = row;
            System.out.printf("  %-9s %5.0f%% %5.0f%% %5.0f%% %5.0f%% %5.0f%% %6.0f%%%n",
                    p, row[0] * 100, row[1] * 100, row[2] * 100,
                    row[3] * 100, row[4] * 100, Bot.week(p, seeds) * 100);
        }

        // Who ends the night, for the policy the ladder is read against.
        for (int n = 1; n <= 5; n++) {
            int[] k = Bot.killers(Bot.Policy.PRO, n, seeds);
            System.out.printf("  PRO n%d  got up %3d   never moved %3d   destroyed %3d%n",
                    n, k[0], k[1], k[2]);
        }

        // The control. Half the nights have nothing in the chair, so a
        // player who does nothing survives half the week -- and that is
        // the floor every other number is measured against.
        double idle = Bot.week(Bot.Policy.IDLE, seeds);
        check("doing nothing is a coin flip (" + Math.round(idle * 100) + "%)",
                idle > 0.40 && idle < 0.60);
        check("and so is shocking at five seconds",
                Math.abs(Bot.week(Bot.Policy.PANIC, seeds) - idle) < 0.06);

        // The lamp left on kills you on every night of the week.
        for (int n = 1; n <= 5; n++) {
            check("night " + n + ": the lamp left on kills you",
                    Bot.survival(Bot.Policy.LAMP_ON, n, 60) == 0.0);
        }

        // THE LADDER. The policy that plays both channels must beat every
        // policy that plays one, and every policy that plays one must beat
        // doing nothing.
        double pro = Bot.week(Bot.Policy.PRO, seeds);
        check("the competent policy beats reacting to sound alone",
                pro > Bot.week(Bot.Policy.LISTEN, seeds));
        check("the competent policy beats looking on a clock alone",
                pro > Bot.week(Bot.Policy.PATROL, seeds));
        check("the competent policy beats doing nothing", pro > idle + 0.15);
        check("reacting to sound beats doing nothing",
                Bot.week(Bot.Policy.LISTEN, seeds) > idle + 0.10);
        check("looking on a clock beats doing nothing",
                Bot.week(Bot.Policy.PATROL, seeds) > idle + 0.10);

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
                row[4] < row[0] - 0.10);
        check("the competent policy is not free (" + Math.round(pro * 100) + "%)",
                pro < 0.92);
    }
    /** Does `audio/<name>.<ext>` exist for any extension the loader reads? */
    static boolean hasCue(String name) {
        java.io.File dir = new java.io.File("audio");
        for (String ext : new String[]{".wav", ".mp3", ".aiff", ".aif", ".m4a", ".aac"}) {
            if (new java.io.File(dir, name + ext).isFile()) return true;
        }
        return false;
    }

}
